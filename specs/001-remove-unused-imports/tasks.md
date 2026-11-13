# Tasks Checklist — 001 Remover imports no utilizados del proyecto

> Formato: `- [ ] R001-TXX — Descripción | Fase N | depende de: R001-TYY | ~Xh | done: <criterio>`
> Derivado de [`plan.md`](plan.md) el 2026-11-13. `[x]` solo si el repo lo demuestra hoy.

## Fase 1 — Análisis y detección

- [x] R001-T01 — Ejecutar análisis de imports unused en todo el proyecto (IntelliJ "Inspect Code" o script) | Fase 1 | — | ~0.5h | done: `./gradlew compileJava` OK + lista de archivos afectados exportada

## Fase 2 — Limpieza de imports en src/main

- [x] R001-T02 — Limpiar imports en módulo `domain` (model, port, service) | Fase 2 | depende de: R001-T01 | ~0.5h | done: `./gradlew compileJava` OK; spot-check 3 archivos domain
- [x] R001-T03 — Limpiar imports en módulo `application` (usecases) | Fase 2 | depende de: R001-T02 | ~0.25h | done: `./gradlew compileJava` OK
- [x] R001-T04 — Limpiar imports en módulo `infrastructure` (config, adapter/in, adapter/out, actuator) | Fase 2 | depende de: R001-T03 | ~0.5h | done: `./gradlew compileJava` OK
- [x] R001-T05 — Limpiar imports en módulo `presentation/mcp` | Fase 2 | depende de: R001-T04 | ~0.25h | done: SALTADO (decisión usuario: no tocar código comentado)

## Fase 3 — Limpieza de imports en src/test

- [x] R001-T06 — Limpiar imports en tests unitarios (`unit/domain`, `unit/infrastructure`) | Fase 3 | depende de: R001-T05 | ~0.5h | done: `./gradlew test --tests "*unit*"` OK
- [x] R001-T07 — Limpiar imports en tests de integración (`integration/`) | Fase 3 | depende de: R001-T06 | ~0.5h | done: `./gradlew test --tests "*IT*"` OK

## Fase 4 — Verificación final y commit

- [x] R001-T08 — Verificación completa: `./gradlew compileJava`, `./gradlew test`, `./gradlew check` | Fase 4 | depende de: R001-T07 | ~0.25h | done: Todos los comandos OK (cobertura ≥ 80% en domain services)
- [x] R001-T09 — Validar diff: solo líneas `import` modificadas en `*.java` | Fase 4 | depende de: R001-T08 | ~0.15h | done: `git diff -- "*.java"` muestra solo cambios en imports
- [x] R001-T10 — Actualizar `state.json` a `done` y `review.md` para reviewer | Fase 4 | depende de: R001-T09 | ~0.1h | done: `state.json` status=done; `review.md` existe

## Buffer

- [ ] R001-T90 — Revertir y re-hacer limpieza en archivo problemático si compila pero falla test/integración | Buffer | — | ~0.5h | done: Archivo identificado, fix aplicado, `./gradlew test` OK