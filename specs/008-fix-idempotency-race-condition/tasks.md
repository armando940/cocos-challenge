# Tasks Checklist — 008 Arreglar la race condition de idempotencia

> Formato: `- [ ] R008-TXX — Descripción | Fase N | ola: WN | depende de: R008-TYY | toca: <rutas> | ~Xh | done: <criterio>`

## Fase 0 — Dominio: Puerto IdempotencyStorePort
- [x] R008-T01 — Añadir método findByKeyWithLock a IdempotencyStorePort | Fase 0 | ola: W1 | — | toca: src/main/java/ar/com/cocos/challenge/domain/port/out/IdempotencyStorePort.java | ~0.5h | done: `grep -n "findByKeyWithLock" src/main/java/ar/com/cocos/challenge/domain/port/out/IdempotencyStorePort.java`

## Fase 1 — Infraestructura: Adaptador Postgres + JPA Repository
- [x] R008-T02 — Añadir método de query nativa findByKeyForUpdateSkipLocked en JpaRepository | Fase 1 | ola: W2 | depende de: R008-T01 | toca: src/main/java/ar/com/cocos/challenge/infrastructure/adapter/out/persistence/IdempotencyKeyJpaRepository.java | ~0.75h | done: `grep -n "findByKeyForUpdateSkipLocked" src/main/java/ar/com/cocos/challenge/infrastructure/adapter/out/persistence/IdempotencyKeyJpaRepository.java`
- [x] R008-T03 — Implementar findByKeyWithLock en PostgresIdempotencyStore con lógica de reserva atómica | Fase 1 | ola: W2 | depende de: R008-T01 | toca: src/main/java/ar/com/cocos/challenge/infrastructure/adapter/out/persistence/PostgresIdempotencyStore.java | ~1.25h | done: `grep -n "findByKeyWithLock" src/main/java/ar/com/cocos/challenge/infrastructure/adapter/out/persistence/PostgresIdempotencyStore.java`

## Fase 2 — Aplicación: Reordenar PlaceOrderUseCase
- [x] R008-T04 — Refactorizar PlaceOrderUseCase.execute para usar findByKeyWithLock tras el lock de usuario | Fase 2 | ola: W3 | depende de: R008-T02, R008-T03 | toca: SHARED(src/main/java/ar/com/cocos/challenge/application/usecase/PlaceOrderUseCase.java) | ~2h | done: `grep -n "findByKeyWithLock" src/main/java/ar/com/cocos/challenge/application/usecase/PlaceOrderUseCase.java && grep -n "lockById" src/main/java/ar/com/cocos/challenge/application/usecase/PlaceOrderUseCase.java`

## Fase 3 — Tests: Integración concurrente + actualización unitarios
- [x] R008-T05 — Crear test de integración concurrente IdempotencyRaceConditionIT | Fase 3 | ola: W4 | depende de: R008-T04 | toca: src/test/java/ar/com/cocos/challenge/integration/IdempotencyRaceConditionIT.java | ~2h | done: `./gradlew test --tests "*IdempotencyRaceConditionIT*"`
- [x] R008-T06 — Actualizar PlaceOrderUseCaseTest con mocks del nuevo método y verificación de orden | Fase 3 | ola: W4 | depende de: R008-T04 | toca: src/test/java/ar/com/cocos/challenge/unit/application/PlaceOrderUseCaseTest.java | ~1h | done: `./gradlew test --tests "*PlaceOrderUseCaseTest*"`

## Fase 4 — Documentación: ADR-009
- [x] R008-T07 — Crear ADR-009 documentando la solución a la race condition | Fase 4 | ola: W4 | depende de: R008-T04 | toca: docs/adr/009-idempotency-race-condition.md, SHARED(docs/adr/README.md) | ~1h | done: `cat docs/adr/009-idempotency-race-condition.md && grep "009" docs/adr/README.md`

## Buffer
- [ ] R008-T90 — Ajustar aislamiento de transacciones si el test IT resulta inestable | Buffer | ola: W9 | — | toca: src/test/java/ar/com/cocos/challenge/integration/IdempotencyRaceConditionIT.java | ~1h | done: `./gradlew test`