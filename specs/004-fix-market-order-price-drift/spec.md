# Requerimiento 004 — Prevenir descalce de precio entre validación y ejecución en órdenes MARKET

| Campo | Valor |
| :--- | :--- |
| **Número** | 004 |
| **Carpeta** | `specs/004-fix-market-order-price-drift/` |
| **Tipo** | bugfix |
| **Estado** | draft |
| **Creado** | 2026-11-14 |
| **Actualizado** | 2026-11-14 |
| **Depende de** | 000 |
| **Supersede a** | — |

> **Alcance de este documento:** solo el **delta** de este requerimiento. Las invariantes del
> proyecto (hexagonal, `BigDecimal`, transacciones, locks, idempotencia, secretos) son globales y
> viven en `AGENTS.md` y en [`../000-baseline-challenge/spec.md`](../000-baseline-challenge/spec.md).
> No las repitas: referencialas. Los ADR aplicables están en `docs/adr/`.

---

## 1. Contexto y motivación

En el flujo de ejecución de órdenes de tipo `MARKET` (`PlaceOrderUseCase`), el sistema realiza dos llamadas independientes a `marketDataProvider.findLatestPrice()`:
1. Una primera vez en `resolveExecutionPrice()` para obtener el precio de referencia necesario para la validación de saldo y tenencia (`BalanceValidator`).
2. Una segunda vez dentro de `executeMarketOrder()` en `OrderMatcher` para asignar el precio final de ejecución (`price.close()`) y marcar la orden como `FILLED`.

Si entre ambas llamadas la caché expira, se refresca o ingresa una nueva cotización de mercado con un precio superior, la orden se ejecuta a un precio mayor al validado. Esto provoca que la cuenta del usuario sufra un debitamiento mayor al saldo verificado, derivando en un **sobregiro silencioso (negative balance)**.

## 2. Alcance

### Dentro

- Asegurar consistencia atómica del precio entre la validación de saldo/tenencia y la ejecución de la orden `MARKET`.
- Evitar lecturas duplicadas e inconsistentes de `marketDataProvider.findLatestPrice()` durante la ejecución de `PlaceOrderUseCase`.
- **Eliminar la caché (`@Cacheable`) en `MarketDataJpaAdapter.findLatestPrice/findLatestPrices`** para que cada consulta obtenga el precio más fresco desde la BD, evitando precios obsoletos que causan descalce.
- Garantizar que si el precio retenido o de ejecución supera el saldo disponible, la orden sea rechazada (`REJECTED`) respetando las invariantes de dominio.
- Agregar/actualizar tests unitarios e integrativos que aseguren la prevención de descalce de precios.

### Fuera

- Modificaciones a órdenes de tipo `LIMIT` (ya utilizan un precio fijo enviado en la solicitud).
- Alteraciones en la firma o esquema de los endpoints REST de la API.

## 3. Criterios de aceptación (Draft)

| ID | Given | When | Then |
| :--- | :--- | :--- | :--- |
| CA-01 | Un usuario con saldo exacto para comprar $N$ acciones al precio de mercado $P_1$ | Se procesa una orden `MARKET` y se captura el precio $P_1$ | La orden se valida y se ejecuta utilizando únicamente el precio capturado $P_1$, sin re-consultar o discrepar en el precio final de ejecución |
| CA-02 | Un intento de colocar una orden `MARKET` | Se ejecuta la orden dentro del caso de uso `PlaceOrderUseCase` | El precio de ejecución de la orden `FILLED` coincide exactamente con el precio de referencia utilizado en `balanceValidator.validate()` |
| CA-03 | Un escenario con volatilidad o variaciones de precio en el proveedor | Se ejecuta la orden `MARKET` | El sistema previene el sobregiro garantizando que la ejecución no sobrepase el saldo validado bajo ningún descalce de caché |
| CA-04 | Cualquier consulta a `marketDataProvider.findLatestPrice()` | Se ejecuta dentro o fuera de `PlaceOrderUseCase` | Devuelve el precio **más reciente** persistido en BD (sin caché), garantizando frescura de datos para validación y ejecución |

## 4. Contratos afectados

Sin cambios en contratos externos. El request/response del endpoint `POST /orders` se mantiene idéntico.

## 5. Impacto en requerimientos previos

- Compatible con `000-baseline-challenge`.
- Ajusta el comportamiento interno de `PlaceOrderUseCase` y `OrderMatcher` para garantizar inmutabilidad del precio de ejecución durante el flujo.

## 6. NFR específicos

- **Consistencia:** Cero sobregiros de saldo por fluctuación de precio entre validación y ejecución.
- **Invariantes:** Mantiene idempotencia transaccional y reglas de `BigDecimal` (scale 2, `HALF_UP`).

## 7. Supuestos y pendientes

- **SUPUESTO:** El precio de mercado debe fijarse al inicio del caso de uso `PlaceOrderUseCase` (o ser pasado de forma explícita a la ejecución de la orden `MARKET`) para que la validación y la ejecución compartan el mismo snapshot de precio.
- **PENDIENTE:** Clarificar si `executeMarketOrder` debe recibir opcionalmente el precio de mercado resuelto o si `OrderMatcher` debe rediseñarse para recibir la cotización capturada.

## 8. Definition of Done

- [ ] Criterios de aceptación CA-01..CA-03 verificados
- [ ] `./gradlew test` verde
- [ ] Cobertura ≥ 80 % en los servicios de dominio tocados (`OrderMatcher`, `PlaceOrderUseCase`)
- [ ] Documentación/contratos actualizados si aplica
- [ ] Reviewer emitió ✅ en `review.md`
