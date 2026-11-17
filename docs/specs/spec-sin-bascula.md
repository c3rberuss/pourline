# SPEC-007: Preparar sin báscula

| Campo | Valor |
|---|---|
| Estado | Aprobado. Plan aprobado: [`plan-sin-bascula.md`](plan-sin-bascula.md) |
| Fecha | 2026-11-17 |
| Rama | `feat-recipe-without-scale`, avanzada a `develop` (`0aa1829`) |
| Deriva de | Uso real: la app solo sirve con la báscula conectada |
| Módulos afectados | `:app`: `brew`, `session`, `history` (migración v5 → v6), `share`, `ui/prepare`, `ui/session`, `ui/history`, `ui/scale` |
| Specs relacionados | [SPEC-002](spec-historial.md), [SPEC-003](spec-curva-objetivo.md), [SPEC-005](spec-navegacion-modos.md), [SPEC-006](spec-molinos.md) |

## 1. Resumen y Contexto

- **Problema:**
  - "Empezar" en Preparar solo se habilita con la báscula `Ready`
    (`PrepareViewModel`: `canStart = scale.connection == ConnectionState.Ready`).
  - La sesión guiada empieza siempre con los pasos previos de tara y dosis (`PrepFlow`), y la
    `BrewSession` pasa a `Active` con el agua que mide la báscula.
  - Sin la báscula a mano no se puede seguir una receta, ni valorarla, ni dejar la preparación en
    el historial o en la bitácora del café.
- **Solución:**
  - Un botón secundario **"Empezar sin báscula"** en Preparar, siempre visible.
  - Una **sesión sin báscula**, guiada solo por el reloj:
    - sin pasos previos;
    - el dial muestra el tiempo del paso;
    - los avisos de etapa y la notificación funcionan igual que con báscula;
    - al terminar hay resumen, valoración y guardado en el historial.
  - Los datos que solo da la báscula no se registran: vertidos reales, curva, flujo, estabilidad y
    ajuste al objetivo. La dosis y el agua son las del plan, y se pueden corregir después.
- **Éxito:**
  - Sin ninguna báscula conectada, un usuario sigue la receta "V60 clásica" de principio a fin,
    la valora y la ve en el historial con la etiqueta "Sin báscula".
  - Las preparaciones con báscula, real o simulada, se comportan exactamente igual que antes.
    Los tests existentes siguen pasando sin cambios en sus expectativas.
- **Alcance (In):**
  - Botón "Empezar sin báscula" en Preparar, con receta o en preparación libre.
  - Sesión sin báscula: estado listo, estado en curso y resumen, con avisos de etapa,
    `BrewService` y diálogo de salida.
  - Guardado en `brews` con una marca de origen nueva (migración v5 → v6).
  - Corrección de dosis y agua en "Añadir detalles", solo en estas preparaciones.
  - Historial, detalle, exclusiones (Comparar, referencia, "guardar como receta", progreso de
    estabilidad) y compartir sin curva.
- **Alcance (Out):**
  - Pausar y reanudar el cronómetro.
  - Pasar de una sesión sin báscula a una con báscula a mitad de la preparación.
  - Registrar vertidos a mano o marcar cada paso como hecho.
  - Unidades distintas de gramos en la sesión sin báscula.
  - Cambios en la sesión con báscula, en `PrepFlow` o en el protocolo BLE.

## 2. Historias de Usuario y Casos de Uso

- **US-1:** Como usuario sin la báscula a mano, quiero empezar una preparación con una receta para
  que la app me guíe paso a paso por tiempo.
- **US-2:** Como usuario sin receta, quiero un cronómetro con mi dosis y mi agua objetivo, para
  preparar y registrar el café igualmente.
- **US-3:** Como usuario, quiero que me avise cuando cambia el paso, también con la pantalla
  apagada, para no tener que mirar el móvil todo el rato.
- **US-4:** Como usuario, al terminar quiero valorar la preparación y añadir notas, para recordar
  qué tal salió ese café con esa receta.
- **US-5:** Como usuario que se desvió de la receta, quiero corregir la dosis y el agua guardadas,
  para que el ratio del historial sea el real.
- **US-6:** Como usuario, quiero distinguir en el historial qué preparaciones se hicieron sin
  báscula, para no compararlas con las que tienen curva.

## 3. Especificación Técnica

### 3.1 Entrada desde Preparar

