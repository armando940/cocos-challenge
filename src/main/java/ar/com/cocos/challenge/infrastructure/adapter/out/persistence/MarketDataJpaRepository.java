package ar.com.cocos.challenge.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Spring Data JPA Repository para MarketDataEntity.
 */
@Repository
public interface MarketDataJpaRepository extends JpaRepository<MarketDataEntity, Long> {

    @Query("SELECT m FROM MarketDataEntity m WHERE m.instrumentId = :instrumentId ORDER BY m.datetime DESC")
    List<MarketDataEntity> findByInstrumentIdOrderByDatetimeDesc(@Param("instrumentId") Long instrumentId);

    /**
     * Último precio (close) de un instrumento.
     * Query derivada: Spring Data aplica el límite de 1 fila. Con @Query el
     * ORDER BY no limita y el retorno Optional explotaba con 2 filas de marketdata.
     */
    Optional<MarketDataEntity> findFirstByInstrumentIdOrderByDatetimeDesc(Long instrumentId);

    @Query(value = "SELECT DISTINCT ON (instrument_id) * FROM marketdata WHERE instrument_id IN :instrumentIds ORDER BY instrument_id, datetime DESC", nativeQuery = true)
    List<MarketDataEntity> findByInstrumentIdInOrderByDatetimeDesc(@Param("instrumentIds") Set<Long> instrumentIds);
}