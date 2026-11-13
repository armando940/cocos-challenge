package ar.com.cocos.challenge.domain.model;

/**
 * Excepción cuando el usuario no tiene acciones suficientes para una venta.
 */
public class InsufficientSharesException extends DomainException {

    private final long available;
    private final long required;

    public InsufficientSharesException(long available, long required) {
        super("INSUFFICIENT_SHARES",
            String.format("Acciones disponibles: %d, Requeridas: %d", available, required));
        this.available = available;
        this.required = required;
    }

    public long getAvailable() {
        return available;
    }

    public long getRequired() {
        return required;
    }
}