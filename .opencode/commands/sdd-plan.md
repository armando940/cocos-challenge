---
description: Genera el plan.md de un requerimiento (fases, tareas, dependencias, riesgos)
agent: planner
---

Genera `specs/NNN-slug/plan.md` a partir del spec del requerimiento y del estado real del repo.

Solicitud: $ARGUMENTS

Resolución del requerimiento:
- Si el primer token es numérico (ej. `003`), operá sobre `specs/003-*/`.
- Si no se indica número, usá `activeRequirement` de `specs/state.json`.
- Si la carpeta no existe, detente: se crea con `/sdd-new`.

Requisitos:
1. Antes de escribir, inventaría lo ya implementado que afecte a **este** requerimiento
   (`src/main/java`, `src/test/java`, `docs/adr`, `build.gradle.kts`) y marca esas tareas como
   hechas.
2. Usa la plantilla `specs/_templates/plan.md`. IDs de tarea: `RNNN-T01`, `RNNN-T02`… (el prefijo
   es obligatorio para que no se mezclen con otros requerimientos).
3. Fases con objetivo, entregable y DoD medible. Cada tarea: ID, descripción, depende de,
   estimación, riesgo (Bajo/Medio/Alto) y criterio de done verificable con un comando.
4. Identifica el camino crítico y los riesgos con su mitigación.
5. No inventes alcance fuera de `specs/NNN-slug/spec.md`; si falta algo, anótalo como dependencia
   del spec.
6. Cierra con la tabla "estado actual vs plan".
7. Actualiza el estado a `planned` en `specs/NNN-slug/state.json` y `specs/README.md`.
8. No escribas `tasks.md` en este paso: eso es `/sdd-tasks`.

Notas del usuario: $ARGUMENTS
