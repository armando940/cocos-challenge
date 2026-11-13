# Plan de Ejecución — Cocos Challenge

> Derivado del repo real el 2026-11-12. Las tareas ya implementadas se listan igual, en estado ✅,
> para que el plan refleje la realidad y no una intención. Fuente de verdad del alcance: [[spec]].

## Resumen

- **Fases**: 5 (0-Setup, 1-Core, 2-Resiliencia, 3-Stretch, 4-Entrega y harness)
- **Estado global**: fases 0, 1 y 2 completas · fase 3 parcial (eventos sí, MCP no) · fase 4 en curso
- **Camino crítico restante**: T-43 (repo Git + release) → T-42 (demo/Postman end-to-end)
- **Riesgo mayor**: T-32 (MCP) — la dependencia `spring-ai-mcp-server` no está en Maven Central estable;
  ya está desactivada a propósito y no debe contarse como implementada

## Estado actual vs plan

| Fase | Objetivo | Estado | Evidencia |
| :--- | :--- | :--- | :--- |
| 0 — Setup | Proyecto Spring Boot ejecutable con BD y Flyway | ✅ Completa | `build.gradle.kts`, `docker-compose.yml`, `db/migration/V1__initial_schema.sql`, `db/seed/V2__dev_seed.sql` |
| 1 — Core | 3 endpoints con hexagonal, idempotencia y tests | ✅ Completa | 35 tests verdes, `PlaceOrderFlowIT`, cobertura 100/100/97.7 % |
| 2 — Resiliencia | Resilience4j, métricas, cache, health | ✅ Completa | `application.yml` (resilience4j), `CacheConfig`, `DatabaseHealthIndicator`, `Resilience4jCircuitBreakerTest` |
| 3 — Stretch | Eventos async + MCP | 🟡 Parcial | Listeners `@Async` + `@TransactionalEventListener` presentes; MCP comentado en `MCPConfig` y sin dependencia |
| 4 — Entrega | Docs, CI, harness y demo | 🟡 En curso | `docs/`, `README.md`, `ci.yml`, `.opencode/agents/`, `opencode.json`, `specs/` |

---

## Fase 0 — Setup (~3 h)

**Objetivo**: repo compilable con BD local y migraciones.
**Entregable**: esqueleto Spring Boot 3.3.4 + Java 21 + Gradle Kotlin DSL.
**DoD**: `./gradlew compileJava` verde y `docker compose up -d postgres` levanta la BD.

| ID | Tarea | Depende de | Estimación | Riesgo | Done |
| :--- | :--- | :--- | :--- | :--- | :--- |
| T-00 | Init Spring Boot 3.3.4 + Java 21 + Gradle Kotlin DSL | — | 0.5 h | Bajo | `./gradlew compileJava` |
| T-01 | `docker-compose.yml` con PG, Prometheus, Grafana y app | T-00 | 1 h | Bajo | `docker compose config` |
| T-02 | Dependencias core en `build.gradle.kts` (JPA, Web, Validation, OpenAPI, Testcontainers, Resilience4j, Caffeine) | T-00 | 0.5 h | Bajo | `./gradlew dependencies` sin conflictos |
| T-03 | Estructura hexagonal (`domain`, `application`, `infrastructure`, `presentation`) | T-00 | 0.5 h | Bajo | Paquetes existen en `src/main/java` |
| T-04 | `application.yml` local/prod + Flyway (schema + seed) | T-01 | 0.5 h | Medio | `./gradlew flywayInfo` o app arranca contra PG local |

## Fase 1 — Core del challenge (~14 h)

**Objetivo**: 3 endpoints con reglas de negocio, idempotencia y tests.
**Entregable**: API funcional contra PG/Neon.
**DoD**: `./gradlew check` verde con cobertura ≥ 80 % en los 3 servicios de dominio.

