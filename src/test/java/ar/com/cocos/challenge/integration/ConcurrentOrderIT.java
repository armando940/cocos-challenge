package ar.com.cocos.challenge.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test de concurrencia real (PostgreSQL + Testcontainers) para el
 * fix de la race condition de saldo (R005 — lock pesimista por usuario).
 *
 * <p>Demuestra que el lock {@code SELECT ... FOR UPDATE} sobre {@code users.id}
 * adquirido dentro de {@code PlaceOrderUseCase.execute()} (@Transactional)
 * serializa las órdenes de un mismo usuario sin bloquear a otros usuarios.</p>
 *
 * <p>Fixture 100% propio: cada test crea su usuario, instrumento y market data
 * con SQL parametrizado. No depende de datos del seed de desarrollo.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@Timeout(180)
class ConcurrentOrderIT {

    /** Monto inicial de cada usuario del escenario (ARS). */
    private static final long INITIAL_CASH = 500_000L;

    /** Monto por orden BUY MARKET (ARS). Con close=100.00 implica size=2000 y costo=200.000. */
    private static final String ORDER_AMOUNT = "200000";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    // ------------------------------------------------------------------
    // CA-01: 6 BUY MARKET concurrentes del mismo usuario
    // ------------------------------------------------------------------

    @Test
    @Timeout(180)
    @DisplayName("CA-01: 6 BUY MARKET concurrentes del mismo usuario nunca sobregiran el saldo")
    void concurrentBuysFromSameUserNeverOverdraw() throws Exception {
        long userId = createUser();
        long instrumentId = createInstrument();
        insertMarketData(instrumentId, new BigDecimal("100.00"));
        fundUser(userId, INITIAL_CASH);

        List<Map<String, Object>> requests = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            requests.add(buyMarketByAmount(userId, instrumentId, ORDER_AMOUNT, "race-same-" + UUID.randomUUID()));
        }

        List<ResponseEntity<String>> responses = fireConcurrently(requests);
        assertThat(responses).hasSize(6);

        int filled = 0;
        int rejected = 0;
        for (ResponseEntity<String> response : responses) {
            JsonNode body = read(response.getBody());
            String status = body.path("status").asText();

            if ("FILLED".equals(status)) {
                filled++;
                assertThat(response.getStatusCode())
                        .as("FILLED debe responder 201")
                        .isEqualTo(HttpStatus.CREATED);
                assertThat(body.path("executedSize").asLong()).isEqualTo(2000L);
            } else if ("REJECTED".equals(status)) {
                rejected++;
                assertThat(response.getStatusCode())
                        .as("REJECTED debe responder 400")
                        .isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(body.path("rejectionReason").asText())
                        .as("El rechazo debe ser por saldo insuficiente")
                        .isEqualTo("INSUFFICIENT_CASH");
            }
        }

        assertThat(filled + rejected)
                .as("Las 6 respuestas deben ser FILLED o REJECTED, sin estados inesperados")
                .isEqualTo(6);
        assertThat(filled)
                .as("Con 500.000 ARS solo entran 2 compras de 200.000")
                .isEqualTo(2);
        assertThat(rejected)
                .as("Las 4 órdenes restantes deben rechazarse")
                .isEqualTo(4);

