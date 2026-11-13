# SDD Commands — Flujo Spec-Driven Development por requerimiento

Esta carpeta define los **comandos del flujo SDD** para usar con los agentes en OpenCode.

El cambio central respecto del flujo anterior: **un requerimiento = una carpeta numerada**. Cada
requerimiento tiene su propio `spec.md`, `plan.md`, `tasks.md`, `state.json` y `review.md`. No hay
un spec global que mezcle todo.

## Agentes Disponibles

| Agente | Archivo | Modo | Rol |
|--------|---------|------|-----|
| `coordinator` | `.opencode/agents/coordinator.md` | `all` (primary + subagent) | Orquestador, intake y numeración |
| `planner` | `.opencode/agents/planner.md` | `subagent` | Planificador técnico del requerimiento |
| `implementer` | `.opencode/agents/implementer.md` | `subagent` | Implementador senior |
| `reviewer` | `.opencode/agents/reviewer.md` | `subagent` (sin edición) | Auditor del requerimiento |

> Los comandos son archivos reales en `.opencode/commands/`. Escribí `/sdd` en la TUI para ver la
> lista.

---

## Modelo de requerimientos

```text
specs/
├── README.md                  # índice: 000, 001, 002… estado y links
├── state.json                 # puntero global: activeRequirement
├── _templates/                # plantillas
│   ├── spec.md · plan.md · tasks.md · review.md · state.json
├── 000-baseline-challenge/    # base implementada + invariantes globales
│   ├── spec.md · plan.md · tasks.md · state.json
└── 001-<slug>/                # requerimiento nuevo
    ├── spec.md · plan.md · tasks.md · state.json · review.md
```

Reglas:

- El número (`NNN`) lo asigna el coordinator: mayor existente + 1, con 3 dígitos `001`, `002`…
- **Un número no se reutiliza** ni se borra. Un requerimiento descartado queda `cancelled`.
- **Un solo requerimiento activo** a la vez (`specs/state.json → activeRequirement`).
- Un requerimiento cerrado no se edita: se crea uno nuevo con `Supersede a: NNN`.
- Los **ADR son globales** (`docs/adr/`).
- Las invariantes del proyecto viven en `AGENTS.md` y en el baseline; cada spec nuevo documenta
  solo su **delta** y las referencia.

### Ciclo de vida

`draft → clarified → planned → ready → in-progress → in-review → done`
(más `blocked` y `cancelled`).

---

## Comandos del Flujo

### `/sdd-new` — Crear requerimiento

```bash
/sdd-new "Agregar endpoint de historial de órdenes por usuario"
```

**Agente**: `coordinator` (sesión actual)
**Entrada**: descripción del requerimiento
**Salida**: `specs/NNN-slug/` con `spec.md` en `draft`, fila en `specs/README.md` y puntero global
actualizado.

No escribe `plan.md` ni `tasks.md`: eso viene después.

---

### `/sdd-spec` — Completar la especificación

```bash
/sdd-spec 003
/sdd-spec          # usa el requerimiento activo
```

**Agente**: `planner` (sesión hija)
**Entrada**: `specs/NNN-slug/spec.md` + baseline + ADR + código real
**Salida**: spec del requerimiento con alcance, criterios de aceptación, contratos, impacto y DoD

---

### `/sdd-clarify` — Clarificar ambigüedades

```bash
/sdd-clarify 003
```

**Agente**: `coordinator` (sesión actual)
**Herramienta**: `question` (máx. 5 preguntas con opciones)
**Salida**: spec actualizado + ADR global si la decisión es arquitectónica + estado `clarified`

---

### `/sdd-plan` — Plan del requerimiento

```bash
/sdd-plan 003
```

**Agente**: `planner` (sesión hija)
**Entrada**: `specs/NNN-slug/spec.md` + estado real del repo
**Salida**: `specs/NNN-slug/plan.md` con fases, tareas `RNNN-TXX`, dependencias, riesgos y DoD

---

### `/sdd-tasks` — Checklist ejecutable

