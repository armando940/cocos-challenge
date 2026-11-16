# ADR-010 — Nullability de `marketdata` legacy: entidad alineada a Flyway y exclusión de filas sin timestamp

> **Superseded by [ADR-011](011-marketdata-datetime-nullable-graceful-valuation.md)** (2026-11-16)

- **Estado:** Aceptado
- **Fecha:** 2026-11-16
- **Decisores:** Armando Silva

## Contexto

La tabla `marketdata` viene del dump legado de producción. En `V1__baseline_prod_schema.sql` la
tabla se crea con `high`, `low`, `"open"`, `"close"`, `previousclose` y `"date"` **sin `NOT NULL`**;
`V2__align_app_schema.sql` solo renombra columnas y ajusta tipos (`NUMERIC(18,4)`, `TIMESTAMPTZ`) y
**nunca agrega** `NOT NULL`. En los datos reales hay filas con `high`/`low`/`open` en `NULL` —por
ejemplo los instrumentos `12` y `53` del `2023-07-13`, que solo traen `close` y `previousClose`—:
`NULL` en OHLC significa "sin operación esa rueda", un dato válido, no un error.

Aun así, `MarketDataEntity` declaraba `@Column(nullable = false)` en `high`, `low` y `open`. Con
`spring.jpa.hibernate.ddl-auto: validate` el build **no falla**, porque Hibernate `validate` no
verifica constraints `NOT NULL`: comprueba tablas y columnas existentes, no su nullability. El
problema es que la anotación **miente** sobre el esquema real: cualquier DDL generado desde JPA
(`create`/`update`, schema export, tests que materialicen el esquema desde las entidades) crearía
`NOT NULL` que rechazan el dump legacy. La entidad deja de estar alineada con la fuente de verdad
(Flyway).

`datetime` también es nullable en la base (la columna `"date"` original nunca tuvo `NOT NULL`) y es
el campo por el que se ordena el "último precio". En PostgreSQL `ORDER BY datetime DESC` ordena
**`NULLS FIRST`**, de modo que una fila legacy sin timestamp sería elegida como la más reciente; al
mapearla, `MarketPrice` (que exige `close`, `previousClose` y `datetime` no nulos vía
`Objects.requireNonNull`) explotaría.

El delta y las decisiones de diseño están en
`specs/011-marketdata-legacy-nullable-ohlc/spec.md`.

## Decisión

Alinear el mapeo con el esquema real y neutralizar el riesgo de las filas sin timestamp en las
consultas, sin tocar el esquema ni los contratos:

1. **Alinear `high`/`low`/`open` con Flyway.** Se quita `nullable = false` de esos tres campos en
   `MarketDataEntity`, de modo que la entidad refleje que son OHLC opcionales del dump legacy.
2. **Mantener no nulos `close`/`previousClose`/`datetime` en la entidad.** Permanecen
   `nullable = false` (junto con `instrumentId` y `createdAt`) porque `MarketPrice` los exige no
   nulos al construirse: un precio sin `close`, `previousClose` o `datetime` no es utilizable.
3. **Filtrar `datetime IS NOT NULL` en las dos consultas de último precio.** La query derivada
   `findFirstByInstrumentIdAndDatetimeIsNotNullOrderByDatetimeDesc` mantiene el `LIMIT 1` (Spring
   Data lo aplica en la query derivada) y la native `DISTINCT ON (instrument_id)` incorpora
   `AND datetime IS NOT NULL` en su `WHERE`, para que la fila sin timestamp no gane por `NULLS
   FIRST`.
4. **Eliminar el método de repositorio sin uso.**
   `findByInstrumentIdOrderByDatetimeDesc` no tenía consumidores y no filtraba `NULL`; se remueve
   en lugar de mantener una variante que podría devolver una fila no mapeable.
5. **No tocar el esquema.** No se modifican `V1`/`V2` ni se agregan migraciones Flyway. La base
   sigue siendo la fuente de verdad y el dump legacy se sigue leyendo tal cual.

Contrato interno resultante:

| Columna | Esquema (`V1`/`V2`) | `MarketDataEntity` | Se usa para "último precio" |
| :--- | :--- | :--- | :--- |
| `high`, `low`, `open` | Nullable | Nullable | No (solo hidratación) |
| `close` | Nullable | `nullable = false` (contrato `MarketPrice`) | Sí |
| `previous_close` | Nullable | `nullable = false` (contrato `MarketPrice`) | Sí |
| `datetime` | Nullable | `nullable = false` (contrato `MarketPrice`) | Sí, filtrando `IS NOT NULL` |
| `created_at` | `NOT NULL DEFAULT CURRENT_TIMESTAMP` | `nullable = false` | No |

## Consecuencias

**Positivas**

- Las filas legacy con `high`/`low`/`open` en `NULL` se hidratan sin excepción: el mapeo deja de
  mentir sobre el esquema y coincide con Flyway.
- Las filas sin timestamp se ignoran: nunca se devuelven como "último precio", así que `MarketPrice`
  nunca se construye con `datetime` nulo.
- Cero cambios de esquema (`V1`/`V2` intactos, sin migraciones nuevas) y cero cambios de contrato
  REST: el delta queda contenido en el adapter de persistencia.
- Se elimina una consulta sin uso que además no filtraba `NULL`, reduciendo superficie de error.

**Negativas / costos**

- Queda una **discrepancia consciente** entidad/BD en `datetime`: la entidad declara
  `nullable = false` aunque la columna sea nullable. Es intencional y se neutraliza en las consultas
  con `datetime IS NOT NULL`; no se relaja la entidad para no romper el contrato de `MarketPrice`.
- Si en el futuro se generara DDL desde JPA (`create`/`update`, schema export), habría que revisar
  esa excepción: el `NOT NULL` generado sobre `datetime` no coincidiría con el esquema legacy y
  debería resolverse antes de materializar el esquema desde las entidades.
- El filtro `datetime IS NOT NULL` es específico de las consultas de último precio; cualquier
  consulta nueva sobre `marketdata` que ordene por `datetime` debe replicarlo.

## Alternativas descartadas

1. **Backfill o `COALESCE` de `high`/`low`/`open`.** Descartada: `NULL` es un dato válido del dump
   (sin operación esa rueda), no un error a corregir; rellenarlo inventaría precios.
2. **Volver nullable `close`/`previousClose`/`datetime` en la entidad.** Descartada: rompe el
   contrato de `MarketPrice`, que exige esos tres valores no nulos con `Objects.requireNonNull`.
3. **Agregar `NOT NULL` a `high`/`low`/`open` (o a `datetime`) en una migración.** Descartada: el
   dump legacy contiene `NULL` en esas columnas; la migración fallaría o exigiría un backfill que
   contradice el punto anterior.
4. **Generar el DDL desde las entidades (`ddl-auto: update`/`create`, schema export).** Descartada:
   cambios implícitos y no auditables; el proyecto exige migraciones versionadas y Flyway como
   fuente de verdad del esquema.

## Referencias

- Requerimiento 011: `specs/011-marketdata-legacy-nullable-ohlc/spec.md`
- `src/main/resources/db/migration/V1__baseline_prod_schema.sql`
- `src/main/resources/db/migration/V2__align_app_schema.sql`
- ADR-006: `docs/adr/006-flyway-baseline-produccion.md`
- PostgreSQL `ORDER BY` y `NULLS FIRST`: https://www.postgresql.org/docs/current/queries-order.html
