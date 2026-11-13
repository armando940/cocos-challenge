package ar.com.cocos.challenge.domain.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Evento: Orden colocada (estado NEW).
 */
public record OrderPlacedEvent(
    Long orderId,
    Long userId,
    Long instrumentId,
    OrderSide side,
    OrderType type,
    Long size,
    BigDecimal price,
    Instant timestamp
) {
    public static OrderPlacedEvent from(Order order) {
        return new OrderPlacedEvent(
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