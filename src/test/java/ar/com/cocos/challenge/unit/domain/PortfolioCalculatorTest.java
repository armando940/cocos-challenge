package ar.com.cocos.challenge.unit.domain;

import ar.com.cocos.challenge.domain.model.Instrument;
import ar.com.cocos.challenge.domain.model.InstrumentType;
import ar.com.cocos.challenge.domain.model.MarketPrice;
import ar.com.cocos.challenge.domain.model.Order;
import ar.com.cocos.challenge.domain.model.OrderSide;
import ar.com.cocos.challenge.domain.model.OrderStatus;
import ar.com.cocos.challenge.domain.model.OrderType;
import ar.com.cocos.challenge.domain.model.Portfolio;
import ar.com.cocos.challenge.domain.model.Position;
import ar.com.cocos.challenge.domain.port.out.InstrumentRepositoryPort;
import ar.com.cocos.challenge.domain.port.out.MarketDataProviderPort;
import ar.com.cocos.challenge.domain.port.out.OrderRepositoryPort;
import ar.com.cocos.challenge.domain.service.PortfolioCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioCalculatorTest {

    private static final Long USER_ID = 1L;
    private static final Long GGAL_ID = 2L;
    private static final Long ARS_ID = 1L;

    @Mock
    private OrderRepositoryPort orderRepository;

    @Mock
    private InstrumentRepositoryPort instrumentRepository;

    @Mock
    private MarketDataProviderPort marketDataProvider;

    private PortfolioCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new PortfolioCalculator(orderRepository, instrumentRepository, marketDataProvider);
    }

    @Test
    @DisplayName("Calcula efectivo, posiciones y rendimientos con órdenes FILLED")
    void calculatesCashPositionsAndReturns() {
        List<Order> orders = List.of(
                filled(1L, ARS_ID, OrderSide.CASH_IN, 5_000_000, "1.00"),
                filled(2L, GGAL_ID, OrderSide.BUY, 100, "10000"),
                filled(3L, GGAL_ID, OrderSide.SELL, 40, "12000"),
                filled(4L, ARS_ID, OrderSide.CASH_OUT, 100_000, "1.00")
        );
        stubFilledOrders(orders);
        stubPrice(GGAL_ID, "11000", "10000");
        stubInstrument(GGAL_ID, "GGAL", "Grupo Financiero Galicia");

        Portfolio portfolio = calculator.calculate(USER_ID);

        // 5.000.000 - 1.000.000 (BUY) + 480.000 (SELL) - 100.000 (CASH_OUT)
        assertThat(portfolio.availableCash().amount()).isEqualByComparingTo("4380000");
        assertThat(portfolio.totalAccountValue().amount()).isEqualByComparingTo("5040000");
        assertThat(portfolio.positionsCount()).isEqualTo(1);

        Position position = portfolio.positions().get(0);
        assertThat(position.instrumentId()).isEqualTo(GGAL_ID);
        assertThat(position.ticker()).isEqualTo("GGAL");
        assertThat(position.quantity()).isEqualTo(60);
        assertThat(position.marketValue().amount()).isEqualByComparingTo("660000");
        assertThat(position.totalCost().amount()).isEqualByComparingTo("600000");
        assertThat(position.totalReturnPct()).isEqualByComparingTo("10.0000");
        assertThat(position.dailyReturnPct()).isEqualByComparingTo("10.0000");
        assertThat(position.unrealizedPnl().amount()).isEqualByComparingTo("60000");
    }

    @Test
    @DisplayName("Portfolio con sólo operaciones de efectivo no consulta mercado")
    void calculatesCashOnlyPortfolio() {
        stubFilledOrders(List.of(
                filled(1L, ARS_ID, OrderSide.CASH_IN, 1_000, "1.00")
        ));

        Portfolio portfolio = calculator.calculate(USER_ID);

        assertThat(portfolio.availableCash().amount()).isEqualByComparingTo("1000");
        assertThat(portfolio.totalAccountValue().amount()).isEqualByComparingTo("1000");
        assertThat(portfolio.positions()).isEmpty();
        verifyNoInteractions(marketDataProvider, instrumentRepository);
    }

    @Test
    @DisplayName("Una posición cerrada (BUY y SELL por el mismo size) no aparece en el portfolio")
    void filtersFlatPositions() {
        stubFilledOrders(List.of(
                filled(2L, GGAL_ID, OrderSide.BUY, 100, "10000"),
                filled(3L, GGAL_ID, OrderSide.SELL, 100, "12000")
        ));
        stubPrice(GGAL_ID, "11000", "10000");
        stubInstrument(GGAL_ID, "GGAL", "Grupo Financiero Galicia");

        Portfolio portfolio = calculator.calculate(USER_ID);

        assertThat(portfolio.positions()).isEmpty();
        // -1.000.000 + 1.200.000
        assertThat(portfolio.availableCash().amount()).isEqualByComparingTo("200000");
    }

    @Test
    @DisplayName("Usa costo promedio ponderado de todas las compras")
    void calculatesWeightedAverageCost() {
        stubFilledOrders(List.of(
                filled(2L, GGAL_ID, OrderSide.BUY, 100, "1000"),
                filled(3L, GGAL_ID, OrderSide.BUY, 100, "2000"),
                filled(4L, GGAL_ID, OrderSide.SELL, 50, "1500")
        ));
        stubPrice(GGAL_ID, "1600", "1500");
        stubInstrument(GGAL_ID, "GGAL", "Grupo Financiero Galicia");

        Position position = calculator.calculate(USER_ID).positions().get(0);

        // promedio = (100*1000 + 100*2000) / 200 = 1500; 150 acciones * 1500
        assertThat(position.quantity()).isEqualTo(150);
        assertThat(position.totalCost().amount()).isEqualByComparingTo("225000");
        assertThat(position.marketValue().amount()).isEqualByComparingTo("240000");
        assertThat(position.totalReturnPct()).isEqualByComparingTo("6.67");
    }

    @Test
    @DisplayName("Una compra MARKET FILLED con price null y executedPrice seteado aporta costo")
    void usesExecutedPriceWhenMarketBuyHasNullPrice() {
        stubFilledOrders(List.of(
                filled(2L, GGAL_ID, OrderSide.BUY, 10, "100")
        ));
        stubPrice(GGAL_ID, "150", "100");
        stubInstrument(GGAL_ID, "GGAL", "Grupo Financiero Galicia");

        Position position = calculator.calculate(USER_ID).positions().get(0);

        // MARKET: price = null, el costo sale de executedPrice (100)
        assertThat(position.quantity()).isEqualTo(10);
        assertThat(position.totalCost().amount()).isEqualByComparingTo("1000");
        assertThat(position.marketValue().amount()).isEqualByComparingTo("1500");
        assertThat(position.totalReturnPct()).isEqualByComparingTo("50.0000");
    }

    @Test
    @DisplayName("Una orden LIMIT FILLED usa executedPrice y no el precio límite en caja y costo")
    void usesExecutionPriceOverLimitPriceForFilledLimitOrder() {
        Order limitBuy = new Order(
                2L, GGAL_ID, USER_ID, OrderSide.BUY, 10L, new BigDecimal("100.00"),
                OrderType.LIMIT, OrderStatus.FILLED, Instant.parse("2026-11-12T10:00:00Z"),
                new BigDecimal("110.00"), 10L, null, "key-limit-2");
        stubFilledOrders(List.of(limitBuy));
        stubPrice(GGAL_ID, "120.00", "110.00");
        stubInstrument(GGAL_ID, "GGAL", "Grupo Financiero Galicia");

        Portfolio portfolio = calculator.calculate(USER_ID);

        // caja: -10 * 110.00 (ejecución), nunca -10 * 100.00 (límite)
        assertThat(portfolio.availableCash().amount()).isEqualByComparingTo("-1100.00");
        Position position = portfolio.positions().get(0);
        // costo: 10 * 110.00 (ejecución), no el límite de 100.00
        assertThat(position.totalCost().amount()).isEqualByComparingTo("1100.00");
        assertThat(position.marketValue().amount()).isEqualByComparingTo("1200.00");
        assertThat(position.totalReturnPct()).isEqualByComparingTo("9.09");
    }

    @Test
    @DisplayName("Falla si el instrumento de una posición no existe")
    void throwsWhenInstrumentIsMissing() {
        stubFilledOrders(List.of(filled(2L, GGAL_ID, OrderSide.BUY, 10, "1000")));
        stubPrice(GGAL_ID, "1100", "1000");
        when(instrumentRepository.findAllById(Set.of(GGAL_ID))).thenReturn(List.of());

        assertThatThrownBy(() -> calculator.calculate(USER_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Instrumento no encontrado");
    }

    @Test
    @DisplayName("Falla si no hay precio de mercado para una posición")
    void throwsWhenPriceIsMissing() {
        stubFilledOrders(List.of(filled(2L, GGAL_ID, OrderSide.BUY, 10, "1000")));
        when(marketDataProvider.findLatestPrices(Set.of(GGAL_ID))).thenReturn(Map.of());
        stubInstrument(GGAL_ID, "GGAL", "Grupo Financiero Galicia");

        assertThatThrownBy(() -> calculator.calculate(USER_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Precio no disponible");
    }

    private void stubFilledOrders(List<Order> orders) {
        when(orderRepository.findFilledByUserId(USER_ID)).thenReturn(orders);
    }

    private void stubPrice(Long instrumentId, String close, String previousClose) {
        when(marketDataProvider.findLatestPrices(Set.of(instrumentId)))
                .thenReturn(Map.of(instrumentId, new MarketPrice(
                        new BigDecimal(close), new BigDecimal(previousClose), Instant.now())));
    }

    private void stubInstrument(Long instrumentId, String ticker, String name) {
        when(instrumentRepository.findAllById(Set.of(instrumentId)))
                .thenReturn(List.of(new Instrument(instrumentId, ticker, name, InstrumentType.ACCION)));
    }

    private Order filled(Long id, Long instrumentId, OrderSide side, long size, String price) {
        BigDecimal executionPrice = new BigDecimal(price);
        return new Order(
                id,
                instrumentId,
                USER_ID,
                side,
                size,
                null, // MARKET: no tiene precio límite
                OrderType.MARKET,
                OrderStatus.FILLED,
                Instant.parse("2026-11-12T10:00:00Z"),
                executionPrice,
                size,
                null,
                "key-" + id
        );
    }
}
