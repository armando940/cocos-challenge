package ar.com.cocos.challenge.domain.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Evento: Orden ejecutada (estado FILLED).
 */
public record OrderFilledEvent(
    Long orderId,
    Long userId,
    Long instrumentId,
    OrderSide side,
    OrderType type,
    Long size,
    BigDecimal executedPrice,
    Long executedSize,
    Instant timestamp
) {
    public static OrderFilledEvent from(Order order) {
        return new OrderFilledEvent(
                order.id(),
                order.userId(),
                order.instrumentId(),
                order.side(),
                order.type(),
                order.size(),
                order.executedPrice(),
                order.executedSize(),
                order.datetime()
        );
    }
}