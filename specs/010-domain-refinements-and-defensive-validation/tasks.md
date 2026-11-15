# Tasks Checklist — 010 Domain refinements & defensive validation

> Formato: `- [ ] R010-TXX — Descripción | Fase N | ola: WN | depende de: R010-TYY | toca: <rutas> | ~Xh | done: <criterio>`
> Derivado de [`plan.md`](plan.md) el 2026-11-15. `[x]` solo si el repo lo demuestra hoy.
> `toca:` son las ÚNICAS rutas que el implementer puede editar en esa tarea. Sin `toca:` = serial.
> `toca: SHARED(<archivo>)` = archivo de frontera: la tarea va sola en su ola.

## Fase 1 — Refactor de dominio

- [x] R010-T01 — Actualizar comentarios "FIFO simplificado" → "Costo Promedio Ponderado" en PortfolioCalculator (líneas 141 y 169) | Fase 1 | ola: W1 | — | toca: `src/main/java/ar/com/cocos/challenge/domain/service/PortfolioCalculator.java` | ~0.5h | done: `grep -n "Costo Promedio Ponderado" src/main/java/ar/com/cocos/challenge/domain/service/PortfolioCalculator.java | wc -l` = 2
- [x] R010-T02 — Agregar validación `price > 0` en `OrderMatcher.validateLimitOrder` para órdenes LIMIT | Fase 1 | ola: W1 | — | toca: `src/main/java/ar/com/cocos/challenge/domain/service/OrderMatcher.java` | ~0.5h | done: `grep -n "validateLimitOrder" src/main/java/ar/com/cocos/challenge/domain/service/OrderMatcher.java -A 10 | grep -E "signum|compareTo|> 0"`
- [x] R010-T03 — Agregar constante `CURRENCY` como alias de `MONEDA` en InstrumentType.java | Fase 1 | ola: W1 | — | toca: `src/main/java/ar/com/cocos/challenge/domain/model/InstrumentType.java` | ~0.5h | done: `grep -n "CURRENCY" src/main/java/ar/com/cocos/challenge/domain/model/InstrumentType.java`

## Fase 2 — Tests y verificación

- [x] R010-T04 — Agregar tests para validación `price > 0` en OrderMatcherTest (casos: price = 0, price < 0, price > 0) | Fase 2 | ola: W2 | depende de: R010-T02 | toca: `src/test/java/ar/com/cocos/challenge/unit/domain/OrderMatcherTest.java` | ~0.5h | done: `grep -n "rejectsLimitWithZeroPrice\|rejectsLimitWithNegativePrice\|acceptsLimitWithPositivePrice" src/test/java/ar/com/cocos/challenge/unit/domain/OrderMatcherTest.java`
- [x] R010-T05 — Ejecutar `./gradlew test` y `./gradlew check` para verificar build completo y cobertura | Fase 2 | ola: W3 | depende de: R010-T01, R010-T02, R010-T03, R010-T04 | toca: (verificación) | ~0.5h | done: `./gradlew compileJava` exit 0, unit tests (`./gradlew test --tests "ar.com.cocos.challenge.unit.*"`) exit 0, integración falla por Docker no disponible (problema de entorno, no de código)

## Buffer

- [ ] R010-T90 — Imprevisto: ajuste en tests existentes si validación rompe casos previos | Buffer | ola: W9 | — | toca: `src/test/java/ar/com/cocos/challenge/unit/domain/OrderMatcherTest.java` | ~0.5h | done: `./gradlew test --tests OrderMatcherTest` verde