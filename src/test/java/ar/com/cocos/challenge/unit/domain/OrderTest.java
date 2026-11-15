package ar.com.cocos.challenge.unit.domain;

import ar.com.cocos.challenge.domain.model.Order;
import ar.com.cocos.challenge.domain.model.OrderSide;
import ar.com.cocos.challenge.domain.model.OrderStatus;
import ar.com.cocos.challenge.domain.model.OrderType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTest {

    private static final Long INSTRUMENT_ID = 2L;
    private static final Long USER_ID = 1L;

    @Test
    @DisplayName("MARKET ejecutada: preserva price nulo y setea executedPrice/executedSize")
    void marketFilledKeepsNullPriceAndSetsExecutionData() {
        Order market = Order.newMarketOrder(INSTRUMENT_ID, USER_ID, OrderSide.BUY, 10L, "key-market");

        Order filled = market.filled(new BigDecimal("11200.00"), 10L);

        assertThat(filled.status()).isEqualTo(OrderStatus.FILLED);
        assertThat(filled.datetime()).isNotNull();
        assertThat(filled.price()).isNull();
        assertThat(filled.executedPrice()).isEqualByComparingTo("11200.00");
        assertThat(filled.executedSize()).isEqualTo(10L);
    }

    @Test
    @DisplayName("LIMIT ejecutada a otro precio: price conserva el límite original")
    void limitFilledKeepsOriginalLimitPrice() {
        Order limit = Order.newLimitOrder(INSTRUMENT_ID, USER_ID, OrderSide.BUY, 10L, new BigDecimal("100.00"), "key-limit");

        Order filled = limit.filled(new BigDecimal("115.00"), 10L);

        assertThat(filled.status()).isEqualTo(OrderStatus.FILLED);
        assertThat(filled.datetime()).isNotNull();
        assertThat(filled.price()).isEqualByComparingTo("100.00");
        assertThat(filled.executedPrice()).isEqualByComparingTo("115.00");
        assertThat(filled.executedSize()).isEqualTo(10L);
    }

    @Test
    @DisplayName("FILLED sin executedPrice es un estado inválido")
    void filledWithoutExecutedPriceFails() {
        assertThatThrownBy(() -> new Order(
                1L, INSTRUMENT_ID, USER_ID, OrderSide.BUY, 10L, null, OrderType.MARKET,
                OrderStatus.FILLED, Instant.now(), null, 10L, null, "key"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("executedPrice");
    }

    @Test
    @DisplayName("FILLED sin executedSize es un estado inválido")
    void filledWithoutExecutedSizeFails() {
        assertThatThrownBy(() -> new Order(
                1L, INSTRUMENT_ID, USER_ID, OrderSide.BUY, 10L, null, OrderType.MARKET,
                OrderStatus.FILLED, Instant.now(), new BigDecimal("11200.00"), null, null, "key"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("executedSize");
    }

    @Test
    @DisplayName("FILLED con executedPrice no positivo es un estado inválido")
    void filledWithNonPositiveExecutedPriceFails() {
        assertThatThrownBy(() -> new Order(
                1L, INSTRUMENT_ID, USER_ID, OrderSide.BUY, 10L, null, OrderType.MARKET,
                OrderStatus.FILLED, Instant.now(), BigDecimal.ZERO, 10L, null, "key"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("executedPrice");

        assertThatThrownBy(() -> new Order(
                1L, INSTRUMENT_ID, USER_ID, OrderSide.BUY, 10L, null, OrderType.MARKET,
                OrderStatus.FILLED, Instant.now(), new BigDecimal("-1.00"), 10L, null, "key"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("executedPrice");
    }

    @Test
    @DisplayName("FILLED con executedSize mayor que size es un estado inválido")
    void filledWithExecutedSizeGreaterThanSizeFails() {
        assertThatThrownBy(() -> new Order(
                1L, INSTRUMENT_ID, USER_ID, OrderSide.BUY, 10L, null, OrderType.MARKET,
                OrderStatus.FILLED, Instant.now(), new BigDecimal("11200.00"), 11L, null, "key"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("executedSize");
    }

    @Test
    @DisplayName("FILLED sin size es un estado inválido")
    void filledWithoutSizeFails() {
        assertThatThrownBy(() -> new Order(
                1L, INSTRUMENT_ID, USER_ID, OrderSide.BUY, null, null, OrderType.MARKET,
                OrderStatus.FILLED, Instant.now(), new BigDecimal("11200.00"), 10L, null, "key"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("size");
    }
}
