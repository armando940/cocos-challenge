package ar.com.cocos.challenge.domain.service;

import ar.com.cocos.challenge.domain.model.Instrument;
import ar.com.cocos.challenge.domain.model.InstrumentPriceNotFoundException;
import ar.com.cocos.challenge.domain.model.InsufficientCashException;
import ar.com.cocos.challenge.domain.model.InsufficientSharesException;
import ar.com.cocos.challenge.domain.model.Money;
import ar.com.cocos.challenge.domain.model.Order;
import ar.com.cocos.challenge.domain.model.OrderSide;
import ar.com.cocos.challenge.domain.model.Portfolio;
import ar.com.cocos.challenge.domain.port.out.InstrumentRepositoryPort;
import ar.com.cocos.challenge.domain.port.out.MarketDataProviderPort;
import ar.com.cocos.challenge.domain.port.out.OrderRepositoryPort;

import java.math.BigDecimal;
import java.util.List;

/**
 * Servicio de dominio para validar saldo y tenencia antes de ejecutar órdenes.
 * Lógica pura de validación financiera.
 */
public class BalanceValidator {

    private final OrderRepositoryPort orderRepository;
    private final InstrumentRepositoryPort instrumentRepository;
    private final MarketDataProviderPort marketDataProvider;
    private final PortfolioCalculator portfolioCalculator;

    public BalanceValidator(
            OrderRepositoryPort orderRepository,
            InstrumentRepositoryPort instrumentRepository,
            MarketDataProviderPort marketDataProvider,
            PortfolioCalculator portfolioCalculator) {
        this.orderRepository = orderRepository;
        this.instrumentRepository = instrumentRepository;
        this.marketDataProvider = marketDataProvider;
        this.portfolioCalculator = portfolioCalculator;
    }

    /**
     * Valida que el usuario tenga saldo/tenencia suficiente para la orden.
     * Lanza excepción si no cumple.
     */
    public void validate(Long userId, OrderSide side, long size, BigDecimal price, Instrument instrument) {
        if (side == OrderSide.BUY || side == OrderSide.CASH_OUT) {
            validateCashAvailability(userId, size, price, side);
        } else if (side == OrderSide.SELL) {
            validateShareAvailability(userId, instrument.id(), size);
        }
        // CASH_IN no requiere validación (es ingreso de fondos)
    }

    /**
     * Valida que el usuario tenga pesos disponibles suficientes.
     * BUY y CASH_OUT: availableCash >= size * price
     * (para el instrumento ARS el precio es 1.00, así que `size` es el monto).
     */
    private void validateCashAvailability(Long userId, long size, BigDecimal price, OrderSide side) {
        if (price == null) {
            throw new IllegalArgumentException("Se requiere precio para validar el saldo disponible");
        }

        Portfolio portfolio = portfolioCalculator.calculate(userId);
        Money availableCash = portfolio.availableCash();
        Money required = Money.of(price).multiply(size);

        if (availableCash.isLessThan(required)) {
            throw new InsufficientCashException(availableCash, required);
        }
    }

    /**
     * Valida que el usuario tenga acciones suficientes para vender.
     * availableShares = sum(BUY.size) - sum(SELL.size) de órdenes FILLED.
     */
    private void validateShareAvailability(Long userId, Long instrumentId, long size) {
        List<Order> filledOrders = orderRepository.findFilledByUserIdAndInstrumentId(userId, instrumentId);

        long availableShares = filledOrders.stream()
                .filter(o -> o.affectsPosition())
                .mapToLong(o -> o.isBuy() ? o.size() : -o.size())
                .sum();

        if (availableShares < size) {
            throw new InsufficientSharesException(availableShares, size);
        }
    }

    /**
     * Valida que una orden MARKET pueda ejecutarse (precio disponible).
     */
    public void validateMarketOrderExecution(Long instrumentId) {
        if (!marketDataProvider.findLatestPrice(instrumentId).isPresent()) {
            throw new InstrumentPriceNotFoundException(instrumentId);
        }
    }
}