# 📋 Especificación — Cocos Capital Backend Challenge

| Campo | Valor |
| :--- | :--- |
| **Tipo** | Especificación funcional y técnica (fuente de verdad de la base) |
| **Requerimiento** | **000 — baseline**: base ya implementada del challenge |
| **Proyecto** | `C:\Users\Stars\Documents\Armando\cocos-challenge` |
| **Origen** | Email de Emi (equipo Cocos) + `backend-challenge.md` |
| **Actualizado** | 2026-11-12 |

> **Alcance de este documento:** la base del proyecto y las invariantes globales que **todos** los
> requerimientos heredan. Los requerimientos nuevos van en `specs/NNN-slug/` y documentan solo su
> delta; no copian estas reglas. **No** contiene el plan de ejecución ni el estado de avance: eso
> vive en [[plan]] y [[tasks]]. Si el código contradice este documento, gana el documento hasta que
> un ADR en `docs/adr/` diga lo contrario.

---

## 1. Contexto del challenge

Puntos clave del email original:

- Desarrollar **3 endpoints** (Portfolio, Buscar Activos, Enviar Orden).
- La base de datos ya existe y está poblada en **Neon PostgreSQL**.
- **Requisito explícito: Java (Spring Boot)**, aunque el repo del challenge mencione Node.js.
- Plazo: ~1 semana (flexible, calidad > velocidad).
- Entregables: repo GitHub, ejecutable localmente, tests, colección Postman.
- Uso de IA permitido, pero evalúan **criterio, decisiones y camino de resolución**.
- Credenciales de BD entregadas por el equipo y **no versionadas**. Variables de entorno:
  `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`.

---

## 2. Alcance funcional

API REST con 3 endpoints core para brokerage/trading:

1. `GET /api/v1/portfolio/{userId}`
2. `GET /api/v1/instruments/search?q=&page=&size=`
3. `POST /api/v1/orders` + `POST /api/v1/orders/{id}/cancel`

### 2.1 Modelo de datos (BD existente)

| Tabla | Columnas clave |
| :--- | :--- |
| `users` | `id`, `email`, `accountNumber` |
| `instruments` | `id`, `ticker`, `name`, `type` |
| `orders` | `id`, `instrumentId`, `userId`, `side`, `size`, `price`, `type`, `status`, `datetime` |
| `marketdata` | `id`, `instrumentId`, `high`, `low`, `open`, `close`, `previousClose`, `datetime` |

> **Libertad permitida:** agregar tablas e índices con justificación (ej. `idempotency_keys`).

### 2.2 Reglas de negocio (innegociables)

#### Portfolio

- **Valor total** = pesos disponibles (ARS) + Σ(cantidad × `close`) de las posiciones.
- **Pesos disponibles** = Σ `size` de órdenes `FILLED`:
  - `CASH_IN` / `SELL` → suman
  - `CASH_OUT` / `BUY` → restan
  - Considera solo el instrumento `type = 'MONEDA'` (ARS).
- **Posiciones** = agrupar órdenes `FILLED` por `instrumentId` (excluyendo MONEDA):
  - `cantidad` = Σ `size` (`BUY` +, `SELL` −)
  - `valorMercado` = `cantidad` × `close`
  - `rendimientoTotal%` = (`valorMercado` − costo ponderado) / costo ponderado × 100
  - `rendimientoDiario%` = (`close` − `previousClose`) / `previousClose` × 100

#### Búsqueda de instrumentos

- Filtro `ILIKE` sobre `ticker` y `name`.
- Paginación (`page`, `size`).
- Incluye `close`, `previousClose` y variación diaria.

#### Envío de órdenes

**Request:**

```json
{
  "userId": 1,
  "instrumentId": 10,
  "side": "BUY",
  "type": "MARKET",
  "price": 1250.00,
  "size": 100,
  "amount": 125000.00,
  "idempotencyKey": "uuid-v4"
}
```

`side`: `BUY | SELL | CASH_IN | CASH_OUT` · `type`: `MARKET | LIMIT` · `size` XOR `amount`.

**Reglas:**

1. `size` XOR `amount` (excluyentes).
2. Si viene `amount`: `size = floor(amount / price)` — **sin fracciones**.
3. **MARKET**: precio = `close` actual → estado `FILLED` inmediato.
4. **LIMIT**: requiere `price` → estado `NEW` (no se ejecuta: no hay simulación de mercado).
5. **Validación transaccional con lock:**
   - `BUY`: pesos disponibles ≥ `size × price`, si no → `REJECTED`.
   - `SELL`: cantidad en posición ≥ `size`, si no → `REJECTED`.
