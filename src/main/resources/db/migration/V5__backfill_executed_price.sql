-- =====================================================================
-- V5 — Backfill de datos de ejecución para órdenes FILLED históricas
-- =====================================================================
-- Motivo
-- ------
-- La nueva invariante de dominio de `Order` exige que toda orden con
-- `status = 'FILLED'` tenga `executedPrice` y `executedSize` no nulos
-- (ver requerimiento 006, `Order.filled()` y constructor compacto).
--
-- `V1__baseline_prod_schema.sql` es el dump legado del esquema productivo:
-- inserta órdenes con `price`/`size` pero sin columnas de ejecución.
-- `V2__align_app_schema.sql` sólo agregó `executed_price` y `executed_size`
-- como columnas nullable, sin completar las filas existentes. Por eso las
-- órdenes FILLED históricas quedan con `executed_price IS NULL` y no son
-- mapeables al modelo de dominio actual.
--
-- Estrategia de compatibilidad
-- ----------------------------
-- Para el esquema legado no existe un precio de ejecución separado: el
-- `price` almacenado era, de hecho, el precio al que se ejecutó la orden.
-- Se hace backfill sólo de las filas FILLED que aún no tienen dato de
-- ejecución, copiando `price` -> `executed_price` y `size` -> `executed_size`.
--
-- Garantías
-- ---------
-- - No se modifica `price` (el límite original queda intacto).
-- - No se tocan órdenes NEW / REJECTED / CANCELLED.
-- - Idempotente: la condición `executed_price IS NULL` evita reescribir
--   filas ya migradas o ejecutadas por la aplicación.
-- - No se alteran migraciones previas (V1–V4).
-- =====================================================================

UPDATE orders
   SET executed_price = price,
       executed_size  = size
 WHERE status = 'FILLED'
   AND executed_price IS NULL;
