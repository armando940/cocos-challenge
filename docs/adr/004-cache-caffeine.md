# ADR-004 — Cache Caffeine selectivo

- **Estado:** Aceptado
- **Fecha:** 2026-11-12
- **Decisores:** Armando Silva

## Contexto

La búsqueda de instrumentos y el último precio de mercado son lecturas repetitivas y
baratas de cachear. En cambio, el portfolio, las órdenes y la validación de saldo deben
leer el estado actual: cachearlos produciría decisiones financieras con datos viejos.

## Decisión

Usar Caffeine con TTL por cache:

| Cache | TTL | Uso | Invalidación |
|---|---|---|---|
| `instrument-search` | 10 min | `GET /instruments/search` (key = query + page + size) | manual / TTL |
| `latest-market-prices` | 1 min | `MarketDataProvider.findLatestPrice(s)` | TTL / job nocturno |
| `all-instruments` | 10 min | validaciones de catálogo | TTL / manual |

**No se cachea**: `GET /portfolio/{userId}`, `POST /orders`, validación de saldo/tenencia.

Detalles:

- `CacheConfig` registra cada cache con su propio `Caffeine` (TTL y tamaño), no un TTL
  global.
- `recordStats()` habilita métricas Micrometer (`cache_gets_total`, `cache_hits_total`,
  `cache_misses_total`) y el endpoint `/actuator/caches`.
- Las keys incluyen todos los parámetros que afectan la respuesta para evitar colisiones.

## Consecuencias

**Positivas**

- Menos presión sobre PostgreSQL en endpoints de lectura.
- Precios con staleness acotado a 1 min; catálogo a 10 min.
- Métricas de hit ratio disponibles para ajustar TTL.

**Negativas / costos**

- Un cambio de precio puede tardar hasta 1 min en verse.
- La invalidación es por TTL (no hay endpoint de administración todavía).
- Si se agregan instrumentos, la búsqueda puede tardar hasta 10 min en reflejarlo.

## Alternativas consideradas

1. **Redis.** Compartido entre instancias, pero agrega infraestructura para un challenge
   de una sola instancia; Caffeine es in-process y suficiente.
2. **Sin cache.** Correcto pero innecesariamente costoso para lecturas repetidas.
3. **Cachear el portfolio.** Descartado: riesgo de mostrar saldos/posiciones obsoletos.
