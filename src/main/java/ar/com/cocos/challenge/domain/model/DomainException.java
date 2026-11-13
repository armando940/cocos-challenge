package ar.com.cocos.challenge.domain.model;

/**
 * Excepción base del dominio.
 */
public abstract class DomainException extends RuntimeException {
    private final String code;

    protected DomainException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}