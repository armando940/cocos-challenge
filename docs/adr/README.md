# ADRs — Cocos Challenge

Registro de decisiones arquitectónicas (Architecture Decision Records) del backend.

| ADR | Decisión | Estado |
|---|---|---|
| [001](001-arquitectura-hexagonal.md) | Arquitectura hexagonal (Ports & Adapters) | Aceptado |
| [002](002-idempotencia.md) | Idempotencia transaccional en órdenes | Aceptado |
| [003](003-resiliencia-resilience4j.md) | Resiliencia con Resilience4j en market data | Aceptado |
| [004](004-cache-caffeine.md) | Cache Caffeine selectivo | Aceptado |
| [005](005-event-driven.md) | Eventos de dominio asíncronos (in-process) | Aceptado (stretch) |
| [006](006-flyway-baseline-produccion.md) | Migraciones Flyway compatibles con el esquema legado de producción | Aceptado |

## Formato

Cada ADR describe **contexto**, **decisión**, **consecuencias** y **alternativas**.
Las decisiones se registran con fecha y se mantienen aunque luego se revisen: si una
decisión cambia, se agrega un ADR nuevo que supersede al anterior en lugar de reescribirlo.
