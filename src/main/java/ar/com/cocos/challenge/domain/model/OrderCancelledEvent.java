package ar.com.cocos.challenge.domain.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Evento: Orden cancelada (estado CANCELLED).
 */
public record OrderCancelledEvent(
    Long orderId,
    Long userId,
    Long instrumentId,
    OrderSide side,
    OrderType type,
    Long size,
    BigDecimal price,
    Instant timestamp
) {
    public static OrderCancelledEvent from(Order order) {
        return new OrderCancelledEvent(
                order.id(),
                order.userId(),
                order.instrumentId(),
                order.side(),
                order.type(),
                order.size(),
                order.price(),
                order.datetime()
        );
    }
}