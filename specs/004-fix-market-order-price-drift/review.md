# Revisión — 004 Prevenir descalce de precio entre validación y ejecución en órdenes MARKET [✅ Aprobado]

## Resumen
El requerimiento 004 resuelve correctamente el bug de descalce de precio (price drift) en órdenes MARKET. La implementación:
1. Captura el precio de referencia **una sola vez** al inicio de `PlaceOrderUseCase.execute()` y lo reutiliza tanto para la validación de saldo/tenencia como para la ejecución final, eliminando la doble lectura inconsistente de `marketDataProvider.findLatestPrice()`.
2. **Elimina la caché (`@Cacheable`) en `MarketDataJpaAdapter.findLatestPrice/findLatestPrices`** para que cada consulta obtenga el precio más fresco desde BD, evitando precios obsoletos que causan descalce.

Todos los criterios de aceptación (CA-01, CA-02, CA-03, CA-04) están verificados con tests unitarios e integrativos. La suite completa pasa (`./gradlew check` verde) con cobertura ≥ 80 % en los servicios tocados.

## Checklist

| Categoría | Estado | Hallazgos |
|-----------|--------|-----------|
| Cumplimiento del spec (CA-01..CA-04) | ✅ | CA-01/02/03: Precio capturado una vez (línea 83 `PlaceOrderUseCase`), usado en validación (línea 87) y ejecución (línea 99). CA-04: `@Cacheable` eliminada en `MarketDataJpaAdapter` (W7/R004-T10). Tests `preventsPriceDriftBetweenValidationAndExecution` (unit) y `preventsMarketOrderPriceDrift` (IT) confirman. |
| Invariantes del proyecto | ✅ | `BigDecimal` scale 2 vía `Money` (HALF_UP), records inmutables, idempotencia intacta, sin credenciales hardcodeadas. |
| Arquitectura Hexagonal | ✅ | `domain/` sin imports Spring/JPA. Puertos en `domain/port/{in,out}`, adapters en `infrastructure/adapter/...`, use cases con `@Service` + `@Transactional`. |
| Transacciones/Concurrencia | ✅ | `@Transactional` en `PlaceOrderUseCase.execute`. Precio fijado dentro de la transacción. Lock pesimista disponible en infraestructura (`OrderJpaRepository.findByIdWithLock` con `PESSIMISTIC_WRITE`). |
| Tests y Cobertura | ✅ | `./gradlew check` verde. Cobertura: OrderMatcher 96%/86%, PlaceOrderUseCase 90%/88%, BalanceValidator 100%. |
| Seguridad y Datos | ✅ | ProblemDetail (RFC 7807) en `GlobalExceptionHandler` con correlation ID. Validación estricta entrada. Sin SQL injection (JPA/Specification). |

## Hallazgos Críticos (Bloqueantes)
*Ninguno.*

## Hallazgos Menores (No bloqueantes)
1. **[src/main/java/ar/com/cocos/challenge/domain/service/BalanceValidator.java:77]** `validateShareAvailability` usa `orderRepository.findFilledByUserIdAndInstrumentId` sin lock pesimista. Es preexistente y fuera del alcance del R004, pero es un punto de mejora para consistencia de concurrencia global.

## Próximos Pasos
- [ ] (opcional) futuro: evaluar añadir lock pesimista en `BalanceValidator` para cerrar ventana de carrera en validación de tenencia (fuera de scope R004)