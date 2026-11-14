# Plan de Ejecución — 005 Eliminar race condition en la validación de saldo (lock pesimista por usuario)

> Derivado de [`spec.md`](spec.md), de `specs/000-baseline-challenge/spec.md` (§2.2 regla 5 y §2.5
> punto 6) y del estado real del repo el 2026-11-14. Los IDs de tarea son `R005-TXX` y solo existen
> dentro de esta carpeta.

## Resumen

- **Fases**: 3 — (1) Infraestructura del lock, (2) Integración transaccional, (3) Verificación y cierre.
- **Olas**: W1–W3 de ejecución + W9 de buffer. 7 tareas de requerimiento + 1 de buffer.
- **Horas estimadas**: ~9.5 h (+1 h de buffer) ≈ **10.5 h**.
- **Camino crítico**: `R005-T01 → R005-T03 → R005-T05 → ./gradlew test`.
- **Riesgo mayor**: `R005-T05` / `R005-T06` (ITs de concurrencia real con Testcontainers: flakiness
  por estado compartido del contenedor/seed) y `R005-T03` (ubicación exacta del lock dentro de la
  `@Transactional`: si queda después de una lectura de `FILLED`, el fix no sirve).

## Inventario previo (lo que el repo ya tiene y este requerimiento reutiliza)

| Activo | Estado hoy | Uso en este requerimiento |
| :--- | :--- | :--- |
| `PlaceOrderUseCase.execute()` (`@Transactional`) | ✅ existe; valida saldo sin lock | Punto de inserción del lock (R005-T03) |
| `BalanceValidator.validate()` → `PortfolioCalculator.calculate()` → `findFilledByUserId()` | ✅ lectura sin lock (origen de la race) | Confirma el problema; no se modifica |
| `UserEntity` (`@Entity`, tabla `users`) | ✅ existe | Base para `UserJpaRepository` (R005-T01) |
| `OrderJpaRepository.findByIdWithLock` (`@Lock(PESSIMISTIC_WRITE)` + `@Query`) | ✅ patrón precedente | Replicar el patrón para `users` |
| `CancelOrderUseCase` → `findByIdWithLock` | ✅ lock de orden existente | Evidencia de que el patrón funciona en `@Transactional` |
| Seed `V900__dev_seed.sql` | ✅ usuario 2 = 500.000 ARS, sin posiciones; GGAL close hoy = 11.200 | Fixture de `R005-T05` |
| `OrderRepositoryPort`, `PersistenceMapper`, `DomainConfig` | ✅ existen | No se tocan (el adapter nuevo es `@Repository` y se inyecta por tipo) |
| Testcontainers + JUnit5 + Mockito + AssertJ | ✅ en `build.gradle.kts` | Base de R005-T04/T05/T06; no se toca el build |
| ADRs 001–006 | ✅ existen | ADR-007 es el siguiente número libre |

## Estado actual vs plan

| Fase | Objetivo | Estado | Evidencia |
| :--- | :--- | :--- | :--- |
| 1 | Puerto/adaptador de lock por usuario + ADR | ⬜ Pendiente | No existen `UserRepositoryPort`, `UserJpaRepository` ni `PostgresUserRepository` (`glob` sobre `domain/port/out` y `adapter/out/persistence`); no existe `docs/adr/007-*` |
| 2 | Adquirir el lock en `PlaceOrderUseCase` antes de validar | ⬜ Pendiente | `PlaceOrderUseCase.execute()` solo hace idempotencia → instrumento → precio → `balanceValidator.validate`; no hay `lockById` |
| 3 | Tests de orden y concurrencia + cierre documental | ⬜ Pendiente | No existen `PlaceOrderUseCaseTest` (`unit/application/` no existe), `ConcurrentOrderIT` ni `ConcurrentSellAndCashOutIT`; `docs/adr/README.md` no lista el 007 |

## Fase 1 — Infraestructura del lock (~2.5 h)

**Objetivo**: Exponer un mutex por usuario a través de un puerto hexagonal y su adaptador JPA, sin que
el dominio conozca JPA, y dejar la decisión registrada en un ADR.
**Entregable**: `UserRepositoryPort`, `UserJpaRepository`, `PostgresUserRepository` y
`docs/adr/007-lock-pesimista-por-usuario.md`.
**DoD**: `./gradlew compileJava` verde; el puerto no importa `jakarta.*` ni `org.springframework.*`;
el repositorio JPA contiene `@Lock(LockModeType.PESSIMISTIC_WRITE)`.

