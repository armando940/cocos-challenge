# Plan de Ejecución — 011 Alinear nullability de MarketDataEntity con el esquema legacy

> Derivado de [`spec.md`](spec.md) y del estado real del repo el 2026-11-16.
> Los IDs de tarea son `R011-T01`, `R011-T02`… y solo existen dentro de este requerimiento.

## Resumen

- **Fases**: 3 (Entidad + repositorio; Tests de integración; ADR + verificación)
- **Olas**: 3 (W1, W2, W3) — 2 tareas en paralelo en W3 (`R011-T03`, `R011-T04`)
- **Horas estimadas**: ~3.5 h de implementación + ~0.5 h de verificación del coordinator
- **Camino crítico**: `R011-T01 → R011-T02 → R011-T03`
- **Riesgo mayor**: `R011-T02` — renombrar la query derivada y filtrar la native `DISTINCT ON` sin perder el `LIMIT 1` ni la cardinalidad por instrumento; una derivación inválida rompe el arranque de Spring Data.

## Estado actual vs plan

| Fase | Objetivo | Estado | Evidencia |
| :--- | :--- | :--- | :--- |
| 1 | Quitar `nullable = false` de `high/low/open`; excluir `datetime IS NULL` en las dos consultas de último precio; eliminar el método sin uso | ✅ Completado (W1+W2) | `grep -n "nullable = false" src/main/java/ar/com/cocos/challenge/infrastructure/adapter/out/persistence/MarketDataEntity.java` solo muestra `instrumentId`, `close`, `previousClose`, `datetime`, `createdAt`; `grep -n "DatetimeIsNotNull\|datetime IS NOT NULL" src/main/java/ar/com/cocos/challenge/infrastructure/adapter/out/persistence/MarketDataJpaRepository.java` |
| 2 | Tests de integración para CA-03 (OHLC legacy en `NULL`) y CA-04 (fila con `datetime` en `NULL` ignorada) | ✅ Completado (W3) | `./gradlew test --tests "ar.com.cocos.challenge.integration.MarketDataJpaAdapterIT"` |
| 3 | ADR 010 y verificación final de la suite/cobertura | ✅ Completado (W3; `test` y `check` verdes) | `Test-Path docs/adr/010-marketdata-nullability-legacy.md`; `./gradlew compileJava test`; `./gradlew check` |

## Cambios concretos por archivo

### 1. `MarketDataEntity.java`

- En `high`, `low` y `open` (líneas 33-40 actuales) quitar `nullable = false`; conservar `precision = 18, scale = 4`.
- **No** tocar `instrumentId`, `close`, `previousClose`, `datetime` ni `createdAt`: siguen con `nullable = false` (CA-02).
- Actualizar el Javadoc de la clase para explicar que el dump legacy puede traer OHLC en `NULL` cuando no hubo operación, y que `close`, `previousClose` y `datetime` sí son obligatorios porque `MarketPrice` los exige.

Resultado esperado en las tres columnas:

```java
@Column(precision = 18, scale = 4)
private BigDecimal high;

@Column(precision = 18, scale = 4)
private BigDecimal low;

@Column(precision = 18, scale = 4)
private BigDecimal open;
```

### 2. `MarketDataJpaRepository.java`

- **Eliminar** `findByInstrumentIdOrderByDatetimeDesc` (líneas 18-19 actuales). Verificado con `grep`: solo aparece su declaración; no tiene consumidores.
- **Renombrar** la query derivada de un solo resultado:

```java
Optional<MarketDataEntity> findFirstByInstrumentIdAndDatetimeIsNotNullOrderByDatetimeDesc(Long instrumentId);
```

  El prefijo `findFirst` hace que Spring Data aplique `LIMIT 1`. No reemplazar por `@Query` sin límite: un `Optional` con más de una fila lanzaría `IncorrectResultSizeDataAccessException`.
- **Agregar el filtro** a la native query de `findLatestPrices`, preservando `DISTINCT ON` y su `ORDER BY`:

