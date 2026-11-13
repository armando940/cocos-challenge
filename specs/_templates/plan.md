# Plan de Ejecución — NNN <título>

> Derivado de [`spec.md`](spec.md) y del estado real del repo el YYYY-MM-DD.
> Los IDs de tarea son `RNNN-T01`, `RNNN-T02`… y solo existen dentro de este requerimiento.

## Resumen

- **Fases**: <n> (<nombres>)
- **Olas**: <n> (W1…Wn) — <tareas paralelizables totales>
- **Horas estimadas**: <h>
- **Camino crítico**: <RNNN-T01 → RNNN-T03 → ...>
- **Riesgo mayor**: <tarea y por qué>

## Estado actual vs plan

| Fase | Objetivo | Estado | Evidencia |
| :--- | :--- | :--- | :--- |
| 1 | <objetivo> | ⬜ Pendiente | `<archivo o comando que lo prueba>` |

## Fase 1 — <nombre> (~Xh)

**Objetivo**: <una frase>
**Entregable**: <qué queda en el repo>
**DoD**: <criterio verificable con comando>

| ID | Tarea | Depende de | Estimación | Riesgo | Done |
| :--- | :--- | :--- | :--- | :--- | :--- |
| RNNN-T01 | <descripción accionable> | — | 1 h | Bajo | `<comando o criterio>` |
| RNNN-T02 | <descripción accionable> | RNNN-T01 | 1 h | Medio | `<comando o criterio>` |

## Olas de ejecución

| Ola | Tareas en paralelo | Lanes | Serializa | Verificación de cierre |
| :-- | :-- | :-- | :-- | :-- |
| W1 | RNNN-T02, RNNN-T03, RNNN-T04 | 3 | — | `./gradlew compileJava test` |
| W2 | RNNN-T05 | 1 | `build.gradle.kts` | `./gradlew check` |

> Dos tareas comparten ola solo si no hay dependencia entre ellas y sus rutas `toca:` (en `tasks.md`)
> son disjuntas. Archivos de frontera (`build.gradle.kts`, `application*.yml`, `db/migration`,
> `ExceptionHandler`, `tasks.md`, `state.json`) serializan: la tarea va sola. Máximo 3 lanes.

## Camino crítico

<Secuencia de tareas que no admite paralelismo.>

## Riesgos y mitigación

| Riesgo | Probabilidad | Impacto | Mitigación |
| :--- | :--- | :--- | :--- |
| <riesgo> | Baja/Media/Alta | Bajo/Medio/Alto | <acción concreta> |