- Debajo de "Empezar" aparece un botón secundario **"Empezar sin báscula"**
  (`testTag("prepare_start_scaleless")`), **siempre visible**, con la báscula lista o sin ella.
- Se habilita con las mismas condiciones que el plan de "Empezar", salvo la báscula:
  - con receta, siempre;
  - sin receta, solo si la dosis y el ratio de Preparar son válidos (los campos libres sin error
    de rango).
- El texto de ayuda `prepare_start_disabled` se mantiene para "Empezar". Con la báscula sin
  conectar, el botón nuevo es el camino visible.
- Al pulsarlo:
  - si hace falta, se pide el permiso de notificaciones, igual que con "Empezar";
  - se llama a `PrepareViewModel.startScalelessSession()`, que llama a
    `ScaleSessionManager.startScalelessSession(plan)`;
  - se navega a la misma pantalla de sesión.
- Si hay una báscula conectada, la conexión no se toca, pero sus pesos no llegan a la sesión
  sin báscula (§3.3).

### 3.2 Modelo de la sesión sin báscula

- Nueva clase pura en `brew/`, por ejemplo `TimedSession`, sin Android ni báscula, análoga a
  `BrewSession`.
  - Usa los mismos `BrewPhase`:
    - `Armed`: listo para empezar;
    - `Active(pouring = false)`: el reloj corre;
    - `Finished(summary, …)`: terminada.
  - Operaciones:
    - `start(nowMs)`: pasa de `Armed` a `Active`, con `t0 = nowMs`.
    - `finish(nowMs, reason)`: pasa de `Active` a `Finished` y emite `BrewEffect.Finished`.
    - `tick(nowMs)`: si `nowMs - t0 >= BrewConfig.maxDurationMs` (15 min), termina con
      `FinishReason.MAX_DURATION`.
    - `discard()`: vuelve a `Armed` sin emitir nada.
  - El `BrewSummary` que produce tiene:
    - `totalTimeMs`: el tiempo transcurrido;
    - `totalWaterG`: el agua del plan (§3.5);
    - `avgFlowGps = 0.0` y `maxFlowGps = 0.0`;
    - `stabilityScore = null`, `stabilityLevel = null` y `levelShare` vacío;
    - `pours` vacío;
    - `finalRatio`: agua del plan / dosis del plan;
    - `reason`: `MANUAL` o `MAX_DURATION`;
    - `interrupted = false`.
  - El `FinishedBrew` lleva `samples` vacío.
- La implementación concreta (clase nueva o modo dentro de `BrewSession`) la decide el plan. El
  contrato es que `BrewSession` y `PrepFlow` no cambian de comportamiento con báscula.

### 3.3 `ScaleSessionManager` y `ScaleUiState`

- `ScaleUiState` gana `scaleless: Boolean` (por defecto `false`). Es `true` desde
  `startScalelessSession` hasta `endSession` o `discardSession`.
- `startScalelessSession(plan: BrewPlan)`:
  - se ignora con una sesión `Active`, sea del tipo que sea;
  - congela el objetivo, el café y la molienda, como `startSession`;
  - no crea `PrepFlow`: `prep` queda en `null`;
  - deja la sesión en `Armed`.
- `scalelessStartTimer()`: pasa a `Active`.
- Con `scaleless = true`, `onMessage` no pasa pesos, taras ni estados del temporizador a ninguna
  sesión. La tarjeta de la báscula puede seguir actualizándose.
- Cuando la báscula conecta, se desconecta o se reconecta, la sesión sin báscula sigue igual. No se
  muestra `DisconnectedNotice` y `FinishReason.DISCONNECTED` no aplica.
- El reloj (`elapsedMs`) y la guía (`guide`) se publican igual que con báscula.
  - `TargetGuide.at(target, phase, tMs, waterG = null, config)` da `deviationG` y
    `deviationLevel` en `null`.
  - `StageAlertTracker.update(t0, target, tMs)` y `StageNotifier` se usan sin cambios.
- `finishBrew()`, `endSession()`, `discardSession()` y `restartPrep()` funcionan también en la
  sesión sin báscula:
  - `restartPrep()` desde `Finished` empieza otra sesión sin báscula con el mismo plan;
  - desde `Armed` no hace nada.
- `save(brew)` construye el `BrewContext` con:
  - `unit = WeightUnit.GRAMS`;
  - `scaleModel = null`;
  - `simulated = false`;
  - `scaleless = true` (campo nuevo, §3.5);
  - `target = UsedTarget(…, fit = null)`. Sin ajuste, porque no hay curva.
