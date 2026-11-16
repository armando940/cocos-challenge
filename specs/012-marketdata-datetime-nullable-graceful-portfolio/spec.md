# Requerimiento 012 — `datetime` nullable en MarketData y valoración degradada del portfolio

| Campo | Valor |
| :--- | :--- |
| **Número** | 012 |
| **Carpeta** | `specs/012-marketdata-datetime-nullable-graceful-portfolio/` |
| **Tipo** | refactor |
| **Estado** | done |
| **Creado** | 2026-11-16 |
| **Actualizado** | 2026-11-16 |
| **Depende de** | 011-marketdata-legacy-nullable-ohlc |
| **Supersede a** | 011-marketdata-legacy-nullable-ohlc |

> **Alcance de este documento:** solo el **delta** de este requerimiento. Las invariantes del
> proyecto (hexagonal, `BigDecimal`, transacciones, locks, idempotencia, secretos) son globales y
> viven en `AGENTS.md` y en [`../000-baseline-challenge/spec.md`](../000-baseline-challenge/spec.md).
> No las repitas: referencialas. Los ADR aplicables están en `docs/adr/`.

---

## 1. Contexto y motivación

El requerimiento 011 alineó `high`, `low` y `open` con el esquema legacy, pero dejó una
**discrepancia consciente** en `datetime`: la columna es nullable en `V1`/`V2` y la entidad seguía
declarando `@Column(nullable = false)`. Se neutralizó el riesgo en las consultas
(`datetime IS NOT NULL`), pero la deuda técnica persiste: la entidad no refleja la fuente de verdad
y cualquier DDL generado desde JPA crearía un `NOT NULL` falso.

Además, si una posición del portfolio no tiene precio de mercado, `PortfolioCalculator` lanza
`IllegalStateException("Precio no disponible…")` y el endpoint `GET /api/v1/portfolio/{userId}`
responde error. Un instrumento sin marketdata (o con todas sus filas sin timestamp) no debería
tumbar la consulta completa: la valoración puede degradarse a 0 mientras el resto del portfolio se
sigue mostrando.

Este requerimiento supersede a 011: completa la alineación de nullability y elimina el edge case de
valoración.

## 2. Alcance

### Dentro

- `MarketDataEntity.java`: quitar `nullable = false` de `datetime` (la columna es nullable en
  `V1`/`V2`). Mantener `nullable = false` en `instrumentId`, `close`, `previousClose` y `createdAt`.
  Actualizar el Javadoc.
- `PersistenceMapper.java`: validación explícita en `toDomain(MarketDataEntity)`; si `datetime` es
  `null`, lanzar `IllegalStateException` con mensaje accionable (incluye el `id` de la fila) en
  lugar del `NullPointerException` críptico de `MarketPrice`. Sin `datetime` no se construye un
  `MarketPrice`.
- `PortfolioCalculator.java`: si no hay precio para una posición con cantidad positiva, devolver
  una `Position` **degradada**: `marketValue = 0.00`, `totalReturnPct = 0`, `dailyReturnPct = 0`,
  conservando `quantity` y el `totalCost` (costo promedio ponderado de las compras). No lanzar
  excepción. Si el instrumento no existe, se mantiene el `IllegalStateException` actual.
- Tests: `PortfolioCalculatorTest` (caso sin precio), `PersistenceMapperTest` (nuevo) y
  `MarketDataJpaAdapterIT` (hidratación de `datetime` null a nivel entidad).
- ADR: crear `docs/adr/011-marketdata-datetime-nullable-graceful-valuation.md` que **supersede** al
  ADR-010, marcar el ADR-010 como superseded (banner, sin reescribir su cuerpo) y actualizar
  `docs/adr/README.md`.

### Fuera

- Retirar los filtros `datetime IS NOT NULL` de las consultas de último precio: se mantienen como
  defensa en profundidad (PostgreSQL ordena `NULLS FIRST` en `DESC`).
- Backfill o `COALESCE` de OHLC/`datetime`.
- Cambios al esquema (`V1`/`V2`, migraciones nuevas).
- Cambios en ejecución de órdenes: `OrderMatcher`/`BalanceValidator` siguen fallando cerrado si no
  hay precio (no se puede operar sin precio de referencia).
- Cambios al contrato REST más allá de la semántica de una posición sin precio (sigue siendo 200 con
  `marketValue: "0.00"`), ni a `Order`, `Money` o `Portfolio`.
