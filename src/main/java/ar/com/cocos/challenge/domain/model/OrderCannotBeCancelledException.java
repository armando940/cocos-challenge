package ar.com.cocos.challenge.domain.model;

/**
 * Excepción cuando se intenta cancelar una orden que no está en estado NEW.
 */
public class OrderCannotBeCancelledException extends DomainException {

    private final Long orderId;
    private final OrderStatus currentStatus;

    public OrderCannotBeCancelledException(Long orderId, OrderStatus currentStatus) {
        super("ORDER_CANNOT_BE_CANCELLED",
            String.format("Solo órdenes en estado NEW pueden cancelarse. Orden %d está en %s", orderId, currentStatus));
        this.orderId = orderId;
        this.currentStatus = currentStatus;
    }

    public Long getOrderId() {
        return orderId;
    }

    public OrderStatus getCurrentStatus() {
        return currentStatus;
    }
}