-- Seed de desarrollo
-- Datos mínimos para probar los 3 endpoints en local/docker.
-- NO se aplica en producción (profile prod usa sólo db/migration).
--
-- Convención de efectivo: el instrumento ARS (type = MONEDA) cotiza 1.00,
-- por lo que en CASH_IN / CASH_OUT el `size` representa el monto en pesos.

INSERT INTO users (id, email, account_number) VALUES
    (1, 'demo@cocos.test', 'ACC-0001'),
    (2, 'sin-saldo@cocos.test', 'ACC-0002')
ON CONFLICT (id) DO NOTHING;

INSERT INTO instruments (id, ticker, name, type) VALUES
    (1, 'ARS',  'Peso Argentino',              'MONEDA'),
    (2, 'GGAL', 'Grupo Financiero Galicia',    'ACCION'),
    (3, 'YPFD', 'YPF Sociedad Anónima',        'ACCION'),
    (4, 'AL30', 'Bono AL30',                   'BONO'),
    (5, 'AAPL', 'Apple Inc. (CEDEAR)',         'CEDEAR')
ON CONFLICT (id) DO NOTHING;

-- Dos días de market data por instrumento (ayer y hoy)
INSERT INTO marketdata (instrument_id, high, low, open, close, previous_close, datetime) VALUES
    (2, 11000, 10750, 10800, 10950, 10800, now() - interval '1 day'),
    (2, 11300, 10900, 10950, 11200, 10950, now()),
    (3, 28800, 27900, 28000, 28500, 28000, now() - interval '1 day'),
    (3, 29200, 28400, 28500, 29000, 28500, now()),
    (4, 72300, 70900, 71500, 72000, 71500, now() - interval '1 day'),
    (4, 72500, 71600, 72000, 71800, 72000, now()),
    (5, 9450, 9300, 9350, 9400, 9300, now() - interval '1 day'),
    (5, 9600, 9350, 9400, 9500, 9400, now());

-- Usuario 1: 5.000.000 de pesos y una posición de 100 GGAL comprada a 10.950
INSERT INTO orders (instrument_id, user_id, side, size, price, type, status, datetime, executed_price, executed_size)
VALUES
    (1, 1, 'CASH_IN', 5000000, 1.0000, 'MARKET', 'FILLED', now() - interval '2 day', 1.0000, 5000000),
    (2, 1, 'BUY',         100, 10950.0000, 'MARKET', 'FILLED', now() - interval '1 day', 10950.0000, 100);

-- Usuario 2: 500.000 de pesos y sin posiciones (sirve para probar rechazos)
INSERT INTO orders (instrument_id, user_id, side, size, price, type, status, datetime, executed_price, executed_size)
VALUES
    (1, 2, 'CASH_IN', 500000, 1.0000, 'MARKET', 'FILLED', now() - interval '2 day', 1.0000, 500000);

-- Sincronizar secuencias con los ids insertados
SELECT setval(pg_get_serial_sequence('users', 'id'), (SELECT MAX(id) FROM users));
SELECT setval(pg_get_serial_sequence('instruments', 'id'), (SELECT MAX(id) FROM instruments));
SELECT setval(pg_get_serial_sequence('orders', 'id'), (SELECT MAX(id) FROM orders));
SELECT setval(pg_get_serial_sequence('marketdata', 'id'), (SELECT MAX(id) FROM marketdata));
