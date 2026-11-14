# Plan de Ejecución — 003 Arreglar CI (gradlew sin bit ejecutable)

> Derivado de [`spec.md`](spec.md) y del estado real del repo el 2026-11-13.
> Los IDs de tarea son `R003-T01`, `R003-T02`… y solo existen dentro de este requerimiento.

## Resumen

- **Fases**: 1 (Fix del bit de ejecutable de `gradlew`)
- **Olas**: 1 (W1) — 0 tareas paralelizables (la única tarea es una operación sobre el índice git; no hay paralelismo posible)
- **Horas estimadas**: 0.5 h
- **Camino crítico**: R003-T01
- **Riesgo mayor**: R003-T01 — el riesgo no es técnico (la operación es trivial) sino de proceso: el fix no tiene efecto hasta commit+push (el CI seguiría en exit 126) y existe la tentación de "parchar" el workflow en paralelo, prohibida por decisión del usuario (spec §2).

## Estado actual vs plan

| Fase | Objetivo | Estado | Evidencia |
| :--- | :--- | :--- | :--- |
| 1 | Marcar `gradlew` como ejecutable (`100755`) en el índice de git para desbloquear los steps de Gradle de CI (líneas 49 y 52 de `.github/workflows/ci.yml`, sin modificarlos) | ⬜ Pendiente | `git ls-files -s gradlew` → hoy `100644 f5feea6d6b116baaca5a2642d4d9fa1f47d574a7`; post-fix: `100755` |

## Fase 1 — Fix del bit de ejecutable de gradlew (~0.5h)

**Objetivo**: Dejar `gradlew` en modo `100755` en el índice (y en el árbol del próximo commit) para que el runner `ubuntu-latest` de GitHub Actions ejecute `./gradlew` sin `Permission denied` / exit 126, sin tocar `.github/workflows/ci.yml`.
**Entregable**: Cambio de modo staged sobre `gradlew` (metadatos git; cero cambios de contenido) listo para commit, con `ci.yml`, `gradlew.bat`, Dockerfile y build scripts intactos.
**DoD**: `git ls-files -s gradlew` → `100755` (CA-01) + `git diff --cached gradlew` sin líneas de contenido (CA-03) + auditoría de otros scripts (CA-04) + smoke test `./gradlew compileJava`. CA-02 (CI verde) se valida post-push.

| ID | Tarea | Depende de | Estimación | Riesgo | Done |
| :--- | :--- | :--- | :--- | :--- | :--- |
| R003-T01 | Ejecutar `git update-index --chmod=+x gradlew` (pasa el modo del índice de `100644` a `100755`, sin editar contenido ni tocar ningún otro archivo: ni `ci.yml` —decisión del usuario, spec §2—, ni `gradlew.bat`, ni Dockerfile) | — | 0.5 h | Bajo | `git ls-files -s gradlew` → `100755` (CA-01) + `git diff --cached gradlew` muestra solo `old mode 100644`/`new mode 100755`, sin líneas de contenido (CA-03) |

## Olas de ejecución

| Ola | Tareas en paralelo | Lanes | Serializa | Verificación de cierre |
| :-- | :-- | :-- | :-- | :-- |
| W1 | R003-T01 | 1 | — | Verificación única del coordinator (lista abajo) |

**No hay paralelismo posible**: una sola tarea en todo el requerimiento, y es una operación sobre el índice git (`toca: git-index(gradlew)`: modo, no contenido de archivo). El cierre de W1 lo hace el coordinator con una única verificación:

1. **CA-01**: `git ls-files -s gradlew` → `100755`.
2. **CA-03**: `git diff --cached --name-only` → única línea `gradlew`, y `git diff --cached gradlew` sin líneas de contenido (solo `old mode 100644` / `new mode 100755`). En `git status --porcelain` aparecen además `M specs/README.md`, `M specs/state.json` y `?? specs/003-fix-gradlew-exec-bit/`: bookkeeping SDD del coordinator, **esperado**; el check exige que **fuera de `specs/`** el único cambio sea `gradlew`. El cambio de modo vive en el índice: `git diff` (unstaged) no lo muestra; usar `git diff --cached` o `git status --porcelain`.
3. **CA-04**: `git ls-files -s "*.sh" gradlew gradlew.bat` → ningún otro script requiera el fix. Inventario a 2026-11-13: no hay archivos `.sh` en el índice; `gradlew` es el único afectado y `gradlew.bat` en `100644` es correcto (Windows no usa bit ejecutable).
4. Smoke test `./gradlew compileJava`: el cambio no toca código; solo confirma que el checkout local sigue sano. **NO** se corre `./gradlew test` (Testcontainers requiere Docker; no hay código tocado).
5. **Post-push (fuera del alcance local)**: **CA-02** — GitHub Actions (`build-and-test`) verde; ningún step falla con `Permission denied` / exit 126. El commit+push es decisión del coordinator/usuario: sin él el fix es inútil (DoD del spec §8).

## Camino crítico

`R003-T01` → commit + push → validación CA-02 en GitHub. Secuencia trivial de una sola tarea: no admite (ni necesita) paralelismo.

## Riesgos y mitigación

| Riesgo | Probabilidad | Impacto | Mitigación |
| :--- | :--- | :--- | :--- |
| (a) `core.filemode=false` (valor actual en Windows, verificado hoy con `git config core.filemode`) y alguien asume que el modo no persiste o intenta revertirlo re-checkouteando | Baja | Medio | `git update-index --chmod=+x` opera sobre el índice y persiste precisamente porque `core.filemode=false` hace que git ignore los bits del filesystem: el modo queda registrado en el árbol del commit y sobrevive clones/checkouts. Check previo: `git config core.filemode`; la verificación CA-01 lo confirma |
| (b) Se interpreta que hay que "arreglar" el workflow (agregar `chmod +x gradlew`, retries, etc.) | Media | Medio | Prohibido por decisión del usuario (spec §2): nada en `.github/workflows/ci.yml`, incluido el gate `vars.ENABLE_DOCKER_PUBLISH == 'true'` de `docker-build` (T-47 del baseline, intacto). La verificación de cierre (ítem 2) confirma que el diff no incluye `ci.yml` |
| (c) El fix queda staged pero sin commit+push → el CI sigue fallando con exit 126 y se percibe que "el fix no funcionó" | Media | Alto | El DoD y el spec §8 exigen commit con el cambio de modo; el commit+push es decisión del coordinator/usuario. CA-02 se valida post-push como paso explícito (ítem 5) |
| (d) Regresión futura: alguien re-agrega `gradlew` desde Windows y el modo vuelve a `100644` | Baja | Medio | Riesgo aceptado en spec §7 a cambio del diff mínimo. `.gitattributes` queda como mejora opcional futura, fuera de alcance de este requerimiento |
| (e) Falso positivo en la verificación de diff: `git status --porcelain` muestra además `specs/README.md`, `specs/state.json` y la carpeta del requerimiento (pendientes del coordinator) | Alta | Bajo | Documentado en la verificación de cierre (ítem 2): son metadatos SDD del coordinator, no cambios del fix; el check de CA-03 se evalúa sobre los cambios fuera de `specs/` |
