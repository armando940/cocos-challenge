# Colección Postman — Cocos Challenge

Guía para probar la API completa desde Postman (o Newman) contra el backend local.

> Estado verificado: **2026-11-16**, contra `http://localhost:8080` con Postgres + seed.
> Resultado: **31 requests, 107 assertions, 0 fallos** (Newman 6).

---

## 1. Antes de empezar

Necesitás:

- Java 21 (`JAVA_HOME` apuntando al JDK 21).
- Docker Desktop corriendo (para Postgres).
- La app levantada con el perfil `local` (es el default).

```powershell
cd C:\Users\Stars\Documents\Armando\cocos-challenge
docker compose up -d postgres
.\gradlew.bat bootRun
```

Cuando veas `Started CocosChallengeApplication`, la API está en `http://localhost:8080`.

Verificación rápida:

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
```

Docs interactivas: `http://localhost:8080/swagger-ui.html`.

---

## 2. Importar en Postman

1. **Import** → `docs/postman/cocos-challenge.postman_collection.json`.
2. **Import** → `docs/postman/cocos-challenge.postman_environment.json`.
3. Arriba a la derecha, seleccionar el environment **Cocos Challenge — Local**.
4. Ejecutar la colección **de arriba hacia abajo** (Collection Runner o Newman).

El environment sólo define:

| Variable | Valor | Para qué |
|---|---|---|
| `baseUrl` | `http://localhost:8080` | Host de la API |
| `userId` | `1` | Usuario con saldo y posiciones |
| `userIdWithoutCash` | `2` | Usuario sin posiciones (para rechazos) |

> ⚠️ **No agregues `ggalId`, `ypfdId`, `arsId`, etc. al environment.** El environment
> tiene prioridad sobre las variables de colección y pisaría los IDs que resuelve la
> carpeta `00 · Preparación`.

---

## 3. Cómo correrla

### Postman (UI)

- Abrir la colección → **Run** → **Run Cocos Challenge — Backend API**.
- O ejecutar request por request, respetando el orden.

### Newman (CLI)

```powershell
npx --yes newman@6 run docs/postman/cocos-challenge.postman_collection.json `
  -e docs/postman/cocos-challenge.postman_environment.json `
  --reporters cli
```

Si querés un reporte HTML:

```powershell
npx --yes newman@6 run docs/postman/cocos-challenge.postman_collection.json `
  -e docs/postman/cocos-challenge.postman_environment.json `
  --reporters cli,htmlextra --reporter-htmlextra-export newman-report.html
```

---

## 4. Qué prueba cada carpeta

| Carpeta | Qué valida |
|---|---|
| `00 · Preparación` | Resuelve por ticker los IDs/precios de GGAL, YPFD, PAMP y ARS. **Siempre ejecutarla primero.** |
| `01 · Health` | `/actuator/health` (app + DB) y `/actuator/health/readiness`. |
| `02 · Portfolio (estado inicial)` | Estructura del portfolio, saldo, posiciones y guarda el efectivo inicial para comparar al final. |
| `03 · Instrumentos` | Búsqueda por ticker, por nombre, paginación y búsqueda sin resultados. |
| `04 · Órdenes (camino feliz)` | MARKET por `size`, reintento idempotente, conflicto 409, MARKET por `amount` (floor), LIMIT → `NEW`, cancelación y doble cancelación (409). |
| `05 · Rechazos y validación` | `INSUFFICIENT_CASH`, `INSUFFICIENT_SHARES`, idempotencia del rechazo, `size` + `amount`, LIMIT sin precio, monto insuficiente, instrumento inexistente (404) y cancelación inexistente (404). |
| `06 · Cierre (venta y efectivo)` | Verifica el débito exacto de las compras, vende 5 YPFD, y hace un `CASH_IN` de $100.000 comprobando el crédito. |

Los tests de la carpeta 06 usan **deltas** (antes/después), no valores absolutos del seed.
Así la colección funciona igual en una base nueva que en una que ya trae el dump legacy.

---

## 5. Convenciones de la API (lo que vas a ver)

### Éxito

`POST /api/v1/orders` devuelve **201** con:

```json
{
  "orderId": 15,
  "status": "FILLED",
  "executedPrice": "7837.50",
  "executedSize": 10,
  "rejectionReason": null,
  "datetime": "2026-11-16T12:47:58.803996900Z"
}
```

- `MARKET` → `FILLED` (precio = último `close`).
- `LIMIT` → `NEW` (no se ejecuta; `executedPrice` y `executedSize` son `null`).
- `CASH_IN` / `CASH_OUT` → `FILLED` a precio 1.00.

### Orden rechazada por saldo/tenencia

**400** con el mismo shape de orden, pero:

```json
{
  "orderId": 18,
  "status": "REJECTED",
  "executedPrice": null,
  "executedSize": null,
  "rejectionReason": "INSUFFICIENT_CASH",
  "datetime": "2026-11-16T12:47:59.243467Z"
}
```

La orden **se persiste** (requisito del challenge).

### Errores de validación / negocio

