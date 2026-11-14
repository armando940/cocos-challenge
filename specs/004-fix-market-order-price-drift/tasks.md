# Tasks Checklist — 004 Prevenir descalce de precio entre validación y ejecución en órdenes MARKET

> Formato: `- [ ] R004-TXX — Descripción | Fase N | ola: WN | depende de: R004-TYY | toca: <rutas> | ~Xh | done: <criterio>`
> Derivado de [`plan.md`](plan.md) el 2026-11-14. `[x]` solo si el repo lo demuestra hoy.
> `toca:` son las ÚNICAS rutas que el implementer puede editar en esa tarea. Sin `toca:` = serial.
> `toca: SHARED(<archivo>)` = archivo de frontera: la tarea va sola en su ola.

## Fase 1 — Core Implementation

- [x] R004-T01 — Modificar OrderMatcher.executeMarketOrder para recibir BigDecimal executionPrice y no llamar a marketDataProvider | Fase 1 | ola: W1 | — | toca: src/main/java/ar/com/cocos/challenge/domain/service/OrderMatcher.java | ~0.75h | done: `grep -n "executeMarketOrder" src/main/java/ar/com/cocos/challenge/domain/service/OrderMatcher.java | grep "BigDecimal executionPrice"`
- [x] R004-T02 — Modificar PlaceOrderUseCase.execute: capturar referencePrice una vez, usarlo en validate y pasarlo a executeMarketOrder | Fase 1 | ola: W2 | depende de: R004-T01 | toca: src/main/java/ar/com/cocos/challenge/application/usecase/PlaceOrderUseCase.java | ~1h | done: `grep -n "referencePrice" src/main/java/ar/com/cocos/challenge/application/usecase/PlaceOrderUseCase.java | wc -l` muestra 3 líneas (1 asignación + 2 usos)

## Fase 2 — Unit Tests

- [x] R004-T03 — Actualizar OrderMatcherTest: tests executesMarketOrderAtLatestClose y failsMarketExecutionWithoutPrice a la nueva firma (pasar price en el mock) | Fase 2 | ola: W3 | depende de: R004-T01 | toca: src/test/java/ar/com/cocos/challenge/unit/domain/OrderMatcherTest.java | ~0.5h | done: `./gradlew test --tests "*OrderMatcherTest.executesMarketOrderAtLatestClose*"`
- [x] R004-T04 — Agregar test unitario preventsPriceDriftBetweenValidationAndExecution en OrderMatcherTest: mock que devuelve precios distintos en llamadas consecutivas, verificar que se usa el precio capturado | Fase 2 | ola: W4 | depende de: R004-T01 | toca: src/test/java/ar/com/cocos/challenge/unit/domain/OrderMatcherTest.java | ~0.5h | done: `./gradlew test --tests "*OrderMatcherTest.preventsPriceDrift*"`
- [x] R004-T05 — Verificar que createOrder con amount en MARKET sigue funcionando (usa resolveExecutionPrice internamente) | Fase 2 | ola: W3 | depende de: R004-T01 | toca: src/test/java/ar/com/cocos/challenge/unit/domain/OrderMatcherTest.java | ~0.25h | done: `./gradlew test --tests "*OrderMatcherTest.createsMarketOrderFromAmount*"`

## Fase 3 — Integration Test

- [x] R004-T06 — Actualizar PlaceOrderFlowIT: aserción explícita de que executedPrice == precio de mercado consultado al inicio del request y agregar caso con verificación de no-descalce | Fase 3 | ola: W5 | depende de: R004-T02 | toca: src/test/java/ar/com/cocos/challenge/integration/PlaceOrderFlowIT.java | ~0.75h | done: `./gradlew test --tests "*PlaceOrderFlowIT.completeOrderLifecycle*"`
- [x] R004-T07 — Agregar caso en IT que simule volatilidad (mock o test doble) para verificar prevención de sobregiro | Fase 3 | ola: W5 | depende de: R004-T02 | toca: src/test/java/ar/com/cocos/challenge/integration/PlaceOrderFlowIT.java | ~0.5h | done: `./gradlew test --tests "*PlaceOrderFlowIT*PriceDrift*"`

## Fase 4 — Verification & Edge Cases

- [x] R004-T08 — Verificar tests de órdenes LIMIT: createsLimitOrderWithSize, createsLimitOrderFromAmount, resolvesLimitExecutionPrice intactos | Fase 4 | ola: W6 | depende de: R004-T01 | toca: src/test/java/ar/com/cocos/challenge/unit/domain/OrderMatcherTest.java | ~0.25h | done: `./gradlew test --tests "*OrderMatcherTest*Limit*"`
- [x] R004-T09 — Ejecutar suite completa y verificar cobertura (≥80% en OrderMatcher y PlaceOrderUseCase) | Fase 4 | ola: W6 | depende de: R004-T05, R004-T06, R004-T07 | toca: — | ~0.5h | done: `./gradlew check` verde

## Fase 5 — Cache Removal

- [x] R004-T10 — Eliminar @Cacheable(value = "latest-market-prices", ...) de findLatestPrice y findLatestPrices en MarketDataJpaAdapter | Fase 5 | ola: W7 | depende de: R004-T02 | toca: src/main/java/ar/com/cocos/challenge/infrastructure/adapter/out/marketdata/MarketDataJpaAdapter.java | ~0.5h | done: `grep -n "@Cacheable" src/main/java/ar/com/cocos/challenge/infrastructure/adapter/out/marketdata/MarketDataJpaAdapter.java` no muestra anotaciones en métodos de precios

## Buffer

- [ ] R004-T90 — Imprevisto: ajuste en BalanceValidator si validación requiere precio de ejecución distinto | Buffer | ola: W9 | — | toca: ? | ~1h | done: `./gradlew test` verde

(End of file - total 49 lines)