- `BrewKeepAlive.needsKeepAlive()` cubre la sesión sin báscula en `Active` y mientras se guarda.
  - `toBrewNotice()` muestra el tiempo y el paso.
  - No muestra los gramos vertidos, pero sí el objetivo del paso si es `POUR`.

### 3.4 Pantalla de sesión

- **Barra superior y menú ⋯:** iguales. En el menú ⋯, "Unidad" se deshabilita y no aparece la
  simulación. "Reiniciar" se comporta como en §3.3.
- **`Armed` (en lugar de `PrepPanel`):**
  - el primer paso de la receta (nombre, tipo, instrucción y objetivo de agua si es `POUR`);
  - la lista de pasos (`StepList`);
  - un botón primario **"Empezar timer"** (`testTag("scaleless_start")`);
  - en preparación libre: la dosis, el ratio y el agua objetivo.
- **`Active` con receta:**
  - el mismo dial (`BrewDial`). Su progreso es el tiempo transcurrido del paso actual sobre su
    duración;
  - en el centro, el cronómetro y una línea de acción:
    - `POUR`: "Vierte hasta X g", con el `targetWaterG` acumulado del paso;
    - `SWIRL` y `WAIT`: la instrucción del paso o el texto de su tipo;
  - debajo, "paso n/N · quedan m:ss";
  - después, el paso actual (`CurrentStep`), el siguiente con su cuenta atrás (`NextStep`) y la
    lista de pasos;
  - sin flujo, sparkline, estabilidad, desviación ni gráfica, en ninguno de los dos modos;
  - el botón "Terminar".
- **`Active` en preparación libre:**
  - el dial sin progreso de pasos, con el cronómetro en el centro y "Agua objetivo: X g";
  - no hay avisos de etapa;
  - el botón "Terminar".
- **Último paso completado** (`guide.allStagesDone`):
  - una alerta informativa persistente: "Pasos completados. Pulsa Terminar cuando acabe de
    filtrar" (`testTag("scaleless_done_hint")`);
  - un único aviso de `StageNotifier`;
  - la sesión no termina sola, salvo por `MAX_DURATION`.
- **Salir:**
  - la X o "atrás" en `Active` abre el diálogo de salida actual (Guardar / Descartar /
    Continuar);
  - en `Armed` cierra sin guardar.
- **Resumen (`SessionSummary`):**
  - el tiempo total, el agua y la dosis del plan, el ratio y el café;
  - una etiqueta **"Sin báscula"** (`testTag("summary_scaleless")`);
  - "Añadir detalles" para valorar y escribir notas, y "Compartir";
  - en Experto, solo el motivo de fin. Se ocultan el flujo, la estabilidad y el ajuste.

### 3.5 Modelo de datos y migración v5 → v6

- `BrewEntity` gana `scaleless: Boolean = false`. La migración `MIGRATION_5_6` ejecuta
  `ALTER TABLE brews ADD COLUMN scaleless INTEGER NOT NULL DEFAULT 0`, y `HistoryDatabase` pasa a
  `version = 6`, con su esquema exportado.
- `BrewContext` gana `scaleless: Boolean = false`. `BrewListRow` y `BrewListItem` ganan
  `scaleless`.
- Valores guardados en una preparación sin báscula:
  - `doseG`: con receta, la dosis escalada del objetivo (`ActiveTarget.scaledDoseG`, o
    `nominalDoseG` si no hay escala); sin receta, `BrewPlan.doseG`.
  - `totalWaterG`: con receta, el agua final de la curva objetivo escalada; sin receta,
    `BrewPlan.doseG × BrewPlan.ratio`.
  - `finalRatio = totalWaterG / doseG`.
  - `avgFlowGps = 0`, `maxFlowGps = 0`, `stabilityScore = null`, `stabilityLevel = null` y
    `share* = 0`.
  - `unit = GRAMS`, `scaleModel = null`, `simulated = false`.
  - `preview`: 48 bytes a cero.
  - `fitScore = null`. `referenceType`, `referenceId`, `referenceName`, `targetWaterG` y
    `targetEndMs` se rellenan como hoy cuando hay receta.