```bash
/sdd-tasks 003
```

**Agente**: `planner` (sesión hija)
**Entrada**: `specs/NNN-slug/plan.md`
**Salida**: `specs/NNN-slug/tasks.md` (checkboxes con done criteria verificable) + estado `ready`

---

### `/sdd-run` — Ejecutar tareas

```bash
/sdd-run 003 ola 2
/sdd-run 003 fase 1
/sdd-run 003 R003-T10..R003-T18
/sdd-run 003 serial
/sdd-run 003 "arreglar los hallazgos del último review"
```

**Agente**: `coordinator` (sesión actual), que lanza N `implementer` en paralelo
**Entrada**: `specs/NNN-slug/tasks.md` (con `ola:` y `toca:`) + `specs/NNN-slug/plan.md`
**Salida**: código, tests, tareas marcadas `[x]` y `state.json` actualizado

Ejecuta por **olas**: una ola es un grupo de tareas sin dependencia entre ellas y con rutas `toca:`
disjuntas. Un `implementer` por lane, en paralelo. Si no hay olas, cae a modo serie.

No toca tareas de otro requerimiento ni carpetas `done`/`cancelled`.

---

### `/sdd-check` — Revisión de calidad

```bash
/sdd-check 003
/sdd-check 003 ola 2
/sdd-check 003 final
```

**Agente**: `reviewer` (sesión hija, sin permiso de edición)
**Entrada**: código implementado + spec del requerimiento + invariantes globales
**Salida**: veredicto (✅/⚠️/❌) + hallazgos con `archivo:línea`, listo para que el coordinator lo
guarde en `specs/NNN-slug/review.md`

Se revisa **por ola**, no por lane: un solo veredicto sobre el conjunto de tareas de esa ola.

---

### `/sdd-status` — Estado real

```bash
/sdd-status          # requerimiento activo
/sdd-status 003      # un requerimiento
/sdd-status all      # tabla global
```

**Agente**: `coordinator` (sesión actual)
**Salida**: estado, tareas hechas vs. repo, cobertura, riesgos y próxima acción

---

### `/sdd` — Flujo completo

```bash
/sdd 003
/sdd "Agregar endpoint de historial de órdenes"
```

**Agente**: `coordinator`, que delega con `subagent`
**Comportamiento**: si la descripción no corresponde a un requerimiento existente, ejecuta primero
el intake de `/sdd-new` y sigue con esa carpeta. Luego:

```text
/sdd-spec → /sdd-clarify → /sdd-plan → /sdd-tasks →
  [W1: /sdd-run → /sdd-check] →
  [W2: /sdd-run → /sdd-check] →
  ...
  Buffer & Polish
```

Dentro de cada ola, `/sdd-run` lanza un implementer por lane en paralelo y `/sdd-check` audita la
ola completa una sola vez.

---

## Ejecución en olas (paralelismo controlado)

Cuando el requerimiento da para más de un implementer, `tasks.md` no se ejecuta tarea por tarea:
se ejecuta **por olas**. Una ola (`W1`, `W2`…) es un conjunto de tareas que se pueden implementar al
mismo tiempo sin pisarse.

### Qué declara el planner

Cada tarea lleva su ola y su alcance de archivos:

```text
- [ ] R003-T05 — Implementar OrderHistoryQueryService | Fase 2 | ola: W2 | depende de: — |
      toca: src/main/java/.../OrderHistoryQueryService.java, src/test/java/.../OrderHistoryQueryServiceTest.java | ~1h | done: ./gradlew test
```

y `plan.md` lleva la tabla de olas:

```text
## Olas de ejecución

| Ola | Tareas en paralelo | Lanes | Serializa | Verificación de cierre |
| W1  | R003-T02, R003-T03, R003-T04 | 3 | — | ./gradlew compileJava test |
| W2  | R003-T05 | 1 | build.gradle.kts | ./gradlew check |
```

### Cuándo dos tareas comparten ola

Las tres condiciones a la vez:

