package ar.com.cocos.challenge.infrastructure.adapter.out.persistence;

import ar.com.cocos.challenge.domain.model.OrderSide;
import ar.com.cocos.challenge.domain.model.OrderStatus;
import ar.com.cocos.challenge.domain.model.OrderType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Entidad JPA para Order.
 */
@Entity
@Table(name = "orders", indexes = {
        @Index(name = "idx_orders_user_status_datetime", columnList = "user_id, status, datetime"),
        @Index(name = "idx_orders_instrument_status", columnList = "instrument_id, status"),
        @Index(name = "idx_orders_idempotency_key", columnList = "idempotency_key"),
        @Index(name = "idx_orders_user_instrument_status", columnList = "user_id, instrument_id, status"),
        @Index(name = "idx_orders_user_side_status", columnList = "user_id, side, status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OrderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "instrument_id", nullable = false)
    private Long instrumentId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private OrderSide side;

    @Column(nullable = false)
    private Long size;

    @Column(precision = 18, scale = 4)
    private BigDecimal price;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private OrderType type;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    @Column(nullable = false)
    private Instant datetime;

    @Column(name = "executed_price", precision = 18, scale = 4)
    private BigDecimal executedPrice;

    @Column(name = "executed_size")
    private Long executedSize;

    @Column(name = "rejection_reason", length = 100)
    private String rejectionReason;

    @Column(name = "idempotency_key", length = 100, unique = true)
    private String idempotencyKey;

    @Column(name = "request_hash", length = 64)
    private String requestHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        if (datetime == null) {
            datetime = Instant.now();
        }
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}