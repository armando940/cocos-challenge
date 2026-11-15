# ADR-008 — Precio límite vs. precio de ejecución en `Order`

- **Estado:** Aceptado
- **Fecha:** 2026-11-14
- **Decisores:** Armando Silva

## Contexto

`Order` tiene dos campos de precio cuya frontera semántica se había difuminado:

- `price`: el **precio límite original** de la orden (`null` para `MARKET`).
- `executedPrice`: el **precio real de ejecución**, sólo aplicable a órdenes `FILLED`.

Hasta este cambio, `Order.filled(executedPrice, executedSize)` **sobrescribía `price`** con el
precio de ejecución. Consecuencia: `price` significaba cosas distintas según el estado de la
orden y se destruía el límite original de una orden `LIMIT` al ejecutarse. En una `MARKET`,
`price` pasaba de `null` a un valor, borrando la distinción entre tipo de orden y ejecución.

`PortfolioCalculator` dependía de esa semántica implícita: `cashEffect(...)` y
`calculateWeightedAverageCost(...)` usaban `order.price()` asumiendo que era el precio de
ejecución. El cálculo funcionaba sólo "de casualidad" porque `filled()` pisaba el campo. Ante
cualquier refactor que empezara a usar `executedPrice` como corresponde, el portfolio habría
calculado mal (efecto de caja o costo en cero para `MARKET`, o con el límite equivocado para
`LIMIT`) **sin error de compilación**: un bug latente y frágil.

Además, el esquema legado (`V1__baseline_prod_schema.sql`) contiene órdenes `FILLED` con
`price`/`size` pero sin datos de ejecución; `V2` sólo agregó las columnas `executed_price` /
`executed_size` como nullable sin completarlas. Esas filas no son mapeables bajo una invariante
estricta de `FILLED`.

El delta y las decisiones de diseño están en `specs/006-fix-filled-price-semantics/spec.md`.

## Decisión

Separar explícitamente las dos semánticas y hacer cumplir la invariante en el dominio:

1. **`price` es el precio límite original.** Es `null` para `MARKET` y conserva su valor para
   `LIMIT` durante toda la vida de la orden. `filled()` **nunca** lo sobreescribe; sólo escribe
   `executedPrice` y `executedSize`.
2. **`executedPrice` es el precio real de ejecución.** Sólo tiene valor en `FILLED`.
3. **Invariante fail-fast en el constructor compacto de `Order`.** Una orden `FILLED` exige
   `executedPrice` no nulo y `> 0`, y `executedSize` no nulo con `0 <= executedSize <= size`.
   Un estado `FILLED` incompleto o inconsistente lanza `IllegalArgumentException`: no es
   representable en el dominio.
4. **El portfolio usa `executedPrice`, nunca `price`.** `PortfolioCalculator.cashEffect` y
   `calculateWeightedAverageCost` toman `executedPrice()`; se elimina el fallback silencioso a
   `BigDecimal.ZERO` y toda dependencia de `price()` en los cálculos de caja y costo. Las
   cantidades siguen siendo `size` (el baseline no modela fills parciales).
5. **Compatibilidad de datos legados vía migración `V5`.** `V5__backfill_executed_price.sql`
   copia `price → executed_price` y `size → executed_size` sólo para filas
   `status = 'FILLED' AND executed_price IS NULL`, sin tocar `price` ni órdenes
   `NEW`/`REJECTED`/`CANCELLED` y sin modificar `V1`–`V4`.

Contrato interno resultante:

| Campo | `NEW` | `FILLED` | `REJECTED` / `CANCELLED` |
| :--- | :--- | :--- | :--- |
| `price` | Límite (LIMIT) o `null` (MARKET) | Igual al original (nunca se sobreescribe) | Igual al original |
| `executedPrice` | `null` | Precio real de ejecución (`> 0`) | `null` |
| `executedSize` | `null` | Cantidad ejecutada (`0 <= executedSize <= size`) | `null` |

No cambian los contratos REST: `OrderResponse` sigue informando `executedPrice`/`executedSize`
para órdenes `FILLED`.

## Consecuencias

**Positivas**

- Semántica de precios sin ambigüedad: cada campo tiene un único significado estable en todo el
  ciclo de vida de la orden.
- El bug latente del portfolio desaparece: caja y costo dependen explícitamente de
  `executedPrice`, así que un refactor de `Order` ya no puede romperlo en silencio.
- La invariante se valida en el constructor (fail-fast): un `FILLED` inválido no se puede
  construir ni persistir, en lugar de propagarse como dato corrupto.
- La deuda de datos legada se resuelve **una sola vez**, de forma idempotente y acotada, en `V5`.

**Negativas / costos**

- Contrato interno más estricto: cualquier código (mappers, fixtures, tests) que construya una
  orden `FILLED` debe proveer `executedPrice`/`executedSize` válidos; los constructores
  incompletos ahora fallan en runtime/arranque.
- Requiere aplicar `V5` antes de leer datos legados; si una base quedara fuera de la cadena de
  migraciones, el mapeo de sus `FILLED` históricas fallaría por la nueva invariante.
- Las filas legadas pierden la distinción original entre límite y ejecución (no existía en el
  dump); el backfill asume `price` como precio de ejecución, que es la interpretación correcta
  del esquema anterior.

## Alternativas consideradas

1. **Mantener `filled()` pisando `price`.** Descartada: conserva la semántica ambigua, destruye
   el límite original y deja el bug del portfolio latente y dependiente de un efecto colateral.
2. **Fallback tolerante en el mapper o en el portfolio** (`executedPrice != null ? executedPrice :
   price`). Descartada: oculta datos inválidos y reintroduce la misma ambigüedad bajo otra forma;
   el dominio debe fallar rápido ante un `FILLED` inconsistente, no adivinar.
3. **Backfill dentro de `V2` reescribiendo una migración ya aplicada.** Descartada: Flyway valida
   el checksum de las migraciones aplicadas; editar `V2` rompe las bases existentes y exige
   `repair` o reset. La compatibilidad se resuelve con una migración nueva (`V5`).
4. **Modelar `price` como opcional y derivar el ejecutado on-the-fly.** Descartada: mantiene un
   único campo con doble significado y no persiste el snapshot de ejecución que requerimiento 004
   ya capturó.