| ID | Tarea | Depende de | Estimación | Riesgo | Done |
| :--- | :--- | :--- | :--- | :--- | :--- |
| R005-T01 | Crear `UserRepositoryPort` (`Optional<Long> lockById(Long userId)`), `UserJpaRepository` (`@Lock(PESSIMISTIC_WRITE)` + `@Query` sobre `UserEntity` con `findByIdWithLock`) y `PostgresUserRepository` (`implements UserRepositoryPort`, mapea `UserEntity::getId`, sin tocar `PersistenceMapper`). | — | 1.5 h | Medio | `./gradlew compileJava` verde; `grep -c "PESSIMISTIC_WRITE" src/main/java/.../UserJpaRepository.java` ≥ 1; `UserRepositoryPort.java` sin imports `jakarta.*`/`org.springframework.*` |
| R005-T02 | Crear `docs/adr/007-lock-pesimista-por-usuario.md` con contexto (lost update/write skew), decisión (`SELECT ... FOR UPDATE` sobre `users.id`), consecuencias y alternativas descartadas: lock de órdenes `FILLED` (no bloquea phantoms), `SERIALIZABLE`, tabla `accounts`. | — | 1.0 h | Bajo | Archivo existe con las 4 secciones y las 3 alternativas nombradas explícitamente |

## Fase 2 — Integración transaccional (~1 h)

**Objetivo**: Que toda orden del mismo usuario espere el lock de su fila de `users` antes de leer el
estado con el que valida saldo/tenencia.
**Entregable**: `PlaceOrderUseCase` inyectando `UserRepositoryPort` y llamando `lockById` en el punto
correcto.
**DoD**: La llamada a `lockById` aparece después del bloque `existing.isPresent()` y antes de
`instrumentRepository.findById(...)`, `orderMatcher.createOrder/resolveExecutionPrice` y
`balanceValidator.validate(...)`.

| ID | Tarea | Depende de | Estimación | Riesgo | Done |
| :--- | :--- | :--- | :--- | :--- | :--- |
| R005-T03 | Inyectar `UserRepositoryPort` como campo final (Lombok ya genera el constructor) y ejecutar `userRepositoryPort.lockById(command.userId())` justo después del chequeo de idempotencia. Ignorar el `Optional` (userId inexistente = no-op; la FK de `orders.user_id` falla como hoy). El lock vive hasta el commit porque `execute()` es `@Transactional`. | R005-T01 | 1.0 h | Alto | `grep -n "lockById"` precede a `balanceValidator.validate` y a `resolveExecutionPrice` en `PlaceOrderUseCase.java`; `./gradlew compileJava` verde; no cambian firmas de `PlaceOrderPort` ni DTOs |

## Fase 3 — Verificación y cierre (~5 h)

**Objetivo**: Probar el orden lock → validación a nivel unitario y demostrar con PostgreSQL real que
nunca se sobregira bajo concurrencia; cerrar el índice de ADRs.
**Entregable**: `PlaceOrderUseCaseTest`, `ConcurrentOrderIT`, `ConcurrentSellAndCashOutIT` y la fila
007 en `docs/adr/README.md`.
**DoD**: `./gradlew test` verde (incluye `**/*IT.class` por configuración del build); CA-01..CA-06
cubiertos; `./gradlew check` verde.

| ID | Tarea | Depende de | Estimación | Riesgo | Done |
| :--- | :--- | :--- | :--- | :--- | :--- |
| R005-T04 | Crear `PlaceOrderUseCaseTest` (package `unit.application`) con Mockito: `InOrder` verifica `userRepositoryPort.lockById` antes de `balanceValidator.validate`; con `BalanceValidator` real (o spy) sobre `PortfolioCalculator`/`OrderRepositoryPort` mockeados, verifica que `lockById` precede a `orderRepository.findFilledByUserId`; caso replay idempotente (key existente) no llama `lockById`. | R005-T03 | 1.5 h | Medio | `./gradlew test --tests "*PlaceOrderUseCaseTest"` verde; `InOrder` explícito en el test |
| R005-T05 | Crear `ConcurrentOrderIT` (`@SpringBootTest` + Testcontainers PostgreSQL): fixture propio por método vía `JdbcTemplate` (usuario con 500.000 ARS e instrumento dedicado con close 100.00); 6 `BUY MARKET` con `amount=200000` (size 2000, costo 200.000) y `idempotencyKey` distintas, lanzadas con `ExecutorService`/`CountDownLatch`. Verificar exactamente **2 `FILLED` + 4 `REJECTED`** (`INSUFFICIENT_CASH`) y `availableCash` final = **100.000** (≥ 0). Segundo método: dos usuarios distintos en paralelo → ambos `FILLED` (CA-03). | R005-T03 | 2.0 h | Alto | `./gradlew test --tests "*ConcurrentOrderIT"` verde; asserts `isEqualByComparingTo("100000")` y conteos exactos |
| R005-T06 | Crear `ConcurrentSellAndCashOutIT` (misma base Testcontainers): CA-05 `SELL MARKET` concurrente (2 × 100 sobre posición de 100) → 1 `FILLED` + 1 `REJECTED` `INSUFFICIENT_SHARES`; CA-06 `CASH_OUT MARKET` concurrente (2 × 300.000 sobre 500.000) → 1 `FILLED` + 1 `REJECTED` y `availableCash` final = 200.000. Fixture propio por escenario; garantizar `marketdata` del instrumento ARS = 1.00 en setup porque el seed `V900` no lo incluye. | R005-T03 | 2.0 h | Alto | `./gradlew test --tests "*ConcurrentSellAndCashOutIT"` verde; ambos escenarios con conteos exactos y saldo final esperado |
| R005-T07 | Agregar la fila del ADR-007 en `docs/adr/README.md`. | R005-T02 | 0.25 h | Bajo | `grep "007-lock-pesimista-por-usuario" docs/adr/README.md` devuelve la fila |

