# Requerimiento 002 — Arreglar workflow de CI: `secrets` inválido en condición `if`

| Campo | Valor |
| :--- | :--- |
| **Número** | 002 |
| **Carpeta** | `specs/002-fix-ci-secrets-if/` |
| **Tipo** | bugfix |
| **Estado** | done |
| **Creado** | 2026-11-13 |
| **Actualizado** | 2026-11-13 |
| **Depende de** | 000 (baseline — tarea T-47, CI secret-safe) |
| **Supersede a** | — |

> **Alcance de este documento:** solo el **delta** de este requerimiento. Las invariantes del
> proyecto (hexagonal, `BigDecimal`, transacciones, locks, idempotencia, secretos) son globales y
> viven en `AGENTS.md` y en [`../000-baseline-challenge/spec.md`](../000-baseline-challenge/spec.md).
> No las repitas: referencialas. Los ADR aplicables están en `docs/adr/`.

---

## 1. Contexto y motivación

El workflow `.github/workflows/ci.yml` es inválido y hace fallar **todas** las corridas de
GitHub Actions. En la línea ~86, el step `Login to Docker Hub` del job `docker-build` declara:

```yaml
if: ${{ secrets.DOCKERHUB_USERNAME != '' && secrets.DOCKERHUB_TOKEN != '' }}
```

GitHub Actions **no permite** el contexto `secrets` en condiciones `if` de steps: el parser
falla con `Unrecognized named-value: 'secrets'` y marca el workflow completo como inválido.
Consecuencia: **ningún job corre**, ni siquiera `build-and-test`. El análisis ya fue hecho;
este requerimiento solo corrige.

## 2. Alcance

### Dentro

- Quitar la condición `if` del step `Login to Docker Hub` en `.github/workflows/ci.yml`.
- Verificar que el YAML resultante sea sintácticamente válido y que no quede ninguna referencia
  a `secrets` dentro de ningún `if`.

### Fuera

- **No** se toca nada más del workflow ni del build (steps, jobs, env, tags).
- **No** se desbloquea `docker-build` ni `release`: el gate a nivel de job
  `vars.ENABLE_DOCKER_PUBLISH == 'true'` queda exactamente igual (T-47).
- **No** se configuran secrets ni variables de repositorio en GitHub.
- No hay cambios de código Java, Gradle, Docker ni documentación.

## 3. Criterios de aceptación

| ID | Given | When | Then |
| :--- | :--- | :--- | :--- |
| CA-01 | `ci.yml` corregido | Se inspecciona el archivo | Ningún `if:` referencia el contexto `secrets` (verificable con grep) |
| CA-02 | Workflow corregido | Se parsea el YAML | Sintaxis válida; sin `Unrecognized named-value` (el error reportado era solo el `secrets` en `if`) |
| CA-03 | Repo sin secrets ni `ENABLE_DOCKER_PUBLISH` (fork) | Push a `main` | `docker-build` sigue **sin correr** (gate de job intacto) → T-47 se mantiene |
| CA-04 | Fix aplicado | `git diff` | Diff mínimo: solo la eliminación de la línea `if` de ese step; ningún otro cambio |

## 4. Contratos afectados

No cambia ningún contrato: endpoints, requests/responses, códigos de error, eventos, tablas o
configuración de la aplicación permanecen idénticos. Es un cambio exclusivo de CI.

## 5. Impacto en requerimientos previos

- **T-47 (baseline, CI secret-safe):** se mantiene cumplido. El job `docker-build` sigue gateado
  por `if: github.event_name == 'push' && github.ref == 'refs/heads/main' && vars.ENABLE_DOCKER_PUBLISH == 'true'`.
  La condición removida era redundante con ese gate e inválida para el parser.
- **docker-build/release siguen bloqueados** hasta configurar `ENABLE_DOCKER_PUBLISH=true` +
  secrets `DOCKERHUB_USERNAME/TOKEN` en GitHub (decisión pendiente global del baseline, fuera de
  alcance acá).

## 6. NFR específicos

Sin NFR adicionales: aplican las invariantes de `AGENTS.md`.

## 7. Supuestos y pendientes

- **SUPUESTO:** con la condición del step removida, `docker-build` solo corre cuando el gate de
  job pasa (`ENABLE_DOCKER_PUBLISH == 'true'`); en un fork sin esa variable el job se saltea
  completo y el login nunca se ejecuta → T-47 intacto.
- **SUPUESTO:** la verificación local es estructural (parseo de YAML + grep + `git diff`); la
  validación en runtime ocurre en el próximo push a GitHub (fuera del alcance local).
- **PENDIENTE (global, pre-existente del baseline):** configurar `DOCKERHUB_USERNAME/TOKEN` y
  `ENABLE_DOCKER_PUBLISH` en el repo de GitHub para habilitar publicación real. No es parte de
  este requerimiento.

## 8. Definition of Done

- [x] Criterios de aceptación CA-01..CA-04 verificados (ver `review.md`)
- [x] Sin cambios en código Java: `git diff --name-only` solo muestra `.github/workflows/ci.yml`
- [x] `./gradlew compileJava` como smoke test (no se requieren tests: cero código Java tocado)
- [x] Documentación actualizada (spec, plan, tasks, review de este requerimiento)
- [x] Reviewer emitió ✅ en `review.md` (2026-11-13, sin hallazgos bloqueantes)
