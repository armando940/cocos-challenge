# Plan de Ejecución — 010 Domain refinements & defensive validation

> Derivado de [`spec.md`](spec.md) y del estado real del repo el 2026-11-15.
> Los IDs de tarea son `R010-T01`, `R010-T02`… y solo existen dentro de este requerimiento.

## Resumen

- **Fases**: 2 (Refactor de dominio, Tests y verificación)
- **Olas**: 3 (W1, W2, W3) — 4 tareas paralelizables totales
- **Horas estimadas**: ~3.5h
- **Camino crítico**: R010-T02 → R010-T04 → R010-T05
- **Riesgo mayor**: R010-T02 (cambio en validación de dominio; requiere test exhaustivo)

## Estado actual vs plan

| Fase | Objetivo | Estado | Evidencia |
| :--- | :--- | :--- | :--- |
| 1 | Actualizar terminología y validaciones en servicios de dominio | ⬜ Pendiente | `src/main/java/ar/com/cocos/challenge/domain/service/PortfolioCalculator.java`, `OrderMatcher.java`, `InstrumentType.java` |
| 2 | Tests de regresión y verificación de build | ⬜ Pendiente | `./gradlew test && ./gradlew check` |

---

## Fase 1 — Refactor de dominio (~2h)

**Objetivo**: Actualizar comentarios en PortfolioCalculator, agregar validación `price > 0` en OrderMatcher y agregar constante `CURRENCY` en InstrumentType.

**Entregable**: Tres archivos de dominio modificados con cambios semánticos precisos y validación defensiva.

**DoD**: `grep -n "Costo Promedio Ponderado" src/main/java/ar/com/cocos/challenge/domain/service/PortfolioCalculator.java` (2 ocurrencias), `grep -n "price.*amount.*signum\|price.*compareTo.*0" src/main/java/ar/com/cocos/challenge/domain/service/OrderMatcher.java` (validación añadida), `grep -n "CURRENCY" src/main/java/ar/com/cocos/challenge/domain/model/InstrumentType.java` (constante añadida).

| ID | Tarea | Depende de | Estimación | Riesgo | Done |
| :--- | :--- | :--- | :--- | :--- | :--- |
| R010-T01 | Actualizar comentarios "FIFO simplificado" → "Costo Promedio Ponderado" en PortfolioCalculator.java (líneas 141 y 169) | — | 0.5h | Bajo | `grep -n "Costo Promedio Ponderado" src/main/java/ar/com/cocos/challenge/domain/service/PortfolioCalculator.java \| wc -l` = 2 |
| R010-T02 | Agregar validación `price > 0` en `OrderMatcher.validateLimitOrder` para órdenes LIMIT | — | 0.5h | Medio | `grep -n "validateLimitOrder" src/main/java/ar/com/cocos/challenge/domain/service/OrderMatcher.java -A 10 \| grep -E "signum|compareTo|> 0"` |
| R010-T03 | Agregar constante `CURRENCY` como alias de `MONEDA` en InstrumentType.java | — | 0.5h | Bajo | `grep -n "CURRENCY" src/main/java/ar/com/cocos/challenge/domain/model/InstrumentType.java` |

---

## Fase 2 — Tests y verificación (~1.5h)

**Objetivo**: Agregar tests de validación `price > 0` en OrderMatcherTest y ejecutar suite completa.

**Entregable**: Tests nuevos pasando, cobertura ≥ 80% en servicios tocados, build verde.

**DoD**: `./gradlew test` verde, `./gradlew check` verde (cobertura ≥ 80% en PortfolioCalculator, OrderMatcher).

| ID | Tarea | Depende de | Estimación | Riesgo | Done |
| :--- | :--- | :--- | :--- | :--- | :--- |
| R010-T04 | Agregar tests para validación `price > 0` en OrderMatcherTest (casos: price = 0, price < 0, price > 0) | R010-T02 | 0.5h | Bajo | `grep -n "validateLimitOrder.*price" src/test/java/ar/com/cocos/challenge/unit/domain/OrderMatcherTest.java -A 5` muestra 3 tests nuevos |
| R010-T05 | Ejecutar `./gradlew test` y `./gradlew check` para verificar build completo y cobertura | R010-T01, R010-T02, R010-T03, R010-T04 | 0.5h | Bajo | `./gradlew test` exit 0, `./gradlew check` exit 0 |

---

## Olas de ejecución

| Ola | Tareas en paralelo | Lanes | Serializa | Verificación de cierre |
| :-- | :-- | :-- | :-- | :-- |
| W1 | R010-T01, R010-T02, R010-T03 | 3 | — | `./gradlew compileJava` |
| W2 | R010-T04 | 1 | Depende de R010-T02 (OrderMatcher) | `./gradlew test --tests OrderMatcherTest` |
| W3 | R010-T05 | 1 | Verificación final | `./gradlew test && ./gradlew check` |

> **Justificación de olas**: T1, T2, T3 tocan archivos disjuntos (`PortfolioCalculator.java`, `OrderMatcher.java`, `InstrumentType.java`) sin dependencias entre sí → W1 en paralelo (3 lanes). T4 toca `OrderMatcherTest.java` que depende del símbolo `validateLimitOrder` modificado en T2 → W2 en serie tras W1. T5 es verificación global → W3 sola.

---

## Camino crítico

R010-T02 → R010-T04 → R010-T05

(Solo la validación `price > 0` requiere test posterior antes de la verificación final).

---

## Riesgos y mitigación

| Riesgo | Probabilidad | Impacto | Mitigación |
| :--- | :--- | :--- | :--- |
| Validación `price > 0` rompe tests existentes que pasan price null o 0 | Baja | Medio | Revisar tests existentes en W1 antes de W2; si hay rotos, arreglar en T4 |
| Alias `CURRENCY` en enum rompe serialización/BD | Baja | Alto | Confirmar que `CURRENCY` es alias (mismo ordinal) no nuevo elemento; tests de integración en W3 validan |
| Cobertura < 80% en servicios tocados | Baja | Medio | T4 incluye tests explícitos para la nueva validación; `./gradlew check` en W3 verifica |

---

## Notas de implementación

- **R010-T01**: Solo comentarios (líneas 141 y 169). No cambia lógica.
- **R010-T02**: En `validateLimitOrder`, agregar `if (command.type() == OrderType.LIMIT && command.price() != null && command.price().amount().signum() <= 0)` → lanzar `IllegalArgumentException`.
- **R010-T03**: En `InstrumentType`, agregar `CURRENCY` como alias: después de `MONEDA,` poner `CURRENCY = MONEDA,` (o `CURRENCY(MONEDA)` si el enum tiene constructor). Verificar que `InstrumentType.CURRENCY == InstrumentType.MONEDA`.
- **R010-T04**: Tests nuevos en `OrderMatcherTest`: `rejectsLimitWithZeroPrice`, `rejectsLimitWithNegativePrice`, `acceptsLimitWithPositivePrice`.
- **R010-T05**: Verificación final con `./gradlew test` (unit + integration) y `./gradlew check` (incluye cobertura Jacoco ≥ 80%).