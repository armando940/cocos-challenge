package ar.com.cocos.challenge.domain.service;

import ar.com.cocos.challenge.domain.model.Instrument;
import ar.com.cocos.challenge.domain.model.MarketPrice;
import ar.com.cocos.challenge.domain.model.Money;
import ar.com.cocos.challenge.domain.model.Order;
import ar.com.cocos.challenge.domain.model.Portfolio;
import ar.com.cocos.challenge.domain.model.Position;
import ar.com.cocos.challenge.domain.port.out.InstrumentRepositoryPort;
import ar.com.cocos.challenge.domain.port.out.MarketDataProviderPort;
import ar.com.cocos.challenge.domain.port.out.OrderRepositoryPort;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Servicio de dominio para calcular el portfolio de un usuario.
 * Lógica pura, sin dependencias de infraestructura.
 */
public class PortfolioCalculator {

    private final OrderRepositoryPort orderRepository;
    private final InstrumentRepositoryPort instrumentRepository;
    private final MarketDataProviderPort marketDataProvider;

    public PortfolioCalculator(
            OrderRepositoryPort orderRepository,
            InstrumentRepositoryPort instrumentRepository,
            MarketDataProviderPort marketDataProvider) {
        this.orderRepository = orderRepository;
        this.instrumentRepository = instrumentRepository;
        this.marketDataProvider = marketDataProvider;
    }

    public Portfolio calculate(Long userId) {
        // 1. Obtener todas las órdenes FILLED del usuario
        List<Order> filledOrders = orderRepository.findFilledByUserId(userId);

        // 2. Separar operaciones de efectivo (ARS) y de posiciones
        Money availableCash = calculateAvailableCash(filledOrders);
        List<Position> positions = calculatePositions(filledOrders);

        // 3. Valor total = efectivo + valor de posiciones
        Money positionsValue = positions.stream()
                .map(Position::marketValue)
                .reduce(Money.zero(), Money::add);

        Money totalAccountValue = availableCash.add(positionsValue);

        return new Portfolio(userId, totalAccountValue, availableCash, positions);
    }

    /**
     * Calcula el efectivo disponible (ARS) sumando/restando órdenes CASH_IN, CASH_OUT, BUY, SELL.
     * Solo considera el instrumento tipo MONEDA (ARS).
     */
    private Money calculateAvailableCash(List<Order> filledOrders) {
        // Filtrar solo órdenes que afectan efectivo
        return filledOrders.stream()
                .filter(o -> o.isCashOperation() || o.isBuy() || o.isSell())
                .map(this::cashEffect)
                .reduce(Money.zero(), Money::add);
    }

    private Money cashEffect(Order order) {
        // El repositorio solo devuelve órdenes FILLED y el dominio garantiza
        // executedPrice no nulo para ese estado. Se usa el precio real de ejecución,
        // nunca el precio límite original (`price`, null en MARKET).
        Money effect = Money.of(order.executedPrice()).multiply(order.size());

        // CASH_IN suma, CASH_OUT resta
        // BUY resta (gasta pesos), SELL suma (recibe pesos)
        if (order.isCashIn() || order.isSell()) {
            return effect;
        } else {
            return effect.negate();
        }
    }

    /**
     * Calcula las posiciones agrupando órdenes FILLED por instrumento (excluyendo MONEDA).
     */
    private List<Position> calculatePositions(List<Order> filledOrders) {
        // Filtrar solo órdenes que afectan posiciones (BUY/SELL de instrumentos no MONEDA)
        List<Order> positionOrders = filledOrders.stream()
                .filter(Order::affectsPosition)
                .toList();

        if (positionOrders.isEmpty()) {
            return List.of();
        }

        // Agrupar por instrumentId
        Map<Long, List<Order>> ordersByInstrument = positionOrders.stream()
                .collect(Collectors.groupingBy(Order::instrumentId));

        // Obtener precios actuales para todos los instrumentos involucrados
        Set<Long> instrumentIds = ordersByInstrument.keySet();
        Map<Long, MarketPrice> prices = marketDataProvider.findLatestPrices(instrumentIds);

        // Obtener información de instrumentos
        List<Instrument> instruments = instrumentRepository.findAllById(instrumentIds);
        Map<Long, Instrument> instrumentMap = instruments.stream()
                .collect(Collectors.toMap(Instrument::id, i -> i));

        return ordersByInstrument.entrySet().stream()
                .map(entry -> buildPosition(entry.getKey(), entry.getValue(), prices, instrumentMap))
                .filter(p -> p.quantity() > 0) // Solo posiciones con cantidad > 0
                .toList();
    }

