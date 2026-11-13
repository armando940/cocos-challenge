---
description: Ejecuta tareas de un requerimiento (una fase o un rango de RNNN-TXX)
agent: implementer
---

Ejecuta las tareas indicadas de `specs/NNN-slug/tasks.md` siguiendo `specs/NNN-slug/spec.md`.

Alcance solicitado: $ARGUMENTS

Resolución del requerimiento:
- Si el primer token es numérico (ej. `003`), operá sobre `specs/003-*/`.
- Si no se indica número, usá `activeRequirement` de `specs/state.json`.
- Si la carpeta no existe o no hay tareas, detente y reportalo.
- Si no se indica fase ni tarea, ejecuta la primera fase con tareas pendientes **de ese
  requerimiento**.

Protocolo por tarea:
1. Lee la tarea (`RNNN-TXX`), el spec del requerimiento, el baseline y el ADR que aplique.
2. Implementa con tests (unitarios y/o Testcontainers según corresponda).
3. Verifica con los comandos del proyecto: `./gradlew compileJava`, `./gradlew test`, y si tocaste
   infraestructura `docker compose up -d`.
4. Marca `[x]` en `specs/NNN-slug/tasks.md` solo si la verificación pasó.
5. Actualiza `specs/NNN-slug/state.json` (tarea actual, completadas, bloqueos, fecha).
6. Reporta: requerimiento, tarea, archivos, tests, decisiones técnicas, bloqueadores y siguiente
   tarea.

No toques tareas de otro requerimiento ni de una carpeta `done`/`cancelled`. Si algo del spec es
ambiguo, detente y repórtalo en vez de asumir. Si un cambio altera una decisión de arquitectura,
actualiza el ADR global en `docs/adr/`.
