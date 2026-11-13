# Cocos Capital - Backend Challenge

Implementación del challenge técnico para Cocos Capital: API REST para sistema de trading/brokerage con arquitectura hexagonal, idempotencia transaccional, resiliencia y observabilidad.

## 🏗️ Arquitectura

```
Hexagonal (Ports & Adapters)
├── domain/                    # Núcleo puro (sin deps externas)
│   ├── model/                 # Value Objects, Entities, Enums, Exceptions
│   ├── port/in/               # Use Case Interfaces (GetPortfolio, PlaceOrder...)
│   ├── port/out/              # Port Interfaces (Repository, Provider, Store...)
│   └── service/               # Domain Services (PortfolioCalculator, OrderMatcher...)
├── application/usecase/       # Implementaciones de Use Cases (@Service, @Transactional)
├── infrastructure/
│   ├── adapter/in/web/        # Controllers REST, DTOs, OpenAPI, ExceptionHandler
│   ├── adapter/out/persistence/  # JPA Entities, Spring Data Repos, Mappers
│   ├── adapter/out/marketdata/   # MarketData Adapter con Resilience4j + Cache
│   ├── adapter/out/event/     # Spring Events + Async Listeners (Notification, Ledger, Fraud)
│   ├── config/                # Resilience4j, OpenAPI, Async, Cache, MCP
│   └── actuator/              # Health Indicators custom
└── presentation/mcp/          # MCP Server Tools (getPortfolio, placeOrder)
```

## 🚀 Requisitos

- **Java 21**
- **Docker & Docker Compose** (para PostgreSQL, Prometheus, Grafana)
- **Gradle 8.x** (incluido via wrapper)

## 📦 Inicio Rápido

### Opción 1: Docker Compose (Recomendado)

```bash
# Levanta PostgreSQL + Prometheus + Grafana + App
docker compose up --build -d

# Ver logs
docker compose logs -f app

# Verificar health
curl http://localhost:8080/actuator/health
```

### Opción 2: Local con Gradle

```bash
# Compilar
./gradlew clean build

# Ejecutar tests
./gradlew test

# Levantar solo PostgreSQL
docker compose up -d postgres

# Ejecutar app (con profile local)
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```

## 🌐 Endpoints

| Método | Endpoint | Descripción |
|--------|----------|-------------|
| GET | `/api/v1/portfolio/{userId}` | Portfolio completo (valor, efectivo, posiciones) |
| GET | `/api/v1/instruments/search?q=GGAL` | Buscar instrumentos por ticker/nombre |
| POST | `/api/v1/orders` | Enviar orden (MARKET/LIMIT) |
| POST | `/api/v1/orders/{id}/cancel` | Cancelar orden pendiente (NEW) |

### Swagger UI
```
http://localhost:8080/swagger-ui.html
```

### Ejemplo Request - Place Order
```bash
curl -X POST http://localhost:8080/api/v1/orders \
  -H "Content-Type: application/json" \
  -d '{
    "userId": 1,
    "instrumentId": 10,
    "side": "BUY",
    "type": "MARKET",
    "size": 100,
    "idempotencyKey": "550e8400-e29b-41d4-a716-446655440000"
  }'
```

## 🗄️ Base de Datos

### Tablas Principales
- `users` - Usuarios del sistema
- `instruments` - Instrumentos (acciones, bonos, moneda)
- `orders` - Órdenes con estados: NEW, FILLED, REJECTED, CANCELLED
- `marketdata` - Precios OHLC de últimos 2 días
- `idempotency_keys` - **Nueva**: Claves de idempotencia (TTL 24h)

### Migraciones y datos de prueba
Flyway corre en el arranque. La estrategia parte del esquema real que ya existe en
producción (dump `database.sql`) y lo evoluciona con migraciones incrementales
(ver [ADR-006](docs/adr/006-flyway-baseline-produccion.md)):

| Migración | Qué hace |
|---|---|
| `db/migration/V1__baseline_prod_schema.sql` | Esquema legado exacto + datos de referencia del dump. En prod se saltea por el baseline. |
| `db/migration/V2__align_app_schema.sql` | Renombra a snake_case, ajusta tipos y agrega columnas nuevas |
| `db/migration/V3__create_idempotency_keys.sql` | Tabla `idempotency_keys` |
| `db/migration/V4__indexes.sql` | Índices de performance e idempotencia |
| `db/seed/V900__dev_seed.sql` | Seed de desarrollo, **sólo local/docker/test** (prod usa únicamente `db/migration`) |

