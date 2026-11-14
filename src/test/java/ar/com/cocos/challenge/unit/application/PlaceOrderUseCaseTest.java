package ar.com.cocos.challenge.unit.application;

import ar.com.cocos.challenge.application.usecase.PlaceOrderUseCase;
import ar.com.cocos.challenge.domain.model.IdempotencyConflictException;
import ar.com.cocos.challenge.domain.model.IdempotencyKey;
import ar.com.cocos.challenge.domain.model.Instrument;
import ar.com.cocos.challenge.domain.model.InstrumentType;
import ar.com.cocos.challenge.domain.model.Order;
import ar.com.cocos.challenge.domain.model.OrderSide;
import ar.com.cocos.challenge.domain.model.OrderStatus;
import ar.com.cocos.challenge.domain.model.OrderType;
import ar.com.cocos.challenge.domain.port.in.PlaceOrderPort;
import ar.com.cocos.challenge.domain.port.out.EventPublisherPort;
import ar.com.cocos.challenge.domain.port.out.IdempotencyStorePort;
import ar.com.cocos.challenge.domain.port.out.InstrumentRepositoryPort;
import ar.com.cocos.challenge.domain.port.out.MarketDataProviderPort;
import ar.com.cocos.challenge.domain.port.out.OrderRepositoryPort;
import ar.com.cocos.challenge.domain.port.out.UserRepositoryPort;
import ar.com.cocos.challenge.domain.service.BalanceValidator;
import ar.com.cocos.challenge.domain.service.OrderMatcher;
import ar.com.cocos.challenge.domain.service.PortfolioCalculator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios del requerimiento 005: el lock pesimista por usuario debe
 * tomarse antes de cualquier lectura/validación que dependa del estado de la
 * cuenta (instrumento, precio y saldo).
 */
@ExtendWith(MockitoExtension.class)
class PlaceOrderUseCaseTest {

    private static final Long USER_ID = 1L;
    private static final Long GGAL_ID = 2L;
    private static final String KEY = "k1";
    private static final BigDecimal REFERENCE_PRICE = new BigDecimal("100.00");

    @Mock
    private OrderRepositoryPort orderRepository;

    @Mock
    private InstrumentRepositoryPort instrumentRepository;

    @Mock
    private UserRepositoryPort userRepositoryPort;

    @Mock
    private IdempotencyStorePort idempotencyStore;

    @Mock
    private BalanceValidator balanceValidator;

    @Mock
    private OrderMatcher orderMatcher;

    @Mock
    private EventPublisherPort eventPublisher;

    @Mock
    private MarketDataProviderPort marketDataProvider;

    private ObjectMapper objectMapper;
    private Instrument ggal;

    @BeforeEach
    void setUp() {
        // ObjectMapper real: el caso de uso serializa el Result (incluye Instant),
        // por eso se registran los módulos disponibles en el classpath.
        objectMapper = new ObjectMapper().findAndRegisterModules();
        ggal = new Instrument(GGAL_ID, "GGAL", "Grupo Financiero Galicia", InstrumentType.ACCION);
    }

    @Test
    @DisplayName("Toma el lock del usuario antes de validar instrumento, precio y saldo")
    void lockSeTomaAntesDeValidarSaldo() {
        PlaceOrderPort.Command command = buyMarketCommand();
        Order newOrder = Order.newMarketOrder(GGAL_ID, USER_ID, OrderSide.BUY, 10L, KEY);
        Order filledOrder = newOrder.withId(100L).filled(REFERENCE_PRICE, 10L);

        when(idempotencyStore.findByKey(KEY)).thenReturn(Optional.empty());
        when(instrumentRepository.findById(GGAL_ID)).thenReturn(Optional.of(ggal));
        when(orderMatcher.createOrder(command, ggal)).thenReturn(newOrder);
        when(orderMatcher.resolveExecutionPrice(command)).thenReturn(REFERENCE_PRICE);
        when(orderRepository.save(any(Order.class)))
                .thenAnswer(invocation -> ((Order) invocation.getArgument(0)).withId(100L));
        when(orderMatcher.executeMarketOrder(any(Order.class), any(Instrument.class), any(BigDecimal.class)))
                .thenReturn(filledOrder);

        PlaceOrderPort.Result result = useCaseWithMockedBalanceValidator().execute(command);

        assertThat(result.status()).isEqualTo(OrderStatus.FILLED);

        InOrder inOrder = inOrder(userRepositoryPort, instrumentRepository, orderMatcher, balanceValidator);
        inOrder.verify(userRepositoryPort).lockById(USER_ID);
        inOrder.verify(instrumentRepository).findById(GGAL_ID);
        inOrder.verify(orderMatcher).resolveExecutionPrice(command);
        inOrder.verify(balanceValidator).validate(USER_ID, OrderSide.BUY, 10L, REFERENCE_PRICE, ggal);
    }

