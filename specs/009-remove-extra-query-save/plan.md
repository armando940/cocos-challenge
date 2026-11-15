# Plan de Ejecución — 009 Eliminar query extra en PostgresOrderRepository.save()

> Derivado de [`spec.md`](spec.md) y del estado real del repo el 2026-11-15.
> Los IDs de tarea son `R009-T01`, `R009-T02`… y solo existen dentro de este requerimiento.

## Resumen

- **Fases**: 2 (Implementación, Verificación)
- **Olas**: 1 (W1) — 2 tareas en serie
- **Horas estimadas**: 1.5 h
- **Camino crítico**: R009-T01 → R009-T02
- **Riesgo mayor**: R009-T01 (cambio en lógica de persistencia, aunque bajo impacto por tests existentes)

## Estado actual vs plan

| Fase | Objetivo | Estado | Evidencia |
| :--- | :--- | :--- | :--- |
| 1 | Simplificar `save()` eliminando `findById` explícito | ⬜ Pendiente | `src/main/java/.../PostgresOrderRepository.java` |
| 2 | Verificar con tests de integración y unidad | ⬜ Pendiente | `./gradlew test` verde |

## Fase 1 — Implementación del refactor (~1 h)

**Objetivo**: Eliminar la llamada a `jpaRepository.findById()` en `PostgresOrderRepository.save()` y simplificar a un solo camino usando `PersistenceMapper.toEntity()` + `jpaRepository.save()`.

**Entregable**: `PostgresOrderRepository.save()` refactorizado sin query extra.

**DoD**: `./gradlew compileJava` compila sin errores.

| ID | Tarea | Depende de | Estimación | Riesgo | Done |
| :--- | :--- | :--- | :--- | :--- | :--- |
| R009-T01 | Refactor `save()`: usar siempre `PersistenceMapper.toEntity(order)` + `jpaRepository.save(entity)` | — | 1 h | Bajo | `./gradlew compileJava` |

## Fase 2 — Verificación (~0.5 h)

**Objetivo**: Confirmar que el refactor no rompe comportamiento (CA-01, CA-02) y que la suite completa pasa.

**Entregable**: Suite de tests verde y cobertura ≥ 80 % en servicios de dominio tocados.

**DoD**: `./gradlew test` y `./gradlew check` verdes.

| ID | Tarea | Depende de | Estimación | Riesgo | Done |
| :--- | :--- | :--- | :--- | :--- | :--- |
| R009-T02 | Ejecutar suite completa (`./gradlew test`) y verificar cobertura (`./gradlew check`) | R009-T01 | 0.5 h | Bajo | `./gradlew check` |

## Olas de ejecución

| Ola | Tareas en paralelo | Lanes | Serializa | Verificación de cierre |
| :-- | :-- | :-- | :-- | :-- |
| W1 | R009-T01 | 1 | — | `./gradlew compileJava` |
| W2 | R009-T02 | 1 | — | `./gradlew check` |

> **Nota**: Ambas tareas tocan rutas disjuntas (implementación vs. solo ejecución de tests), pero R009-T02 depende de R009-T01. No hay paralelismo posible. Archivos de frontera: ninguno (solo `PostgresOrderRepository.java`).

## Camino crítico

R009-T01 → R009-T02

## Riesgos y mitigación

| Riesgo | Probabilidad | Impacto | Mitigación |
| :--- | :--- | :--- | :--- |
| Regresión en actualización de órdenes existentes (camino `else` eliminado) | Baja | Medio | Tests de integración `PlaceOrderFlowIT` cubren creación, ejecución, cancelación y re-ejecución idempotente; ejecutar suite completa antes de cerrar. |
| `PersistenceMapper.toEntity()` no preserva campos que `updateEntity` actualizaba (ej. `created_at`) | Baja | Bajo | Revisar `PersistenceMapper.toEntity()`: setea todos los campos incluyendo `id`; `created_at`/`updated_at` se gestionan con `@PrePersist`/`@PreUpdate` en la entidad JPA. Verificado en código. |
| Tests de integración fallan por configuración Testcontainers (entorno) | Media | Bajo | Ejecutar localmente con Docker Desktop; en CI ya está validado. No bloquea el plan. |