# Revisión — 000 Backend challenge Cocos (baseline)

| Campo | Valor |
| :--- | :--- |
| **Fecha** | 2026-11-12 |
| **Alcance auditado** | `T-00..T-47` (fases 0–4) |
| **Veredicto** | ✅ Aprobado |
| **Auditor** | `reviewer` |

## Resumen

Se auditó la base del challenge contra el spec del baseline y las invariantes del proyecto. La
suite está verde (35 tests: 34 unit + 1 integración con Testcontainers), la cobertura de los
servicios de dominio es 100 / 100 / 97.7 % y Resilience4j y Caffeine son demostrables. El único
stretch no implementado es el MCP server, desactivado a propósito por falta de dependencia estable.

## Checklist

| Categoría | Estado | Hallazgos |
| :--- | :--- | :--- |
| Cumplimiento del spec | ✅ | 3 endpoints core + cancelación funcionando |
| Arquitectura hexagonal | ✅ | `domain/` sin imports de Spring/JPA |
| Transacciones / concurrencia | ✅ | `@Transactional` + lock pesimista en validación |
| Tests y cobertura | ✅ | 35 verdes; gate ≥ 80 % en dominio |
| Seguridad | ✅ | Sin credenciales versionadas; variables de entorno |

## Hallazgos bloqueantes

Ninguno.

## Hallazgos menores

1. El spec original tenía una connection string de Neon con password en texto plano; la clave debe
   rotarse en Neon (ver `pendingDecisions` en `state.json`).
2. El MCP server queda fuera de alcance hasta que `spring-ai-mcp-server` esté en Maven Central
   estable.

## Próximos pasos

- [ ] Cerrar la entrega: demo end-to-end, repo Git + tag `v1.0.0` y rotación de la clave de Neon.
- [ ] Todo trabajo nuevo se crea como requerimiento `NNN` con `/sdd-new`.
