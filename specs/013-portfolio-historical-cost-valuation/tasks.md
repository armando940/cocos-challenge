# Tasks Checklist — 013 Valoración a costo histórico en lugar de 0 para posiciones sin precio de mercado

> Formato: `- [ ] R013-TXX — Descripción | Fase N | ola: WN | depende de: R013-TYY | toca: <rutas> | ~Xh | done: <criterio>`
> Derivado de [`plan.md`](plan.md) el 2026-11-16. `[x]` solo si el repo lo demuestra hoy.
> `toca:` son las ÚNICAS rutas que el implementer puede editar en esa tarea. Sin `toca:` = serial.
> `toca: SHARED(<archivo>)` = archivo de frontera: la tarea va sola en su ola.

## Fase 1 — Valoración a costo histórico y documentación ADR

- [x] R013-T01 — Crear ADR-012 (`docs/adr/012-portfolio-historical-cost-valuation.md`) supersediendo la decisión de valoración degradada en 0 del ADR-011; agregar banner `Superseded` en ADR-011 solo para la decisión de valoración; actualizar `docs/adr/README.md` | Fase 1 | ola: W1 | — | toca: `docs/adr/012-portfolio-historical-cost-valuation.md`, `docs/adr/011-marketdata-datetime-nullable-graceful-valuation.md`, `docs/adr/README.md` | ~0.5h | done: `Test-Path docs/adr/012-portfolio-historical-cost-valuation.md`
- [x] R013-T02 — Modificar `PortfolioCalculator.buildPosition` (líneas ~140-152) para devolver `marketValue = totalCost` cuando `price == null` (en vez de `Money.zero()`), manteniendo `totalReturnPct = 0` y `dailyReturnPct = 0`; actualizar `PortfolioCalculatorTest.returnsDegradedPositionWhenPriceIsMissing` y `filtersFlatPositions` para verificar `marketValue = totalCost`, `unrealizedPnl = 0`, `totalReturnPct = 0`, `dailyReturnPct = 0`, y `totalAccountValue` | Fase 1 | ola: W1 | — | toca: `src/main/java/ar/com/cocos/challenge/domain/service/PortfolioCalculator.java`, `src/test/java/ar/com/cocos/challenge/unit/domain/PortfolioCalculatorTest.java` | ~1h | done: `./gradlew test --tests "ar.com.cocos.challenge.unit.domain.PortfolioCalculatorTest"`

## Buffer

- [ ] R013-T90 — Imprevisto en cobertura, migración ADR o verificación del reviewer | Buffer | ola: W9 | — | toca: ? | ~0.5h | done: criterio definido por el coordinador al momento