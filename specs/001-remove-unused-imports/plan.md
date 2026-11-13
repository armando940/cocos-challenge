# Plan de Ejecución — 001 Remover imports no utilizados del proyecto

> Derivado de [`spec.md`](spec.md) y del estado real del repo el 2026-11-13.
> Los IDs de tarea son `R001-T01`, `R001-T02`… y solo existen dentro de este requerimiento.

## Resumen

- **Fases**: 4 (Análisis y detección, Limpieza main, Limpieza test, Verificación final)
- **Horas estimadas**: ~3.5h
- **Camino crítico**: R001-T01 → R001-T02 → R001-T03 → R001-T04 → R001-T05 → R001-T06 → R001-T07
- **Riesgo mayor**: R001-T02/R001-T03 (limpieza masiva de imports) — si se elimina un import requerido por procesamiento de anotaciones (ej. `@Autowired` en campos, JPA metamodel), la compilación falla. Mitigación: verificación incremental con `./gradlew compileJava` tras cada módulo.

## Estado actual vs plan

| Fase | Objetivo | Estado | Evidencia |
| :--- | :--- | :--- | :--- |
| 1 | Análisis y detección de imports unused | ⬜ Pendiente | `./gradlew compileJava` OK + reporte IDE/linter |
| 2 | Limpieza de imports en `src/main` | ⬜ Pendiente | `./gradlew compileJava` OK |
| 3 | Limpieza de imports en `src/test` | ⬜ Pendiente | `./gradlew test` OK |
| 4 | Verificación final y commit | ⬜ Pendiente | CA-01..CA-04 verificados, solo cambios en líneas `import` |

## Fase 1 — Análisis y detección (~0.5h)

**Objetivo**: Identificar exhaustivamente todos los imports no utilizados en el proyecto usando herramientas automatizadas.

**Entregable**: Lista de archivos con imports unused confirmados.

**DoD**: `./gradlew compileJava` compila OK; reporte de IDE (IntelliJ "Optimize Imports" inspection) o script genera lista accionable.

| ID | Tarea | Depende de | Estimación | Riesgo | Done |
| :--- | :--- | :--- | :--- | :--- | :--- |
| R001-T01 | Ejecutar análisis de imports unused en todo el proyecto (IntelliJ "Code > Inspect Code > Unused imports" o script gradle spotless/checkstyle) | — | 0.5h | Bajo | `./gradlew compileJava` OK + lista de archivos afectados exportada |

## Fase 2 — Limpieza de imports en `src/main` (~1.5h)

**Objetivo**: Remover todos los imports no utilizados en código de producción (`src/main/java`).

**Entregable**: Código `src/main` sin imports unused, compilando correctamente.

**DoD**: `./gradlew compileJava` compila sin errores; verificación spot-check en 3 archivos aleatorios confirma ausencia de imports unused.

| ID | Tarea | Depende de | Estimación | Riesgo | Done |
| :--- | :--- | :--- | :--- | :--- | :--- |
| R001-T02 | Limpiar imports en módulo `domain` (model, port, service) | R001-T01 | 0.5h | Medio | `./gradlew compileJava` OK; `grep -r "import.*\*.*;" src/main/java/ar/com/cocos/challenge/domain` sin unused |
| R001-T03 | Limpiar imports en módulo `application` (usecases) | R001-T02 | 0.25h | Bajo | `./gradlew compileJava` OK |
| R001-T04 | Limpiar imports en módulo `infrastructure` (config, adapter/in, adapter/out, actuator) | R001-T03 | 0.5h | Medio | `./gradlew compileJava` OK |
| R001-T05 | Limpiar imports en módulo `presentation/mcp` | R001-T04 | 0.25h | Bajo | `./gradlew compileJava` OK |

## Fase 3 — Limpieza de imports en `src/test` (~1h)

**Objetivo**: Remover todos los imports no utilizados en código de test (`src/test/java`).

**Entregable**: Código `src/test` sin imports unused, tests pasando.

**DoD**: `./gradlew test` pasa (incluye Testcontainers); spot-check en 2 archivos de test confirma ausencia de imports unused.

| ID | Tarea | Depende de | Estimación | Riesgo | Done |
| :--- | :--- | :--- | :--- | :--- | :--- |
| R001-T06 | Limpiar imports en tests unitarios (`unit/domain`, `unit/infrastructure`) | R001-T05 | 0.5h | Bajo | `./gradlew test --tests "*unit*"` OK |
| R001-T07 | Limpiar imports en tests de integración (`integration/`) | R001-T06 | 0.5h | Medio | `./gradlew test --tests "*IT*"` OK |

## Fase 4 — Verificación final y commit (~0.5h)

**Objetivo**: Confirmar que todos los criterios de aceptación se cumplen y preparar commit limpio.

**Entregable**: Commit con solo cambios en líneas `import` de archivos `*.java`.

**DoD**: CA-01..CA-04 verificados; `git diff --stat` muestra solo modificaciones en imports; `./gradlew check` OK (cobertura ≥ 80% en domain services).

| ID | Tarea | Depende de | Estimación | Riesgo | Done |
| :--- | :--- | :--- | :--- | :--- | :--- |
| R001-T08 | Verificación completa: `./gradlew compileJava`, `./gradlew test`, `./gradlew check` | R001-T07 | 0.25h | Bajo | Todos los comandos OK |
| R001-T09 | Validar diff: solo líneas `import` modificadas en `*.java` | R001-T08 | 0.15h | Bajo | `git diff -- "*.java" \| grep -E "^[\+\-].*import" \| wc -l` = total cambios |
| R001-T10 | Actualizar `state.json` a `done` y `review.md` para reviewer | R001-T09 | 0.1h | Bajo | `state.json` status=done; `review.md` existe |

## Camino crítico

R001-T01 → R001-T02 → R001-T03 → R001-T04 → R001-T05 → R001-T06 → R001-T07 → R001-T08 → R001-T09 → R001-T10

(No hay paralelismo seguro: cada fase de limpieza debe validar compilación antes de pasar a la siguiente para aislar regresiones.)

## Riesgos y mitigación

| Riesgo | Probabilidad | Impacto | Mitigación |
| :--- | :--- | :--- | :--- |
| Import eliminado rompe procesamiento de anotaciones (ej. JPA `@Entity`, Spring `@Autowired` en campo) | Media | Alto | Verificación incremental con `./gradlew compileJava` tras cada módulo (T02-T05). Spot-check manual en entidades JPA y componentes Spring. |
| Testcontainers falla por cambio incidental en test de integración | Baja | Medio | Ejecutar `./gradlew test --tests "*IT*"` aislado en T07; si falla, revertir solo ese archivo. |
| Spotless/Checkstyle no configurado → regresiones futuras | Media | Bajo | Fuera de alcance (supuesto en spec). Documentar en `review.md` como recomendación. |
| MCP comentado (MCPConfig, PlaceOrderTool, GetPortfolioTool) tiene imports que parecen unused pero son para feature stretch | Baja | Bajo | Verificar que esos archivos se limpien igual; si el código está comentado, los imports también pueden removerse. |
| `import package.*` (wildcard) usado en domain/model — optimización puede expandirlo a imports individuales | Media | Bajo | IntelliJ "Optimize Imports" respeta estilo del proyecto. Validar que no rompa convenciones. |