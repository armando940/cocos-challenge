# Tasks Checklist — 012 `datetime` nullable en MarketData y valoración degradada del portfolio

> Formato: `- [ ] RNNN-TXX — Descripción | Fase N | ola: WN | depende de: RNNN-TYY | toca: <rutas> | ~Xh | done: <criterio>`
> Derivado de [`plan.md`](plan.md) el 2026-11-16. `[x]` solo si el repo lo demuestra hoy.
> `toca:` son las ÚNICAS rutas que el implementer puede editar en esa tarea. Sin `toca:` = serial.
> Reestructurado por el coordinator: cada lane de W1 incluye su test para cerrar la ola con la suite
> verde (el corte código/tests dejaba rojo `throwsWhenPriceIsMissing`).

## Fase 1 — Implementación y tests

- [x] R012-T01 — Quitar `nullable = false` de `datetime` y actualizar el Javadoc (columna nullable en V1/V2; `close`/`previousClose` siguen obligatorios por `MarketPrice`); agregar en `MarketDataJpaAdapterIT` el test de hidratación con `datetime NULL` vía `marketDataRepository.findById` (ticker `TESTG`, `INSERT … RETURNING id`) | Fase 1 | ola: W1 | depende de: — | toca: `src/main/java/ar/com/cocos/challenge/infrastructure/adapter/out/persistence/MarketDataEntity.java`, `src/test/java/ar/com/cocos/challenge/integration/MarketDataJpaAdapterIT.java` | ~1.25h | done: `grep -n "nullable = false" src/main/java/ar/com/cocos/challenge/infrastructure/adapter/out/persistence/MarketDataEntity.java` solo muestra `instrumentId`, `close`, `previousClose` y `createdAt`; `./gradlew test --tests "ar.com.cocos.challenge.integration.MarketDataJpaAdapterIT"` verde

- [x] R012-T02 — Validar `entity.getDatetime() == null` en `PersistenceMapper.toDomain(MarketDataEntity)` con `IllegalStateException("MarketData sin datetime (id=…): no se puede construir MarketPrice")`, sin imports nuevos; crear `PersistenceMapperTest` (POJO puro) con CA-02 y CA-03 | Fase 1 | ola: W1 | depende de: — | toca: `src/main/java/ar/com/cocos/challenge/infrastructure/adapter/out/persistence/PersistenceMapper.java`, `src/test/java/ar/com/cocos/challenge/unit/infrastructure/PersistenceMapperTest.java` | ~1.25h | done: `grep -n "MarketData sin datetime" src/main/java/ar/com/cocos/challenge/infrastructure/adapter/out/persistence/PersistenceMapper.java`; `./gradlew test --tests "ar.com.cocos.challenge.unit.infrastructure.PersistenceMapperTest"` verde

- [x] R012-T03 — Reordenar `buildPosition`: instrumento (mantener throw `Instrumento no encontrado`) → `netQuantity` → `Position.empty` si ≤ 0 → `Position` degradada si no hay precio (`marketValue = Money.zero()`, `totalCost` ponderado real, returns `0`); rama con precio intacta; reemplazar `throwsWhenPriceIsMissing` por el test degradado y agregar el caso flat sin precio | Fase 1 | ola: W1 | depende de: — | toca: `src/main/java/ar/com/cocos/challenge/domain/service/PortfolioCalculator.java`, `src/test/java/ar/com/cocos/challenge/unit/domain/PortfolioCalculatorTest.java` | ~2h | done: `grep -n "Precio no disponible" src/main/java/ar/com/cocos/challenge/domain/service/PortfolioCalculator.java` sin resultados; `./gradlew test --tests "ar.com.cocos.challenge.unit.domain.PortfolioCalculatorTest"` verde

## Fase 2 — ADR

- [x] R012-T04 — Crear `docs/adr/011-marketdata-datetime-nullable-graceful-valuation.md` (contexto, decisión con entidad alineada + guard en mapper + valoración degradada y filtros de 011 preservados; consecuencias; alternativas; referencias), insertar el banner `> **Superseded by [ADR-011](011-marketdata-datetime-nullable-graceful-valuation.md)** (2026-11-16)` tras el H1 del ADR-010 sin reescribir su cuerpo y actualizar `docs/adr/README.md` (fila 011; 010 → `Superseded por 011`) | Fase 2 | ola: W2 | depende de: R012-T01, R012-T02, R012-T03 | toca: `docs/adr/011-marketdata-datetime-nullable-graceful-valuation.md`, `docs/adr/010-marketdata-nullability-legacy.md`, `docs/adr/README.md` | ~1h | done: `Test-Path docs/adr/011-marketdata-datetime-nullable-graceful-valuation.md`; `grep -n "Superseded by" docs/adr/010-marketdata-nullability-legacy.md`; `grep -n "011-marketdata-datetime" docs/adr/README.md`

## Buffer

- [ ] R012-T90 — Si `./gradlew check` falla por cobertura < 80 % en `PortfolioCalculator`, ampliar el test degradado/plano hasta cubrir las ramas nuevas | Buffer | ola: W9 | depende de: R012-T03 | toca: `src/test/java/ar/com/cocos/challenge/unit/domain/PortfolioCalculatorTest.java` | ~0.5h | done: `./gradlew check` verde

- [ ] R012-T91 — Si el test del IT colisiona con datos de Testcontainers o `RETURNING id` no se lee con `JdbcTemplate`, aislar con ticker/id únicos o usar `KeyHolder` | Buffer | ola: W9 | depende de: R012-T01 | toca: `src/test/java/ar/com/cocos/challenge/integration/MarketDataJpaAdapterIT.java` | ~0.5h | done: `./gradlew test --tests "ar.com.cocos.challenge.integration.MarketDataJpaAdapterIT"` verde

- [ ] R012-T92 — Si el banner altera el cuerpo del ADR-010 o la tabla del índice, restaurar y reaplicar solo el banner | Buffer | ola: W9 | depende de: R012-T04 | toca: `docs/adr/010-marketdata-nullability-legacy.md`, `docs/adr/README.md` | ~0.5h | done: `git diff --numstat docs/adr/010-marketdata-nullability-legacy.md` sin líneas eliminadas

> Cierre de ola (coordinator, fuera de lanes): W1 `./gradlew compileJava test`; W2 `./gradlew check`.
