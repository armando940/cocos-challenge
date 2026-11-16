-- =====================================================================
-- V901 — Precio del instrumento ARS en el seed de desarrollo
-- =====================================================================
-- Motivo
-- ------
-- `V900__dev_seed.sql` documenta que el instrumento ARS (type = MONEDA)
-- cotiza 1.00, pero no inserta ninguna fila en `marketdata`.
--
-- Las órdenes CASH_IN / CASH_OUT se envían como MARKET y el caso de uso
-- resuelve el precio de ejecución con `findLatestPrice(instrumentId)`.
-- Sin una fila de marketdata para ARS, esas órdenes fallan con 404
-- (InstrumentPriceNotFoundException) aunque el instrumento exista.
--
-- Esta migración agrega dos días de cotización 1.00 para ARS, igual que
-- hace V900 con el resto de los instrumentos. Es idempotente: sólo inserta
-- si el instrumento todavía no tiene marketdata.
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