- `brew_curves` conserva la relación 1:1: se guarda una curva válida sin muestras ni vertidos,
  codificada con `BrewCurveCodec`. Así `DeletedBrew`, el deshacer y `BrewRecord` no cambian de
  forma. Si el codec no admite cero muestras, el plan lo ajusta sin romper las curvas existentes.
- `brew_targets` guarda la copia de la curva objetivo, como hoy, cuando hay receta.
- **Edición:** en `BrewEditScreen`/`BrewEditForm`, si `entity.scaleless`, aparecen los campos
  editables "Dosis (g)" y "Agua (g)" (`testTag("brew_edit_dose")`, `testTag("brew_edit_water")`).
  - Usan los mismos rangos válidos que la dosis libre de Preparar para la dosis.
  - Para el agua: mayor que 0 y como máximo `RecipeValidator.MAX_WATER_G` (1000 g).
  - Al guardar se recalcula `finalRatio`.
  - En preparaciones con báscula, estos campos no aparecen.

### 3.6 Historial, detalle y exclusiones

- **Lista (`HistoryRow`):**
  - etiqueta "Sin báscula" (`history_row_scaleless`), con el mismo estilo que "Simulada";
  - sin minicurva (no se dibuja la de ceros);
  - se ven el tiempo, el agua, el ratio y la valoración.
- **Filtros y bitácora del café:** las incluyen, como a cualquier preparación.
- **`BrewDao.progress`** (gráfica de progreso de estabilidad): ya las excluye, porque exige
  `stabilityScore IS NOT NULL`. Se añade `scaleless = 0` de forma explícita.
- **Comparar:**
  - `ComparePickViewModel` no las ofrece como B;
  - el detalle de una preparación sin báscula oculta la acción "Comparar".
- **Referencia y recetas:**
  - `TargetPickerViewModel.brews` las excluye;
  - el detalle oculta "Usar como referencia" y "Guardar como receta" (`RecipeFromBrew`).
- **Detalle (`BrewDetailScreen`):**
  - etiqueta "Sin báscula";
  - los datos (`BrewFactsCard`);
  - la curva objetivo guardada, si la hay, sin curva real, sin estabilidad, sin flujo y sin
    ajuste;
  - sin receta: sin gráfica.
- **Compartir (`BrewShareCard`):**
  - sin gráfica ni estabilidad;
  - con el tiempo, la dosis, el agua, el ratio, el café, la valoración, las notas y la marca
    "Sin báscula".
  - `BrewShareData` gana `scaleless: Boolean`.

### 3.7 Textos

Las cadenas nuevas, en `values` (es) y `values-en`:

| Clave | es | en |
|---|---|---|
| `prepare_start_scaleless` | Empezar sin báscula | Start without scale |
| `scaleless_start` | Empezar timer | Start timer |
| `scaleless_pour_to` | Vierte hasta %1$s g | Pour to %1$s g |
| `scaleless_step_progress` | paso %1$d/%2$d · quedan %3$s | step %1$d/%2$d · %3$s left |
| `scaleless_water_goal` | Agua objetivo: %1$s g | Water goal: %1$s g |
| `scaleless_done_hint` | Pasos completados. Pulsa Terminar cuando acabe de filtrar | All steps done. Tap Finish when it stops dripping |
| `history_row_scaleless` | Sin báscula | No scale |
| `brew_edit_dose` | Dosis | Dose |
| `brew_edit_water` | Agua | Water |

El plan puede reutilizar cadenas existentes equivalentes.

### 3.8 Flujo de ejecución

1. En Preparar, el usuario elige la receta (opcional), el café y la molienda, y pulsa "Empezar sin
   báscula".
2. `startScalelessSession(plan)`: la sesión queda en `Armed` con `scaleless = true`, y se navega a
   la sesión.
3. La pantalla muestra el primer paso y la lista. El usuario pulsa "Empezar timer" y la sesión
   pasa a `Active`: corren el reloj, la guía, los avisos y `BrewService`.
4. Al completarse el último paso aparece el aviso. El usuario pulsa "Terminar", o a los 15 min la
   sesión termina sola.
5. `Finished` lleva a `save()`, con la entidad `scaleless = true`, y al resumen.
6. "Añadir detalles": valoración, notas y corrección de la dosis y el agua.
7. "Listo": `endSession()` limpia `scaleless` y la dosis, y se vuelve a Preparar.

## 4. Casos Límite y Manejo de Errores