## Olas de ejecución

| Ola | Tareas en paralelo | Lanes | Serializa | Verificación de cierre |
| :-- | :-- | :-- | :-- | :-- |
| W1 | R005-T01, R005-T02 | 2 | — (rutas disjuntas: `src/main/.../port/out` + `persistence` vs `docs/adr/007-*.md`) | `./gradlew compileJava` |
| W2 | R005-T03, R005-T07 | 2 | — (T03 espera T01; T07 espera T02; rutas disjuntas) | `./gradlew compileJava` |
| W3 | R005-T04, R005-T05, R005-T06 | 3 | — (archivos disjuntos; T05/T06 levantan 2 contenedores Testcontainers en paralelo, ver riesgos) | `./gradlew test` |
| W9 | R005-T90 | 1 | — | `./gradlew test` |

> Dos tareas comparten ola solo si no hay dependencia entre ellas y sus rutas `toca:` (en `tasks.md`)
> son disjuntas. Este requerimiento **no** toca archivos de frontera (`build.gradle.kts`,
> `application*.yml`, `db/migration/*`, `ExceptionHandler`): cada tarea va en su lane sin
> `SHARED(...)`. Máximo 3 lanes por ola (W3 llega justo al límite).

## Camino crítico

`R005-T01` (puerto + adaptador) → `R005-T03` (lock en el use case) → `R005-T05` (IT que demuestra
CA-01 en PostgreSQL real) → `./gradlew test` de cierre de ola. `R005-T04` y `R005-T06` corren en
paralelo con `R005-T05` en W3 y no lo bloquean; si el coordinator debe serializar por recursos de
Docker, el orden sugerido es T05 → T06 → T04.

## Riesgos y mitigación

| Riesgo | Probabilidad | Impacto | Mitigación |
| :--- | :--- | :--- | :--- |
| El lock se adquiere fuera de la transacción o después de una lectura de `FILLED` → el fix no elimina la race | Media | Alto | `R005-T04` con `InOrder` (lock antes de validate y de `findFilledByUserId`) + revisión temprana de `R005-T03` por `reviewer`; `execute()` sigue `@Transactional` |
| IT de concurrencia flaky por estado compartido: el cache de contexto de Spring puede reutilizar el contenedor de otra clase y contaminar el usuario 2 | Media | Alto | Cada IT prepara su fixture de forma idempotente en `@BeforeEach`/setup (usuario, saldo/tenencia vía `JdbcTemplate`), sin depender del orden de clases; `@DirtiesContext` si el contexto se comparte |
| CA-06 (`CASH_OUT`) falla porque el seed `V900` no trae `marketdata` del instrumento ARS (1) y `resolveExecutionPrice` lo exige | Alta | Medio | `R005-T06` inserta `marketdata` ARS = 1.00 en el setup del IT; si aun así falla, se reporta como hallazgo preexistente fuera de alcance (no se toca el seed ni migraciones) |
| Lock en fila inexistente es no-op y la orden falla por FK (comportamiento esperado) | Baja | Bajo | Documentado en ADR-007 y `R005-T03`; no se agrega código de error nuevo |
| Race de idempotencia (misma `idempotencyKey` concurrente) no resuelta | Baja | Medio | Declarado fuera de alcance en §2 del spec; el lock por usuario la serializa de facto para el mismo usuario |
| Deadlocks o agotamiento del pool por múltiples locks | Baja | Medio | Un único lock (fila de usuario) y siempre primero; Hikari `maximum-pool-size: 20` > 6 hilos del IT |
| Ambigüedad de firma del puerto (`Optional<?>` vs `void`) | Baja | Bajo | Se fija `Optional<Long> lockById(Long userId)`; el adapter mapea `UserEntity::getId`. `void` solo si el implementer documenta el cambio |
| T05/T06 en paralelo levantan 2 contenedores PostgreSQL y estresan Docker en Windows | Media | Medio | El coordinator puede serializar W3 (T05 → T06 → T04); buffer `R005-T90` absorbe timeouts |
