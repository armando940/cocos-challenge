# Requerimiento 006 — Separar precio límite de precio de ejecución (`Order.filled`) y desacoplar el portfolio

| Campo | Valor |
| :--- | :--- |
| **Número** | 006 |
| **Carpeta** | `specs/006-fix-filled-price-semantics/` |
| **Tipo** | bugfix |
| **Estado** | clarified |
| **Creado** | 2026-11-14 |
| **Actualizado** | 2026-11-14 |
| **Depende de** | 000, 004 |
| **Supersede a** | — |

> **Alcance de este documento:** solo el **delta** de este requerimiento. Las invariantes del
> proyecto (hexagonal, `BigDecimal`, transacciones, locks, idempotencia, secretos) son globales y
> viven en `AGENTS.md` y en [`../000-baseline-challenge/spec.md`](../000-baseline-challenge/spec.md).
> No las repitas: referencialas. Los ADR aplicables están en `docs/adr/`.

---

## 1. Contexto y motivación

`Order` tiene dos campos de precio con semántica ambigua:

- `price`: debería ser el **precio límite original** de la orden (null para `MARKET`).
- `executedPrice`: el **precio real de ejecución** (solo para `FILLED`).

Hoy `Order.filled(executedPrice, executedSize)` **sobrescribe `price` con el precio de ejecución**
(línea 109 de `Order.java`). Eso hace que `price` signifique cosas distintas según el estado de la
orden y destruye el dato original de una orden `LIMIT` al ejecutarse.

`PortfolioCalculator` usa `order.price()` en **dos lugares**:

1. `cashEffect(...)`: efecto de caja de `CASH_IN`, `CASH_OUT`, `BUY`, `SELL`.
2. `calculateWeightedAverageCost(...)`: costo promedio ponderado de las compras.

En ambos casos el cálculo **asume que `price` es el precio de ejecución**. Funciona sólo "de
casualidad" porque `filled()` pisa `price`; si alguien usa `executedPrice` como corresponde, el
portfolio calcula mal (efecto de caja o costo en cero para órdenes `MARKET`, o con el precio
límite equivocado para `LIMIT`). El bug está latente y es frágil: cualquier refactor de
`Order` rompe el portfolio sin que falle la compilación.

## 2. Alcance

### Dentro

- `Order.filled(executedPrice, executedSize)`: **preservar `price`** (límite original, `null` en
  `MARKET`) y escribir únicamente `executedPrice`/`executedSize`.
- Reforzar la invariante de dominio de una orden `FILLED` en el constructor compacto de `Order`:
  `executedPrice` y `executedSize` obligatorios, `executedPrice > 0`, `0 <= executedSize <= size`.
- `PortfolioCalculator`: usar `executedPrice()` en `cashEffect` y en `calculateWeightedAverageCost`,
  eliminando el fallback silencioso a `BigDecimal.ZERO` y la dependencia de `price()`.
- Migración Flyway de compatibilidad (`V5`) que hace backfill de `executed_price`/`executed_size`
  para órdenes `FILLED` históricas (el esquema legado `V1` las dejó en `NULL`).
- Tests unitarios de `Order`, `PortfolioCalculator` y `OrderMatcher` alineados a la nueva semántica.
- Test de integración que verifique la persistencia separada (`price` vs `executed_price`) y el
  débito de caja calculado con el precio de ejecución.
- ADR global que documenta la semántica de precios y la estrategia de compatibilidad.

### Fuera

- Cambios en los endpoints REST y sus DTOs (`POST /api/v1/orders`, `GET /api/v1/portfolio/{id}`,
  cancelación). El contrato externo no cambia.
- Cambios en el matching de órdenes `LIMIT` (siguen quedando en `NEW`; no hay motor de ejecución).
- Rediseño del cálculo de posiciones (FIFO real, lotes, fills parciales). Se mantiene el promedio
  ponderado simple del baseline.
- Cambios en idempotencia, locks o resiliencia.

## 3. Criterios de aceptación

