package ar.com.cocos.challenge.domain.service;

import ar.com.cocos.challenge.domain.model.Instrument;
import ar.com.cocos.challenge.domain.model.InstrumentPriceNotFoundException;
import ar.com.cocos.challenge.domain.model.MarketPrice;
import ar.com.cocos.challenge.domain.model.Money;
import ar.com.cocos.challenge.domain.model.Order;
import ar.com.cocos.challenge.domain.model.OrderType;
import ar.com.cocos.challenge.domain.port.in.PlaceOrderPort;
import ar.com.cocos.challenge.domain.port.out.MarketDataProviderPort;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Servicio de dominio para crear y matchear órdenes.
 * Lógica pura de negocio para órdenes MARKET y LIMIT.
 */
public class OrderMatcher {

    private final MarketDataProviderPort marketDataProvider;

    public OrderMatcher(MarketDataProviderPort marketDataProvider) {
        this.marketDataProvider = marketDataProvider;
    }

    /**
     * Crea una orden a partir del comando, calculando size si viene amount.
     */
    public Order createOrder(PlaceOrderPort.Command command, Instrument instrument) {
        Long size = command.size();

        // Si viene amount, calcular size = floor(amount / price)
        if (command.hasAmount()) {
            BigDecimal price = resolveExecutionPrice(command);
            size = calculateSizeFromAmount(command.amount(), price);
        }

        if (size == null || size <= 0) {
            throw new IllegalArgumentException("Tamaño de orden inválido: " + size);
        }

        if (command.type() == OrderType.MARKET) {
            return Order.newMarketOrder(
                    command.instrumentId(),
                    command.userId(),
                    command.side(),
                    size,
                    command.idempotencyKey()
            );
        } else {
            return Order.newLimitOrder(
                    command.instrumentId(),
                    command.userId(),
                    command.side(),
                    size,
                    command.price().amount(),
                    command.idempotencyKey()
            );
        }
    }

    /**
     * Ejecuta una orden MARKET inmediatamente usando el precio close actual.
     */
    public Order executeMarketOrder(Order order, Instrument instrument) {
        if (!order.isMarket()) {
            throw new IllegalStateException("Solo órdenes MARKET pueden ejecutarse inmediatamente");
        }

        MarketPrice price = marketDataProvider.findLatestPrice(order.instrumentId())
                .orElseThrow(() -> new InstrumentPriceNotFoundException(order.instrumentId()));

        return order.filled(price.close(), order.size());
    }

    /**
     * Calcula el precio de ejecución para una orden.
     * MARKET: usa close actual. LIMIT: usa precio especificado.
     *
     * Público porque la validación de saldo necesita el mismo precio de
     * referencia antes de ejecutar (una orden MARKET no trae precio en el request).
     */
    public BigDecimal resolveExecutionPrice(PlaceOrderPort.Command command) {
        if (command.type() == OrderType.MARKET) {
            MarketPrice price = marketDataProvider.findLatestPrice(command.instrumentId())
                    .orElseThrow(() -> new InstrumentPriceNotFoundException(command.instrumentId()));
            return price.close();
        }
        return command.price().amount();
    }

    /**
     * Calcula la cantidad máxima de acciones enteras que se pueden comprar/vender
     * con un monto dado: floor(amount / price).
     * No se admiten fracciones de acciones.
     */
    public static long calculateSizeFromAmount(Money amount, BigDecimal price) {
        if (price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Precio debe ser positivo");
        }
        if (amount.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Monto debe ser positivo");
        }

        BigDecimal sizeDecimal = amount.amount().divide(price, 0, RoundingMode.DOWN);
        long size = sizeDecimal.longValue();

        if (size <= 0) {
            throw new IllegalArgumentException("Monto insuficiente para comprar al menos 1 acción");
        }

        return size;
    }

    /**
     * Valida que una orden LIMIT tenga precio especificado.
     */
    public static void validateLimitOrder(PlaceOrderPort.Command command) {
        if (command.type() == OrderType.LIMIT && command.price() == null) {
            throw new IllegalArgumentException("Precio obligatorio para órdenes LIMIT");
        }
    }
}