package ar.com.cocos.challenge.infrastructure.adapter.out.persistence;

import ar.com.cocos.challenge.domain.model.Order;
import ar.com.cocos.challenge.domain.model.OrderSide;
import ar.com.cocos.challenge.domain.model.OrderStatus;
import ar.com.cocos.challenge.domain.port.out.OrderRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Adaptador JPA para OrderRepositoryPort.
 */
@Repository
@RequiredArgsConstructor
public class PostgresOrderRepository implements OrderRepositoryPort {

    private final OrderJpaRepository jpaRepository;

    @Override
    public Order save(Order order) {
        OrderEntity entity = PersistenceMapper.toEntity(order);
        return PersistenceMapper.toDomain(jpaRepository.save(entity));
    }

    @Override
    public Optional<Order> findById(Long id) {
        return jpaRepository.findById(id).map(PersistenceMapper::toDomain);
    }

    @Override
    public Optional<Order> findByIdWithLock(Long id) {
        return jpaRepository.findByIdWithLock(id).map(PersistenceMapper::toDomain);
    }

    @Override
    public Optional<Order> findByIdempotencyKey(String key) {
        return jpaRepository.findByIdempotencyKey(key).map(PersistenceMapper::toDomain);
    }

    @Override
    public List<Order> findByUserIdAndStatus(Long userId, OrderStatus status) {
        return jpaRepository.findByUserIdAndStatusOrderByDatetimeDesc(userId, status).stream()
                .map(PersistenceMapper::toDomain)
                .toList();
    }

    @Override
    public List<Order> findFilledByUserId(Long userId) {
        return jpaRepository.findByUserIdAndStatusOrderByDatetimeDesc(userId, OrderStatus.FILLED).stream()
                .map(PersistenceMapper::toDomain)
                .toList();
    }

    @Override
    public List<Order> findFilledByUserIdAndInstrumentId(Long userId, Long instrumentId) {
        return jpaRepository.findByUserIdAndInstrumentIdAndStatus(userId, instrumentId, OrderStatus.FILLED).stream()
                .map(PersistenceMapper::toDomain)
                .toList();
    }

    @Override
    public List<Order> findFilledByInstrumentId(Long instrumentId) {
        return jpaRepository.findByInstrumentIdAndStatus(instrumentId, OrderStatus.FILLED).stream()
                .map(PersistenceMapper::toDomain)
                .toList();
    }

    @Override
    public List<Order> findByUserIdAndInstrumentIdAndStatus(Long userId, Long instrumentId, OrderStatus status) {
        return jpaRepository.findByUserIdAndInstrumentIdAndStatus(userId, instrumentId, status).stream()
                .map(PersistenceMapper::toDomain)
                .toList();
    }

    @Override
    public List<Order> findByUserIdAndSideAndStatus(Long userId, OrderSide side, OrderStatus status) {
        return jpaRepository.findByUserIdAndSideAndStatus(userId, side, status).stream()
                .map(PersistenceMapper::toDomain)
                .toList();
    }

    @Override
    public long countByUserIdAndSideAndStatus(Long userId, OrderSide side, OrderStatus status) {
        return jpaRepository.countByUserIdAndSideAndStatus(userId, side, status);
    }

    @Override
    public void deleteById(Long id) {
        jpaRepository.deleteById(id);
    }
}