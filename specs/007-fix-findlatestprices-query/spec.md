# Requerimiento 007 — Fix findLatestPrices sin LIMIT

| Campo | Valor |
| :--- | :--- |
| **Número** | 007 |
| **Carpeta** | `specs/007-fix-findlatestprices-query/` |
| **Tipo** | bugfix |
| **Estado** | draft |
| **Creado** | 2026-11-14 |
| **Actualizado** | 2026-11-14 |
| **Depende de** | — |
| **Supersede a** | — |

> **Alcance de este documento:** solo el **delta** de este requerimiento. Las invariantes del
> proyecto (hexagonal, `BigDecimal`, transacciones, locks, idempotencia, secretos) son globales y
> viven en `AGENTS.md` y en [`../000-baseline-challenge/spec.md`](../000-baseline-challenge/spec.md).
> No las repitas: referencialas. Los ADR aplicables están en `docs/adr/`.

---

## 1. Contexto y motivación

El método `MarketDataJpaAdapter.findLatestPrices(Set<Long> instrumentIds)` usa la query
`findByInstrumentIdInOrderByDatetimeDesc` del repositorio JPA, que trae **TODAS las filas
históricas** para cada instrumento solicitado, ordenadas por fecha descendente. El código Java
luego hace un `collect(Collectors.toMap(...))` con merge function para quedarse solo con la primera
(más reciente) de cada instrumento.

Esto es ineficiente:
- Trae N filas por instrumento (donde N = historial completo) en lugar de 1 fila por instrumento
- Consume memoria y ancho de banda innecesario
- Degrada performance conforme crece la tabla `marketdata`

La solución es reescribir la query para que la BD devuelva directamente **una sola fila por
instrumento** (la más reciente), usando una subquery o window function.

## 2. Alcance

### Dentro

- Reemplazar `MarketDataJpaRepository.findByInstrumentIdInOrderByDatetimeDesc` por una query
  optimizada que devuelva solo el último registro por `instrument_id` (ej. `ROW_NUMBER()` o
  subquery con `MAX(datetime)`).
- Ajustar `MarketDataJpaAdapter.findLatestPrices` para que ya no necesite el `toMap` con merge
  function (la query ya trae 1 fila por instrumento).
- Verificar que los tests existentes pasen y agregar test de integración que valide que la query
  trae exactamente 1 fila por instrumento.

### Fuera

- No cambia la interfaz `MarketDataProviderPort.findLatestPrices`.
- No cambia `findLatestPrice` (single instrument) — ya usa `findFirstByInstrumentIdOrderByDatetimeDesc` que sí limita a 1.
- No toca índices de la tabla (el índice `idx_marketdata_instrument_datetime` ya existe y sirve).
- No toca circuit breaker, retry ni fallback.

## 3. Criterios de aceptación

| ID | Given | When | Then |
| :--- | :--- | :--- | :--- |
| CA-01 | Tabla `marketdata` con 1000 filas históricas para instrumento A y 500 para B | Se llama `findLatestPrices(Set.of(A, B))` | La query SQL ejecuta y devuelve **exactamente 2 filas** (1 por instrumento) |
| CA-02 | Mismo escenario | Se llama `findLatestPrices` | El resultado `Map<Long, MarketPrice>` tiene 2 entradas con los precios más recientes (close) de A y B |
| CA-03 | Llamada con `Set.of()` vacío | Se llama `findLatestPrices(emptySet())` | Devuelve `Map` vacío sin error |
| CA-04 | Instrumentos que no tienen marketdata | Se llama `findLatestPrices` con IDs inexistentes | Esos IDs no aparecen en el Map resultado (no lanzan excepción) |

## 4. Contratos afectados

**Ningún contrato externo cambia.** La firma del puerto `MarketDataProviderPort.findLatestPrices`
y su comportamiento observable (Map con últimos precios) se mantienen idénticos. Solo cambia la
implementación interna (query SQL).

## 5. Impacto en requerimientos previos

- Compatible con **004-fix-market-order-price-drift** (que eliminó `@Cacheable` de este método).
- No requiere migración de datos.
- No supersede a ningún requerimiento.

## 6. NFR específicos

Sin NFR adicionales: aplican las invariantes de `AGENTS.md` (hexagonal, `BigDecimal`,
transacciones, cobertura ≥ 80% en domain services).

## 7. Supuestos y pendientes

- **SUPUESTO:** La BD es PostgreSQL (como en el challenge), que soporta `ROW_NUMBER()` y `DISTINCT ON`.
- **PENDIENTE:** Confirmar si preferimos `DISTINCT ON (instrument_id) ORDER BY datetime DESC` (más
  conciso en Postgres) o `ROW_NUMBER()` (más portable). Por ahora se documenta `DISTINCT ON`.

## 8. Definition of Done

- [ ] Criterios de aceptación CA-01..CA-04 verificados
- [ ] `./gradlew test` verde
- [ ] Cobertura ≥ 80 % en los servicios de dominio tocados
- [ ] Documentación/contratos actualizados (Swagger, README o ADR si aplica)
- [ ] Reviewer emitió ✅ en `review.md`