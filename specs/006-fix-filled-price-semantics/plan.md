# Plan de Ejecución — 006 Separar precio límite de precio de ejecución (`Order.filled`) y desacoplar el portfolio

> Derivado de [`spec.md`](spec.md) y del estado real del repo el 2026-11-14.
> Los IDs de tarea son `R006-T01`, `R006-T02`… y solo existen dentro de este requerimiento.

## Resumen

- **Fases**: 4 (Semántica de dominio, Compatibilidad de datos, Documentación e integración, Verificación y cierre) + buffer
- **Olas**: 4 operativas (W1…W4) + W9 buffer — 7 tareas, 6 paralelizables por archivos (3 en W1, 2 en W3)
- **Horas estimadas**: ~5.5 h (+1 h de buffer)
- **Camino crítico**: R006-T01 → R006-T04 → R006-T06 → R006-T07
- **Riesgo mayor**: R006-T01 — el constructor compacto de `Order` pasa a rechazar todo `FILLED` sin `executedPrice`/`executedSize`; cualquier construcción legada (mappers de persistencia, fixtures de tests no listados) rompe la suite de forma ruidosa antes de que el resto del plan pueda ejecutarse

## Estado actual vs plan

| Fase | Objetivo | Estado | Evidencia |
| :--- | :--- | :--- | :--- |
| 1 | Semántica de dominio: `filled()` preserva `price`, invariante FILLED fail-fast, portfolio usa `executedPrice()` | ⬜ Pendiente | `src/main/java/ar/com/cocos/challenge/domain/model/Order.java` (línea 109 pisa `price`), `src/main/java/ar/com/cocos/challenge/domain/service/PortfolioCalculator.java` (líneas 70, 173, 182) |
| 2 | Compatibilidad de datos: backfill de órdenes FILLED legadas (`executed_price IS NULL`) | ⬜ Pendiente | faltan `src/main/resources/db/migration/V5__backfill_executed_price.sql`; migraciones existentes: V1–V4 |
| 3 | Documentación e integración: ADR-008 y IT que verifica `price` vs `executed_price` en BD | ⬜ Pendiente | faltan `docs/adr/008-precio-limite-vs-ejecucion.md` y fila en `docs/adr/README.md` (hoy llega a 007); `PlaceOrderFlowIT.java` no usa `JdbcTemplate` |
| 4 | Verificación y cierre: suite completa verde y cobertura ≥80% en `PortfolioCalculator` | ⬜ Pendiente | `./gradlew test`, `./gradlew check` (no ejecutados sobre el cambio) |

## Fase 1 — Semántica de dominio (~3h)

**Objetivo**: Alinear modelo y portfolio a la semántica `price` = límite original / `executedPrice` = precio real de ejecución, con fail-fast de estados FILLED inválidos.
**Entregable**: `Order` con invariante FILLED y `filled()` que no pisa `price`; `PortfolioCalculator` leyendo exclusivamente `executedPrice()`; tests unitarios de `Order`, `PortfolioCalculator` y `OrderMatcher` alineados.
**DoD**: `./gradlew test --tests "*OrderTest*" --tests "*PortfolioCalculatorTest*" --tests "*OrderMatcherTest*"` verde.

| ID | Tarea | Depende de | Estimación | Riesgo | Done |
| :--- | :--- | :--- | :--- | :--- | :--- |
| R006-T01 | `Order.filled(executedPrice, executedSize)` preserva `price` y escribe sólo `executedPrice`/`executedSize`; constructor compacto impone FILLED válido (`executedPrice != null && > 0`, `executedSize != null`, `0 <= executedSize <= size`); crear `OrderTest` con CA-01/CA-02/CA-03 | — | 1 h | Alto | `./gradlew test --tests "*unit.domain.OrderTest*"` verde y `grep -n "OrderStatus.FILLED" src/main/java/ar/com/cocos/challenge/domain/model/Order.java` muestra `size, price, type` (no `executedPrice`) en `filled()` |
| R006-T02 | `PortfolioCalculator.cashEffect` y `calculateWeightedAverageCost` usan `executedPrice()`; eliminar el fallback `?: BigDecimal.ZERO`; actualizar `PortfolioCalculatorTest` (helper `filled(...)` como MARKET `price = null` + `executedPrice` seteado, casos LIMIT con `price != executedPrice`, reemplazar `handlesBuyWithoutPrice`) | — (dependencia semántica no bloqueante con R006-T01: archivos disjuntos y API de `Order` sin cambios) | 1.5 h | Medio | `grep -n "price()" src/main/java/ar/com/cocos/challenge/domain/service/PortfolioCalculator.java` sin coincidencias; `./gradlew test --tests "*PortfolioCalculatorTest*"` verde |
| R006-T03 | Alinear `OrderMatcherTest`: MARKET FILLED pasa a esperar `price == null` (líneas ~121 y ~162) y `executedPrice`/`executedSize` seteados | — | 0.5 h | Bajo | `./gradlew test --tests "*OrderMatcherTest*"` verde y `grep -n "filled.price()).isNull()" src/test/java/ar/com/cocos/challenge/unit/domain/OrderMatcherTest.java` muestra 2 coincidencias |

