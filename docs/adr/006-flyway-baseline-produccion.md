# ADR-006 — Migraciones Flyway compatibles con el esquema legado de producción

- **Estado:** Aceptado
- **Fecha:** 2026-11-12
- **Decisores:** Armando Silva

## Contexto

El equipo de Cocos entregó una base PostgreSQL (Neon) **ya creada y poblada**. Su
esquema real es el del dump `database.sql`: columnas en camelCase plegadas a
minúsculas (`accountnumber`, `instrumentid`, `userid`, `previousclose`), ids
`SERIAL`/`INT`, precios `NUMERIC(10,2)`, fechas `TIMESTAMP`/`DATE` y sin tabla de
idempotencia.

La aplicación, en cambio, tiene entidades JPA con `ddl-auto: validate` que esperan
columnas en **snake_case** (`account_number`, `instrument_id`, `user_id`,
`previous_close`), ids `BIGINT`, precios `NUMERIC(18,4)`, `TIMESTAMPTZ` y columnas
nuevas (`executed_price`, `rejection_reason`, `idempotency_key`, timestamps).

El proyecto tenía un `V1__initial_schema.sql` que creaba directamente el esquema
nuevo. Si se desplegaba contra producción:

1. Flyway, con `baseline-on-migrate: true`, detecta un esquema no vacío y registra
   un baseline en la versión 1: **no ejecuta V1**.
2. La app arranca con columnas que no existen → `ddl-auto: validate` falla y el
   contenedor no levanta.

Es decir, el esquema “nuevo” solo funcionaba en bases vacías, no en producción.

## Decisión

Modelar la evolución como una cadena de migraciones donde **V1 es la fotografía
exacta del esquema legado** y las siguientes migraciones lo transforman:

| Versión | Archivo | Responsabilidad |
|---|---|---|
| V1 | `V1__baseline_prod_schema.sql` | DDL exacto del dump + datos de referencia. En producción se saltea por el baseline. |
| V2 | `V2__align_app_schema.sql` | Renombra columnas a snake_case, ajusta tipos, agrega columnas nuevas y normaliza `instruments.type`. |
| V3 | `V3__create_idempotency_keys.sql` | Crea `idempotency_keys` (ADR-002). |
| V4 | `V4__indexes.sql` | Índices de performance y unicidad de idempotencia. |
| V900 | `V900__dev_seed.sql` | Seed de desarrollo. Solo en `db/seed`; el profile `prod` no lo carga. |

Reglas asociadas:

- **V1 no lleva datos.** Los datos ya existen en producción. Cargarlos en una
  migración duplicaría filas en cualquier base que ya tenga el dump.
- **Se mantiene `baseline-on-migrate: true` con `baseline-version: 1`.** En una
  base no vacía (producción) Flyway registra V1 como baseline y aplica V2+. En una
  base vacía (local/CI/Testcontainers) aplica V1 y luego V2+. Ambos caminos
  convergen en el mismo esquema.
- **Timestamps a UTC.** `orders.datetime` y `marketdata.date` se convierten a
  `TIMESTAMPTZ` asumiendo que los valores legados son UTC (supuesto del challenge).
- **Normalización de datos.** `ACCIONES → ACCION` (y plurales equivalentes) porque
  el dump usa plurales y el enum `InstrumentType` usa singulares. Los valores
  desconocidos caen en `OTRO` para no romper la lectura.
- **Sin `UNIQUE` sobre columnas legacy.** La app no inserta en `users` ni
  `instruments`, y Hibernate no valida constraints. Agregarlas podría abortar el
  deploy si producción tuviera duplicados históricos. La unicidad de
  `idempotency_keys.key` sí se crea (tabla nueva).

## Consecuencias

**Positivas**

- Desplegar contra producción ya no rompe: Flyway migra el esquema legado in-place.
- Una base nueva y producción recorren exactamente las mismas migraciones desde
  V2; el esquema final es idéntico.
- El camino queda auditable: cada cambio de columna o tipo está en una migración.

**Negativas / costos**

- Las bases locales que ya corrieron el `V1` viejo tienen un checksum distinto.
  Hay que resetearlas (`docker compose down -v` o borrar la base) o hacer
  `flyway repair`. Es esperable en un cambio de baseline.
- `V1` asume que el dump entregado refleja producción. Si producción difiere,
  V2 fallará en el primer `RENAME COLUMN`. **Preflight recomendado** antes del
  deploy: comparar `information_schema.columns` de producción contra V1.
- V2 hace `DROP`/`ADD` de FKs y cambia tipos. En una tabla muy grande puede
  bloquear escrituras brevemente; en el volumen del challenge es despreciable.
- No se agregan constraints de unicidad legacy (ver decisión).

## Alternativas consideradas

1. **Dejar el V1 “nuevo” y crear todo con `CREATE TABLE IF NOT EXISTS`.**
   No funciona: en producción las tablas existen con otras columnas y
   `IF NOT EXISTS` no las altera; `ddl-auto: validate` rompe.
2. **Incluir los `INSERT` del dump dentro de V1.**
   Duplicaría datos en cualquier base que ya tenga el dump (producción incluida).
3. **Renombrar todo a camelCase para “respetar” el dump.**
   Obligaría a cambiar todas las entidades JPA y el seed; el equipo ya fijó
   snake_case en el modelo. Se prefiere alinear la base al código, no al revés.
4. **Usar `ddl-auto: update` para que Hibernate ajuste el esquema.**
   Inaceptable en producción: cambios implícitos, no auditables y sin control de
   tipos/datos. El proyecto exige migraciones versionadas.
5. **Un script SQL manual ejecutado una vez en producción.**
   Pierde trazabilidad y no deja el repositorio reproducible desde cero.
