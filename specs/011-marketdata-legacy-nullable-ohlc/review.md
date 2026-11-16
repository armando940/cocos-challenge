# Revisión — 011 Alinear nullability de MarketDataEntity con el esquema legacy

| Campo | Valor |
| :--- | :--- |
| **Fecha** | 2026-11-16 |
| **Alcance auditado** | `R011-T01..R011-T04` (Fases 1–3, olas W1–W3) |
| **Veredicto** | ✅ Aprobado |
| **Auditor** | `reviewer` |

## Resumen

El requerimiento 011 corrige la desalineación entre `MarketDataEntity` y el esquema real de
producción (`V1__baseline_prod_schema.sql` / `V2__align_app_schema.sql`). Se removió
`@Column(nullable = false)` de `high`, `low` y `open`, permitiendo hidratar los registros del dump
legacy sin operación en la rueda. Además, se agregaron los filtros `datetime IS NOT NULL` en la
query derivada de un solo resultado y en la native `DISTINCT ON` de `findLatestPrices`, para
neutralizar el `NULLS FIRST` de PostgreSQL sobre `datetime` nullable y evitar fallos al construir
`MarketPrice`.

La suite `./gradlew compileJava test` y `./gradlew check` (incluida `jacocoTestCoverageVerification`)
terminan verdes. La decisión y su discrepancia consciente quedaron documentadas en
`docs/adr/010-marketdata-nullability-legacy.md` e indexadas en `docs/adr/README.md`.

## Checklist

| Categoría | Estado | Hallazgos |
| :--- | :---: | :--- |
| Cumplimiento del spec | ✅ | CA-01..CA-07 verificados. `high/low/open` sin `nullable = false`; `instrumentId`, `close`, `previousClose`, `datetime` y `createdAt` lo conservan. |
| Arquitectura hexagonal | ✅ | `domain/` no incorpora imports de Spring/JPA. `MarketDataJpaAdapter` sigue implementando `MarketDataProviderPort` sin cambiar la API de dominio. |
| Transacciones / concurrencia | ✅ | Sólo consultas de lectura; no cambia el comportamiento transaccional. `DISTINCT ON` preserva 1 fila por instrumento. |
| Tests y cobertura | ✅ | `MarketDataJpaAdapterIT`: 5 tests, 0 fallos. Tests aislados con tickers propios (`TESTE`, `TESTF`) y fila legacy `datetime = NULL` insertada por JDBC. `check` ≥ 80 %. |
| Seguridad | ✅ | Sin credenciales, sin cambios a Flyway `V1`/`V2`, sin cambios a contratos REST/DTOs. Trabajo no commiteado del usuario intacto. |
| Alcance y olas | ✅ | Sólo se editaron rutas `toca:` declaradas. `docs/postman/*` y `V901__seed_ars_marketdata.sql` no se tocaron. |

## Hallazgos bloqueantes

Ninguno.

## Hallazgos menores

1. **`MarketDataEntity.java:58`** — `datetime` es nullable en la base pero se mantiene
   `nullable = false` en la entidad. Es una discrepancia consciente documentada en el ADR-010: el
   dominio `MarketPrice` exige timestamp y las consultas filtran `datetime IS NOT NULL`, por lo que
   el riesgo de runtime queda neutralizado.
2. **`MarketDataJpaAdapterIT.java:194,235`** — Los `@DisplayName` nuevos reutilizan los prefijos
   `CA-03:`/`CA-04:`, que ya usaban los tests de R007 en la misma clase. No afecta la ejecución ni
   la cobertura; en un futuro refactor conviene prefijar con `R011` para legibilidad de los reportes.

## Próximos pasos

- [x] Persistir este veredicto en `specs/011-marketdata-legacy-nullable-ohlc/review.md`.
- [x] Actualizar `specs/011-marketdata-legacy-nullable-ohlc/state.json` y `specs/state.json` a `done`.
- [ ] (Opcional, otro requerimiento) Prefijar los `@DisplayName` de los tests con su requerimiento
      de origen si se reordena `MarketDataJpaAdapterIT`.