## Fase 2 — Compatibilidad de datos (~0.5h)

**Objetivo**: Completar `executed_price`/`executed_size` de órdenes FILLED históricas que V1 dejó en `NULL`, sin tocar V1–V4.
**Entregable**: `V5__backfill_executed_price.sql` con `UPDATE` acotado a `status = 'FILLED' AND executed_price IS NULL`.
**DoD**: `./gradlew test --tests "*PlaceOrderFlowIT*"` verde (Flyway aplica V5 sobre el esquema Testcontainers).

| ID | Tarea | Depende de | Estimación | Riesgo | Done |
| :--- | :--- | :--- | :--- | :--- | :--- |
| R006-T04 | Crear `V5__backfill_executed_price.sql`: `UPDATE orders SET executed_price = price, executed_size = size WHERE status = 'FILLED' AND executed_price IS NULL;` (sin tocar V1–V4 ni `price`) | R006-T01 | 0.5 h | Medio | `grep -n "executed_price = price" src/main/resources/db/migration/V5__backfill_executed_price.sql` y Flyway aplica la migración en la suite de integración |

## Fase 3 — Documentación e integración (~1.5h)

**Objetivo**: Registrar la decisión de semántica de precios y demostrar end-to-end que la persistencia separa `price` de `executed_price` y que el débito usa el precio de ejecución.
**Entregable**: ADR-008 + fila en el índice de ADRs; `PlaceOrderFlowIT` con verificación en BD vía `JdbcTemplate`.
**DoD**: `./gradlew test --tests "*PlaceOrderFlowIT*"` verde y `grep -n "008" docs/adr/README.md` muestra la fila.

| ID | Tarea | Depende de | Estimación | Riesgo | Done |
| :--- | :--- | :--- | :--- | :--- | :--- |
| R006-T05 | Escribir `docs/adr/008-precio-limite-vs-ejecucion.md` (contexto, decisión, consecuencias, alternativas: backfill V5 vs mapeo tolerante) y agregar la fila en `docs/adr/README.md` | R006-T01 | 0.5 h | Bajo | `docs/adr/008-precio-limite-vs-ejecucion.md` existe y `grep -n "008-precio-limite-vs-ejecucion" docs/adr/README.md` devuelve 1 línea |
| R006-T06 | En `PlaceOrderFlowIT`, inyectar `JdbcTemplate` y verificar que una MARKET BUY FILLED persiste `price IS NULL` y `executed_price` = precio capturado; verificar que el débito del portfolio coincide con `executedPrice × size` (CA-04/CA-07) | R006-T01, R006-T04 | 1 h | Medio | `./gradlew test --tests "*PlaceOrderFlowIT*"` verde con aserciones sobre `price`/`executed_price` |

## Fase 4 — Verificación y cierre (~0.5h)

**Objetivo**: Confirmar CA-01..CA-08, suite completa y cobertura del servicio tocado.
**Entregable**: Build y pipeline de calidad verdes; sin tareas de código pendientes.
**DoD**: `./gradlew test` y `./gradlew check` verdes; `jacocoTestCoverageVerification` ≥80% en `PortfolioCalculator`.

