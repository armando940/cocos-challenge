# Review — Requerimiento 003: Arreglar CI (`gradlew` sin bit de ejecutable)

| Campo | Valor |
| :--- | :--- |
| **Requerimiento** | 003 (`specs/003-fix-gradlew-exec-bit/`) |
| **Fecha de revisión** | 2026-11-13 |
| **Revisor** | Coordinator SDD / Reviewer |
| **Veredicto final** | ✅ Aprobado |

---

## 1. Resumen de hallazgos

- **Fix de raíz aplicado correctamente:** `git update-index --chmod=+x gradlew` cambió el modo en el índice de `100644` a `100755` con el mismo SHA `f5feea6d6b116baaca5a2642d4d9fa1f47d574a7`.
- **Cero cambios de contenido:** `git diff --cached gradlew` confirma que la única modificación es `old mode 100644 / new mode 100755`. Ninguna línea agregada ni borrada en `gradlew`.
- **Decisión del usuario respetada:** `.github/workflows/ci.yml` permaneció intacto. No se agregaron steps `chmod +x` ni se alteró ningún gate (el gate `vars.ENABLE_DOCKER_PUBLISH == 'true'` de T-47 sigue igual).
- **Alcance acotado:** fuera de las carpetas `specs/` (bookkeeping de SDD), únicamente `gradlew` fue modificado en todo el repositorio.
- **Smoke test exitoso:** `./gradlew compileJava --no-daemon` respondió `BUILD SUCCESSFUL` en 7 segundos.

---

## 2. Auditoría de Criterios de Aceptación (spec §3)

| ID | Criterio | Estado | Evidencia |
| :--- | :--- | :---: | :--- |
| **CA-01** | `git ls-files -s gradlew` da `100755` | ✅ | `100755 f5feea6d6b116baaca5a2642d4d9fa1f47d574a7 0 gradlew` |
| **CA-02** | GitHub Actions no falla con exit 126 | ⏳ Pending push | Se validará automáticamente al pushear el commit a GitHub |
| **CA-03** | `git diff` fuera de `specs/` muestra solo `gradlew` | ✅ | `git diff --cached --name-only` da solo `gradlew` (cambio de metadatos) |
| **CA-04** | Auditoría de scripts shell en el repo | ✅ | `gradlew` es el único script shell; `gradlew.bat` está en `100644` (correcto para Windows) |

---

## 3. Checklist de Invariantes y DoD (spec §8)

- [x] Criterios de aceptación CA-01, CA-03 y CA-04 verificados en local.
- [x] Sin cambios en código Java/Gradle/workflow/Dockerfile: `git diff --cached --name-only` da solo `gradlew`.
- [x] `./gradlew compileJava` verde localmente.
- [x] Documentación/bookkeeping del requerimiento actualizado (`spec.md`, `plan.md`, `tasks.md`, `state.json`, `review.md`).
- [x] Veredicto ✅ aprobado.

---

## 4. Próximo paso recomendado

Hacer commit y push a la rama `main` o `develop` para que GitHub Actions corra con los permisos actualizados en Linux:

```bash
git commit -m "fix(ci): grant execution permission bit to gradlew (100755)"
git push
```
