# Plan de Ejecución — 002 Arreglar workflow CI (secrets en if)

> Derivado de [`spec.md`](spec.md) y del estado real del repo el 2026-11-13.
> Los IDs de tarea son `R002-T01`, `R002-T02`… y solo existen dentro de este requerimiento.

## Resumen

- **Fases**: 1 (Fix del workflow CI)
- **Olas**: 1 (W1) — 0 tareas paralelizables (la única tarea toca un archivo de CI; no hay paralelismo posible)
- **Horas estimadas**: 0.5 h
- **Camino crítico**: R002-T01
- **Riesgo mayor**: R002-T01 — error de indentación/estructura YAML al eliminar la línea 86, que dejaría el workflow inválido de nuevo; se mitiga con parseo del YAML resultante.

## Estado actual vs plan

| Fase | Objetivo | Estado | Evidencia |
| :--- | :--- | :--- | :--- |
| 1 | Fix del workflow CI: remover la condición `if` con contexto `secrets` (inválida para el parser de GitHub Actions) del step `Login to Docker Hub` | ⬜ Pendiente | `grep -n "if:.*secrets" .github/workflows/ci.yml` → devuelve hoy la línea 86; post-fix: `git diff .github/workflows/ci.yml` |

## Fase 1 — Fix del workflow CI (~0.5h)

**Objetivo**: Restaurar la validez de `.github/workflows/ci.yml` removiendo la única referencia al contexto `secrets` dentro de un `if` de step (`Unrecognized named-value: 'secrets'`).
**Entregable**: `ci.yml` sin la línea 86 y sin ningún otro cambio; el gate de job `vars.ENABLE_DOCKER_PUBLISH == 'true'` (línea 76, T-47) queda intacto.
**DoD**: cero `secrets` dentro de `if:` (verificable con grep) + `git diff` mínimo (solo esa línea eliminada) + smoke test `./gradlew compileJava`.

| ID | Tarea | Depende de | Estimación | Riesgo | Done |
| :--- | :--- | :--- | :--- | :--- | :--- |
| R002-T01 | Remover la línea 86 (`if: ${{ secrets.DOCKERHUB_USERNAME != '' && secrets.DOCKERHUB_TOKEN != '' }}`) del step `Login to Docker Hub` del job `docker-build`, sin tocar nada más (gate de job de la línea 76 intacto) | — | 0.5 h | Bajo | `grep "if:.*secrets" .github/workflows/ci.yml` sin matches + `git diff` muestra solo esa línea eliminada (1 −, 0 +) |

## Olas de ejecución

| Ola | Tareas en paralelo | Lanes | Serializa | Verificación de cierre |
| :-- | :-- | :-- | :-- | :-- |
| W1 | R002-T01 | 1 | — | Validación sintáctica del YAML (parseo post-fix) + `git diff --name-only` (solo `.github/workflows/ci.yml`) + `./gradlew compileJava` como smoke test |

**No hay paralelismo posible**: una sola tarea en todo el requerimiento, sobre un archivo de frontera de CI. El cierre de W1 lo hace el coordinator con una única verificación:

1. Parseo del YAML resultante (p. ej. `python -c "import yaml;yaml.safe_load(open('.github/workflows/ci.yml',encoding='utf-8'));print('YAML OK')"` o `actionlint .github/workflows/ci.yml` si está disponible).
2. `git diff --name-only` → únicamente `.github/workflows/ci.yml` (cero código Java).
3. `grep "if:.*secrets" .github/workflows/ci.yml` → cero matches (CA-01).
4. `./gradlew compileJava` → smoke test. **NO** se corre `./gradlew test`: el cambio no toca código Java y Testcontainers requiere Docker.

## Camino crítico

`R002-T01` — secuencia trivial de una sola tarea: no admite (ni necesita) paralelismo.

## Riesgos y mitigación

| Riesgo | Probabilidad | Impacto | Mitigación |
| :--- | :--- | :--- | :--- |
| (a) Error de indentación/estructura YAML al borrar la línea → workflow inválido de nuevo | Baja | Alto | El Done exige parseo del YAML resultante y `git diff` mínimo (1 línea eliminada, 0 agregadas); el edit es quirúrgico sobre la línea 86 identificada |
| (b) Otra referencia `secrets` dentro de algún `if` no detectada → el parser seguiría fallando | Baja | Alto | Grep exhaustivo de todos los `if:` del archivo en la verificación. Inventario hecho a 2026-11-13: `if:` en líneas 58 y 65 (`if: always()`), 76 (gate `docker-build` con `github`/`vars`), 86 (la inválida) y 118 (gate `release`); las referencias `secrets` restantes (89, 90, 96, 128) están en `with:`/`env:` y son válidas |
| (c) Se interpreta que hay que "aprovechar" para desbloquear `docker-build`/`release` (configurar secrets, tocar el gate o borrar jobs) | Media | Medio | Fuera de alcance explícito (spec §2): el gate de job `vars.ENABLE_DOCKER_PUBLISH == 'true'` (T-47) queda exactamente igual; la verificación de cierre confirma que el diff es solo esa línea |
