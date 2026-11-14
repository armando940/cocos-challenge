# Tasks Checklist — 005 Eliminar race condition en la validación de saldo (lock pesimista por usuario)

> Formato: `- [ ] R005-TXX — Descripción | Fase N | ola: WN | depende de: R005-TYY | toca: <rutas> | ~Xh | done: <criterio>`
> Derivado de [`plan.md`](plan.md) el 2026-11-14. `[x]` solo si el repo lo demuestra hoy.
> `toca:` son las ÚNICAS rutas que el implementer puede editar en esa tarea. Sin `toca:` = serial.
> Este requerimiento no toca archivos de frontera: no hay `SHARED(...)`.

## Fase 1 — Infraestructura del lock

- [x] R005-T01 — Crear `UserRepositoryPort` (`Optional<Long> lockById(Long userId)`), `UserJpaRepository` (`@Lock(PESSIMISTIC_WRITE)` + `@Query` `findByIdWithLock`) y `PostgresUserRepository` (mapea `UserEntity::getId`, sin tocar `PersistenceMapper`) | Fase 1 | ola: W1 | — | toca: `src/main/java/ar/com/cocos/challenge/domain/port/out/UserRepositoryPort.java`, `src/main/java/ar/com/cocos/challenge/infrastructure/adapter/out/persistence/UserJpaRepository.java`, `src/main/java/ar/com/cocos/challenge/infrastructure/adapter/out/persistence/PostgresUserRepository.java` | ~1.5h | done: `./gradlew compileJava` verde y `grep -c "PESSIMISTIC_WRITE" src/main/java/ar/com/cocos/challenge/infrastructure/adapter/out/persistence/UserJpaRepository.java` ≥ 1
- [x] R005-T02 — Crear ADR-007 con contexto, decisión (`SELECT ... FOR UPDATE` sobre `users.id`), consecuencias y alternativas descartadas (lock de `FILLED` por phantoms, `SERIALIZABLE`, tabla `accounts`) | Fase 1 | ola: W1 | — | toca: `docs/adr/007-lock-pesimista-por-usuario.md` | ~1h | done: archivo existe con las 4 secciones y nombra las 3 alternativas

## Fase 2 — Integración transaccional

- [x] R005-T03 — Inyectar `UserRepositoryPort` en `PlaceOrderUseCase` y llamar `lockById(command.userId())` después del bloque de idempotencia y antes de instrumento/precio/`balanceValidator.validate`; ignorar `Optional` (userId inexistente = no-op) | Fase 2 | ola: W2 | depende de: R005-T01 | toca: `src/main/java/ar/com/cocos/challenge/application/usecase/PlaceOrderUseCase.java` | ~1h | done: `grep -n "lockById"` precede a `balanceValidator.validate` y a `resolveExecutionPrice`; `./gradlew compileJava` verde

## Fase 3 — Verificación y cierre

- [x] R005-T04 — Crear `PlaceOrderUseCaseTest` (Mockito `InOrder`): `lockById` antes de `balanceValidator.validate`, `lockById` antes de `orderRepository.findFilledByUserId` (con `BalanceValidator` real o spy) y replay idempotente sin `lockById` | Fase 3 | ola: W3 | depende de: R005-T03 | toca: `src/test/java/ar/com/cocos/challenge/unit/application/PlaceOrderUseCaseTest.java` | ~1.5h | done: `./gradlew test --tests "*PlaceOrderUseCaseTest"` verde
- [x] R005-T05 — Crear `ConcurrentOrderIT` (Testcontainers PostgreSQL): fixture propio por método vía `JdbcTemplate` (usuario con 500.000 ARS vía CASH_IN FILLED e instrumento dedicado con marketdata close 100.00); 6 `BUY MARKET` concurrentes `amount=200000` (size 2000, costo 200.000) → exactamente 2 `FILLED` + 4 `REJECTED` y `availableCash` final = 100.000; segundo método con 2 usuarios distintos en paralelo → ambos `FILLED` (CA-03) | Fase 3 | ola: W3 | depende de: R005-T03 | toca: `src/test/java/ar/com/cocos/challenge/integration/ConcurrentOrderIT.java` | ~2h | done: `./gradlew test --tests "*ConcurrentOrderIT"` verde con asserts de conteo exacto y `isEqualByComparingTo("100000")`
- [x] R005-T06 — Crear `ConcurrentSellAndCashOutIT` (Testcontainers PostgreSQL): CA-05 `SELL MARKET` 2 × 100 sobre posición FILLED de 100 → 1 `FILLED` + 1 `REJECTED` `INSUFFICIENT_SHARES`; CA-06 `CASH_OUT MARKET` 2 × 300.000 sobre 500.000 → 1 `FILLED` + 1 `REJECTED` y saldo final = 200.000; fixtures propios por método (`JdbcTemplate`) y `marketdata` del instrumento ARS = 1.00 en setup | Fase 3 | ola: W3 | depende de: R005-T03 | toca: `src/test/java/ar/com/cocos/challenge/integration/ConcurrentSellAndCashOutIT.java` | ~2h | done: `./gradlew test --tests "*ConcurrentSellAndCashOutIT"` verde con ambos escenarios
- [x] R005-T07 — Agregar la fila del ADR-007 en el índice de ADRs | Fase 3 | ola: W2 | depende de: R005-T02 | toca: `docs/adr/README.md` | ~0.25h | done: `grep "007-lock-pesimista-por-usuario" docs/adr/README.md` devuelve la fila

## Buffer

- [ ] R005-T90 — (no ejecutada: los ITs fueron estables en la primera corrida; buffer no utilizado)