> Si ya tenías una base local creada con el `V1` viejo, reseteala una vez:
> `docker compose down -v` (o `flyway repair`). El baseline cambió.

El seed crea:

| Usuario | Pesos | Posiciones |
|---|---|---|
| 1 (`demo@cocos.test`) | 5.000.000 | 100 GGAL compradas a 10.950 |
| 2 (`sin-saldo@cocos.test`) | 500.000 | — (sirve para probar rechazos) |

Instrumentos: `ARS` (MONEDA), `GGAL`, `YPFD` (ACCION), `AL30` (BONO), `AAPL` (CEDEAR), con dos
días de `marketdata` cada uno.

### Conexión Neon (Producción)
```yaml
spring:
  datasource:
    url: jdbc:postgresql://<host>/neondb?sslmode=require
    username: ${SPRING_DATASOURCE_USERNAME}
    password: ${SPRING_DATASOURCE_PASSWORD}
```

## ⚙️ Configuración

### Perfiles
- `local` - Desarrollo local (PostgreSQL en localhost:5432)
- `docker` - Docker Compose (PostgreSQL en contenedor)
- `test` - Testcontainers (BD efímera por test)
- `prod` - Neon PostgreSQL (variables de entorno)

### Variables de Entorno
```bash
SPRING_PROFILES_ACTIVE=local
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/cocos_challenge
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=postgres
NEON_PASSWORD=your_neon_password
```

## 🧪 Tests

La suite está implementada y es ejecutable:

| Tipo | Clase | Qué cubre |
|---|---|---|
| Unit (34) | `PortfolioCalculatorTest`, `OrderMatcherTest`, `BalanceValidatorTest`, `Resilience4jCircuitBreakerTest` | Cálculo de portfolio, matching MARKET/LIMIT, validación de saldo/tenencia, precios, edge cases y apertura del CircuitBreaker |
| Integración (1) | `PlaceOrderFlowIT` | Flujo completo contra PostgreSQL real (Testcontainers): MARKET fill, LIMIT new, rechazos, idempotencia y cancelación |

```bash
# Suite completa (unit + integración). Requiere Docker porque usa Testcontainers.
./gradlew test

# Sólo unit tests (no requiere Docker)
./gradlew test --tests "*unit*"

# Sólo integration tests (requiere Docker)
./gradlew test --tests "*integration*"

# Reporte HTML/XML de cobertura
./gradlew jacocoTestReport
# → build/reports/jacoco/test/html/index.html

# Verifica el umbral (≥80% en los servicios de dominio). Corre con `check`.
./gradlew jacocoTestCoverageVerification
./gradlew check
```

### Cobertura de los servicios de dominio

| Clase | Cobertura de líneas |
|---|---|
| `PortfolioCalculator` | 100 % |
| `BalanceValidator` | 100 % |
| `OrderMatcher` | 97.7 % |

`build.gradle.kts` falla el build si alguno baja de 80 % (`jacocoTestCoverageVerification`).

### Notas de Testcontainers

- Docker debe estar corriendo. En CI se usa el Docker del runner de GitHub Actions.
- En Windows, Docker Desktop expone el engine Linux en
  `npipe:////./pipe/dockerDesktopLinuxEngine`; el build lo configura automáticamente y
  fija `api.version=1.44` porque Docker Engine 29 rechaza versiones viejas con HTTP 400.

### Colección Postman

Importar en Postman:

1. `docs/postman/cocos-challenge.postman_collection.json`
2. `docs/postman/cocos-challenge.postman_environment.json` (seleccionar environment **Cocos Challenge — Local**)

La colección cubre health, portfolio, búsqueda, MARKET por size/amount, idempotencia,
rechazos, LIMIT + cancelación y validaciones. Ejecutarla de arriba hacia abajo.

### Smoke manual rápido

