package ar.com.cocos.challenge.integration;

import ar.com.cocos.challenge.domain.model.InstrumentType;
import ar.com.cocos.challenge.domain.model.MarketPrice;
import ar.com.cocos.challenge.infrastructure.adapter.out.marketdata.MarketDataJpaAdapter;
import ar.com.cocos.challenge.infrastructure.adapter.out.persistence.InstrumentEntity;
import ar.com.cocos.challenge.infrastructure.adapter.out.persistence.InstrumentJpaRepository;
import ar.com.cocos.challenge.infrastructure.adapter.out.persistence.MarketDataEntity;
import ar.com.cocos.challenge.infrastructure.adapter.out.persistence.MarketDataJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test de integración para MarketDataJpaAdapter.findLatestPrices.
 *
 * Valida los criterios de aceptación CA-01 a CA-04 del spec R007:
 * - CA-01: El resultado tiene exactamente 1 entrada por instrumento solicitado
 * - CA-02: Los precios corresponden al close de la fila más reciente (datetime más alto)
 * - CA-03: Llamada con Set vacío devuelve Map vacío
 * - CA-04: Instrumentos sin marketdata no aparecen en el resultado (no lanzan excepción)
 */
@SpringBootTest
@Testcontainers
@TestPropertySource(properties = {
    "spring.jpa.hibernate.ddl-auto=validate",
    "spring.flyway.enabled=true"
})
class MarketDataJpaAdapterIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MarketDataJpaAdapter marketDataAdapter;

    @Autowired
    private MarketDataJpaRepository marketDataRepository;

    @Autowired
    private InstrumentJpaRepository instrumentRepository;

    @Test
    @DisplayName("CA-01 y CA-02: findLatestPrices devuelve exactamente 1 fila por instrumento con el close más reciente")
    void findLatestPrices_returnsOnlyLatestPerInstrument() {
        // ---------------------------------------------------------------
        // Given: 2 instrumentos con 3 filas históricas cada uno
        // ---------------------------------------------------------------
        InstrumentEntity instrumentA = createInstrument("TESTA", "Test Instrument A", InstrumentType.ACCION);
        InstrumentEntity instrumentB = createInstrument("TESTB", "Test Instrument B", InstrumentType.ACCION);
        instrumentRepository.saveAll(List.of(instrumentA, instrumentB));
        instrumentRepository.flush();

        long idA = instrumentA.getId();
        long idB = instrumentB.getId();

        // Instrumento A: 3 timestamps distintos
        // La más reciente debe ser la que se devuelve (close = 150.00)
        Instant now = Instant.now();
        marketDataRepository.saveAll(List.of(
            createMarketData(idA, new BigDecimal("100.00"), new BigDecimal("99.00"),
                    new BigDecimal("101.00"), new BigDecimal("100.00"), new BigDecimal("98.00"),
                    now.minusSeconds(3600 * 3)),  // 3 horas atrás - más antigua
            createMarketData(idA, new BigDecimal("120.00"), new BigDecimal("119.00"),
                    new BigDecimal("121.00"), new BigDecimal("120.00"), new BigDecimal("118.00"),
                    now.minusSeconds(3600 * 2)),  // 2 horas atrás
            createMarketData(idA, new BigDecimal("150.00"), new BigDecimal("149.00"),
                    new BigDecimal("151.00"), new BigDecimal("150.00"), new BigDecimal("148.00"),
                    now.minusSeconds(3600)),      // 1 hora atrás - MÁS RECIENTE
            // Instrumento B: 3 timestamps distintos
            // La más reciente debe ser la que se devuelve (close = 250.00)
            createMarketData(idB, new BigDecimal("200.00"), new BigDecimal("199.00"),
                    new BigDecimal("201.00"), new BigDecimal("200.00"), new BigDecimal("198.00"),
                    now.minusSeconds(3600 * 3)),
            createMarketData(idB, new BigDecimal("220.00"), new BigDecimal("219.00"),
                    new BigDecimal("221.00"), new BigDecimal("220.00"), new BigDecimal("218.00"),
                    now.minusSeconds(3600 * 2)),
            createMarketData(idB, new BigDecimal("250.00"), new BigDecimal("249.00"),
                    new BigDecimal("251.00"), new BigDecimal("250.00"), new BigDecimal("248.00"),
                    now.minusSeconds(3600))
        ));
        marketDataRepository.flush();

        // ---------------------------------------------------------------
        // When: Llamar a findLatestPrices con ambos instrumentos
        // ---------------------------------------------------------------
        Map<Long, MarketPrice> result = marketDataAdapter.findLatestPrices(Set.of(idA, idB));

        // ---------------------------------------------------------------
        // Then: CA-01 - Exactamente 2 entradas (1 por instrumento)
        // ---------------------------------------------------------------
        assertThat(result)
                .as("CA-01: Debe devolver exactamente 1 entrada por instrumento solicitado")
                .hasSize(2);
        assertThat(result.keySet()).containsExactlyInAnyOrder(idA, idB);

        // ---------------------------------------------------------------
        // Then: CA-02 - Los precios corresponden al close de la fila más reciente
        // ---------------------------------------------------------------
        MarketPrice priceA = result.get(idA);
        assertThat(priceA)
                .as("CA-02: Instrumento A debe tener el close de la fila más reciente (150.00)")
                .isNotNull();
        assertThat(priceA.close())
                .as("CA-02: close del instrumento A debe ser 150.00 (el más reciente)")
                .isEqualByComparingTo("150.00");
        assertThat(priceA.previousClose())
                .as("previousClose del instrumento A debe ser 148.00 (el de la fila más reciente)")
                .isEqualByComparingTo("148.00");

        MarketPrice priceB = result.get(idB);
        assertThat(priceB)
                .as("CA-02: Instrumento B debe tener el close de la fila más reciente (250.00)")
                .isNotNull();
        assertThat(priceB.close())
                .as("CA-02: close del instrumento B debe ser 250.00 (el más reciente)")
                .isEqualByComparingTo("250.00");
        assertThat(priceB.previousClose())
                .as("previousClose del instrumento B debe ser 248.00 (el de la fila más reciente)")
                .isEqualByComparingTo("248.00");
    }

    @Test
    @DisplayName("CA-03: findLatestPrices con Set vacío devuelve Map vacío")
    void findLatestPrices_emptySet_returnsEmptyMap() {
        // ---------------------------------------------------------------
        // When: Llamar con Set vacío
        // ---------------------------------------------------------------
        Map<Long, MarketPrice> result = marketDataAdapter.findLatestPrices(Set.of());

        // ---------------------------------------------------------------
        // Then: CA-03 - Devuelve Map vacío
        // ---------------------------------------------------------------
        assertThat(result)
                .as("CA-03: Set vacío debe devolver Map vacío")
                .isEmpty();
    }

    @Test
    @DisplayName("CA-04: Instrumentos sin marketdata no aparecen en el resultado (no lanzan excepción)")
    void findLatestPrices_instrumentWithoutData_notInResult() {
        // ---------------------------------------------------------------
        // Given: Solo instrumento A tiene marketdata, B existe pero no tiene marketdata
        // ---------------------------------------------------------------
        InstrumentEntity instrumentA = createInstrument("TESTC", "Test Instrument C", InstrumentType.ACCION);
        InstrumentEntity instrumentB = createInstrument("TESTD", "Test Instrument D", InstrumentType.ACCION);
        instrumentRepository.saveAll(List.of(instrumentA, instrumentB));
        instrumentRepository.flush();

        long idA = instrumentA.getId();
        long idB = instrumentB.getId();

        Instant now = Instant.now();
        marketDataRepository.save(createMarketData(idA, new BigDecimal("100.00"), new BigDecimal("99.00"),
                new BigDecimal("101.00"), new BigDecimal("100.00"), new BigDecimal("98.00"), now));
        marketDataRepository.flush();

        // ---------------------------------------------------------------
        // When: Llamar con ambos instrumentos
        // ---------------------------------------------------------------
        Map<Long, MarketPrice> result = marketDataAdapter.findLatestPrices(Set.of(idA, idB));

        // ---------------------------------------------------------------
        // Then: CA-04 - Solo A aparece, B no está (no lanza excepción)
        // ---------------------------------------------------------------
        assertThat(result)
                .as("CA-04: Solo debe devolver instrumentos que tienen marketdata")
                .hasSize(1);
        assertThat(result.keySet()).containsOnly(idA);
        assertThat(result.get(idA).close()).isEqualByComparingTo("100.00");
    }

    private MarketDataEntity createMarketData(
            Long instrumentId,
            BigDecimal high,
            BigDecimal low,
            BigDecimal open,
            BigDecimal close,
            BigDecimal previousClose,
            Instant datetime) {
        MarketDataEntity entity = new MarketDataEntity();
        entity.setInstrumentId(instrumentId);
        entity.setHigh(high);
        entity.setLow(low);
        entity.setOpen(open);
        entity.setClose(close);
        entity.setPreviousClose(previousClose);
        entity.setDatetime(datetime);
        return entity;
    }

    private InstrumentEntity createInstrument(String ticker, String name, InstrumentType type) {
        InstrumentEntity entity = new InstrumentEntity();
        entity.setTicker(ticker);
        entity.setName(name);
        entity.setType(type);
        return entity;
    }
}