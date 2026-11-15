# Revisión — 006 Separar precio límite de precio de ejecución (`Order.filled`) y desacoplar el portfolio [✅ Aprobado]

## Resumen
El requerimiento 006 resuelve correctamente el bug de semántica ambigua de precios en `Order` y desacopla el portfolio del precio límite. La implementación:

1. **`Order.filled()`** ahora preserva `price` (precio límite original, `null` en `MARKET`) y escribe únicamente `executedPrice`/`executedSize`.
2. **Invariante FILLED fail-fast** en el constructor compacto de `Order`: `executedPrice != null && > 0`, `executedSize != null`, `0 <= executedSize <= size`.
3. **`PortfolioCalculator`** usa `executedPrice()` en `cashEffect` y `calculateWeightedAverageCost`, eliminando el fallback silencioso a `BigDecimal.ZERO` y la dependencia de `price()`.
4. **Migración `V5`** de backfill acotada e idempotente para órdenes `FILLED` legadas del dump V1 (`executed_price IS NULL`).
5. **Tests** alineados a la nueva semántica: `OrderTest` (nuevo), `PortfolioCalculatorTest` (MARKET `price=null`, LIMIT `price!=executedPrice`), `OrderMatcherTest` (MARKET FILLED `price==null`), `PlaceOrderFlowIT` (JdbcTemplate valida persistencia separada y débito correcto).
6. **ADR-008** documenta la decisión, alternativas descartadas y estrategia de compatibilidad.

Todos los criterios de aceptación **CA-01..CA-08** están verificados. Suite completa verde (`./gradlew test` y `./gradlew check`). Cobertura `PortfolioCalculator`: **100 % lines / 100 % methods** (supera el umbral ≥80 %).

---

## Checklist

| Categoría | Estado | Hallazgos |
|-----------|--------|-----------|
| **Cumplimiento del spec (CA-01..CA-08)** | ✅ | CA-01/02: `OrderTest` valida `filled()` preserva límite y setea ejecución; CA-03: invariante FILLED rejecta `executedPrice` nulo, ≤0, `executedSize` nulo/negativo/`>size`. CA-04/05: `PortfolioCalculator` y tests usan `executedPrice()` para caja y costo. CA-06: migración `V5` backfilla `executed_price`/`executed_size` de V1. CA-07: contrato REST sin cambios (`executedPrice` ya informado). CA-08: `./gradlew test` + `./gradlew check` verdes. |
| **Invariantes del proyecto** | ✅ | Hexagonal (domain sin Spring/JPA), `BigDecimal` (vía `Money` scale 2 HALF_UP), records inmutables, transacciones y locks sin cambios, idempotencia intacta, secretos fuera del repo. |
| **Arquitectura Hexagonal** | ✅ | Puertos en `domain/port/{in,out}`, adapters en `infrastructure/adapter/...`, use cases `@Service` + `@Transactional`. `Order` y `PortfolioCalculator` sin imports de infraestructura. |
| **Tests y Cobertura** | ✅ | `./gradlew test` BUILD SUCCESSFUL (unit + IT). `./gradlew check` BUILD SUCCESSFUL con `jacocoTestCoverageVerification` ≥80% en `PortfolioCalculator` (100% lines/methods). |
| **Seguridad y Datos** | ✅ | ProblemDetail (RFC 7807) en `GlobalExceptionHandler`. Validación estricta entrada. Sin SQL injection (JPA/Specification). Migración `V5` acotada a `status='FILLED'`. |

---

## Hallazgos Críticos (Bloqueantes)
*Ninguno.*

---

## Hallazgos Menores (No bloqueantes)

1. **`src/main/java/ar/com/cocos/challenge/infrastructure/adapter/out/persistence/PersistenceMapper.java:72`**  
   `updateEntity` vuelve a escribir `price` en cada actualización (correcto semánticamente, ya que `price` no cambia; sólo ruido de persistencia).

2. **`src/main/java/ar/com/cocos/challenge/infrastructure/adapter/out/persistence/PersistenceMapper.java:67`**  
   `requestHash` usa `idempotencyKey` como placeholder (deuda técnica conocida, fuera de alcance R006).

---

## Próximos Pasos
- [ ] Cerrar requerimiento: actualizar `state.json` a `done` y `specs/README.md` a `✅ done`.
- [ ] (Opcional) Evaluar si el ADR-008 merece referenciarse en `specs/000-baseline-challenge/spec.md` como convención de dominio.

---

**Fecha:** 2026-11-14  
**Reviewer:** agente `reviewer` (subagent)  
**Veredicto final:** ✅ **Aprobado**