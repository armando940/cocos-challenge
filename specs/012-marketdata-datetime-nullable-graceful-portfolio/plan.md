# Plan de Ejecución — 012 `datetime` nullable en MarketData y valoración degradada del portfolio

> Derivado de [`spec.md`](spec.md) y del estado real del repo el 2026-11-16. Este requerimiento
> **supersede** a `011-marketdata-legacy-nullable-ohlc`.
> IDs de tarea: `R012-T01`… y solo existen dentro de esta carpeta.

## Resumen

- **Fases**: 2 (Implementación + tests; ADR y cierre)
- **Olas**: 2 — W1 con 3 lanes (código + sus tests en el mismo lane), W2 con 1 lane (ADR)
- **Horas estimadas**: ~4.5 h + verificación del coordinator
- **Camino crítico**: `R012-T03 (dominio + test) → cierre W1 → R012-T04 (ADR) → cierre W2`
- **Riesgo mayor**: `R012-T03` — cambia la semántica de valoración (de excepción a degradación) y
  reordena `buildPosition`; un orden equivocado puede enmascarar `Instrumento no encontrado` (CA-05)
  o alterar `totalAccountValue` de los tests existentes.

> **Nota de coordinación:** el plan original separaba código (W1) y tests (W2), lo que dejaba
> `./gradlew test` determinísticamente rojo al cerrar W1 (`throwsWhenPriceIsMissing` todavía esperaba
> la excepción vieja). Para que **cada ola cierre con la suite verde**, cada lane de W1 incluye su
> test: los paths siguen siendo disjuntos y ninguna tarea comparte archivo.

## Estado actual vs plan

| Fase | Objetivo | Estado | Evidencia |
| :--- | :--- | :--- | :--- |
| 1 | Entidad con `datetime` nullable, guard en el mapper y valoración degradada, con tests | ⬜ Pendiente | `MarketDataEntity.java` sigue con `nullable = false` en `datetime`; `PersistenceMapper` construye `MarketPrice` sin guard; `PortfolioCalculator.buildPosition` lanza `"Precio no disponible…"` |
| 2 | ADR-011 que supersede al 010, banner e índice | ⬜ Pendiente | No existe `docs/adr/011-…`; `docs/adr/README.md` lista el 010 como `Aceptado` |

## Diseño decidido (fijado por el coordinator — no reinventar)

### 1. `MarketDataEntity.java`

- Quitar `nullable = false` **solo** de `datetime`. La columna es nullable en `V1`/`V2`.
- Mantener `nullable = false` en `instrumentId`, `close`, `previousClose` y `createdAt`.
- No tocar `precision`/`scale`, `@Table`, índices, `@PrePersist` ni el resto de los campos.
- Actualizar el Javadoc: la columna `datetime` es nullable en el esquema legacy; `close` y
  `previousClose` siguen obligatorios porque `MarketPrice` los exige no nulos.

Resultado esperado:

```java
@Column
private Instant datetime;
```

### 2. `PersistenceMapper.toDomain(MarketDataEntity)`

```java
if (entity.getDatetime() == null) {
    throw new IllegalStateException(
            "MarketData sin datetime (id=" + entity.getId() + "): no se puede construir MarketPrice");
}
```

- Sin imports nuevos (usar `if`, no `Objects`).
- No cambiar los otros overloads de `toDomain`/`toEntity`.
- La validación es defensiva: las consultas de último precio ya filtran `datetime IS NOT NULL`
  (011). Cualquier lectura futura (`findById`/`findAll`) falla con mensaje accionable en lugar del
  `NullPointerException` de `MarketPrice`.

### 3. `PortfolioCalculator.buildPosition`

Orden de chequeos (mínimo cambio):

1. Resolver el `Instrument` y **mantener** el throw `"Instrumento no encontrado: " + instrumentId`
   (CA-05). Va primero porque `Position.empty(...)` necesita `ticker` y `name`.
2. Calcular `netQuantity` (BUY suma, SELL resta).
3. Si `netQuantity <= 0` → `Position.empty(instrumentId, instrument.ticker(), instrument.name())`.
4. Si `prices.get(instrumentId) == null` → `Position` degradada:
   `quantity = netQuantity`, `marketValue = Money.zero()`,
   `totalCost = calculateWeightedAverageCost(orders, netQuantity)`,
   `totalReturnPct = BigDecimal.ZERO`, `dailyReturnPct = BigDecimal.ZERO`; sin excepción.
5. Si hay precio → rama actual **intacta**.

