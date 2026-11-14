package ar.com.cocos.challenge.domain.port.out;

import ar.com.cocos.challenge.domain.model.Order;

/**
 * Puerto de salida para publicar eventos de dominio.
 */
public interface EventPublisherPort {
    void publishOrderPlaced(Order order);
    void publishOrderFilled(Order order);
    void publishOrderRejected(Order order);
    void publishOrderCancelled(Order order);
}