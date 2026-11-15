# Requerimiento 008 — Arreglar la race condition de idempotencia

| Campo | Valor |
| :--- | :--- |
| **Número** | 008 |
| **Carpeta** | `specs/008-fix-idempotency-race-condition/` |
| **Tipo** | bugfix |
| **Estado** | draft |
| **Creado** | 2026-11-15 |
| **Actualizado** | 2026-11-15 |
| **Depende de** | 005 |
| **Supersede a** | — |

---

## 1. Contexto y motivación

Actualmente, el sistema mitiga race conditions de idempotencia mediante un lock pesimista por usuario en `PlaceOrderUseCase` (línea 78: `userRepositoryPort.lockById(command.userId())`). Esto cubre el caso común de que el mismo usuario envíe dos veces el mismo request con la misma `idempotency_key`.

**El problema:** Si dos usuarios distintos (ej. `userId=1` y `userId=2`) envían un request con la **misma** `idempotency_key` exactamente al mismo tiempo:
1. Ambos pasan el chequeo `idempotencyStore.findByKey()` → ambos obtienen `Optional.empty()` (la key no existe aún)
2. Ambos adquieren su respectivo lock de usuario (locks distintos, no se bloquean entre sí)
3. Ambos procesan la orden completamente
4. Ambos intentan `idempotencyStore.save()` → la restricción `UNIQUE(key)` en `idempotency_keys` falla para uno de los dos con una excepción de BD (`DataIntegrityViolationException`), que burbujea como 500 en lugar de un 409 controlado.

Esto rompe la garantía de idempotencia: **dos requests con la misma key deberían resultar en exactamente una ejecución**, sin importar el usuario.

## 2. Alcance

### Dentro
- Asegurar que el chequeo e inserción de la `idempotency_key` sea atómico o esté protegido contra concurrencia **global** para la misma llave (independiente del usuario).
- Manejar la excepción de duplicidad de base de datos si ocurre una inserción concurrente, devolviendo HTTP 409 con `ProblemDetail` RFC 7807.
- Garantizar que solo una transacción prospere para una misma `idempotency_key`, sin importar el `userId`.
- Añadir test de integración que reproduzca la race condition con dos hilos/usuarios concurrentes.

### Fuera
- No se cambiará la lógica de negocio de las órdenes (validaciones, matching, eventos).
- No se modificarán los contratos REST externos.
- No se tocará el lock por usuario para validación de saldo/tenencia (ese sigue siendo necesario y correcto).

## 3. Criterios de aceptación

| ID | Given | When | Then |
| :--- | :--- | :--- | :--- |
| **CA-01** | Una `idempotency_key` inexistente en BD | Dos hilos simultáneos (distinto `userId` cada uno) ejecutan `PlaceOrderUseCase.execute()` con esa misma key y requests **idénticos** | Exactamente **uno** retorna `201 Created` con el `orderId`; el otro retorna **`409 Conflict`** con `ProblemDetail` type `https://cocos.challenge/errors/idempotency-conflict` y property `key`. **No** hay `500` ni excepción de BD sin manejar. |
| **CA-02** | Una `idempotency_key` inexistente en BD | Dos hilos simultáneos (distinto `userId`) con la **misma key pero request distinto** (diferente `instrumentId`, `size`, etc.) | Exactamente **uno** retorna `201`; el otro retorna **`409 Conflict`** por hash mismatch (comportamiento actual: `IdempotencyConflictException`). |
| **CA-03** | Una `idempotency_key` **ya procesada** y guardada en BD (con `response_body` y `http_status`) | Un request (cualquier `userId`) con esa misma key y **mismo hash** | Retorna **`201`** (o el status original) con el **mismo `response_body` cacheado** sin reprocesar la orden (comportamiento actual mantenido). |
| **CA-04** | Una `idempotency_key` **ya procesada** y guardada en BD | Un request (cualquier `userId`) con esa misma key pero **hash distinto** | Retorna **`409 Conflict`** con `ProblemDetail` type `https://cocos.challenge/errors/idempotency-conflict` (comportamiento actual mantenido). |
| **CA-05** | Ejecución normal (sin concurrencia) | Un request con key nueva | Funciona exactamente como antes: orden procesada, key guardada, `201` devuelto. |

> **Verificación técnica:** CA-01 y CA-02 se validan con un test de integración concurrente (`@Testcontainers`, `ExecutorService`, `CountDownLatch`, dos hilos, aserciones sobre status codes y que solo se cree una orden en BD). CA-03/04/05 ya están cubiertos por tests existentes; se confirman en regresión.

## 4. Solución propuesta (técnica)

La solución recomendada es **mover la lógica de idempotencia dentro de la transacción y usar `SELECT ... FOR UPDATE` sobre la fila de `idempotency_keys`** (o `INSERT ... ON CONFLICT` en PostgreSQL) para serializar el acceso a la misma key globalmente.

### Opción A — `SELECT FOR UPDATE` en el puerto `IdempotencyStorePort` (preferida, portable JPA)

