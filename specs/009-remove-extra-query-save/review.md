# Revisión — 009 Eliminar query extra en PostgresOrderRepository.save() [✅ Aprobado]

## Resumen
El refactor elimina correctamente la query extra `findById()` en `PostgresOrderRepository.save()`. El cambio simplifica el método a una sola línea que usa `PersistenceMapper.toEntity(order)` + `jpaRepository.save(entity)`, aprovechando el comportamiento nativo de JPA/Hibernate para INSERT (id=null) y MERGE/UPDATE (id!=null). Todos los criterios de aceptación (CA-01, CA-02) verificados por la suite completa de tests (unitarios + integración).

## Checklist
| Categoría | Estado | Hallazgos |
|-----------|--------|-----------|
| Cumplimiento del spec | ✅ | CA-01 y CA-02 cubiertos; alcance respetado (solo `save()`, sin tocar otros métodos). |
| Invariantes | ✅ | Sin roturas de baseline. |
| Hexagonal | ✅ | `domain/` sigue sin imports de Spring/JPA; adapter en `infrastructure/adapter/out/persistence`. |
| Transacciones/Concurrencia | ✅ | El cambio es interno al adapter; los use cases siguen con `@Transactional` y locks pesimistas donde corresponde. |
| Tests | ✅ | `./gradlew test` y `./gradlew check` verdes; cobertura ≥ 80 % verificada (`jacocoTestCoverageVerification`). |
| Seguridad | ✅ | Cero credenciales hardcodeadas; validaciones de entrada intactas. |

## Hallazgos Críticos (Bloqueantes)
_Ninguno._

## Hallazgos Menores (No bloqueantes)
_Ninguno._ La implementación es limpia y cumple exactamente con lo especificado.

## Próximos Pasos
- [x] reviewer: emitir ✅ en `review.md`
- [ ] coordinator: actualizar `state.json` y cerrar el requerimiento