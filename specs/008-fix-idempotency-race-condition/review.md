# Revisión — 008 Arreglar la race condition de idempotencia [✅ Aprobado]

## Resumen
La implementación resuelve correctamente la race condition cross-user en la clave de idempotencia usando **Opción A** (`SELECT FOR UPDATE SKIP LOCKED` + `INSERT ON CONFLICT`). Todos los criterios de aceptación CA-01..CA-05 están verificados por tests (nuevos y de regresión), la arquitectura hexagonal se respeta, la cobertura supera el 80% y no hay regresiones.

## Checklist
| Categoría | Estado | Hallazgos |
|-----------|--------|-----------|
| Cumplimiento del spec | ✅ | CA-01..CA-05 cubiertos; alcance respetado; contratos inmutados |
| Invariantes globales | ✅ | Hexagonal, BigDecimal, transacciones, locks, idempotencia, secretos — todo OK |
| Arquitectura Hexagonal | ✅ | Domain puro (sin Spring/JPA); ports en `domain/port`; adapters en `infrastructure/adapter` |
| Transacciones/Concurrencia | ✅ | `@Transactional` en use case; `lockById(userId)` → `findByKeyWithLock(key)`; `PESSIMISTIC_WRITE` en user/order; slot atómico con `INSERT ON CONFLICT` |
| Tests | ✅ | `IdempotencyRaceConditionIT` (CA-01, CA-02) estable en 3 ejecuciones; `PlaceOrderUseCaseTest` actualizado con verificación de orden; `PlaceOrderFlowIT` cubre CA-03/04/05 |
| Seguridad | ✅ | Cero credenciales hardcodeadas; validación de entrada; ProblemDetail RFC 7807 |
| Documentación | ⚠️ | ADR-009 creado y referenciado (spec.md menciona ADR-003 por error menor) |

## Hallazgos Críticos (Bloqueantes)
**Ninguno.** La implementación cumple con todos los criterios de aceptación y las invariantes del proyecto.

## Hallazgos Menores (No bloqueantes)
1. **[spec.md:117]** El spec menciona "ADR-003 actualizado o creado" pero el ADR real es **ADR-009** (ADR-003 ya existía para Resilience4j). Es solo inconsistencia de documentación en el spec, no afecta código.
2. **[IdempotencyRaceConditionIT.java:65-96]** Código muerto: `restTemplate`, `baseUrl` y método `getPort()` no se usan (el test llama al port directamente). Recomendado limpiar.
3. **[state.json]** Muestra `status: "ready"` y `currentPhase: 0` pero `tasks.md` tiene todas las tareas marcadas `[x]`. Actualizar estado al cerrar el requerimiento.

## Próximos Pasos
- [ ] coordinator: actualizar `state.json` a `status: "done"` y `lastReview` con timestamp
- [ ] coordinator: guardar este reporte en `specs/008-fix-idempotency-race-condition/review.md`
- [ ] Opcional (implementer): limpiar código muerto en `IdempotencyRaceConditionIT` y corregir referencia ADR en `spec.md` si se desea consistencia documental