```java
@Query(value = "SELECT DISTINCT ON (instrument_id) * FROM marketdata WHERE instrument_id IN :instrumentIds AND datetime IS NOT NULL ORDER BY instrument_id, datetime DESC", nativeQuery = true)
List<MarketDataEntity> findByInstrumentIdInOrderByDatetimeDesc(@Param("instrumentIds") Set<Long> instrumentIds);
```

  `DISTINCT ON` sigue garantizando 1 fila por instrumento; el `WHERE` nuevo no cambia la cardinalidad. Sin el filtro, `ORDER BY datetime DESC` en PostgreSQL ordena `NULLS FIRST` y una fila sin timestamp ganaría como “última”.

### 3. `MarketDataJpaAdapter.java` — desvío de alcance justificado

- Actualizar la única llamada de `findLatestPrice`:

```java
return jpaRepository.findFirstByInstrumentIdAndDatetimeIsNotNullOrderByDatetimeDesc(instrumentId)
        .map(PersistenceMapper::toDomain);
```

- No cambiar la firma pública del puerto ni la lógica de `findLatestPrices`. El spec dice “sin cambiar la API pública del adapter”; este cambio es interno.
- **Justificación de la ruta extra**: el spec no lista este archivo en “Dentro”, pero el método renombrado es consumido únicamente por `MarketDataJpaAdapter` (verificado con `grep`). Sin actualizar esta línea el proyecto no compila. La alternativa de mantener el nombre viejo con `@Query` + `LIMIT 1` queda como fallback en `R011-T90`, pero pierde la query derivada y su límite automático.

### 4. `MarketDataJpaAdapterIT.java`

- Agregar `findLatestPrice_legacyOhlcNulls_returnsMarketPrice` (CA-03): crear un instrumento propio (p. ej. `TESTE`), persistir una fila con `high/low/open = null`, `close = 20.50`, `previousClose = 20.50` y `datetime` válido; invocar `findLatestPrice` y verificar `close`, `previousClose` y que no haya excepción.
- Agregar `findLatestPriceAndLatestPrices_ignoreNullDatetime` (CA-04): crear un instrumento propio (p. ej. `TESTF`), persistir una fila con `datetime = null` y `close` más alto (p. ej. `999.00`) y otra con `datetime` válido y `close = 100.00`; verificar que `findLatestPrice` devuelve `100.00` y que `findLatestPrices(Set.of(id))` devuelve solo la fila con timestamp.
- Reutilizar el helper `createMarketData(...)`: ya acepta `BigDecimal` e `Instant` nulos. No modificarlo salvo que haga falta, y en ese caso mantener el mismo archivo.
- Usar tickers e ids propios; no depender de los instrumentos `12`/`53` del seed V1 ni de los tickers `TESTA`–`TESTD` de los tests existentes. La clase usa `@SpringBootTest` + Testcontainers y comparte la base entre tests.

### 5. `docs/adr/010-marketdata-nullability-legacy.md`

Crear el ADR con estas secciones:

- **Contexto**: `V1`/`V2` dejan `high`, `low`, `open` y `datetime` nullable; la entidad declaraba `NOT NULL` en OHLC; `ddl-auto: validate` no valida constraints de nulabilidad.
- **Decisión**: alinear `high/low/open` con Flyway; mantener `close`, `previousClose` y `datetime` no nulos en la entidad por contrato de `MarketPrice`; filtrar `datetime IS NOT NULL` en `findFirst…` y en la native `DISTINCT ON`; eliminar el método sin uso.
- **Consecuencias**: las filas legacy con OHLC `NULL` se hidratan; las filas sin timestamp se ignoran; no hay cambios de esquema ni de contrato REST; `datetime` sigue siendo una discrepancia consciente entre entidad y BD, neutralizada en consultas.
- **Alternativas descartadas**: backfill/`COALESCE` de OHLC (el `NULL` es un dato válido), volver nullable `datetime` o `close`/`previousClose` en la entidad (rompe `MarketPrice`), agregar `NOT NULL` en migración (rechaza el dump legacy), generar DDL desde entidades (crea constraints falsos).
- **Referencias**: `specs/011-marketdata-legacy-nullable-ohlc/spec.md`, `V1__baseline_prod_schema.sql`, `V2__align_app_schema.sql`, ADR 006.

