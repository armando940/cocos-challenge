package ar.com.cocos.challenge.integration;

import ar.com.cocos.challenge.domain.model.IdempotencyConflictException;
import ar.com.cocos.challenge.domain.port.in.PlaceOrderPort;
import ar.com.cocos.challenge.domain.port.out.InstrumentRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test de integración para verificar la race condition de idempotencia cross-user (CA-01, CA-02).
 * 
 * Requiere: 008-fix-idempotency-race-condition
 * Verifica que dos usuarios distintos con la misma idempotency_key no puedan procesar
 * la orden dos veces (exactly-once global por key).
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class IdempotencyRaceConditionIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("cocos")
            .withUsername("cocos")
            .withPassword("cocos");

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private PlaceOrderPort placeOrderPort;

    @Autowired
    private InstrumentRepositoryPort instrumentRepository;

    private Long ggalId;
    private Long user1Id = 1L;
    private Long user2Id = 2L;

    @BeforeEach
    void setUp() {
        // Buscar instrumento GGAL
        ggalId = instrumentRepository.findByTicker("GGAL")
                .orElseThrow(() -> new IllegalStateException("GGAL no encontrado en BD de test"))
                .id();
    }

    @Test
    @DisplayName("CA-01: Misma key, distinto user, mismo request → exactamente uno 201, el otro 409")
    void sameKeyDifferentUserSameRequest_exactlyOneSuccess() throws Exception {
        // Given: Una key inexistente
        String idempotencyKey = "race-test-" + System.currentTimeMillis();

        // When: Dos hilos (distinto userId) ejecutan la misma orden simultáneamente
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        AtomicReference<String> firstOrderId = new AtomicReference<>();

        Runnable task = () -> {
            try {
                startLatch.await(); // Esperar señal de inicio simultáneo

                // Cada hilo usa su propio userId
                Long userId = Thread.currentThread().getName().contains("user1") ? user1Id : user2Id;

                var command = new PlaceOrderPort.Command(
                        userId,
                        ggalId,
                        ar.com.cocos.challenge.domain.model.OrderSide.BUY,
                        ar.com.cocos.challenge.domain.model.OrderType.MARKET,
                        null,
                        10L,
                        null,
                        idempotencyKey
                );

                PlaceOrderPort.Result result = placeOrderPort.execute(command);

                if (result.status() == ar.com.cocos.challenge.domain.model.OrderStatus.FILLED) {
                    successCount.incrementAndGet();
                    firstOrderId.compareAndSet(null, result.orderId().toString());
                }
            } catch (IdempotencyConflictException e) {
                conflictCount.incrementAndGet();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                errorCount.incrementAndGet();
            } finally {
                doneLatch.countDown();
            }
        };

        ExecutorService executor = Executors.newFixedThreadPool(2);
        executor.submit(() -> {
            Thread.currentThread().setName("user1-thread");
            task.run();
        });
        executor.submit(() -> {
            Thread.currentThread().setName("user2-thread");
            task.run();
        });

        // Disparar ambos hilos simultáneamente
        startLatch.countDown();

        // Esperar a que terminen (timeout generoso)
        boolean completed = doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdownNow();

        // Then: Exactamente uno éxito, el otro conflict 409
        assertThat(completed).as("Test no completó en tiempo").isTrue();
        assertThat(errorCount.get()).as("No debe haber errores inesperados").isZero();
        assertThat(successCount.get()).as("Exactamente una orden debe procesarse exitosamente").isEqualTo(1);
        assertThat(conflictCount.get()).as("La otra debe fallar con IdempotencyConflictException (409)").isEqualTo(1);
        assertThat(firstOrderId.get()).isNotNull();
    }

    @Test
    @DisplayName("CA-02: Misma key, distinto user, request distinto → uno 201, el otro 409 por hash mismatch")
    void sameKeyDifferentUserDifferentRequest_conflictByHashMismatch() throws Exception {
        // Given: Una key inexistente
        String idempotencyKey = "race-test-hash-" + System.currentTimeMillis();

        // When: Dos hilos con misma key pero request distinto (distinto size)
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        Runnable taskUser1 = () -> {
            try {
                startLatch.await();
                var command = new PlaceOrderPort.Command(
                        user1Id,
                        ggalId,
                        ar.com.cocos.challenge.domain.model.OrderSide.BUY,
                        ar.com.cocos.challenge.domain.model.OrderType.MARKET,
                        null,
                        10L, // size = 10
                        null,
                        idempotencyKey
                );
                PlaceOrderPort.Result result = placeOrderPort.execute(command);
                if (result.status() == ar.com.cocos.challenge.domain.model.OrderStatus.FILLED) {
                    successCount.incrementAndGet();
                }
            } catch (IdempotencyConflictException e) {
                conflictCount.incrementAndGet();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                errorCount.incrementAndGet();
            } finally {
                doneLatch.countDown();
            }
        };

        Runnable taskUser2 = () -> {
            try {
                startLatch.await();
                var command = new PlaceOrderPort.Command(
                        user2Id,
                        ggalId,
                        ar.com.cocos.challenge.domain.model.OrderSide.BUY,
                        ar.com.cocos.challenge.domain.model.OrderType.MARKET,
                        null,
                        20L, // size = 20 (DISTINTO request)
                        null,
                        idempotencyKey
                );
                PlaceOrderPort.Result result = placeOrderPort.execute(command);
                if (result.status() == ar.com.cocos.challenge.domain.model.OrderStatus.FILLED) {
                    successCount.incrementAndGet();
                }
            } catch (IdempotencyConflictException e) {
                conflictCount.incrementAndGet();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                errorCount.incrementAndGet();
            } finally {
                doneLatch.countDown();
            }
        };

        ExecutorService executor = Executors.newFixedThreadPool(2);
        executor.submit(taskUser1);
        executor.submit(taskUser2);

        startLatch.countDown();

        boolean completed = doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdownNow();

        // Then: Exactamente uno éxito, el otro conflict 409 por hash mismatch
        assertThat(completed).as("Test no completó en tiempo").isTrue();
        assertThat(errorCount.get()).as("No debe haber errores inesperados").isZero();
        assertThat(successCount.get()).as("Exactamente una orden debe procesarse exitosamente").isEqualTo(1);
        assertThat(conflictCount.get()).as("La otra debe fallar con IdempotencyConflictException (409) por hash mismatch").isEqualTo(1);
    }
}