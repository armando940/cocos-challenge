package ar.com.cocos.challenge.infrastructure.adapter.out.persistence;

import ar.com.cocos.challenge.domain.model.OrderSide;
import ar.com.cocos.challenge.domain.model.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA Repository para OrderEntity.
 */
@Repository
public interface OrderJpaRepository extends JpaRepository<OrderEntity, Long> {

    Optional<OrderEntity> findByIdempotencyKey(String key);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM OrderEntity o WHERE o.id = :id")
    Optional<OrderEntity> findByIdWithLock(@Param("id") Long id);

    List<OrderEntity> findByUserIdAndStatus(Long userId, OrderStatus status);

    List<OrderEntity> findByUserIdAndStatusOrderByDatetimeDesc(Long userId, OrderStatus status);

    @Query("SELECT o FROM OrderEntity o WHERE o.userId = :userId AND o.status = :status AND o.side IN (:sides)")
    List<OrderEntity> findByUserIdAndStatusAndSideIn(Long userId, OrderStatus status, List<OrderSide> sides);

    @Query("SELECT o FROM OrderEntity o WHERE o.userId = :userId AND o.instrumentId = :instrumentId AND o.status = :status")
    List<OrderEntity> findByUserIdAndInstrumentIdAndStatus(Long userId, Long instrumentId, OrderStatus status);

    @Query("SELECT o FROM OrderEntity o WHERE o.userId = :userId AND o.side = :side AND o.status = :status")
    List<OrderEntity> findByUserIdAndSideAndStatus(Long userId, OrderSide side, OrderStatus status);

    @Query("SELECT o FROM OrderEntity o WHERE o.instrumentId = :instrumentId AND o.status = :status")
    List<OrderEntity> findByInstrumentIdAndStatus(Long instrumentId, OrderStatus status);

    long countByUserIdAndSideAndStatus(Long userId, OrderSide side, OrderStatus status);
}