- El trabajo no commiteado del usuario (`docs/postman/*`, `V901__seed_ars_marketdata.sql`).

## 3. Criterios de aceptación

| ID | Given | When | Then |
| :--- | :--- | :--- | :--- |
| CA-01 | `MarketDataEntity.java` | Se inspecciona la entidad | `datetime` **no** declara `nullable = false`; `instrumentId`, `close`, `previousClose` y `createdAt` lo siguen declarando |
| CA-02 | `MarketDataEntity` con `datetime = null` | Se invoca `PersistenceMapper.toDomain(entity)` | Lanza `IllegalStateException` con mensaje que menciona `datetime` y el `id` de la fila; no se construye `MarketPrice` |
| CA-03 | `MarketDataEntity` completa | Se invoca `PersistenceMapper.toDomain(entity)` | Devuelve `MarketPrice` con `close`, `previousClose` y `datetime` equivalentes |
| CA-04 | Un usuario con una posición y `findLatestPrices` sin entrada para ese instrumento | Se ejecuta `PortfolioCalculator.calculate(userId)` | Devuelve el portfolio con la posición (`quantity` neta, `marketValue = 0.00`, `totalReturnPct = 0`, `dailyReturnPct = 0`) y **no** lanza excepción; `totalAccountValue = availableCash` |
| CA-05 | Una posición cuyo instrumento no existe | Se ejecuta `PortfolioCalculator.calculate(userId)` | Sigue lanzando `IllegalStateException("Instrumento no encontrado…")` |
| CA-06 | Una fila `marketdata` con `datetime` en `NULL` | Se invoca `findLatestPrice` y `findLatestPrices` | La fila se ignora (comportamiento de 011 preservado); la entidad puede hidratarla sin excepción |
| CA-07 | Suite y calidad | `./gradlew compileJava test` y `./gradlew check` | Verdes (cobertura ≥ 80 % en servicios de dominio) |
| CA-08 | Documentación | Se revisa `docs/adr/` | Existe ADR-011 que supersede al ADR-010; ADR-010 marcado como superseded; índice actualizado |

## 4. Contratos afectados

- `GET /api/v1/portfolio/{userId}`: para una posición sin precio, la respuesta pasa de error a `200`
  con `marketValue: "0.00"`, `totalReturnPct: "0"`, `dailyReturnPct: "0"` y la `quantity` real. No
  cambian nombres de campos, códigos de estado para otros casos ni el contrato de órdenes.
- No hay cambios de esquema ni de tablas.

## 5. Impacto en requerimientos previos

- **Supersede a 011:** la parte de `high/low/open` se conserva; cambia la decisión sobre `datetime`
  (de discrepancia consciente a alineación total) y se agrega la valoración degradada.
- Los filtros `datetime IS NOT NULL` introducidos en 011 se mantienen (CA-06), por lo que la
  selección del "último precio" no cambia.
- 004/006/007/010 no se ven afectados: no cambia el precio usado en ejecución ni los rendimientos de
  posiciones con precio.

## 6. NFR específicos

Sin NFR adicionales: aplican las invariantes de `AGENTS.md`. La valoración degradada sigue usando
`BigDecimal`/`Money` (nunca `double`).

## 7. Supuestos y pendientes

- **SUPUESTO:** una posición sin precio conserva `totalCost` (dato real de las ejecuciones) pero
  expone rendimientos `0` porque no son calculables; `unrealizedPnl()` no se expone en la API, así
  que no hay inconsistencia visible.
- **SUPUESTO:** la validación del mapper es defensiva: las consultas de último precio ya filtran
  `datetime IS NULL`, pero cualquier consulta futura que hidrate estas filas falla con un mensaje
  claro en lugar de un NPE.
- **SUPUESTO:** el ADR-010 no se reescribe; se crea ADR-011 y se agrega un banner de superseded,
  según la convención de `docs/adr/README.md`.
- **PENDIENTE:** ninguna decisión bloqueante.

## 8. Definition of Done

- [x] Criterios de aceptación CA-01..CA-08 verificados
- [x] `./gradlew compileJava test` verde
- [x] `./gradlew check` verde (cobertura ≥ 80 % en servicios de dominio tocados)
- [x] ADR-011 creado y ADR-010 marcado como superseded
- [x] Reviewer emitió ✅ en `review.md`
