-- =====================================================================
-- V4 — Índices de performance y unicidad de idempotencia
-- =====================================================================
-- Los índices se crean después de V2 (cuando las columnas ya tienen su
-- nombre definitivo en snake_case) y de V3 (idempotency_keys).
--
-- Nota deliberada: NO se agregan UNIQUE sobre columnas legacy
-- (`users.email`, `users.account_number`, `instruments.ticker`). La app no
-- inserta en esas tablas y Hibernate (`ddl-auto: validate`) no valida
-- constraints; agregarlas podría abortar el deploy si producción tuviera
-- duplicados históricos. Si en el futuro se necesitan, van en una
-- migración aparte con preflight de duplicados.
-- =====================================================================

-- Órdenes: portfolio / historial por usuario
CREATE INDEX IF NOT EXISTS idx_orders_user_status_datetime
    ON orders (user_id, status, datetime DESC);

-- Órdenes: posiciones por instrumento
CREATE INDEX IF NOT EXISTS idx_orders_instrument_status
    ON orders (instrument_id, status);

-- Órdenes: validación de tenencia (usuario + instrumento)
CREATE INDEX IF NOT EXISTS idx_orders_user_instrument_status
    ON orders (user_id, instrument_id, status);

-- Órdenes: validación de saldo (usuario + lado)
CREATE INDEX IF NOT EXISTS idx_orders_user_side_status
    ON orders (user_id, side, status);

-- Órdenes: lookup de idempotencia (columna nueva, históricos NULL).
-- Parcial para no chocar con los múltiples NULL del esquema legado.
CREATE UNIQUE INDEX IF NOT EXISTS ux_orders_idempotency_key
    ON orders (idempotency_key)
    WHERE idempotency_key IS NOT NULL;

-- Market data: último precio por instrumento
CREATE INDEX IF NOT EXISTS idx_marketdata_instrument_datetime
    ON marketdata (instrument_id, datetime DESC);

-- Market data: ventana de últimos días
CREATE INDEX IF NOT EXISTS idx_marketdata_datetime
    ON marketdata (datetime DESC);

-- Idempotencia: limpieza por expiración (TTL 24 h)
CREATE INDEX IF NOT EXISTS idx_idempotency_expires_at
    ON idempotency_keys (expires_at);
