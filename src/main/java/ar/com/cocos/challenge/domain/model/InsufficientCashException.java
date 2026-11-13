package ar.com.cocos.challenge.domain.model;

import ar.com.cocos.challenge.domain.model.Money;

/**
 * Excepción cuando el usuario no tiene pesos suficientes para una compra.
 */
public class InsufficientCashException extends DomainException {

    private final Money available;
    private final Money required;

    public InsufficientCashException(Money available, Money required) {
        super("INSUFFICIENT_CASH",
            String.format("Pesos disponibles: %s, Requeridos: %s", available, required));
        this.available = available;
        this.required = required;
    }

    public Money getAvailable() {
        return available;
    }

    public Money getRequired() {
        return required;
    }
}