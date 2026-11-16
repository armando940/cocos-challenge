package ar.com.cocos.challenge.unit.infrastructure;

import ar.com.cocos.challenge.domain.model.MarketPrice;
import ar.com.cocos.challenge.infrastructure.adapter.out.persistence.MarketDataEntity;
import ar.com.cocos.challenge.infrastructure.adapter.out.persistence.PersistenceMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests POJO puros del {@link PersistenceMapper} para el mapeo de {@code MarketData}.
 *
 * <p>El esquema legacy permite {@code datetime} en NULL; el mapper debe fallar con un mensaje
 * accionable en lugar del {@code NullPointerException} críptico de {@link MarketPrice}.</p>
 */
class PersistenceMapperTest {

    @Test
    @DisplayName("CA-02: MarketData sin datetime lanza IllegalStateException con el id de la fila")
    void throwsActionableExceptionWhenDatetimeIsNull() {
        // Given: una fila con datetime NULL (close/previousClose válidos)
        MarketDataEntity entity = new MarketDataEntity();
        entity.setId(42L);
        entity.setClose(new BigDecimal("20.50"));
        entity.setPreviousClose(new BigDecimal("20.00"));
        entity.setDatetime(null);

        // When / Then: el mapper valida explícitamente y no llega a construir el MarketPrice
        assertThatThrownBy(() -> PersistenceMapper.toDomain(entity))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("datetime")
                .hasMessageContaining("42");
    }

    @Test
    @DisplayName("CA-03: MarketData completa se mapea a MarketPrice con close, previousClose y datetime")
    void mapsCompleteEntityToMarketPrice() {
        // Given: una entidad completa con los datos de mercado seteados
        Instant datetime = Instant.parse("2023-07-13T00:00:00Z");
        MarketDataEntity entity = new MarketDataEntity(
                7L,
                1L,
                null,
                null,
                null,
                new BigDecimal("20.50"),
                new BigDecimal("20.00"),
                datetime,
                null
        );

        // When: se mapea a dominio
        MarketPrice price = PersistenceMapper.toDomain(entity);

        // Then: el precio conserva los valores de la entidad
        assertThat(price.close()).isEqualByComparingTo("20.50");
        assertThat(price.previousClose()).isEqualByComparingTo("20.00");
        assertThat(price.datetime()).isEqualTo(datetime);
    }
}
