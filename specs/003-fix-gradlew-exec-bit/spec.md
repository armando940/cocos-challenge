# Requerimiento 003 — Arreglar CI: `gradlew` sin bit de ejecutable (`Permission denied`, exit 126)

| Campo | Valor |
| :--- | :--- |
| **Número** | 003 |
| **Carpeta** | `specs/003-fix-gradlew-exec-bit/` |
| **Tipo** | bugfix |
| **Estado** | done |
| **Creado** | 2026-11-13 |
| **Actualizado** | 2026-11-13 |
| **Depende de** | 002 (workflow ya sintácticamente válido; este es el siguiente fallo de runtime) |
| **Supersede a** | — |

> **Alcance de este documento:** solo el **delta** de este requerimiento. Las invariantes del
> proyecto (hexagonal, `BigDecimal`, transacciones, locks, idempotencia, secretos) son globales y
> viven en `AGENTS.md` y en [`../000-baseline-challenge/spec.md`](../000-baseline-challenge/spec.md).
> No las repitas: referencialas. Los ADR aplicables están en `docs/adr/`.

---

## 1. Contexto y motivación

Tras el fix de 002, el workflow arranca pero falla en el primer step de Gradle:

```text
./gradlew: Permission denied
Error: Process completed with exit code 126.
```

Causa raíz verificada en local: el wrapper está commiteado **sin el bit de ejecutable**.

```text
$ git ls-files -s gradlew
100644 f5feea6d6b116baaca5a2642d4d9fa1f47d574a7 0	gradlew
```

El repo se desarrolla en **Windows**, donde el bit ejecutable no existe; al agregar `gradlew`
desde Windows, git lo registró con modo `100644` (archivo normal) en vez de `100755`. El runner
de GitHub Actions es `ubuntu-latest` (Linux), donde `./gradlew` exige permiso de ejecución → el
shell rechaza invocarlo con `Permission denied` y exit code `126` (EACCES / not executable).
Los line-endings **no** son problema: `git ls-files --eol gradlew` reporta `i/lf w/lf`.

## 2. Alcance

### Dentro

- **Fix de raíz (único cambio):** marcar `gradlew` como ejecutable en el índice de git
  (`git update-index --chmod=+x gradlew`) para que el árbol quede en modo `100755`.
- (Decisión del usuario 2026-11-13: sin cambios en el workflow; si con el modo `100755` el CI pasa, alcanza.)

### Fuera

- **No se toca `.github/workflows/ci.yml`**: sin step `chmod +x gradlew` ni ningún otro cambio
  (el gate `vars.ENABLE_DOCKER_PUBLISH == 'true'` de `docker-build` queda intacto → T-47 se mantiene).
- **No** se toca código Java, Gradle (build scripts), Dockerfile ni documentación de la app.
- **No** se agrega un `.gitattributes` global (alcance mínimo; queda como pendiente opcional).
- **No** se reintentan jobs ni se cambia la estrategia de caché de Gradle.

## 3. Criterios de aceptación

| ID | Given | When | Then |
| :--- | :--- | :--- | :--- |
| CA-01 | Fix aplicado y commiteado | `git ls-files -s gradlew` | El modo reportado es `100755` |
| CA-02 | Fix aplicado | Corrida de GitHub Actions (`build-and-test`) | Ningún step falla con `Permission denied` / exit `126` por `gradlew` |
| CA-03 | Fix aplicado | `git diff --name-only` (estado staged/working) | Solo aparece `gradlew` (cambio de modo); `.github/workflows/ci.yml` y el resto del repo intactos |
| CA-04 | Repo auditado | `git ls-files -s` filtrado por scripts | Ningún otro archivo shell requiere el fix (`gradlew.bat` es inválido como objetivo: Windows no usa bit ejecutable) |

## 4. Contratos afectados

No cambia ningún contrato: endpoints, requests/responses, códigos de error, eventos, tablas o
configuración de la aplicación permanecen idénticos. Es un cambio exclusivo de metadatos git + CI.

## 5. Impacto en requerimientos previos

- **002 (workflow válido):** se mantiene; este requerimiento no toca la estructura de jobs/steps
  salvo, opcionalmente, sumar un step nuevo al inicio de `build-and-test`.
- **T-47 (baseline, CI secret-safe):** intacto; `docker-build` sigue gateado por
  `vars.ENABLE_DOCKER_PUBLISH == 'true'`.
- **T-42/T-43 (demo y release tag):** desbloqueados en su precondición de CI verde; siguen
  pendientes por sí mismos.

## 6. NFR específicos

Sin NFR adicionales: aplican las invariantes de `AGENTS.md`. El cambio es de metadatos git/CI.

## 7. Supuestos y pendientes

- **DECIDIDO (usuario, 2026-11-13):** solo fix de raíz en el índice de git; nada en el workflow.
- **SUPUESTO:** el cambio de modo persiste aunque el repo se clone en Windows: git guarda el modo
  en el índice/árbol del commit; `core.fileMode=false` (default en Windows) no lo revierte.
- **SUPUESTO (verificado en due diligence):** `gradlew.bat` no requiere fix (formato batch de
  Windows) y el Dockerfile no usa el wrapper (invoca `gradle` directo sobre imagen oficial), por
  lo que no hay segundo punto de fallo por este modo.
- **PENDIENTE (aceptado):** si alguien re-agrega `gradlew` desde Windows en el futuro, el modo
  puede volver a `100644` y el CI volvería a fallar con exit `126`; se acepta el riesgo a cambio
  del diff mínimo. Un `.gitattributes` queda como mejora opcional futura (fuera de alcance).

## 8. Definition of Done

- [x] Criterios de aceptación CA-01..CA-04 verificados (ver `review.md`)
- [x] Sin cambios en código Java/Gradle/workflow/Dockerfile: `git diff --name-only` limitado a `gradlew`
- [x] `./gradlew compileJava` como smoke test local (no se requieren tests nuevos: cero código tocado)
- [ ] Commit aplicado con el cambio de modo (el fix es inútil sin push a GitHub)
- [x] Documentación actualizada (spec, plan, tasks, review de este requerimiento)
- [x] Reviewer emitió ✅ en `review.md`