- La báscula se conecta, se reconecta o se desconecta durante la sesión sin báscula: no tiene
  efecto. No aparece aviso de desconexión y los pesos no se registran.
- La báscula está `Ready` y el usuario pulsa "Empezar sin báscula": la sesión es sin báscula y los
  pesos se ignoran.
- Se llega a 15 min en `Active`: termina con `MAX_DURATION`, se guarda y aparece el resumen.
- Al pulsar la X en `Active`: diálogo Guardar / Descartar / Continuar. "Descartar" no guarda
  nada.
- Falla el guardado: `SavedBrew.Failed` y el mensaje `scale_error_save`, como hoy.
- La receta se borra mientras la sesión está en curso: la sesión usa el objetivo congelado y se
  guarda igual.
- Preparación libre con la dosis o el ratio inválidos: el botón está deshabilitado.
- La app pasa a segundo plano o se apaga la pantalla: `BrewService` mantiene la sesión y los
  avisos, como con báscula.
- Se pulsa "Empezar sin báscula" con una sesión `Active` en curso: se ignora (no puede pasar desde
  la UI, pero el gestor lo protege).
- En la edición, una dosis o un agua fuera de rango: error en el campo y "Guardar" deshabilitado.
- Preparaciones anteriores a v6: `scaleless = 0` y se comportan como hoy.

## 5. Criterios de Aceptación (Definition of Done)

- [ ] Sin báscula conectada, "Empezar sin báscula" está visible y habilitado con una receta, y con
      la báscula `Ready` también está visible.
- [ ] Sin receta y con la dosis o el ratio inválidos, "Empezar sin báscula" está deshabilitado.
- [ ] La sesión sin báscula no muestra los pasos de tara y dosis: muestra el primer paso y
      "Empezar timer".
- [ ] En `Active` con receta, el dial avanza con el tiempo del paso, y la línea de acción y
      "paso n/N · quedan m:ss" coinciden con `TargetGuide` en cada instante.
- [ ] Los avisos de etapa (cuenta atrás y cambio de paso) se disparan en los mismos instantes que
      con la báscula simulada siguiendo la misma receta. Con la pantalla apagada, la notificación
      de `BrewService` muestra el tiempo y el paso.
- [ ] Al completar el último paso aparece `scaleless_done_hint` y la sesión sigue en `Active`
      hasta "Terminar".
- [ ] A los 15 min la sesión termina sola con `MAX_DURATION` y se guarda.
- [ ] La preparación guardada tiene `scaleless = 1`, la dosis y el agua del plan, el ratio
      correcto, el flujo en 0, la estabilidad y el ajuste en `null`, `unit = GRAMS` y la copia de
      la curva objetivo si había receta.
- [ ] Mientras dura la sesión sin báscula, los pesos que llegan de una báscula conectada no
      cambian el estado de la sesión (test JVM con una conexión simulada que emite pesos).
- [ ] La migración v5 → v6 conserva las preparaciones existentes con `scaleless = 0` (test de
      migración de Room).
- [ ] El historial muestra la etiqueta "Sin báscula" sin minicurva. Comparar, el selector de
      referencia y la gráfica de progreso no las incluyen. El detalle oculta "Comparar", "Usar
      como referencia" y "Guardar como receta".
- [ ] En "Añadir detalles" de una preparación sin báscula se editan la dosis y el agua, y el ratio
      se recalcula. En una con báscula esos campos no aparecen.
- [ ] Compartir una preparación sin báscula genera la imagen sin gráfica y con la marca "Sin
      báscula".
- [ ] Todos los tests JVM e instrumentados existentes pasan. Hay tests nuevos de:
  - JVM:
    - la sesión cronometrada: transiciones, `MAX_DURATION`, resumen y descarte;
    - `ScaleSessionManager` en modo sin báscula: inicio, guardado, pesos ignorados y
      reinicio;
    - `BrewRepository` y `BrewDao`: guardado, exclusiones y edición de dosis y agua;
    - la migración 5 → 6.
  - Instrumentados de Compose: Preparar → "Empezar sin báscula" → "Empezar timer" → "Terminar" →
    resumen → "Añadir detalles" (valoración) → historial con la etiqueta.
- [ ] Prueba manual en un dispositivo vía adb: una receta completa sin báscula, con la pantalla
      apagada a mitad, termina guardada y valorada.
- [ ] detekt y lint sin avisos nuevos. Se compila solo el módulo `:app`.
