# Requerimiento 013 — Valoración a costo histórico en lugar de 0 para posiciones sin precio de mercado

| Campo | Valor |
| :--- | :--- |
| **Número** | 013 |
| **Carpeta** | `specs/013-portfolio-historical-cost-valuation/` |
| **Tipo** | bugfix |
| **Estado** | draft |
| **Creado** | 2026-11-16 |
| **Actualizado** | 2026-11-16 |
| **Depende de** | 012-marketdata-datetime-nullable-graceful-portfolio |
| **Supersede a** | comportamiento de valoración degradada en 012 (CA-04) |

> **Alcance de este documento:** solo el **delta** de este requerimiento. Las invariantes del
> proyecto (hexagonal, `BigDecimal`, transacciones, locks, idempotencia, secretos) son globales y
> viven en `AGENTS.md` y en [`../000-baseline-challenge/spec.md`](../000-baseline-challenge/spec.md).
> No las repitas: referencialas. Los ADR aplicables están en `docs/adr/`.

---

## 1. Contexto y motivación

El requerimiento 012 implementó una degradación graceful para posiciones sin precio de mercado: cuando
`findLatestPrices` no devuelve precio para un instrumento, `PortfolioCalculator` valora la posición
en `marketValue = 0` (ver `PortfolioCalculator.java:140-152`).

Esto genera tres problemas contables:

1. **Subvaluación del patrimonio**: `totalAccountValue = availableCash + sum(positions.marketValue)`.
   Si una posición costó 1000 ARS y se valora en 0, el patrimonio aparece 1000 ARS por debajo de la
   realidad.

2. **Inconsistencia matemática**: `marketValue = 0` implica pérdida del 100% del capital invertido,
   pero `totalReturnPct = 0` y `dailyReturnPct = 0` implican que no hubo ganancia ni pérdida.
   Ambas no pueden ser ciertas simultáneamente.

3. **`unrealizedPnl()` inconsistente**: `Position.unrealizedPnl() = marketValue - totalCost`.
   Con `marketValue = 0`, devuelve `-totalCost` (pérdida real calculada), pero la API no la expone.
   El cliente ve `totalReturnPct = 0` y cree que no hay pérdida.

4. **Falta de transparencia**: No hay forma de distinguir entre una posición que vale 0 (porque se
   vendió todo) y una que no tiene precio disponible.

La solución es **degradar a costo histórico** (totalCost) en lugar de 0. Esto:
- ✅ Preserva `totalAccountValue` (patrimonio real)
- ✅ Hace `unrealizedPnl() = 0` (consistente con `totalReturnPct = 0`)
- ✅ El cliente infiere "sin precio" cuando `totalReturnPct = 0` y `dailyReturnPct = 0`
- ✅ Alineado con prácticas reales de brokerages (valoran a costo cuando el feed está caído)

---

## 2. Alcance

### Dentro

- `PortfolioCalculator.java` (líneas ~140-152): cambiar la degradación de `Money.zero()` a
  `totalCost` para `marketValue` cuando `price == null`. Mantener `totalReturnPct = 0` y
  `dailyReturnPct = 0` (sin precio, no son calculables).
- Tests en `PortfolioCalculatorTest`: actualizar el caso de "posición sin precio" para verificar
  `marketValue = totalCost`, `totalReturnPct = 0`, `dailyReturnPct = 0` y `unrealizedPnl() = 0`.
- ADR: crear `docs/adr/012-portfolio-historical-cost-valuation.md` que **supersede** la decisión de
  valoración degradada en el ADR-011 (ver CA-08 del requerimiento 012). Marcar ADR-011 como
  superseded (banner) y actualizar `docs/adr/README.md`.

### Fuera

- Cambios al contrato REST más allá de la semántica del campo `marketValue` para posiciones sin
  precio (sigue siendo 200).
- Cambios en `OrderMatcher`, `BalanceValidator`, ejecución de órdenes: siguen fallando cerrado si no
  hay precio (no se puede operar sin precio de referencia).
- Cambios en `MarketDataEntity`, `PersistenceMapper`, consultas de último precio.
- El caso de instrumento inexistente (sigue lanzando `IllegalStateException`).

---

## 3. Criterios de aceptación

