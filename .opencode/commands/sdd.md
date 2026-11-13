---
description: Flujo SDD completo para un requerimiento (o crea uno nuevo)
agent: coordinator
---

Ejecuta el flujo SDD completo sobre un requerimiento: spec → clarify → plan → tasks →
(run → check por fase) → cierre.

Alcance o instrucciones del usuario: $ARGUMENTS

Paso 0 — Resolver el requerimiento:
- Si el usuario describe un requerimiento que no existe en `specs/README.md`, corré primero el
  protocolo de `/sdd-new` (asignar NNN, crear `specs/NNN-slug/` y el spec draft) y continuá con esa
  carpeta.
- Si hay un número (ej. `003`), usá `specs/003-*/`.
- Si no hay número ni descripción nueva, usá `activeRequirement` de `specs/state.json`.

Protocolo:
1. Lee `specs/README.md`, `specs/state.json`, `specs/NNN-slug/{spec,plan,tasks,state}.json|md`,
   `AGENTS.md`, el baseline y `docs/adr/*.md`. Si faltan artefactos, créalos en orden antes de
   ejecutar nada.
2. Clarifica con el usuario solo lo que bloquee; anota el resto como supuesto.
3. Por ola: lanza un `implementer` por lane en paralelo (rutas `toca:` disjuntas, máx. 3 lanes) y
   después un solo `reviewer` sobre la ola completa. Sin olas, ejecutá en serie.
   No avances de ola sin veredicto ✅ o aceptación explícita del usuario.
4. Si el reviewer dice ❌, vuelve al implementer con los hallazgos exactos; no reescribas la fase.
5. Guarda el veredicto en `specs/NNN-slug/review.md` y actualiza `specs/NNN-slug/state.json`
   después de cada acción. Actualiza también `specs/README.md` y el puntero global.
6. No mezcles requerimientos: si aparece trabajo de otro, se crea o se retoma su carpeta.
7. Detente y pregunta si: hay riesgo de pérdida de datos, se necesita una credencial, hay que
   borrar archivos, o el spec y el código se contradicen sin forma de decidir.

Cierra con un resumen de 10 líneas: requerimiento, qué se completó, qué quedó pendiente y qué
decisión necesita el usuario.
