# ADR-005 — Eventos de dominio asíncronos (in-process)

- **Estado:** Aceptado (stretch goal)
- **Fecha:** 2026-11-12
- **Decisores:** Armando Silva

## Contexto

Además de persistir la orden, un brokerage real dispara efectos secundarios:
notificación al usuario, asiento contable (ledger) y chequeos de fraude. Ejecutarlos
dentro del request aumenta la latencia y acopla el caso de uso a servicios externos.
El challenge lo propone como stretch goal.

## Decisión

Publicar eventos de dominio con Spring Events y consumirlos de forma asíncrona después
del commit:

| Evento | Cuándo se publica | Listeners |
|---|---|---|
| `OrderPlaced` | orden LIMIT creada (`NEW`) | Notification, Ledger (reserva), Fraud |
| `OrderFilled` | orden MARKET ejecutada (`FILLED`) | Notification, Ledger (asiento), Fraud |
| `OrderRejected` | orden rechazada (`REJECTED`) | Notification, Fraud |
| `OrderCancelled` | orden cancelada (`CANCELLED`) | Notification |

Implementación:

- `SpringEventPublisher` implementa `EventPublisherPort` y publica con
  `ApplicationEventPublisher`.
- Los listeners usan `@TransactionalEventListener(phase = AFTER_COMMIT)` + `@Async`,
  con un `ThreadPoolTaskExecutor` dedicado (`AsyncConfig`, pool 4–16, cola 100).
- Si la transacción hace rollback, los listeners no se ejecutan.
- Hoy los listeners **registran el evento y dejan el `TODO`** de la integración real:
  son el punto de extensión, no una implementación de ledger/fraude productiva.

## Consecuencias

**Positivas**

- El request de la orden no espera notificaciones ni ledger.
- Bajo acoplamiento: agregar un listener no toca el caso de uso.
- Los eventos sólo se disparan si el commit fue exitoso.

**Negativas / costos**

- Entrega **in-process y no durable**: si el proceso cae después del commit y antes de
  que corra el listener, el evento se pierde.
- No hay reintentos ni dead-letter queue.
- Para producción haría falta un broker (Kafka/Rabbit) o el patrón **outbox** con una
  tabla de eventos y un publicador confiable.

## Alternativas consideradas

1. **Llamadas síncronas dentro del caso de uso.** Simple, pero acopla y suma latencia.
2. **Kafka/RabbitMQ.** Correcto para producción, pero excesivo para el alcance y el
   entorno local del challenge.
3. **Transactional outbox desde el día uno.** Más robusto, pero agrega tabla, job y
   complejidad; se documenta como evolución natural.
