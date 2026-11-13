---
description: Genera o actualiza el spec.md de un requerimiento
agent: planner
---

Genera o actualiza el `spec.md` del requerimiento indicado.

Solicitud: $ARGUMENTS

Resolución del requerimiento:
- Si el primer token es numérico (ej. `003`), operá sobre `specs/003-*/`.
- Si no se indica número, usá `activeRequirement` de `specs/state.json`.
- Si la carpeta no existe, detente: se crea con `/sdd-new`. No la inventes.

Pasos:
1. Lee `specs/NNN-slug/spec.md`, `AGENTS.md`, `specs/000-baseline-challenge/spec.md` y los ADR que
   apliquen.
2. Contrasta el spec con el código real en `src/main/java` para no documentar algo que ya no existe.
3. Completá las secciones del spec: contexto, alcance dentro/fuera, criterios de aceptación,
   contratos afectados, impacto en requerimientos previos, NFR específicos, supuestos y DoD.
4. **No dupliques las invariantes globales** (hexagonal, `BigDecimal`, transacciones, locks,
   idempotencia, secretos): referencialas a `AGENTS.md` y al baseline.
5. Todo hueco o duda va marcado como `SUPUESTO:` o `PENDIENTE:`; no lo rellenes por tu cuenta.
6. No cambies el estado a `clarified`: eso ocurre en `/sdd-clarify`. No escribas `plan.md` ni
   `tasks.md`: eso es `/sdd-plan` y `/sdd-tasks`.

Contexto adicional del usuario: $ARGUMENTS
