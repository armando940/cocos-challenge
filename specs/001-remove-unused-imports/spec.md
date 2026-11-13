# Requerimiento 001 — Remover imports no utilizados del proyecto

| Campo | Valor |
| :--- | :--- |
| **Número** | 001 |
| **Carpeta** | `specs/001-remove-unused-imports/` |
| **Tipo** | refactor |
| **Estado** | clarified |
| **Creado** | 2026-11-13 |
| **Actualizado** | 2026-11-13 |
| **Depende de** | 000 (baseline) |
| **Supersede a** | — |

> **Alcance de este documento:** solo el **delta** de este requerimiento. Las invariantes del
> proyecto (hexagonal, `BigDecimal`, transacciones, locks, idempotencia, secretos) son globales y
> viven en `AGENTS.md` y en [`../000-baseline-challenge/spec.md`](../000-baseline-challenge/spec.md).
> No las repitas: referencialas. Los ADR aplicables están en `docs/adr/`.

---

## 1. Contexto y motivación

El proyecto acumula imports no utilizados en múltiples archivos Java a lo largo de los módulos
domain, application e infrastructure. Estos imports generan ruido, dificultan la lectura y pueden
ocultar dependencias reales. Se solicita limpieza completa para mejorar mantenibilidad y cumplir
estándares de calidad de código.

## 2. Alcance

### Dentro

- Identificar y remover todos los `import` no utilizados en archivos `*.java` del proyecto
- Incluye módulos: `domain`, `application`, `infrastructure`, `presentation` (si existe)
- Verificar que la compilación y tests sigan pasando tras la limpieza

### Fuera

- No se modifican firmas, lógica de negocio ni configuraciones
- No se tocan archivos de configuración (gradle, docker, yaml, properties)
- No se refactoriza código más allá de eliminar imports

## 3. Criterios de aceptación

| ID | Given | When | Then |
| :--- | :--- | :--- | :--- |
| CA-01 | Proyecto con imports no utilizados | Se ejecuta la limpieza | `./gradlew compileJava` compila sin errores |
| CA-02 | Proyecto compilando | Se ejecuta `./gradlew test` | Todos los tests pasan (incluye Testcontainers) |
| CA-03 | Código limpio | Se revisa con IDE/linter | No quedan imports unused en ningún `*.java` |
| CA-04 | Cambios aplicados | Se hace commit | Solo se modifican líneas de `import` en archivos Java |

## 4. Contratos afectados

No cambia ningún contrato: endpoints, requests/responses, códigos de error, eventos o tablas
permanecen idénticos. Es una refactorización interna pura.

## 5. Impacto en requerimientos previos

Compatible con el baseline `000`. No hay migraciones de datos ni cambios de comportamiento.

## 6. NFR específicos

Sin NFR adicionales: aplican las invariantes de `AGENTS.md`.

## 7. Supuestos y pendientes

- **SUPUESTO:** Se usará la funcionalidad "Optimize Imports" de IntelliJ o equivalente automatizado
  (script gradle/spotless) para garantizar exhaustividad.
- **SUPUESTO:** No hay imports que parezcan unused pero sean requeridos por procesamiento de
  anotaciones (ej. `@Autowired` en campos, JPA metamodel). Se validará en CI.
- **PENDIENTE:** Confirmar si se desea configurar Spotless/Checkstyle para evitar regresiones
  futuras (fuera de alcance de este requerimiento).

## 8. Definition of Done

- [ ] Criterios de aceptación CA-01..CA-04 verificados
- [ ] `./gradlew test` verde
- [ ] Cobertura ≥ 80 % en los servicios de dominio tocados (no aplica cambio en lógica)
- [ ] Documentación/contratos actualizados (no aplica)
- [ ] Reviewer emitió ✅ en `review.md`