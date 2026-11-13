# Specs — Índice de requerimientos

Un **requerimiento = una carpeta numerada**. Cada carpeta contiene su propio `spec.md`,
`plan.md`, `tasks.md`, `state.json` y `review.md`. Nada de un `spec.md` global que mezcle todo:
el requerimiento es la unidad de cambio, de planificación y de revisión.

## Requerimientos

| # | Carpeta | Título | Tipo | Estado | Activo | Última revisión |
| :--- | :--- | :--- | :--- | :--- | :---: | :--- |
| 000 | [`000-baseline-challenge/`](000-baseline-challenge/spec.md) | Backend challenge Cocos (base implementada) | baseline | ✅ done | — | 2026-11-12 · ✅ aprobado |
| 001 | [`001-remove-unused-imports/`](001-remove-unused-imports/spec.md) | Remover imports no utilizados del proyecto | refactor | ✅ done | ✅ | 2026-11-13 · ✅ aprobado |

> Próximo número libre: **002**. Se asigna con `/sdd-new`, no a mano.
> El baseline `000` conserva los IDs legacy `T-XX`; los requerimientos nuevos usan `RNNN-TXX`.

> El requerimiento **activo** es el que figura en [`state.json`](state.json) (`activeRequirement`).
> Solo puede haber uno activo a la vez; el resto queda `paused` hasta que se retome.
> Hoy no hay ninguno activo: el baseline está cerrado. El próximo se crea con `/sdd-new`.

## Estructura de una carpeta

```text
specs/NNN-slug/
├── spec.md       # qué se pide: alcance, reglas, criterios de aceptación, contratos
├── plan.md       # cómo se hace: fases, dependencias, riesgos, DoD
├── tasks.md      # checklist ejecutable: RNNN-T01, RNNN-T02…
├── state.json    # fase/tarea actual, completadas, bloqueos, último veredicto
└── review.md     # veredicto del reviewer y hallazgos (lo escribe el coordinator)
```

Plantillas en [`_templates/spec.md`](_templates/spec.md).

## Estados del requerimiento

| Estado | Significado |
| :--- | :--- |
| `draft` | Existe el spec, todavía sin clarificar ni planificar. |
| `clarified` | Ambigüedades resueltas con el usuario. |
| `planned` | Existe `plan.md` aprobado. |
| `ready` | Existe `tasks.md` con checklist ejecutable. |
| `in-progress` | Hay tareas en ejecución. |
| `in-review` | Implementación terminada, esperando veredicto. |
| `done` | Reviewer aprobó y el DoD está cumplido. |
| `blocked` | Dependencia externa o decisión pendiente impide avanzar. |
| `cancelled` | Descartado; se conserva por trazabilidad. |

## Reglas

1. **La numeración la asigna el coordinator**, nunca a mano. Se toma el mayor número existente + 1.
2. **Un número no se reutiliza** ni se borra: un requerimiento cancelado queda `cancelled`.
3. **Un solo requerimiento activo.** Para trabajar en otro, se cambia `activeRequirement`.
4. **Un requerimiento cerrado no se edita.** Si hay que cambiarlo, se crea uno nuevo con
   `Supersede a: NNN` en el spec.
5. **Lo global no se duplica.** Las invariantes del proyecto viven en `AGENTS.md` y en el
   baseline `000`; cada spec nuevo solo documenta su delta y las referencia.
6. **Los ADR son globales** (`docs/adr/`). Un requerimiento puede crear un ADR si cambia una
   decisión de arquitectura, pero no tiene ADRs propios.
7. Los requerimientos son unidades **documentales y de cambio**, no módulos de código: varios
   pueden tocar los mismos archivos. El código sigue organizado por arquitectura hexagonal.

## Crear un requerimiento nuevo

```text
/sdd-new "Agregar endpoint de historial de órdenes por usuario"
```

El coordinator crea `specs/NNN-slug/` desde las plantillas, escribe el `spec.md` en `draft`,
actualiza este índice y mueve el puntero de `state.json`. Después:

```text
/sdd-clarify NNN   → resuelve ambigüedades
/sdd-plan NNN      → genera plan.md
/sdd-tasks NNN     → genera tasks.md
/sdd-run NNN       → implementa
/sdd-check NNN     → audita
```
