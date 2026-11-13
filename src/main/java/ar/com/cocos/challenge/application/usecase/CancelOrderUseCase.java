package ar.com.cocos.challenge.application.usecase;

import ar.com.cocos.challenge.domain.model.Order;
import ar.com.cocos.challenge.domain.model.OrderCannotBeCancelledException;
import ar.com.cocos.challenge.domain.model.OrderNotFoundException;
import ar.com.cocos.challenge.domain.model.OrderStatus;
import ar.com.cocos.challenge.domain.port.in.CancelOrderPort;
import ar.com.cocos.challenge.domain.port.out.EventPublisherPort;
import ar.com.cocos.challenge.domain.port.out.OrderRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * Implementación del caso de uso: Cancelar Orden.
 */
@Service
@RequiredArgsConstructor
public class CancelOrderUseCase implements CancelOrderPort {

    private final OrderRepositoryPort orderRepository;
    private final EventPublisherPort eventPublisher;

    @Override
    @Transactional
    public Result execute(Long orderId) {
        Objects.requireNonNull(orderId, "orderId cannot be null");

        Order order = orderRepository.findByIdWithLock(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (order.status() != OrderStatus.NEW) {
            throw new OrderCannotBeCancelledException(orderId, order.status());
        }

        Order cancelled = order.cancelled();
        Order saved = orderRepository.save(cancelled);

        eventPublisher.publishOrderCancelled(saved);

        return new Result(saved.id(), saved.status());
    }
}