# ADR-011 — `datetime` nullable en `marketdata` y valoración degradada del portfolio

- **Estado:** Aceptado
- **Fecha:** 2026-11-16
- **Decisores:** Armando Silva
- **Supersede a:** ADR-010

## Contexto

La tabla `marketdata` proviene del esquema legado de producción. Tanto en `V1__baseline_prod_schema.sql` como en
`V2__align_app_schema.sql` la columna `"date"`/`datetime` se define **sin `NOT NULL`**, y en el dump real
existen filas sin timestamp (p.ej. instrumentos 12 y 53 del 2023-07-13). Estas filas son legítimas:
el dato falta en la fuente y no puede inventarse.

En ADR-010 se decidió:
- Alinear `high`/`low`/`open` a nullable en la entidad (correcto, son OHLC opcionales).
- Mantener `datetime` como `nullable = false` en `MarketDataEntity` para preservar el contrato de
  `MarketPrice` (que exige `close`, `previousClose` y `datetime` no nulos vía
  `Objects.requireNonNull`).
- Filtrar `datetime IS NOT NULL` en las dos consultas de "último precio" para evitar el problema
  de PostgreSQL `ORDER BY datetime DESC` → `NULLS FIRST`.

Esto resolvió la hidratación y la consulta de último precio, pero **el portfolio seguía fallando** al
valorar posiciones cuyo instrumento solo tenía filas sin `datetime` en `marketdata`:
`MarketDataJpaAdapter.getLatestPrice(instrumentId)` lanzaba
`InstrumentPriceNotFoundException` y `PortfolioCalculator.buildPosition` propagaba la excepción,
dejando sin portfolio a usuarios con instrumentos en ese estado.

No es aceptable que un instrumento sin precio reciente rompa la valoración completa del portfolio:
el portfolio debe ser **observables** aunque falten datos de mercado. La valoración debe ser
*graceful*: mostrar lo que se sabe (cantidad, costo ponderado) y marcar lo que no se sabe (valor de
mercado 0, returns 0).

## Decisión

Extender la estrategia de "no romper el contrato, degradar la valoración" en tres puntos:

1. **`MarketDataJpaAdapter` tolerante a ausencia de precio con timestamp.**
   - `getLatestPrice(instrumentId)`: en lugar de lanzar excepción cuando no encuentra fila con
     `datetime IS NOT NULL`, retorna `null` (o `Optional.empty()`). El llamador decide qué hacer.
   - `getLatestPrices(Set<Long> instrumentIds)`: omite instrumentos sin precio reciente del mapa
     retornado (no lanza, simplemente no los incluye).

2. **`PersistenceMapper.toDomain(MarketDataEntity)` estricto solo para filas con timestamp.**
   - Si `entity.datetime() == null`, lanza `IllegalStateException` con mensaje diagnóstico
     (`"MarketData sin datetime (id=...): no se puede construir MarketPrice"`). Esta fila nunca
     debería llegar aquí porque las consultas ya filtran `datetime IS NOT NULL`; el throw es una
     red de seguridad defensiva.

3. **`PortfolioCalculator.buildPosition` valoración degradada sin excepción.**
   - Busca precio en el mapa provisto por el adapter (`Map<Long, MarketPrice> prices`).
   - Si **no hay precio** para el instrumento:
     - `quantity` = suma de tamaños netos de órdenes `FILLED`.
     - `totalCost` = costo ponderado real (`Σ size * executedPrice` de cada fill).
     - `marketValue` = `Money.ZERO` (valor actual desconocido).
     - `unrealizedPnl` = `Money.ZERO`.
     - `dailyPnl` = `Money.ZERO`.
     - `weightedAveragePrice` = `totalCost / quantity` (costo promedio real de adquisición).
   - Si **hay precio**: valoración completa usando `close` y `previousClose` (comportamiento actual).
   - **Sigue lanzando** `InstrumentNotFoundException("Instrument <id> no encontrado")` si el
     instrumento no existe en la tabla `instrument` (eso sí es un error de integridad de datos).

Contrato interno actualizado:

