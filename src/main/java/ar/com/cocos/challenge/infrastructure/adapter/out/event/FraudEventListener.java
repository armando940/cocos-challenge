package ar.com.cocos.challenge.infrastructure.adapter.out.event;

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
 * Listener para detección de fraude / patrones anómalos en órdenes.
 * Se ejecuta asíncronamente después de commit.
 */
@Component
@RequiredArgsConstructor
public class FraudEventListener {

    private static final Logger log = LoggerFactory.getLogger(FraudEventListener.class);

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderPlaced(OrderPlacedEvent event) {
        log.info("FRAUD CHECK: Orden nueva - OrderID: {}, UserID: {}, Side: {}, Size: {}, Price: {}",
                event.orderId(), event.userId(), event.side(), event.size(), event.price());

        // TODO: Implementar reglas de detección de fraude
        // - Validar límites de orden por usuario/día
        // - Detectar patrones de lavado de dinero (layering)
        // - Verificar órdenes a precios anómalos vs mercado
        // - Cross-referenciar con listas de sanciones
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderFilled(OrderFilledEvent event) {
        log.info("FRAUD CHECK: Orden ejecutada - OrderID: {}, UserID: {}, ExecPrice: {}",
                event.orderId(), event.userId(), event.executedPrice());

        // TODO: Post-ejecución fraud checks
        // - Verificar que precio de ejecución sea razonable
        // - Alertar si hay patrones de wash trading
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderRejected(OrderRejectedEvent event) {
        log.info("FRAUD CHECK: Orden rechazada - OrderID: {}, UserID: {}, Reason: {}",
                event.orderId(), event.userId(), event.rejectionReason());

        // TODO: Analizar rechazos repetidos como señal de fraude
    }
}