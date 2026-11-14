package ar.com.cocos.challenge.infrastructure.adapter.out.persistence;

import ar.com.cocos.challenge.domain.model.Instrument;
import ar.com.cocos.challenge.domain.model.InstrumentType;
import ar.com.cocos.challenge.domain.port.out.InstrumentRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Adaptador JPA para InstrumentRepositoryPort.
 */
@Repository
@RequiredArgsConstructor
public class PostgresInstrumentRepository implements InstrumentRepositoryPort {

    private final InstrumentJpaRepository jpaRepository;

    @Override
    public Optional<Instrument> findById(Long id) {
        return jpaRepository.findById(id).map(PersistenceMapper::toDomain);
    }

    @Override
    public List<Instrument> findByTickerOrNameContainingIgnoreCase(String query, int page, int size) {
        List<InstrumentEntity> result = jpaRepository.findByTickerOrNameContainingIgnoreCase(query, PageRequest.of(page, size));
        return result.stream().map(PersistenceMapper::toDomain).toList();
    }

    @Override
    public long countByTickerOrNameContainingIgnoreCase(String query) {
        return jpaRepository.countByTickerOrNameContainingIgnoreCase(query);
    }

    @Override
    public List<Instrument> findAllById(Set<Long> ids) {
        return jpaRepository.findAllById(ids).stream()
                .map(PersistenceMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Instrument> findByType(InstrumentType type) {
        return jpaRepository.findByType(type).stream()
                .map(PersistenceMapper::toDomain)
                .toList();
    }

    @Override
    public Optional<Instrument> findByTicker(String ticker) {
        return jpaRepository.findByTicker(ticker).map(PersistenceMapper::toDomain);
    }
}