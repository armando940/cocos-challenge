# Plan de Ejecución — 007 Fix findLatestPrices sin LIMIT

> Derivado de [`spec.md`](spec.md) y del estado real del repo el 2026-11-14.
> Los IDs de tarea son `R007-T01`, `R007-T02`… y solo existen dentro de este requerimiento.

## Resumen

- **Fases**: 2 (Query + Adapter + Tests)
- **Olas**: 2 (W1, W2) — 3 tareas paralelizables totales
- **Horas estimadas**: ~2.5h
- **Camino crítico**: R007-T01 → R007-T02 → R007-T03
- **Riesgo mayor**: R007-T01 (query SQL) — si la sintaxis DISTINCT ON falla en Testcontainers, reescribir con ROW_NUMBER()

## Estado actual vs plan

| Fase | Objetivo | Estado | Evidencia |
| :--- | :--- | :--- | :--- |
| 1 | Reemplazar query en `MarketDataJpaRepository` por DISTINCT ON | ⬜ Pendiente | `grep -n "findByInstrumentIdInOrderByDatetimeDesc" src/main/java/.../MarketDataJpaRepository.java` muestra la nueva query |
| 2 | Simplificar `MarketDataJpaAdapter.findLatestPrices` y tests | ⬜ Pendiente | `./gradlew test` verde; test de integración valida 1 fila por instrumento |

## Fase 1 — Query optimizada en Repositorio (~1h)

**Objetivo**: Cambiar `findByInstrumentIdInOrderByDatetimeDesc` para que devuelva solo 1 fila por instrumento usando `DISTINCT ON` de PostgreSQL.
**Entregable**: `MarketDataJpaRepository.java` con query optimizada.
**DoD**: `./gradlew compileJava` compila; query SQL inspeccionada con `EXPLAIN` muestra index scan en `idx_marketdata_instrument_datetime`.

| ID | Tarea | Depende de | Estimación | Riesgo | Done |
| :--- | :--- | :--- | :--- | :--- | :--- |
| R007-T01 | Reemplazar `@Query` por `DISTINCT ON (instrument_id) ORDER BY datetime DESC` en `MarketDataJpaRepository` | — | 0.75h | Medio | `grep -n "DISTINCT ON" src/main/java/.../MarketDataJpaRepository.java` |
| R007-T02 | Ajustar `MarketDataJpaAdapter.findLatestPrices`: quitar `toMap` con merge function (la query ya trae 1 por instrumento) | R007-T01 | 0.25h | Bajo | `grep -n "toMap" src/main/java/.../MarketDataJpaAdapter.java` no aparece en findLatestPrices |

## Fase 2 — Tests y validación (~1.5h)

**Objetivo**: Agregar test de integración que valide que la query trae exactamente 1 fila por instrumento y que los tests existentes pasan.
**Entregable**: Test de integración + `./gradlew test` verde.
**DoD**: `./gradlew test` verde; cobertura ≥ 80% en domain services; test específico verifica CA-01..CA-04.

| ID | Tarea | Depende de | Estimación | Riesgo | Done |
| :--- | :--- | :--- | :--- | :--- | :--- |
| R007-T03 | Agregar test de integración `MarketDataJpaAdapterIT.findLatestPrices_returnsOnlyLatestPerInstrument` con dataset multi-instrumento/multi-fila | R007-T02 | 1h | Bajo | Test pasa y valida CA-01..CA-04 |
| R007-T04 | Correr suite completa y verificar cobertura | R007-T03 | 0.5h | Bajo | `./gradlew check` verde |

## Olas de ejecución

| Ola | Tareas en paralelo | Lanes | Serializa | Verificación de cierre |
| :-- | :-- | :-- | :-- | :-- |
| W1 | R007-T01, R007-T02 | 2 | — | `./gradlew compileJava test --tests "*MarketDataJpaAdapter*"` |
| W2 | R007-T03, R007-T04 | 2 | — | `./gradlew check` |

> R007-T01 y R007-T02 tocan archivos distintos (Repository vs Adapter) → pueden ir en paralelo en W1.
> R007-T03 y R007-T04 tocan archivos de test y no compiten → pueden ir en paralelo en W2.
> Máximo 3 lanes por ola. Archivos de frontera no aplica aquí.

## Camino crítico

R007-T01 → R007-T02 → R007-T03 → R007-T04  
(La query debe compilar antes de ajustar el adapter; el adapter debe funcionar antes del test de integración; el test debe pasar antes del check final).

## Riesgos y mitigación

| Riesgo | Probabilidad | Impacto | Mitigación |
| :--- | :--- | :--- | :--- |
| `DISTINCT ON` no soportado o sintaxis distinta en Testcontainers (PostgreSQL) | Media | Medio | Si falla, reescribir R007-T01 con `ROW_NUMBER() OVER (PARTITION BY instrument_id ORDER BY datetime DESC) = 1` (standard SQL) |
| Query usa índice incorrecto (seq scan) | Baja | Medio | Verificar con `EXPLAIN ANALYZE` en test; forzar hint si necesario (poco probable con idx existente) |
| Tests existentes rompen por cambio de comportamiento sutil | Baja | Alto | Ejecutar `./gradlew test` tras cada ola; comparar resultados con fixture known-good |