# Requerimiento 005 — Eliminar race condition en la validación de saldo (lock pesimista por usuario)

| Campo | Valor |
| :--- | :--- |
| **Número** | 005 |
| **Carpeta** | `specs/005-fix-balance-validation-race/` |
| **Tipo** | bugfix |
| **Estado** | done |
| **Creado** | 2026-11-14 |
| **Actualizado** | 2026-11-14 |
| **Depende de** | 000, 004 |
| **Supersede a** | — |

> **Alcance de este documento:** solo el **delta** de este requerimiento. Las invariantes del
> proyecto (hexagonal, `BigDecimal`, transacciones, locks, idempotencia, secretos) son globales y
> viven en `AGENTS.md` y en [`../000-baseline-challenge/spec.md`](../000-baseline-challenge/spec.md).
> No las repitas: referencialas. Los ADR aplicables están en `docs/adr/`.

---

## 1. Contexto y motivación

`PlaceOrderUseCase.execute()` está anotado `@Transactional` y valida saldo/tenencia llamando a
`BalanceValidator.validate()`. Ese validador calcula el portfolio con `PortfolioCalculator.calculate()`,
que a su vez lee **todas las órdenes `FILLED` del usuario** con
`orderRepository.findFilledByUserId(userId)`. Esa lectura **no adquiere ningún lock**.

Consecuencia (lost update / write skew):

1. Dos requests `BUY` del mismo usuario entran en transacciones concurrentes.
2. Ambas leen el mismo conjunto de órdenes `FILLED` (sin la orden de la otra, todavía no commiteada).
3. Ambas ven saldo suficiente y pasan la validación.
4. Ambas se persisten como `FILLED` (las `MARKET` se ejecutan en la misma transacción).
5. El saldo disponible derivado queda negativo: **sobregiro silencioso**.

El baseline `000` exige explícitamente `SELECT FOR UPDATE` al validar saldo y tenencia
(§2.2 regla 5, §2.3, §2.5 punto 6). Hoy ese lock solo existe para cancelar órdenes
(`findByIdWithLock` en `CancelOrderUseCase`), no para la validación de saldo.

## 2. Alcance

### Dentro

- Adquirir un **lock pesimista (`SELECT ... FOR UPDATE`) sobre la fila del usuario** al inicio de la
  transacción de `PlaceOrderUseCase`, antes de cualquier lectura de órdenes `FILLED` usada para
  validar saldo o tenencia.
- Mantener el lock hasta el commit de la transacción, de modo que validación + persistencia +
  ejecución `MARKET` sean atómicas **por usuario**.
- Introducir el puerto de salida que expone el lock (p. ej. `UserRepositoryPort.lockById`) y su
  adaptador JPA, respetando la arquitectura hexagonal (el dominio no conoce JPA).
- Tests:
  - unitario que verifique el **orden** lock → `balanceValidator.validate`;
  - integrativo de concurrencia real (Testcontainers + PostgreSQL) que dispare N órdenes `BUY`
    `MARKET` simultáneas del mismo usuario y verifique que **nunca** se sobregira.
- ADR global `docs/adr/007-lock-pesimista-por-usuario.md` documentando la estrategia elegida.

### Fuera

- Cambiar el aislamiento global a `SERIALIZABLE` (se mantiene `READ_COMMITTED` + lock explícito).
- Rediseñar el modelo de saldo (tabla `accounts`/`balances`). El saldo sigue derivándose on-the-fly
  de las órdenes `FILLED`.
- Resolver la race de **idempotencia** (dos requests con la misma `idempotencyKey` concurrentes).
  Queda documentada como supuesto/limitación; el lock por usuario la serializa de facto para el
  mismo usuario, pero no es el objetivo de este requerimiento.
- Modificar contratos REST, DTOs ni códigos de error existentes.
- Lockear lecturas de portfolio (`GetPortfolioUseCase`), que son de solo lectura.

## 3. Decisión de diseño (clarificada)

**Lock sobre la fila del usuario (`users`), no sobre las órdenes `FILLED`.**

`SELECT ... FOR UPDATE` sobre las órdenes existentes **no** impide el fenómeno: las filas que
importan son las que la transacción concurrente va a insertar (phantoms), y un `FOR UPDATE` no
bloquea inserciones nuevas. En cambio, la fila de `users` es un **mutex estable por cuenta**:

