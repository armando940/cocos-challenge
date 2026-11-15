# Tasks Checklist — 009 Eliminar query extra en PostgresOrderRepository.save()

> Formato: `- [ ] RNNN-TXX — Descripción | Fase N | ola: WN | depende de: RNNN-TYY | toca: <rutas> | ~Xh | done: <criterio>`
> Derivado de [`plan.md`](plan.md) el 2026-11-15. `[x]` solo si el repo lo demuestra hoy.
> `toca:` son las ÚNICAS rutas que el implementer puede editar en esa tarea. Sin `toca:` = serial.
> `toca: SHARED(<archivo>)` = archivo de frontera: la tarea va sola en su ola.

## Fase 1 — Implementación del refactor

- [x] R009-T01 — Refactor save(): usar siempre PersistenceMapper.toEntity(order) + jpaRepository.save(entity) | Fase 1 | ola: W1 | — | toca: src/main/java/ar/com/cocos/challenge/infrastructure/adapter/out/persistence/PostgresOrderRepository.java | ~1h | done: `./gradlew compileJava`

## Fase 2 — Verificación

- [x] R009-T02 — Ejecutar suite completa (./gradlew test) y verificar cobertura (./gradlew check) | Fase 2 | ola: W2 | depende de: R009-T01 | toca: (ninguna — solo ejecución) | ~0.5h | done: `./gradlew check`

## Buffer

- [ ] R009-T90 — Imprevisto: ajuste en tests si fallan por cambio de comportamiento | Buffer | ola: W9 | — | toca: ? | ~1h | done: `./gradlew test`