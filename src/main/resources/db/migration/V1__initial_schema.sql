-- Flyway Baseline Migration
-- Creates the core schema for Cocos Challenge
-- Based on the existing Neon database structure + idempotency_keys table

-- Users table
CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    account_number VARCHAR(50) NOT NULL UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Instruments table
CREATE TABLE IF NOT EXISTS instruments (
    id BIGSERIAL PRIMARY KEY,
    ticker VARCHAR(20) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(50) NOT NULL, -- ACCION, BONO, MONEDA, etc.
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Orders table
CREATE TABLE IF NOT EXISTS orders (
    id BIGSERIAL PRIMARY KEY,
    instrument_id BIGINT NOT NULL REFERENCES instruments(id),
    user_id BIGINT NOT NULL REFERENCES users(id),
    side VARCHAR(20) NOT NULL, -- BUY, SELL, CASH_IN, CASH_OUT
    size BIGINT NOT NULL, -- cantidad de acciones (entero, sin fracciones)
    price NUMERIC(18, 4), -- precio por acción (NULL para MARKET)
    type VARCHAR(20) NOT NULL, -- MARKET, LIMIT
    status VARCHAR(20) NOT NULL DEFAULT 'NEW', -- NEW, FILLED, REJECTED, CANCELLED
    datetime TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    executed_price NUMERIC(18, 4), -- precio real de ejecución (para FILLED)
    executed_size BIGINT, -- cantidad real ejecutada (para FILLED)
    rejection_reason VARCHAR(100), -- INSUFFICIENT_CASH, INSUFFICIENT_SHARES, etc.
    idempotency_key VARCHAR(100), -- clave de idempotencia
    request_hash VARCHAR(64), -- hash del request para detectar duplicados
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Market Data table
CREATE TABLE IF NOT EXISTS marketdata (
    id BIGSERIAL PRIMARY KEY,
    instrument_id BIGINT NOT NULL REFERENCES instruments(id),
    high NUMERIC(18, 4) NOT NULL,
    low NUMERIC(18, 4) NOT NULL,
    open NUMERIC(18, 4) NOT NULL,
    close NUMERIC(18, 4) NOT NULL,
    previous_close NUMERIC(18, 4) NOT NULL,
    datetime TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Idempotency Keys table (nueva - para idempotencia transaccional)
CREATE TABLE IF NOT EXISTS idempotency_keys (
    id BIGSERIAL PRIMARY KEY,
    key VARCHAR(100) NOT NULL UNIQUE,
    request_hash VARCHAR(64) NOT NULL,
    response_body JSONB NOT NULL,
    http_status INT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Índices para performance

-- Orders: consultas por usuario + estado + fecha
CREATE INDEX IF NOT EXISTS idx_orders_user_status_datetime 
    ON orders(user_id, status, datetime DESC);

-- Orders: consultas por instrumento + estado (para posiciones)
CREATE INDEX IF NOT EXISTS idx_orders_instrument_status 
    ON orders(instrument_id, status);

-- Orders: idempotencia lookup
CREATE INDEX IF NOT EXISTS idx_orders_idempotency_key 
    ON orders(idempotency_key) WHERE idempotency_key IS NOT NULL;

-- MarketData: último precio por instrumento
CREATE INDEX IF NOT EXISTS idx_marketdata_instrument_datetime 
    ON marketdata(instrument_id, datetime DESC);

-- MarketData: precios de últimos 2 días
CREATE INDEX IF NOT EXISTS idx_marketdata_datetime 
    ON marketdata(datetime DESC);

-- Idempotency: limpieza por expiración
CREATE INDEX IF NOT EXISTS idx_idempotency_expires_at 
    ON idempotency_keys(expires_at);

-- Comentarios para documentación
COMMENT ON TABLE users IS 'Usuarios del sistema de trading';
COMMENT ON TABLE instruments IS 'Instrumentos financieros disponibles (acciones, bonos, moneda)';
COMMENT ON TABLE orders IS 'Órdenes de compra/venta/transferencia con estados y idempotencia';
COMMENT ON TABLE marketdata IS 'Datos de mercado (OHLC) de últimos 2 días por instrumento';
COMMENT ON TABLE idempotency_keys IS 'Claves de idempotencia para requests de órdenes (TTL 24h)';

COMMENT ON COLUMN orders.side IS 'BUY=compra, SELL=venta, CASH_IN=ingreso pesos, CASH_OUT=egreso pesos';
COMMENT ON COLUMN orders.type IS 'MARKET=ejecución inmediata, LIMIT=precio límite';
COMMENT ON COLUMN orders.status IS 'NEW=pendiente, FILLED=ejecutada, REJECTED=rechazada, CANCELLED=cancelada';
COMMENT ON COLUMN orders.size IS 'Cantidad de acciones (entero, sin fracciones)';
COMMENT ON COLUMN instruments.type IS 'ACCION, BONO, MONEDA, etc.';