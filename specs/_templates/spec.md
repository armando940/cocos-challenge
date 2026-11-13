# Requerimiento NNN — <título>

| Campo | Valor |
| :--- | :--- |
| **Número** | NNN |
| **Carpeta** | `specs/NNN-<slug>/` |
| **Tipo** | feature · bugfix · refactor · spike |
| **Estado** | draft |
| **Creado** | YYYY-MM-DD |
| **Actualizado** | YYYY-MM-DD |
| **Depende de** | — |
| **Supersede a** | — |

> **Alcance de este documento:** solo el **delta** de este requerimiento. Las invariantes del
> proyecto (hexagonal, `BigDecimal`, transacciones, locks, idempotencia, secretos) son globales y
> viven en `AGENTS.md` y en [`../000-baseline-challenge/spec.md`](../000-baseline-challenge/spec.md).
> No las repitas: referencialas. Los ADR aplicables están en `docs/adr/`.

---

## 1. Contexto y motivación

<Qué problema resuelve, quién lo pidió y por qué ahora. 3–6 líneas.>

## 2. Alcance

### Dentro

- <cambio concreto 1>
- <cambio concreto 2>

### Fuera

- <lo que explícitamente no se toca>

## 3. Criterios de aceptación

| ID | Given | When | Then |
| :--- | :--- | :--- | :--- |
| CA-01 | <contexto> | <acción> | <resultado verificable> |
| CA-02 | <contexto> | <acción> | <resultado verificable> |

## 4. Contratos afectados

<Endpoints, requests/responses, códigos de error, eventos o tablas que cambian. Si no cambia
ningún contrato, escribirlo explícitamente.>

```json
// ejemplo de request/response si aplica
```

## 5. Impacto en requerimientos previos

- <compatibilidad, migraciones de datos, cambios de comportamiento>
- <si supersede a otro requerimiento, indicarlo acá>

## 6. NFR específicos

<Solo los que agreguen o ajusten algo respecto de las invariantes globales. Si no hay, escribir
"Sin NFR adicionales: aplican las invariantes de AGENTS.md".>

## 7. Supuestos y pendientes

- **SUPUESTO:** <decisión tomada sin confirmar>
- **PENDIENTE:** <duda que bloquea o requiere respuesta>

## 8. Definition of Done

- [ ] Criterios de aceptación CA-01..CA-N verificados
- [ ] `./gradlew test` verde
- [ ] Cobertura ≥ 80 % en los servicios de dominio tocados
- [ ] Documentación/contratos actualizados (Swagger, README o ADR si aplica)
- [ ] Reviewer emitió ✅ en `review.md`
