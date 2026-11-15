# Specs — Índice de requerimientos

Un **requerimiento = una carpeta numerada**. Cada carpeta contiene su propio `spec.md`,
`plan.md`, `tasks.md`, `state.json` y `review.md`. Nada de un `spec.md` global que mezcle todo:
el requerimiento es la unidad de cambio, de planificación y de revisión.

## Requerimientos

| # | Carpeta | Título | Tipo | Estado | Activo | Última revisión |
| :--- | :--- | :--- | :--- | :--- | :---: | :--- |
| 000 | [`000-baseline-challenge/`](000-baseline-challenge/spec.md) | Backend challenge Cocos (base implementada) | baseline | ✅ done | — | 2026-11-12 · ✅ aprobado |
| 001 | [`001-remove-unused-imports/`](001-remove-unused-imports/spec.md) | Remover imports no utilizados del proyecto | refactor | ✅ done | — | 2026-11-13 · ✅ aprobado |
| 002 | [`002-fix-ci-secrets-if/`](002-fix-ci-secrets-if/spec.md) | Arreglar workflow CI: `secrets` inválido en `if` | bugfix | ✅ done | — | 2026-11-13 · ✅ aprobado |
| 003 | [`003-fix-gradlew-exec-bit/`](003-fix-gradlew-exec-bit/spec.md) | Arreglar CI: `gradlew` sin bit de ejecutable | bugfix | ✅ done | — | 2026-11-13 · ✅ aprobado |
| 004 | [`004-fix-market-order-price-drift/`](004-fix-market-order-price-drift/spec.md) | Prevenir descalce de precio entre validación y ejecución en órdenes MARKET | bugfix | ✅ done | — | 2026-11-14 · ✅ aprobado |
| 005 | [`005-fix-balance-validation-race/`](005-fix-balance-validation-race/spec.md) | Eliminar race condition en la validación de saldo (lock pesimista por usuario) | bugfix | ✅ done | — | 2026-11-14 · ✅ aprobado |
| 006 | [`006-fix-filled-price-semantics/`](006-fix-filled-price-semantics/spec.md) | Separar precio límite de precio de ejecución (`Order.filled`) y desacoplar el portfolio | bugfix | ✅ done | — | 2026-11-14 · ✅ aprobado |
| 007 | [`007-fix-findlatestprices-query/`](007-fix-findlatestprices-query/spec.md) | Fix findLatestPrices sin LIMIT: query trae todas las filas históricas | bugfix | ✅ done | — | 2026-11-14 · ✅ aprobado |
| 008 | [`008-fix-idempotency-race-condition/`](008-fix-idempotency-race-condition/spec.md) | Arreglar la race condition de idempotencia (misma key, distinto user) | bugfix | ✅ done | — | 2026-11-15 · ✅ aprobado |
| 009 | [`009-remove-extra-query-save/`](009-remove-extra-query-save/spec.md) | Eliminar query extra en `PostgresOrderRepository.save()` | refactor | ✅ done | — | 2026-11-15 · ✅ aprobado |
| 010 | [`010-domain-refinements-and-defensive-validation/`](010-domain-refinements-and-defensive-validation/spec.md) | Domain refinements & defensive validation | refactor | ✅ done | — | 2026-11-15 · ✅ aprobado |

> Próximo número libre: **011**. Se asigna con `/sdd-new`, no a mano.
> El baseline `000` conserva los IDs legacy `T-XX`; los requerimientos nuevos usan `RNNN-TXX`.

> No hay requerimiento **activo** (figura en [`state.json`](state.json)).

## Estructura de una carpeta

```text
specs/NNN-slug/
├── spec.md       # qué se pide: alcance, reglas, criterios de aceptación, contratos
├── plan.md       # cómo se hace: fases, dependencias, olas paralelizables, riesgos, DoD
├── tasks.md      # checklist ejecutable: RNNN-T01… con ola: WN y toca: <rutas>
├── state.json    # fase/ola/tarea actual, completadas, bloqueos, último veredicto
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
/sdd-plan NNN      → genera plan.md (con olas de ejecución)
/sdd-tasks NNN     → genera tasks.md (con ola: y toca: por tarea)
/sdd-run NNN       → implementa por olas (un implementer por lane)
/sdd-check NNN     → audita la ola
```