## Fase 1 — Entidad y repositorio (~1.5 h)

**Objetivo**: Alinear la entidad con el esquema legacy y hacer que las dos consultas de último precio ignoren filas sin `datetime`.
**Entregable**: `MarketDataEntity.java` sin `NOT NULL` en OHLC; `MarketDataJpaRepository.java` con la query derivada filtrada, la native filtrada y sin el método muerto; `MarketDataJpaAdapter.java` actualizado.
**DoD**: `./gradlew compileJava` verde; los `grep` de la tabla de estado muestran los cambios y no queda la declaración de `findByInstrumentIdOrderByDatetimeDesc`.

| ID | Tarea | Depende de | Estimación | Riesgo | Done |
| :--- | :--- | :--- | :--- | :--- | :--- |
| R011-T01 | Quitar `nullable = false` de `high`, `low` y `open`; documentar OHLC legacy opcional y mantener no nulos `instrumentId`, `close`, `previousClose`, `datetime`, `createdAt` | — | 0.5 h | Bajo | `grep -n "nullable = false" src/main/java/ar/com/cocos/challenge/infrastructure/adapter/out/persistence/MarketDataEntity.java` solo muestra los cinco campos que deben seguir no nulos |
| R011-T02 | Eliminar `findByInstrumentIdOrderByDatetimeDesc`; renombrar `findFirst…` a `findFirstByInstrumentIdAndDatetimeIsNotNullOrderByDatetimeDesc`; agregar `datetime IS NOT NULL` a la native `DISTINCT ON`; actualizar la llamada en `MarketDataJpaAdapter` | R011-T01 (serialización por acoplamiento funcional, no por compilación) | 1 h | Medio | `grep -n "findFirstByInstrumentIdAndDatetimeIsNotNullOrderByDatetimeDesc"` aparece en repositorio y adapter; `grep -n "datetime IS NOT NULL"` en el repositorio; `./gradlew compileJava` verde |

## Fase 2 — Tests de integración (~1.5 h)

**Objetivo**: Probar la hidratación de una fila legacy con OHLC `NULL` y la exclusión de filas con `datetime` `NULL` en ambos caminos del adapter.
**Entregable**: Dos tests nuevos en `MarketDataJpaAdapterIT` que cubren CA-03 y CA-04.
**DoD**: `./gradlew test --tests "ar.com.cocos.challenge.integration.MarketDataJpaAdapterIT"` verde, incluidos los tests existentes de R007.

| ID | Tarea | Depende de | Estimación | Riesgo | Done |
| :--- | :--- | :--- | :--- | :--- | :--- |
| R011-T03 | Agregar `findLatestPrice_legacyOhlcNulls_returnsMarketPrice` y `findLatestPriceAndLatestPrices_ignoreNullDatetime` con datos propios y sin depender del seed | R011-T01, R011-T02 | 1.5 h | Medio | `./gradlew test --tests "ar.com.cocos.challenge.integration.MarketDataJpaAdapterIT"` verde |

## Fase 3 — ADR y verificación (~1 h)

**Objetivo**: Registrar la decisión de alineación y cerrar la verificación completa.
**Entregable**: ADR `010-marketdata-nullability-legacy.md`; suite y cobertura verdes.
**DoD**: el ADR existe con decisión/consecuencias/alternativas; `./gradlew compileJava test` y `./gradlew check` verdes (los ejecuta el coordinator al cerrar W3).

| ID | Tarea | Depende de | Estimación | Riesgo | Done |
| :--- | :--- | :--- | :--- | :--- | :--- |
| R011-T04 | Escribir `docs/adr/010-marketdata-nullability-legacy.md` con contexto, decisión, consecuencias y alternativas | R011-T01, R011-T02 | 0.5 h | Bajo | `Test-Path docs/adr/010-marketdata-nullability-legacy.md` y `grep -n "## Decisión\|## Consecuencias\|## Alternativas" docs/adr/010-marketdata-nullability-legacy.md` |

> La verificación final **no** es una tarea de implementer ni un lane: la corre el coordinator al cerrar W3 con `./gradlew compileJava test` y `./gradlew check`.

