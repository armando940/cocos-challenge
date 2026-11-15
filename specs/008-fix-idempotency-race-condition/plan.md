# Plan de Ejecución — 008 Arreglar la race condition de idempotencia

## Resumen
- **Fases**: 5
- **Horas estimadas**: ~8.5h
- **Camino crítico**: R008-T01 → R008-T02 → R008-T04 → R008-T05
- **Riesgo mayor**: Test concurrente flaky (timing) + query nativa `FOR UPDATE SKIP LOCKED` en dialecto JPA

---

## Estado actual vs plan
| Fase | Objetivo | Estado | Evidencia |
|------|----------|--------|-----------|
| 0 | Dominio: nuevo método en `IdempotencyStorePort` | ⏳ Pendiente | — |
| 1 | Infraestructura: implementación con `SELECT FOR UPDATE SKIP LOCKED` + `INSERT ON CONFLICT` | ⏳ Pendiente | — |
| 2 | Aplicación: reordenar `PlaceOrderUseCase` (lock usuario → idempotencia con lock → procesar) | ⏳ Pendiente | — |
| 3 | Tests: IT concurrente (CA-01, CA-02) + actualizar unit tests | ⏳ Pendiente | — |
| 4 | Documentación: ADR-003 | ⏳ Pendiente | — |

---

## Fase 0 — Dominio: Puerto `IdempotencyStorePort` (~0.5h)
**Objetivo**: Definir el contrato atómico `findByKeyWithLock` en el puerto de dominio.
**Entregable**: `domain/port/out/IdempotencyStorePort.java` con nuevo método.
**DoD**: `./gradlew compileJava` compila sin errores; interfaz usada por `PlaceOrderUseCase`.

| ID | Tarea | Depende de | Estimación | Riesgo | Done |
|----|-------|------------|------------|--------|------|
| R008-T01 | Añadir `Optional<IdempotencyKey> findByKeyWithLock(String key)` a `IdempotencyStorePort` con javadoc que explique semántica: retorna `Optional.of(existing)` si existe; `Optional.empty()` si no existía y se reservó slot (caller debe `save()`); lanza excepción si concurrencia impide reservar | — | 0.5h | Bajo | `grep -n "findByKeyWithLock" src/main/java/ar/com/cocos/challenge/domain/port/out/IdempotencyStorePort.java` |

---

## Fase 1 — Infraestructura: Adaptador Postgres + JPA Repository (~2h)
**Objetivo**: Implementar el lock pesimista a nivel de fila en `idempotency_keys` usando query nativa PostgreSQL.
**Entregable**: `PostgresIdempotencyStore.findByKeyWithLock()` + método en `IdempotencyKeyJpaRepository`.
**DoD**: `./gradlew compileJava test` verde (tests unitarios existentes pasan); query nativa ejecutable en Postgres.

| ID | Tarea | Depende de | Estimación | Riesgo | Done |
|----|-------|------------|------------|--------|------|
| R008-T02 | Añadir método de query nativa en `IdempotencyKeyJpaRepository`: `Optional<IdempotencyKeyEntity> findByKeyForUpdateSkipLocked(String key)` con `@Query(nativeQuery = true, value = "SELECT * FROM idempotency_keys WHERE key = :key FOR UPDATE SKIP LOCKED")` | R008-T01 | 0.75h | Medio (dialecto JPA / native query) | `grep -n "findByKeyForUpdateSkipLocked" src/main/java/ar/com/cocos/challenge/infrastructure/adapter/out/persistence/IdempotencyKeyJpaRepository.java` |
| R008-T03 | Implementar `findByKeyWithLock` en `PostgresIdempotencyStore`:<br>1. Llamar `jpaRepository.findByKeyForUpdateSkipLocked(key)`<br>2. Si presente → `Optional.of(toDomain(entity))`<br>3. Si vacío → `INSERT ... ON CONFLICT (key) DO NOTHING RETURNING *` (query nativa) para reservar slot; si retorna fila → `Optional.empty()` (slot reservado); si no retorna → `Optional.of(existing)` (otro hilo ganó la carrera)<br>4. Mantener `@Transactional` en el use case para que el lock dure hasta commit | R008-T01 | 1.25h | Alto (lógica de reserva atómica + manejo de carrera) | `grep -n "findByKeyWithLock" src/main/java/ar/com/cocos/challenge/infrastructure/adapter/out/persistence/PostgresIdempotencyStore.java` |

> **Nota de implementación**: El `INSERT ... ON CONFLICT DO NOTHING RETURNING *` es PostgreSQL 9.5+. Si el `RETURNING` no funciona vía JPA, fallback: `save()` dentro de try-catch `DataIntegrityViolationException` y re-leer con `findByKeyForUpdateSkipLocked`.

