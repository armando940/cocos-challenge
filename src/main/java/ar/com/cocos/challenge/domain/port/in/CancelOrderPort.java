package ar.com.cocos.challenge.domain.port.in;

import ar.com.cocos.challenge.domain.model.OrderStatus;

/**
 * Puerto de entrada para cancelar órdenes.
 */
public interface CancelOrderPort {

    record Result(
        Long orderId,
        OrderStatus status
    ) {}

    Result execute(Long orderId);
}