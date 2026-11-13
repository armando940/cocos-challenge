# ADR-002 — Idempotencia transaccional en órdenes

- **Estado:** Aceptado
- **Fecha:** 2026-11-12
- **Decisores:** Armando Silva

## Contexto

Un cliente puede reintentar `POST /api/v1/orders` por timeout, error de red o doble clic.
Sin protección, el reintento crea una segunda orden y mueve saldo/posición dos veces.
El challenge pide explícitamente idempotencia y el dominio es financiero: duplicar una
orden es un incidente, no un detalle cosmético.

## Decisión

Aceptar una `idempotencyKey` obligatoria en el body de la orden y persistirla en una
tabla dedicada:

```sql
CREATE TABLE idempotency_keys (
  id BIGSERIAL PRIMARY KEY,
  key VARCHAR(100) NOT NULL UNIQUE,
  request_hash VARCHAR(64) NOT NULL,
  response_body JSONB NOT NULL,
  http_status INT NOT NULL,
  created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
  expires_at TIMESTAMPTZ NOT NULL
);
```

Flujo en `PlaceOrderUseCase` (dentro de la misma transacción):

1. Buscar `key` en `idempotency_keys`.
2. Si existe y el `request_hash` (SHA-256 del request normalizado) coincide, devolver la
   respuesta serializada original (misma orden, mismo `orderId` y `datetime`).
3. Si existe y el hash **no** coincide, responder `409 Conflict` (misma key, otro request).
4. Si no existe, procesar la orden y guardar `key + hash + response_body`.
5. TTL de 24 h; un `@Scheduled` horario borra las claves expiradas.

## Consecuencias

**Positivas**

- Reintentos seguros y respuesta estable para el cliente.
- El conflicto de key se detecta antes de tocar saldo o crear órdenes.
- La respuesta original queda auditable en `response_body`.

**Negativas / costos**

- La key es obligatoria: un cliente que no la envía recibe `400`.
- Requiere limpieza periódica para no crecer indefinidamente.
- La comparación de hash debe normalizar el request (hoy usa los campos relevantes:
  user, instrument, side, type, price, size, amount).

## Alternativas consideradas

1. **Header HTTP `Idempotency-Key`.** Es el estándar de facto (Stripe), pero el contrato
   del challenge define la key en el body.
2. **Índice único en `orders.idempotency_key`.** Evita duplicados pero no devuelve la
   respuesta original ni detecta request distinto con la misma key.
3. **Redis con TTL.** Rápido, pero agrega infraestructura y pierde durabilidad/auditoría
   frente a PostgreSQL, que ya es parte del stack.
