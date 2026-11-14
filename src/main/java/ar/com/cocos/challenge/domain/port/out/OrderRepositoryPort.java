package ar.com.cocos.challenge.domain.port.out;

import ar.com.cocos.challenge.domain.model.Order;
import ar.com.cocos.challenge.domain.model.OrderStatus;
import ar.com.cocos.challenge.domain.model.OrderSide;

import java.util.List;
import java.util.Optional;

/**
 * Puerto de salida para repositorio de órdenes.
 */
public interface OrderRepositoryPort {
    Order save(Order order);
    Optional<Order> findById(Long id);
    Optional<Order> findByIdWithLock(Long id); // SELECT FOR UPDATE
    Optional<Order> findByIdempotencyKey(String key);
    List<Order> findByUserIdAndStatus(Long userId, OrderStatus status);
    List<Order> findFilledByUserId(Long userId);
    List<Order> findFilledByUserIdAndInstrumentId(Long userId, Long instrumentId);
    List<Order> findFilledByInstrumentId(Long instrumentId);
    List<Order> findByUserIdAndInstrumentIdAndStatus(Long userId, Long instrumentId, OrderStatus status);
    List<Order> findByUserIdAndSideAndStatus(Long userId, OrderSide side, OrderStatus status);
    long countByUserIdAndSideAndStatus(Long userId, OrderSide side, OrderStatus status);
    void deleteById(Long id);
}