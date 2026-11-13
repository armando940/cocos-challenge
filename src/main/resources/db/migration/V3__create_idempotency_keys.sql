-- =====================================================================
-- V3 — Tabla de idempotencia transaccional (ADR-002)
-- =====================================================================
-- Tabla nueva, no existe en el esquema legado. Se crea aparte para que
-- producción la reciba aunque V1 se saltee por el baseline de Flyway.
-- =====================================================================

CREATE TABLE IF NOT EXISTS idempotency_keys (
    id            BIGSERIAL PRIMARY KEY,
    key           VARCHAR(100) NOT NULL,
    request_hash  VARCHAR(64)  NOT NULL,
    response_body JSONB        NOT NULL,
    http_status   INT          NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ux_idempotency_keys_key UNIQUE (key)
);
