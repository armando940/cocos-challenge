---
description: Muestra el estado real del flujo SDD (global o por requerimiento)
agent: coordinator
---

Reporta el estado del proyecto sin modificar nada.

Solicitud: $ARGUMENTS

Modo:
- Si el argumento es `all` (o no hay `activeRequirement`), reportá **todos** los requerimientos.
- Si el primer token es numérico (ej. `003`), reportá solo `specs/003-*/`.
- Si no se indica nada, reportá el `activeRequirement` de `specs/state.json`.

Protocolo:
1. Lee `specs/README.md`, `specs/state.json` y, del requerimiento en foco, `spec.md`, `plan.md`,
   `tasks.md`, `state.json` y `review.md` si existen.
2. Contrasta con el repo: tareas marcadas `[x]` que no están implementadas, y trabajo hecho que no
   figura en `tasks.md` (revisa `src/main`, `src/test`, `docs/adr`, `.github/workflows`).
3. Verifica rápido el estado de calidad: `./gradlew compileJava test` y la cobertura de Jacoco.
4. Devuelve, según el modo:
   - **Global**: tabla con # · carpeta · título · estado · activo · última revisión, y próximos
     pasos por requerimiento.
   - **Uno**: fase actual, % de tareas por fase, última revisión, riesgos abiertos y la próxima
     acción concreta recomendada.
5. Si detectás que el índice y las carpetas no coinciden, reportalo como inconsistencia.
