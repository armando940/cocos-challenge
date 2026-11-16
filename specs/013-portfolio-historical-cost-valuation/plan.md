# Plan de Ejecución — 013 Valoración a costo histórico en lugar de 0 para posiciones sin precio de mercado

> Derivado de [`spec.md`](spec.md) y del estado real del repo el 2026-11-16.
> Los IDs de tarea son `R013-T01`, `R013-T02`… y solo existen dentro de este requerimiento.

## Resumen

- **Fases**: 1 (Valoración a costo histórico y documentación ADR)
- **Olas**: 1 (W1) — 2 tareas en paralelo
- **Horas estimadas**: 1.5 h
- **Camino crítico**: W1 (R013-T01, R013-T02)
- **Riesgo mayor**: Bajo — cambio aislado en lógica pura de dominio y documentación

## Estado actual vs plan

| Fase | Objetivo | Estado | Evidencia |
| :--- | :--- | :--- | :--- |
| 1 | Cambiar la valoración degradada en PortfolioCalculator de Money.zero() a totalCost y documentar el cambio en ADR-012 | ⬜ Pendiente | `PortfolioCalculator.java`, `PortfolioCalculatorTest.java`, `docs/adr/012-portfolio-historical-cost-valuation.md` |

## Fase 1 — Valoración a costo histórico y documentación ADR (~1.5h)

**Objetivo**: Reemplazar la valoración degradada en `0` por el costo histórico (`totalCost`) para posiciones sin precio de mercado en `PortfolioCalculator`, manteniendo `totalReturnPct = 0` y `dailyReturnPct = 0`, y documentar la decisión supersediendo parcialmente el ADR-011.  
**Entregable**: `PortfolioCalculator` actualizado, tests unitarios en `PortfolioCalculatorTest` actualizados y verdes, y ADR-012 creado con banner en ADR-011.  
**DoD**: `./gradlew check` verde (cobertura ≥80% en `PortfolioCalculator`) y `docs/adr/` actualizado.

| ID | Tarea | Depende de | Estimación | Riesgo | Done |
| :--- | :--- | :--- | :--- | :--- | :--- |
| R013-T01 | Documentar ADR-012 supersediendo decisión de valoración de ADR-011 y actualizar índice `docs/adr/README.md` | — | 0.5 h | Bajo | `Test-Path docs/adr/012-portfolio-historical-cost-valuation.md` |
| R013-T02 | Actualizar `PortfolioCalculator.java` para valorar a `totalCost` cuando `price == null` y actualizar `PortfolioCalculatorTest.java` | — | 1 h | Bajo | `./gradlew test --tests "ar.com.cocos.challenge.unit.domain.PortfolioCalculatorTest"` |

## Olas de ejecución

| Ola | Tareas en paralelo | Lanes | Serializa | Verificación de cierre |
| :-- | :-- | :-- | :-- | :-- |
| W1 | R013-T01, R013-T02 | 2 | — | `./gradlew check` |

> W1 ejecuta en 2 lanes paralelas ya que R013-T01 toca únicamente archivos de documentación bajo `docs/adr/` y R013-T02 toca el código Java de dominio y su test correspondiente bajo `src/`. No hay dependencias entre ellas ni solapamiento de archivos.

## Camino crítico

`W1 (R013-T01, R013-T02)` — Ambas tareas se pueden realizar de manera independiente y en paralelo.

## Riesgos y mitigación

| Riesgo | Probabilidad | Impacto | Mitigación |
| :--- | :--- | :--- | :--- |
| Reducción no intencionada de la cobertura en `PortfolioCalculator` | Baja | Medio | Mantener/ampliar las aserciones en `PortfolioCalculatorTest` y ejecutar `./gradlew check` al cerrar la ola |
