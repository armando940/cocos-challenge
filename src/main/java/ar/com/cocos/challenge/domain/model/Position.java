package ar.com.cocos.challenge.domain.model;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Posición de un instrumento en el portfolio.
 */
public record Position(
    Long instrumentId,
    String ticker,
    String name,
    Long quantity,           // cantidad neta de acciones (BUY - SELL)
    Money marketValue,       // quantity * close price
    Money totalCost,         // costo promedio ponderado * quantity
    BigDecimal totalReturnPct,  // rendimiento total %
    BigDecimal dailyReturnPct   // rendimiento diario %
) {
    public Position {
        Objects.requireNonNull(instrumentId, "instrumentId cannot be null");
        Objects.requireNonNull(ticker, "ticker cannot be null");
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(quantity, "quantity cannot be null");
        Objects.requireNonNull(marketValue, "marketValue cannot be null");
        Objects.requireNonNull(totalCost, "totalCost cannot be null");
        Objects.requireNonNull(totalReturnPct, "totalReturnPct cannot be null");
        Objects.requireNonNull(dailyReturnPct, "dailyReturnPct cannot be null");
    }

    public boolean isLong() {
        return quantity > 0;
    }

    public boolean isFlat() {
        return quantity == 0;
    }

    public Money unrealizedPnl() {
        return marketValue.subtract(totalCost);
    }

    public static Position empty(Long instrumentId, String ticker, String name) {
        return new Position(instrumentId, ticker, name, 0L, Money.zero(), Money.zero(), BigDecimal.ZERO, BigDecimal.ZERO);
    }
}