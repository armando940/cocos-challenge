package ar.com.cocos.challenge.domain.port.out;

import ar.com.cocos.challenge.domain.model.IdempotencyKey;

import java.util.Optional;

/**
 * Puerto de salida para store de idempotencia.
 */
public interface IdempotencyStorePort {
    Optional<IdempotencyKey> findByKey(String key);
    IdempotencyKey save(IdempotencyKey key);
    void deleteExpiredKeys();
}