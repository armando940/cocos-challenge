package ar.com.cocos.challenge.domain.model;

/**
 * Excepción cuando la orden no existe.
 */
public class OrderNotFoundException extends DomainException {

    private final Long orderId;

    public OrderNotFoundException(Long orderId) {
        super("ORDER_NOT_FOUND", "Orden no encontrada: " + orderId);
        this.orderId = orderId;
    }

    public Long getOrderId() {
        return orderId;
    }
}