# Tasks Checklist — NNN <título>

> Formato: `- [ ] RNNN-TXX — Descripción | Fase N | ola: WN | depende de: RNNN-TYY | toca: <rutas> | ~Xh | done: <criterio>`
> Derivado de [`plan.md`](plan.md) el YYYY-MM-DD. `[x]` solo si el repo lo demuestra hoy.
> `toca:` son las ÚNICAS rutas que el implementer puede editar en esa tarea. Sin `toca:` = serial.
> `toca: SHARED(<archivo>)` = archivo de frontera: la tarea va sola en su ola.

## Fase 1 — <nombre>

- [ ] RNNN-T01 — <tarea> | Fase 1 | ola: W1 | — | toca: `src/main/java/.../X.java`, `src/test/java/.../XTest.java` | ~1h | done: `<comando>`
- [ ] RNNN-T02 — <tarea> | Fase 1 | ola: W1 | — | toca: `src/main/java/.../Y.java`, `src/test/java/.../YTest.java` | ~1h | done: `<comando>`
- [ ] RNNN-T03 — <tarea> | Fase 1 | ola: W2 | depende de: RNNN-T01, RNNN-T02 | toca: SHARED(`src/main/resources/application.yml`) | ~1h | done: `<comando>`

## Buffer

- [ ] RNNN-T90 — <imprevisto conocido> | Buffer | ola: W9 | — | toca: ? | ~1h | done: <criterio>
