# Revisión — 012 `datetime` nullable en MarketData y valoración degradada del portfolio

| Campo | Valor |
| :--- | :--- |
| **Fecha** | 2026-11-16 |
| **Alcance auditado** | `R012-T01..R012-T04` (Fases 1–2, olas W1–W2) |
| **Veredicto** | ✅ Aprobado |
| **Auditor** | `reviewer` |

## Resumen

El requerimiento 012 **supersede** al 011 y completa la alineación de la entidad con el esquema
legacy: `datetime` ya no declara `nullable = false` (coherente con `V1`/`V2`), se agrega un guard
defensivo en `PersistenceMapper.toDomain()` y, lo más importante, `PortfolioCalculator` deja de
lanzar excepción cuando falta precio de mercado —devuelve una **posición degradada**
(`marketValue = 0.00`, returns `0`, mantiene `quantity` y `totalCost` ponderado real). El endpoint
`GET /api/v1/portfolio/{userId}` responde **200** con esa semántica, preservando observabilidad
del portfolio completo.

La suite `./gradlew compileJava test` y `./gradlew check` (incl. `jacocoTestCoverageVerification
≥ 80 %`) terminan **verdes**. ADR-011 creado y supersede a ADR-010; ADR-010 marcado con banner
sin reescribir su cuerpo; índice actualizado. El trabajo no commiteado del usuario
(`docs/postman/*`, `V901__seed_ars_marketdata.sql`) no se tocó.

## Checklist

| Categoría | Estado | Hallazgos |
| :--- | :---: | :--- |
| Cumplimiento del spec | ✅ | CA-01..CA-08 verificados por greps, diffs y tests. |
| Arquitectura hexagonal | ✅ | `domain/` sin Spring/JPA. Adapter implementa puerto sin cambiar la API. |
| Transacciones / concurrencia | ✅ | No se tocan `@Transactional`; `calculate` es lectura pura; `MarketPrice`/`Money` inmutables. |
| Tests y cobertura | ✅ | `PortfolioCalculatorTest` 9 tests; `PersistenceMapperTest` 2 tests; `MarketDataJpaAdapterIT` 6 tests; `check` ≥ 80 %. |
| Seguridad | ✅ | Sin credenciales, sin cambios a Flyway, contrato REST intacto salvo semántica documentada. |
| ADR y docs | ✅ | ADR-011 supersede a 010; banner en 010 sin reescribir; índice actualizado. |

## Hallazgos bloqueantes

Ninguno.

## Hallazgos menores

1. **`state.json`** — `completedTasks` no incluye `R012-T04` aunque los documentos existen y son correctos. Desajuste de seguimiento del coordinator.
2. **`MarketDataJpaAdapterIT.java:194,235`** — `@DisplayName` reutilizan prefijos `CA-03:`/`CA-04:` de R007; no afecta ejecución. Conviene prefijar con `R011`/`R012` en futuro refactor.
3. **Riesgo residual** — `unrealizedPnl()` de posición degradada = `−totalCost` (no expuesto en REST). Guard del mapper solo se dispara en `findById`/`findAll` (consultas de último precio filtran `datetime IS NOT NULL`).

## Próximos pasos

- [x] Persistir veredicto en `specs/012-marketdata-datetime-nullable-graceful-portfolio/review.md`.
- [x] Actualizar `specs/012-.../state.json` y `specs/state.json` a `done`.
- [ ] (Opcional) Prefijar `@DisplayName` en `MarketDataJpaAdapterIT` con su requerimiento de origen.