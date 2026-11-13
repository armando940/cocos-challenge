# AGENTS.md — cocos-challenge

Backend challenge de Cocos Capital: API REST de brokerage (portfolio, búsqueda de instrumentos,
órdenes MARKET/LIMIT, cancelación) con arquitectura hexagonal, idempotencia transaccional,
resiliencia y observabilidad. Java 21 + Spring Boot 3.3 + Gradle Kotlin DSL + PostgreSQL.

## Comandos

```bash
./gradlew compileJava          # compilar
./gradlew test                 # unit + integration (incluye **/*IT.class por config)
./gradlew check                # test + jacocoTestCoverageVerification (≥80% en domain services)
docker compose up -d postgres  # solo BD para correr local
docker compose up --build -d   # stack completo (app, Prometheus, Grafana)
./gradlew bootRun              # requiere profile local y Postgres arriba
```

Notas del entorno:
- `./gradlew test` necesita Docker corriendo (Testcontainers). En Windows el build ya fuerza
  `DOCKER_HOST=npipe:////./pipe/dockerDesktopLinuxEngine`.
- La cobertura se verifica en `check`, no en `test`.
- `SPRING_PROFILES_ACTIVE=local` usa `db/seed`; `prod` solo `db/migration`.

## Invariantes del proyecto

1. **Hexagonal**: `domain/` no importa Spring, Jakarta persistence ni nada de infraestructura.
   Puertos en `domain/port/in` y `domain/port/out`; adapters en `infrastructure/adapter/...`.
2. **Dinero**: siempre `BigDecimal` (scale 2, `HALF_UP`). Nunca `double`/`float`.
3. **Inmutabilidad**: records para modelos y DTOs, sin setters, `List.copyOf()` en colecciones.
4. **Transacciones**: `@Transactional` en use cases de escritura, `readOnly = true` en queries.
5. **Concurrencia**: lock pesimista al validar saldo/tenencia.
6. **Idempotencia**: tabla `idempotency_keys` con `UNIQUE(key)` + hash del request en `PlaceOrder`.
7. **Errores**: excepciones de dominio → `ProblemDetail` (RFC 7807), nunca stacktraces al cliente.
8. **Secretos**: cero credenciales en el repo; variables de entorno (`.env` no se commitea).

## Trampas conocidas

- El MCP (`presentation/mcp`, `infrastructure/config/MCPConfig.java`) está **comentado** y la
  dependencia `spring-ai-*` no está en `build.gradle.kts`. No lo cuentes como implementado.
- `build.gradle.kts` fija versiones de `prometheus-metrics-*` a propósito: no las "limpies" sin
  reproducir el problema de resolución.
- Hay exclusiones de JAXB también por un conflicto real: no las borres sin correr `./gradlew test`.
- El CI publica en Docker Hub y crea release solo en `main`; requiere secrets configurados.

## Flujo de trabajo

Este repo usa Spec-Driven Development **por requerimiento**. Cada requerimiento vive en su propia
carpeta numerada:

```text
specs/
├── README.md                  # índice de requerimientos
├── state.json                 # puntero global (activeRequirement)
├── _templates/                # plantillas de spec/plan/tasks/review/state
├── 000-baseline-challenge/    # base implementada + invariantes globales
└── NNN-slug/                  # un requerimiento nuevo por carpeta
    ├── spec.md · plan.md · tasks.md · state.json · review.md
```

- Un requerimiento nuevo se crea con `/sdd-new`; el número lo asigna el coordinator.
- Los IDs de tarea son `RNNN-TXX` y solo valen dentro de su carpeta.
- Los ADR siguen siendo globales (`docs/adr/`).
- Las invariantes del proyecto viven en este archivo y en el baseline; **no se duplican** en cada
  spec.

### Ejecución en olas

`tasks.md` declara, por tarea, `ola: WN` y `toca: <rutas>` (las únicas que esa tarea puede editar).
Dos tareas comparten ola solo si no hay dependencia entre ellas, sus rutas son disjuntas y ninguna
crea un símbolo que la otra consuma. Máximo 3 lanes por ola.

- `/sdd-run NNN ola N` lanza un `implementer` por lane, en paralelo, con whitelist de rutas.
- Los implementers en paralelo **no** escriben `tasks.md`/`state.json` y **no** corren Gradle: eso lo
  hace el coordinator al cerrar la ola (`git diff --name-only` + una sola verificación).
- Archivos de frontera (`build.gradle.kts`, `application*.yml`, `db/migration/*`, `ExceptionHandler`)
  serializan: la tarea que los toca va sola. Ante duda de solapamiento, serie.

Ver `SDD_COMMANDS.md` y los comandos `/sdd-*` de `.opencode/commands/`.

Antes de cambiar código: leé la tarea `RNNN-TXX` en `specs/NNN-slug/tasks.md`, el delta en
`specs/NNN-slug/spec.md`, las invariantes en `specs/000-baseline-challenge/spec.md` y el ADR que
aplique. Si el spec y el código se contradicen, no elijas por tu cuenta: reportalo.

---

### ⏳ Próximos pasos Harness (para la siguiente sesión)

1. Validar el flujo por requerimiento end-to-end con un requerimiento real:
   `/sdd-new` → `/sdd-clarify` → `/sdd-plan` → `/sdd-tasks` → `/sdd-run` → `/sdd-check`.
2. CI secrets — configurar `DOCKERHUB_USERNAME/TOKEN` o quitar el job `docker-build`
   (ver `T-47` en `specs/000-baseline-challenge/tasks.md`).
3. Cerrar la entrega del baseline: demo end-to-end (`T-42`) y tag de release (`T-43`).
