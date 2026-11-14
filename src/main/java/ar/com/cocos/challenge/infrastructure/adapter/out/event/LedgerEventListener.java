package ar.com.cocos.challenge.infrastructure.adapter.out.event;

import ar.com.cocos.challenge.domain.model.OrderFilledEvent;
import ar.com.cocos.challenge.domain.model.OrderPlacedEvent;
import ar.com.cocos.challenge.domain.model.OrderType;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Listener para contabilidad (ledger) - registra movimientos contables de órdenes ejecutadas.
 * Se ejecuta asíncronamente después de commit.
 */
@Component
@RequiredArgsConstructor
public class LedgerEventListener {

    private static final Logger log = LoggerFactory.getLogger(LedgerEventListener.class);

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderFilled(OrderFilledEvent event) {
        log.info("LEDGER: Asiento contable - OrderID: {}, UserID: {}, InstrumentID: {}, Side: {}, Size: {}, Price: {}",
                event.orderId(), event.userId(), event.instrumentId(), event.side(), event.executedSize(), event.executedPrice());

        // TODO: Implementar lógica de ledger real
        // - Debitar/creditar cuentas contables
        // - Generar asiento de diario
        // - Actualizar saldos contables
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderPlaced(OrderPlacedEvent event) {
        // Para órdenes LIMIT, registrar reserva de fondos/acciones en contabilidad
        if (event.type() == OrderType.LIMIT) {
            log.info("LEDGER: Reserva de fondos/acciones - OrderID: {}, UserID: {}, Side: {}, Size: {}",
                    event.orderId(), event.userId(), event.side(), event.size());
        }
    }
}