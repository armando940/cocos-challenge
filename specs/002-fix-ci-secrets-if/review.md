# Revisión — 002 Arreglar workflow CI: `secrets` inválido en `if`

| Campo | Valor |
| :--- | :--- |
| **Fecha** | 2026-11-13 |
| **Alcance auditado** | `R002-T01` (ola W1 completa) |
| **Veredicto** | ✅ Aprobado |
| **Auditor** | `reviewer` |

## Resumen

El fix es exactamente el declarado en el spec: eliminación quirúrgica de la línea 86 (`if:` con
contexto `secrets`) del step `Login to Docker Hub`. `git diff --numstat` = `0 1` exacto; el YAML
parsea correctamente; los 3 jobs y todos los steps se preservan; el gate de T-47
(`vars.ENABLE_DOCKER_PUBLISH == 'true'`) quedó byte a byte idéntico; el job `release` no fue
tocado. La documentación del requerimiento es coherente con lo implementado. No hay hallazgos
bloqueantes.

## Checklist

| Categoría | Estado | Hallazgos |
| :--- | :--- | :--- |
| **CA-01** — Ningún `if:` referencia `secrets` | ✅ | Inventario post-fix: exactamente 4 `if:` → líneas 58 y 65 (`always()`), 76 (gate `docker-build`), 117 (gate `release`). Grep `if:.*secrets` → **0 matches**. Las referencias `secrets.` restantes (88, 89, 95 en `with:`, 127 en `env:`) son ubicaciones válidas para el parser de Actions |
| **CA-02** — YAML válido, 3 jobs preservados | ✅ | `yaml.safe_load` parsea sin error. Jobs: `['build-and-test', 'docker-build', 'release']`. `docker-build` conserva sus 5 steps incl. `Login to Docker Hub` (ahora sin `if` a nivel step). Los 4 `if:` restantes usan solo contextos válidos (`always()`, `github.*`, `vars.*`); la validación runtime completa ocurre en el próximo push (SUPUESTO §7, correctamente declarado) |
| **CA-03** — Gate T-47 intacto; `release` no tocado | ✅ | Gate de job parseado == exactamente `"github.event_name == 'push' && github.ref == 'refs/heads/main' && vars.ENABLE_DOCKER_PUBLISH == 'true'"` (línea 76) — coincide con T-47 del baseline ("CI pasa en fork sin secrets"). `release` intacto: gate `push && refs/heads/main` + único step `Create Release` con `GITHUB_TOKEN` en `env:` |
| **CA-04** — Diff mínimo, cero cambios de código | ✅ | `git diff --numstat -- .github/workflows/ci.yml` = **`0 1`** exacto (solo la línea `if:` eliminada). `git status` no muestra cambios en `src/`, `build.gradle.kts`, Dockerfiles ni `.env`. Los otros archivos modificados (`specs/README.md`, `specs/state.json`, nuevo `specs/002-fix-ci-secrets-if/`) son bookkeeping SDD del coordinator — esperables |
| **Semántica del fix** | ✅ | Con el `if` de step removido: en fork sin variable, el job entero se saltea → login nunca corre (SUPUESTO §7 cumple). Con gate habilitado, login corre incondicional dentro del job — correcto, los secrets se asumen configurados |
| **Referencias `secrets` en `with:`/`env:`** | ✅ | `DOCKERHUB_USERNAME`/`DOCKERHUB_TOKEN` en `with:` (login 88-89, metadata 95) y `GITHUB_TOKEN` en `env:` (127) — intactas y semánticamente correctas. Sin credenciales literales en el archivo |
| **Arquitectura hexagonal** | N/A | Cero código Java tocado (verificado vía `git status`) |
| **Transacciones / concurrencia** | N/A | No aplica a cambio de CI |
| **Tests y cobertura** | ✅ (N/A) | Correcto no correr `./gradlew test`: no hay código Java modificado. Smoke test `compileJava` corrido por el coordinator (EXIT=0), conforme al DoD del spec |
| **Seguridad** | ✅ | T-47 preservado. Sin secretos expuestos ni hardcodeados. Scope creep evitado: no se desbloquearon `docker-build`/`release` (riesgo (c) del plan, mitigado) |
| **Documentación y trazabilidad** | ✅ | `tasks.md` R002-T01 `[x]` con evidencia que coincide con lo verificado (0+/1−, YAML OK). `plan.md` con inventario pre-fix de `if:` exacto (58/65/76/86/118; post-fix release corrió 118→117 por el corrimiento, esperado). `state.json` local `in-review` con nota de cierre coherente. Spec §7 registra el pendiente global de secrets (pre-existente del baseline) |

## Hallazgos bloqueantes

Ninguno.

## Hallazgos menores

1. **`specs/state.json:20`** — Inconsistencia de ciclo de vida entre punteros: el `state.json`
   **global** registraba 002 con `"status": "draft"`, el `README.md` decía `🟡 ready` y el
   `state.json` **local** `in-review`. → Resuelto por el coordinator en el cierre: global y
   README sincronizados a `done`.
2. **`.github/workflows/ci.yml:114` (pre-existente, fuera de alcance)** — `release` tiene
   `needs: docker-build`, por lo que en un fork (donde `docker-build` se saltea) `release`
   también se saltea por dependencia aunque su propio `if:` pase. Comportamiento del baseline,
   **no introducido ni agravado** por 002; registrar como candidato a futuro requerimiento si se
   quiere release independiente del publish.
3. **`plan.md:38`** — El plan sugiere `actionlint` como alternativa de validación; no está
   disponible en este entorno. El parseo con `yaml.safe_load` + grep exhaustivo lo suple;
   la validación semántica completa de Actions solo ocurre en GitHub (ya declarado como supuesto).

## Próximos pasos

- [x] implementer: sin hallazgos críticos que corregir — R002-T01 conforme.
- [x] coordinator: guardar este reporte en `specs/002-fix-ci-secrets-if/review.md`, actualizar
  `specs/002-fix-ci-secrets-if/state.json` (`lastReview.verdict = "approved"`, status → `done`) y
  sincronizar el estado de 002 en `specs/state.json` global y `specs/README.md`.
- [ ] Recordatorio global (pre-existente del baseline, fuera de este requerimiento): configurar
  `ENABLE_DOCKER_PUBLISH` + secrets `DOCKERHUB_USERNAME/TOKEN` en GitHub para habilitar
  publicación real (verificado en el próximo push a `main`, per SUPUESTO §7).