1. **Sin dependencia** directa ni transitiva.
2. **`toca:` disjuntos**: ningún archivo en común.
3. **Sin acoplamiento de compilación**: ninguna crea un símbolo que la otra consuma.

Máximo **3 lanes** por ola. Ante cualquier duda, serie: un lane pisado cuesta más que una ola lenta.

### Archivos de frontera

Serializan siempre; la tarea que los toca va sola y se marca `toca: SHARED(...)`:

| Archivo | Por qué |
| :--- | :--- |
| `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties` | build compartido |
| `src/main/resources/application*.yml` | configuración global |
| `src/main/resources/db/migration/*` | orden de Flyway |
| `ExceptionHandler` / mappers compartidos | punto único de cambio |
| `tasks.md`, `state.json`, `specs/README.md` | escritura exclusiva del coordinator |

También es regla: el test viaja en la misma tarea que su fuente (`X.java` + `XTest.java`).

### Qué hace el coordinator

1. Audita el árbol (`git status --porcelain`) y **recalcula** conflictos de `toca:`: si dos tareas
   comparten archivo, baja una a la ola siguiente.
2. Lanza un `implementer` por lane, en paralelo (varias llamadas `subagent` en el mismo turno), con
   **solo** su tarea, su ola, su whitelist de rutas y el criterio de done.
3. Al cerrar: `git diff --name-only` para detectar solapamiento real → **una** verificación
   (`./gradlew compileJava test`) → recién ahí marca `[x]` y actualiza `state.json`.
4. Manda **un** `reviewer` sobre la ola completa.

### Prohibiciones del modo paralelo

Van en el prompt de cada implementer:

| Prohibición | Motivo |
| :--- | :--- |
| No correr `./gradlew` ni `docker compose` | daemon, `build/`, Testcontainers y Docker son recursos compartidos |
| No editar `tasks.md`, `state.json`, `README.md` | escritura única del coordinator (si no, last-write-wins) |
| No tocar rutas fuera de la whitelist | el `write`/`edit` es last-write-wins silencioso |
| No crear archivos de frontera | rompen la secuencia de build/migraciones |
| No refactorizar fuera del alcance | convierte un lane barato en un conflicto |

### Cuándo NO usar olas

- Tareas de menos de ~20 minutos: coordinar cuesta más que hacerlas.
- Repo sucio o con cambios sin commitear fuera del requerimiento.
- Tareas que tocan el mismo archivo o el mismo bean de Spring.
- Refactors transversales (`rename`, cambio de paquete, formato masivo).

> Alternativa no usada por defecto: **git worktree por lane**. Aísla de verdad el build, pero obliga
> a mergear cada lane de vuelta. Se justifica solo para tareas grandes o riesgosas, no para el flujo
> diario.

---

## Cómo se resuelve el requerimiento

Todos los comandos aceptan un selector:

| Argumento | Efecto |
|-----------|--------|
| `003` | Opera sobre `specs/003-*/` |
| nada | Usa `activeRequirement` de `specs/state.json` |
| `all` | Solo en `/sdd-status`: reporta todos |

Si la carpeta no existe, el comando se detiene y sugiere `/sdd-new`. No se inventan números.

---

## Uso Práctico en OpenCode

| Querés… | Hacé esto |
|---------|-----------|
| Pedir algo nuevo | `/sdd-new "descripción"` |
| Correr un paso del flujo | `/sdd-plan 003`, `/sdd-run 003`, `/sdd-check 003` |
| Correr todo un requerimiento | `/sdd 003` |
| Crear y ejecutar uno nuevo | `/sdd "descripción"` |
| Ver dónde estás | `/sdd-status` o `/sdd-status all` |
| Ejecutar en paralelo | `/sdd-run 003 ola 2` (un implementer por lane) |
| Forzar serie | `/sdd-run 003 serial` |
| Hablar con el coordinador | Elegí `coordinator` como agente (Tab / selector) |
| Delegar a un especialista | Lo hace el coordinator con `subagent`; no se escribe `@planner` |