| ID | Given | When | Then |
| :--- | :--- | :--- | :--- |
| CA-01 | Una orden `LIMIT` `BUY` con `price = 100.00` | Se ejecuta `filled(115.00, 10)` | `price` sigue siendo `100.00` y `executedPrice` es `115.00` |
| CA-02 | Una orden `MARKET` recién creada (`price = null`) | Se ejecuta `filled(11200.00, 10)` | `price == null`, `executedPrice == 11200.00`, `executedSize == 10` y `status == FILLED` |
| CA-03 | Una orden marcada `FILLED` | Se construye sin `executedPrice`, sin `executedSize`, con precio no positivo o con `executedSize > size` | El constructor de `Order` lanza `IllegalArgumentException` (estado inválido no representable) |
| CA-04 | Un portfolio con órdenes `BUY`/`SELL`/`CASH_IN`/`CASH_OUT` `FILLED` | Se calcula el efectivo disponible | El efecto de caja usa `executedPrice × size`; `price` (límite) no participa del cálculo |
| CA-05 | Un portfolio con compras `MARKET` (`price = null`) y `LIMIT` (`price != executedPrice`) | Se calcula el costo promedio ponderado | El costo usa `executedPrice`; nunca se ignora la compra por `price` nulo ni se usa el límite |
| CA-06 | Una base con órdenes `FILLED` legadas (`executed_price IS NULL`) | Arranca la aplicación con Flyway | `V5` completa `executed_price = price` y `executed_size = size`; el mapeo a dominio no falla |
| CA-07 | Cualquier request existente de `POST /api/v1/orders` | Se envía y se lee la respuesta | El JSON de respuesta no cambia (`executedPrice` sigue informándose para `FILLED`) |
| CA-08 | Suite completa | Se ejecuta `./gradlew test` | Verde, incluidos unit tests e ITs de órdenes y portfolio |

## 4. Contratos afectados

Sin cambios en contratos externos. Se explicita el contrato interno del modelo de dominio:

| Campo | `NEW` | `FILLED` | `REJECTED` / `CANCELLED` |
| :--- | :--- | :--- | :--- |
| `price` | Límite (LIMIT) o `null` (MARKET) | Igual al original (nunca se sobreescribe) | Igual al original |
| `executedPrice` | `null` | Precio real de ejecución (`> 0`) | `null` |
| `executedSize` | `null` | Cantidad ejecutada (`0 <= executedSize <= size`) | `null` |

`OrderResponse` (`executedPrice`, `executedSize`) se mantiene idéntico.

## 5. Impacto en requerimientos previos

- **004** capturó el precio de referencia una sola vez para evitar descalce. Este requerimiento
  completa esa decisión: el snapshot se persiste en `executedPrice`, no en `price`.
- **005** no se ve afectado (el lock y la validación usan `size` y el precio de referencia, no
  `Order.price`).
- Compatibilidad de datos: `V5` hace backfill sólo de filas `FILLED` con `executed_price IS NULL`;
  no modifica `price` ni órdenes `NEW`/`REJECTED`/`CANCELLED`.
- El baseline `000` define el efectivo como `Σ size` de `FILLED`; el precio usado para valuar ese
  flujo pasa a ser explícitamente el de ejecución.

## 6. NFR específicos

- Sin dependencias nuevas.
- `BigDecimal` para todo precio; se mantienen scale 2 / `HALF_UP` vía `Money`.
- Modelo inmutable (records) y dominio sin imports de infraestructura.
- Migración idempotente y segura para producción (sólo `UPDATE` acotado por `status = 'FILLED'`).

## 7. Supuestos y decisiones (resueltos sin bloquear)

- **DECISIÓN 1:** `price` = precio límite original; `executedPrice` = precio real de ejecución.
  `filled()` preserva `price`.
- **DECISIÓN 2:** una orden `FILLED` sin `executedPrice`/`executedSize` es un estado inválido y se
  rechaza en el constructor (fail-fast). No se agregan fallbacks silenciosos.
- **DECISIÓN 3:** el portfolio no conoce "precio límite": para caja y costo usa siempre
  `executedPrice`; las cantidades siguen siendo `size` (baseline: no hay fills parciales).
- **DECISIÓN 4:** compatibilidad con datos legados vía migración `V5` de backfill; no se tocan las
  migraciones existentes (`V1`–`V4`).
- **DECISIÓN 5:** se registra ADR-008 con la semántica y la estrategia de migración.
- **PENDIENTE:** ninguno que bloquee la ejecución.

## 8. Definition of Done

- [ ] Criterios de aceptación CA-01..CA-08 verificados
- [ ] `./gradlew test` verde (unit + IT)
- [ ] Cobertura ≥ 80 % en los servicios de dominio tocados (`PortfolioCalculator`)
- [ ] ADR-008 y su fila en `docs/adr/README.md`
- [ ] Migración `V5` aplicada en la suite de integración
- [ ] Reviewer emitió ✅ en `review.md`
