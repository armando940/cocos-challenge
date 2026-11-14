package ar.com.cocos.challenge.infrastructure.adapter.out.event;

import ar.com.cocos.challenge.domain.model.OrderCancelledEvent;
import ar.com.cocos.challenge.domain.model.OrderFilledEvent;
import ar.com.cocos.challenge.domain.model.OrderPlacedEvent;
import ar.com.cocos.challenge.domain.model.OrderRejectedEvent;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Listener para notificaciones (email, push, etc.) al colocarse/ejecutarse/rechazarse órdenes.
 * Se ejecuta asíncronamente después de commit de la transacción.
 */
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventListener.class);

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderPlaced(OrderPlacedEvent event) {
        log.info("NOTIFICATION: Orden colocada - OrderID: {}, UserID: {}, Side: {}, Type: {}",
                event.orderId(), event.userId(), event.side(), event.type());
        // TODO: Integrar con servicio de notificaciones real (email, push, websocket)
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderFilled(OrderFilledEvent event) {
        log.info("NOTIFICATION: Orden ejecutada - OrderID: {}, UserID: {}, ExecutedPrice: {}, ExecutedSize: {}",
                event.orderId(), event.userId(), event.executedPrice(), event.executedSize());
        // TODO: Notificar ejecución al usuario
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderRejected(OrderRejectedEvent event) {
        log.warn("NOTIFICATION: Orden rechazada - OrderID: {}, UserID: {}, Reason: {}",
                event.orderId(), event.userId(), event.rejectionReason());
        // TODO: Notificar rechazo al usuario con razón
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderCancelled(OrderCancelledEvent event) {
        log.info("NOTIFICATION: Orden cancelada - OrderID: {}, UserID: {}",
                event.orderId(), event.userId());
        // TODO: Notificar cancelación al usuario
    }
}