6. **Idempotencia**: `idempotencyKey` única → el reintento devuelve la respuesta original.
7. `CASH_IN` / `CASH_OUT`: transferencias con `instrumentId` = MONEDA, `type` = MARKET, `FILLED`.
8. Toda orden rechazada **se persiste** con estado `REJECTED` y `rejection_reason`.

#### Cancelación

- Solo órdenes `status = NEW` (LIMIT pendientes) → `CANCELLED`.
- Cualquier otro estado → `409 ONLY_NEW_ORDERS_CAN_BE_CANCELLED`.

#### Cálculo de posiciones

- On-the-fly al consultar el portfolio (no hay tabla `positions` persistida).
- Opcional: vista materializada si la performance lo exige, documentada en ADR.

### 2.3 Requisitos no funcionales

| Requisito | Tecnología / detalle |
| :--- | :--- |
| **Lenguaje** | Java 21 |
| **Framework** | Spring Boot 3.3.x |
| **Arquitectura** | Hexagonal (Ports & Adapters) |
| **Acceso a datos** | Spring Data JPA + Hibernate |
| **Transaccionalidad** | `@Transactional` + `READ_COMMITTED` + `SELECT FOR UPDATE` |
| **Idempotencia** | Tabla `idempotency_keys` (`key` UNIQUE, `request_hash`, `response_body`, TTL 24 h) |
| **Resiliencia** | Resilience4j: CircuitBreaker, Retry (backoff + jitter) y Bulkhead sobre `MarketDataProvider` |
| **Observabilidad** | Micrometer + Prometheus + Grafana, health/readiness, logs con correlation ID |
| **Tests** | JUnit 5 + Mockito (unit) + Testcontainers (integración), mínimo un flujo completo de órdenes |
| **Documentación API** | Springdoc OpenAPI 3 → Swagger UI `/swagger-ui.html` |
| **Colección Postman** | `docs/postman/` |
| **Ejecutabilidad local** | `docker compose up` (BD + app + Prometheus + Grafana) |
| **Cache** | Caffeine: búsqueda 5–10 min, precios 30–60 s. **Nunca** portfolio ni órdenes |
| **Event-driven (stretch)** | Spring Events async: `OrderPlaced`, `OrderFilled`, `OrderRejected`, `OrderCancelled` → listeners Notificación, Ledger, Fraude |
| **MCP (stretch)** | Spring AI MCP server con tools `getPortfolio` y `placeOrder` |

### 2.4 Estrategia de cache

| Qué cachear | TTL | Invalidación |
| :--- | :--- | :--- |
| `GET /instruments/search` (por query) | 10 min | expiración por TTL |
| `MarketDataProvider` (precios por instrumento) | 1 min | expiración por TTL |
| Lista de instrumentos (validación) | 10 min | expiración por TTL |

| **No cachear** | Por qué |
| :--- | :--- |
| `GET /portfolio/{userId}` | Cambia con cada orden `FILLED` |
| `POST /orders` | Las escrituras nunca se cachean |
| Validación de saldo/tenencia | Debe leer el estado actual con lock |

Implementación: Caffeine + `@Cacheable` + métricas Micrometer.

### 2.5 Supuestos y decisiones de diseño

1. Sin autenticación → `userId` viaja en el body o la ruta.
2. Sin simulación de mercado → MARKET usa `close`, LIMIT queda `NEW`.
3. ARS = instrumento `type = 'MONEDA'` con `ticker = 'ARS'`, precio 1.00.
4. Portfolio calculado on-the-fly.
5. Idempotencia en el body (no en header HTTP estándar).
6. Concurrencia optimista/pesimista según el caso: `SELECT FOR UPDATE` al validar saldo y tenencia.
7. Timezone UTC, fechas ISO-8601.
8. Solo ARS; dinero siempre `BigDecimal` (scale 2, `HALF_UP`).

### 2.6 Contratos REST

#### `GET /api/v1/portfolio/{userId}`

```json
{
  "userId": 1,
  "totalAccountValue": 1250000.00,
  "availableCash": 250000.00,
  "positions": [{
    "instrumentId": 10,
    "ticker": "GGAL",
    "name": "Grupo Financiero Galicia",
    "quantity": 100,
    "marketValue": 1000000.00,
    "totalReturnPct": 12.5,
    "dailyReturnPct": 1.2
  }]
}
```

