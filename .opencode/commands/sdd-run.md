---
description: Ejecuta una ola (o fase) de un requerimiento, con implementers en paralelo por lane
agent: coordinator
---

Ejecuta las tareas indicadas de `specs/NNN-slug/tasks.md` siguiendo `specs/NNN-slug/spec.md`, por
**olas** cuando haya tareas paralelizables.

Alcance solicitado: $ARGUMENTS

Resolución del requerimiento:
- Si el primer token es numérico (ej. `003`), operá sobre `specs/003-*/`.
- Si no se indica número, usá `activeRequirement` de `specs/state.json`.
- Si la carpeta no existe o no hay tareas, detente y reportalo.
- Si no se indica ola, fase ni tarea, ejecutá la **primera ola con tareas pendientes** de ese
  requerimiento.

Selectores aceptados:

| Selector | Efecto |
| :-- | :-- |
| `003 ola 2` | Ejecuta la ola `W2` completa |
| `003 fase 1` | Ejecuta todas las olas de la fase 1, en orden |
| `003 R003-T10..R003-T18` | Ejecuta ese rango; si el rango cubre una ola entera, la paraleliza |
| `003 serial` | Fuerza ejecución tarea por tarea (sin lanes), para debug o cuando el repo está sucio |
| `003 "arreglar hallazgos del review"` | Ejecución correctiva en un solo lane |

## Protocolo

1. Leé `tasks.md` (líneas con `ola:` y `toca:`) y la tabla "Olas de ejecución" de `plan.md`.
2. **Auditoría previa**: `git status --porcelain`. Si hay cambios sin commitear fuera del alcance del
   requerimiento, detenete y pedí confirmación antes de lanzar lanes.
3. **Recalculá conflictos**: si dos tareas de la ola comparten archivo en `toca:`, o una crea un
   símbolo que la otra usa, mové la segunda a la ola siguiente y anotalo en
   `state.json → pendingDecisions`. Default seguro: sin `toca:` legible o `SHARED(...)` → serial.
4. **Máximo 3 lanes** por ola.
5. Lanzá un `subagent(agent="implementer")` por lane, todos en el mismo turno para que corran en
   paralelo (con `background=true` si tu versión lo soporta y son más de dos). Cada prompt incluye:
   requerimiento, SU tarea, SU ola, whitelist de rutas, criterio de done y las prohibiciones del modo
   paralelo.
6. **Prohibiciones en cada prompt**: no correr `./gradlew`; no editar `tasks.md`, `state.json` ni
   `README.md`; no tocar rutas fuera de la whitelist; no crear archivos de frontera
   (`build.gradle.kts`, `application*.yml`, `db/migration/*`, `ExceptionHandler`).
7. **Cierre de la ola**:
   - `git diff --name-only` → si un archivo fue tocado por dos lanes, no marques nada y reasigná.
   - Una sola verificación: `./gradlew compileJava test` (y `check` si la ola toca cobertura o
     infraestructura).
   - Recién entonces marcá `[x]` en `tasks.md` y actualizá `state.json` (`currentWave`,
     `completedTasks`, `blockedTasks`, `updated`).
8. **Reviewer por ola**: un solo `/sdd-check NNN ola N` sobre la ola completa, no uno por lane.
9. Reportá: ola, lanes, tareas, archivos, tests, decisiones técnicas, bloqueadores y próxima ola.

## Reglas

- Un lane = una tarea. No mezcles dos tareas de la ola en el mismo subagente.
- Un lane bloqueado no frena a los demás: esperá a que terminen, revertí sus cambios si rompen la
  compilación (`git checkout -- <sus rutas>`) y mové la tarea a `blockedTasks`.
- Si el requerimiento no trae `ola:` en `tasks.md`, ejecutá en serie como antes y sugerí correr
  `/sdd-tasks` para agregarlas.
- No toques tareas de otro requerimiento ni de una carpeta `done`/`cancelled`.
- Si el spec es ambiguo, detente y reportalo en vez de asumir. Si el cambio altera una decisión de
  arquitectura, actualizá el ADR global en `docs/adr/`.
