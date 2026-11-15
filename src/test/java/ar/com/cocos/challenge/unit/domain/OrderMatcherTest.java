package ar.com.cocos.challenge.unit.domain;

import ar.com.cocos.challenge.domain.model.Instrument;
import ar.com.cocos.challenge.domain.model.InstrumentPriceNotFoundException;
import ar.com.cocos.challenge.domain.model.InstrumentType;
import ar.com.cocos.challenge.domain.model.MarketPrice;
import ar.com.cocos.challenge.domain.model.Money;
import ar.com.cocos.challenge.domain.model.Order;
import ar.com.cocos.challenge.domain.model.OrderSide;
import ar.com.cocos.challenge.domain.model.OrderStatus;
import ar.com.cocos.challenge.domain.model.OrderType;
import ar.com.cocos.challenge.domain.port.in.PlaceOrderPort;
import ar.com.cocos.challenge.domain.port.out.MarketDataProviderPort;
import ar.com.cocos.challenge.domain.service.OrderMatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderMatcherTest {

    private static final Long USER_ID = 1L;
    private static final Long GGAL_ID = 2L;

    @Mock
    private MarketDataProviderPort marketDataProvider;

    private OrderMatcher matcher;
    private Instrument ggal;

    @BeforeEach
    void setUp() {
        matcher = new OrderMatcher(marketDataProvider);
        ggal = new Instrument(GGAL_ID, "GGAL", "Grupo Financiero Galicia", InstrumentType.ACCION);
    }

    @Test
    @DisplayName("Crea una orden MARKET con size y sin precio")
    void createsMarketOrderWithSize() {
        Order order = matcher.createOrder(
                command(OrderType.MARKET, OrderSide.BUY, 10L, null, null), ggal);

        assertThat(order.status()).isEqualTo(OrderStatus.NEW);
        assertThat(order.type()).isEqualTo(OrderType.MARKET);
        assertThat(order.size()).isEqualTo(10);
        assertThat(order.price()).isNull();
        assertThat(order.instrumentId()).isEqualTo(GGAL_ID);
    }

    @Test
    @DisplayName("Crea una orden LIMIT con precio")
    void createsLimitOrderWithSize() {
        Order order = matcher.createOrder(
                command(OrderType.LIMIT, OrderSide.BUY, 5L, new BigDecimal("10000"), null), ggal);

        assertThat(order.status()).isEqualTo(OrderStatus.NEW);
        assertThat(order.type()).isEqualTo(OrderType.LIMIT);
        assertThat(order.size()).isEqualTo(5);
        assertThat(order.price()).isEqualByComparingTo("10000");
    }

    @Test
    @DisplayName("Con amount calcula size = floor(amount / close) para MARKET")
    void createsMarketOrderFromAmount() {
        when(marketDataProvider.findLatestPrice(GGAL_ID))
                .thenReturn(Optional.of(price("9500", "9400")));

        Order order = matcher.createOrder(
                command(OrderType.MARKET, OrderSide.BUY, null, null, new BigDecimal("50000")), ggal);

        assertThat(order.size()).isEqualTo(5);
        assertThat(order.price()).isNull();
    }

    @Test
    @DisplayName("Con amount calcula size = floor(amount / price) para LIMIT")
    void createsLimitOrderFromAmount() {
        Order order = matcher.createOrder(
                command(OrderType.LIMIT, OrderSide.BUY, null, new BigDecimal("100"), new BigDecimal("1050")), ggal);

        assertThat(order.size()).isEqualTo(10);
        assertThat(order.price()).isEqualByComparingTo("100");
        verifyNoInteractions(marketDataProvider);
    }

    @Test
    @DisplayName("Rechaza un amount que no alcanza para una acción entera")
    void rejectsAmountSmallerThanOneShare() {
        assertThatThrownBy(() -> matcher.createOrder(
                command(OrderType.LIMIT, OrderSide.BUY, null, new BigDecimal("1000"), new BigDecimal("500")), ggal))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Monto insuficiente");
    }

    @Test
    @DisplayName("Ejecuta una orden MARKET al último close")
    void executesMarketOrderAtLatestClose() {
        Order order = Order.newMarketOrder(GGAL_ID, USER_ID, OrderSide.BUY, 10L, "key-market");
        // Ahora executeMarketOrder recibe el precio como parámetro (no consulta marketDataProvider)
        BigDecimal executionPrice = new BigDecimal("11200");

        Order filled = matcher.executeMarketOrder(order, ggal, executionPrice);

        assertThat(filled.status()).isEqualTo(OrderStatus.FILLED);
        assertThat(filled.executedPrice()).isEqualByComparingTo("11200");
        assertThat(filled.executedSize()).isEqualTo(10);
        // MARKET: filled() preserva el `price` original (null), el precio de ejecución va en executedPrice
        assertThat(filled.price()).isNull();
        // Verificar que NO se consultó marketDataProvider
        verifyNoInteractions(marketDataProvider);
    }

    @Test
    @DisplayName("No ejecuta una orden LIMIT como MARKET")
    void doesNotExecuteLimitOrderAsMarket() {
        Order limit = Order.newLimitOrder(GGAL_ID, USER_ID, OrderSide.BUY, 10L, new BigDecimal("100"), "key-limit");

        // El precio no importa porque debe lanzar excepción antes de usarlo
        assertThatThrownBy(() -> matcher.executeMarketOrder(limit, ggal, new BigDecimal("100")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Solo órdenes MARKET");
        verifyNoInteractions(marketDataProvider);
    }

    @Test
    @DisplayName("Falla al ejecutar MARKET si precio de ejecución es null")
    void failsMarketExecutionWithNullPrice() {
        Order order = Order.newMarketOrder(GGAL_ID, USER_ID, OrderSide.BUY, 10L, "key-market");

        assertThatThrownBy(() -> matcher.executeMarketOrder(order, ggal, null))
                .isInstanceOf(NullPointerException.class);
        // Verificar que NO se consultó marketDataProvider
        verifyNoInteractions(marketDataProvider);
    }

    @Test
    @DisplayName("Previene descalce de precio entre validación y ejecución: usa el precio provisto, no re-consulta market data")
    void preventsPriceDriftBetweenValidationAndExecution() {
        // Given: Una orden MARKET y un precio de ejecución capturado durante la validación
        Order order = Order.newMarketOrder(GGAL_ID, USER_ID, OrderSide.BUY, 10L, "key-market");
        BigDecimal capturedPrice = new BigDecimal("10000"); // Precio capturado en validación

        // When: Ejecutar la orden MARKET con el precio capturado
        Order filled = matcher.executeMarketOrder(order, ggal, capturedPrice);

        // Then: La orden se ejecuta con el precio CAPTURADO (10000)
        assertThat(filled.status()).isEqualTo(OrderStatus.FILLED);
        assertThat(filled.executedPrice()).isEqualByComparingTo("10000"); // Precio capturado
        // MARKET: `price` original se preserva en null; el precio capturado va en executedPrice
        assertThat(filled.price()).isNull();
        assertThat(filled.executedSize()).isEqualTo(10);

        // Verificar que NO se consultó marketDataProvider durante la ejecución
        verifyNoInteractions(marketDataProvider);
    }

    @Test
    @DisplayName("Resuelve el precio de una orden MARKET con el close")
    void resolvesMarketExecutionPrice() {
        when(marketDataProvider.findLatestPrice(GGAL_ID))
                .thenReturn(Optional.of(price("11200", "10800")));

        BigDecimal price = matcher.resolveExecutionPrice(
                command(OrderType.MARKET, OrderSide.BUY, 10L, null, null));

        assertThat(price).isEqualByComparingTo("11200");
    }

    @Test
    @DisplayName("Resuelve el precio de una orden LIMIT con el precio enviado")
    void resolvesLimitExecutionPrice() {
        BigDecimal price = matcher.resolveExecutionPrice(
                command(OrderType.LIMIT, OrderSide.BUY, 10L, new BigDecimal("10000"), null));

        assertThat(price).isEqualByComparingTo("10000");
        verifyNoInteractions(marketDataProvider);
    }

    @Test
    @DisplayName("Falla al resolver precio MARKET sin market data")
    void failsResolvingMarketPriceWithoutData() {
        when(marketDataProvider.findLatestPrice(GGAL_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> matcher.resolveExecutionPrice(
                command(OrderType.MARKET, OrderSide.BUY, 10L, null, null)))
                .isInstanceOf(InstrumentPriceNotFoundException.class);
    }

    @Test
    @DisplayName("calculateSizeFromAmount trunca hacia abajo")
    void calculatesSizeWithFloor() {
        assertThat(OrderMatcher.calculateSizeFromAmount(Money.of("10000"), new BigDecimal("3000")))
                .isEqualTo(3);
    }

    @Test
    @DisplayName("calculateSizeFromAmount valida precio positivo")
    void rejectsNonPositivePrice() {
        assertThatThrownBy(() -> OrderMatcher.calculateSizeFromAmount(Money.of("100"), BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Precio debe ser positivo");
    }

    @Test
    @DisplayName("calculateSizeFromAmount valida monto positivo")
    void rejectsNonPositiveAmount() {
        assertThatThrownBy(() -> OrderMatcher.calculateSizeFromAmount(Money.of("0"), new BigDecimal("10")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Monto debe ser positivo");
    }

    @Test
    @DisplayName("validateLimitOrder acepta MARKET sin precio")
    void acceptsMarketWithoutPrice() {
        assertThatCode(() -> OrderMatcher.validateLimitOrder(
                command(OrderType.MARKET, OrderSide.BUY, 10L, null, null)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validateLimitOrder rechaza LIMIT sin precio")
    void rejectsLimitWithoutPrice() {
        PlaceOrderPort.Command command = mock(PlaceOrderPort.Command.class);
        when(command.type()).thenReturn(OrderType.LIMIT);
        when(command.price()).thenReturn(null);

        assertThatThrownBy(() -> OrderMatcher.validateLimitOrder(command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Precio obligatorio");
    }

    @Test
    @DisplayName("validateLimitOrder rechaza LIMIT con precio cero")
    void rejectsLimitWithZeroPrice() {
        assertThatThrownBy(() -> OrderMatcher.validateLimitOrder(
                command(OrderType.LIMIT, OrderSide.BUY, 10L, BigDecimal.ZERO, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("validateLimitOrder rechaza LIMIT con precio negativo")
    void rejectsLimitWithNegativePrice() {
        assertThatThrownBy(() -> OrderMatcher.validateLimitOrder(
                command(OrderType.LIMIT, OrderSide.BUY, 10L, new BigDecimal("-10"), null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("validateLimitOrder acepta LIMIT con precio positivo")
    void acceptsLimitWithPositivePrice() {
        assertThatCode(() -> OrderMatcher.validateLimitOrder(
                command(OrderType.LIMIT, OrderSide.BUY, 10L, new BigDecimal("100"), null)))
                .doesNotThrowAnyException();
    }

    private PlaceOrderPort.Command command(
            OrderType type,
            OrderSide side,
            Long size,
            BigDecimal price,
            BigDecimal amount) {
        return new PlaceOrderPort.Command(
                USER_ID,
                GGAL_ID,
                side,
                type,
                price != null ? Money.of(price) : null,
                size,
                amount != null ? Money.of(amount) : null,
                "key-1"
        );
    }

    private MarketPrice price(String close, String previousClose) {
        return new MarketPrice(new BigDecimal(close), new BigDecimal(previousClose), Instant.now());
    }
}
