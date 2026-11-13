package ar.com.cocos.challenge.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Orden de trading en el dominio.
 * Inmutable - para cambios se crea nueva instancia.
 */
public record Order(
    Long id,
    Long instrumentId,
    Long userId,
    OrderSide side,
    Long size,              // cantidad de acciones (entero)
    BigDecimal price,       // precio por acción (null para MARKET)
    OrderType type,         // MARKET | LIMIT
    OrderStatus status,     // NEW | FILLED | REJECTED | CANCELLED
    Instant datetime,       // timestamp de creación/actualización
    BigDecimal executedPrice, // precio real de ejecución (para FILLED)
    Long executedSize,      // cantidad real ejecutada (para FILLED)
    String rejectionReason, // razón de rechazo (para REJECTED)
    String idempotencyKey   // clave de idempotencia
) {
    public Order {
        Objects.requireNonNull(side, "side cannot be null");
        Objects.requireNonNull(type, "type cannot be null");
        Objects.requireNonNull(status, "status cannot be null");
        Objects.requireNonNull(datetime, "datetime cannot be null");
        if (size != null && size < 0) {
            throw new IllegalArgumentException("size cannot be negative");
        }
    }

    public boolean isMarket() {
        return type == OrderType.MARKET;
    }

    public boolean isLimit() {
        return type == OrderType.LIMIT;
    }

    public boolean isBuy() {
        return side == OrderSide.BUY;
    }

    public boolean isSell() {
        return side == OrderSide.SELL;
    }

    public boolean isCashIn() {
        return side == OrderSide.CASH_IN;
    }

    public boolean isCashOut() {
        return side == OrderSide.CASH_OUT;
    }

    public boolean isFilled() {
        return status == OrderStatus.FILLED;
    }

    public boolean isNew() {
        return status == OrderStatus.NEW;
    }

    public boolean isRejected() {
        return status == OrderStatus.REJECTED;
    }

    public boolean isCancelled() {
        return status == OrderStatus.CANCELLED;
    }

    public boolean isCashOperation() {
        return side == OrderSide.CASH_IN || side == OrderSide.CASH_OUT;
    }

    public boolean affectsCash() {
        return isCashOperation() || isBuy() || isSell();
    }

    public boolean affectsPosition() {
        return !isCashOperation() && (isBuy() || isSell());
    }

    public static Order newMarketOrder(Long instrumentId, Long userId, OrderSide side, Long size, String idempotencyKey) {
        return new Order(
            null, instrumentId, userId, side, size, null, OrderType.MARKET,
            OrderStatus.NEW, Instant.now(), null, null, null, idempotencyKey
        );
    }

    public static Order newLimitOrder(Long instrumentId, Long userId, OrderSide side, Long size, BigDecimal price, String idempotencyKey) {
        return new Order(
            null, instrumentId, userId, side, size, price, OrderType.LIMIT,
            OrderStatus.NEW, Instant.now(), null, null, null, idempotencyKey
        );
    }

    public Order withId(Long id) {
        return new Order(id, instrumentId, userId, side, size, price, type, status, datetime, executedPrice, executedSize, rejectionReason, idempotencyKey);
    }

    public Order filled(BigDecimal executedPrice, Long executedSize) {
        // `price` pasa a guardar el precio real de ejecución: el portfolio
        // calcula el efecto de caja con esta columna.
        return new Order(id, instrumentId, userId, side, size, executedPrice, type, OrderStatus.FILLED, Instant.now(), executedPrice, executedSize, null, idempotencyKey);
    }

    public Order rejected(String reason) {
        return new Order(id, instrumentId, userId, side, size, price, type, OrderStatus.REJECTED, Instant.now(), null, null, reason, idempotencyKey);
    }

    public Order cancelled() {
        if (status != OrderStatus.NEW) {
            throw new IllegalStateException("Only NEW orders can be cancelled");
        }
        return new Order(id, instrumentId, userId, side, size, price, type, OrderStatus.CANCELLED, Instant.now(), null, null, null, idempotencyKey);
    }
}