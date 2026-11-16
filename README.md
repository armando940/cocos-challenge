# Cocos Capital - Backend Challenge

API REST para sistema de trading/brokerage con arquitectura hexagonal, idempotencia transaccional, resiliencia y observabilidad.

## 📑 Tabla de Contenidos

- [🚀 Inicio Rápido](#-inicio-rápido)
- [🌐 Endpoints](#-endpoints)
- [🏗️ Arquitectura](#️-arquitectura)
- [🧪 Tests](#-tests)
- [📋 Decisiones y Supuestos](#-decisiones-y-supuestos)
- [✨ Features Avanzados](#-features-avanzados)
- [📁 Estructura del Proyecto](#-estructura-del-proyecto)

---

## 🚀 Inicio Rápido

### 🔴 Conexión a la Base de Datos de Cocos (Neon)

Este proyecto está configurado para conectarse directamente a la base de datos PostgreSQL proporcionada por Cocos Capital.

#### Configuración

Crea un archivo `.env` con las variables:

```bash
SPRING_PROFILES_ACTIVE=prod
SPRING_DATASOURCE_URL=jdbc:postgresql://ep-cold-morning-bduc39hl-pooler.c-8.us-east-2.aws.neon.tech/neondb?sslmode=require
SPRING_DATASOURCE_USERNAME=neondb_owner
SPRING_DATASOURCE_PASSWORD=npg_1IDTfWKbhp8U
```

> **Importante**: Spring Boot **no carga `.env` automáticamente**. Las variables deben exportarse al shell antes de ejecutar.

#### Ejecutar (PowerShell - Windows)

**Método recomendado - Dot-source el script (usa el .env):**
```powershell
. .\load-env.ps1
./gradlew bootRun
```
> El punto y espacio al principio (`. .\load-env.ps1`) ejecuta el script en la **misma sesión**, por lo que las variables quedan disponibles para `./gradlew bootRun`.

---

**Método alternativo - Variables inline (sin .env):**
```powershell
$env:SPRING_PROFILES_ACTIVE="prod"; $env:SPRING_DATASOURCE_URL="jdbc:postgresql://ep-cold-morning-bduc39hl-pooler.c-8.us-east-2.aws.neon.tech/neondb?sslmode=require"; $env:SPRING_DATASOURCE_USERNAME="neondb_owner"; $env:SPRING_DATASOURCE_PASSWORD="npg_1IDTfWKbhp8U"; ./gradlew bootRun
```

> **NO funciona**: `Get-Content .env | ForEach-Object { $env:$matches[1] = $matches[2] }` — PowerShell no permite nombres de variable dinámicos con `$env:$key`.

#### Ejecutar (Linux/macOS/Git Bash)

```bash
# source exporta las variables en la shell actual
source <(grep -v '^#' .env | sed 's/^/export /')
./gradlew bootRun

# O en una sola línea:
SPRING_PROFILES_ACTIVE=prod SPRING_DATASOURCE_URL="jdbc:postgresql://ep-cold-morning-bduc39hl-pooler.c-8.us-east-2.aws.neon.tech/neondb?sslmode=require" SPRING_DATASOURCE_USERNAME=neondb_owner SPRING_DATASOURCE_PASSWORD=npg_1IDTfWKbhp8U ./gradlew bootRun
```

La aplicación se conectará automáticamente a la base de datos de Cocos y usará los datos existentes.

### 🟢 Desarrollo Local (Opcional)

Para desarrollo local con PostgreSQL, Prometheus y Grafana en Docker:

```bash
# Levanta todo el stack
docker compose up --build -d

# Verificar health
curl http://localhost:8080/actuator/health
```

O ejecutar solo con Gradle (profile `local` usa valores por defecto en application.yml):

```bash
# Levantar solo PostgreSQL
docker compose up -d postgres

# Ejecutar app (con profile local)
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```

> **Nota**: Las migraciones de Flyway y el seed solo aplican en desarrollo local. La DB de Neon ya tiene el esquema y datos configurados.
>
> **Windows PowerShell**: Usá `.\load-env.ps1` si creás un `.env` local, o exportá las variables manualmente antes de `./gradlew bootRun`.

### Requisitos

- **Java 21**
- **Docker & Docker Compose** (solo para desarrollo local)
- **Gradle 8.x** (incluido via wrapper)

---

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

### Ejemplo: Place Order
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

---

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

---

## 🗄️ Base de Datos

### Tablas Principales

> **Nota**: Esta sección aplica solo para desarrollo local. La DB de Neon ya tiene el esquema configurado.
- `users` - Usuarios del sistema
- `instruments` - Instrumentos (acciones, bonos, moneda)
- `orders` - Órdenes con estados: NEW, FILLED, REJECTED, CANCELLED
- `marketdata` - Precios OHLC de últimos 2 días
- `idempotency_keys` - **Nueva**: Claves de idempotencia (TTL 24h)

### Migraciones y datos de prueba (Solo Desarrollo Local)

Flyway corre en el arranque con migraciones incrementales (ver [ADR-006](docs/adr/006-flyway-baseline-produccion.md)):

| Migración | Qué hace |
|---|---|
| `V1__baseline_prod_schema.sql` | Esquema legado + datos de referencia |
| `V2__align_app_schema.sql` | Renombra a snake_case, ajusta tipos |
| `V3__create_idempotency_keys.sql` | Tabla `idempotency_keys` |
| `V4__indexes.sql` | Índices de performance |
| `V900__dev_seed.sql` | Seed de desarrollo (solo local) |

El seed crea:

| Usuario | Pesos | Posiciones |
|---|---|---|
| 1 (`demo@cocos.test`) | 5.000.000 | 100 GGAL compradas a 10.950 |
| 2 (`sin-saldo@cocos.test`) | 500.000 | — (sirve para probar rechazos) |

Instrumentos: `ARS` (MONEDA), `GGAL`, `YPFD` (ACCION), `AL30` (BONO), `AAPL` (CEDEAR), con dos
días de `marketdata` cada uno.



---

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

```bash
# Suite completa (unit + integración con Testcontainers)
./gradlew test

# Solo unit tests (no requiere Docker)
./gradlew test --tests "*unit*"

# Solo integration tests (requiere Docker)
./gradlew test --tests "*integration*"
```

### Cobertura

| Clase | Cobertura |
|---|---|
| `PortfolioCalculator` | 100 % |
| `BalanceValidator` | 100 % |
| `OrderMatcher` | 97.7 % |

El build falla si algún servicio de dominio baja de 80%.

### Colección Postman

Disponible en [`docs/postman/`](docs/postman/README.md) con colección completa y environment configurado.

---

## ✨ Features Avanzados

### 🛡️ Resiliencia (Resilience4j)

| Patrón | Aplicado a |
|--------|------------|
| Circuit Breaker (50% failure rate, 30s open) | MarketDataProvider |
| Retry (3 attempts, exponential backoff) | MarketDataProvider |

Ver estado: `curl http://localhost:8080/actuator/health`

### 💾 Cache (Caffeine)

| Cache | TTL |
|-------|-----|
| `instrument-search` | 10 min |
| `latest-market-prices` | 1 min |
| `all-instruments` | 10 min |

**NO se cachea**: Portfolio, Órdenes, Validaciones de saldo.

### 🔄 Event-Driven

Eventos de dominio publicados asíncronamente:

| Evento | Listeners |
|--------|-----------|
| `OrderPlaced` | Notification, Ledger, Fraud |
| `OrderFilled` | Notification, Ledger, Fraud |
| `OrderRejected` | Notification, Fraud |
| `OrderCancelled` | Notification |

### 📊 Observabilidad

**Solo disponible en desarrollo local con Docker:**

- **Prometheus**: `http://localhost:9090`
- **Grafana**: `http://localhost:3000` (admin/admin)
- Dashboards preconfigurados en `docker/grafana/dashboards/`

Métricas: `http_server_requests_seconds`, `resilience4j_circuitbreaker_state`, `cache_hits_total`

### 🤖 MCP Server (Deshabilitado por defecto)

Model Context Protocol para consultar API via LLM:

```bash
SPRING_PROFILES_ACTIVE=local,mcp ./gradlew bootRun
```

Tools: `get_portfolio(userId)`, `place_order(...)`

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

---

## 📋 Decisiones y Supuestos

Decisiones clave documentadas en [`docs/adr/`](docs/adr/README.md):

1. **Java 21 + Spring Boot** (según pedido del equipo, no Node.js como mencionaba el challenge)
2. **Efectivo modelado como instrumento `ARS`** (MONEDA, precio 1.00)
3. **MARKET se ejecuta al `close`** del último marketdata y queda `FILLED`
4. **LIMIT queda en `NEW`** hasta cancelación (no se simula mercado)
5. **Órdenes rechazadas se persisten** con `status = REJECTED` y `rejection_reason`
6. **Idempotencia en body** (`idempotencyKey`): misma key → misma orden, key distinta → 409
7. **Portfolio calculado on-the-fly** desde órdenes `FILLED` (sin tabla de posiciones)
8. **Servicios de dominio como POJOs** (sin anotaciones Spring)

## ✅ Definition of Done

- [x] Conexión a DB de Neon funcionando
- [x] 3 endpoints funcionales (portfolio, búsqueda, órdenes + cancelación)
- [x] MARKET ejecutado al `close` → `FILLED`; LIMIT → `NEW`; rechazos → `REJECTED`
- [x] Idempotencia verificada (misma key → misma orden, key distinta → 409)
- [x] Tests de integración con Testcontainers
- [x] Cobertura ≥ 80% en domain services
- [x] Resilience4j implementado
- [x] Métricas Prometheus en `/actuator/prometheus`
- [x] Colección Postman en `docs/postman/`
- [x] ADRs en `docs/adr/`


## 👨‍💻 Autor

Armando Silva - Senior Backend Developer
- Java 21, Spring Boot 3, Arquitectura Hexagonal
- Sistemas financieros, Trading, Alta concurrencia