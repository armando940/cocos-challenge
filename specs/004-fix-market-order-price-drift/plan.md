# Plan de Ejecución — 004 Prevenir descalce de precio entre validación y ejecución en órdenes MARKET

> Derivado de [`spec.md`](spec.md) y del estado real del repo el 2026-11-14.
> Los IDs de tarea son `R004-T01`, `R004-T02`… y solo existen dentro de este requerimiento.

## Resumen

- **Fases**: 5 (Core Implementation, Unit Tests, Integration Test, Verification, Cache Removal)
- **Olas**: 7 (W1…W7) — 8 tareas paralelizables totales
- **Horas estimadas**: ~6h
- **Camino crítico**: R004-T01 → R004-T02 → R004-T05 → R004-T07 → R004-T10
- **Riesgo mayor**: R004-T02 (cambio de firma en OrderMatcher + uso en PlaceOrderUseCase) — cualquier desajuste rompe la compilación y los tests de integración

## Estado actual vs plan

| Fase | Objetivo | Estado | Evidencia |
| :--- | :--- | :--- | :--- |
| 1 | Core Implementation: capturar precio una sola vez y pasarlo a ejecución | ✅ Done | `src/main/java/.../OrderMatcher.java`, `src/main/java/.../PlaceOrderUseCase.java` |
| 2 | Unit Tests: actualizar tests existentes + test de descalce | ✅ Done | `src/test/java/.../OrderMatcherTest.java`, nuevo test de drift |
| 3 | Integration Test: verificar precio de ejecución == precio validación | ✅ Done | `src/test/java/.../PlaceOrderFlowIT.java` |
| 4 | Verification: LIMIT sin cambios, suite completa verde, cobertura ≥80% | ✅ Done | `./gradlew test`, `./gradlew check` |
| 5 | Cache Removal: eliminar `@Cacheable` en `MarketDataJpaAdapter` para precios frescos | ⬜ Pendiente | `src/main/java/.../MarketDataJpaAdapter.java` |

## Fase 1 — Core Implementation (~2h)

**Objetivo**: Eliminar la segunda llamada a `marketDataProvider.findLatestPrice()` en `executeMarketOrder` y hacer que `PlaceOrderUseCase` capture el precio una sola vez.
**Entregable**: Código compilando y pasando tests unitarios básicos.
**DoD**: `./gradlew compileJava test --tests "*OrderMatcherTest*"` verde.

| ID | Tarea | Depende de | Estimación | Riesgo | Done |
| :--- | :--- | :--- | :--- | :--- | :--- |
| R004-T01 | Modificar `OrderMatcher.executeMarketOrder` para recibir `BigDecimal executionPrice` y no llamar a `marketDataProvider` | — | 0.75h | Medio | `grep -n "executeMarketOrder" src/main/java/.../OrderMatcher.java` muestra firma con 3 params |
| R004-T02 | Modificar `PlaceOrderUseCase.execute`: capturar `referencePrice` una vez (línea 83), usarlo en `balanceValidator.validate` y pasarlo a `executeMarketOrder` | R004-T01 | 1h | Alto | `grep -n "referencePrice" src/main/java/.../PlaceOrderUseCase.java` muestra una sola asignación y dos usos |

## Fase 2 — Unit Tests (~1.5h)

**Objetivo**: Actualizar tests de `OrderMatcher` para la nueva firma y agregar test que verifique prevención de descalce.
**Entregable**: Tests unitarios verdes.
**DoD**: `./gradlew test --tests "*OrderMatcherTest*"` verde.

| ID | Tarea | Depende de | Estimación | Riesgo | Done |
| :--- | :--- | :--- | :--- | :--- | :--- |
| R004-T03 | Actualizar `OrderMatcherTest`: tests `executesMarketOrderAtLatestClose`, `failsMarketExecutionWithoutPrice` a la nueva firma (pasar price en el mock) | R004-T01 | 0.5h | Bajo | `./gradlew test --tests "*OrderMatcherTest.executesMarketOrderAtLatestClose*"` verde |
| R004-T04 | Agregar test unitario `preventsPriceDriftBetweenValidationAndExecution` en `OrderMatcherTest` (o nueva clase): mock que devuelve precios distintos en llamadas consecutivas, verificar que se usa el precio capturado | R004-T01 | 0.5h | Medio | `./gradlew test --tests "*preventsPriceDrift*"` verde |
| R004-T05 | Verificar que `createOrder` con `amount` en MARKET sigue funcionando (usa `resolveExecutionPrice` internamente) | R004-T01 | 0.25h | Bajo | `./gradlew test --tests "*createsMarketOrderFromAmount*"` verde |

## Fase 3 — Integration Test (~1h)

**Objetivo**: Verificar end-to-end que el precio de ejecución coincide con el de validación.
**Entregable**: Test de integración actualizado y verde.
**DoD**: `./gradlew test --tests "*PlaceOrderFlowIT*"` verde.

