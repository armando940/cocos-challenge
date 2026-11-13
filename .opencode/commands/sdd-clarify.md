---
description: Detecta ambigüedades del spec de un requerimiento y pregunta al usuario
agent: coordinator
---

Revisa el `spec.md` del requerimiento y detecta las ambigüedades que bloquean la planificación.

Enfoque adicional del usuario: $ARGUMENTS

Resolución del requerimiento:
- Si el primer token es numérico (ej. `003`), operá sobre `specs/003-*/`.
- Si no se indica número, usá `activeRequirement` de `specs/state.json`.
- Si la carpeta no existe, detente: se crea con `/sdd-new`.

Reglas:
- Máximo 5 preguntas, cada una con 2–4 opciones concretas y una recomendada.
- Solo pregunta lo que cambia el diseño o el alcance. Lo demás va a `SUPUESTO:` en el spec.
- No planifiques ni escribas código en este paso.

Después de recibir respuestas:
1. Actualiza `specs/NNN-slug/spec.md` (decisiones + fecha).
2. Si la decisión es arquitectónica, crea o actualiza el ADR correspondiente en `docs/adr/`
   (los ADR son globales, no por requerimiento).
3. Cambia el estado del requerimiento a `clarified` en `specs/state.json` y en `specs/README.md`.
4. Resume en 5 líneas qué cambió.