#### `GET /api/v1/instruments/search?q=GGAL&page=0&size=20`

```json
{
  "content": [{
    "instrumentId": 10,
    "ticker": "GGAL",
    "name": "Grupo Financiero Galicia",
    "type": "ACCION",
    "currentPrice": 10000.00,
    "previousClose": 9880.00,
    "dailyChangePct": 1.21
  }],
  "page": 0, "size": 20, "totalElements": 1, "totalPages": 1
}
```

#### `POST /api/v1/orders`

```json
// 201
{ "orderId": 55, "status": "FILLED", "executedPrice": 10000.00, "executedSize": 100, "datetime": "2026-11-11T14:30:00Z" }

// 400
{ "orderId": 56, "status": "REJECTED", "reason": "INSUFFICIENT_CASH", "message": "Pesos disponibles: 50000, Requeridos: 100000" }
```

#### `POST /api/v1/orders/{id}/cancel`

- `200` → `CANCELLED`
- `409` → `ONLY_NEW_ORDERS_CAN_BE_CANCELLED`

### 2.7 Definition of Done del challenge

- [ ] `docker compose up` deja Swagger UI en `localhost:8080/swagger-ui.html`.
- [ ] Los 3 endpoints funcionan contra Neon/local con las reglas correctas.
- [ ] Test funcional de órdenes: MARKET fill, LIMIT new, REJECTED por saldo, idempotencia y cancelación.
- [ ] Cobertura ≥ 80 % en `PortfolioCalculator`, `OrderMatcher` y `BalanceValidator`.
- [ ] Resilience4j demostrable (fallo simulado abre el circuit breaker).
- [ ] Métricas Prometheus en `/actuator/prometheus`, incluyendo cache.
- [ ] Colección Postman en `docs/postman/`.
- [ ] README con arquitectura, ADRs, cómo correr, testear y ver métricas.
- [ ] **Stretch**: listeners async + MCP server con 2 tools.

---

## 3. Arquitectura objetivo (hexagonal)

```text
cocos-challenge/
├── .github/workflows/ci.yml
├── docker-compose.yml            # PostgreSQL + Prometheus + Grafana + App
├── Dockerfile                    # Multi-stage
├── docs/
│   ├── adr/                      # ADRs inmutables
│   └── postman/                  # Colección + environment
├── specs/
│   ├── README.md                 # Índice de requerimientos
│   ├── state.json                # Puntero global (activeRequirement)
│   ├── _templates/               # Plantillas de spec/plan/tasks/review/state
│   ├── 000-baseline-challenge/   # Este documento + plan + tasks + state
│   └── NNN-slug/                 # Un requerimiento nuevo por carpeta
├── src/main/java/ar/com/cocos/challenge/
│   ├── domain/
│   │   ├── model/                # Records y excepciones de dominio
│   │   ├── port/in/              # Casos de uso
│   │   ├── port/out/             # Puertos de salida
│   │   └── service/              # PortfolioCalculator, OrderMatcher, BalanceValidator
│   ├── application/usecase/      # Orquestación (@Service, @Transactional)
│   ├── infrastructure/
│   │   ├── adapter/in/web/       # Controllers, DTOs, GlobalExceptionHandler
│   │   ├── adapter/out/          # persistence, marketdata, event
│   │   ├── config/               # Resilience4j, Cache, OpenAPI, Async, Domain, MCP
│   │   └── actuator/             # Health indicators
│   └── presentation/mcp/         # MCP Server + Tools (stretch, hoy desactivado)
└── src/test/
    ├── unit/domain/              # Tests de servicios de dominio
    ├── unit/infrastructure/      # Tests de resiliencia
    ├── integration/              # Testcontainers
    └── contract/                 # Reservado para contract tests (vacío)
```

---

## 4. Referencias

- Email original: Emi (Cocos) — 2026-11-11.
- Repo del challenge: <https://github.com/cocos-capital/cocos-challenge/blob/main/backend-challenge.md>
- BD Neon: `ep-cold-morning-bduc39hl-pooler.c-8.us-east-2.aws.neon.tech` (credenciales fuera del repo).
- Decisiones: [[docs/adr/README]] · Plan: [[plan]] · Tareas: [[tasks]]
- Nota del proyecto en la bóveda: `01_Projects/personal/cocos-challenge/cocos-challenge.md`
