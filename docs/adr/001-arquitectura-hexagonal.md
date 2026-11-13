# ADR-001 — Arquitectura hexagonal (Ports & Adapters)

- **Estado:** Aceptado
- **Fecha:** 2026-11-12
- **Decisores:** Armando Silva

## Contexto

El challenge pide una API de trading/brokerage en Java + Spring Boot. La lógica de
negocio (saldo, posiciones, matching de órdenes, idempotencia) es la parte que más
importa y la que el evaluador va a leer. Si esa lógica queda mezclada con controllers,
entidades JPA y detalles de Spring, se vuelve difícil de testear y de explicar.

## Decisión

Separar el código en tres capas con dependencias hacia adentro:

```
domain/          → modelos, puertos y servicios puros (sin Spring)
application/     → casos de uso (@Service, @Transactional)
infrastructure/  → adapters web/JPA/market data/eventos, config y wiring
```

Reglas concretas:

- `domain` no importa Spring ni JPA. Los servicios `PortfolioCalculator`, `OrderMatcher`
  y `BalanceValidator` son POJOs y se testean con Mockito sin levantar contexto.
- Los casos de uso dependen de interfaces (`domain/port/in` y `domain/port/out`), no de
  implementaciones.
- El cableado de los servicios de dominio vive en `infrastructure/config/DomainConfig`
  (`@Bean`), no en el dominio.
- Los adapters traducen entre DTO/Entity y modelo de dominio (`PersistenceMapper`).

## Consecuencias

**Positivas**

- Tests unitarios rápidos y deterministas del núcleo financiero.
- El dominio se puede mover de framework sin reescribir reglas.
- El flujo `Controller → UseCase → Domain → Port → Adapter` es explícito y auditable.

**Negativas / costos**

- Más archivos y algo de boilerplate (puertos, mappers).
- La navegación requiere conocer la estructura hexagonal.

## Alternativas consideradas

1. **MVC en capas clásico (Controller → Service → Repository).** Más directo, pero mezcla
   reglas con infraestructura y dificulta aislar los cálculos financieros.
2. **Clean Architecture completa con módulos Gradle separados.** Aísla aún más, pero
   agrega complejidad de build que no se justifica para el tamaño del challenge.
3. **Arquitectura por features.** Válida, pero menos alineada con el vocabulario de
   puertos/adapters que pide el enunciado.
