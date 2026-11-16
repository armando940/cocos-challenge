# ADR-012 — Valoración de posiciones sin precio de mercado a costo histórico

- **Estado:** Aceptado
- **Fecha:** 2026-11-16
- **Decisores:** Armando Silva
- **Supersede a:** Decisión de valoración degradada en ADR-011 (punto 3: `marketValue = Money.ZERO` cuando no hay precio)

## Contexto

ADR-011 introdujo una valoración "graceful" para el portfolio cuando un instrumento no tiene precio de mercado reciente (filas en `marketdata` sin `datetime`). La decisión fue:

- `marketValue = Money.ZERO`
- `unrealizedPnl = Money.ZERO`
- `dailyPnl = Money.ZERO`
- `totalReturnPct = 0%`
- `dailyReturnPct = 0%`

Esto resolvió la observabilidad (el portfolio ya no rompe), pero introdujo tres problemas:

1. **Subvaluación patrimonial**: El patrimonio del usuario aparece artificialmente reducido. Una posición comprada a $100.000 muestra `marketValue = $0`, distorsionando el total del portfolio.
2. **Inconsistencia matemática**: `unrealizedPnl = marketValue - totalCost` se define como `$0 - $100.000 = -$100.000`, pero ADR-011 forzaba `unrealizedPnl = $0`. La fórmula y el valor reportado no coinciden.
3. **Falta de transparencia**: `marketValue = 0` es indistinguible de una posición que realmente vale $0 (p. ej. acción en default). El cliente no puede inferir "falta precio" vs. "valor real cero".

El problema no es la ausencia de `datetime` (eso sigue vigente per ADR-011), sino **qué valor asignar a `marketValue` cuando no hay precio**.

## Decisión

Cuando `MarketDataJpaAdapter` no encuentra precio reciente para un instrumento (el mapa de precios no contiene la clave), `PortfolioCalculator.buildPosition` valorará la posición así:

| Campo | Valor |
|-------|-------|
| `quantity` | Suma de tamaños netos de órdenes `FILLED` (sin cambios) |
| `totalCost` | Costo ponderado real `Σ size * executedPrice` de cada fill (sin cambios) |
| `marketValue` | **`totalCost`** (costo histórico como proxy de valor actual) |
| `unrealizedPnl` | **`Money.ZERO`** (consistente: `marketValue - totalCost = 0`) |
| `dailyPnl` | **`Money.ZERO`** (sin precio previo, no hay cambio diario) |
| `weightedAveragePrice` | `totalCost / quantity` (costo promedio real, sin cambios) |
| `totalReturnPct` | **`0%`** (no hay ganancia/pérdida realizada ni latente sin precio de mercado) |
| `dailyReturnPct` | **`0%`** (sin precio previo, no hay retorno diario) |

El contrato REST `PortfolioResponse` no cambia: todos los campos ya existen. Lo que cambia es su semántica cuando falta precio de mercado.

Se añade implícitamente una señal para el cliente: **si `marketValue == totalCost` y `unrealizedPnl == 0` y `totalReturnPct == 0` y `dailyReturnPct == 0` → el instrumento no tiene precio de mercado actual**. En el futuro, si se requiere una bandera explícita, se puede añadir `hasMarketPrice: boolean` sin breaking change.

## Consecuencias

### Positivas

- **Patrimonio preservado**: El total del portfolio refleja el capital invertido real, no un valor artificialmente reducido a cero.
- **Consistencia matemática**: `unrealizedPnl = marketValue - totalCost` se cumple siempre (da 0 cuando no hay precio).
- **Señal clara para el cliente**: La igualdad `marketValue == totalCost` + returns en 0% indica "sin precio de mercado" sin necesidad de nuevo campo.
- **Cero cambios de esquema ni contrato REST**: Solo cambia la lógica interna de `PortfolioCalculator`.
- **Separación de preocupaciones intacta**: El adapter sigue proveyendo `Optional<MarketPrice>`; el caso de uso decide la estrategia; el dominio (`PortfolioCalculator`) sigue siendo puro y determinista.

### Negativas / costos

- **Riesgo de sobrevaluación percibida**: Si el precio real cayó drásticamente, `marketValue = totalCost` oculta la pérdida latente. Mitigación: los returns en 0% y la igualdad `marketValue == totalCost` son la señal de "dato no disponible".
- **No distingue "precio = costo" real de "precio desconocido"**: Un instrumento que casualmente cotiza exactamente al precio de adquisición se vería igual. Probabilidad baja; si es crítico, futuro `hasMarketPrice`.

## Alternativas descartadas

1. **Mantener `marketValue = 0` (ADR-011 original).** Descarte: subvalúa patrimonio, rompe la identidad `unrealizedPnl = marketValue - totalCost`, y es indistinguible de valor real cero.

2. **Usar `previousClose` del último precio histórico disponible (aunque sea viejo).** Descarte: un precio de hace meses no representa "valor actual" y daría falsa confianza. Además, `marketdata` puede no tener *ninguna* fila con `datetime` para ese instrumento.

3. **Devolver `null`/`Optional` en `marketValue` y `returns` en el DTO.** Descarte: rompería el contrato REST (campos no opcionales hoy) y propagaría `Optional` al frontend.

4. **Añadir campo `hasMarketPrice` ya.** Descarte: YAGNI. La señal implícita (`marketValue == totalCost` + returns 0) es suficiente para MVP. Si el frontend lo pide, se añade en PR aparte sin breaking change.

## Referencias

- ADR-011: `docs/adr/011-marketdata-datetime-nullable-graceful-valuation.md` (supersedido parcialmente — solo la decisión de valoración; `datetime` nullable y OHLC nullable siguen vigentes)
- Requerimiento 013: `specs/013-portfolio-historical-cost-valuation/spec.md`
- `PortfolioCalculator.buildPosition` — implementación de la valoración
- `MarketDataJpaAdapter.getLatestPrices` — proveedor de precios (retorna `Map<Long, MarketPrice>` sin instrumentos sin precio)