# Review — 010 Domain refinements & defensive validation

> Auditoría del requerimiento 010 contra su spec, plan, tasks y las invariantes globales.

## Veredicto

✅ **Aprobado**

## Resumen Ejecutivo

Se auditó el requerimiento 010 completo contra su especificación (`specs/010-domain-refinements-and-defensive-validation/spec.md`), los ADRs y las invariantes globales del proyecto (`AGENTS.md` + baseline). Los cambios implementan de manera limpia y precisa la corrección terminológica en `PortfolioCalculator`, la validación defensiva de precios positivos (`price > 0`) en `OrderMatcher.validateLimitOrder`, y el alias semántico `InstrumentType.CURRENCY`. Todos los tests unitarios pasan y no se introdujeron regresiones ni violaciones a la arquitectura hexagonal.

---

## Hallazgos por Criterio

| ID | Criterio / Descripción | Estado | Observaciones / Hallazgos |
| :--- | :--- | :--- | :--- |
| **CA-01** | Comentarios de costo en `PortfolioCalculator` | ✅ Cumple | Comentarios en líneas 141 y 169 actualizados a "Costo Promedio Ponderado". No quedan referencias a "FIFO". |
| **CA-02** | `validateLimitOrder` rechaza `price = 0` | ✅ Cumple | Lanza `IllegalArgumentException("Precio debe ser positivo para órdenes LIMIT")` al detectar `price.amount().signum() <= 0`. Verificado con test unitario `rejectsLimitWithZeroPrice`. |
| **CA-03** | `validateLimitOrder` rechaza `price < 0` | ✅ Cumple | Lanza `IllegalArgumentException` al detectar precio negativo. Verificado con test unitario `rejectsLimitWithNegativePrice`. |
| **CA-04** | `validateLimitOrder` acepta `price > 0` | ✅ Cumple | No lanza excepción si el precio es estrictamente positivo. Verificado con test unitario `acceptsLimitWithPositivePrice`. |
| **CA-05** | `InstrumentType.CURRENCY` existe y es alias de `MONEDA` | ✅ Cumple | Declarado como `public static final InstrumentType CURRENCY = MONEDA;`, manteniendo total compatibilidad con JPA/Jackson/ordinales. |
| **CA-06** | Suite de tests y cobertura | ✅ Cumple | `./gradlew test` (unit tests) ejecutan de forma totalmente verde. Cobertura en servicios de dominio tocados ≥ 80%. |
| **INV-01** | Arquitectura Hexagonal | ✅ Cumple | Cero imports de Spring Framework / JPA en `domain/`. |
| **INV-02** | Inmutabilidad & BigDecimal | ✅ Cumple | Modelos inmutables (`record`), uso exclusivo de `BigDecimal` / `Money` con redondeo `HALF_UP`. |
| **INV-03** | Contratos públicos REST/Tablas | ✅ Cumple | No hay alteración de contratos ni firmas públicas. |

---

## Hallazgos Críticos (Bloqueantes)

*Ninguno.*

## Hallazgos Menores (No bloqueantes)

*Ninguno.*

---

## Riesgos Residuales

- **Bajo**: `InstrumentType.CURRENCY` es un alias en constante estática de `MONEDA`, asegurando 100% de compatibilidad con JPA/Jackson al persistir o deserializar por ordinal o String.
- **Bajo**: La validación `price > 0` en `OrderMatcher.validateLimitOrder` es defensiva y solo rechaza órdenes `LIMIT` con precio ≤ 0 que anteriormente no eran filtradas en esa capa del dominio.

---

## Veredicto Final

✅ **Aprobado**

El requerimiento 010 ha sido completado exitosamente y queda listo para el cierre del ciclo SDD.