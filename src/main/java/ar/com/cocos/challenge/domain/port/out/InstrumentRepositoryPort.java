package ar.com.cocos.challenge.domain.port.out;

import ar.com.cocos.challenge.domain.model.Instrument;
import ar.com.cocos.challenge.domain.model.InstrumentType;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Puerto de salida para repositorio de instrumentos.
 */
public interface InstrumentRepositoryPort {
    Optional<Instrument> findById(Long id);
    List<Instrument> findByTickerOrNameContainingIgnoreCase(String query, int page, int size);
    long countByTickerOrNameContainingIgnoreCase(String query);
    List<Instrument> findAllById(Set<Long> ids);
    List<Instrument> findByType(InstrumentType type);
    Optional<Instrument> findByTicker(String ticker);
}