No tocar `calculate()`, `calculateAvailableCash`, `cashEffect`, `calculatePositions` (incluido el
`.filter(p -> p.quantity() > 0)`) ni `calculateWeightedAverageCost`. No tocar
`OrderMatcher`/`BalanceValidator`: la ejecución de órdenes sigue fallando cerrado sin precio.

### 4. Tests (van en el mismo lane que su código)

- **`PortfolioCalculatorTest`**: reemplazar `throwsWhenPriceIsMissing` por
  `returnsDegradedPositionWhenPriceIsMissing` (quantity neta, `marketValue` 0.00, `totalCost`
  ponderado, returns 0, `totalAccountValue == availableCash` con una única posición) y agregar
  `returnsNoPositionsForFlatPositionWithoutPrice` (BUY y SELL mismo size, `findLatestPrices` vacío →
  `positions()` vacío, sin excepción). No tocar `throwsWhenInstrumentIsMissing` ni los asserts de
  tests con precio.
- **`PersistenceMapperTest`** (nuevo, `src/test/java/.../unit/infrastructure/`, POJO puro sin Spring):
  CA-02 (`IllegalStateException`, mensaje con `datetime` y el `id`) y CA-03 (mapping de
  `close`/`previousClose`/`datetime`).
- **`MarketDataJpaAdapterIT`**: test nuevo con instrumento propio (`TESTG`) que inserte por
  `jdbcTemplate` una fila con `datetime NULL` (`INSERT … RETURNING id`) y verifique
  `marketDataRepository.findById(id)` con `getDatetime() == null`; no romper CA-04 de 011 ni el
  helper `createMarketData`.

### 5. ADR

- Crear `docs/adr/011-marketdata-datetime-nullable-graceful-valuation.md` con **Contexto**,
  **Decisión** (entidad alineada; validación en mapper; valoración degradada + filtros de 011
  preservados), **Consecuencias**, **Alternativas descartadas** y **Referencias**.
- Insertar al inicio de `docs/adr/010-marketdata-nullability-legacy.md` (después del H1, sin
  reescribir el cuerpo): `> **Superseded by [ADR-011](011-marketdata-datetime-nullable-graceful-valuation.md)** (2026-11-16)`.
- `docs/adr/README.md`: agregar fila 011 y cambiar el estado del 010 a `Superseded por 011`.

## Fase 1 — Implementación y tests (~3.5 h)

**Objetivo**: alinear la entidad, validar `datetime` en el mapper y degradar la valoración sin
precio, con sus tests.
**Entregable**: 3 archivos de producción + 3 archivos de test.
**DoD**: `./gradlew compileJava test` verde (incluye 011, R007 y los casos nuevos).

| ID | Tarea | Depende de | Estimación | Riesgo | Done |
| :--- | :--- | :--- | :--- | :--- | :--- |
| R012-T01 | Entidad: quitar `nullable = false` de `datetime` + Javadoc; test en `MarketDataJpaAdapterIT` de hidratación con `datetime NULL` vía `findById` | — | 1.25 h | Bajo | `grep -n "nullable = false" MarketDataEntity.java` solo muestra los cuatro campos; `MarketDataJpaAdapterIT` verde |
| R012-T02 | Mapper: guard `IllegalStateException` con `id`; `PersistenceMapperTest` nuevo (CA-02/CA-03) | — | 1.25 h | Bajo | `grep -n "MarketData sin datetime" PersistenceMapper.java`; `PersistenceMapperTest` verde |
| R012-T03 | `PortfolioCalculator.buildPosition` degradado; actualizar/reemplazar tests en `PortfolioCalculatorTest` (CA-04/CA-05) | — | 2 h | Alto | `grep -n "Precio no disponible" PortfolioCalculator.java` sin resultados; `PortfolioCalculatorTest` verde |

## Fase 2 — ADR y cierre (~1 h)

**Objetivo**: documentar la decisión que supersede al ADR-010 y cerrar la verificación.
**Entregable**: ADR-011, banner en 010 e índice actualizado.
**DoD**: `./gradlew check` verde y greps del ADR/banner/índice.

| ID | Tarea | Depende de | Estimación | Riesgo | Done |
| :--- | :--- | :--- | :--- | :--- | :--- |
| R012-T04 | Crear ADR-011, banner `Superseded by` en ADR-010 e índice | R012-T01, R012-T02, R012-T03 | 1 h | Bajo | `Test-Path docs/adr/011-marketdata-datetime-nullable-graceful-valuation.md`; `grep -n "Superseded by" docs/adr/010-marketdata-nullability-legacy.md`; `grep -n "011-marketdata-datetime" docs/adr/README.md` |

## Olas de ejecución