| ID | Given | When | Then |
| :--- | :--- | :--- | :--- |
| CA-01 | Un usuario tiene una posición con `quantity > 0` y el instrumento **no** tiene precio en marketdata | Se consulta `GET /api/v1/portfolio/{userId}` | El endpoint devuelve 200 OK con `marketValue = totalCost`, `totalReturnPct = "0"`, `dailyReturnPct = "0"` |
| CA-02 | Un usuario tiene una posición con `quantity > 0` y el instrumento **sí** tiene precio en marketdata | Se consulta `GET /api/v1/portfolio/{userId}` | El endpoint devuelve 200 OK con `marketValue` calculado con el precio actual, `totalReturnPct` y `dailyReturnPct` calculados correctamente |
| CA-03 | Un usuario tiene una posición con `quantity = 0` (flat) | Se consulta `GET /api/v1/portfolio/{userId}` | La posición **no aparece** en el array `positions` (filtrado por `quantity > 0`) |
| CA-04 | Un usuario tiene una posición sin precio y se calcula `totalAccountValue` | Se consulta `GET /api/v1/portfolio/{userId}` | `totalAccountValue = availableCash + sum(positions.marketValue)` donde `marketValue = totalCost` para posiciones sin precio |
| CA-05 | Un instrumento existe en `orders` pero **no** en `instruments` | Se consulta `GET /api/v1/portfolio/{userId}` | El endpoint devuelve 500 Internal Server Error con mensaje `"Instrumento no encontrado: {instrumentId}"` |
| CA-06 | Suite y calidad | `./gradlew compileJava test` y `./gradlew check` | Verdes (cobertura ≥ 80 % en `PortfolioCalculator`) |
| CA-07 | Documentación | Se revisa `docs/adr/` | Existe ADR-012 que supersede la decisión de valoración en ADR-011; ADR-011 marcado como superseded; índice actualizado |

---

## 4. Contratos afectados

- `GET /api/v1/portfolio/{userId}`: para una posición sin precio, el campo `marketValue` pasa de
  `"0.00"` a `totalCost` (ej. `"1000.00"`). `totalReturnPct` y `dailyReturnPct` siguen siendo
  `"0"`. No cambian nombres de campos, códigos de estado para otros casos ni el contrato de órdenes.

**Ejemplo respuesta con posición sin precio (después de este fix):**

```json
{
  "userId": 1,
  "availableCash": "50000.00",
  "positions": [
    {
      "instrumentId": 12,
      "ticker": "DOME",
      "quantity": 100,
      "marketValue": "1000.00",
      "totalCost": "1000.00",
      "totalReturnPct": "0.00",
      "dailyReturnPct": "0.00"
    }
  ],
  "totalAccountValue": "51000.00"
}
```

---

## 5. Impacto en requerimientos previos

- **Modifica comportamiento de 012 (CA-04):** antes `marketValue = 0`; ahora `marketValue = totalCost`.
  El resto de CA de 012 se mantienen (CA-01, 02, 03, 05, 06, 07, 08).
- **Supersede parcialmente a 011/012:** la decisión de "valorar en 0 cuando no hay precio" se
  revierte a "valorar en costo histórico". El ADR-011 sigue vigente para la parte de
  `datetime` nullable; se crea ADR-012 que supersede solo la decisión de valoración.
- 004/006/007/010 no se ven afectados: no cambia el precio usado en ejecución ni los rendimientos
  de posiciones **con** precio.

---

## 6. NFR específicos

Sin NFR adicionales: aplican las invariantes de `AGENTS.md`. La valoración a costo histórico sigue
usando `BigDecimal`/`Money` (nunca `double`). La inmutabilidad de `Position` se respeta (record).

---

## 7. Supuestos y pendientes

- **SUPUESTO:** una posición sin precio conserva `totalCost` (dato real de las ejecuciones) y expone
  rendimientos `0` porque no son calculables; `unrealizedPnl()` ahora es consistente (`= 0`).
- **SUPUESTO:** el ADR-011 no se reescribe; se crea ADR-012 y se agrega un banner de superseded
  solo para la decisión de valoración, según la convención de `docs/adr/README.md`.
- **PENDIENTE:** ninguna decisión bloqueante.

---

## 8. Definition of Done

- [ ] Criterios de aceptación CA-01..CA-07 verificados
- [ ] `./gradlew compileJava test` verde
- [ ] `./gradlew check` verde (cobertura ≥ 80 % en `PortfolioCalculator`)
- [ ] ADR-012 creado y ADR-011 marcado como superseded (solo banner para la decisión de valoración)
- [ ] Reviewer emitió ✅ en `review.md`