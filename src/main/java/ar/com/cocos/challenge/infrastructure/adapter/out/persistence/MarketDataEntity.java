package ar.com.cocos.challenge.infrastructure.adapter.out.persistence;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Entidad JPA para MarketData.
 */
@Entity
@Table(name = "marketdata", indexes = {
        @Index(name = "idx_marketdata_instrument_datetime", columnList = "instrument_id, datetime DESC"),
        @Index(name = "idx_marketdata_datetime", columnList = "datetime DESC")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MarketDataEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "instrument_id", nullable = false)
    private Long instrumentId;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal high;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal low;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal open;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal close;

    @Column(name = "previous_close", nullable = false, precision = 18, scale = 4)
    private BigDecimal previousClose;

    @Column(nullable = false)
    private Instant datetime;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}