```bash
BASE=http://localhost:8080

curl -s $BASE/api/v1/portfolio/1
curl -s "$BASE/api/v1/instruments/search?q=GG"
curl -s -X POST $BASE/api/v1/orders -H "Content-Type: application/json" -d '{
  "userId": 1, "instrumentId": 3, "side": "BUY", "type": "MARKET",
  "size": 10, "idempotencyKey": "11111111-1111-1111-1111-111111111111"
}'
```

## 🛡️ Resiliencia (Resilience4j)

| Patrón | Configuración | Aplicado a |
|--------|---------------|------------|
| Circuit Breaker | 50% failure rate, 10 calls window, 30s open | MarketDataProvider |
| Retry | 3 attempts, 500ms base, exponential backoff + jitter | MarketDataProvider |

> `Bulkhead` y `TimeLimiter` están configurados en `application.yml` y en
> `Resilience4jConfig`, pero **no se aplican con anotaciones**: `TimeLimiter`
> exige métodos que devuelvan `CompletionStage` y el proveedor de market data es
> síncrono (con la anotación la llamada rompía en runtime).

### Verificar Circuit Breaker
```bash
# Health endpoint muestra estado
curl http://localhost:8080/actuator/health

# Métricas Prometheus
curl http://localhost:8080/actuator/prometheus | grep resilience4j
```

## 📊 Observabilidad

### Prometheus
```
http://localhost:9090
```

### Grafana
```
http://localhost:3000 (admin/admin)
```
Dashboards preconfigurados en `docker/grafana/dashboards/`

### Métricas Clave
- `http_server_requests_seconds` - Latencia y throughput HTTP
- `jvm_memory_used_bytes` - Memoria JVM
- `resilience4j_circuitbreaker_state` - Estado CB (0=CLOSED, 1=OPEN, 2=HALF_OPEN)
- `cache_gets_total`, `cache_hits_total`, `cache_misses_total` - Stats de cache

### Logging
Log en texto plano con patrón configurable en `application.yml`. La salida ECS/JSON
(`logging.structured`) es de Spring Boot 3.4+ y este proyecto usa 3.3.4, así que no se aplica.

## 💾 Cache (Caffeine)

| Cache | TTL | Invalidación |
|-------|-----|--------------|
| `instrument-search` | 10 min | Manual / Admin endpoint |
| `latest-market-prices` | 1 min | Manual / Job nocturno |
| `all-instruments` | 10 min | Manual |

**NO se cachea**: Portfolio, Órdenes, Validaciones de saldo.

## 🔄 Event-Driven (Stretch)

Eventos de dominio publicados asíncronamente tras commit:

| Evento | Listeners |
|--------|-----------|
| `OrderPlaced` | Notification, Ledger (reserva), Fraud |
| `OrderFilled` | Notification, Ledger (asiento), Fraud |
| `OrderRejected` | Notification, Fraud |
| `OrderCancelled` | Notification |

## 🤖 MCP Server (Stretch)

Model Context Protocol para consultar API via LLM:

```bash
# Habilitar profile mcp
SPRING_PROFILES_ACTIVE=local,mcp ./gradlew bootRun
```

Tools disponibles:
- `get_portfolio(userId)` → Portfolio completo
- `place_order(userId, instrumentId, side, type, price, size, amount, idempotencyKey)` → Envía orden

## 📁 Estructura del Proyecto

```
cocos-challenge/
├── .github/workflows/ci.yml          # CI/CD GitHub Actions
├── docker-compose.yml                # PostgreSQL + Prometheus + Grafana + App
├── Dockerfile                        # Multi-stage build (distroless)
├── build.gradle.kts                  # Dependencias y configuración
├── src/
│   ├── main/
│   │   ├── java/ar/com/cocos/challenge/
│   │   │   ├── domain/               # Núcleo hexagonal
│   │   │   ├── application/usecase/  # Casos de uso
│   │   │   ├── infrastructure/       # Adapters, config (DomainConfig arma los servicios de dominio)
│   │   │   └── presentation/mcp/     # MCP Tools (no habilitado)
│   │   └── resources/
│   │       ├── application.yml       # Config principal
│   │       ├── application-prod.yml  # Perfil Neon (sólo variables de entorno)
│   │       └── db/{migration,seed}/  # Flyway: esquema y seed de desarrollo
│   └── test/java/ar/com/cocos/challenge/
│       ├── unit/domain/              # 33 tests: PortfolioCalculator, OrderMatcher, BalanceValidator
│       ├── unit/infrastructure/      # Demostración de CircuitBreaker (Resilience4j)
│       └── integration/              # PlaceOrderFlowIT (Testcontainers + PostgreSQL real)
├── docs/
│   ├── adr/                          # ADR-001 a ADR-005 + índice
│   └── postman/                      # Colección + environment
└── specs/
    ├── README.md                     # Índice de requerimientos
    ├── 000-baseline-challenge/       # Spec/plan/tasks del challenge base
    └── NNN-<requerimiento>/          # Un requerimiento por carpeta
```

