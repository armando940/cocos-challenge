# Requerimiento 009 — Eliminar query extra en PostgresOrderRepository.save()

| Campo | Valor |
| :--- | :--- |
| **Número** | 009 |
| **Carpeta** | `specs/009-remove-extra-query-save/` |
| **Tipo** | refactor |
| **Estado** | draft |
| **Creado** | 2026-11-15 |
| **Actualizado** | 2026-11-15 |
| **Depende de** | — |
| **Supersede a** | — |

> **Alcance de este documento:** solo el **delta** de este requerimiento. Las invariantes del
> proyecto (hexagonal, `BigDecimal`, transacciones, locks, idempotencia, secretos) son globales y
> viven en `AGENTS.md` y en [`../000-baseline-challenge/spec.md`](../000-baseline-challenge/spec.md).
> No las repitas: referencialas. Los ADR aplicables están en `docs/adr/`.

---

## 1. Contexto y motivación

Actualmente, `PostgresOrderRepository.save()` realiza una consulta explícita `findById` antes de guardar/actualizar una orden.
Esto introduce un round-trip adicional innecesario a la base de datos, ya que Spring Data JPA / Hibernate manejan la detección de entidades nuevas o existentes a través de `save()`.

## 2. Alcance

### Dentro

- Eliminar la llamada a `jpaRepository.findById()` en `PostgresOrderRepository.save()`.
- Simplificar el método `save()` para que use directamente `PersistenceMapper.toEntity()` y `jpaRepository.save()`.

### Fuera

- No se cambia la lógica de negocio de las órdenes.
- No se alteran otros métodos del repositorio.

## 3. Criterios de aceptación

| ID | Given | When | Then |
| :--- | :--- | :--- | :--- |
| CA-01 | Una orden nueva | Se llama a `save()` | La orden se persiste correctamente y se retorna con su ID asignado. |
| CA-02 | Una orden existente | Se llama a `save()` | La orden se actualiza correctamente en la base de datos. |

## 4. Contratos afectados

Ninguno. El comportamiento externo del repositorio se mantiene igual, solo cambia la implementación interna para optimizar el rendimiento.

## 5. Impacto en requerimientos previos

Ninguno. Es un refactor de bajo nivel en la capa de persistencia.

## 6. NFR específicos

Sin NFR adicionales: aplican las invariantes de AGENTS.md.

## 7. Supuestos y pendientes

- **SUPUESTO:** El uso de `PersistenceMapper.toEntity()` seguido de `jpaRepository.save()` es suficiente para cubrir tanto inserciones como actualizaciones, ya que `PersistenceMapper.toEntity()` mapea todos los campos (incluyendo el ID).

## 8. Definition of Done

- [ ] Criterios de aceptación CA-01..CA-02 verificados
- [ ] `./gradlew test` verde
- [ ] Cobertura ≥ 80 % en los servicios de dominio tocados
- [ ] Reviewer emitió ✅ en `review.md`