| Ola | Tareas en paralelo | Lanes | Rutas `toca:` disjuntas | Verificación de cierre |
| :-- | :-- | :-- | :-- | :-- |
| W1 | R012-T01, R012-T02, R012-T03 | 3 | entidad+IT / mapper+test / dominio+test | `./gradlew compileJava test` (coordinator) |
| W2 | R012-T04 | 1 | 3 documentos ADR | `./gradlew check` (coordinator) |

> Máximo 3 lanes. W1 cierra verde porque cada lane entrega código **y** su test. W2 va sola porque
> los tres documentos ADR los escribe un mismo lane (coherencia del supersede).

## Camino crítico

`R012-T03 → cierre W1 → R012-T04 → cierre W2`

- `R012-T03` define la semántica de valoración y repara el test obsoleto en el mismo lane.
- `R012-T01` y `R012-T02` son independientes y corren en paralelo.
- `R012-T04` documenta lo implementado y no bloquea el build.

## Riesgos y mitigación

| Riesgo | Probabilidad | Impacto | Mitigación |
| :--- | :--- | :--- | :--- |
| La degradación enmascara el instrumento faltante y rompe CA-05 | Media | Alto | Resolver el instrumento y lanzar `Instrumento no encontrado` **antes** de cualquier retorno temprano; `throwsWhenInstrumentIsMissing` debe seguir verde |
| El test de portfolio altera `totalAccountValue` de otros casos | Media | Medio | Reemplazar solo `throwsWhenPriceIsMissing`; el caso degradado usa una única posición y asserta `totalAccountValue == availableCash` |
| `PersistenceMapperTest` arrastra Spring/JPA y se vuelve frágil | Baja | Medio | POJO puro en `unit/infrastructure`: `new MarketDataEntity()` + setters + método estático |
| El banner del ADR-010 altera el cuerpo | Baja | Medio | Insertar una sola línea después del H1; `git diff --numstat` sin líneas eliminadas |
| Colisión de datos en la base compartida de Testcontainers | Media | Medio | Ticker `TESTG` y `INSERT … RETURNING id`; no reutilizar `TESTA`–`TESTF` ni el seed |
| Cobertura < 80 % por las ramas nuevas de `PortfolioCalculator` | Baja | Medio | El lane T03 cubre degradada, flat y con precio; si `check` falla, buffer |
| `unrealizedPnl()` de la posición degradada queda en −`totalCost` | Baja | Bajo | No se expone en REST (`PortfolioController` mapea `marketValue`, returns y quantity); documentarlo en ADR-011 |

## Hallazgos, contradicciones y aclaraciones (spec vs código)

| # | Hallazgo | Resolución |
| :--- | :--- | :--- |
| 1 | 011 (CA-02) y ADR-010 exigen `datetime nullable = false`; 012 (CA-01) lo revierte | Contradicción **intencional** por supersede: manda 012; ADR-011 deroga al 010; no se edita el 011 |
| 2 | El Javadoc de `MarketDataEntity` afirma que `datetime` es obligatorio | R012-T01 lo actualiza junto con la anotación |
| 3 | `Position.empty(...)` necesita `ticker`/`name` del instrumento | Orden real: instrumento → `netQuantity` → empty → precio → rama normal |
| 4 | `calculatePositions` filtra `quantity() > 0` | El caso flat sin precio solo devuelve lista vacía si el chequeo de precio va después de `netQuantity` |
| 5 | `MarketDataJpaRepository` ya filtra `datetime IS NOT NULL` (011) | Fuera de alcance: no se toca; R012-T01 verifica solo `findById` |
| 6 | `docs/adr/README.md` lista el 010 como `Aceptado` | R012-T04 lo marca `Superseded por 011` y agrega la fila 011 |

## Definition of Done global

| Criterio | Verificación | Tarea |
| :--- | :--- | :--- |
| CA-01: `datetime` sin `nullable = false`; los otros cuatro lo conservan | `grep -n "nullable = false" …/MarketDataEntity.java` | R012-T01 |
| CA-02/CA-03: guard del mapper y mapping correcto | `PersistenceMapperTest` verde | R012-T02 |
| CA-04/CA-05: posición degradada; instrumento inexistente sigue fallando | `PortfolioCalculatorTest` verde | R012-T03 |
| CA-06: entidad hidrata `datetime NULL`; consultas de 011 lo siguen ignorando | `MarketDataJpaAdapterIT` verde | R012-T01 |
| CA-07: suite y cobertura | `./gradlew compileJava test` y `./gradlew check` | Cierre W1/W2 |
| CA-08: ADR-011, banner y índice | `Test-Path` + greps | R012-T04 |
