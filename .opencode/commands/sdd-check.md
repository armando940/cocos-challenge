---
description: Audita un requerimiento contra su spec y emite veredicto
agent: reviewer
---

Audita el alcance indicado contra `specs/NNN-slug/spec.md`, las invariantes globales
(`AGENTS.md` + `specs/000-baseline-challenge/spec.md`), los ADR y tus criterios de revisión.

Alcance: $ARGUMENTS

Resolución del requerimiento:
- Si el primer token es numérico (ej. `003`), auditá `specs/003-*/`.
- Si no se indica número, usá `activeRequirement` de `specs/state.json`.
- Si el alcance dice `ola N` (o `WN`), auditá **solo** las tareas con esa `ola:` en `tasks.md` y sus
  rutas `toca:`. Una revisión por ola, nunca una por lane.
- Si no se indica nada más, audita la ola más reciente con tareas marcadas en ese `tasks.md`.
- No audites otros requerimientos: cada carpeta se revisa por separado.

Instrucciones:
- Ejecuta las verificaciones tú mismo (`./gradlew test`, `./gradlew jacocoTestReport`, greps de
  arquitectura) en vez de confiar en el reporte del implementer.
- Cita archivo:línea en cada hallazgo. Sin generalidades.
- Separa bloqueantes de menores y da el fix concreto de cada bloqueante.
- Emite veredicto: ✅ Aprobado / ⚠️ Cambios menores / ❌ Rehacer.
- No modifiques código: eres auditor. El fix lo hace el implementer con `/sdd-run`.

Cierra con un bloque listo para guardar en `specs/NNN-slug/review.md` y el estado sugerido:
`in-review`, `done` o `blocked`. El coordinator es quien escribe el archivo y actualiza
`state.json`.