        BigDecimal finalCash = availableCash(userId);
        assertThat(finalCash)
                .as("El saldo final debe ser exactamente 500.000 - 2 * 200.000")
                .isEqualByComparingTo("100000.00");
        assertThat(finalCash)
                .as("El saldo nunca debe quedar negativo (sobregiro)")
                .isGreaterThanOrEqualTo(BigDecimal.ZERO);
    }

    // ------------------------------------------------------------------
    // CA-03: dos usuarios distintos en paralelo
    // ------------------------------------------------------------------

    @Test
    @Timeout(180)
    @DisplayName("CA-03: dos usuarios distintos compran en paralelo y ambos se ejecutan (lock por usuario, no global)")
    void concurrentBuysFromDifferentUsersBothFill() throws Exception {
        long instrumentId = createInstrument();
        insertMarketData(instrumentId, new BigDecimal("100.00"));

        long userA = createUser();
        long userB = createUser();
        fundUser(userA, INITIAL_CASH);
        fundUser(userB, INITIAL_CASH);

        List<Map<String, Object>> requests = List.of(
                buyMarketByAmount(userA, instrumentId, ORDER_AMOUNT, "race-user-a-" + UUID.randomUUID()),
                buyMarketByAmount(userB, instrumentId, ORDER_AMOUNT, "race-user-b-" + UUID.randomUUID()));

        List<ResponseEntity<String>> responses = fireConcurrently(requests);
        assertThat(responses).hasSize(2);

        for (ResponseEntity<String> response : responses) {
            JsonNode body = read(response.getBody());
            assertThat(response.getStatusCode())
                    .as("Ambas órdenes deben crearse/ejecutarse (201)")
                    .isEqualTo(HttpStatus.CREATED);
            assertThat(body.path("status").asText())
                    .as("El lock es por usuario: ninguno de los dos debe rechazarse")
                    .isEqualTo("FILLED");
            assertThat(body.path("executedSize").asLong()).isEqualTo(2000L);
        }

        assertThat(availableCash(userA))
                .as("El usuario A debe quedar con 500.000 - 200.000")
                .isEqualByComparingTo("300000.00");
        assertThat(availableCash(userB))
                .as("El usuario B debe quedar con 500.000 - 200.000")
                .isEqualByComparingTo("300000.00");
    }

    // ------------------------------------------------------------------
    // Fixture (SQL parametrizado, aislado por test)
    // ------------------------------------------------------------------

    private long createUser() {
        String email = "race-" + UUID.randomUUID() + "@cocos.test";
        String accountNumber = "RACE-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        Long id = jdbc.queryForObject(
                "INSERT INTO users (email, account_number) VALUES (?, ?) RETURNING id",
                Long.class, email, accountNumber);
        return Objects.requireNonNull(id, "No se pudo crear el usuario de fixture");
    }

    private long createInstrument() {
        // ticker único y corto: "RC" + 6 hex = 8 caracteres (schema permite VARCHAR(20))
        String ticker = "RC" + UUID.randomUUID().toString()
                .replace("-", "")
                .substring(0, 6)
                .toUpperCase(Locale.ROOT);
        Long id = jdbc.queryForObject(
                "INSERT INTO instruments (ticker, name, type) VALUES (?, 'Race Test', 'ACCION') RETURNING id",
                Long.class, ticker);
        return Objects.requireNonNull(id, "No se pudo crear el instrumento de fixture");
    }

    private void insertMarketData(long instrumentId, BigDecimal close) {
        // datetime lejano para que sea inequívocamente el último precio del instrumento.
        jdbc.update(
                "INSERT INTO marketdata (instrument_id, high, low, \"open\", \"close\", previous_close, datetime) "
                        + "VALUES (?, ?, ?, ?, ?, ?, TIMESTAMPTZ '2099-01-01 00:00:00+00')",
                instrumentId, close, close, close, close, close);
    }

    /**
     * Inserta un CASH_IN FILLED (instrumento ARS, precio 1.00) para dotar de
     * efectivo al usuario sin depender del seed de desarrollo.
     */
    private void fundUser(long userId, long amount) {
        Long arsInstrumentId = jdbc.queryForObject(
                "SELECT id FROM instruments WHERE type = 'MONEDA' ORDER BY id LIMIT 1",
                Long.class);
        assertThat(arsInstrumentId)
                .as("El baseline siempre crea el instrumento MONEDA (ARS)")
                .isNotNull();

        jdbc.update(
                "INSERT INTO orders (instrument_id, user_id, side, size, price, type, status, datetime, "
                        + "executed_price, executed_size, idempotency_key) "
                        + "VALUES (?, ?, 'CASH_IN', ?, 1.0000, 'MARKET', 'FILLED', now(), 1.0000, ?, ?)",
                arsInstrumentId, userId, amount, amount, "cash-in-" + UUID.randomUUID());
    }

    private Map<String, Object> buyMarketByAmount(
            long userId, long instrumentId, String amount, String idempotencyKey) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("userId", userId);
        body.put("instrumentId", instrumentId);
        body.put("side", "BUY");
        body.put("type", "MARKET");
        // size y amount son mutuamente excluyentes: acá se envía solo amount.
        body.put("amount", new BigDecimal(amount));
        body.put("idempotencyKey", idempotencyKey);
        return body;
    }

    // ------------------------------------------------------------------
    // Helpers HTTP / concurrencia
    // ------------------------------------------------------------------

    /**
     * Dispara los POST en paralelo con un {@link CountDownLatch} para que todos
     * arranquen al mismo tiempo. Cada {@link Future} se espera con timeout.
     */
    private List<ResponseEntity<String>> fireConcurrently(List<Map<String, Object>> bodies) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(bodies.size());
        CountDownLatch startGate = new CountDownLatch(1);
        List<Future<ResponseEntity<String>>> futures = new ArrayList<>();
        try {
            for (Map<String, Object> body : bodies) {
                futures.add(pool.submit(() -> {
                    startGate.await();
                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.APPLICATION_JSON);
                    return rest.postForEntity(
                            "/api/v1/orders",
                            new HttpEntity<>(body, headers),
                            String.class);
                }));
            }

            startGate.countDown();

            List<ResponseEntity<String>> responses = new ArrayList<>(futures.size());
            for (Future<ResponseEntity<String>> future : futures) {
                responses.add(future.get(30, TimeUnit.SECONDS));
            }
            return responses;
        } finally {
            pool.shutdownNow();
            pool.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private BigDecimal availableCash(long userId) throws Exception {
        ResponseEntity<String> response = rest.getForEntity("/api/v1/portfolio/" + userId, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = read(response.getBody());
        return new BigDecimal(body.path("availableCash").asText());
    }

    private JsonNode read(String body) throws Exception {
        if (body == null || body.isBlank()) {
            return objectMapper.createObjectNode();
        }
        return objectMapper.readTree(body);
    }
}
