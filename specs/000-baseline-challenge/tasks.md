# Tasks Checklist — Cocos Challenge (baseline 000)

> Formato: `- [x] T-XX — Descripción | Fase N | depende de: T-YY | ~Xh | done: <criterio>`  
> Derivado de `specs/000-baseline-challenge/plan.md` el 2026-11-12. Las tareas marcadas con `[x]` ya están verificadas en el repo.
> **IDs legacy**: el baseline conserva `T-XX`. Los requerimientos nuevos usan `RNNN-TXX`.

## Fase 0 — Setup
- [x] T-00 — Init Spring Boot 3.3.4 + Java 21 + Gradle Kotlin DSL | Fase 0 | - | ~0.5h | done: `./gradlew compileJava`
- [x] T-01 — docker-compose.yml (PG local, Prometheus, Grafana) | Fase 0 | depende de: T-00 | ~1h | done: `docker compose config`
- [x] T-02 — build.gradle.kts con dependencias core | Fase 0 | depende de: T-00 | ~0.5h | done: `./gradlew dependencies`
- [x] T-03 — Estructura hexagonal de paquetes | Fase 0 | depende de: T-00 | ~0.5h | done: directorio `src/main/java`
- [x] T-04 — Config BD + Flyway (schema + seed) | Fase 0 | depende de: T-01 | ~0.5h | done: `db/migration/V1__initial_schema.sql` y `db/seed/V2__dev_seed.sql`

## Fase 1 — Core Challenge
- [x] T-10 — Domain models (records, BigDecimal) | Fase 1 | depende de: T-03 | ~1h | done: `domain/model` sin setters
- [x] T-11 — Interfaces de puertos in/out | Fase 1 | depende de: T-10 | ~1h | done: `domain/port` sin Spring
- [x] T-12 — Domain services + unit tests (cobertura ≥ 80%) | Fase 1 | depende de: T-10, T-11 | ~3h | done: `./gradlew jacocoTestCoverageVerification` (100/100/97.7%)
- [x] T-13 — Entidades JPA + repos | Fase 1 | depende de: T-04, T-10 | ~1.5h | done: `infrastructure/adapter/out/persistence`
- [x] T-14 — Adapters out (Postgres repos, marketdata) | Fase 1 | depende de: T-11, T-13 | ~2h | done: `./gradlew test`
- [x] T-15 — Implementación de use cases | Fase 1 | depende de: T-11, T-12 | ~2h | done: `application/usecase`
- [x] T-16 — Controllers + DTOs + OpenAPI + GlobalExceptionHandler | Fase 1 | depende de: T-15 | ~1.5h | done: `/swagger-ui.html`
- [x] T-17 — Idempotencia en PlaceOrder (tabla + hash + TTL 24h) | Fase 1 | depende de: T-14, T-15 | ~1h | done: `PostgresIdempotencyStore` y `PlaceOrderUseCase`
- [x] T-18 — Integration test PlaceOrderFlowIT con Testcontainers | Fase 1 | depende de: T-17 | ~2h | done: `./gradlew test --tests "*PlaceOrderFlowIT*"`

## Fase 2 — Resilience & Observability
- [x] T-20 — Configuración Resilience4j (CB, Retry, Bulkhead) | Fase 2 | depende de: T-14 | ~1.5h | done: `Resilience4jConfig` y `application.yml`
- [x] T-21 — Micrometer + Prometheus + Grafana | Fase 2 | depende de: T-01 | ~1.5h | done: `/actuator/prometheus`
- [x] T-22 — Health/Readiness endpoints + DatabaseHealthIndicator | Fase 2 | depende de: T-21 | ~0.5h | done: `/actuator/health/readiness`
- [x] T-23 — Logging estructurado con correlation ID | Fase 2 | depende de: T-16 | ~0.5h | done: `logging.pattern.console` en `application.yml`
- [x] T-24 — Cache Caffeine en búsqueda y precios | Fase 2 | depende de: T-14, T-16 | ~1h | done: `CacheConfig` y `@Cacheable`
- [x] T-25 — Tests de resiliencia (CB open) | Fase 2 | depende de: T-20 | ~1h | done: `Resilience4jCircuitBreakerTest`
- [x] T-26 — Colección + environment de Postman | Fase 2 | depende de: T-16 | ~1h | done: `docs/postman/*.json`

## Fase 3 — Stretch Goal (Event-Driven & MCP)
- [x] T-30 — Domain events (OrderPlaced, OrderFilled, OrderRejected, OrderCancelled) | Fase 3 | depende de: T-10 | ~0.5h | done: records en `domain/model`
- [x] T-31 — Event publisher + 3 listeners @Async AFTER_COMMIT | Fase 3 | depende de: T-30 | ~2h | done: `SpringEventPublisher`, `FraudEventListener`, `LedgerEventListener`, `NotificationEventListener`
- [ ] T-32 — MCP Server config + tools (getPortfolio, placeOrder) | Fase 3 | depende de: T-15 | ~3h | done: MCP funcional (hoy desactivado en `MCPConfig` por falta de dep en Maven Central)
- [x] T-33 — Tests de eventos de dominio | Fase 3 | depende de: T-31 | ~2h | done: `./gradlew test` (ejercita la emisión en `PlaceOrderUseCase`)
- [x] T-34 — Documentación de eventos en ADR-005 | Fase 3 | depende de: T-33 | ~1h | done: `docs/adr/005-event-driven.md`

## Fase 4 — Entrega y Harness OpenCode V2
- [x] T-40 — README + ADRs 001–005 | Fase 4 | depende de: T-18 | ~2h | done: `README.md` y `docs/adr/`
- [x] T-41 — Dockerfile multi-stage + GitHub Actions CI | Fase 4 | depende de: T-01 | ~1.5h | done: `Dockerfile` y `.github/workflows/ci.yml`
- [x] T-44 — Harness OpenCode V2: `.opencode/agents/` y `.opencode/commands/` nativos | Fase 4 | - | ~1h | done: los 4 agentes y 8 comandos en formato V2
- [x] T-45 — `opencode.json` con `default_agent: coordinator` y modelo `freellmapi/auto` | Fase 4 | depende de: T-44 | ~0.25h | done: `opencode.json`
- [x] T-46 — Separación de artefactos SDD (`spec.md`, `plan.md`, `tasks.md`, `state.json`) | Fase 4 | depende de: T-44 | ~1h | done: `specs/`
- [x] T-47 — CI secret-safe: hacer `docker-build` condicional a la presencia de secrets o variable de repo | Fase 4 | depende de: T-41 | ~0.5h | done: CI pasa en repo fork sin secrets
- [ ] T-42 — Demo local + ejecución end-to-end de Postman collection | Fase 4 | depende de: T-26, T-41 | ~1h | done: capturas de pantalla / logs de corrida exitosa
- [ ] T-43 — Inicialización de repo Git local y tag v1.0.0 | Fase 4 | depende de: T-40 | ~0.5h | done: `git tag v1.0.0`

## Buffer
- [ ] T-90 — Probar conexión real a Neon PostgreSQL en profile prod | Buffer | - | ~0.5h | done: `SPRING_PROFILES_ACTIVE=prod` con credenciales de env
- [ ] T-91 — Integración de Spring AI MCP Server cuando se publique la dependencia estable | Buffer | depende de: T-32 | ~1h | done: `MCPConfig` desmarcado y compilando
