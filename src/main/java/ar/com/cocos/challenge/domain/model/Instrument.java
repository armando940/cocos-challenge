package ar.com.cocos.challenge.domain.model;

import java.util.Objects;

/**
 * Instrumento financiero: acción, bono, moneda, etc.
 */
public record Instrument(
    Long id,
    String ticker,
    String name,
    InstrumentType type
) {
    public Instrument {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(ticker, "ticker cannot be null");
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(type, "type cannot be null");
    }

    public boolean isCurrency() {
        return type == InstrumentType.MONEDA;
    }

    public boolean isTradable() {
        return type != InstrumentType.MONEDA;
    }
}