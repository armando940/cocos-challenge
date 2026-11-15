# ADR 003: Race Condition de Idempotencia Cross-User

## Status
Accepted

## Context
El sistema implementa idempotencia transaccional mediante una tabla `idempotency_keys` con restricción `UNIQUE(key)`. El requerimiento 005 introdujo un lock pesimista por usuario (`userRepositoryPort.lockById`) en `PlaceOrderUseCase` para serializar órdenes del mismo usuario y validar saldo/tenencia correctamente.

Sin embargo, existía un edge case: **dos usuarios distintos usando la misma `idempotency_key` simultáneamente**. El lock por usuario no los serializa (son locks distintos), por lo que:

1. Ambos pasan `findByKey()` → `Optional.empty()`
2. Ambos adquieren su lock de usuario respectivo
3. Ambos procesan la orden completamente
4. Ambos intentan `save()` → violation de `UNIQUE(key)` → `DataIntegrityViolationException` → HTTP 500

Esto rompe la garantía **exactly-once**: dos requests con la misma key deberían resultar en exactamente una ejecución, sin importar el usuario.

## Decision
Implementar **lock pesimista global por `idempotency_key`** usando `SELECT ... FOR UPDATE SKIP LOCKED` en PostgreSQL, moviendo la comprobación de idempotencia **después** del lock de usuario pero **antes** de cualquier lógica de negocio.

### Flujo nuevo en `PlaceOrderUseCase.execute()`:
1. `userRepositoryPort.lockById(userId)` — lock por usuario (para validación de saldo)
2. `idempotencyStore.findByKeyWithLock(key)` — lock global por key (idempotencia cross-user)
   - Retorna `Optional.of(existing)` → validar hash y devolver cacheado
   - Retorna `Optional.empty()` → slot reservado, procesar orden
3. Procesar orden (validar instrumento, precio, saldo, ejecutar)
4. `idempotencyStore.save(key)` — UPSERT (actualiza el slot reservado)

### Implementación en `PostgresIdempotencyStore`:
- Nuevo método en puerto: `Optional<IdempotencyKey> findByKeyWithLock(String key)`
- Query nativa: `SELECT * FROM idempotency_keys WHERE key = ? FOR UPDATE SKIP LOCKED`
- Si no existe: `INSERT ... ON CONFLICT (key) DO NOTHING RETURNING *` para reservar slot atómicamente
- `save()`: UPSERT con `ON CONFLICT (key) DO UPDATE` para actualizar slot reservado o insertar nuevo

## Alternatives Considered

### Opción B: Manejo de excepción de BD (`DataIntegrityViolationException`)
- Mantener flujo actual, envolver `save()` en try-catch
- Al atrapar violación de `ux_idempotency_keys_key` → relanzar `IdempotencyConflictException`
- **Pros**: Simple, pocas líneas de código
- **Contras**: **No garantiza exactly-once** — dos hilos procesan la orden completamente antes de chocar en el INSERT. El cliente recibe 409 pero la lógica de negocio se ejecutó dos veces (doble débito, doble evento, etc.). **Rechazada** por violar la semántica de idempotencia.

### Opción C: Lock de tabla (`LOCK TABLE idempotency_keys IN EXCLUSIVE MODE`)
- Serializa todas las inserciones de idempotencia
- **Pros**: Portable, simple
- **Contras**: Contención extrema, mata concurrencia. **Rechazada**.

## Consequences

### Positivas
- **Exactly-once real**: Solo un hilo entra a la lógica de negocio por `idempotency_key`, globalmente.
- **Latencia despreciable**: Un `SELECT FOR UPDATE` adicional (< 1ms en Postgres local).
- **Ortogonal al lock de usuario**: El lock de usuario sigue protegiendo saldo/tenencia; el lock de key protege idempotencia. Orden fijo: **siempre lock usuario ANTES que lock key** (evita deadlocks).

### Negativas
- **Query nativa PostgreSQL-specific**: `FOR UPDATE SKIP LOCKED` y `ON CONFLICT` son sintaxis Postgres. Si se cambia de BD, requiere adaptación.
- **Complejidad en `save()`**: Debe hacer UPSERT en lugar de INSERT simple para manejar el slot reservado.
- **Test concurrente potencialmente flaky**: Requiere aislamiento de transacción adecuado (`REPEATABLE_READ` o `SERIALIZABLE`) y sincronización precisa (`CountDownLatch`).

## Testing
- **CA-01**: Dos users, misma key, mismo request → exactamente un `201`, otro `409` (`IdempotencyConflictException`).
- **CA-02**: Dos users, misma key, request distinto → exactamente un `201`, otro `409` por hash mismatch.
- **CA-03/04/05**: Regresión de comportamiento existente (cache hit, hash mismatch, flujo normal).
- Test de integración: `IdempotencyRaceConditionIT` con `ExecutorService` + `CountDownLatch` para disparo simultáneo real.

## References
- Requerimiento 008: `specs/008-fix-idempotency-race-condition/spec.md`
- Requerimiento 005 (lock por usuario): `specs/005-fix-balance-validation-race/`
- PostgreSQL `FOR UPDATE SKIP LOCKED`: https://www.postgresql.org/docs/current/sql-select.html#SQL-FOR-UPDATE-SKIP-LOCKED
- PostgreSQL `ON CONFLICT`: https://www.postgresql.org/docs/current/sql-insert.html#SQL-ON-CONFLICT