## Olas de ejecución

| Ola | Tareas en paralelo | Lanes | Serializa | Verificación de cierre |
| :-- | :-- | :-- | :-- | :-- |
| W1 | R011-T01 | 1 | — | `./gradlew compileJava` (coordinator) |
| W2 | R011-T02 | 1 | Acoplamiento funcional con T01: misma cadena de persistencia y el adapter consume entidad/repositorio; se evita un build intermedio desalineado | `./gradlew compileJava test --tests "ar.com.cocos.challenge.integration.MarketDataJpaAdapterIT"` (coordinator) |
| W3 | R011-T03, R011-T04 | 2 | —; tests y ADR tocan rutas disjuntas (`src/test/...` vs `docs/adr/...`) | `./gradlew compileJava test` y `./gradlew check` (coordinator) |

> W1 y W2 van con un solo lane porque `MarketDataEntity` y `MarketDataJpaRepository`/`MarketDataJpaAdapter` comparten el contrato de persistencia y el adapter consume ambos. W3 sí paraleliza tests y ADR porque no hay dependencia entre ellos ni solapamiento de archivos. Máximo 2 lanes.

## Camino crítico

`R011-T01 → R011-T02 → R011-T03 → cierre W3`

- `R011-T01` debe dejar la entidad alineada antes de tocar las consultas.
- `R011-T02` debe compilar y mantener el arranque de Spring Data antes de escribir los tests.
- `R011-T03` valida CA-03/CA-04 y desbloquea el cierre de W3.
- `R011-T04` depende de T02 pero corre en paralelo con T03 en W3; está en el DoD (CA-07), no en el camino crítico de build.

## Riesgos y mitigación

| Riesgo | Probabilidad | Impacto | Mitigación |
| :--- | :--- | :--- | :--- |
| `ORDER BY datetime DESC` en PostgreSQL ordena `NULLS FIRST`: una fila legacy sin timestamp gana como “última” y `MarketPrice` falla por `datetime` nulo | Media | Alto | Filtrar `datetime IS NOT NULL` en **ambas** consultas; test CA-04 cubre `findLatestPrice` y `findLatestPrices`; no confiar en `NULLS LAST` como sustituto del filtro |
| La query derivada renombrada no se resuelve al arrancar Spring Data (nombre inválido o propiedad mal derivada) | Media | Alto | Verificar con `./gradlew test --tests "*MarketDataJpaAdapterIT"`; fallback `R011-T90` con `@Query` nativa `... ORDER BY datetime DESC LIMIT 1` |
| Reemplazar la derivada `findFirst…` por un `@Query` sin `LIMIT 1` y recibir más de una fila, provocando `IncorrectResultSizeDataAccessException` | Baja | Alto | Conservar `findFirstByInstrumentIdAndDatetimeIsNotNullOrderByDatetimeDesc`; si se usa SQL explícito, incluir `LIMIT 1` |
| La native `DISTINCT ON` pierde el filtro o se altera su `ORDER BY` y devuelve una fila sin timestamp o más de una por instrumento | Baja | Alto | Mantener `WHERE ... AND datetime IS NOT NULL` y `ORDER BY instrument_id, datetime DESC`; el test de CA-04 verifica 1 entrada por instrumento con `close` válido |
| El `toMap` de `findLatestPrices` no tiene merge function: si la query devolviera duplicados por instrumento, falla con `IllegalStateException` | Baja | Medio | `DISTINCT ON` garantiza 1 fila por instrumento; no cambiar la cardinalidad al agregar el filtro; test de CA-04 lo cubre |
| Los tests nuevos comparten la base Testcontainers con los tests existentes y el seed V1 (incluidos los instrumentos `12`/`53` con OHLC `NULL`) | Media | Medio | Usar instrumentos y tickers propios (`TESTE`, `TESTF`), no reutilizar ids del seed, y consultar por el id devuelto al persistir |
| `MarketDataEntity` sigue con `datetime nullable = false` aunque la columna es nullable; si alguien genera DDL desde JPA, se crea un `NOT NULL` que rechaza filas legacy sin timestamp | Baja | Medio | Es una decisión consciente del spec; el ADR lo documenta y las consultas filtran. `ddl-auto: validate` no valida nulabilidad, así que el build no falla |
| El cambio exige tocar `MarketDataJpaAdapter.java`, archivo no listado en “Dentro” del spec ni en las rutas iniciales | Media | Medio | Incluido en `R011-T02` con justificación: es el único consumidor del método renombrado. Alternativa en `R011-T90` si el coordinator rechaza el desvío |
| `docs/adr/README.md` no indexa el ADR 010 y queda un índice incompleto | Baja | Bajo | Fuera de las rutas permitidas y de CA-07; se reporta al coordinator como pendiente documental, no se toca en este requerimiento |

