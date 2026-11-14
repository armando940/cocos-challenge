package ar.com.cocos.challenge.infrastructure.adapter.out.persistence;

import ar.com.cocos.challenge.domain.model.IdempotencyKey;
import ar.com.cocos.challenge.domain.port.out.IdempotencyStorePort;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

/**
 * Adaptador JPA para IdempotencyStorePort.
 */
@Repository
@RequiredArgsConstructor
public class PostgresIdempotencyStore implements IdempotencyStorePort {

    private final IdempotencyKeyJpaRepository jpaRepository;

    @Override
    public Optional<IdempotencyKey> findByKey(String key) {
        return jpaRepository.findByKey(key).map(PersistenceMapper::toDomain);
    }

    @Override
    public IdempotencyKey save(IdempotencyKey key) {
        return PersistenceMapper.toDomain(jpaRepository.save(PersistenceMapper.toEntity(key)));
    }

    @Override
    @Scheduled(fixedRate = 3600000) // Cada hora
    public void deleteExpiredKeys() {
        jpaRepository.deleteExpiredKeys(Instant.now());
    }
}