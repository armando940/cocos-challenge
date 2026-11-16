-- =====================================================================
-- V6 — Marketdata para ARS (MONEDA) en producción
-- =====================================================================
-- Motivo
-- ------
-- El instrumento ARS (ticker='ARS', type='MONEDA') no tiene filas en marketdata
-- en el baseline V1. Las órdenes CASH_IN/CASH_OUT tipo MARKET requieren precio
-- de ejecución (close de marketdata). Sin marketdata fallan con 404.
--
-- Esta migración inserta dos días de cotización 1.00 para ARS.
-- Es idempotente: solo inserta si no existe marketdata para el instrumento.
-- =====================================================================

INSERT INTO marketdata (instrument_id, high, low, open, close, previous_close, datetime)
SELECT ars.id, v.high, v.low, v.open, v.close, v.previous_close, v.datetime
FROM (VALUES
    (1.0000, 1.0000, 1.0000, 1.0000, 1.0000, now() - interval '1 day'),
    (1.0000, 1.0000, 1.0000, 1.0000, 1.0000, now())
) AS v(high, low, open, close, previous_close, datetime)
CROSS JOIN (SELECT id FROM instruments WHERE ticker = 'ARS' ORDER BY id LIMIT 1) AS ars
WHERE NOT EXISTS (
    SELECT 1 FROM marketdata m WHERE m.instrument_id = ars.id
);