`@planner:` en el chat **no** invoca al agente: es texto plano. La delegación real ocurre por la
herramienta `subagent`, y los comandos `/sdd-*` ya traen el agente configurado.

### Flujo típico día a día

```markdown
## Nuevo requerimiento
> /sdd-new "Agregar historial de órdenes"
> /sdd-clarify 003
> /sdd-plan 003
> /sdd-tasks 003
> Usuario revisa y aprueba specs/003-*/tasks.md

## Desarrollo
> /sdd-run 003 ola 1
# coordinator lanza un implementer por lane (paralelo, rutas disjuntas)
# verifica él mismo: compileJava + test
> /sdd-check 003 ola 1
# reviewer audita la ola completa y emite veredicto
# Si ❌ → /sdd-run 003 "fix hallazgos del review" → /sdd-check 003 ola 1
# Si ✅ → /sdd-run 003 ola 2

## Cierre
> /sdd-run 003 buffer
> /sdd-check 003 final
# coordinator guarda review.md y pasa el estado a done
```

Cada `/sdd-*` corre en una sesión hija cuando el agente es `subagent`, así que no arrastra el
contexto de la conversación: el prompt del comando tiene que ser autosuficiente.

---

## Estado Persistente

### Global — `specs/state.json`

```json
{
  "activeRequirement": "003-order-history-endpoint",
  "requirements": [
    { "id": "000", "folder": "000-baseline-challenge", "status": "done" },
    { "id": "003", "folder": "003-order-history-endpoint", "status": "in-progress" }
  ],
  "lastUpdated": "2026-11-12"
}
```

### Por requerimiento — `specs/NNN-slug/state.json`

```json
{
  "requirement": "003",
  "folder": "003-order-history-endpoint",
  "status": "in-progress",
  "currentPhase": 1,
  "currentWave": "W2",
  "currentTask": "R003-T04",
  "completedTasks": ["R003-T01", "R003-T02"],
  "blockedTasks": [],
  "pendingDecisions": [],
  "lastReview": {
    "verdict": "approved",
    "date": "2026-11-12",
    "notes": "Criterios CA-01..CA-03 verificados"
  }
}
```

> `coordinator` actualiza ambos tras cada acción. Si no existen, no hay memoria entre sesiones: el
> flujo vuelve a empezar desde `/sdd-status`.

---

## Referencia Rápida

| Comando | Agente | Dónde corre | Artefacto que produce |
|---------|--------|-------------|------------------------|
| `/sdd-new` | `coordinator` | sesión actual | `specs/NNN-slug/spec.md` + índice |
| `/sdd-spec` | `planner` | sesión hija | `specs/NNN-slug/spec.md` |
| `/sdd-clarify` | `coordinator` | sesión actual | spec + ADR global |
| `/sdd-plan` | `planner` | sesión hija | `specs/NNN-slug/plan.md` |
| `/sdd-tasks` | `planner` | sesión hija | `specs/NNN-slug/tasks.md` |
| `/sdd-run` | `coordinator` → N `implementer` en paralelo | sesión actual + hijas | código + tests + `[x]` |
| `/sdd-check` | `reviewer` | sesión hija (sin edición) | veredicto por ola → `review.md` |
| `/sdd-status` | `coordinator` | sesión actual | reporte |
| `/sdd` | `coordinator` | sesión actual, delega | flujo completo |

---

## Notas para el Equipo Cocos

Este flujo SDD garantiza:

1. **Aislamiento por requerimiento** = cada cambio tiene su spec, plan, tasks y revisión.
2. **Historial** = los requerimientos cerrados no se reescriben; se supersede con uno nuevo.
3. **Calidad acotada** = el reviewer audita un alcance concreto, no "todo el proyecto".
4. **Trazabilidad** = `RNNN-TXX` en tareas, commits y reportes; ADR global para decisiones.
5. **Entrega incremental** = cada fase tiene DoD y veredicto antes de avanzar.
