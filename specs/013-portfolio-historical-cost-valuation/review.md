# Revisión — 013 Valoración a costo histórico en lugar de 0 para posiciones sin precio de mercado [✅ Aprobado]

## Resumen
El requerimiento 013 implementa correctamente el cambio de valoración degradada de `Money.zero()` a `totalCost` (costo histórico) para posiciones sin precio de mercado. Todos los criterios de aceptación CA-01 a CA-07 se verifican. Los tests pasan, la cobertura JaCoCo es 99% instrucciones / 88% branches (supera el 80% requerido), y la documentación ADR está completa con ADR-012 supersediendo la decisión de valoración de ADR-011.

## Checklist

| Categoría | Estado | Hallazgos |
|-----------|--------|-----------|
| Cumplimiento del spec (CA-01..CA-07) | ✅ | Todos los criterios verificados: CA-01/CA-04 marketValue=totalCost, CA-02 valoración normal con precio, CA-03 filtrado flat, CA-05 error instrumento inexistente, CA-06 build verde con cobertura ≥80%, CA-07 ADR-012 creado y ADR-011 marcado |
| Invariantes del proyecto | ✅ | Domain puro (sin Spring/JPA), BigDecimal/Money para dinero, inmutabilidad (records), transaccionalidad en use cases (no aplica aquí), locks e idempotencia no tocados |
| Arquitectura Hexagonal | ✅ | `PortfolioCalculator` en `domain/service`, puertos en `domain/port/out`, sin deps de framework |
| Transacciones/Concurrencia | ✅ | No aplica cambios (solo lógica de valoración pura) |
| Tests | ✅ | 8 tests en `PortfolioCalculatorTest`, todos verdes; cobertura 99% inst / 88% branch |
| Seguridad | ✅ | Sin credenciales hardcodeadas, validaciones de entrada en DTOs (no aplica aquí), sin SQL injection |

## Hallazgos Críticos (Bloqueantes)
_Ninguno_

## Hallazgos Menores (No bloqueantes)
_Ninguno_

## Verificación de Criterios de Aceptación

| CA | Descripción | Verificación |
|----|-------------|--------------|
| CA-01 | Posición sin precio → marketValue = totalCost, returns = 0 | ✅ Test `returnsDegradedPositionWhenPriceIsMissing` línea 210: `marketValue = 10000.00`, `totalReturnPct = 0`, `dailyReturnPct = 0` |
| CA-02 | Posición con precio → valoración completa correcta | ✅ Test `calculatesCashPositionsAndReturns` línea 81-85: marketValue, totalCost, returns correctos |
| CA-03 | Posición flat (quantity=0) no aparece | ✅ Test `filtersFlatPositions` línea 115: `positions().isEmpty()` |
| CA-04 | totalAccountValue = cash + sum(marketValue) con totalCost | ✅ Test `returnsDegradedPositionWhenPriceIsMissing` línea 218: `totalAccountValue = 100000.00` (90000 cash + 10000 position) |
| CA-05 | Instrumento inexistente → 500 con mensaje | ✅ Test `throwsWhenInstrumentIsMissing` línea 187-189: `IllegalStateException` con mensaje |
| CA-06 | `./gradlew compileJava test` y `check` verdes, cobertura ≥80% | ✅ Build successful, cobertura 99% inst / 88% branch |
| CA-07 | ADR-012 creado, ADR-011 superseded, README actualizado | ✅ `docs/adr/012-portfolio-historical-cost-valuation.md` existe, ADR-011 tiene banner ⚠️, README línea 17-18 actualizado |

## Próximos Pasos
- [ ] coordinator: guardar este reporte en `review.md` y actualizar `state.json` a `done`

---

_Revisado por @reviewer — Staff/Principal Engineer_