# Requerimiento 011 — Alinear nullability de MarketDataEntity con el esquema legacy

| Campo | Valor |
| :--- | :--- |
| **Número** | 011 |
| **Carpeta** | `specs/011-marketdata-legacy-nullable-ohlc/` |
| **Tipo** | bugfix |
| **Estado** | done |
| **Creado** | 2026-11-16 |
| **Actualizado** | 2026-11-16 |
| **Depende de** | 000-baseline-challenge |
| **Supersede a** | — |

> **Alcance de este documento:** solo el **delta** de este requerimiento. Las invariantes del
> proyecto (hexagonal, `BigDecimal`, transacciones, locks, idempotencia, secretos) son globales y
> viven en `AGENTS.md` y en [`../000-baseline-challenge/spec.md`](../000-baseline-challenge/spec.md).
> No las repitas: referencialas. Los ADR aplicables están en `docs/adr/`.

---

## 1. Contexto y motivación

`MarketDataEntity` declara `@Column(nullable = false)` en `high`, `low` y `open`, pero el esquema
legacy de producción (`V1__baseline_prod_schema.sql`, columnas `NUMERIC` sin `NOT NULL`) contiene
filas con esos tres campos en `NULL` — por ejemplo los instrumentos `12` y `53` del `2023-07-13`,
que sólo traen `close` y `previousClose`. `V2__align_app_schema.sql` sólo ajusta tipos; nunca agrega
`NOT NULL`. La anotación de la entidad miente sobre el esquema real.

Hoy la app corre con `spring.jpa.hibernate.ddl-auto: validate`, que no chequea constraints
`NOT NULL`, así que el build actual no falla. El riesgo es real en cuanto alguien genere DDL desde
las entidades (`create`/`update`, schema export, tests que materialicen el esquema desde JPA): se
crearían `NOT NULL` que rechazan el dato legacy, y el modelo de persistencia queda desalineado de la
fuente de verdad (Flyway).

Además, `datetime` es nullable en la base (la columna `date` original nunca tuvo `NOT NULL`) y es el
campo por el que se ordena el "último precio". En PostgreSQL `ORDER BY datetime DESC` ordena
`NULLS FIRST`, de modo que una fila legacy sin timestamp sería elegida como la más reciente y
`MarketPrice` (que exige `datetime` no nulo) explotaría al mapear.

## 2. Alcance

### Dentro

- `MarketDataEntity.java`: quitar `nullable = false` de `high`, `low` y `open`, dejando documentado
  que son OHLC opcionales del dump legacy. Mantener `nullable = false` en `instrumentId`, `close`,
  `previousClose`, `datetime` y `createdAt`.
- `MarketDataJpaRepository.java`: excluir filas con `datetime IS NULL` en las dos consultas usadas
  para resolver el último precio (`findFirst…` y la `DISTINCT ON` de `findLatestPrices`), y alinear
  o eliminar el método `findByInstrumentIdOrderByDatetimeDesc` que hoy no tiene uso y no filtra.
- Tests de integración en `MarketDataJpaAdapterIT`: hidratación de una fila legacy con
  `high/low/open` en `NULL` y exclusión de una fila con `datetime` en `NULL`.
- `docs/adr/010-marketdata-nullability-legacy.md`: decisión de alinear la entidad con Flyway y de
  filtrar filas sin timestamp en lugar de relajar el contrato de `MarketPrice`.

### Fuera

- Backfill o normalización de los `NULL` legacy: `NULL` en `high/low/open` significa "sin operación
  ese día" y es un dato válido, no un error a corregir.
- Volver nullable `close`, `previousClose` o `datetime` en `MarketDataEntity`: `MarketPrice` los
  exige no nulos y el dump legacy siempre los trae.
- Cambios a `V1`/`V2` ni nuevas migraciones Flyway (no se toca el esquema).
- Cambios a búsqueda de instrumentos, portfolio, órdenes, contratos REST o response codes.
- El trabajo no commiteado del usuario (`docs/postman/*`, `V901__seed_ars_marketdata.sql`).

## 3. Criterios de aceptación

| ID | Given | When | Then |
| :--- | :--- | :--- | :--- |
| CA-01 | `MarketDataEntity.java` | Se inspecciona la entidad | `high`, `low` y `open` **no** declaran `nullable = false` |
| CA-02 | `MarketDataEntity.java` | Se inspecciona la entidad | `instrumentId`, `close`, `previousClose`, `datetime` y `createdAt` **siguen** con `nullable = false` |
| CA-03 | Una fila `marketdata` con `high`, `low`, `open` en `NULL` y `close`/`previousClose`/`datetime` válidos | Se invoca `MarketDataJpaAdapter.findLatestPrice` | Devuelve `MarketPrice` con el `close` y `previousClose` de la fila, sin excepción |
| CA-04 | Para un mismo instrumento, una fila con `datetime` en `NULL` (close más alto) y otra con `datetime` válido | Se invoca `findLatestPrice` y `findLatestPrices` | Se devuelve la fila con `datetime` válido; la fila sin timestamp se ignora |
| CA-05 | Suite completa | `./gradlew compileJava test` | Verde, incluidos los nuevos tests de integración |
| CA-06 | Checks de calidad | `./gradlew check` | Verde (cobertura ≥ 80 % en servicios de dominio) |
| CA-07 | Documentación | Se revisa `docs/adr/` | Existe `010-marketdata-nullability-legacy.md` con la decisión y su justificación |

## 4. Contratos afectados

Ningún contrato público cambia: no se modifican endpoints, DTOs, códigos de error ni el esquema de
base de datos. El delta es interno al adapter de persistencia (`MarketDataEntity`,
`MarketDataJpaRepository`) y a tests/ADR.

## 5. Impacto en requerimientos previos

- Compatible con 000–010.
- `R007` fijó la forma de `findLatestPrices` (`DISTINCT ON`); este requerimiento agrega el filtro
  `datetime IS NOT NULL` sin cambiar la cardinalidad (1 fila por instrumento).
- `R004` eliminó la caché de precios; el filtro nuevo se aplica en la misma consulta, sin caché de
  por medio.
- No cambia el comportamiento para filas con datos completos: los resultados actuales se mantienen.

## 6. NFR específicos

Sin NFR adicionales: aplican las invariantes de `AGENTS.md`. En particular no se introduce `double`;
los `BigDecimal` del adapter se mantienen intactos.

## 7. Supuestos y pendientes

- **SUPUESTO:** `NULL` en `high`/`low`/`open` es un valor legítimo del dump legacy; no se
  backfillea ni se hace `COALESCE` con `close`.
- **SUPUESTO:** `datetime` se mantiene `nullable = false` en la entidad porque un precio sin
  timestamp no es utilizable por `MarketPrice`; el riesgo de filas sin timestamp se neutraliza en las
  consultas (`datetime IS NOT NULL`) y no relajando la entidad.
- **SUPUESTO:** `findByInstrumentIdOrderByDatetimeDesc` no tiene consumidores; se alinea con el
  filtro o se elimina, a criterio del implementer, sin cambiar la API pública del adapter.
- **PENDIENTE:** ninguna decisión bloqueante.

## 8. Definition of Done

- [x] Criterios de aceptación CA-01..CA-07 verificados
- [x] `./gradlew compileJava test` verde
- [x] `./gradlew check` verde (cobertura ≥ 80 % en servicios de dominio tocados)
- [x] ADR `docs/adr/010-marketdata-nullability-legacy.md` creado
- [x] Reviewer emitió ✅ en `review.md`
