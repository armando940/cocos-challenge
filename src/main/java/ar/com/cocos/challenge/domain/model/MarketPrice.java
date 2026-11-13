package ar.com.cocos.challenge.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;

/**
 * Precio de mercado de un instrumento.
 */
public record MarketPrice(
    BigDecimal close,
    BigDecimal previousClose,
    Instant datetime
) {
    public MarketPrice {
        Objects.requireNonNull(close, "close cannot be null");
        Objects.requireNonNull(previousClose, "previousClose cannot be null");
        Objects.requireNonNull(datetime, "datetime cannot be null");
    }

    public BigDecimal dailyChangePct() {
        if (previousClose.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return close.subtract(previousClose)
                .divide(previousClose, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }

    public BigDecimal dailyChange() {
        return close.subtract(previousClose);
    }
}