---

## Fase 2 — Aplicación: Reordenar `PlaceOrderUseCase` (~2h)
**Objetivo**: Mover la comprobación de idempotencia **después** del lock de usuario y usar el nuevo método con lock, garantizando exactly-once global por key.
**Entregable**: `PlaceOrderUseCase.execute()` refactorizado.
**DoD**: `./gradlew test` verde; orden de llamadas: `lockById` → `findByKeyWithLock` → procesar → `save`.

| ID | Tarea | Depende de | Estimación | Riesgo | Done |
|----|-------|------------|------------|--------|------|
| R008-T04 | Refactorizar `PlaceOrderUseCase.execute()`:<br>1. **Eliminar** el `findByKey` previo al lock de usuario (línea 68 actual)<br>2. **Mantener** `userRepositoryPort.lockById(command.userId())` (línea 78) — sigue siendo necesario para validar saldo/tenencia<br>3. **Llamar** `idempotencyStore.findByKeyWithLock(command.idempotencyKey())` **después** del lock de usuario<br>4. Si `Optional.of(existing)` → validar hash y devolver cacheado (igual que antes)<br>5. Si `Optional.empty()` → procesar orden completa, luego `saveIdempotency()` (el slot ya está reservado)<br>6. Ajustar `saveIdempotency` para no fallar si el slot ya tiene respuesta (defensa en profundidad)<br>7. Loggear `DEBUG` cuando se detecta key existente vs slot reservado | R008-T02, R008-T03 | 2h | Medio (cambio de orden de operaciones en transacción) | `grep -n "findByKeyWithLock" src/main/java/ar/com/cocos/challenge/application/usecase/PlaceOrderUseCase.java && grep -n "lockById" src/main/java/ar/com/cocos/challenge/application/usecase/PlaceOrderUseCase.java` |

---

## Fase 3 — Tests: Integración concurrente + actualización unitarios (~3h)
**Objetivo**: Verificar CA-01 y CA-02 con test concurrente real; actualizar mocks en tests unitarios.
**Entregable**: `IdempotencyRaceConditionIT.java` (nuevo) + `PlaceOrderUseCaseTest.java` actualizado.
**DoD**: `./gradlew test` verde incluyendo el nuevo IT; cobertura ≥ 80% en `PostgresIdempotencyStore` y `PlaceOrderUseCase`.

| ID | Tarea | Depende de | Estimación | Riesgo | Done |
|----|-------|------------|------------|--------|------|
| R008-T05 | Crear `src/test/integration/IdempotencyRaceConditionIT.java` (Testcontainers):<br>- `@SpringBootTest` + `@Autowired PlaceOrderPort`<br>- Dos `Command` con **distinto `userId`** pero **misma `idempotencyKey`** y **mismo request** (CA-01)<br>- `ExecutorService` (2 hilos) + `CountDownLatch` para disparo simultáneo<br>- Aserciones: exactamente un `201`, el otro `409` con `ProblemDetail` type `https://cocos.challenge/errors/idempotency-conflict`<br>- Verificar en BD que solo existe **una** orden y una `idempotency_key`<br>- Segundo caso (CA-02): misma key, **request distinto** → uno `201`, otro `409` por hash mismatch<br>- Configurar aislamiento de transacción `REPEATABLE_READ` o `SERIALIZABLE` si el test es flaky | R008-T04 | 2h | **Alto** (flakiness por timing, aislamiento TX, Testcontainers overhead) | `./gradlew test --tests "*IdempotencyRaceConditionIT*"` pasa consistentemente (3 ejecuciones) |
| R008-T06 | Actualizar `PlaceOrderUseCaseTest.java`:<br>- Mockear `idempotencyStore.findByKeyWithLock()` en lugar de `findByKey()`<br>- Verificar orden de llamadas: `lockById` → `findByKeyWithLock` → resto<br>- Ajustar test `replayIdempotenteNoTomaLock` para que use el nuevo método y confirme que **no** se toma lock de usuario si key ya existe (comportamiento actual mantenido) | R008-T04 | 1h | Bajo | `./gradlew test --tests "*PlaceOrderUseCaseTest*"` verde |

---

## Fase 4 — Documentación: ADR-003 (~1h)
**Objetivo**: Documentar la decisión técnica de `SELECT FOR UPDATE SKIP LOCKED` vs `ON CONFLICT` y por qué se rechazó la Opción B.
**Entregable**: `docs/adr/003-idempotency-race-condition.md`.
**DoD**: Archivo creado y vinculado desde `docs/adr/README.md`.

