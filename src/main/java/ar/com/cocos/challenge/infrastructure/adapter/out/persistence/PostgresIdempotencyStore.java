package ar.com.cocos.challenge.infrastructure.adapter.out.persistence;

import ar.com.cocos.challenge.domain.model.IdempotencyKey;
import ar.com.cocos.challenge.domain.port.out.IdempotencyStorePort;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * Adaptador JPA para IdempotencyStorePort.
 */
@Repository
@RequiredArgsConstructor
public class PostgresIdempotencyStore implements IdempotencyStorePort {

    private final IdempotencyKeyJpaRepository jpaRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Optional<IdempotencyKey> findByKey(String key) {
        return jpaRepository.findByKey(key).map(PersistenceMapper::toDomain);
    }

    @Override
    @Transactional
    public Optional<IdempotencyKey> findByKeyWithLock(String key) {
        // 1. Try to find existing key with FOR UPDATE SKIP LOCKED
        Optional<IdempotencyKeyEntity> existing = jpaRepository.findByKeyForUpdateSkipLocked(key);
        if (existing.isPresent()) {
            return Optional.of(PersistenceMapper.toDomain(existing.get()));
        }

        // 2. Key doesn't exist, try to reserve slot with INSERT ON CONFLICT DO NOTHING RETURNING *
        // Using positional parameters to avoid issues with ::jsonb cast in named parameters
        String insertSql = """
            INSERT INTO idempotency_keys (key, request_hash, response_body, http_status, created_at, expires_at)
            VALUES (?, '', '{}'::jsonb, 0, ?, ?)
            ON CONFLICT (key) DO NOTHING
            RETURNING *
            """;

        Query query = entityManager.createNativeQuery(insertSql, IdempotencyKeyEntity.class)
                .setParameter(1, key)
                .setParameter(2, Instant.now())
                .setParameter(3, Instant.now().plusSeconds(86400)); // 24h TTL

        @SuppressWarnings("unchecked")
        Optional<IdempotencyKeyEntity> inserted = query.getResultList().stream().findFirst();

        if (inserted.isPresent()) {
            // Slot reserved successfully - caller must save the actual data (will UPDATE this row)
            return Optional.empty();
        }

        // 3. Another thread won the race, re-read with lock
        Optional<IdempotencyKeyEntity> winner = jpaRepository.findByKeyForUpdateSkipLocked(key);
        return winner.map(PersistenceMapper::toDomain);
    }

    @Override
    public IdempotencyKey save(IdempotencyKey key) {
        // UPSERT: insert or update the row (handles the case where findByKeyWithLock reserved a slot)
        // Using positional parameters to avoid issues with ::jsonb cast in named parameters
        String upsertSql = """
            INSERT INTO idempotency_keys (key, request_hash, response_body, http_status, created_at, expires_at)
            VALUES (?, ?, ?::jsonb, ?, ?, ?)
            ON CONFLICT (key) DO UPDATE SET
                request_hash = EXCLUDED.request_hash,
                response_body = EXCLUDED.response_body,
                http_status = EXCLUDED.http_status,
                expires_at = EXCLUDED.expires_at
            RETURNING *
            """;

        Query query = entityManager.createNativeQuery(upsertSql, IdempotencyKeyEntity.class)
                .setParameter(1, key.key())
                .setParameter(2, key.requestHash())
                .setParameter(3, key.responseBody())
                .setParameter(4, key.httpStatus())
                .setParameter(5, key.createdAt())
                .setParameter(6, key.expiresAt());

        @SuppressWarnings("unchecked")
        IdempotencyKeyEntity saved = (IdempotencyKeyEntity) query.getSingleResult();

        return PersistenceMapper.toDomain(saved);
    }

    @Override
    @Scheduled(fixedRate = 3600000) // Cada hora
    public void deleteExpiredKeys() {
        jpaRepository.deleteExpiredKeys(Instant.now());
    }
}