| Columna / Campo | Esquema (`V1`/`V2`) | `MarketDataEntity` | Consultas último precio | Valoración portfolio |
| :--- | :--- | :--- | :--- | :--- |
| `high`, `low`, `open` | Nullable | Nullable | No usado | No usado |
| `close` | Nullable | `nullable = false` (contrato `MarketPrice`) | Sí (no nulo en filas con datetime) | Sí si hay precio |
| `previous_close` | Nullable | `nullable = false` (contrato `MarketPrice`) | Sí (no nulo en filas con datetime) | Sí si hay precio |
| `datetime` | Nullable | `nullable = false` (contrato `MarketPrice`) | Filtro `IS NOT NULL` + `ORDER BY DESC` | Requerido para valoración completa |
| `created_at` | `NOT NULL DEFAULT` | `nullable = false` | No | No |

## Consecuencias

**Positivas**

- Portfolio **siempre responde** (200) aunque falten precios de mercado recientes.
- Valoración **transparente**: el cliente ve cantidad y costo real de sus posiciones; `marketValue = 0`
  y `returns = 0` señalan explícitamente "falta precio actual".
- Cero cambios de esquema (`V1`/``V2` intactos, sin migraciones nuevas) y cero cambios de contrato
  REST: `PortfolioResponse` ya tiene todos los campos; solo cambia su semántica cuando falta precio.
- Separación de preocupaciones limpia: el adapter de persistencia provee `Optional` de precio; el
  caso de uso decide la estrategia de valoración; el dominio (`PortfolioCalculator`) es puramente
  funcional y determinista (fácil de testear).

**Negativas / costos**

- Discrepancia entidad/BD en `datetime` persiste intencionalmente (ver ADR-010): la entidad dice
  `nullable = false` pero la columna es nullable. Se neutraliza con filtros en consultas y
  valoración tolerante; no se relaja la entidad para no romper `MarketPrice`.
- Cualquier nueva consulta sobre `marketdata` que ordene por `datetime` debe replicar
  `datetime IS NOT NULL` para no elegir filas sin timestamp como "más recientes".
- La valoración degradada usa `Money.ZERO` para `marketValue` y `pnl`; un consumidor que asuma
  "valor 0 = posición sin valor" podría malinterpretar "valor 0 = precio desconocido". La documentación
  de la API y el frontend deben distinguir ambos casos (p.ej. bandera `hasMarketPrice` en futuro).

## Alternativas descartadas

1. **Backfill de `datetime` con fecha de `created_at` o fecha de carga.** Descartada: inventar un
   timestamp falso daría la impresión de que hay un precio "de hoy" cuando en realidad es un dato
   sin fecha confiable. Mejor valor 0 explícito que precio fantasioso.

2. **`COALESCE(datetime, created_at)` en las consultas de último precio.** Descartada por la
   misma razón: mezcla datos de distinta naturaleza y oculta la ausencia real de timestamp de mercado.

3. **Devolver 404/500 en portfolio si algún instrumento falta precio.** Descartada: rompe la
   observabilidad del portfolio completo por un dato accesorio faltante. El portfolio es la vista
   principal del usuario; debe ser tolerante a datos de mercado incompletos.

4. **Hacer `datetime` nullable en `MarketDataEntity` y `MarketPrice`.** Descartada: `MarketPrice`
   es un value object de dominio que exige `close`, `previousClose` y `datetime` no nulos
   (invariante de negocio: un precio sin fecha no es un precio). Cambiar eso propagaría `Optional`
   por todo el dominio y complicaría cálculos que hoy son puros y totales.

## Referencias

- Requerimiento 012: `specs/012-marketdata-datetime-nullable-graceful-portfolio/spec.md`
- Requerimiento 011 (base): `specs/011-marketdata-legacy-nullable-ohlc/spec.md`
- `docs/adr/010-marketdata-nullability-legacy.md` (superseded)
- ADR-006: `docs/adr/006-flyway-baseline-produccion.md`
- PostgreSQL `ORDER BY` y `NULLS FIRST`: https://www.postgresql.org/docs/current/queries-order.html