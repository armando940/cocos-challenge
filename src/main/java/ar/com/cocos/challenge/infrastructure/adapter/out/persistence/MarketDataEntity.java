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
 *
 * <p>El esquema legacy de produccion ({@code V1__baseline_prod_schema.sql}) permite NULL en
 * {@code high}, {@code low} y {@code open}: el dump historico trae esos campos en NULL cuando no
 * hubo operacion para el instrumento en esa rueda. Por eso esos tres campos se mapean como
 * opcionales.</p>
 *
 * <p>{@code datetime} es nullable en el esquema legacy: el dump historico puede traer filas sin
 * timestamp. La entidad lo hidrata sin error; el adaptador filtra esas filas antes de mapearlas a
 * dominio.</p>
 *
 * <p>En cambio, {@code close} y {@code previousClose} siguen siendo obligatorios porque el modelo
 * de dominio {@code MarketPrice} los exige no nulos ({@code Objects.requireNonNull}) al construirse;
 * sin ellos no hay precio de mercado utilizable.</p>
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

    @Column(precision = 18, scale = 4)
    private BigDecimal high;

    @Column(precision = 18, scale = 4)
    private BigDecimal low;

    @Column(precision = 18, scale = 4)
    private BigDecimal open;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal close;

    @Column(name = "previous_close", nullable = false, precision = 18, scale = 4)
    private BigDecimal previousClose;

    @Column
    private Instant datetime;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}