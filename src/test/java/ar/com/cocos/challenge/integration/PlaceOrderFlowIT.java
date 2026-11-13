package ar.com.cocos.challenge.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test funcional end-to-end del flujo de órdenes contra PostgreSQL real
 * (Testcontainers + Flyway + seed de desarrollo).
 *
 * Cubre el mínimo pedido por el challenge:
 * MARKET fill, LIMIT new, rechazos por saldo/tenencia, idempotencia y cancelación.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class PlaceOrderFlowIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Flujo completo de órdenes: MARKET, idempotencia, rechazos, LIMIT y cancelación")
    void completeOrderLifecycle() throws Exception {
        // ---------------------------------------------------------------
        // Estado inicial (seed V2__dev_seed.sql)
        // ---------------------------------------------------------------
        JsonNode health = getJson("/actuator/health", HttpStatus.OK);
        assertThat(health.path("status").asText()).isEqualTo("UP");

        // Resolve IDs dinámicamente a partir de la base de datos actual
        long userId = 1L;
        long ggalId = findInstrumentIdByTicker("GGAL");
        long ypfdId = findInstrumentIdByTicker("YPFD");
        // AAPL no existe en el dump de producción; usar PAMP como alternativa
        long aaplId = findInstrumentIdByTicker("PAMP");
        long arsId = findInstrumentIdByTicker("ARS");

        JsonNode initialPortfolio = getJson("/api/v1/portfolio/" + userId, HttpStatus.OK);
        assertThat(initialPortfolio.path("availableCash").asText()).isNotEmpty();
        assertThat(initialPortfolio.path("totalAccountValue").asText()).isNotEmpty();
        assertThat(initialPortfolio.path("positions").size()).isGreaterThanOrEqualTo(0);

        JsonNode search = getJson("/api/v1/instruments/search?q=GGAL", HttpStatus.OK);
        assertThat(search.path("totalElements").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(search.path("content").get(0).path("ticker").asText()).isEqualTo("GGAL");
        BigDecimal ggalCurrentPrice = new BigDecimal(search.path("content").get(0).path("currentPrice").asText());

        // ---------------------------------------------------------------
        // MARKET BUY por size -> FILLED al close
        // ---------------------------------------------------------------
        Map<String, Object> marketRequest =
                orderRequest(userId, ypfdId, "BUY", "MARKET", null, 10L, null, "it-market-buy");
        JsonNode market = postJson("/api/v1/orders", marketRequest, HttpStatus.CREATED);

        assertThat(market.path("status").asText()).isEqualTo("FILLED");
        // El precio de ejecución debe coincidir con el close actual del instrumento
        JsonNode ypfdSearch = getJson("/api/v1/instruments/search?q=YPFD", HttpStatus.OK);
        BigDecimal ypfdCurrentPrice = new BigDecimal(ypfdSearch.path("content").get(0).path("currentPrice").asText());
        assertThat(new BigDecimal(market.path("executedPrice").asText())).isEqualByComparingTo(ypfdCurrentPrice);
        assertThat(market.path("executedSize").asLong()).isEqualTo(10);
        long marketOrderId = market.path("orderId").asLong();
        assertThat(marketOrderId).isPositive();

        // ---------------------------------------------------------------
        // Idempotencia: mismo request + misma key => misma orden
        // ---------------------------------------------------------------
        JsonNode replay = postJson("/api/v1/orders", marketRequest, HttpStatus.CREATED);
        assertThat(replay.path("orderId").asLong()).isEqualTo(marketOrderId);
        assertThat(replay.path("datetime").asText()).isEqualTo(market.path("datetime").asText());

        // Misma key con request distinto => 409
        Map<String, Object> conflictingRequest =
                orderRequest(userId, ypfdId, "BUY", "MARKET", null, 11L, null, "it-market-buy");
        postJson("/api/v1/orders", conflictingRequest, HttpStatus.CONFLICT);

        // ---------------------------------------------------------------
        // MARKET BUY por amount -> size = floor(amount / close)
        // ---------------------------------------------------------------
        JsonNode amount = postJson(
                "/api/v1/orders",
                orderRequest(userId, aaplId, "BUY", "MARKET", null, null, new BigDecimal("50000"), "it-amount-buy"),
                HttpStatus.CREATED);

        assertThat(amount.path("status").asText()).isEqualTo("FILLED");
        // aaplId apunta a PAMP en producción
        JsonNode pampSearch = getJson("/api/v1/instruments/search?q=PAMP", HttpStatus.OK);
        BigDecimal aaplCurrentPrice = new BigDecimal(pampSearch.path("content").get(0).path("currentPrice").asText());
        assertThat(new BigDecimal(amount.path("executedPrice").asText())).isEqualByComparingTo(aaplCurrentPrice);
        // size = floor(amount / price)
        long expectedSize = new BigDecimal("50000").divide(aaplCurrentPrice, 0, java.math.RoundingMode.DOWN).longValue();
        assertThat(amount.path("executedSize").asLong()).isEqualTo(expectedSize);

        // ---------------------------------------------------------------
        // Rechazos: se persisten como REJECTED y responden 400
        // ---------------------------------------------------------------
        // Para probar rechazo por saldo, usar una cantidad imposiblemente grande
        JsonNode rejectedCash = postJson(
                "/api/v1/orders",
                orderRequest(userId, ggalId, "BUY", "MARKET", null, 9999999L, null, "it-reject-cash"),
                HttpStatus.BAD_REQUEST);
        assertThat(rejectedCash.path("status").asText()).isEqualTo("REJECTED");
        assertThat(rejectedCash.path("rejectionReason").asText()).isEqualTo("INSUFFICIENT_CASH");

        JsonNode rejectedShares = postJson(
                "/api/v1/orders",
                orderRequest(userId, ggalId, "SELL", "MARKET", null, 999L, null, "it-reject-shares"),
                HttpStatus.BAD_REQUEST);
        assertThat(rejectedShares.path("status").asText()).isEqualTo("REJECTED");
        assertThat(rejectedShares.path("rejectionReason").asText()).isEqualTo("INSUFFICIENT_SHARES");

        // ---------------------------------------------------------------
        // LIMIT -> NEW y cancelación (segunda cancelación => 409)
        // ---------------------------------------------------------------
        JsonNode limit = postJson(
                "/api/v1/orders",
                orderRequest(userId, ggalId, "BUY", "LIMIT", new BigDecimal("10000"), 5L, null, "it-limit"),
                HttpStatus.CREATED);

        assertThat(limit.path("status").asText()).isEqualTo("NEW");
        assertThat(limit.path("executedPrice").isNull()).isTrue();
        assertThat(limit.path("executedSize").isNull()).isTrue();
        long limitOrderId = limit.path("orderId").asLong();

        JsonNode cancelled = postJson("/api/v1/orders/" + limitOrderId + "/cancel", null, HttpStatus.OK);
        assertThat(cancelled.path("status").asText()).isEqualTo("CANCELLED");

        postJson("/api/v1/orders/" + limitOrderId + "/cancel", null, HttpStatus.CONFLICT);

        // ---------------------------------------------------------------
        // Validación de request: size y amount son mutuamente excluyentes
        // ---------------------------------------------------------------
        postJson(
                "/api/v1/orders",
                orderRequest(userId, ggalId, "BUY", "MARKET", null, 1L, new BigDecimal("1000"), "it-invalid"),
                HttpStatus.BAD_REQUEST);

        // ---------------------------------------------------------------
        // Portfolio final: el efectivo baja por las compras ejecutadas;
        // las rechazadas y la LIMIT cancelada no afectan.
        // ---------------------------------------------------------------
        JsonNode finalPortfolio = getJson("/api/v1/portfolio/" + userId, HttpStatus.OK);
        // El efectivo debe haber disminuido por las compras MARKET ejecutadas
        BigDecimal initialCash = new BigDecimal(initialPortfolio.path("availableCash").asText());
        BigDecimal finalCash = new BigDecimal(finalPortfolio.path("availableCash").asText());
        assertThat(finalCash).isLessThan(initialCash);
        // El total de la cuenta debe ser >= efectivo disponible
        BigDecimal totalValue = new BigDecimal(finalPortfolio.path("totalAccountValue").asText());
        assertThat(totalValue).isGreaterThanOrEqualTo(finalCash);
        // Debe haber al menos una posición
        assertThat(finalPortfolio.path("positions").size()).isGreaterThanOrEqualTo(1);
    }

    private JsonNode getJson(String path, HttpStatus expectedStatus) throws Exception {
        ResponseEntity<String> response = rest.getForEntity(path, String.class);
        assertThat(response.getStatusCode()).isEqualTo(expectedStatus);
        return read(response.getBody());
    }

    private JsonNode postJson(String path, Map<String, Object> body, HttpStatus expectedStatus) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        ResponseEntity<String> response = rest.postForEntity(path, entity, String.class);
        assertThat(response.getStatusCode()).isEqualTo(expectedStatus);
        return read(response.getBody());
    }

    private JsonNode read(String body) throws Exception {
        if (body == null || body.isBlank()) {
            return objectMapper.createObjectNode();
        }
        return objectMapper.readTree(body);
    }

    private long findInstrumentIdByTicker(String ticker) throws Exception {
        JsonNode search = getJson("/api/v1/instruments/search?q=" + ticker, HttpStatus.OK);
        assertThat(search.path("totalElements").asLong()).isGreaterThan(0);
        return search.path("content").get(0).path("instrumentId").asLong();
    }

    private Map<String, Object> orderRequest(
            Long userId,
            Long instrumentId,
            String side,
            String type,
            BigDecimal price,
            Long size,
            BigDecimal amount,
            String idempotencyKey) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("userId", userId);
        body.put("instrumentId", instrumentId);
        body.put("side", side);
        body.put("type", type);
        if (price != null) {
            body.put("price", price);
        }
        if (size != null) {
            body.put("size", size);
        }
        if (amount != null) {
            body.put("amount", amount);
        }
        body.put("idempotencyKey", idempotencyKey);
        return body;
    }
}