## Hallazgos y desvíos respecto del spec/código

| # | Hallazgo | Impacto | Resolución en este plan |
| :--- | :--- | :--- | :--- |
| 1 | El spec y la lista inicial de rutas no incluyen `MarketDataJpaAdapter.java`, pero renombrar `findFirstByInstrumentIdOrderByDatetimeDesc` exige actualizar su única llamada | El build no compila si no se toca | `R011-T02` incluye el adapter con justificación; fallback en `R011-T90` |
| 2 | `datetime` es nullable en Flyway (V1 `"date" DATE`, V2 solo renombra/convierte), pero el spec ordena mantener `nullable = false` en la entidad | La entidad no refleja exactamente la BD; un DDL generado desde JPA sería incorrecto para `datetime` | Se mantiene por contrato de `MarketPrice` (SUPUESTO del spec); el ADR documenta la excepción y las consultas filtran |
| 3 | `findByInstrumentIdOrderByDatetimeDesc` no tiene consumidores (solo su declaración aparece en el repo) | Código muerto | Se elimina en `R011-T02` (preferido) |
| 4 | `findFirstByInstrumentIdOrderByDatetimeDesc` es derivada con `LIMIT 1`; si se convierte a `@Query` sin límite, `Optional` puede explotar con más de una fila | CA-04/T02 | Se conserva derivada renombrada; el fallback usa `LIMIT 1` explícito |
| 5 | La native `DISTINCT ON (instrument_id) ... ORDER BY instrument_id, datetime DESC` no filtra `NULL`; `DESC` pone los `NULL` primero en PostgreSQL | Una fila sin timestamp ganaría como última | `datetime IS NOT NULL` en el `WHERE`; test CA-04 |
| 6 | `MarketDataJpaAdapter.findLatestPrices` usa `toMap` sin merge function | Duplicados por instrumento lanzarían excepción | `DISTINCT ON` mantiene 1 fila por instrumento; el filtro nuevo no cambia la cardinalidad |
| 7 | El ADR 010 no está en el índice `docs/adr/README.md` (lista 001–009) | Índice desactualizado | Fuera de rutas/spec; se reporta al coordinator, no se modifica |

## Definition of Done global

| Criterio | Verificación | Tarea |
| :--- | :--- | :--- |
| CA-01: `high/low/open` sin `nullable = false` | `grep -n "nullable = false" .../MarketDataEntity.java` solo muestra `instrumentId`, `close`, `previousClose`, `datetime`, `createdAt` | R011-T01 |
| CA-02: `instrumentId/close/previousClose/datetime/createdAt` siguen no nulos | Mismo `grep` que CA-01 | R011-T01 |
| CA-03: fila legacy con OHLC `NULL` hidrata `MarketPrice` | `MarketDataJpaAdapterIT.findLatestPrice_legacyOhlcNulls_returnsMarketPrice` verde | R011-T03 |
| CA-04: fila con `datetime` `NULL` ignorada por ambos métodos | `MarketDataJpaAdapterIT.findLatestPriceAndLatestPrices_ignoreNullDatetime` verde | R011-T03 |
| CA-05: suite completa verde | `./gradlew compileJava test` | Cierre W3 (coordinator) |
| CA-06: cobertura ≥ 80 % en servicios de dominio | `./gradlew check` | Cierre W3 (coordinator) |
| CA-07: ADR creado con decisión y justificación | `Test-Path docs/adr/010-marketdata-nullability-legacy.md` | R011-T04 |