| ID | Tarea | Depende de | Estimación | Riesgo | Done |
|----|-------|------------|------------|--------|------|
| R008-T07 | Crear `docs/adr/003-idempotency-race-condition.md` con:<br>- Contexto: race condition cross-user en `idempotency_key`<br>- Decisión: Opción A (`SELECT FOR UPDATE SKIP LOCKED` + `INSERT ON CONFLICT`)<br>- Alternativas: Opción B (catch exception) y por qué se rechaza (no exactly-once)<br>- Consecuencias: + exactly-once global, + latency ~1ms, - query nativa Postgres-specific<br>- Referencia a CA-01..CA-05 y test IT<br>- Actualizar `docs/adr/README.md` con entrada 003 | R008-T04 | 1h | Bajo | `cat docs/adr/003-idempotency-race-condition.md && grep "003" docs/adr/README.md` |

---

## Olas de ejecución
| Ola | Tareas en paralelo | Lanes | Serializa | Verificación de cierre |
|-----|--------------------|-------|-----------|------------------------|
| W1 | R008-T01 | 1 | — | `./gradlew compileJava` |
| W2 | R008-T02, R008-T03 | 2 | — | `./gradlew compileJava test` (unitarios) |
| W3 | R008-T04 | 1 | `PlaceOrderUseCase.java` (frontera lógica de negocio) | `./gradlew compileJava test` |
| W4 | R008-T05, R008-T06, R008-T07 | 3 | — | `./gradlew test` (suite completa) + `./gradlew check` (cobertura) |

> **Regla de frontera**: `PlaceOrderUseCase.java` serializa W3 porque es el orquestador central y toca lógica de negocio transaccional. R008-T05 (IT) y R008-T06 (unit) tocan archivos de test disjuntos; R008-T07 toca `docs/adr/` — todos disjuntos entre sí y con el código principal.

---

## Camino crítico
`R008-T01 (0.5h) → R008-T02 (0.75h) → R008-T04 (2h) → R008-T05 (2h)` = **5.25h mínimo secuencial**

R008-T03 corre en paralelo con R008-T02 (W2). R008-T06 y R008-T07 corren en paralelo con R008-T05 (W4).

---

## Riesgos y mitigación
| Riesgo | Probabilidad | Impacto | Mitigación |
|--------|--------------|---------|------------|
| **Test IT flaky** (timing, aislamiento TX, Testcontainers lentos) | **Alta** | **Alto** (bloquea CI) | 1. Ejecutar IT 3 veces en local antes de push<br>2. Usar `@Transactional(propagation = NOT_SUPPORTED)` en el test para que cada hilo tenga su propia TX<br>3. Aislamiento `REPEATABLE_READ` en `@Testcontainers` config<br>4. Timeout generoso (30s) y reintento con `Awaitility` |
| **Query nativa `FOR UPDATE SKIP LOCKED` no portable / falla en JPA** | Media | Alto | 1. Probar query directo en `psql` antes de integrar<br>2. Fallback: `@Query(nativeQuery = true)` sin derivación JPQL<br>3. Si falla, `LOCK TABLE idempotency_keys IN EXCLUSIVE MODE` (más pesado pero portable) |
| **Deadlock entre lock de usuario + lock de idempotencia** | Baja | Alto | Orden fijo: **siempre** `lockById(userId)` **antes** de `findByKeyWithLock(key)`. Documentado en ADR y código. |
| **Inserción concurrente gana la carrera y `INSERT ON CONFLICT` no retorna fila** | Media | Medio | Lógica defensiva en `PostgresIdempotencyStore`: si `INSERT ... RETURNING` no retorna → re-leer con `findByKeyForUpdateSkipLocked` y retornar `Optional.of(existing)`. |
| **Cobertura < 80% en `PostgresIdempotencyStore`** | Baja | Medio | El IT ejerce el path concurrente; agregar test unitario específico para `findByKeyWithLock` con mock de `JpaRepository` si hace falta. |

---

## Criterios de Done globales (Definition of Done del requerimiento)
- [ ] CA-01..CA-05 verificados (`./gradlew test` verde incluyendo `IdempotencyRaceConditionIT`)
- [ ] `./gradlew check` verde (cobertura ≥ 80% en `PostgresIdempotencyStore` y `PlaceOrderUseCase`)
- [ ] Reviewer emitió ✅ en `specs/008-fix-idempotency-race-condition/review.md`
- [ ] ADR-003 creado y referenciado en `docs/adr/README.md`
- [ ] Sin regresiones en tests existentes (`PlaceOrderUseCaseTest`, tests de idempotencia previos)