- `PlaceOrderUseCase` adquiere `SELECT ... FOR UPDATE` de `users.id = :userId` como **primer acceso
  a datos** de la transacción (después del chequeo de idempotencia, que no lee saldo).
- La segunda transacción del mismo usuario queda bloqueada hasta el commit de la primera.
- Al desbloquearse, bajo `READ_COMMITTED` cada sentencia toma un snapshot nuevo, por lo que su
  lectura de `FILLED` **sí ve** la orden recién commiteada.
- Usuarios distintos no se bloquean entre sí (el lock es por fila, no de tabla).

Si el `userId` no existe, el lock no encuentra fila: se comporta como no-op y la orden termina
fallando por la FK de `orders.user_id`, igual que hoy (no se agrega un código de error nuevo).

## 4. Criterios de aceptación

| ID | Given | When | Then |
| :--- | :--- | :--- | :--- |
| CA-01 | Un usuario con saldo $S$ y N órdenes `BUY MARKET` concurrentes cuyos montos individuales caben en $S$ pero cuya suma lo excede | Se envían las N órdenes en paralelo | Se ejecutan como `FILLED` solo las que caben en $S$; el resto queda `REJECTED` con `INSUFFICIENT_CASH`; `availableCash` final ≥ 0 |
| CA-02 | Un request `POST /orders` | Se ejecuta `PlaceOrderUseCase.execute()` | El lock pesimista de la cuenta se adquiere **antes** de la primera lectura de órdenes `FILLED` y se mantiene hasta el commit |
| CA-03 | Dos usuarios distintos operando a la vez | Ambos envían órdenes concurrentes | No se bloquean entre sí (el lock es por `users.id`) |
| CA-04 | El flujo funcional existente (`PlaceOrderFlowIT`) | Se corre la suite | Sigue verde sin cambios de contrato ni de comportamiento secuencial |
| CA-05 | Un usuario con $Q$ acciones y dos `SELL MARKET` concurrentes de $Q$ cada una | Se envían en paralelo | Solo una se `FILLED`; la otra `REJECTED` con `INSUFFICIENT_SHARES` (misma raíz, mismo lock) |
| CA-06 | Un usuario con saldo $S$ y dos `CASH_OUT MARKET` concurrentes que juntos exceden $S$ | Se envían en paralelo | Solo los que caben se `FILLED`; el resto `REJECTED`; `availableCash` final ≥ 0 |

## 5. Contratos afectados

Ninguno. `POST /api/v1/orders` mantiene request, response y códigos HTTP (201/400/409) idénticos.
El cambio es exclusivamente de concurrencia interna.

## 6. Impacto en requerimientos previos

- Compatible con `000-baseline-challenge`: **cumple** la invariante de `SELECT FOR UPDATE` en la
  validación de saldo/tenencia que el baseline ya declaraba.
- Compatible con `004-fix-market-order-price-drift`: el lock se adquiere con el mismo snapshot de
  precio ya resuelto; no re-introduce descalce.
- No edita carpetas de requerimientos cerrados.

## 7. NFR específicos

- **Consistencia:** `availableCash` derivado nunca < 0 ante concurrencia del mismo usuario.
- **Latencia:** requests de usuarios distintos no se serializan; el lock es por fila.
- **Sin deadlocks nuevos:** se adquiere un único lock (fila de usuario) y antes de cualquier otro
  acceso de escritura.
- **Testeabilidad:** lock aislado en un puerto mockeable; el dominio permanece sin imports de JPA.

## 8. Supuestos y pendientes

- **SUPUESTO:** La fila de `users` existe para todo usuario que puede operar (FK `orders.user_id`).
- **SUPUESTO:** `READ_COMMITTED` (default de PostgreSQL y del proyecto) + lock de fila es suficiente;
  no hace falta subir el aislamiento.
- **SUPUESTO:** El lock se toma para todas las órdenes (incluido `CASH_IN`) por simplicidad y para
  serializar el ledger de caja; el costo es despreciable.
- **PENDIENTE:** ninguno bloqueante.

## 9. Definition of Done

- [x] CA-01..CA-06 verificados con tests automatizados
- [x] `./gradlew test` verde (incluye el IT de concurrencia con Testcontainers)
- [x] `./gradlew check` verde (cobertura ≥ 80 % en servicios de dominio tocados)
- [x] ADR-007 creado y referenciado
- [x] `review.md` con veredicto ✅ del reviewer
