package ar.com.cocos.challenge.domain.port.out;

import ar.com.cocos.challenge.domain.model.IdempotencyKey;

import java.util.Optional;

/**
 * Puerto de salida para store de idempotencia.
 */
public interface IdempotencyStorePort {
    Optional<IdempotencyKey> findByKey(String key);

    /**
     * Busca la key y, si no existe, reserva un "slot" con lock pesimista.
     * Retorna Optional.empty() si la key no existía y se reservó el slot (el caller debe guardar).
     * Retorna Optional.of(existing) si la key ya existía.
     * Lanza excepción si hubo concurrencia y no se pudo reservar.
     */
    Optional<IdempotencyKey> findByKeyWithLock(String key);

    IdempotencyKey save(IdempotencyKey key);
    void deleteExpiredKeys();
}