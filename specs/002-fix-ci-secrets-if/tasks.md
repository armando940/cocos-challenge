# Tasks Checklist — 002 Arreglar workflow CI (secrets en if)

> Formato: `- [ ] RNNN-TXX — Descripción | Fase N | ola: WN | depende de: RNNN-TYY | toca: <rutas> | ~Xh | done: <criterio>`
> Derivado de [`plan.md`](plan.md) el 2026-11-13. `[x]` solo si el repo lo demuestra hoy.
> `toca:` son las ÚNICAS rutas que el implementer puede editar en esa tarea. Sin `toca:` = serial.
> `toca: SHARED(<archivo>)` = archivo de frontera: la tarea va sola en su ola.
> Nota: `.github/workflows/ci.yml` es archivo de frontera (infraestructura de CI): por eso R002-T01 va sola en W1.

## Fase 1 — Fix del workflow CI

- [x] R002-T01 — Remover la línea 86 (if con secrets) del step "Login to Docker Hub" en ci.yml, sin tocar nada más (el gate de job de la línea 76, `vars.ENABLE_DOCKER_PUBLISH == 'true'`, queda intacto) | Fase 1 | ola: W1 | — | toca: `.github/workflows/ci.yml` | ~0.5h | done: grep confirma cero referencias a secrets dentro de if: en ci.yml y git diff muestra solo esa línea eliminada ✅ (2026-11-13: 0+/1−, YAML parsea OK, compileJava verde)

## Buffer

- [ ] R002-T90 — Otras referencias inválidas que aparezcan durante la verificación del workflow | Buffer | ola: W9 | — | toca: ? | ~0.5h | done: criterio definido al momento de activarse