| ID | Tarea | Depende de | Estimación | Riesgo | Done |
| :--- | :--- | :--- | :--- | :--- | :--- |
| R006-T07 | Ejecutar `./gradlew test` y `./gradlew check`; revisar el reporte JaCoCo de `PortfolioCalculator`; verificar que no quedó ninguna construcción de FILLED sin `executedPrice` (ni en `src/main` ni en fixtures de test) | R006-T02, R006-T03, R006-T05, R006-T06 | 0.5 h | Bajo | `./gradlew test` verde y `./gradlew check` verde con cobertura ≥80% en `PortfolioCalculator` |

## Olas de ejecución

| Ola | Tareas en paralelo | Lanes | Serializa | Verificación de cierre |
| :-- | :-- | :-- | :-- | :-- |
| W1 | R006-T01, R006-T02, R006-T03 | 3 | — | `./gradlew compileJava test` |
| W2 | R006-T04 | 1 | SHARED(`src/main/resources/db/migration/V5__backfill_executed_price.sql`) — orden de Flyway | `./gradlew test --tests "*PlaceOrderFlowIT*"` |
| W3 | R006-T05, R006-T06 | 2 | — | `./gradlew test --tests "*PlaceOrderFlowIT*"` + verificación de ADR/README |
| W4 | R006-T07 | 1 | — (sin `toca:`, la ejecuta el coordinator) | `./gradlew check` |
| W9 | R006-T90 | 1 | `toca: ?` → serial por default | `./gradlew test` |

> Dos tareas comparten ola solo si no hay dependencia entre ellas y sus rutas `toca:` (en `tasks.md`)
> son disjuntas. Archivos de frontera (`build.gradle.kts`, `application*.yml`, `db/migration`,
> `ExceptionHandler`, `tasks.md`, `state.json`) serializan: la tarea va sola. Máximo 3 lanes.
> Nota W1: R006-T02 consume la semántica que introduce R006-T01, pero no su firma (la API de `Order`
> no cambia y los archivos son disjuntos); comparten ola porque la verificación de cierre compila y
> testea el conjunto.

## Camino crítico

R006-T01 → R006-T04 → R006-T06 → R006-T07

(R006-T01 define la invariante que valida la migración; R006-T04 debe aplicarse antes de que el IT
lea `executed_price`; R006-T06 es la evidencia end-to-end que habilita el cierre. R006-T02 corre en
paralelo en W1 pero también es prerequisito del cierre porque `Order.filled()` inválido y portfolio
desalineado no pueden coexistir en verde.)

## Riesgos y mitigación

| Riesgo | Probabilidad | Impacto | Mitigación |
| :--- | :--- | :--- | :--- |
| El constructor compacto de `Order` rompe construcciones FILLED legadas (mappers JPA, fixtures de `ConcurrentOrderIT`/`ConcurrentSellAndCashOutIT`) | Media | Alto | R006-T01 corre primero en W1; R006-T03 alinea `OrderMatcherTest` en el mismo cierre; R006-T07 hace barrido explícito de construcciones FILLED en `src/main` y `src/test` con `grep` |
| `PortfolioCalculator` deja de compilar o rompe tests por `price()` nullable en LIMIT (`price != executedPrice`) | Media | Medio | R006-T02 cubre explícitamente el caso LIMIT con precio distinto al de ejecución y elimina el fallback a ZERO |
| Migración V5 aplicada sobre una BD donde `executed_size` no existe o `price` es NULL en FILLED (datos sucios) | Baja | Medio | `UPDATE` acotado a `status = 'FILLED' AND executed_price IS NULL`; V2 ya agrega la columna; verificar con el IT de W2/W3 |
| IT flaky por depender del precio de mercado real en Testcontainers | Baja | Medio | R006-T06 compara contra el `executedPrice` devuelto por la propia respuesta del `POST`, no contra un precio esperado hardcodeado |
| Cobertura <80% en `PortfolioCalculator` tras quitar el branch de fallback | Baja | Medio | R006-T02 agrega casos LIMIT/MARKET que cubren las ramas de `cashEffect` y `calculateWeightedAverageCost`; R006-T07 verifica el reporte JaCoCo |
| Desalineación entre ADR-008 y el comportamiento final (si se ajusta en ejecución) | Baja | Bajo | R006-T05 se ejecuta en W3, después de que W1 estabilice la semántica |
