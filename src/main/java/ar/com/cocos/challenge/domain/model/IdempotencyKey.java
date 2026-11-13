package ar.com.cocos.challenge.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Clave de idempotencia para requests de órdenes.
 */
public record IdempotencyKey(
    String key,
    String requestHash,
    String responseBody,
    int httpStatus,
    Instant createdAt,
    Instant expiresAt
) {
    public IdempotencyKey {
        Objects.requireNonNull(key, "key cannot be null");
        Objects.requireNonNull(requestHash, "requestHash cannot be null");
        Objects.requireNonNull(responseBody, "responseBody cannot be null");
        Objects.requireNonNull(createdAt, "createdAt cannot be null");
        Objects.requireNonNull(expiresAt, "expiresAt cannot be null");
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public boolean matchesRequestHash(String hash) {
        return requestHash.equals(hash);
    }
}