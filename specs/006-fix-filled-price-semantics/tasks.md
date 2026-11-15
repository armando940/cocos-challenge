# Tasks Checklist — 006 Separar precio límite de precio de ejecución (`Order.filled`) y desacoplar el portfolio

> Formato: `- [ ] RNNN-TXX — Descripción | Fase N | ola: WN | depende de: RNNN-TYY | toca: <rutas> | ~Xh | done: <criterio>`
> Derivado de [`plan.md`](plan.md) el 2026-11-14. `[x]` solo si el repo lo demuestra hoy.
> `toca:` son las ÚNICAS rutas que el implementer puede editar en esa tarea. Sin `toca:` = serial.
> `toca: SHARED(<archivo>)` = archivo de frontera: la tarea va sola en su ola.

## Fase 1 — Semántica de dominio

- [x] R006-T01 — `Order.filled()` preserva `price` original y setea solo `executedPrice`/`executedSize`; constructor compacto impone FILLED válido (executedPrice no nulo y > 0, executedSize no nulo, 0 <= executedSize <= size) con IllegalArgumentException; crear `OrderTest` con CA-01/CA-02/CA-03 | Fase 1 | ola: W1 | — | toca: `src/main/java/ar/com/cocos/challenge/domain/model/Order.java`, `src/test/java/ar/com/cocos/challenge/unit/domain/OrderTest.java` | ~1h | done: `./gradlew test --tests "*unit.domain.OrderTest*"` verde
- [x] R006-T02 — `PortfolioCalculator` usa `executedPrice()` en `cashEffect` y `calculateWeightedAverageCost` y elimina el fallback a ZERO; actualizar `PortfolioCalculatorTest` (helper `filled(...)` MARKET con `price = null` + `executedPrice`, casos LIMIT con `price != executedPrice`, reemplazar `handlesBuyWithoutPrice`) | Fase 1 | ola: W1 | — | toca: `src/main/java/ar/com/cocos/challenge/domain/service/PortfolioCalculator.java`, `src/test/java/ar/com/cocos/challenge/unit/domain/PortfolioCalculatorTest.java` | ~1.5h | done: `./gradlew test --tests "*PortfolioCalculatorTest*"` verde y `grep -n "price()" src/main/java/ar/com/cocos/challenge/domain/service/PortfolioCalculator.java` sin coincidencias
- [x] R006-T03 — Alinear `OrderMatcherTest`: MARKET FILLED espera `price == null` (líneas ~121 y ~162) con `executedPrice`/`executedSize` seteados | Fase 1 | ola: W1 | — | toca: `src/test/java/ar/com/cocos/challenge/unit/domain/OrderMatcherTest.java` | ~0.5h | done: `./gradlew test --tests "*OrderMatcherTest*"` verde

## Fase 2 — Compatibilidad de datos

- [x] R006-T04 — Migración `V5__backfill_executed_price.sql`: `UPDATE orders SET executed_price = price, executed_size = size WHERE status = 'FILLED' AND executed_price IS NULL;` sin tocar V1–V4 ni `price` | Fase 2 | ola: W2 | depende de: R006-T01 | toca: SHARED(`src/main/resources/db/migration/V5__backfill_executed_price.sql`) | ~0.5h | done: `grep -n "executed_price = price" src/main/resources/db/migration/V5__backfill_executed_price.sql` y Flyway aplica V5 en `./gradlew test --tests "*PlaceOrderFlowIT*"`

## Fase 3 — Documentación e integración

- [x] R006-T05 — ADR-008 (contexto, decisión, consecuencias, alternativas de compatibilidad) + fila en el índice de ADRs | Fase 3 | ola: W3 | depende de: R006-T01 | toca: `docs/adr/008-precio-limite-vs-ejecucion.md`, `docs/adr/README.md` | ~0.5h | done: `grep -n "008-precio-limite-vs-ejecucion" docs/adr/README.md` devuelve 1 línea y el archivo existe
- [x] R006-T06 — `PlaceOrderFlowIT`: inyectar `JdbcTemplate` y verificar que una MARKET BUY FILLED persiste `price IS NULL` y `executed_price` = precio capturado, y que el débito del portfolio coincide con `executedPrice × size` | Fase 3 | ola: W3 | depende de: R006-T01, R006-T04 | toca: `src/test/java/ar/com/cocos/challenge/integration/PlaceOrderFlowIT.java` | ~1h | done: `./gradlew test --tests "*PlaceOrderFlowIT*"` verde con aserciones sobre `price`/`executed_price`

## Fase 4 — Verificación y cierre

- [x] R006-T07 — Ejecutar `./gradlew test` y `./gradlew check`; verificar cobertura ≥80% en `PortfolioCalculator` y barrido de construcciones FILLED sin `executedPrice` en `src/main` y `src/test` | Fase 4 | ola: W4 | depende de: R006-T02, R006-T03, R006-T05, R006-T06 | toca: — | ~0.5h | done: `./gradlew test` verde y `./gradlew check` verde con `jacocoTestCoverageVerification` ≥80% en `PortfolioCalculator`

## Buffer

- [ ] R006-T90 — Imprevisto: ajuste en mappers/fixtures de persistencia que construyan FILLED sin `executedPrice` por la nueva invariante de `Order` | Buffer | ola: W9 | — | toca: ? | ~1h | done: `./gradlew test` verde
