package ar.com.cocos.challenge.unit.domain;

import ar.com.cocos.challenge.domain.model.Instrument;
import ar.com.cocos.challenge.domain.model.InstrumentPriceNotFoundException;
import ar.com.cocos.challenge.domain.model.InstrumentType;
import ar.com.cocos.challenge.domain.model.InsufficientCashException;
import ar.com.cocos.challenge.domain.model.InsufficientSharesException;
import ar.com.cocos.challenge.domain.model.MarketPrice;
import ar.com.cocos.challenge.domain.model.Money;
import ar.com.cocos.challenge.domain.model.Order;
import ar.com.cocos.challenge.domain.model.OrderSide;
import ar.com.cocos.challenge.domain.model.Portfolio;
import ar.com.cocos.challenge.domain.port.out.InstrumentRepositoryPort;
import ar.com.cocos.challenge.domain.port.out.MarketDataProviderPort;
import ar.com.cocos.challenge.domain.port.out.OrderRepositoryPort;
import ar.com.cocos.challenge.domain.service.BalanceValidator;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BalanceValidatorTest {

    private static final Long USER_ID = 1L;
    private static final Long GGAL_ID = 2L;
    private static final Long ARS_ID = 1L;

    @Mock
    private OrderRepositoryPort orderRepository;

    @Mock
    private InstrumentRepositoryPort instrumentRepository;

    @Mock
    private MarketDataProviderPort marketDataProvider;

    @Mock
    private PortfolioCalculator portfolioCalculator;

    private BalanceValidator validator;
    private Instrument ggal;
    private Instrument ars;

    @BeforeEach
    void setUp() {
        validator = new BalanceValidator(orderRepository, instrumentRepository, marketDataProvider, portfolioCalculator);
        ggal = new Instrument(GGAL_ID, "GGAL", "Grupo Financiero Galicia", InstrumentType.ACCION);
        ars = new Instrument(ARS_ID, "ARS", "Peso Argentino", InstrumentType.MONEDA);
    }

    @Test
    @DisplayName("BUY con saldo suficiente pasa")
    void buyWithEnoughCashPasses() {
        stubCash("100000");

        assertThatCode(() -> validator.validate(USER_ID, OrderSide.BUY, 10, new BigDecimal("5000"), ggal))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("BUY sin saldo suficiente lanza InsufficientCashException")
    void buyWithoutEnoughCashFails() {
        stubCash("10000");

        assertThatThrownBy(() -> validator.validate(USER_ID, OrderSide.BUY, 10, new BigDecimal("5000"), ggal))
                .isInstanceOf(InsufficientCashException.class)
                .satisfies(ex -> {
                    InsufficientCashException cash = (InsufficientCashException) ex;
                    assertThat(cash.getCode()).isEqualTo("INSUFFICIENT_CASH");
                    assertThat(cash.getAvailable().amount()).isEqualByComparingTo("10000");
                    assertThat(cash.getRequired().amount()).isEqualByComparingTo("50000");
                });
    }

    @Test
    @DisplayName("CASH_OUT valida el saldo igual que una compra")
    void cashOutWithoutEnoughCashFails() {
        stubCash("100");

        assertThatThrownBy(() -> validator.validate(USER_ID, OrderSide.CASH_OUT, 500, new BigDecimal("1"), ars))
                .isInstanceOf(InsufficientCashException.class);
    }

    @Test
    @DisplayName("CASH_IN no requiere validación")
    void cashInSkipsValidation() {
        assertThatCode(() -> validator.validate(USER_ID, OrderSide.CASH_IN, 1000, new BigDecimal("1"), ars))
                .doesNotThrowAnyException();
        verifyNoInteractions(portfolioCalculator, orderRepository, instrumentRepository, marketDataProvider);
    }

    @Test
    @DisplayName("SELL con tenencia suficiente pasa")
    void sellWithEnoughSharesPasses() {
        when(orderRepository.findFilledByUserIdAndInstrumentId(USER_ID, GGAL_ID))
                .thenReturn(List.of(
                        filled(OrderSide.BUY, 100),
                        filled(OrderSide.SELL, 30)
                ));

        assertThatCode(() -> validator.validate(USER_ID, OrderSide.SELL, 50, new BigDecimal("11200"), ggal))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("SELL sin tenencia suficiente lanza InsufficientSharesException")
    void sellWithoutEnoughSharesFails() {
        when(orderRepository.findFilledByUserIdAndInstrumentId(USER_ID, GGAL_ID))
                .thenReturn(List.of(
                        filled(OrderSide.BUY, 100),
                        filled(OrderSide.SELL, 30)
                ));

        assertThatThrownBy(() -> validator.validate(USER_ID, OrderSide.SELL, 100, new BigDecimal("11200"), ggal))
                .isInstanceOf(InsufficientSharesException.class)
                .satisfies(ex -> {
                    InsufficientSharesException shares = (InsufficientSharesException) ex;
                    assertThat(shares.getCode()).isEqualTo("INSUFFICIENT_SHARES");
                    assertThat(shares.getAvailable()).isEqualTo(70);
                    assertThat(shares.getRequired()).isEqualTo(100);
                });
    }

    @Test
    @DisplayName("SELL sin órdenes previas lanza InsufficientSharesException")
    void sellWithoutOrdersFails() {
        when(orderRepository.findFilledByUserIdAndInstrumentId(USER_ID, GGAL_ID)).thenReturn(List.of());

        assertThatThrownBy(() -> validator.validate(USER_ID, OrderSide.SELL, 1, new BigDecimal("11200"), ggal))
                .isInstanceOf(InsufficientSharesException.class)
                .satisfies(ex -> assertThat(((InsufficientSharesException) ex).getAvailable()).isZero());
    }

    @Test
    @DisplayName("BUY sin precio lanza IllegalArgumentException")
    void buyWithoutPriceFails() {
        assertThatThrownBy(() -> validator.validate(USER_ID, OrderSide.BUY, 10, null, ggal))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("precio");
        verifyNoInteractions(portfolioCalculator);
    }

    @Test
    @DisplayName("validateMarketOrderExecution pasa si hay precio")
    void validatesMarketExecutionWithPrice() {
        when(marketDataProvider.findLatestPrice(GGAL_ID))
                .thenReturn(Optional.of(new MarketPrice(
                        new BigDecimal("11200"), new BigDecimal("10800"), Instant.now())));

        assertThatCode(() -> validator.validateMarketOrderExecution(GGAL_ID))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validateMarketOrderExecution falla si no hay precio")
    void validatesMarketExecutionWithoutPrice() {
        when(marketDataProvider.findLatestPrice(GGAL_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> validator.validateMarketOrderExecution(GGAL_ID))
                .isInstanceOf(InstrumentPriceNotFoundException.class);
    }

    private void stubCash(String availableCash) {
        when(portfolioCalculator.calculate(USER_ID))
                .thenReturn(new Portfolio(
                        USER_ID,
                        Money.of(availableCash),
                        Money.of(availableCash),
                        List.of()));
    }

    private Order filled(OrderSide side, long size) {
        return new Order(
                1L,
                GGAL_ID,
                USER_ID,
                side,
                size,
                new BigDecimal("10000"),
                ar.com.cocos.challenge.domain.model.OrderType.MARKET,
                ar.com.cocos.challenge.domain.model.OrderStatus.FILLED,
                Instant.now(),
                new BigDecimal("10000"),
                size,
                null,
                "key-" + side + "-" + size
        );
    }
}
