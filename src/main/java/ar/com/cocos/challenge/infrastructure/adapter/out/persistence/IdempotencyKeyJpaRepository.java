package ar.com.cocos.challenge.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * Spring Data JPA Repository para IdempotencyKeyEntity.
 */
@Repository
public interface IdempotencyKeyJpaRepository extends JpaRepository<IdempotencyKeyEntity, Long> {

    Optional<IdempotencyKeyEntity> findByKey(String key);

    @Modifying
    @Transactional
    @Query("DELETE FROM IdempotencyKeyEntity k WHERE k.expiresAt < :now")
    int deleteExpiredKeys(@Param("now") Instant now);
}