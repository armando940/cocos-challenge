-- =====================================================================
-- V2 — Alineación del esquema legado al modelo de la aplicación
-- =====================================================================
-- A partir de V1 (esquema productivo) esta migración:
--
--   1. Renombra columnas legacy (camelCase plegado a minúsculas) al
--      snake_case que esperan las entidades JPA (`ddl-auto: validate`).
--   2. Ajusta tipos (SERIAL/INT -> BIGINT, NUMERIC(10,2) -> NUMERIC(18,4),
--      TIMESTAMP/DATE -> TIMESTAMPTZ).
--   3. Agrega las columnas nuevas del challenge (ejecución, rechazo,
--      idempotencia y auditoría).
--   4. Normaliza valores de `instruments.type` al enum `InstrumentType`.
--
-- Se ejecuta SIEMPRE: en producción Flyway hace baseline en V1 y arranca
-- desde V2; en local/CI V1 crea el esquema legado y luego corre V2.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 0. Soltar FKs antes de cambiar tipos de id/FK a BIGINT.
--    Se buscan por catálogo para no depender del nombre autogenerado.
-- ---------------------------------------------------------------------
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN
        SELECT conrelid::regclass AS tbl, conname
        FROM pg_constraint
        WHERE contype = 'f'
          AND conrelid IN ('orders'::regclass, 'marketdata'::regclass)
    LOOP
        EXECUTE format('ALTER TABLE %s DROP CONSTRAINT %I', r.tbl, r.conname);
    END LOOP;
END
$$;

-- ---------------------------------------------------------------------
-- 1. users
-- ---------------------------------------------------------------------
ALTER TABLE users RENAME COLUMN accountnumber TO account_number;
ALTER TABLE users ALTER COLUMN id TYPE BIGINT;
ALTER TABLE users ALTER COLUMN account_number TYPE VARCHAR(50);

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP;

-- ---------------------------------------------------------------------
-- 2. instruments
-- ---------------------------------------------------------------------
ALTER TABLE instruments ALTER COLUMN id TYPE BIGINT;
ALTER TABLE instruments ALTER COLUMN ticker TYPE VARCHAR(20);
ALTER TABLE instruments ALTER COLUMN type TYPE VARCHAR(50);

ALTER TABLE instruments
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE instruments
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP;

-- Normalización de datos: el dump usa plurales ('ACCIONES'), el enum
-- de dominio usa singulares ('ACCION'). Sin esto, Hibernate no puede
-- materializar el enum al leer la fila.
UPDATE instruments SET type = 'ACCION' WHERE type = 'ACCIONES';
UPDATE instruments SET type = 'BONO'   WHERE type = 'BONOS';
UPDATE instruments SET type = 'CEDEAR' WHERE type = 'CEDEARS';
UPDATE instruments SET type = 'OPCION' WHERE type = 'OPCIONES';
UPDATE instruments SET type = 'FUTURO' WHERE type = 'FUTUROS';
UPDATE instruments
   SET type = 'OTRO'
 WHERE type IS NULL
    OR type NOT IN ('ACCION', 'BONO', 'MONEDA', 'CEDEAR', 'OPCION', 'FUTURO', 'OTRO');

-- ---------------------------------------------------------------------
-- 3. orders
-- ---------------------------------------------------------------------
ALTER TABLE orders RENAME COLUMN instrumentid TO instrument_id;
ALTER TABLE orders RENAME COLUMN userid TO user_id;

ALTER TABLE orders ALTER COLUMN id TYPE BIGINT;
ALTER TABLE orders ALTER COLUMN instrument_id TYPE BIGINT;
ALTER TABLE orders ALTER COLUMN user_id TYPE BIGINT;
ALTER TABLE orders ALTER COLUMN size TYPE BIGINT;
ALTER TABLE orders ALTER COLUMN price TYPE NUMERIC(18, 4);
ALTER TABLE orders ALTER COLUMN type TYPE VARCHAR(20);
ALTER TABLE orders ALTER COLUMN side TYPE VARCHAR(20);
ALTER TABLE orders
    ALTER COLUMN datetime TYPE TIMESTAMPTZ USING datetime AT TIME ZONE 'UTC';

-- Columnas nuevas del challenge
ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS executed_price NUMERIC(18, 4);
ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS executed_size BIGINT;
ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS rejection_reason VARCHAR(100);
ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(100);
ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS request_hash VARCHAR(64);
ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP;

-- ---------------------------------------------------------------------
-- 4. marketdata
-- ---------------------------------------------------------------------
ALTER TABLE marketdata RENAME COLUMN instrumentid TO instrument_id;
ALTER TABLE marketdata RENAME COLUMN previousclose TO previous_close;
ALTER TABLE marketdata RENAME COLUMN "date" TO datetime;

ALTER TABLE marketdata ALTER COLUMN id TYPE BIGINT;
ALTER TABLE marketdata ALTER COLUMN instrument_id TYPE BIGINT;
ALTER TABLE marketdata ALTER COLUMN high TYPE NUMERIC(18, 4);
ALTER TABLE marketdata ALTER COLUMN low TYPE NUMERIC(18, 4);
ALTER TABLE marketdata ALTER COLUMN "open" TYPE NUMERIC(18, 4);
ALTER TABLE marketdata ALTER COLUMN "close" TYPE NUMERIC(18, 4);
ALTER TABLE marketdata ALTER COLUMN previous_close TYPE NUMERIC(18, 4);
ALTER TABLE marketdata
    ALTER COLUMN datetime TYPE TIMESTAMPTZ USING datetime::timestamp AT TIME ZONE 'UTC';

ALTER TABLE marketdata
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

-- ---------------------------------------------------------------------
-- 5. Recrear FKs con nombres explícitos (facilita migraciones futuras)
-- ---------------------------------------------------------------------
ALTER TABLE orders
    ADD CONSTRAINT fk_orders_instrument
        FOREIGN KEY (instrument_id) REFERENCES instruments (id);
ALTER TABLE orders
    ADD CONSTRAINT fk_orders_user
        FOREIGN KEY (user_id) REFERENCES users (id);
ALTER TABLE marketdata
    ADD CONSTRAINT fk_marketdata_instrument
        FOREIGN KEY (instrument_id) REFERENCES instruments (id);

-- ---------------------------------------------------------------------
-- 6. Secuencias: pasarlas a BIGINT para acompañar las columnas id
-- ---------------------------------------------------------------------
ALTER SEQUENCE IF EXISTS users_id_seq AS BIGINT;
ALTER SEQUENCE IF EXISTS instruments_id_seq AS BIGINT;
ALTER SEQUENCE IF EXISTS orders_id_seq AS BIGINT;
ALTER SEQUENCE IF EXISTS marketdata_id_seq AS BIGINT;
