package ar.com.cocos.challenge.domain.model;

/**
 * Excepción cuando hay conflicto de idempotencia (mismo key, diferente request).
 */
public class IdempotencyConflictException extends DomainException {

    private final String key;

    public IdempotencyConflictException(String key) {
        super("IDEMPOTENCY_CONFLICT",
            "Clave de idempotencia ya usada con request diferente: " + key);
        this.key = key;
    }

    public String getKey() {
        return key;
    }
}