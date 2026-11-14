package ar.com.cocos.challenge.infrastructure.adapter.out.event;

import ar.com.cocos.challenge.domain.model.Order;
import ar.com.cocos.challenge.domain.model.OrderCancelledEvent;
import ar.com.cocos.challenge.domain.model.OrderFilledEvent;
import ar.com.cocos.challenge.domain.model.OrderPlacedEvent;
import ar.com.cocos.challenge.domain.model.OrderRejectedEvent;
import ar.com.cocos.challenge.domain.port.out.EventPublisherPort;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Implementación de EventPublisherPort usando Spring ApplicationEventPublisher.
 * Los listeners se ejecutan asíncronamente con @Async.
 */
@Component
@RequiredArgsConstructor
public class SpringEventPublisher implements EventPublisherPort {

    private final ApplicationEventPublisher eventPublisher;

    @Override
    public void publishOrderPlaced(Order order) {
        eventPublisher.publishEvent(OrderPlacedEvent.from(order));
    }

    @Override
    public void publishOrderFilled(Order order) {
        eventPublisher.publishEvent(OrderFilledEvent.from(order));
    }

    @Override
    public void publishOrderRejected(Order order) {
        eventPublisher.publishEvent(OrderRejectedEvent.from(order));
    }

    @Override
    public void publishOrderCancelled(Order order) {
        eventPublisher.publishEvent(OrderCancelledEvent.from(order));
    }
}