package ar.com.cocos.challenge.domain.port.in;

import ar.com.cocos.challenge.domain.model.Money;
import ar.com.cocos.challenge.domain.model.Order;
import ar.com.cocos.challenge.domain.model.OrderSide;
import ar.com.cocos.challenge.domain.model.OrderStatus;
import ar.com.cocos.challenge.domain.model.OrderType;

/**
 * Puerto de entrada para enviar órdenes.
 */
public interface PlaceOrderPort {

    /**
     * Comando para colocar una orden.
     */
    record Command(
        Long userId,
        Long instrumentId,
        OrderSide side,
        OrderType type,
        Money price,           // obligatorio si LIMIT
        Long size,             // XOR con amount
        Money amount,          // XOR con size
        String idempotencyKey  // obligatorio
    ) {
        public Command {
            if (size == null && amount == null) {
                throw new IllegalArgumentException("Debe proporcionar size o amount");
            }
            if (size != null && amount != null) {
                throw new IllegalArgumentException("No puede proporcionar tanto size como amount");
            }
            if (type == OrderType.LIMIT && price == null) {
                throw new IllegalArgumentException("Precio obligatorio para órdenes LIMIT");
            }
            if (idempotencyKey == null || idempotencyKey.isBlank()) {
                throw new IllegalArgumentException("idempotencyKey obligatorio");
            }
        }

        public boolean hasSize() {
            return size != null;
        }

        public boolean hasAmount() {
            return amount != null;
        }
    }

    /**
     * Resultado de colocar una orden.
     */
    record Result(
        Long orderId,
        OrderStatus status,
        Money executedPrice,
        Long executedSize,
        String rejectionReason,
        java.time.Instant datetime
    ) {
        public static Result from(Order order) {
            return new Result(
                order.id(),
                order.status(),
                order.executedPrice() != null ? Money.of(order.executedPrice()) : null,
                order.executedSize(),
                order.rejectionReason(),
                order.datetime()
            );
        }
    }

    Result execute(Command command);
}