# Tasks Checklist — 003 Arreglar CI (gradlew sin bit ejecutable)

> Formato: `- [ ] RNNN-TXX — Descripción | Fase N | ola: WN | depende de: RNNN-TYY | toca: <rutas> | ~Xh | done: <criterio>`
> Derivado de [`plan.md`](plan.md) el 2026-11-13. `[x]` solo si el repo lo demuestra hoy.
> `toca:` son las ÚNICAS rutas que el implementer puede editar en esa tarea. Sin `toca:` = serial.
> `toca: SHARED(<archivo>)` = archivo de frontera: la tarea va sola en su ola.
> Nota: R003-T01 no edita ningún archivo del filesystem: es una operación de git sobre el índice
> (metadatos de modo, sin contenido). Por eso `toca:` usa el marcador especial `git-index(gradlew)`
> (modo, no contenido) en vez de una ruta editable. W1 va con 1 lane: no hay paralelismo posible
> en este requerimiento. CA-02 (CI verde en GitHub) se valida post-push, fuera del alcance local.

## Fase 1 — Fix del bit de ejecutable de gradlew

- [x] R003-T01 — Ejecutar `git update-index --chmod=+x gradlew` (pasa el modo del índice de `100644` a `100755`, sin editar contenido ni tocar `.github/workflows/ci.yml` —decisión del usuario—, `gradlew.bat`, Dockerfile ni build scripts) | Fase 1 | ola: W1 | — | toca: git-index(gradlew) | ~0.5h | done: `git ls-files -s gradlew` → `100755` (CA-01) + `git diff --cached gradlew` sin líneas de contenido, solo `old mode 100644`/`new mode 100755` + fuera de `specs/` el único cambio del repo es `gradlew` (CA-03) ✅ (2026-11-13: modo 100755 en índice, compileJava verde)

## Buffer

- [ ] R003-T90 — Imprevistos: si tras commit+push el CI sigue fallando por un motivo distinto a `gradlew`, o si la auditoría CA-04 revela otro script con modo incorrecto | Buffer | ola: W9 | — | toca: ? | ~0.5h | done: criterio definido al momento de activarse