| ID | Tarea | Depende de | Estimación | Riesgo | Done |
| :--- | :--- | :--- | :--- | :--- | :--- |
| T-10 | Modelos de dominio (`Money`, `Instrument`, `Order`, `Portfolio`, `Position`, `IdempotencyKey`) | T-03 | 1 h | Bajo | Records sin setters, `BigDecimal` |
| T-11 | Puertos `in`/`out` sin dependencias de Spring | T-10 | 1 h | Bajo | `grep -r "import org.springframework" domain/` vacío |
| T-12 | `PortfolioCalculator`, `OrderMatcher`, `BalanceValidator` + unit tests | T-10, T-11 | 3 h | **Alto** | `./gradlew jacocoTestCoverageVerification` |
| T-13 | Entidades JPA + Spring Data repos | T-04, T-10 | 1.5 h | Bajo | `ddl-auto: validate` arranca |
| T-14 | Adapters `out`: instrumentos, órdenes, market data, idempotencia | T-11, T-13 | 2 h | Bajo | `./gradlew test` |
| T-15 | Use cases: portfolio, búsqueda, órdenes, cancelación | T-11, T-12 | 2 h | Medio | `./gradlew test` |
| T-16 | Controllers, DTOs, OpenAPI y `GlobalExceptionHandler` | T-15 | 1.5 h | Bajo | `/swagger-ui.html` responde y los 3 endpoints documentados |
| T-17 | Idempotencia en `PlaceOrder` (tabla + hash + TTL 24 h + 409 en conflicto) | T-14, T-15 | 1 h | **Alto** | Test de reintento idéntico y de conflicto |
| T-18 | `PlaceOrderFlowIT` con Testcontainers (MARKET, LIMIT, rechazos, idempotencia, cancelación) | T-17 | 2 h | Medio | `./gradlew test --tests "*PlaceOrderFlowIT*"` |

## Fase 2 — Resiliencia y observabilidad (~8 h)

**Objetivo**: el sistema no se cae si falla el market data y es observable.
**Entregable**: Resilience4j + Micrometer/Prometheus + Caffeine + health.
**DoD**: `/actuator/prometheus` expone métricas de HTTP, cache y circuit breaker.

| ID | Tarea | Depende de | Estimación | Riesgo | Done |
| :--- | :--- | :--- | :--- | :--- | :--- |
| T-20 | CircuitBreaker + Retry + Bulkhead sobre `MarketDataProvider` (TimeLimiter solo configurado, ver ADR-003) | T-14 | 1.5 h | Medio | `Resilience4jCircuitBreakerTest` |
| T-21 | Micrometer + Prometheus + Grafana | T-01 | 1.5 h | Bajo | `curl /actuator/prometheus` |
| T-22 | Health/readiness + `DatabaseHealthIndicator` | T-21 | 0.5 h | Bajo | `/actuator/health/readiness` |
| T-23 | Logging estructurado + correlation ID | T-16 | 0.5 h | Bajo | Log con `traceId`; hoy logging plano (Boot 3.3.4 no soporta `ecs`) |
| T-24 | Cache Caffeine con TTL por cache y métricas | T-14, T-16 | 1 h | Bajo | `/actuator/caches` + `cache.gets` |
| T-25 | Test de resiliencia (fallo simulado abre el CB) | T-20 | 1 h | Medio | Test verde |
| T-26 | Colección + environment de Postman | T-16 | 1 h | Bajo | `docs/postman/*.json` importables |

## Fase 3 — Stretch (~10 h)

**Objetivo**: demostrar criterio de arquitectura más allá del mínimo.
**Entregable**: eventos de dominio async (in-process) y, si la dependencia lo permite, MCP.
**DoD**: listeners ejecutados tras commit y documentados en ADR-005.

