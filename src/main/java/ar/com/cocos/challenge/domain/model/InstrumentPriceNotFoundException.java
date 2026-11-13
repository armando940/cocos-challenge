package ar.com.cocos.challenge.domain.model;

/**
 * Excepción cuando no se encuentra precio para un instrumento.
 */
public class InstrumentPriceNotFoundException extends DomainException {

    private final Long instrumentId;

    public InstrumentPriceNotFoundException(Long instrumentId) {
        super("PRICE_NOT_FOUND", "Precio no disponible para instrumento: " + instrumentId);
        this.instrumentId = instrumentId;
    }

    public Long getInstrumentId() {
        return instrumentId;
    }
}