| ID | Tarea | Depende de | Estimación | Riesgo | Done |
| :--- | :--- | :--- | :--- | :--- | :--- |
| R004-T06 | Actualizar `PlaceOrderFlowIT`: aserción explícita de que `executedPrice` == precio de mercado consultado al inicio del request (ya implícito, hacerlo explícito y agregar caso con verificación de no-descalce) | R004-T02 | 0.75h | Medio | `./gradlew test --tests "*PlaceOrderFlowIT.completeOrderLifecycle*"` verde |
| R004-T07 | Agregar caso en IT que simule volatilidad (mock o test doble) para verificar prevención de sobregiro | R004-T02 | 0.5h | Medio | `./gradlew test --tests "*PlaceOrderFlowIT*PriceDrift*"` verde |

## Fase 4 — Verification & Edge Cases (~1h)

**Objetivo**: Confirmar que LIMIT no se ve afectado, suite completa verde, cobertura ≥80%.
**Entregable**: Build completo exitoso.
**DoD**: `./gradlew check` verde, coverage ≥80% en `OrderMatcher` y `PlaceOrderUseCase`.

| ID | Tarea | Depende de | Estimación | Riesgo | Done |
| :--- | :--- | :--- | :--- | :--- | :--- |
| R004-T08 | Verificar tests de órdenes LIMIT: `createsLimitOrderWithSize`, `createsLimitOrderFromAmount`, `resolvesLimitExecutionPrice` intactos | R004-T01 | 0.25h | Bajo | `./gradlew test --tests "*Limit*"` verde |
| R004-T09 | Ejecutar suite completa y verificar cobertura | R004-T05, R004-T06, R004-T07 | 0.5h | Bajo | `./gradlew check` verde, `jacocoTestCoverageVerification` pasa |

## Fase 5 — Cache Removal (~0.5h)

**Objetivo**: Eliminar `@Cacheable` en `MarketDataJpaAdapter.findLatestPrice` y `findLatestPrices` para que cada consulta obtenga el precio más fresco desde BD.
**Entregable**: Adapter sin anotaciones de caché en métodos de precios.
**DoD**: `./gradlew compileJava test` verde; verificación manual de que ya no hay `@Cacheable` en métodos de precios.

| ID | Tarea | Depende de | Estimación | Riesgo | Done |
| :--- | :--- | :--- | :--- | :--- | :--- |
| R004-T10 | Eliminar `@Cacheable(value = "latest-market-prices", ...)` de `findLatestPrice` y `findLatestPrices` en `MarketDataJpaAdapter` | R004-T02 | 0.5h | Bajo | `grep -n "@Cacheable" src/main/java/.../MarketDataJpaAdapter.java` no muestra anotaciones en métodos de precios |

## Olas de ejecución

| Ola | Tareas en paralelo | Lanes | Serializa | Verificación de cierre |
| :-- | :-- | :-- | :--- | :--- |
| W1 | R004-T01 | 1 | — | `./gradlew compileJava` |
| W2 | R004-T02 | 1 | `src/main/java/.../OrderMatcher.java` (firma) | `./gradlew compileJava` |
| W3 | R004-T03, R004-T05 | 2 | — | `./gradlew test --tests "*OrderMatcherTest*"` |
| W4 | R004-T04 | 1 | — | `./gradlew test --tests "*preventsPriceDrift*"` |
| W5 | R004-T06, R004-T07 | 1 | `src/main/java/.../PlaceOrderUseCase.java` | `./gradlew test --tests "*PlaceOrderFlowIT*"` |
| W6 | R004-T08, R004-T09 | 2 | — | `./gradlew check` |
| W7 | R004-T10 | 1 | `src/main/java/.../MarketDataJpaAdapter.java` | `./gradlew compileJava test` |

> Dos tareas comparten ola solo si no hay dependencia entre ellas y sus rutas `toca:` (en `tasks.md`) son disjuntas. Archivos de frontera (`build.gradle.kts`, `application*.yml`, `db/migration`, `ExceptionHandler`, `tasks.md`, `state.json`) serializan: la tarea va sola. Máximo 3 lanes.

## Camino crítico

R004-T01 → R004-T02 → R004-T06 → R004-T09 → R004-T10

(El cambio de firma en `OrderMatcher` propaga a `PlaceOrderUseCase`, que a su vez debe validarse en integración antes del check final. La eliminación de caché es el paso final que garantiza precios siempre frescos.)

## Riesgos y mitigación

| Riesgo | Probabilidad | Impacto | Mitigación |
| :--- | :--- | :--- | :--- |
| Firma de `executeMarketOrder` inconsistente entre `OrderMatcher` y `PlaceOrderUseCase` | Media | Alto | Revisar compilación tras cada tarea del camino crítico (W1, W2) |
| Test de integración flaky por timing de precios en Testcontainers | Baja | Medio | El test de descalce (R004-T07) usa mock/overrides determinísticos, no precios reales |
| Cobertura < 80% en `OrderMatcher` o `PlaceOrderUseCase` tras refactor | Baja | Medio | R004-T04 y R004-T07 añaden branches nuevos; verificar con `jacocoTestReport` antes de `check` |
| Cambio rompe creación de órdenes MARKET con `amount` (usa `resolveExecutionPrice` interno) | Baja | Alto | R004-T05 cubre explícitamente este path |
| Eliminar caché aumenta carga en BD / latencia en consultas de precios | Baja | Medio | TTL original era 1 min; si hay alta concurrencia, evaluar caché con TTL < 1s o read-replica; monitorear métricas `marketData` |

(End of file - total 131 lines)