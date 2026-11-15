# Tasks Checklist — 007 Fix findLatestPrices sin LIMIT

> Formato: `- [ ] RNNN-TXX — Descripción | Fase N | ola: WN | depende de: RNNN-TYY | toca: <rutas> | ~Xh | done: <criterio>`
> Derivado de [`plan.md`](plan.md) el 2026-11-14. `[x]` solo si el repo lo demuestra hoy.
> `toca:` son las ÚNICAS rutas que el implementer puede editar en esa tarea. Sin `toca:` = serial.
> `toca: SHARED(<archivo>)` = archivo de frontera: la tarea va sola en su ola.

## Fase 1 — Query optimizada en Repositorio

- [x] R007-T01 — Reemplazar `@Query` por `DISTINCT ON (instrument_id) ORDER BY datetime DESC` en `MarketDataJpaRepository` | Fase 1 | ola: W1 | — | toca: `src/main/java/ar/com/cocos/challenge/infrastructure/adapter/out/persistence/MarketDataJpaRepository.java` | ~0.75h | done: `grep -n "DISTINCT ON" src/main/java/ar/com/cocos/challenge/infrastructure/adapter/out/persistence/MarketDataJpaRepository.java`

- [x] R007-T02 — Ajustar `MarketDataJpaAdapter.findLatestPrices`: quitar `toMap` con merge function (la query ya trae 1 por instrumento) | Fase 1 | ola: W1 | — | toca: `src/main/java/ar/com/cocos/challenge/infrastructure/adapter/out/marketdata/MarketDataJpaAdapter.java` | ~0.25h | done: `grep -n "toMap" src/main/java/ar/com/cocos/challenge/infrastructure/adapter/out/marketdata/MarketDataJpaAdapter.java` no muestra merge function en findLatestPrices

## Fase 2 — Tests y validación

- [x] R007-T03 — Agregar test de integración `MarketDataJpaAdapterIT.findLatestPrices_returnsOnlyLatestPerInstrument` con dataset multi-instrumento/multi-fila | Fase 2 | ola: W2 | depende de: R007-T02 | toca: `src/test/java/ar/com/cocos/challenge/integration/MarketDataJpaAdapterIT.java` | ~1h | done: `./gradlew test --tests "MarketDataJpaAdapterIT.findLatestPrices_returnsOnlyLatestPerInstrument"` pasa

- [x] R007-T04 — Correr suite completa y verificar cobertura (`./gradlew check`) | Fase 2 | ola: W2 | depende de: R007-T03 | toca: (ninguna — solo comando de verificación) | ~0.5h | done: `./gradlew check` verde

## Buffer

- [ ] R007-T90 — Reescribir query con `ROW_NUMBER()` si `DISTINCT ON` falla en Testcontainers | Buffer | ola: W9 | — | toca: `src/main/java/ar/com/cocos/challenge/infrastructure/adapter/out/persistence/MarketDataJpaRepository.java` | ~1h | done: `./gradlew compileJava test` verde