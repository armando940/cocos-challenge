# Tasks Checklist — 011 Alinear nullability de MarketDataEntity con el esquema legacy

> Formato: `- [ ] RNNN-TXX — Descripción | Fase N | ola: WN | depende de: RNNN-TYY | toca: <rutas> | ~Xh | done: <criterio>`
> Derivado de [`plan.md`](plan.md) el 2026-11-16. `[x]` solo si el repo lo demuestra hoy.
> `toca:` son las ÚNICAS rutas que el implementer puede editar en esa tarea. Sin `toca:` = serial.
> `toca: SHARED(<archivo>)` = archivo de frontera: la tarea va sola en su ola.

## Fase 1 — Entidad y repositorio

- [x] R011-T01 — Quitar `nullable = false` de `high`, `low` y `open`; documentar OHLC legacy opcional y mantener no nulos `instrumentId`, `close`, `previousClose`, `datetime` y `createdAt` | Fase 1 | ola: W1 | depende de: — | toca: `src/main/java/ar/com/cocos/challenge/infrastructure/adapter/out/persistence/MarketDataEntity.java` | ~0.5h | done: `grep -n "nullable = false" src/main/java/ar/com/cocos/challenge/infrastructure/adapter/out/persistence/MarketDataEntity.java` solo muestra `instrumentId`, `close`, `previousClose`, `datetime` y `createdAt`

- [x] R011-T02 — Eliminar `findByInstrumentIdOrderByDatetimeDesc`; renombrar `findFirstByInstrumentIdOrderByDatetimeDesc` a `findFirstByInstrumentIdAndDatetimeIsNotNullOrderByDatetimeDesc`; agregar `datetime IS NOT NULL` a la native `DISTINCT ON`; actualizar la llamada en `MarketDataJpaAdapter` | Fase 1 | ola: W2 | depende de: R011-T01 | toca: `src/main/java/ar/com/cocos/challenge/infrastructure/adapter/out/persistence/MarketDataJpaRepository.java`, `src/main/java/ar/com/cocos/challenge/infrastructure/adapter/out/marketdata/MarketDataJpaAdapter.java` | ~1h | done: `grep -n "findFirstByInstrumentIdAndDatetimeIsNotNullOrderByDatetimeDesc"` aparece en repositorio y adapter; `grep -n "datetime IS NOT NULL"` aparece en el repositorio; `./gradlew compileJava` verde

## Fase 2 — Tests de integración

- [x] R011-T03 — Agregar `findLatestPrice_legacyOhlcNulls_returnsMarketPrice` y `findLatestPriceAndLatestPrices_ignoreNullDatetime` con instrumentos/tickers propios y sin depender del seed | Fase 2 | ola: W3 | depende de: R011-T01, R011-T02 | toca: `src/test/java/ar/com/cocos/challenge/integration/MarketDataJpaAdapterIT.java` | ~1.5h | done: `./gradlew test --tests "ar.com.cocos.challenge.integration.MarketDataJpaAdapterIT"` verde

## Fase 3 — ADR y verificación

- [x] R011-T04 — Escribir `docs/adr/010-marketdata-nullability-legacy.md` con contexto, decisión, consecuencias y alternativas | Fase 3 | ola: W3 | depende de: R011-T01, R011-T02 | toca: `docs/adr/010-marketdata-nullability-legacy.md`, `docs/adr/README.md` | ~0.5h | done: `Test-Path docs/adr/010-marketdata-nullability-legacy.md` y `grep -n "## Decisión" docs/adr/010-marketdata-nullability-legacy.md`

## Buffer

- [ ] R011-T90 — Fallback si la query derivada renombrada no arranca o la native `DISTINCT ON` no excluye los `NULL`: reemplazar por `@Query` nativa con `datetime IS NOT NULL ... ORDER BY datetime DESC LIMIT 1` para el single result y ajustar el `WHERE` de la query múltiple | Buffer | ola: W9 | depende de: — | toca: `src/main/java/ar/com/cocos/challenge/infrastructure/adapter/out/persistence/MarketDataJpaRepository.java`, `src/main/java/ar/com/cocos/challenge/infrastructure/adapter/out/marketdata/MarketDataJpaAdapter.java` | ~1h | done: `./gradlew test --tests "ar.com.cocos.challenge.integration.MarketDataJpaAdapterIT"` verde

- [ ] R011-T91 — Si los tests nuevos colisionan con datos del seed V1 (incluidos los instrumentos `12`/`53` con OHLC `NULL`) o con los tickers `TESTA`–`TESTD`, aislar con instrumentos y tickers únicos y consultar por el id persistido | Buffer | ola: W9 | depende de: — | toca: `src/test/java/ar/com/cocos/challenge/integration/MarketDataJpaAdapterIT.java` | ~0.5h | done: `./gradlew test --tests "ar.com.cocos.challenge.integration.MarketDataJpaAdapterIT"` verde

> Cierre de W3 (coordinator, fuera de lanes): `./gradlew compileJava test` y `./gradlew check`.
