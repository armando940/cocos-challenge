package ar.com.cocos.challenge.infrastructure.adapter.out.persistence;

import ar.com.cocos.challenge.domain.model.*;

import java.time.Instant;

/**
 * Mapper entre modelos de dominio y entidades JPA.
 */
public final class PersistenceMapper {

    private PersistenceMapper() {}

    // Instrument
    public static Instrument toDomain(InstrumentEntity entity) {
        return new Instrument(
                entity.getId(),
                entity.getTicker(),
                entity.getName(),
                entity.getType()
        );
    }

    public static InstrumentEntity toEntity(Instrument domain) {
        InstrumentEntity entity = new InstrumentEntity();
        entity.setId(domain.id());
        entity.setTicker(domain.ticker());
        entity.setName(domain.name());
        entity.setType(domain.type());
        return entity;
    }

    // Order
    public static Order toDomain(OrderEntity entity) {
        return new Order(
                entity.getId(),
                entity.getInstrumentId(),
                entity.getUserId(),
                entity.getSide(),
                entity.getSize(),
                entity.getPrice(),
                entity.getType(),
                entity.getStatus(),
                entity.getDatetime(),
                entity.getExecutedPrice(),
                entity.getExecutedSize(),
                entity.getRejectionReason(),
                entity.getIdempotencyKey()
        );
    }

    public static OrderEntity toEntity(Order domain) {
        OrderEntity entity = new OrderEntity();
        entity.setId(domain.id());
        entity.setInstrumentId(domain.instrumentId());
        entity.setUserId(domain.userId());
        entity.setSide(domain.side());
        entity.setSize(domain.size());
        entity.setPrice(domain.price());
        entity.setType(domain.type());
        entity.setStatus(domain.status());
        entity.setDatetime(domain.datetime());
        entity.setExecutedPrice(domain.executedPrice());
        entity.setExecutedSize(domain.executedSize());
        entity.setRejectionReason(domain.rejectionReason());
        entity.setIdempotencyKey(domain.idempotencyKey());
        entity.setRequestHash(domain.idempotencyKey() != null ? domain.idempotencyKey() : null); // placeholder
        return entity;
    }

    public static void updateEntity(OrderEntity entity, Order domain) {
        entity.setPrice(domain.price());
        entity.setSize(domain.size());
        entity.setStatus(domain.status());
        entity.setExecutedPrice(domain.executedPrice());
        entity.setExecutedSize(domain.executedSize());
        entity.setRejectionReason(domain.rejectionReason());
        entity.setDatetime(domain.datetime());
    }

    // MarketData
    public static MarketPrice toDomain(MarketDataEntity entity) {
        return new MarketPrice(
                entity.getClose(),
                entity.getPreviousClose(),
                entity.getDatetime()
        );
    }

    // IdempotencyKey
    public static IdempotencyKey toDomain(IdempotencyKeyEntity entity) {
        return new IdempotencyKey(
                entity.getKey(),
                entity.getRequestHash(),
                entity.getResponseBody(),
                entity.getHttpStatus(),
                entity.getCreatedAt(),
                entity.getExpiresAt()
        );
    }

    public static IdempotencyKeyEntity toEntity(IdempotencyKey domain) {
        IdempotencyKeyEntity entity = new IdempotencyKeyEntity();
        entity.setKey(domain.key());
        entity.setRequestHash(domain.requestHash());
        entity.setResponseBody(domain.responseBody());
        entity.setHttpStatus(domain.httpStatus());
        entity.setCreatedAt(domain.createdAt());
        entity.setExpiresAt(domain.expiresAt());
        return entity;
    }
}