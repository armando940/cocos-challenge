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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test de concurrencia real contra PostgreSQL (Testcontainers) para
 * el requerimiento 005 (lock pesimista por usuario en {@code PlaceOrderUseCase}).
 *
 * <p>Cubre dos escenarios con fixtures 100% propios (sin depender del seed):</p>
 * <ul>
 *   <li><b>CA-05</b>: dos {@code SELL MARKET} concurrentes de la misma posición no
 *       pueden vender más acciones de las que hay.</li>
 *   <li><b>CA-06</b>: dos {@code CASH_OUT MARKET} concurrentes no pueden sobregirar
 *       el efectivo disponible.</li>
 * </ul>
 *
 * <p>La sincronización se hace con {@link CountDownLatch} (nunca {@code Thread.sleep})
 * para que ambos requests lleguen al backend lo más cerca posible en el tiempo.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@Timeout(180)
class ConcurrentSellAndCashOutIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final long REQUEST_TIMEOUT_SECONDS = 30;

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    // ------------------------------------------------------------------
    // CA-05 — dos SELL MARKET concurrentes no sobrevenden
    // ------------------------------------------------------------------

    @Test
    @DisplayName("CA-05: dos SELL MARKET concurrentes de la misma posición => 1 FILLED + 1 INSUFFICIENT_SHARES")
    void concurrentSellCannotOversell() throws Exception {
        String suffix = newSuffix();
        long userId = insertUser(suffix);
        long instrumentId = insertEquityInstrument("RACE" + suffix.toUpperCase());
        insertMarketData(instrumentId, new BigDecimal("100.00"));

        long arsId = findOrCreateArsInstrument();
        // Saldo inicial: 1.000.000 ARS (no hace falta para SELL, pero replica un usuario real).
        insertFilledCashIn(userId, arsId, 1_000_000L, newKey("cashin"));
        // Posición inicial: 100 acciones a 100.00, FILLED.
        insertFilledBuy(userId, instrumentId, 100L, newKey("buy"));

        Map<String, Object> sellOne = orderRequest(
                userId, instrumentId, "SELL", "MARKET", 100L, newKey("sell-a"));
        Map<String, Object> sellTwo = orderRequest(
                userId, instrumentId, "SELL", "MARKET", 100L, newKey("sell-b"));

        List<OrderHttpResponse> responses = race(
                () -> postOrder(sellOne),
                () -> postOrder(sellTwo));

        assertThat(responses)
                .as("debe haber exactamente un 201 (FILLED) y un 400 (REJECTED)")
                .extracting(OrderHttpResponse::httpStatus)
                .containsExactlyInAnyOrder(HttpStatus.CREATED.value(), HttpStatus.BAD_REQUEST.value());

        OrderHttpResponse filled = onlyWithStatus(responses, HttpStatus.CREATED.value());
        assertThat(filled.body().path("status").asText())
                .as("la orden que gana el lock se ejecuta completa")
                .isEqualTo("FILLED");
        assertThat(filled.body().path("executedSize").asLong()).isEqualTo(100L);

        OrderHttpResponse rejected = onlyWithStatus(responses, HttpStatus.BAD_REQUEST.value());
        assertThat(rejected.body().path("status").asText()).isEqualTo("REJECTED");
        assertThat(rejected.body().path("rejectionReason").asText())
                .as("la segunda venta ve 0 acciones disponibles y se rechaza")
                .isEqualTo("INSUFFICIENT_SHARES");

        // Doble check contra la base: exactamente 1 SELL FILLED y 1 SELL REJECTED.
        assertThat(countOrders(userId, instrumentId, "SELL", "FILLED")).isEqualTo(1L);
        assertThat(countOrders(userId, instrumentId, "SELL", "REJECTED")).isEqualTo(1L);

        JsonNode portfolio = getJson("/api/v1/portfolio/" + userId);
        assertThat(quantityOf(portfolio, instrumentId))
                .as("la posición del instrumento queda en 0; nunca negativa")
                .isZero();
    }

    // ------------------------------------------------------------------
    // CA-06 — dos CASH_OUT MARKET concurrentes no sobregiran el efectivo
    // ------------------------------------------------------------------

    @Test
    @DisplayName("CA-06: dos CASH_OUT MARKET concurrentes => 1 FILLED + 1 INSUFFICIENT_CASH y saldo 200.000")
    void concurrentCashOutCannotOverdraw() throws Exception {
        String suffix = newSuffix();
        long userId = insertUser(suffix);
        long arsId = findOrCreateArsInstrument();

        // El instrumento ARS cotiza 1.00: `size` es el monto en pesos.
        insertMarketData(arsId, new BigDecimal("1.00"));
        insertFilledCashIn(userId, arsId, 500_000L, newKey("cashin"));

        Map<String, Object> cashOutOne = orderRequest(
                userId, arsId, "CASH_OUT", "MARKET", 300_000L, newKey("cashout-a"));
        Map<String, Object> cashOutTwo = orderRequest(
                userId, arsId, "CASH_OUT", "MARKET", 300_000L, newKey("cashout-b"));

        List<OrderHttpResponse> responses = race(
                () -> postOrder(cashOutOne),
                () -> postOrder(cashOutTwo));

        assertThat(responses)
                .as("debe haber exactamente un 201 (FILLED) y un 400 (REJECTED)")
                .extracting(OrderHttpResponse::httpStatus)
                .containsExactlyInAnyOrder(HttpStatus.CREATED.value(), HttpStatus.BAD_REQUEST.value());

        OrderHttpResponse filled = onlyWithStatus(responses, HttpStatus.CREATED.value());
        assertThat(filled.body().path("status").asText()).isEqualTo("FILLED");
        assertThat(filled.body().path("executedSize").asLong()).isEqualTo(300_000L);

        OrderHttpResponse rejected = onlyWithStatus(responses, HttpStatus.BAD_REQUEST.value());
        assertThat(rejected.body().path("status").asText()).isEqualTo("REJECTED");
        assertThat(rejected.body().path("rejectionReason").asText())
                .as("la segunda extracción ve 200.000 y no alcanza para 300.000")
                .isEqualTo("INSUFFICIENT_CASH");

        // Doble check contra la base.
        assertThat(countOrders(userId, arsId, "CASH_OUT", "FILLED")).isEqualTo(1L);
        assertThat(countOrders(userId, arsId, "CASH_OUT", "REJECTED")).isEqualTo(1L);

        JsonNode portfolio = getJson("/api/v1/portfolio/" + userId);
        BigDecimal availableCash = new BigDecimal(portfolio.path("availableCash").asText());
        assertThat(availableCash)
                .as("saldo final exacto tras una sola extracción")
                .isEqualByComparingTo("200000.00");
        assertThat(availableCash)
                .as("el saldo derivado nunca queda negativo")
                .isGreaterThanOrEqualTo(BigDecimal.ZERO);
    }

    // ------------------------------------------------------------------
    // Fixtures (JdbcTemplate, todo parametrizado; no toca seed ni migraciones)
    // ------------------------------------------------------------------

    private String newSuffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private String newKey(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    private long insertUser(String suffix) {
        Long id = jdbc.queryForObject(
                "INSERT INTO users (email, account_number) VALUES (?, ?) RETURNING id",
                Long.class,
                "race-" + suffix + "@test.com",
                "ACC-" + suffix.substring(0, 8));
        return requireId(id, "user");
    }

    private long insertEquityInstrument(String ticker) {
        Long id = jdbc.queryForObject(
                "INSERT INTO instruments (ticker, name, type) VALUES (?, 'Race Test', 'ACCION') RETURNING id",
                Long.class,
                ticker);
        return requireId(id, "instrument");
    }

    private void insertMarketData(long instrumentId, BigDecimal close) {
        jdbc.update(
                "INSERT INTO marketdata "
                        + "(instrument_id, high, low, \"open\", \"close\", previous_close, datetime) "
                        + "VALUES (?, ?, ?, ?, ?, ?, TIMESTAMPTZ '2099-01-01 00:00:00+00')",
                instrumentId, close, close, close, close, close);
    }

    private void insertFilledCashIn(long userId, long instrumentId, long amount, String idempotencyKey) {
        jdbc.update(
                "INSERT INTO orders "
                        + "(instrument_id, user_id, side, size, price, type, status, datetime, "
                        + "executed_price, executed_size, idempotency_key) "
                        + "VALUES (?, ?, 'CASH_IN', ?, 1.0000, 'MARKET', 'FILLED', now(), 1.0000, ?, ?)",
                instrumentId, userId, amount, amount, idempotencyKey);
    }

    private void insertFilledBuy(long userId, long instrumentId, long size, String idempotencyKey) {
        jdbc.update(
                "INSERT INTO orders "
                        + "(instrument_id, user_id, side, size, price, type, status, datetime, "
                        + "executed_price, executed_size, idempotency_key) "
                        + "VALUES (?, ?, 'BUY', ?, 100.0000, 'MARKET', 'FILLED', now(), 100.0000, ?, ?)",
                instrumentId, userId, size, size, idempotencyKey);
    }

    /**
     * El instrumento ARS (type = MONEDA) existe en el baseline V1; si por algún
     * motivo no estuviera, el fixture lo crea para no depender del seed.
     */
    private long findOrCreateArsInstrument() {
        List<Long> ids = jdbc.queryForList(
                "SELECT id FROM instruments WHERE type = 'MONEDA' ORDER BY id LIMIT 1",
                Long.class);
        if (!ids.isEmpty()) {
            return ids.get(0);
        }
        Long id = jdbc.queryForObject(
                "INSERT INTO instruments (ticker, name, type) "
                        + "VALUES ('ARS', 'Peso Argentino', 'MONEDA') RETURNING id",
                Long.class);
        return requireId(id, "ARS instrument");
    }

    private long countOrders(long userId, long instrumentId, String side, String status) {
        Long count = jdbc.queryForObject(
                "SELECT count(*) FROM orders "
                        + "WHERE user_id = ? AND instrument_id = ? AND side = ? AND status = ?",
                Long.class, userId, instrumentId, side, status);
        return count != null ? count : 0L;
    }

    private static long requireId(Long id, String what) {
        assertThat(id).as("id generado para %s", what).isNotNull();
        return id;
    }

    // ------------------------------------------------------------------
    // HTTP helpers
    // ------------------------------------------------------------------

    private Map<String, Object> orderRequest(
            long userId,
            long instrumentId,
            String side,
            String type,
            long size,
            String idempotencyKey) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("userId", userId);
        body.put("instrumentId", instrumentId);
        body.put("side", side);
        body.put("type", type);
        body.put("size", size);
        body.put("idempotencyKey", idempotencyKey);
        return body;
    }

    private OrderHttpResponse postOrder(Map<String, Object> body) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        ResponseEntity<String> response = rest.postForEntity("/api/v1/orders", entity, String.class);
        return new OrderHttpResponse(response.getStatusCode().value(), read(response.getBody()));
    }

    private JsonNode getJson(String path) throws Exception {
        ResponseEntity<String> response = rest.getForEntity(path, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return read(response.getBody());
    }

    private JsonNode read(String body) throws Exception {
        if (body == null || body.isBlank()) {
            return objectMapper.createObjectNode();
        }
        return objectMapper.readTree(body);
    }

    // ------------------------------------------------------------------
    // Concurrencia
    // ------------------------------------------------------------------

    /**
     * Lanza dos requests en paralelo con un {@link CountDownLatch} de arranque.
     * Ambos hilos esperan la señal y disparan a la vez; el {@code Future.get}
     * acota la espera para no colgar el test si el lock se traba.
     */
    private List<OrderHttpResponse> race(
            Callable<OrderHttpResponse> first,
            Callable<OrderHttpResponse> second) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<OrderHttpResponse> firstFuture = executor.submit(() -> {
                start.await();
                return first.call();
            });
            Future<OrderHttpResponse> secondFuture = executor.submit(() -> {
                start.await();
                return second.call();
            });
            start.countDown();
            return List.of(
                    firstFuture.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                    secondFuture.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    private static OrderHttpResponse onlyWithStatus(List<OrderHttpResponse> responses, int expectedStatus) {
        List<OrderHttpResponse> matches = responses.stream()
                .filter(response -> response.httpStatus() == expectedStatus)
                .toList();
        assertThat(matches)
                .as("respuestas con HTTP %d", expectedStatus)
                .hasSize(1);
        return matches.get(0);
    }

    private static long quantityOf(JsonNode portfolio, long instrumentId) {
        for (JsonNode position : portfolio.path("positions")) {
            if (position.path("instrumentId").asLong() == instrumentId) {
                return position.path("quantity").asLong();
            }
        }
        return 0L;
    }

    private record OrderHttpResponse(int httpStatus, JsonNode body) {
    }
}
