# Requerimiento 010 — Domain refinements & defensive validation

| Campo | Valor |
| :--- | :--- |
| **Número** | 010 |
| **Carpeta** | `specs/010-domain-refinements-and-defensive-validation/` |
| **Tipo** | refactor |
| **Estado** | done |
| **Creado** | 2026-11-15 |
| **Actualizado** | 2026-11-15 |
| **Depende de** | 000-baseline-challenge |
| **Supersede a** | — |

> **Alcance de este documento:** solo el **delta** de este requerimiento. Las invariantes del
> proyecto (hexagonal, `BigDecimal`, transacciones, locks, idempotencia, secretos) son globales y
> viven en `AGENTS.md` y en [`../000-baseline-challenge/spec.md`](../000-baseline-challenge/spec.md).
> No las repitas: referencialas. Los ADR aplicables están en `docs/adr/`.

---

## 1. Contexto y motivación

Tres mejoras de precisión semántica y robustez defensiva en el dominio:
1. **Terminología de Costo**: En `PortfolioCalculator`, el comentario dice "FIFO simplificado". Técnicamente, la implementación es **Costo Promedio Ponderado (Average Cost)**, no FIFO. Ajustar el comentario a "Costo Promedio Ponderado" es 100% preciso.
2. **Validación de Precio en LIMIT**: En `OrderMatcher.validateLimitOrder`, se valida que el precio no sea null. Agregar también validación `price > 0` como *defensive programming*.
3. **Constante para Instrumento Moneda**: El código asume que las operaciones de efectivo usan el instrumento ARS (tipo MONEDA). Agregar constante `InstrumentType.CURRENCY` como alias semántico de `MONEDA` para mayor claridad y evitar depender de la lógica interna `order.isCashOperation()`.

---

## 2. Alcance

### Dentro

- `PortfolioCalculator.java`: Actualizar comentarios en líneas 141 y 169 de "FIFO simplificado" a "Costo Promedio Ponderado".
- `OrderMatcher.java`: En `validateLimitOrder`, agregar validación de que `price.amount() > 0` para órdenes LIMIT.
- `InstrumentType.java`: Agregar constante `CURRENCY` que sea alias de `MONEDA` (o renombrar/añadir alias).

### Fuera

- Cambios en la lógica de cálculo de costo (el algoritmo sigue siendo promedio ponderado).
- Cambios en la API pública / contratos REST.
- Migración de base de datos.
- Cambios en `Instrument.isCurrency()` — ya existe y funciona.

---

## 3. Criterios de aceptación

| ID | Given | When | Then |
| :--- | :--- | :--- | :--- |
| CA-01 | Código fuente de `PortfolioCalculator` | Se leen los comentarios en `buildPosition` y `calculateWeightedAverageCost` | Ambos dicen "Costo Promedio Ponderado" (sin mencionar FIFO) |
| CA-02 | Una orden LIMIT con precio `0` o negativo | Se llama `OrderMatcher.validateLimitOrder` | Lanza `IllegalArgumentException` con mensaje que indique precio inválido |
| CA-03 | Una orden LIMIT con precio positivo | Se llama `OrderMatcher.validateLimitOrder` | No lanza excepción |
| CA-04 | Código usa `InstrumentType.CURRENCY` | Se compila y tests pasan | `InstrumentType.CURRENCY` existe y es igual a `InstrumentType.MONEDA` |
| CA-05 | Suite de tests completa | `./gradlew test` | Todos los tests pasan (incluyendo nuevos tests de validación) |
| CA-06 | Cobertura de dominio | `./gradlew check` | Cobertura ≥ 80% en servicios de dominio tocados |

---

## 4. Contratos afectados

Ningún contrato público (REST, eventos, tablas) cambia. Solo documentación interna y validaciones defensivas.

---

## 5. Impacto en requerimientos previos

- Compatible con todos los requerimientos previos (000–009).
- No rompe comportamiento existente: la validación `price > 0` es una restricción adicional que solo afecta inputs inválidos (precio 0 o negativo) que antes no se validaban.
- `InstrumentType.CURRENCY` es un alias de `MONEDA`; código existente usando `MONEDA` o `Instrument.isCurrency()` sigue funcionando.

---

## 6. NFR específicos

Sin NFR adicionales: aplican las invariantes de `AGENTS.md` (inmutabilidad, BigDecimal, hexagonal, etc.).

---

## 7. Supuestos y pendientes

- **SUPUESTO**: `InstrumentType.CURRENCY` se implementa como alias de `MONEDA` (mismo ordinal/valor), no como nuevo elemento del enum, para mantener compatibilidad binaria y de BD.
- **SUPUESTO**: La validación `price > 0` en `validateLimitOrder` usa `command.price().amount().signum() <= 0` para detectar cero y negativos.
- **SUPUESTO**: Los tests existentes de `OrderMatcherTest` que validan `validateLimitOrder` no necesitan cambios salvo agregar casos para precio ≤ 0.

---

## 8. Definition of Done

- [ ] Criterios de aceptación CA-01..CA-06 verificados
- [ ] `./gradlew test` verde
- [ ] Cobertura ≥ 80 % en los servicios de dominio tocados (`PortfolioCalculator`, `OrderMatcher`)
- [ ] Documentación/contratos actualizados (no aplica, cambios internos)
- [ ] Reviewer emitió ✅ en `review.md`