1. **Nuevo método en `IdempotencyStorePort`:**
   ```java
   /**
    * Busca la key y, si no existe, reserva un "slot" con lock pesimista.
    * Retorna Optional.empty() si la key no existía y se reservó el slot (el caller debe guardar).
    * Retorna Optional.of(existing) si la key ya existía.
    * Lanza excepción si hubo concurrencia y no se pudo reservar.
    */
   Optional<IdempotencyKey> findByKeyWithLock(String key);
   ```

2. **Implementación en `PostgresIdempotencyStore`:**
   - Query nativa: `SELECT * FROM idempotency_keys WHERE key = ? FOR UPDATE SKIP LOCKED` (PostgreSQL 9.5+).
   - Si retorna fila → `Optional.of(existing)`.
   - Si no retorna fila → `INSERT` con `ON CONFLICT (key) DO NOTHING RETURNING *` (o `INSERT` + catch unique violation) para reservar el slot atomically.
   - La transacción (@Transactional en el use case) mantiene el lock hasta commit.

3. **Cambio en `PlaceOrderUseCase.execute()`:**
   - **Eliminar** el `findByKey` previo al lock de usuario (línea 68).
   - **Llamar** `idempotencyStore.findByKeyWithLock(command.idempotencyKey())` **después** del lock de usuario (o al inicio de la transacción, antes de validar instrumento).
   - Si retorna `Optional.of(existing)` → validar hash y devolver cacheado.
   - Si retorna `Optional.empty()` → procesar orden, luego `save()` (el slot ya está reservado).

### Opción B — Manejo de excepción de BD única (fallback más simple)

Si la Opción A resulta compleja por dialecto SQL:
1. Mantener el flujo actual pero **envolver `save()` en try-catch** de `DataIntegrityViolationException` (o `ConstraintViolationException`).
2. Al atrapar violación de `ux_idempotency_keys_key` → relanzar `IdempotencyConflictException(key)`.
3. Esto convierte el 500 en 409 controlado. **No elimina la race** (dos hilos procesan la orden), pero al menos el cliente recibe error correcto. **No recomendada** porque viola "exactly once" (dos ejecuciones de negocio).

> **Decisión:** Implementar **Opción A**. Es la única que garantiza **exactly-once** real (solo un hilo entra a la lógica de negocio). La Opción B es un parche que permite doble procesamiento.

## 5. Contratos afectados

No se cambian los contratos externos (API REST). Se refuerza la consistencia interna del puerto `IdempotencyStorePort`.

## 6. Impacto en requerimientos previos

- Complementa al `005-fix-balance-validation-race` que introdujo el lock por usuario (`userRepositoryPort.lockById`).
- El lock de usuario **sigue siendo necesario** para validar saldo/tenencia con `SELECT FOR UPDATE` sobre `users` y `orders` (evita race en el balance). La nueva protección de idempotencia es **ortogonal**: protege la key globalmente; el lock de usuario protege el estado de la cuenta.

## 7. NFR específicos

- **Consistencia:** Exactly-once por `idempotency_key` global (cross-user).
- **Latencia:** Un `SELECT FOR UPDATE` adicional por request (despreciable, < 1ms en Postgres local).
- **Observabilidad:** Loggear a nivel `DEBUG` cuando se detecta key existente vs. slot reservado.

## 8. Supuestos y pendientes

- **SUPUESTO:** PostgreSQL soporta `FOR UPDATE SKIP LOCKED` (sí, desde 9.5). Si el dialecto JPA no lo mapea bien, usar query nativa (`@Query(nativeQuery = true)`).
- **PENDIENTE:** Verificar que el test concurrente pase en CI (Testcontainers + paralelismo real). Si falla por aislamiento de transacción, ajustar a `REPEATABLE_READ` o `SERIALIZABLE` en el test.

## 9. Definition of Done

- [ ] Criterios de aceptación CA-01..CA-05 verificados (tests nuevos + regresión)
- [ ] `./gradlew test` verde (incluye test concurrente nuevo)
- [ ] Cobertura ≥ 80 % en los servicios de dominio tocados (`IdempotencyStorePort` impl, `PlaceOrderUseCase`)
- [ ] Reviewer emitió ✅ en `review.md`
- [ ] ADR-009 actualizado o creado documentando la decisión de `SELECT FOR UPDATE` vs `ON CONFLICT`

---

## Apéndice: Archivos que se espera modificar

| Archivo | Tipo de cambio |
| :--- | :--- |
| `domain/port/out/IdempotencyStorePort.java` | Nuevo método `findByKeyWithLock` |
| `infrastructure/adapter/out/persistence/PostgresIdempotencyStore.java` | Implementación con query nativa `FOR UPDATE SKIP LOCKED` + `INSERT ... ON CONFLICT` |
| `infrastructure/adapter/out/persistence/IdempotencyKeyJpaRepository.java` | Método de query nativa para lock |
| `application/usecase/PlaceOrderUseCase.java` | Reordenar: lock usuario → idempotencia con lock → procesar → save |
| `src/test/integration/IdempotencyRaceConditionIT.java` | **Nuevo** test concurrente (CA-01, CA-02) |
| `src/test/unit/application/PlaceOrderUseCaseTest.java` | Actualizar mocks y verificar orden de llamadas |
| `docs/adr/003-idempotency-race-condition.md` | **Nuevo** ADR documentando la solución |