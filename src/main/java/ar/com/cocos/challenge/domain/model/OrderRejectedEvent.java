package ar.com.cocos.challenge.domain.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Evento: Orden rechazada (estado REJECTED).
 */
public record OrderRejectedEvent(
    Long orderId,
    Long userId,
    Long instrumentId,
    OrderSide side,
    OrderType type,
    Long size,
    BigDecimal price,
    String rejectionReason,
    Instant timestamp
) {
    public static OrderRejectedEvent from(Order order) {
        return new OrderRejectedEvent(
                order.id(),
                order.userId(),
                order.instrumentId(),
                order.side(),
                order.type(),
                order.size(),
                order.price(),
                order.rejectionReason(),
                order.datetime()
        );
    }
}