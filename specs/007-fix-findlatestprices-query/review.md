# Review — 007 Fix findLatestPrices sin LIMIT

## Veredicto
✅ Aprobado

## Hallazgos
### Cumplimiento de spec
| CA | Estado | Evidencia |
|----|--------|-----------|
| CA-01 | ✅ | Test `findLatestPrices_returnsOnlyLatestPerInstrument` valida que la query devuelve exactamente 2 filas (1 por instrumento) con 3 filas históricas cada uno |
| CA-02 | ✅ | Mismo test verifica que `close` y `previousClose` corresponden a la fila más reciente (`datetime` máximo): A=150.00/148.00, B=250.00/248.00 |
| CA-03 | ✅ | Test `findLatestPrices_emptySet_returnsEmptyMap` valida `Set.of()` → `Map` vacío sin error |
| CA-04 | ✅ | Test `findLatestPrices_instrumentWithoutData_notInResult` valida que instrumento B sin marketdata no aparece en el resultado (no lanza excepción) |

### Invariantes globales
| Invariante | Estado | Comentario |
|------------|--------|------------|
| Hexagonal | ✅ | `domain/` sin imports Spring/JPA; puerto `MarketDataProviderPort` en `domain/port/out`; adapter en `infrastructure/adapter/out/marketdata` |
| BigDecimal | ✅ | `MarketPrice` (record) usa `BigDecimal` para `close`/`previousClose`; `MarketDataEntity` mapea con `precision=18, scale=4` |
| Transacciones | ✅ | Método de solo lectura; no requiere `@Transactional` (el use case que lo llama maneja la transacción si aplica) |
| Locks | ✅ | N/A — operación de lectura; no valida saldo/tenencia |
| Idempotencia | ✅ | N/A — no es operación de escritura |
| Secretos | ✅ | Cero credenciales hardcodeadas; configuración via env vars / `application.yml` |
| Errores RFC 7807 | ✅ | `GlobalExceptionHandler` usa `ProblemDetail` builder para todas las excepciones de dominio |

### Calidad y arquitectura
| Aspecto | Estado | Comentario |
|---------|--------|------------|
| Query SQL | ✅ | `SELECT DISTINCT ON (instrument_id) * FROM marketdata WHERE instrument_id IN :instrumentIds ORDER BY instrument_id, datetime DESC` — correcta para PostgreSQL, usa índice `idx_marketdata_instrument_datetime`, devuelve 1 fila por instrumento |
| Tests | ✅ | 3 tests de integración (@Testcontainers con Postgres real) cubren CA-01..CA-04; dataset multi-instrumento/multi-fila bien diseñado |
| Sin regresiones | ✅ | `./gradlew test` verde (suite completa); `./gradlew check` verde (cobertura ≥80% verificada en domain services: PortfolioCalculator, BalanceValidator, OrderMatcher) |

## Decisión
Continuar — el requerimiento está completo y cumple todos los criterios de aceptación e invariantes. Cerrar el requerimiento.