    @Test
    @DisplayName("Un replay idempotente con request distinto no toma lock ni valida saldo")
    void replayIdempotenteNoTomaLock() {
        PlaceOrderPort.Command command = buyMarketCommand();
        Instant now = Instant.now();

        when(idempotencyStore.findByKey(KEY)).thenReturn(Optional.of(
                new IdempotencyKey(KEY, "hash-distinto", "{}", 201, now, now.plusSeconds(3600))));

        PlaceOrderUseCase useCase = useCaseWithMockedBalanceValidator();

        assertThatThrownBy(() -> useCase.execute(command))
                .isInstanceOf(IdempotencyConflictException.class);

        verifyNoInteractions(userRepositoryPort, balanceValidator);
    }

    @Test
    @DisplayName("Toma el lock del usuario antes de leer las órdenes FILLED usadas por el balance")
    void lockSeTomaAntesDeLeerOrdenesFilled() {
        PlaceOrderPort.Command command = buyMarketCommand();
        Order newOrder = Order.newMarketOrder(GGAL_ID, USER_ID, OrderSide.BUY, 10L, KEY);
        Order filledOrder = newOrder.withId(100L).filled(REFERENCE_PRICE, 10L);

        // CASH_IN FILLED de 1.000.000 ARS: deja saldo más que suficiente para
        // comprar 10 acciones a 100.00 (requerido = 1.000).
        Order cashIn = new Order(
                1L,
                1L,
                USER_ID,
                OrderSide.CASH_IN,
                1_000_000L,
                BigDecimal.ONE,
                OrderType.MARKET,
                OrderStatus.FILLED,
                Instant.now(),
                BigDecimal.ONE,
                1_000_000L,
                null,
                "seed"
        );

        when(idempotencyStore.findByKey(KEY)).thenReturn(Optional.empty());
        when(instrumentRepository.findById(GGAL_ID)).thenReturn(Optional.of(ggal));
        when(orderMatcher.createOrder(command, ggal)).thenReturn(newOrder);
        when(orderMatcher.resolveExecutionPrice(command)).thenReturn(REFERENCE_PRICE);
        when(orderRepository.findFilledByUserId(USER_ID)).thenReturn(List.of(cashIn));
        when(orderRepository.save(any(Order.class)))
                .thenAnswer(invocation -> ((Order) invocation.getArgument(0)).withId(100L));
        when(orderMatcher.executeMarketOrder(any(Order.class), any(Instrument.class), any(BigDecimal.class)))
                .thenReturn(filledOrder);

        PortfolioCalculator portfolioCalculator =
                new PortfolioCalculator(orderRepository, instrumentRepository, marketDataProvider);
        BalanceValidator realValidator =
                new BalanceValidator(orderRepository, instrumentRepository, marketDataProvider, portfolioCalculator);

        PlaceOrderUseCase useCase = new PlaceOrderUseCase(
                orderRepository,
                instrumentRepository,
                userRepositoryPort,
                idempotencyStore,
                realValidator,
                orderMatcher,
                eventPublisher,
                objectMapper);

        PlaceOrderPort.Result result = useCase.execute(command);

        assertThat(result.status()).isEqualTo(OrderStatus.FILLED);

        InOrder inOrder = inOrder(userRepositoryPort, orderRepository);
        inOrder.verify(userRepositoryPort).lockById(USER_ID);
        inOrder.verify(orderRepository).findFilledByUserId(USER_ID);
    }

    private PlaceOrderUseCase useCaseWithMockedBalanceValidator() {
        return new PlaceOrderUseCase(
                orderRepository,
                instrumentRepository,
                userRepositoryPort,
                idempotencyStore,
                balanceValidator,
                orderMatcher,
                eventPublisher,
                objectMapper);
    }

    private PlaceOrderPort.Command buyMarketCommand() {
        return new PlaceOrderPort.Command(
                USER_ID,
                GGAL_ID,
                OrderSide.BUY,
                OrderType.MARKET,
                null,
                10L,
                null,
                KEY);
    }
}