| ID | Tarea | Depende de | Estimación | Riesgo | Done |
| :--- | :--- | :--- | :--- | :--- | :--- |
| T-30 | Eventos de dominio `OrderPlaced/Filled/Rejected/Cancelled` | T-10 | 0.5 h | Bajo | Records compilando en `domain/model` |
| T-31 | `SpringEventPublisher` + 3 listeners `@Async` `AFTER_COMMIT` | T-30 | 2 h | Medio | `AsyncConfig` con pool nombrado |
| T-32 | MCP server (Spring AI) con tools `getPortfolio` y `placeOrder` | T-15 | 3 h | **Alto** | **Bloqueado**: `spring-ai-mcp-server` no está en Maven Central estable |
| T-33 | Tests de eventos (y de MCP si T-32 avanza) | T-31, T-32 | 2 h | Medio | Test que verifica el listener tras commit |
| T-34 | Documentación de eventos y MCP | T-33 | 1 h | Bajo | `docs/adr/005-event-driven.md` (+ sección MCP) |

## Fase 4 — Entrega y harness (~8 h)

**Objetivo**: repo presentable para el evaluador y flujo SDD operativo.
**Entregable**: docs, CI sin dependencias de secrets, harness V2 funcionando.
**DoD**: `/sdd-status` devuelve estado coherente con el repo y el CI no falla por falta de secrets.

| ID | Tarea | Depende de | Estimación | Riesgo | Done |
| :--- | :--- | :--- | :--- | :--- | :--- |
| T-40 | README (arquitectura, cómo correr, tests, cobertura) + ADRs 001–005 | T-18 | 2 h | Bajo | Archivos en `README.md` y `docs/adr/` |
| T-41 | `Dockerfile` multi-stage + GitHub Actions CI | T-01 | 1.5 h | Bajo | `docker build .` y `ci.yml` válido |
| T-42 | Demo de 3 min y corrida de la colección Postman contra la app | T-26, T-41 | 1 h | Bajo | Capturas + colección ejecutada en local |
| T-43 | Repo Git + tag de release | T-40, T-41 | 0.5 h | Bajo | `git tag v1.0.0` |
| T-44 | Harness V2: layout `agents/` + `commands/`, frontmatter nativo, permisos | — | 1 h | Bajo | `opencode.json` cargado, `/sdd-status` disponible |
| T-45 | `opencode.json` con `default_agent: coordinator` y modelo barato para `reviewer` | T-44 | 0.25 h | Bajo | Archivo válido contra el schema |
| T-46 | Separar `spec.md`, `plan.md`, `tasks.md` y `state.json` | T-44 | 1 h | Bajo | Los 4 archivos existen y no se contradicen |
| T-47 | CI: publicar en Docker Hub solo si hay secrets (gate por variable de repo) | T-41 | 0.5 h | Bajo | Job `docker-build` no corre sin `ENABLE_DOCKER_PUBLISH` |
| T-48 | Contract tests OpenAPI en `src/test/.../contract` | T-16 | 1.5 h | Medio | `./gradlew test` con el paquete `contract` |

---

## Camino crítico

```text
T-43 (Git + release) → T-42 (demo end-to-end)
                     ↘ T-48 (contract tests, opcional)
```

El resto de la fase 4 ya está hecho o no bloquea la entrega.

## Riesgos y mitigación

| Riesgo | Probabilidad | Impacto | Mitigación |
| :--- | :--- | :--- | :--- |
| MCP no compila por dependencia no publicada | Alta | Bajo (stretch) | Se deja desactivado y documentado en ADR-005 + README; no se presenta como implementado |
| Rotación pendiente de la clave de Neon | Media | **Alto** | Rotar en Neon y usar solo `SPRING_DATASOURCE_*`; no versionar connection strings |
| CI rompe por `docker-build` sin secrets | Alta | Medio | T-47: gate por variable de repositorio |
| Tests de integración dependen de Docker | Media | Medio | Documentado en README y `AGENTS.md`; `api.version=1.44` fijado para Docker 29 |
| Cobertura se degrada al agregar código | Media | Medio | `jacocoTestCoverageVerification` enganchado a `check` |
