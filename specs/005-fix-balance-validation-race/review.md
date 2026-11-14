# Revisión — 005 Eliminar race condition en la validación de saldo (lock pesimista por usuario)

| Campo | Valor |
| :--- | :--- |
| **Fecha** | 2026-11-14 |
| **Alcance auditado** | `R005-T01..R005-T07` (olas W1, W2 y W3) |
| **Veredicto** | ✅ Aprobado |
| **Auditor** | `reviewer` |

## Resumen

Se auditó el requerimiento completo contra su spec, el baseline `000` y las invariantes de
`AGENTS.md`. El lock pesimista sobre `users.id` se adquiere dentro de la `@Transactional` de
`PlaceOrderUseCase.execute()`, después del chequeo de idempotencia y antes de cualquier lectura de
órdenes `FILLED`, y se mantiene hasta el commit. Los tests de concurrencia con Testcontainers
demuestran que no hay sobregiro ni sobreventa con requests simultáneos, y que usuarios distintos no
se serializan. `./gradlew test` y `./gradlew check` verdes. El DoD del spec §9 se cumple.

## Evidencia de verificación (coordinator)

| Comando | Resultado |
| :--- | :--- |
| `./gradlew compileJava` | BUILD SUCCESSFUL (W1 y W2) |
| `./gradlew test` | BUILD SUCCESSFUL — `ConcurrentOrderIT` 2/2, `ConcurrentSellAndCashOutIT` 2/2, `PlaceOrderUseCaseTest` 3/3, `PlaceOrderFlowIT` y resto de la suite sin fallos |
| `./gradlew check` | BUILD SUCCESSFUL — `jacocoTestCoverageVerification` OK |

## Checklist

| Categoría | Estado | Hallazgos |
| :--- | :--- | :--- |
| Cumplimiento del spec (CA-01..CA-06) | ✅ | CA-01/03 en `ConcurrentOrderIT`; CA-05/06 en `ConcurrentSellAndCashOutIT`; CA-02 en `PlaceOrderUseCaseTest` (`InOrder` lock → validate y lock → `findFilledByUserId`); CA-04 con `PlaceOrderFlowIT`. Conteos exactos y saldos finales verificados. |
| Arquitectura hexagonal | ✅ | `UserRepositoryPort` en `domain/port/out` (solo `java.util.Optional`); `UserJpaRepository` + `PostgresUserRepository` en infraestructura. Dominio sin Spring/JPA. |
| Transacciones / concurrencia | ✅ | `@Lock(PESSIMISTIC_WRITE)` + `@Query` sobre `users.id`, dentro de `@Transactional`, antes de toda lectura de estado y retenido hasta commit. Lock por fila: usuarios distintos no se bloquean. |
| Tests y cobertura | ✅ | 7 tests nuevos (3 unitarios + 4 ITs). Fixtures propios vía `JdbcTemplate`, sin depender del seed. `CountDownLatch`/`ExecutorService` con timeout y cierre en `finally`. Sin `Thread.sleep`. `check` verde (≥ 80 %). |
| Seguridad | ✅ | SQL parametrizado, sin credenciales ni datos sensibles. Sin cambios de contrato REST. |
| Alcance / documentación | ✅ | Solo rutas del requerimiento. ADR-007 con contexto, decisión, consecuencias y 3 alternativas; indexado en `docs/adr/README.md`. `spec`/`plan`/`tasks` consistentes. |

## Hallazgos bloqueantes

Ninguno.

## Hallazgos menores

Ninguno.

## Riesgos residuales (documentados, no bloquean el cierre)

1. **Lock wait sin timeout explícito** — PostgreSQL espera indefinidamente por defecto. La
   transacción es corta y el ADR-007 lo registra como costo aceptado.
2. **Usuario inexistente → no-op** — El `Optional` vacío se ignora; la FK de `orders.user_id`
   mantiene el comportamiento previo. Documentado en el spec §8 y el ADR-007.
3. **Race de idempotencia (misma key concurrente)** — Fuera de alcance (§2 del spec). El lock por
   usuario la serializa de facto para el mismo usuario.

## Próximos pasos

- [x] Marcar `R005-T01..T07` como completadas en `tasks.md`.
- [x] Estado del requerimiento a `done`.
- [x] Actualizar `specs/README.md` y `specs/state.json`.
- [ ] (Opcional, fuera de alcance) Evaluar `lock_timeout` a nivel de aplicación si el volumen de
  requests por usuario crece.