    private Position buildPosition(
            Long instrumentId,
            List<Order> orders,
            Map<Long, MarketPrice> prices,
            Map<Long, Instrument> instrumentMap) {

        Instrument instrument = instrumentMap.get(instrumentId);
        if (instrument == null) {
            throw new IllegalStateException("Instrumento no encontrado: " + instrumentId);
        }

        // Calcular cantidad neta: BUY suma, SELL resta
        long netQuantity = orders.stream()
                .mapToLong(o -> o.isBuy() ? o.size() : -o.size())
                .sum();

        if (netQuantity <= 0) {
            return Position.empty(instrumentId, instrument.ticker(), instrument.name());
        }

        // Calcular costo promedio ponderado (Costo Promedio Ponderado: promedio de precios de compra)
        Money totalCost = calculateWeightedAverageCost(orders, netQuantity);

        MarketPrice price = prices.get(instrumentId);
        if (price == null) {
            // Degradación graceful: sin precio de mercado la posición se valora a COSTO HISTÓRICO
            // (totalCost) en lugar de 0. Esto preserva el patrimonio (totalAccountValue),
            // hace unrealizedPnl() = 0 (consistente con totalReturnPct = 0),
            // y el cliente infiere "sin precio" cuando totalReturnPct = 0 y dailyReturnPct = 0.
            return new Position(
                    instrumentId,
                    instrument.ticker(),
                    instrument.name(),
                    netQuantity,
                    totalCost,           // ← marketValue = costo (NO 0)
                    totalCost,           // ← costo real
                    BigDecimal.ZERO,     // ← totalReturnPct = 0 (sin precio, no sé si gané/perdí)
                    BigDecimal.ZERO      // ← dailyReturnPct = 0 (sin precio actual)
            );
        }

        Money marketValue = Money.of(price.close()).multiply(netQuantity);

        // Rendimiento total: (valorMercado - costoTotal) / costoTotal * 100
        BigDecimal totalReturnPct = BigDecimal.ZERO;
        if (totalCost.amount().compareTo(BigDecimal.ZERO) != 0) {
            totalReturnPct = marketValue.amount().subtract(totalCost.amount())
                    .divide(totalCost.amount(), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
        }

        // Rendimiento diario del instrumento
        BigDecimal dailyReturnPct = price.dailyChangePct();

        return new Position(
                instrumentId,
                instrument.ticker(),
                instrument.name(),
                netQuantity,
                marketValue,
                totalCost,
                totalReturnPct,
                dailyReturnPct
        );
    }

    /**
     * Calcula el costo promedio ponderado de las compras (Costo Promedio Ponderado).
     * Solo considera órdenes BUY para el costo.
     */
    private Money calculateWeightedAverageCost(List<Order> orders, long netQuantity) {
        // Solo las compras FILLED aportan costo.
        List<Order> buyOrders = orders.stream()
                .filter(Order::isBuy)
                .toList();

        if (buyOrders.isEmpty()) {
            return Money.zero();
        }

        // Costo total = suma(size * executedPrice) de todas las compras
        BigDecimal totalCost = buyOrders.stream()
                .map(o -> o.executedPrice().multiply(BigDecimal.valueOf(o.size())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Promedio ponderado = costoTotal / cantidadTotalComprada
        long totalBought = buyOrders.stream().mapToLong(Order::size).sum();
        BigDecimal avgPrice = totalCost.divide(BigDecimal.valueOf(totalBought), 4, RoundingMode.HALF_UP);

        // Costo de la posición actual = avgPrice * netQuantity
        return Money.of(avgPrice.multiply(BigDecimal.valueOf(netQuantity)));
    }
}