## 📋 Decisiones Arquitectónicas y Supuestos

Las decisiones formales están documentadas en [`docs/adr/`](docs/adr/README.md):
[001 — Hexagonal](docs/adr/001-arquitectura-hexagonal.md),
[002 — Idempotencia](docs/adr/002-idempotencia.md),
[003 — Resiliencia](docs/adr/003-resiliencia-resilience4j.md),
[004 — Cache](docs/adr/004-cache-caffeine.md) y
[005 — Event-Driven](docs/adr/005-event-driven.md).

Resumen de las decisiones aplicadas:

1. **Java 21 / Spring Boot en lugar de Node.js.** El documento del challenge menciona Node.js,
   pero la consigna del equipo fue explícitamente Java + Spring Boot. Se mantiene el stack pedido.
2. **Efectivo modelado con el instrumento `ARS` (`type = MONEDA`), precio 1.00.** Por lo tanto
   `size` en `CASH_IN` / `CASH_OUT` representa el monto en pesos, y el saldo es
   `Σ size` de órdenes `FILLED` (`CASH_IN`/`SELL` suman, `CASH_OUT`/`BUY` restan).
3. **`orders.price` guarda el precio de ejecución en órdenes `FILLED`.** Para MARKET se persiste
   el `close` del último `marketdata`, que es lo que necesita el cálculo de portfolio.
4. **Orden rechazada = orden persistida.** Si el saldo/tenencia no alcanza, la orden se guarda con
   `status = REJECTED` y `rejection_reason` (`INSUFFICIENT_CASH` / `INSUFFICIENT_SHARES`), y el
   endpoint responde 400.
5. **LIMIT no se ejecuta.** No se simula el mercado (lo dice el challenge): queda en `NEW` hasta
   que el usuario la cancele. El saldo no se reserva al enviarla.
6. **Idempotencia en el body** (`idempotencyKey`), no en header. Se guarda request hash + respuesta
   serializada 24h; un reintento idéntico devuelve la misma orden, uno distinto da 409.
7. **Servicios de dominio sin anotaciones de Spring.** `PortfolioCalculator`, `OrderMatcher` y
   `BalanceValidator` son POJOs; el cableado vive en `infrastructure/config/DomainConfig`.
8. **Cálculo de portfolio on-the-fly**, sin tabla de posiciones: se agregan las órdenes `FILLED`.

## ✅ Definition of Done

- [x] `docker compose up -d postgres` + `./gradlew bootRun` levanta la app y Swagger UI responde
- [x] 3 endpoints funcionales con las reglas de negocio (portfolios, búsqueda, órdenes + cancelación)
- [x] MARKET se ejecuta al `close` y queda `FILLED`; LIMIT queda `NEW`; rechazos se guardan `REJECTED`
- [x] Idempotencia verificada (misma key → misma orden; key distinta → 409)
- [x] **Test funcional `PlaceOrderFlowIT`** contra PostgreSQL real (Testcontainers)
- [x] Cobertura ≥ 80% en domain services (100 %, 100 % y 97.7 %)
- [x] Resilience4j demostrable con fallo simulado (`Resilience4jCircuitBreakerTest`)
- [x] Métricas Prometheus en `/actuator/prometheus`
- [x] Colección Postman en `docs/postman/` (colección + environment)
- [x] ADRs en `docs/adr/` (001 a 005)
- [ ] **Stretch**: MCP server — requiere `spring-ai-mcp-server`, deshabilitado


## 👨‍💻 Autor

Armando Silva - Senior Backend Developer
- Java 21, Spring Boot 3, Arquitectura Hexagonal
- Sistemas financieros, Trading, Alta concurrencia