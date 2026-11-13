---
description: Genera el tasks.md (checklist ejecutable) de un requerimiento desde su plan, con olas paralelizables
agent: planner
---

Genera `specs/NNN-slug/tasks.md` a partir de `specs/NNN-slug/plan.md`.

Solicitud: $ARGUMENTS

Resolución del requerimiento:
- Si el primer token es numérico (ej. `003`), operá sobre `specs/003-*/`.
- Si no se indica número, usá `activeRequirement` de `specs/state.json`.
- Si la carpeta no existe, detente: se crea con `/sdd-new`.

Formato obligatorio por tarea:
`- [ ] RNNN-TXX — Descripción | Fase N | ola: WN | depende de: RNNN-TYY | toca: <rutas> | ~Xh | done: <criterio objetivo>`

Reglas:
- Una tarea debe caber en una sesión de trabajo y dejar el repo compilando.
- `toca:` lista las **únicas** rutas que esa tarea puede editar, con paths reales del repo
  (verificalos con `glob`/`grep`; no los inventes). El test va en la misma tarea que su fuente.
- Una tarea que toca un archivo de frontera (`build.gradle.kts`, `application*.yml`, `db/migration/*`,
  `ExceptionHandler`, mappers compartidos) se marca `toca: SHARED(<archivo>)` y va **sola** en su ola.
- **Olas**: agrupá tareas en `W1`, `W2`… solo si no hay dependencia entre ellas, sus `toca:` son
  disjuntas y ninguna crea un símbolo que otra consuma. Máximo 3 lanes por ola. Sin duda → series.
- Agregá/bloque "Olas de ejecución" en `plan.md` si no existe, con la tabla
  `Ola | Tareas en paralelo | Lanes | Serializa | Verificación de cierre`.
- Si el requerimiento no admite paralelismo, dejá una tarea por ola y explicá por qué en `Serializa`.
- Marca `[x]` solo lo que puedas verificar como hecho en el repo hoy.
- Agrupa por fase y añade al final un bloque "Buffer" (`ola: W9`, `toca: ?`).
- No dupliques el plan: sin tablas de estimación ni narrativa, solo el checklist accionable.
- Actualiza el estado a `ready` en `specs/NNN-slug/state.json` (con `"currentWave": "W1"`) y
  `specs/README.md`.

Restricciones del usuario: $ARGUMENTS
