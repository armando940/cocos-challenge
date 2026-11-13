---
description: Genera el tasks.md (checklist ejecutable) de un requerimiento desde su plan
agent: planner
---

Genera `specs/NNN-slug/tasks.md` a partir de `specs/NNN-slug/plan.md`.

Solicitud: $ARGUMENTS

Resolución del requerimiento:
- Si el primer token es numérico (ej. `003`), operá sobre `specs/003-*/`.
- Si no se indica número, usá `activeRequirement` de `specs/state.json`.
- Si la carpeta no existe, detente: se crea con `/sdd-new`.

Formato obligatorio por tarea:
`- [ ] RNNN-TXX — Descripción | Fase N | depende de: RNNN-TYY | ~Xh | done: <criterio objetivo>`

Reglas:
- Una tarea debe caber en una sesión de trabajo y dejar el repo compilando.
- Marca `[x]` solo lo que puedas verificar como hecho en el repo hoy.
- Agrupa por fase y añade al final un bloque "Buffer" para imprevistos.
- No dupliques el plan: sin tablas de estimación ni narrativa, solo el checklist accionable.
- Actualiza el estado a `ready` en `specs/NNN-slug/state.json` y `specs/README.md`.

Restricciones del usuario: $ARGUMENTS
