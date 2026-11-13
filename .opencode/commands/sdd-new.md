---
description: Crea un requerimiento nuevo en specs/NNN-slug con spec draft, índice y puntero
agent: coordinator
---

Crea un **requerimiento nuevo numerado**.

Descripción del requerimiento: $ARGUMENTS

Protocolo:
1. Lee `specs/README.md` y `specs/state.json`.
2. Calcula `NNN` = mayor `id` existente + 1, con 3 dígitos (000 es baseline; el primero nuevo es
   001). No reutilices números ni borres carpetas: un requerimiento descartado queda `cancelled`.
3. Deriva un slug corto en minúsculas con guiones (3–5 palabras, sin acentos).
4. Si la descripción no alcanza para escribir contexto y 2–3 criterios de aceptación, hacé como
   máximo 3 preguntas con `question`. Si alcanza, no preguntes.
5. Crea `specs/NNN-slug/` copiando `specs/_templates/spec.md`, `plan.md`, `tasks.md`, `review.md` y
   `state.json`. Reemplazá `NNN`, `<slug>`, `<título>`, fechas y estado `draft`.
6. Escribí el `spec.md` con contexto, alcance (dentro/fuera) y criterios de aceptación en formato
   Given/When/Then. Lo que no sepas va a `SUPUESTO:` o `PENDIENTE:`; no inventes reglas de negocio.
7. Actualizá `specs/README.md` (nueva fila en la tabla) y `specs/state.json`
   (`activeRequirement` = la carpeta nueva).
8. No escribas `plan.md` ni `tasks.md` todavía: eso es `/sdd-plan` y `/sdd-tasks`.
9. Si el requerimiento reemplaza a otro, anotá `Supersede a: NNN` en su spec y no edites la carpeta
   vieja.

Cierra con: número asignado, carpeta creada, ruta del spec, supuestos/pendientes y próximo comando.
