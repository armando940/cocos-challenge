package ar.com.cocos.challenge.domain.model;

/**
 * Excepción cuando el instrumento no existe.
 */
public class InstrumentNotFoundException extends DomainException {

    private final Long instrumentId;

    public InstrumentNotFoundException(Long instrumentId) {
        super("INSTRUMENT_NOT_FOUND", "Instrumento no encontrado: " + instrumentId);
        this.instrumentId = instrumentId;
    }

    public Long getInstrumentId() {
        return instrumentId;
    }
}