Todos son **ProblemDetail (RFC 7807)** con `title`, `status`, `detail`, `instance` y datos
extra dentro de `properties`:

```json
{
  "type": "https://cocos.challenge/errors/idempotency-conflict",
  "title": "Conflicto de idempotencia",
  "status": 409,
  "detail": "Clave de idempotencia ya usada con request diferente: ...",
  "instance": "uri=/api/v1/orders",
  "properties": { "traceId": null, "key": "..." }
}
```

| Caso | HTTP | `title` |
|---|---|---|
| `size` + `amount`, LIMIT sin precio, monto insuficiente | 400 | `Solicitud inválida` |
| Misma `idempotencyKey`, request distinto | 409 | `Conflicto de idempotencia` |
| Cancelar una orden que no está `NEW` | 409 | `Orden no puede cancelarse` |
| Instrumento inexistente | 404 | `Instrumento no encontrado` |
| Precio de mercado faltante (ej. ARS sin marketdata) | 404 | `Precio no disponible` |
| Orden inexistente al cancelar | 404 | `Orden no encontrada` |

---

## 6. Por qué los IDs se resuelven por ticker

En una base que corre la migración `V1` (dump legacy de producción) **los IDs 1–5 no son
los del seed**. Ejemplo real de la base local:

| Ticker | ID real | Precio último close |
|---|---|---|
| GGAL | 34 | 885.80 |
| YPFD | 50 | 7837.50 |
| PAMP | 47 | 925.85 |
| ARS | 66 | 1.00 |
| AAPL | **no existe** | — |

Por eso `V900__dev_seed.sql` no puede imponer los IDs y la colección resuelve todo con
`GET /api/v1/instruments/search?q=<ticker>` en la carpeta 00.

Consecuencias prácticas:

- No uses `instrumentId: 2` esperando GGAL: hoy es CAPX.
- Para el test de `amount` se usa **PAMP** (existe en el dump y tiene precio), no AAPL.
- Si cambiás el seed, la colección sigue funcionando mientras existan GGAL, YPFD, PAMP y ARS.

---

## 7. Resetear los datos entre corridas

Cada corrida crea órdenes nuevas. Hay dos formas de volver atrás:

### Reset liviano (sin borrar el volumen)

Borra sólo las órdenes creadas por la API y las claves de idempotencia. Las órdenes del
seed tienen `idempotency_key IS NULL`, así que quedan intactas.

```powershell
docker exec -it cocos-postgres psql -U postgres -d cocos_challenge -c "DELETE FROM idempotency_keys; DELETE FROM orders WHERE idempotency_key IS NOT NULL;"
```

### Reset total (base desde cero)

```powershell
docker compose down -v
docker compose up -d postgres
.\gradlew.bat bootRun
```

---

## 8. Problemas conocidos al levantar la app

### `Migrations have failed validation: Detected resolved migration not applied to database: 5`

Pasó porque `V5` se agregó después de que la base local ya tenía aplicada `V900`.
Flyway no aplica una migración con versión menor a la última sin `out-of-order`.

Opciones:

1. **Reset total** (recomendado): `docker compose down -v` y volver a levantar.
2. Aplicarla una sola vez con out-of-order:

   ```powershell
   $env:SPRING_FLYWAY_OUT_OF_ORDER="true"
   .\gradlew.bat bootRun
   ```

   Después podés arrancar normal: la migración ya queda registrada.

### `CASH_IN` / `CASH_OUT` devolvía 404 `Precio no disponible`

Era un faltante del seed: `V900` documenta que ARS cotiza 1.00 pero no insertaba
marketdata para ARS. Se corrigió con `V901__seed_ars_marketdata.sql`.
Si ves este error, la base no aplicó `V901`: reiniciá la app (o hacé reset total).

### Las búsquedas tardan en reflejar cambios

`GET /instruments/search` está cacheado 10 minutos (Caffeine). Si cambiás datos de
marketdata, reiniciá la app o esperá el TTL.

---

## 9. Hallazgos abiertos (no bloquean la colección)

Detectados al verificar el backend contra la colección:

1. **`GET /api/v1/portfolio/{userId}` con un usuario inexistente devuelve 200** con
   portfolio vacío y saldo `0.00`. El controller documenta 404. Hay que decidir si se
   valida la existencia del usuario o se corrige la documentación.
2. **Un método HTTP no soportado (ej. `GET /api/v1/orders`) devuelve 500** en lugar de
   405: `GlobalExceptionHandler.handleGeneric` captura `HttpRequestMethodNotSupportedException`.
   Conviene un handler específico para `HttpRequestMethodNotSupportedException` (405) y
   `HttpMediaTypeNotSupportedException` (415).
3. **`V900__dev_seed.sql` es un no-op en bases que corren `V1`**: los `INSERT ... ON CONFLICT (id)`
   de users/instruments se saltean porque los IDs ya existen en el dump legacy. Las órdenes
   del seed sí se insertan, pero apuntando a instrumentos equivocados (ej. la compra de
   "GGAL" termina siendo CAPX). No se tocó para no romper el checksum de Flyway; una
   migración nueva (ticker-based) sería el camino si se quiere un seed determinístico.
