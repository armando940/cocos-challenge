package ar.com.cocos.challenge.domain.model;

/**
 * Tipo de instrumento financiero.
 */
public enum InstrumentType {
    ACCION,
    BONO,
    MONEDA,
    CEDEAR,
    OPCION,
    FUTURO,
    OTRO;

    /** Alias semántico para MONEDA. */
    public static final InstrumentType CURRENCY = MONEDA;
}