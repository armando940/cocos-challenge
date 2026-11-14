# ADR-007 — Lock pesimista por usuario en la validación de saldo

- **Estado:** Aceptado
- **Fecha:** 2026-11-14
- **Decisores:** Armando Silva

## Contexto

`PlaceOrderUseCase.execute()` es `@Transactional` y valida saldo/tenencia llamando a
`BalanceValidator`, que calcula el portfolio con `PortfolioCalculator`. Ese cálculo lee
todas las órdenes `FILLED` del usuario con `orderRepository.findFilledByUserId(userId)`
(y, para tenencia, `findFilledByUserIdAndInstrumentId`). Esa lectura **no adquiere
ningún lock**.

Con dos requests concurrentes del mismo usuario (dos `BUY`, dos `SELL` o dos `CASH_OUT`)
se produce un lost update / write skew: ambas transacciones leen el mismo conjunto de
órdenes `FILLED` sin ver la orden de la otra —todavía no commiteada—, ambas pasan la
validación y ambas se persisten como `FILLED`. El saldo disponible derivado queda
negativo: sobregiro silencioso.

El baseline `specs/000-baseline-challenge/spec.md` exige explícitamente
`SELECT FOR UPDATE` al validar saldo y tenencia (§2.2 regla 5, §2.3, §2.5 punto 6).
Hoy ese lock solo existe para cancelar órdenes (`findByIdWithLock` en
`CancelOrderUseCase`), no para la validación de saldo. El delta y las decisiones de
diseño de este cambio están en `specs/005-fix-balance-validation-race/spec.md`.

## Decisión

Adquirir un lock pesimista (`SELECT ... FOR UPDATE`) sobre la fila del usuario
(`users.id = :userId`) al inicio de la transacción de `PlaceOrderUseCase`, antes de
cualquier lectura de órdenes `FILLED` usada para validar saldo o tenencia, y mantenerlo
hasta el commit.

Implementación:

- Nuevo `UserRepositoryPort.lockById(Long userId)`. El dominio sigue dependiendo de una
  interfaz y no conoce JPA.
- `PostgresUserRepository` implementa el puerto delegando en
  `UserJpaRepository.findByIdWithLock`, anotado con `@Lock(LockModeType.PESSIMISTIC_WRITE)`.
- En `PlaceOrderUseCase.execute()`, el lock se adquiere después del chequeo de
  idempotencia (que no lee saldo) y antes de `BalanceValidator.validate()`.

La fila de `users` funciona como un **mutex estable por cuenta**:

- El lock se toma como primer acceso a datos relevante de la transacción y se libera
  recién en el commit o rollback.
- La segunda transacción del mismo usuario queda bloqueada hasta el commit de la primera.
- Bajo `READ_COMMITTED` —el default de PostgreSQL y del proyecto— cada sentencia toma un
  snapshot nuevo; al desbloquearse, la lectura de `FILLED` de la segunda transacción
  **sí ve** la orden recién commiteada y vuelve a validar sobre el saldo correcto.
- Usuarios distintos no se bloquean entre sí: el lock es por fila, no de tabla.

Si el `userId` no existe, el lock no encuentra fila y se comporta como no-op; la orden
termina fallando por la FK de `orders.user_id`, igual que hoy. No se agrega un código de
error nuevo y no se modifican contratos REST.

## Consecuencias

**Positivas**

- Nunca se produce sobregiro por concurrencia del mismo usuario: validación, persistencia
  y ejecución `MARKET` quedan atómicas por cuenta.
- Lock por fila: usuarios distintos siguen operando en paralelo sin serializarse.
- El dominio permanece sin imports de JPA; el lock está aislado en un puerto mockeable.
- El patrón reutiliza el `findByIdWithLock` ya usado en la cancelación de órdenes, sin
  introducir mecanismos de concurrencia nuevos.

**Negativas / costos**

- Los requests del mismo usuario se serializan durante la ventana de la transacción; es
  el precio de la consistencia del saldo.
- El lock se toma también para `CASH_IN`, por simplicidad y para serializar el ledger de
  caja; el costo se considera despreciable.
- Si el `userId` no existe, el lock es no-op y la falla se manifiesta por la FK, como
  hoy.
- No resuelve la race de idempotencia entre dos requests con la misma `idempotencyKey`;
  el lock la serializa de facto para el mismo usuario, pero ese no es el objetivo del
  requerimiento.

## Alternativas consideradas

1. **`SELECT FOR UPDATE` sobre las órdenes `FILLED`.** Descartada: las filas que importan
   son las que la transacción concurrente va a insertar; un `FOR UPDATE` no bloquea
   phantoms ni inserciones nuevas, por lo que el sobregiro persiste.
2. **Subir el aislamiento de la transacción a `SERIALIZABLE`.** Descartada: introduce
   errores de serialización y reintentos en todo el flujo, con alcance global, para
   resolver un problema acotado a las operaciones de un mismo usuario.
3. **Tabla `accounts`/saldo materializado con lock propio.** Descartada: implica rediseñar
   el modelo de saldo, que hoy se deriva on-the-fly de las órdenes `FILLED`; queda fuera
   del alcance de este requerimiento.
