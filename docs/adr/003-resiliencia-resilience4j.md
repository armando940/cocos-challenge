# ADR-003 — Resiliencia con Resilience4j en market data

- **Estado:** Aceptado
- **Fecha:** 2026-11-12
- **Decisores:** Armando Silva

## Contexto

Los precios de mercado son una dependencia externa (hoy tabla `marketdata`, mañana un
proveedor HTTP). Si esa dependencia falla o tarda, el endpoint de órdenes y el portfolio
no deberían quedar colgados ni propagar errores en cascada. El challenge valora
resiliencia explícita y demostrable.

## Decisión

Aplicar Resilience4j en `MarketDataJpaAdapter` (`domain/port/out/MarketDataProviderPort`):

| Patrón | Configuración | Estado |
|---|---|---|
| **CircuitBreaker** | ventana 10, mínimo 5 llamadas, 50 % failure rate, 30 s open | Activo con anotación |
| **Retry** | 3 intentos, 500 ms, backoff exponencial x2 + jitter 0.1 | Activo con anotación |
| **Bulkhead** | 10 llamadas concurrentes, espera 0 ms | Configurado, sin anotación |
| **TimeLimiter** | 2 s timeout | Configurado, sin anotación |

Implementación:

- `@CircuitBreaker(name = "marketData", fallbackMethod = "...")` y `@Retry(name = "marketData")`
  sobre `findLatestPrice` y `findLatestPrices`.
- Fallbacks explícitos: si el proveedor no responde, se lanza
  `RuntimeException("Market data service unavailable...")` en lugar de devolver datos
  silenciosamente incorrectos.
- Estado y métricas expuestos en `/actuator/health` y `/actuator/prometheus`
  (`resilience4j_circuitbreaker_*`).

### Por qué TimeLimiter no está anotado

`@TimeLimiter` exige que el método devuelva `CompletionStage`/`CompletableFuture`. El
proveedor actual es síncrono (JPA) y al anotarlo rompía en runtime. Se deja configurado
en `application.yml` y `Resilience4jConfig` para cuando el adapter pase a ser asíncrono.
Bulkhead tampoco se anota para no agregar semántica de rechazo sin un caso de uso claro.

## Consecuencias

**Positivas**

- Falla rápida cuando el proveedor está caído (circuit open) en lugar de esperar timeouts.
- Reintentos transparentes para errores transitorios.
- Métricas listas para alertar.

**Negativas / costos**

- Los fallbacks hoy lanzan excepción: el cliente recibe 5xx, que es lo correcto para no
  operar con precios dudosos, pero no hay degradación graceful.
- La configuración de Bulkhead/TimeLimiter es preventiva y no está ejercitada por tests.

## Alternativas consideradas

1. **Hystrix.** Deprecado y sin mantenimiento; Resilience4j es el reemplazo estándar.
2. **Sin resiliencia, confiar en timeouts de JDBC.** Simple, pero un proveedor lento
   bloquea hilos y degrada todo el servicio.
3. **Decoradores funcionales en lugar de anotaciones.** Más explícitos y testeables, pero
   más verbosos; las anotaciones alcanzan para el alcance del challenge.
