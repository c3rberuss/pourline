# Plan de implementación: SPEC-007 Preparar sin báscula

| Campo | Valor |
|---|---|
| Spec | [`spec-sin-bascula.md`](spec-sin-bascula.md) (SPEC-007) |
| Estado | **Aprobado (2026-11-17).** P1: `specialUse` (D16). P2 a P4, como se proponen. Commits por fase permitidos. |
| Fecha | 2026-11-17 |
| Rama | `feat-recipe-without-scale`, avanzada a `develop` (`0aa1829`), con el commit del spec (`9ac240e`) |
| Módulos | `:app` (`:protocol` no cambia) |

El spec no trae fases, así que este plan las define. El orden va de dentro afuera: primero el dominio puro (la sesión
cronometrada y las cantidades del plan), después los datos (v6), luego el gestor, y por último las pantallas, el
servicio en segundo plano, el historial, la edición y compartir. Cada capa se prueba antes de que la siguiente la
use. La pantalla de sesión (F4) va antes del botón de Preparar (F5) para que, cuando el botón exista, el recorrido
completo ya funcione.

El contrato que más condiciona el diseño es el del §1 del spec: las sesiones con báscula, real o simulada, se
comportan exactamente igual que antes. Por eso la sesión sin báscula es una clase nueva y no un modo de
`BrewSession` (D1), y todas las ramas nuevas del gestor dependen de un `scaleless` que vale `false` en el camino de
siempre (D4).

El código real contradice o no cubre algunos puntos del spec. Se listan en el §1.3 con el ajuste mínimo que propone
este plan. Lo más importante:

- `BrewCurveCodec` **sí** admite cero muestras (test `empty lists round trip`), así que no hace falta ajustarlo.
- `UsedTarget.fit` no admite `null`. Se mantiene el tipo, y el ajuste se guarda con `score = null` (D7).
- Con Android 14 o posterior, `BrewService` no arranca sin el permiso de Bluetooth. Además, sin tráfico BLE la CPU
  puede dormirse con la pantalla apagada (D15, D16 y P1).

## 1. Punto de partida

### 1.1 Se reutiliza

| Pieza | Uso en la sesión sin báscula |
|---|---|
| `brew/BrewModels.kt`: `BrewPhase`, `BrewState`, `BrewSummary`, `BrewEffect.Finished`, `FinishedBrew`, `FinishReason` | La sesión cronometrada publica los mismos tipos, así que la UI, el guardado y `BrewKeepAlive` no necesitan tipos nuevos. |
| `brew/BrewConfig.kt` (`maxDurationMs = 900_000`) | El límite de 15 min. |
| `brew/TargetGuide.kt` (`TargetGuide.at`) | La guía con `waterG = null`. Ya da el primer paso en `Armed`, el paso por tiempo en `Active`, `countdownS` y `allStagesDone`. |
| `brew/StageAlertTracker.kt` y `brew/StageNotifier.kt` | Sin cambios: los avisos de cambio de paso. |
| `brew/BrewFit.kt` (`brewFit`) | Sin muestras devuelve `score = null`, `targetWaterG = finalWaterG` y `targetEndMs = endMs`. Así se rellenan las columnas de la referencia "como hoy" (D7). |
| `history/BrewCurveCodec.kt` | Cero muestras y cero vertidos se codifican y decodifican sin error (`BrewCurveCodecTest.empty lists round trip`). `brew_curves` conserva la relación 1:1 sin tocar el codec. |
| `history/BrewPreview.kt` | `compute(emptyList(), t, agua > 0)` da 48 bytes a cero. |
| `session/BrewPlan.kt` (`BrewPlan`, `targetWaterG`) | El plan de Preparar y el agua objetivo. |
| `session/BrewKeepAlive.kt` (`needsKeepAlive`) | Ya cubre cualquier sesión con plan en `Armed`, en `Active` y mientras se guarda (D26). |
| `ui/session/SessionScreen.kt`: `CurrentStep`, `NextStep`, el diálogo de salida, `leave()` y `KeepScreenOn` | `CurrentStep` en `Armed` ya muestra el primer paso, porque la guía lo da. El diálogo y `leave()` ya hacen lo que pide el §3.4. |
| `ui/session/StepList.kt` (`StepList`, `stepStates`) y `ui/session/SessionFormat.kt` (`formatClock`, `nextStepInfo`) | La lista de pasos y la cuenta atrás. |
| `ui/components/AntProgressCircle.kt` | El anillo del dial (D11). |
| `ui/scale/BrewChart.kt` | Con `samples` vacío ya dibuja solo el objetivo (su KDoc lo dice). Sirve para la curva objetivo del detalle (D21). |
| `ui/prepare/FreeBrewInput.kt` (`parseOneDecimal`, `parseFreeDose`, `parseFreeRatio`) | La validez de la dosis y el ratio libres, y la dosis al editar. |
| `recipe/RecipeValidator.kt` (`MAX_WATER_G = 1000.0`) | El tope del agua al editar. |
| `androidTest/.../ui/TestAppDependencies.kt`, `TestApp`, `RecordingNotifier` y `PourlineTestRunner` | La base de los tests instrumentados nuevos. |
| `test/.../session/ScaleTestFakes.kt` (`FakeScaleConnection`, `RecordingConnectionFactory`, `FakeBrewSaver`, `FakeNotifier`...) | La base de los tests JVM del gestor. |

### 1.2 Falta o hay que corregir

- `PrepareViewModel` solo deja empezar con `canStart = scale.connection == ConnectionState.Ready`. No hay otro camino.
- `PrepareScreen` guarda los textos de la dosis y el ratio dentro de `FreeFields` (`rememberSaveable`). Su validez
  solo se conoce ahí, y el botón nuevo la necesita (D10).
- `ScaleSessionManager` solo conoce `BrewSession` y `PrepFlow`:
  - `publish()` usa `session.state`;
  - el objetivo, el café y la molienda se congelan al pasar a `Active` (con `frozenFor`, `coffeeFrozenFor` y
    `grindFrozenFor`), no al abrir la sesión.
- `ScaleUiState` no sabe si la sesión es sin báscula.
- `CurrentStep` y `NextStep` usan `ui.unit`, que es la unidad de la báscula. La sesión sin báscula va siempre en
  gramos.
- `BrewNotice` y `BrewService.text` muestran el agua vertida o "desconectada". No muestran el paso.
- `BrewService` arranca solo con `BLUETOOTH_CONNECT` en API 34 o superior, usa solo el tipo `connectedDevice` y no
  retiene la CPU.
- `BrewEntity`, `BrewContext`, `BrewListRow` y `BrewListItem` no tienen la marca. `HistoryDatabase` está en
  `version = 5`.
- Las consultas no excluyen nada:
  - `BrewDao.progress` filtra por `simulated = 0 AND stabilityScore IS NOT NULL`;
  - `ComparePickViewModel` y `TargetPickerViewModel` listan todas las preparaciones.
- `HistoryRow` dibuja siempre la minicurva. `BrewDetailScreen` muestra "Comparar" y "Guardar como receta" según el
  modo.
- `BrewEditForm` no edita la dosis ni el agua. `BrewDao` no tiene una escritura para ellas.
- `BrewShareData` no lleva la dosis. `BrewShareCard` dibuja siempre la gráfica y la estabilidad.
- `TestAppDependencies` construye el gestor con el reloj real. Un test instrumentado de una receta de 2 min
  tardaría 2 min (D24).
- No hay detekt en el proyecto (ni en `build.gradle.kts` ni en `libs.versions.toml`). Como en los planes
  anteriores, el control es `:app:lintDebug`.

### 1.3 Discrepancias entre el spec y el código

| # | Spec | Código real | Ajuste que propone el plan |
|---|---|---|---|
| 1 | §3.5: "Si el codec no admite cero muestras, el plan lo ajusta". | `BrewCurveCodec.encode(emptyList(), emptyList())` produce la versión seguida de `deflate([0, 0])`, y `decode` lo acepta. Ya hay un test: `empty lists round trip`. | Ninguno. El codec no cambia y las curvas existentes no se tocan. |
| 2 | §3.3: `target = UsedTarget(…, fit = null)`. | `UsedTarget.fit` es `BrewFit` (no admite `null`). `BrewRepository.save` lee `target?.fit?.targetWaterG` y `targetEndMs` para rellenar las columnas de la referencia "como hoy", que el §3.5 sí pide. | Se conserva el tipo. `brewFit(emptyList(), …)` ya devuelve `BrewFit(score = null, …)`, así que `fitScore` queda `null` y las columnas de la referencia se rellenan (D7). |
| 3 | §3.3: `startScalelessSession` "congela el objetivo, el café y la molienda, como `startSession`". | `startSession` no congela nada: el congelado ocurre al pasar a `Active`, con `t0` como clave. | Se congela en `startScalelessSession`, como pide el spec. Las claves por `t0` se rellenan después como hoy (D3). |
| 4 | §3.6 y §5: el detalle oculta "Usar como referencia". | `BrewDetailScreen` no tiene esa acción. Una preparación se elige como referencia en `TargetPicker` (Preparar). | La exclusión va en `TargetPickerViewModel.brews` (D22). En el detalle solo se ocultan "Comparar" y "Guardar como receta". |
| 5 | §5: tests JVM de `BrewRepository`, `BrewDao` y la migración 5 → 6. | El proyecto no tiene Robolectric. Los tests de Room (`HistoryMigrationTest` y `BrewDaoTest`) son instrumentados. | Los tests de Room siguen siendo instrumentados. El mapeo de `BrewRepository.save` se extrae a una función pura con test JVM. No se añade Robolectric (D18). |
| 6 | §3.4: "el mismo dial (`BrewDial`)". | El centro de `BrewDial` muestra el agua, el ratio y el flujo. No tiene parámetros para un reloj ni para una línea de acción. | `TimedDial` (nuevo) usa el mismo `AntProgressCircle`, con el mismo tamaño y el mismo `testTag("session_dial")`. `BrewDial` no se toca, para no arriesgar la sesión con báscula (D11). |
| 7 | §3.7 dice `brew_edit_dose` = "Dosis" y `brew_edit_water` = "Agua"; §3.5 dice "Dosis (g)" y "Agua (g)". | Los campos del formulario llevan la unidad en la etiqueta (`brew_edit_temp` = "Temperatura (°C)"). | Se sigue el §3.5: "Dosis (g)" / "Dose (g)" y "Agua (g)" / "Water (g)" (D25). |
| 8 | §4: con la pantalla apagada, `BrewService` mantiene la sesión y los avisos "como con báscula". | En API 34 o superior, `BrewService.start` no arranca sin `BLUETOOTH_CONNECT`, porque el tipo `connectedDevice` lo exige. Además, sin tráfico BLE la CPU puede suspenderse con la pantalla apagada, y los `delay` del hilo principal se paran. | Tipo `specialUse` en API 34 o superior para la sesión sin báscula (P1) y un `WakeLock` parcial mientras está en `Active` (D15 y D16). |
| 9 | §3.6: compartir muestra la dosis. | `BrewShareData` no la lleva. | `BrewShareData.doseG: Double? = null` (D23). |
| 10 | §1: "Los tests existentes siguen pasando sin cambios en sus expectativas". | `HistoryMigrationTest.aFreshInstallIsCreatedAtVersion5WithTheBuiltIns` comprueba `version = 5`. | Es la única expectativa que cambia, y la obliga el propio spec (v6). Las aperturas con Room de ese test añaden `MIGRATION_5_6`. |

## 2. Decisiones que completan el spec

Marcadas como "(concreta)" si eligen entre opciones que el spec deja abiertas, y como "(amplía)" si añaden algo que
el spec no dice.

### 2.1 Dominio y gestor

| ID | Tema | Decisión | Motivo |
|---|---|---|---|
| D1 | Sesión cronometrada (concreta §3.2) | Clase nueva `brew/TimedSession.kt`, pura (sin `android.*`), con `class TimedSession(private val config: BrewConfig = BrewConfig())`. Publica `val state: BrewState`: `samples` vacío, `currentStability` y `currentLevel` a `null`, y `t0MonoMs` en `Active` y en `Finished`. Operaciones: `fun arm(amounts: PlannedAmounts)` (Armed con las cantidades), `fun start(nowMs: Long): List<BrewEffect>`, `fun tick(nowMs: Long): List<BrewEffect>`, `fun finish(nowMs: Long, reason: FinishReason = FinishReason.MANUAL): List<BrewEffect>` (solo `MANUAL` o `MAX_DURATION`, con `require`) y `fun discard(): List<BrewEffect>` (vuelve a `Armed` con las mismas cantidades y una lista vacía). Con `MAX_DURATION`, `totalTimeMs = maxDurationMs`, como en `BrewSession`. Solo emite `BrewEffect.Finished`, nunca los efectos del temporizador. | El spec exige que `BrewSession` y `PrepFlow` no cambien con báscula. Una clase aparte lo garantiza sin tocar los 53 tests de `BrewSessionTest`. `arm` existe porque las cantidades salen del plan congelado (D3), no de la báscula. |
| D2 | Cantidades del plan (concreta §3.5) | Nuevo `data class PlannedAmounts(val doseG: Double?, val waterG: Double)` en `brew/TimedSession.kt`, con `val ratio: Double? get() = doseG?.takeIf { it > 0 }?.let { waterG / it }`. Nueva `fun plannedAmounts(target: ActiveTarget?, plan: BrewPlan): PlannedAmounts` en `session/BrewPlan.kt`. Con receta: `doseG = target.scaledDoseG ?: target.nominalDoseG` y `waterG = target.curve.finalWaterG`. Sin receta: `doseG = plan.doseG` y `waterG = plan.doseG * plan.ratio`. La dosis capturada por la báscula no interviene. | Es exactamente lo que dice el §3.5. Con la receta escalada, la dosis y el agua salen del mismo `ActiveTarget`, así que el ratio es coherente. |
| D3 | Congelado (concreta §3.3) | `startScalelessSession` congela en un `private data class ScalelessFrozen(val target: ActiveTarget?, val coffee: PickedCoffee?, val grind: GrindSetting?, val amounts: PlannedAmounts)`: `_ui.value.target`, `_ui.value.coffee`, `grind.current()` y `plannedAmounts(target, plan)`. En `Armed`, `withTarget` y `withCoffee` devuelven lo congelado en lugar de la elección viva. Al pasar a `Active`, el código actual copia `previous.target` y `previous.coffee` con la clave `t0` (`frozenFor`, `coffeeFrozenFor`), así que `save()` los encuentra igual. La molienda en `Active` toma `ScalelessFrozen.grind` en lugar de volver a llamar a `grind.current()`. | El spec pide congelar al abrir la sesión. Además, así una receta borrada en `Armed` no deja la sesión sin objetivo (§4). Reutilizar las claves por `t0` mantiene `save()` igual para los dos tipos de sesión. |
| D4 | Enrutado en el gestor (concreta §3.3) | Campos nuevos en `ScaleSessionManager`: `private val timed = TimedSession()`, `private var scaleless = false`, `private var scalelessFrozen: ScalelessFrozen? = null`, `private var scalelessTicker: Job? = null` y `private var doneAlertedFor: Long? = null`. `publish()` usa `brew = if (scaleless) timed.state else session.state`. `startScalelessSession` hace `session.reset()` y deja `armed = false`, así que el bucle de 1 s, el recolector de la conexión y `closeConnection` siguen sin cambios: la `BrewSession` está en `Armed` y no reacciona. `onMessage` añade un `|| scaleless` explícito a la compuerta (`if (!armed \|\| scaleless) emptyList()`). `startSession` y `startScalelessSession` se ignoran si cualquiera de las dos sesiones está en `Active`. `startSession` deja `scaleless = false`. `selectTarget` y `selectCoffee` no cambian: leen la fase publicada, que ya es la de la sesión cronometrada. | Con `scaleless = false`, cada rama nueva es la de hoy. Los pesos siguen actualizando la tarjeta (`reduce`), como permite el spec, pero no llegan a ninguna sesión. Como `TimedSession` no emite efectos de temporizador, no se envía nada a la báscula. |
| D5 | Reloj de la sesión (amplía) | Un `Job` propio (`scalelessTicker`) en `scope` mientras la sesión cronometrada está en `Active`, con `delay(SCALELESS_TICK_MS)` y `SCALELESS_TICK_MS = 100L` en el `companion`. En cada vuelta: `val effects = timed.tick(clock())`, `publish()` y `handle(effects)`, en el orden del bucle actual. Termina solo al salir de `Active`, y se cancela en `endSession`, `discardSession` y `startSession`. Los cálculos usan siempre `clock()`, nunca la cuenta de ticks. | Con báscula, `publish()` corre con cada peso (unos 10 Hz), y ahí se calculan los avisos y la cuenta atrás. Con el bucle de 1 s, la sesión sin báscula avisaría hasta 1 s tarde. Con 100 ms, los avisos caen en los mismos instantes que con la báscula simulada (criterio 5 del §5). |
| D6 | Aviso de pasos completados (concreta §3.4) | En `alertStage()`, solo con `scaleless`: cuando `guide.allStagesDone` pasa a `true`, se llama una vez por `t0` a `notifier.stageStarted(curve.stages.last())`. La marca `doneAlertedFor` vale `t0`. Sin receta no hay avisos (la curva es `null`). | `StageAlertTracker` y `StageNotifier` no cambian (§3.3). `AndroidStageNotifier.stageStarted` vibra y pita sin mirar la etapa, así que reutilizarlo da el "único aviso" del §3.4. |
| D7 | Guardado (concreta §3.3) | El cálculo de `used` no cambia: `brewFit(brew.samples, …)` con `samples` vacío ya da `score = null`. El `BrewContext` lleva, si `scaleless`, `unit = WeightUnit.GRAMS`, `scaleModel = null`, `simulated = false` y `scaleless = true`. `lastFit` queda `null` en la sesión sin báscula. `details`, el café, la hora de inicio y el manejo de `SavedBrew` no cambian. | Es el ajuste mínimo para la discrepancia 2 (§1.3). Sin `lastFit`, el resumen y la imagen de compartir no tienen nada de "ajuste" que esconder. |
| D8 | Estado de la UI (concreta §3.3) | `ScaleUiState` gana `scaleless: Boolean = false` y `plannedDoseG: Double? = null`, y la propiedad derivada `val sessionUnit: WeightUnit get() = if (scaleless) WeightUnit.GRAMS else unit`. Con `scaleless`, `withSession` da `targetWaterG = scalelessFrozen.amounts.waterG`. | La UI necesita la dosis del plan (Armed libre, resumen) y una unidad fija sin tocar `unit`, que sigue siendo la de la tarjeta de la báscula. Con báscula, `sessionUnit == unit`. |
| D9 | Reiniciar y menú ⋯ (concreta §3.3 y §3.4) | `restartPrep()` sin báscula: en `Finished`, `endSession()` y después `startScalelessSession(plan)` con el mismo `BrewPlan`; en `Armed` y en `Active` no hace nada. En `SessionOptionsSheet`: `unitEnabled = connection == Ready && !scaleless`; `restartEnabled = if (scaleless) phase is Finished else phase !is Active`; `simulation = null` si `scaleless`. | "Reiniciar" en `Armed` no haría nada (§3.3), así que se deshabilita para no ofrecer un botón muerto. La simulación no tiene sentido sin báscula (§3.4). |
| D26 | Servicio en `Armed` (concreta §3.3) | `needsKeepAlive()` no cambia: la sesión sin báscula también tiene el servicio en `Armed`, como la sesión con báscula en los pasos previos. | Android solo deja arrancar un servicio en primer plano con la app delante. Arrancarlo al abrir la sesión, y no al pulsar "Empezar timer", sigue el mismo camino que hoy (`BrewKeepAlive`). |
| D27 | Receta sin dosis (concreta §3.5) | Si el objetivo no tiene `scaledDoseG` ni `nominalDoseG` (una preparación de referencia sin dosis), `PlannedAmounts.doseG` y `finalRatio` quedan `null`. La dosis se puede añadir después al editar (D19). | El spec no cubre el caso. Inventar la dosis libre de Preparar mezclaría dos fuentes de datos. |

### 2.2 Interfaz

| ID | Tema | Decisión | Motivo |
|---|---|---|---|
| D10 | Botón en Preparar (concreta §3.1) | Los textos `doseText` y `ratioText` suben de `FreeFields` a `PrepareScreen`, con `rememberSaveable(ui.target == null) { mutableStateOf(formatInput(ui.plan.doseG)) }` (y lo mismo para el ratio). La clave reinicia los textos al poner o quitar la receta, igual que hoy, cuando `FreeFields` sale de la composición. Nueva `internal fun canStartScaleless(hasTarget: Boolean, doseText: String, ratioText: String): Boolean` en `FreeBrewInput.kt`. Botón `AntButton` con `type` por defecto (secundario), `size = AntSize.Large`, ancho completo y `testTag("prepare_start_scaleless")`, debajo de "Empezar" y de su ayuda `prepare_start_hint`. Pide el permiso de notificaciones igual que "Empezar", llama a `viewModel.startScalelessSession()` y luego a `onStart()`. `PrepareViewModel` gana `fun startScalelessSession() = manager.startScalelessSession(ui.value.plan)`. La navegación no cambia. | La validez de los campos libres solo existe en la UI: el ViewModel solo guarda los valores válidos. Subir los textos es el cambio más pequeño que permite deshabilitar el botón con un campo inválido. |
| D11 | Pantalla de sesión (concreta §3.4) | En `SessionScreen`, `DisconnectedNotice` no se compone si `scaleless`, y el `when` gana dos ramas: `Active` con `scaleless` → `ScalelessActivePanel`, y `Armed` con `scaleless` → `ScalelessReadyPanel` (los dos nuevos, en `ui/session/ScalelessPanels.kt`). **Armed** (`testTag("scaleless_ready")`): con receta, `CurrentStep(ui)` (en `Armed` la guía ya da el primer paso: nombre, tipo, instrucción y "hasta X g") y `StepList` con todos los pasos pendientes. Sin receta, tres `AntStatistic`: dosis, ratio y agua (`scaleless_plan_dose`, `scaleless_plan_ratio` y `scaleless_plan_water`). Después, un botón primario grande `testTag("scaleless_start")` con el texto `prep_start_timer`. **Active con receta** (`testTag("scaleless_active")`): `TimedDial` con el reloj (`scaleless_clock`) y la línea de acción (`scaleless_action`) en el centro, y debajo del dial la línea `scaleless_step_progress`. Después, `CurrentStep`, `NextStep`, `StepList` y "Terminar" (`session_finish`). Cuando `allStagesDone`, una `AntAlert` informativa con `liveRegion` (`testTag("scaleless_done_hint")`) entre el dial y `CurrentStep`. **Active libre**: `TimedDial` con fracción 0, el reloj, `scaleless_water_goal` en el centro y "Terminar". Nada de flujo, sparkline, estabilidad, desviación, gráfica ni paginador en ningún modo. `CurrentStep`, `NextStep`, `MaxDial` y `MinDial` pasan de `private` a `internal`. `CurrentStep` y `NextStep` usan `ui.sessionUnit` en lugar de `ui.unit`, lo que no cambia nada con báscula. | Paneles separados dejan `ActivePanel` y `PrepPanel` intactos, y con ellos `SessionPerformanceTest` y `SessionLayoutTest`. "Debajo" (§3.4) se interpreta como fuera del dial: el centro ya lleva el reloj de 48 sp y la línea de acción, y a 360 dp con letra grande no cabe una tercera línea. |
| D12 | Vista del paso (concreta §3.4) | Nueva función pura en `SessionFormat.kt`: `data class TimedStepView(val number: Int, val count: Int, val remainingMs: Long, val fraction: Float)` y `fun timedStepView(guide: GuideState?, curve: TargetCurve?, tMs: Long?): TimedStepView?`. Devuelve `number = stage.index + 1`, `count = curve.stages.size`, `remainingMs = (stage.endMs - tMs).coerceAtLeast(0)` y `fraction = ((tMs - stage.startMs) / (stage.endMs - stage.startMs)).coerceIn(0f, 1f)`, o 1 si la duración es 0. Da `null` sin guía, sin etapa (antes del primer paso) o con `allStagesDone`. El tiempo se formatea con `formatClock`, que trunca como `NextStep`. La línea de acción: `POUR` → `scaleless_pour_to` con el agua del paso en gramos enteros; `SWIRL` y `WAIT` → la instrucción del paso o, si no tiene, `stringResource(stepTypeLabel(type))`; antes del primer paso → `session_step_waiting`; con todo hecho → `session_steps_done`. La cantidad la da `internal fun guideGrams(grams: Double): String` (nueva, en `ui/scale/GuideFormat.kt`), sin el símbolo, porque la cadena del spec ya lleva la "g". | Una función pura sale de `GuideState`, igual que la pantalla, y se puede comprobar contra `TargetGuide.at` cada 100 ms (criterio 4 del §5). `formatGuideAmount` añade " g", y la cadena del spec también. |
| D13 | Resumen (concreta §3.4) | Con `scaleless`, `SessionSummary` usa gramos. Lee la dosis, el agua y el ratio de `record?.entity` (`doseG`, `totalWaterG` y `finalRatio`) cuando ya está guardada; si no, de `ui.plannedDoseG` y del `summary`. Así el resumen refleja la corrección hecha en "Añadir detalles". Muestra una etiqueta `AntTag(history_row_scaleless, AntTagColor.Primary)` con `testTag("summary_scaleless")`. En Experto (`SUMMARY_FINISH_REASON`) solo aparece `summary_reason`, dentro de `summary_expert`. "Añadir detalles", "Compartir" y "Listo" no cambian. | El §3.4 pide la dosis y el agua del plan, el ratio y el café. La línea del ratio y el café ya existe (`summary_ratio`). |
| D14 | Notificación (concreta §3.3) | `BrewNotice` gana `scaleless: Boolean = false`, `step: BrewNoticeStep? = null` y `stepsDone: Boolean = false`, con `data class BrewNoticeStep(val number: Int, val count: Int, val type: StageType, val name: String?, val targetWaterG: Double?)` (nuevo). `targetWaterG` solo se rellena en `POUR`, redondeado al gramo. Con `scaleless`, `toBrewNotice()` da `water = null`, `unit = GRAMS`, `disconnected = false` y `targetWater` igual al agua del plan. El paso sale de `guide.stage` y cambia solo al cambiar de paso, así que `distinctUntilChanged` no publica a 10 Hz. El texto en `BrewService.text`: `Armed` → `brew_notice_scaleless_ready`; paso `POUR` → `brew_notice_step_pour`; otro paso → `brew_notice_step`; todo hecho → `session_steps_done`; antes del primer paso → `session_step_waiting`; sin receta → `scaleless_water_goal`; guardando → `brew_notice_saving`. El tiempo es el cronómetro actual (`setUsesChronometer` con `t0MonoMs`). | El §3.3 pide el tiempo y el paso, más el objetivo del paso si es `POUR`, y nunca el agua vertida. |
| D24 | Reloj de los tests instrumentados | `TestAppDependencies` gana `clock: () -> Long = SystemClock::elapsedRealtime` y se lo pasa a `ScaleSessionManager`. Los tests nuevos usan un `var now` que el test adelanta (`now += 46_000`) y esperan con `rule.waitUntil` al siguiente tick (100 ms reales). | La V60 dura 1:55 y `MAX_DURATION` 15 min. Con el reloj real, los tests serían lentos e inestables. Con el valor por defecto, los tests existentes no cambian. |
| D25 | Cadenas (concreta §3.7) | Nuevas en `values` y `values-en`: `prepare_start_scaleless`, `scaleless_pour_to`, `scaleless_step_progress`, `scaleless_water_goal`, `scaleless_done_hint`, `history_row_scaleless` y `brew_edit_dose` / `brew_edit_water` (con el texto del §3.7, salvo las de edición, que llevan la unidad: "Dosis (g)" / "Dose (g)" y "Agua (g)" / "Water (g)", discrepancia 7). Además: `brew_edit_dose_error` ("Entre 5 y 60 g, con un decimal como máximo."), `brew_edit_water_error` ("Más de 0 y hasta 1000 g, con un decimal como máximo."), `scaleless_dial_description` (accesibilidad del dial: "Tiempo %1$s. %2$s"), `brew_notice_scaleless_ready` ("Listo: pulsa Empezar timer"), `brew_notice_step` ("Paso %1$d/%2$d · %3$s"), `brew_notice_step_pour` ("Paso %1$d/%2$d · %3$s · hasta %4$s") y `brew_detail_target_chart` ("Curva objetivo"). Se reutilizan: `prep_start_timer` para `scaleless_start`, `history_row_scaleless` para las etiquetas del resumen, del detalle y de compartir, `session_steps_done` y `session_step_waiting`. | El §3.7 permite reutilizar cadenas equivalentes. El texto de `prepare_range_error` habla de la dosis y del ratio, así que no sirve para la edición. |

### 2.3 Datos, historial y compartir

| ID | Tema | Decisión | Motivo |
|---|---|---|---|
| D17 | Columna y migración (concreta §3.5) | `@ColumnInfo(defaultValue = "0") val scaleless: Boolean = false` al final de `BrewEntity`. `val MIGRATION_5_6` con `ALTER TABLE brews ADD COLUMN scaleless INTEGER NOT NULL DEFAULT 0`. `HistoryDatabase` en `version = 6`, con `addMigrations(…, MIGRATION_5_6)` y la línea de la v6 en el KDoc, y se genera `app/schemas/dev.josueamaya.pourline.history.HistoryDatabase/6.json`. `BrewListRow` y `BrewListItem` ganan `scaleless: Boolean = false` al final, y la consulta `list` añade la columna. | El `defaultValue` hace que el esquema exportado coincida con el `DEFAULT 0` de la migración. Es el precedente de `recipe_stages.type` en la v4. El valor por defecto en las clases de lista evita tocar los constructores de los tests existentes. |
| D18 | Tests de Room (discrepancia 5) | `HistoryMigrationTest` y `BrewDaoTest` siguen instrumentados. Se extrae `internal fun brewEntity(finished: FinishedBrew, context: BrewContext, preview: ByteArray): BrewEntity` en `BrewRepository.kt`, que `save` usa y que tiene un test JVM (`BrewRepositoryMappingTest`). | Añadir Robolectric sería una dependencia nueva fuera del alcance del spec. La función pura cubre en la JVM lo que el spec quería probar del guardado. |
| D19 | Edición de dosis y agua (concreta §3.5; amplía con la dosis vacía) | `BrewEditForm` gana `scaleless: Boolean = false`, `dose: String = ""` y `water: String = ""`. Con `scaleless`, `isValid` exige además que la dosis esté vacía o cumpla `parseFreeDose` (5 a 60 g, un decimal), y que el agua cumpla `parseBrewWater` (nueva: `parseOneDecimal`, mayor que 0 y hasta `RecipeValidator.MAX_WATER_G`). `fun amounts(): PlannedAmounts?` da `null` sin `scaleless`. `BrewEditForm.from(details, coffee, amounts: PlannedAmounts? = null)` rellena los textos. `BrewEditViewModel` los carga si `entity.scaleless`. Al guardar, pasa las cantidades solo si cambiaron, en la misma transacción que los detalles: `BrewDao.applyDetails(id, details, amounts: PlannedAmounts? = null)` llama a `updateScalelessAmounts(id, doseG, totalWaterG, finalRatio): Int` (nueva), con `UPDATE brews SET doseG = :doseG, totalWaterG = :totalWaterG, finalRatio = :finalRatio WHERE id = :id AND scaleless = 1`. `finalRatio` es `amounts.ratio`. `BrewRepository.updateDetails(id, details, amounts: PlannedAmounts? = null)`. En `BrewEditScreen`, los dos campos van después de la temperatura, con `testTag("brew_edit_dose")`, `testTag("brew_edit_water")` y sus errores (`brew_edit_dose_error` y `brew_edit_water_error`). | La dosis vacía es necesaria para una preparación sin dosis (D27). Si no, nunca se podría guardar el formulario. El `WHERE scaleless = 1` impide, ya en la base de datos, reescribir los datos medidos de una preparación con báscula. Una sola transacción evita guardar la mitad de los cambios. |
| D20 | Fila del historial (concreta §3.6) | Con `item.scaleless`, en lugar de `BrewPreviewLine` va un `Spacer` de 64 × 40 dp, que conserva la alineación. Va una `AntTag(history_row_scaleless, AntTagColor.Primary)` con `testTag("history_row_scaleless")`, siempre visible. Las etiquetas de estabilidad y de ajuste no aparecen ni en Experto. `BrewPreviewLine` gana `testTag("history_row_preview")` para poder comprobar que no se dibuja. | La de ceros dibujaría una línea plana. Mostrar "sin puntuación" no aporta nada si no se puede puntuar. |
| D21 | Detalle (concreta §3.6) | Con `entity.scaleless`: la etiqueta `history_row_scaleless` (`testTag("brew_detail_scaleless")`) y `BrewFactsCard` en los dos modos, sin `BrewSummaryCard`, `FitCard`, `TastingCard` ni el aviso de métrica antigua. En Experto (`BREW_DETAIL_CHART`) y con `record.target != null`, una tarjeta `brew_detail_target_chart` con `TargetChart(target)` (nueva, en `ui/scale/Chart.kt`). Llama a `BrewChart` con `samples = emptyList()`, gramos, `flowUnitLabel = null` y la descripción `target_description`. Sin receta no hay gráfica. `BrewChart` y `drawGridAndLabels` pasan a aceptar `flowUnitLabel: String?`: con `null` no se dibuja el eje del flujo, y los llamadores actuales no cambian. Los botones "Comparar" y "Guardar como receta" no se componen. `DetailsCard`, "Editar" y "Borrar" siguen igual. | El spec pide la curva objetivo sin curva real, sin flujo, sin estabilidad y sin ajuste. Mostrarla solo en Experto sigue la regla de SPEC-005 para todas las gráficas del detalle (P2). |
| D22 | Exclusiones (concreta §3.6) | `BrewDao.progress` añade `AND scaleless = 0`. `ComparePickViewModel.items` filtra `!it.scaleless`. `hasOthers` pasa a usar `BrewRepository.comparableCount()` (nueva), que llama a `BrewDao.comparableCount()` con `SELECT COUNT(*) FROM brews WHERE scaleless = 0`. `TargetPickerViewModel.brews` filtra `!it.scaleless`. `RecipeFromBrew` no cambia: su único camino de entrada es el botón del detalle, que se oculta. `count()` sigue igual para el resto de usos. | Las exclusiones van en la capa que alimenta cada lista. Los filtros por café o método, y la bitácora del café, siguen incluyéndolas (§3.6). |
| D23 | Compartir (concreta §3.6; amplía con `doseG`) | `BrewShareData` gana `scaleless: Boolean = false` y `doseG: Double? = null`. Con `scaleless`, `BrewShareCard` muestra en la cabecera la etiqueta `history_row_scaleless` (`testTag("share_scaleless")`) en lugar de la del ajuste. En el hueco de la gráfica (`ShareChartSlot`, que conserva `share_chart` para medir) va una rejilla de 2 × 2 cifras grandes: tiempo, dosis, agua y ratio (`share_scaleless_metrics`). No hay `ShareMetrics`, porque llevaría la estabilidad. La valoración, las notas (`ShareQuote`) y el pie con el café siguen igual. `summaryShareData` rellena `scaleless`, `doseG` y `unit = GRAMS`, con `subtitle` igual al nombre del objetivo congelado. `detailShareData` usa los valores de la entidad. | El marco tiene un tamaño fijo y la gráfica ocupa el alto sobrante. Ocupar ese hueco con las cifras evita una imagen medio vacía. |

### 2.4 Segundo plano

| ID | Tema | Decisión | Motivo |
|---|---|---|---|
| D15 | CPU con la pantalla apagada (amplía) | `BrewService` retiene un `PowerManager.PARTIAL_WAKE_LOCK` (etiqueta `pourline:scaleless`) mientras `scaleless && brew.phase is Active`. Lo observa con un recolector propio (`map { … }.distinctUntilChanged()`) y le pone un tiempo máximo de `BrewConfig().maxDurationMs + WAKE_LOCK_MARGIN_MS` (60 s). Lo suelta al salir de `Active`, en `stop()` y en `onDestroy()`. El manifiesto gana `android.permission.WAKE_LOCK`, que es un permiso normal. | Con báscula, el tráfico BLE despierta la CPU. Sin él, los `delay` del hilo principal usan `uptimeMillis`, que se para cuando la CPU duerme, y los avisos llegarían tarde o no llegarían. El tiempo máximo evita retener la CPU si algo falla. |
| D16 | Tipo del servicio (amplía; P1) | En API 34 o superior, la sesión sin báscula arranca `BrewService` con `ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE`. `BrewService.start(context: Context, scaleless: Boolean)` no exige `BLUETOOTH_CONNECT` si `scaleless`. `foregroundType(scaleless: Boolean)`: `specialUse` en API 34 o superior con `scaleless`, `connectedDevice` desde Q en otro caso, y 0 antes de Q. Manifiesto: `FOREGROUND_SERVICE_SPECIAL_USE`, `foregroundServiceType="connectedDevice\|specialUse"` y `<property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE" android:value="brew_timer" />`. `PourlineApp` pasa `scaleSession.ui.value.scaleless` en la lambda de `BrewKeepAlive`. | `connectedDevice` exige el permiso de Bluetooth en Android 14, y no describe una sesión sin dispositivo. Precisamente el caso "sin báscula" es el del usuario que puede no haberlo concedido nunca. Ver P1 para la alternativa. |

## 3. Fases

Orden: F0 → F1 → F2 → F3 → F4 → F5 → F6 → F7 → F8 → F9 → F10. F2 solo depende de F1 (`PlannedAmounts`, para
`applyDetails`). F7, F8 y F9 dependen de F2 y no dependen entre sí. F6 necesita F3.

| Fase | Contenido | Tamaño | Estado |
|---|---|---|---|
| F0 | Línea base: tests, lint y dispositivos | S | Pendiente |
| F1 | Dominio puro: `TimedSession`, `PlannedAmounts`, `plannedAmounts` y `timedStepView` | S | Pendiente |
| F2 | Datos v6: columna, migración 5 → 6, contexto, repositorio y consultas | M | Pendiente |
| F3 | Gestor y estado: `startScalelessSession`, reloj, avisos, guardado y exclusión de pesos | L | Pendiente |
| F4 | Pantalla de sesión: listo, en curso, pasos completados, resumen y menú ⋯ | L | Pendiente |
| F5 | Preparar: botón "Empezar sin báscula" | S | Pendiente |
| F6 | Segundo plano: notificación con el paso, `WakeLock` y tipo del servicio | M | Pendiente |
| F7 | Historial, detalle y exclusiones (Comparar, referencia y progreso) | M | Pendiente |
| F8 | "Añadir detalles": dosis y agua | M | Pendiente |
| F9 | Compartir sin curva | S | Pendiente |
| F10 | Verificación: E2E instrumentado, prueba manual por adb, suites, lint y cierre | M | Pendiente |

Cada fase deja `:app` compilando y sus tests en verde. Se compila y se prueba solo `:app`, nunca con
`./gradlew build` ni con builds limpios.

- **Tests instrumentados:** siempre con `ANDROID_SERIAL=emulator-5556` (AVD `SG_API34`, en `en-US`). El
  `emulator-5554` es del usuario y no se usa. Si `adb devices -l` muestra otro dispositivo, se pregunta.
- **Sin ARTEMIS.** Los tests de interfaz localizan por `testTag` y leen los textos esperados con `stringResource`
  del contexto de la app, por el idioma del emulador.
- **Fallos previos que no bloquean:** `CoffeeDetailScreenTest.theFactsAreFoldedByDefaultAndOpenOnTap` (idioma) y la
  oscilación del caso Experto de `SessionPerformanceTest`.
- **Commits:** uno por fase, en Conventional Commits y en inglés, sin `Co-Authored-By` ni atribución, y solo si se
  piden. El del plan, cuando se apruebe:
  `docs(specs): add scaleless brewing implementation plan (SPEC-007)`.

### F0. Línea base

**Objetivo:** conocer el punto de partida antes de tocar código.

**Pasos:**

1. `adb devices -l`: confirmar `emulator-5556`. Si hay más dispositivos además de `emulator-5554`, preguntar.
2. `./gradlew :app:testDebugUnitTest`: anotar el número de tests, fallos y omitidos.
3. `ANDROID_SERIAL=emulator-5556 ./gradlew :app:connectedDebugAndroidTest`: anotar el total y los fallos conocidos.
4. `./gradlew :app:lintDebug`: anotar los errores y los avisos.
5. Una pasada de `SessionPerformanceTest`: anotar p50, p95 y el porcentaje de fotogramas por encima de 32 ms de los
   dos casos (§7.2).

Las cifras van a la cabecera del plan. No hay cambios de código ni commit.

### F1. Dominio puro

**Objetivo:** la sesión cronometrada, las cantidades del plan y la vista del paso, sin Android y con tests JVM.

**Archivos:**

| Archivo | Cambio |
|---|---|
| `brew/TimedSession.kt` (nuevo) | `PlannedAmounts` y `TimedSession` (D1 y D2). El resumen: `BrewSummary(totalTimeMs, totalWaterG = amounts.waterG, avgFlowGps = 0.0, maxFlowGps = 0.0, stabilityScore = null, stabilityLevel = null, levelShare = emptyMap(), pours = emptyList(), finalRatio = amounts.ratio, reason, interrupted = false)`. El final: `FinishedBrew(t0, samples = emptyList(), summary, doseG = amounts.doseG)`. Con `MANUAL`, `totalTimeMs = (nowMs - t0).coerceIn(0, config.maxDurationMs)`. |
| `session/BrewPlan.kt` | `fun plannedAmounts(target: ActiveTarget?, plan: BrewPlan): PlannedAmounts` (D2). |
| `ui/session/SessionFormat.kt` | `TimedStepView` y `timedStepView(guide, curve, tMs)` (D12). |
| `ui/scale/GuideFormat.kt` | `internal fun guideGrams(grams: Double): String` (gramos enteros, sin símbolo). |

**Tests:**

| Archivo | Casos |
|---|---|
| `test/.../brew/TimedSessionTest.kt` (nuevo) | `` `starts armed with no samples and no t0` ``; `` `start moves to active with t0 now and pouring false` ``; `` `start without arm or outside armed is ignored` ``; `` `finish emits a finished brew with empty samples and the plan summary` `` (agua del plan, flujos a 0, estabilidad `null`, `levelShare` y `pours` vacíos, `interrupted = false` y `MANUAL`); `` `the ratio is water over dose and null without a dose` ``; `` `tick before 15 minutes does nothing` ``; `` `tick at 15 minutes finishes with max duration and the total capped` ``; `` `finish outside active emits nothing` ``; `` `discard from active or finished returns to armed without emitting` ``; `` `a finish reason other than manual or max duration is rejected` ``. |
| `test/.../session/BrewPlanTest.kt` | `` `planned amounts of a scaled recipe use the scaled dose and the final water of the scaled curve` ``; `` `planned amounts of an unscaled recipe use the nominal dose` ``; `` `a recipe without a dose gives a null dose and ratio` ``; `` `a free plan gives the dose and dose times ratio` ``. |
| `test/.../ui/session/SessionFormatTest.kt` | `timedStepView` con la V60 clásica (`BuiltInRecipes`: vertidos de 0 a 10 s, de 45 a 65 s y de 90 a 115 s, y esperas entre ellos): a los 5 s da 1/5, 5 000 ms restantes y fracción 0,5; a los 45 s, 3/5, 20 000 ms y 0; `null` antes del primer paso y con `allStagesDone`; fracción 1 con un paso de duración 0. `` `the view agrees with TargetGuide every 100 ms` ``: para la V60 y el 4:6, de 0 a 130 s, el número de paso es `guide.stage.index + 1` y los restantes son `stage.endMs - t`. |

**Verificación:**
`./gradlew :app:testDebugUnitTest --tests "dev.josueamaya.pourline.brew.TimedSessionTest" --tests "dev.josueamaya.pourline.session.BrewPlanTest" --tests "dev.josueamaya.pourline.ui.session.SessionFormatTest" --tests "dev.josueamaya.pourline.brew.BrewSessionTest"`.
`grep -n "import android" app/src/main/kotlin/dev/josueamaya/pourline/brew/TimedSession.kt` no encuentra nada.

Commit sugerido: `feat(brew): add a timed brew session for brewing without a scale`.

### F2. Datos v6

**Objetivo:** guardar, listar, excluir y corregir las preparaciones sin báscula en la base de datos, con la migración
5 → 6.

**Archivos:**

| Archivo | Cambio |
|---|---|
| `history/BrewEntities.kt` | `BrewEntity.scaleless` con `@ColumnInfo(defaultValue = "0")`; `BrewListRow.scaleless: Boolean = false`; `BrewListItem.scaleless: Boolean = false` (D17). |
| `history/Migrations.kt` | `val MIGRATION_5_6` (D17). |
| `history/HistoryDatabase.kt` | `version = 6`, `addMigrations(migration1To2(texts), MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)` y la v6 en el KDoc. Se genera `6.json`. |
| `history/HistoryModels.kt` | `BrewContext.scaleless: Boolean = false`. |
| `history/BrewRepository.kt` | `internal fun brewEntity(finished, context, preview): BrewEntity` (D18) con `scaleless = context.scaleless`; `save` la usa. `toItem` copia `scaleless`. `fun comparableCount(): Flow<Int>`. `updateDetails(id, details, amounts: PlannedAmounts? = null)`. |
| `history/BrewDao.kt` | `list` con `scaleless`; `progress` con `AND scaleless = 0`; `comparableCount()`; `updateScalelessAmounts(...)` (`protected`); `applyDetails(id, details, amounts: PlannedAmounts? = null)` (D19 y D22). |

**Tests:**

| Archivo | Casos |
|---|---|
| `androidTest/.../history/HistoryMigrationTest.kt` | `migrates5To6AddingScalelessAsFalse`: una v5 con dos preparaciones (una con curva, objetivo, café y molino). Tras `runMigrationsAndValidate(NAME, 6, true, MIGRATION_5_6)`: `PRAGMA table_info(brews)` tiene `scaleless` `NOT NULL` con `DEFAULT 0`, las dos filas valen 0, y el resto de columnas y las filas de `brew_curves` y `brew_targets` no cambian. `migrates1To2To3To4To5To6InAChain`. `migrates5To6ThenRoomReadsEverything`: Room lee las preparaciones y sus curvas. `aFreshInstallIsCreatedAtVersion5WithTheBuiltIns` pasa a `…Version6…` (discrepancia 10). Las cuatro aperturas con Room añaden `MIGRATION_5_6`. Helper `insertV5Brew` (nuevo). |
| `androidTest/.../history/BrewDaoTest.kt` | `aScalelessBrewIsStoredWithAnEmptyCurveAndAZeroPreview`: con `TimedSession` y la V60, `scaleless = 1`, `doseG = 15`, `totalWaterG = 250`, `finalRatio = 250/15`, flujos 0, estabilidad y `fitScore` `null`, las `share*` a 0, `unit = GRAMS`, `scaleModel = null`, `simulated = 0`, `preview` de 48 ceros, la curva se decodifica vacía con `curveError = false`, `brew_targets` con la copia, y `targetWaterG` y `targetEndMs` de la curva. `theListCarriesScaleless`. `progressSkipsScalelessBrewsEvenWithAScore`: inserta una fila `scaleless = 1` con `stabilityScore` para probar el filtro explícito. `comparableCountSkipsScalelessBrews`. `applyDetailsWithAmountsRewritesDoseWaterAndRatioOnlyOnScalelessBrews`: en una con báscula no cambia nada. `applyDetailsWithANullDoseLeavesTheRatioNull`. `deleteAndRestoreKeepScaleless`. |
| `androidTest/.../history/TestBrews.kt` | `scalelessFinished(totalTimeMs, amounts)` y `context(…, scaleless = false)`. |
| `test/.../history/BrewRepositoryMappingTest.kt` (nuevo) | `brewEntity` con un contexto sin báscula, con receta y sin ella, y con uno con báscula: cada columna del §3.5, y `scaleless = false` por defecto. |
| `test/.../history/BrewPreviewTest.kt` | `` `no samples give zeros even with water` ``. |

`BrewCurveCodecTest` no cambia: `` `empty lists round trip` `` ya cubre la curva vacía.

**Verificación:** `./gradlew :app:compileDebugKotlin` (genera `6.json`, que se añade al commit).
`./gradlew :app:testDebugUnitTest --tests "dev.josueamaya.pourline.history.*"`.
`ANDROID_SERIAL=emulator-5556 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.josueamaya.pourline.history.HistoryMigrationTest,dev.josueamaya.pourline.history.BrewDaoTest`.

Commit sugerido: `feat(history): store brews made without a scale, migrating the database to v6`.

### F3. Gestor y estado

**Objetivo:** abrir, cronometrar, avisar, terminar, guardar, descartar y reiniciar una sesión sin báscula en
`ScaleSessionManager`, sin cambiar el camino con báscula.

**Archivos:**

| Archivo | Cambio |
|---|---|
| `session/ScaleUiState.kt` | `scaleless`, `plannedDoseG` y `sessionUnit` (D8). |
| `session/ScaleSessionManager.kt` | Campos de D4. `fun startScalelessSession(plan: BrewPlan)`: comprueba que ninguna sesión está en `Active`; hace `session.reset()`, `armed = false`, `prep = null`, `forgetFrozen()` y `doneAlertedFor = null`; congela (D3); llama a `timed.arm(amounts)` y pone `scaleless = true`, `sessionPlan = plan`; y limpia `savedBrew`, `lastFit` y `lastBrew` como `startSession`. `fun scalelessStartTimer()`: con `scaleless`, `handle(timed.start(clock()))`, `publish()` y arranca el reloj (D5). `onMessage`: la compuerta de D4. `publish`: `brew`, `scaleless` y `plannedDoseG`. `withTarget`, `withCoffee` y `withSession`: lo congelado (D3 y D8). `alertStage`: el aviso de fin (D6). `save`: el contexto de D7. `finishBrew`, `endSession`, `discardSession` y `restartPrep`: una rama `scaleless` con la misma forma que la de hoy (`endSession` desde `Finished` borra la dosis; las dos limpian `scaleless`, `scalelessFrozen` y el reloj). `startSession`: la guarda doble y `scaleless = false`. `const val SCALELESS_TICK_MS = 100L`. |

**Tests:**

| Archivo | Casos |
|---|---|
| `test/.../session/ScaleSessionManagerScalelessTest.kt` (nuevo; `TestCoroutineScheduler` con `clock = { scheduler.currentTime }`, para que los `delay` y el reloj avancen juntos) | `` `start leaves the session armed without prep, scaleless and with the plan` ``. `` `it freezes the recipe, the coffee and the grind at the start` ``: cambiar la referencia o el café, o borrar la receta, en `Armed` no cambia `ui.target`, `ui.coffee` ni lo guardado. `` `start is ignored while a scale or a scaleless session is active` ``. `` `start timer moves to active and publishes the elapsed time and the guide without deviation` ``. `` `weights, tares and timer messages from a connected scale do not change the session` ``: una `FakeScaleConnection` en `Ready` emite pesos cada 100 ms; `ui.brew` es el de la sesión cronometrada, no hay muestras y `RecordingConnectionFactory` no recibe tramas. `` `connecting, losing and reconnecting the scale keeps the session active` ``: no hay `DISCONNECTED` y `connect` a otro destino no la termina. `` `stage alerts fire at the same instants as a simulated scale session with the same recipe` ``: dos gestores con la V60, uno con báscula (pesos cada 100 ms y `prepStartTimer` en el mismo instante) y otro sin ella. Los avisos `(índice, instante)` son iguales (10, 45, 65 y 90 s), y también la secuencia de `guide.countdownS` muestreada cada 100 ms. `` `the done alert fires once at the end of the last step and the session stays active` `` (115 s). `` `a free scaleless session has no stage alerts and shows the water goal` ``. `` `finish saves with grams, no model, not simulated, scaleless and the target with a null fit score` ``: con `FakeBrewSaver`, `FinishedBrew.samples` vacío, `doseG = 15`, agua 250, el ratio y `lastFit = null`. `` `a free session saves the plan dose and dose times ratio` ``. `` `at 15 minutes the session finishes with max duration and is saved` ``. `` `discard saves nothing and clears scaleless` ``. `` `restart from finished starts another scaleless session with the same plan and does nothing from armed` ``. `` `end session from finished clears scaleless and the dose` ``. `` `a scale session after a scaleless one has its prep steps as before` ``. `` `select target and coffee are ignored while the scaleless session is active` ``. |
| `test/.../session/ScaleTestFakes.kt` | Si `FakeNotifier` no guarda el instante de cada aviso, un `StageNotifier` de prueba que anota `(stage.index, clock())`. |
| `test/.../session/ScaleUiStateTest.kt` | `` `the session unit is grams without a scale and the scale unit otherwise` ``. |

**Verificación:** `./gradlew :app:compileDebugKotlin`, y
`./gradlew :app:testDebugUnitTest --tests "dev.josueamaya.pourline.session.*" --tests "dev.josueamaya.pourline.brew.*"`.
Todos los `ScaleSessionManager*Test` existentes pasan sin tocarlos.

Commit sugerido: `feat(session): run a timed session without a scale in the session manager`.

### F4. Pantalla de sesión

**Objetivo:** los estados `Armed`, `Active` y resumen de la sesión sin báscula, con el aviso de pasos completados y el
menú ⋯.

**Archivos:**

| Archivo | Cambio |
|---|---|
| `ui/session/ScalelessPanels.kt` (nuevo) | `ScalelessReadyPanel(ui: ScaleUiState, onStart: () -> Unit)` y `ScalelessActivePanel(ui: ScaleUiState, onFinish: () -> Unit)` (D11), con la vista de D12. |
| `ui/session/BrewDial.kt` | `TimedDial(fraction: Float, clock: String, action: String?, description: String, modifier: Modifier)` (nuevo): `AntProgressCircle` sin segmentos, con `testTag("session_dial")`, la descripción `scaleless_dial_description` y, dentro, `scaleless_clock` y `scaleless_action`. `BrewDial` no cambia. |
| `ui/session/SessionScreen.kt` | Las ramas de D11, sin `DisconnectedNotice` con `scaleless`, el menú ⋯ de D9, y `CurrentStep`, `NextStep`, `MaxDial` y `MinDial` como `internal` y con `ui.sessionUnit`. `ActivePanel` y `PrepPanel` no cambian. |
| `ui/session/SessionSummary.kt` | La rama sin báscula de D13. |
| `res/values/strings.xml`, `res/values-en/strings.xml` | `scaleless_pour_to`, `scaleless_step_progress`, `scaleless_water_goal`, `scaleless_done_hint`, `scaleless_dial_description` e `history_row_scaleless`. |
| `androidTest/.../ui/TestAppDependencies.kt` | El parámetro `clock` (D24). |

**Tests:**

| Archivo | Casos |
|---|---|
| `androidTest/.../ui/session/ScalelessSessionScreenTest.kt` (nuevo; `TestApp(env)` con un reloj de prueba y la V60) | `armedShowsTheFirstStepTheListAndStartTimerWithoutPrepSteps`: `session_current_step` con el bloom y "hasta 40 g", `session_steps`, `scaleless_start`, y ningún nodo de los pasos previos. `armedWithoutARecipeShowsDoseRatioAndWater` (15 g, 1:16, 240 g). `activeFollowsTheClock`: a los 5 s, `scaleless_action` con `scaleless_pour_to` 40 y `scaleless_step_progress` 1/5 · 0:05; a los 46 s, 3/5 y "hasta 150 g" en `session_current_step`. `activeHasNoFlowChartOrDeviation`: no existen `session_water`, `session_ratio_flow`, `session_chart` ni la página de la gráfica, en Básico ni en Experto. `activeWithoutARecipeShowsTheWaterGoalAndFinish`. `whenAllStepsAreDoneTheHintShowsAndTheSessionStaysActive` (116 s; `session_finish` sigue). `finishShowsTheScalelessSummary`: `summary_scaleless`, `summary_dose` 15 g, `summary_water` 250 g y `summary_ratio`; en Experto, `summary_reason` sin `summary_avg_flow` ni `summary_stability`. `closeInActiveOpensTheExitDialogAndDiscardSavesNothing`. `closeInArmedLeavesWithoutSaving`. `optionsDisableUnitAndRestartInArmedAndHideTheSimulation`. `noDisconnectedNoticeWithoutAScale`. `aConnectedSimulatedScaleDoesNotChangeTheSession`: con la simulada en `Ready` y pesos añadidos por `simulateAdd`, la pantalla no cambia. |
| `androidTest/.../ui/session/SessionScreenTest.kt`, `SessionLayoutTest.kt` | Sin cambios. Se ejecutan para comprobar que la sesión con báscula no cambia. |

**Verificación:** `./gradlew :app:compileDebugKotlin`.
`ANDROID_SERIAL=emulator-5556 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.josueamaya.pourline.ui.session.ScalelessSessionScreenTest,dev.josueamaya.pourline.ui.session.SessionScreenTest,dev.josueamaya.pourline.ui.session.SessionLayoutTest,dev.josueamaya.pourline.ui.session.SessionTopBarTest`.

Commit sugerido: `feat(session): show the timed session without a scale`.

### F5. Preparar

**Objetivo:** el botón "Empezar sin báscula", siempre visible, con su regla de habilitado.

**Archivos:**

| Archivo | Cambio |
|---|---|
| `ui/prepare/FreeBrewInput.kt` | `canStartScaleless(hasTarget, doseText, ratioText)` (D10). |
| `ui/prepare/PrepareViewModel.kt` | `fun startScalelessSession()`. |
| `ui/prepare/PrepareScreen.kt` | Los textos suben a `PrepareScreen` con la clave de D10. `DoseCard` y `FreeFields` los reciben junto a sus `onChange`. El botón nuevo va debajo de la ayuda de "Empezar". |
| `res/values/strings.xml`, `res/values-en/strings.xml` | `prepare_start_scaleless`. |

**Tests:**

| Archivo | Casos |
|---|---|
| `test/.../ui/prepare/FreeBrewInputTest.kt` | `` `with a recipe the scaleless start is always allowed` ``; `` `without a recipe it needs a valid dose and ratio` `` (vacío, fuera de rango y con dos decimales). |
| `androidTest/.../ui/prepare/PrepareScreenTest.kt` | `theScalelessStartIsVisibleAndEnabledWithARecipeAndNoScale`. `theScalelessStartIsVisibleWithTheScaleReady`. `theScalelessStartIsDisabledWithAnInvalidFreeDoseOrRatio`, que vuelve a habilitarse al corregirla. `theScalelessStartOpensTheSessionReadyToStart`: `scaleless_start` visible. `togglingTheRecipeResetsTheFreeTextsAsBefore`. Los casos existentes no cambian. |

**Verificación:** `./gradlew :app:testDebugUnitTest --tests "dev.josueamaya.pourline.ui.prepare.*"`.
`ANDROID_SERIAL=emulator-5556 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.josueamaya.pourline.ui.prepare.PrepareScreenTest,dev.josueamaya.pourline.ui.prepare.PrepareCoffeeTest`.

Commit sugerido: `feat(prepare): start a brew without a scale`.

### F6. Segundo plano

**Objetivo:** con la app en segundo plano o la pantalla apagada, la notificación muestra el tiempo y el paso, y los
avisos siguen sonando.

**Archivos:**

| Archivo | Cambio |
|---|---|
| `session/BrewKeepAlive.kt` | `BrewNoticeStep` y los campos de `BrewNotice`, y la rama sin báscula de `toBrewNotice()` (D14). `needsKeepAlive` no cambia (D26). |
| `session/BrewService.kt` | `text(notice)` con las ramas de D14; el `WakeLock` de D15; `foregroundType(scaleless)` y `start(context, scaleless)` (D16). |
| `PourlineApp.kt` | `BrewKeepAlive(scaleSession.ui, MainScope()) { BrewService.start(this, scaleSession.ui.value.scaleless) }`. |
| `AndroidManifest.xml` | `WAKE_LOCK`, `FOREGROUND_SERVICE_SPECIAL_USE`, el tipo `connectedDevice\|specialUse` y la `property` del subtipo (D16; si P1 elige la alternativa, solo `WAKE_LOCK`). |
| `res/values/strings.xml`, `res/values-en/strings.xml` | `brew_notice_scaleless_ready`, `brew_notice_step` y `brew_notice_step_pour`. |

**Tests:**

| Archivo | Casos |
|---|---|
| `test/.../session/BrewKeepAliveTest.kt` | `` `a scaleless session needs the service while armed, active and saving` ``. `` `the scaleless notice shows the step and its target only for pours` ``. `` `the scaleless notice has no water and is never disconnected` ``. `` `a free scaleless notice carries the water goal` ``. `` `the scaleless notice only changes when the step changes` ``. `` `when all steps are done the notice says so` ``. Los casos existentes no cambian. |

**Verificación:** `./gradlew :app:compileDebugKotlin`,
`./gradlew :app:testDebugUnitTest --tests "dev.josueamaya.pourline.session.BrewKeepAliveTest"` y
`./gradlew :app:lintDebug` (comprueba el manifiesto y la `property`). Una comprobación rápida por adb sobre
`emulator-5556`, con la app instalada (`ANDROID_SERIAL=emulator-5556 ./gradlew :app:installDebug`) y
`BLUETOOTH_CONNECT` revocado (`adb -s emulator-5556 shell pm revoke dev.josueamaya.pourline android.permission.BLUETOOTH_CONNECT`):

1. Empezar sin báscula y pulsar "Empezar timer". `adb -s emulator-5556 shell dumpsys activity services dev.josueamaya.pourline`
   muestra `BrewService` con `isForeground=true` y el tipo `specialUse`.
2. `adb -s emulator-5556 shell dumpsys power | grep pourline:scaleless` muestra el `WakeLock`.
3. `adb -s emulator-5556 shell dumpsys notification --noredact` muestra el texto del paso.
4. Al terminar, el `WakeLock` y el servicio desaparecen.

Al acabar, `pm grant` devuelve el permiso. La prueba completa con la pantalla apagada está en el §7.3.

Commit sugerido: `feat(session): keep the timed session alive in the background`.

### F7. Historial, detalle y exclusiones

**Objetivo:** las preparaciones sin báscula se distinguen en el historial y no se ofrecen para comparar, como
referencia ni en el progreso.

**Archivos:**

| Archivo | Cambio |
|---|---|
| `ui/history/HistoryRow.kt` | D20. |
| `ui/history/BrewDetailScreen.kt` | D21 en `DetailContent`. |
| `ui/scale/Chart.kt` | `internal fun TargetChart(target: TargetCurve, modifier: Modifier = Modifier)` (nuevo). |
| `ui/scale/BrewChart.kt`, `ui/scale/ChartDrawing.kt` | `flowUnitLabel: String?`: con `null` no se dibuja el eje del flujo (D21). |
| `ui/history/ComparePickViewModel.kt` | `items` sin las preparaciones sin báscula y `hasOthers` con `comparableCount()` (D22). |
| `ui/scale/TargetPickerViewModel.kt` | `brews` sin las preparaciones sin báscula (D22). |
| `res/values/strings.xml`, `res/values-en/strings.xml` | `brew_detail_target_chart`. |

**Tests:**

| Archivo | Casos |
|---|---|
| `androidTest/.../ui/history/HistoryScreenTest.kt` | `scalelessBrewsAreLabeledAndHaveNoPreview`: en Básico y en Experto aparece `history_row_scaleless`, no hay `history_row_preview` en esa fila, y se ven el agua, el tiempo, el ratio y la valoración. `scalelessBrewsDoNotCountForProgress`: con dos puntuadas reales y una sin báscula, el progreso tiene dos puntos. `filterByCoffeeIncludesScalelessBrews`. |
| `androidTest/.../ui/history/BrewDetailScreenTest.kt` | `aScalelessBrewShowsTheTagAndTheFactsInBothModes`. `aScalelessBrewHidesCompareAndSaveAsRecipe` (Experto). `aScalelessBrewWithARecipeShowsOnlyTheTargetCurveInExpert`: está `brew_detail_target_chart` y no hay `FitCard` ni `BrewSummaryCard`. `aScalelessBrewWithoutARecipeHasNoChart`. |
| `androidTest/.../ui/history/ComparePickScreenTest.kt` | `scalelessBrewsAreNotOfferedAsB`. `withOnlyScalelessOthersThereIsNothingToCompare`. |
| `androidTest/.../ui/history/BrewCompareScreenTest.kt`, `androidTest/.../ui/scale/TargetPickerTest.kt` | Sin cambios. La exclusión del selector de referencia se prueba en el E2E de F10 (la pestaña de preparaciones no aparece si la única preparación es sin báscula). |

**Verificación:** `./gradlew :app:compileDebugKotlin`.
`ANDROID_SERIAL=emulator-5556 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.josueamaya.pourline.ui.history.HistoryScreenTest,dev.josueamaya.pourline.ui.history.BrewDetailScreenTest,dev.josueamaya.pourline.ui.history.ComparePickScreenTest,dev.josueamaya.pourline.ui.history.BrewCompareScreenTest,dev.josueamaya.pourline.ui.scale.TargetPickerTest`.

Commit sugerido: `feat(history): tag brews made without a scale and keep them out of comparisons and references`.

### F8. Edición de la dosis y el agua

**Objetivo:** en "Añadir detalles" de una preparación sin báscula se corrigen la dosis y el agua, y el ratio se
recalcula.

**Archivos:**

| Archivo | Cambio |
|---|---|
| `ui/history/BrewEditForm.kt` | Los campos, `parseBrewWater`, `amounts()`, `isValid` y `from(…, amounts)` (D19). |
| `ui/history/BrewEditViewModel.kt` | Carga las cantidades si `entity.scaleless`. `save()` pasa `amounts` a `repository.updateDetails` solo si cambiaron. |
| `ui/history/BrewEditScreen.kt` | Los dos campos (teclado decimal) después de la temperatura, con sus errores, solo si `form.scaleless`. |
| `res/values/strings.xml`, `res/values-en/strings.xml` | `brew_edit_dose`, `brew_edit_water`, `brew_edit_dose_error` y `brew_edit_water_error`. |

**Tests:**

| Archivo | Casos |
|---|---|
| `test/.../ui/history/BrewEditFormTest.kt` | `` `a scale brew form ignores dose and water` ``. `` `a scaleless form is valid with a dose from 5 to 60 g and water above 0 up to 1000 g` ``. `` `an empty dose is valid and gives a null ratio` ``. `` `an invalid dose or water makes the form invalid` `` (0, 61, 1000.1 y dos decimales). `` `amounts round trip through from and amounts` ``. `` `changing dose or water makes the form dirty` ``. |
| `androidTest/.../ui/history/BrewEditScreenTest.kt` | `scalelessBrewsEditDoseAndWaterAndTheRatioIsRecomputed`: 16 g y 260 g → la entidad tiene `doseG = 16`, `totalWaterG = 260` y `finalRatio = 16,25`. `anOutOfRangeDoseShowsTheErrorAndDisablesSave`. `scaleBrewsDoNotShowDoseOrWater`. Los casos existentes no cambian. |

**Verificación:** `./gradlew :app:testDebugUnitTest --tests "dev.josueamaya.pourline.ui.history.*"`.
`ANDROID_SERIAL=emulator-5556 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.josueamaya.pourline.ui.history.BrewEditScreenTest,dev.josueamaya.pourline.history.BrewDaoTest`.

Commit sugerido: `feat(history): correct the dose and water of a brew made without a scale`.

### F9. Compartir

**Objetivo:** la imagen de una preparación sin báscula, sin gráfica ni estabilidad y con la marca "Sin báscula".

**Archivos:**

| Archivo | Cambio |
|---|---|
| `share/ShareModels.kt` | `BrewShareData.scaleless` y `doseG` (D23). |
| `share/BrewShareCard.kt` | La cabecera con `share_scaleless` y la rejilla `share_scaleless_metrics` en `ShareChartSlot` cuando `scaleless`. |
| `ui/session/SessionShare.kt` | `summaryShareData` con `scaleless`, `doseG`, gramos y el nombre del objetivo como subtítulo. |
| `ui/history/BrewDetailScreen.kt` | `detailShareData` con `scaleless` y `doseG = entity.doseG`. |

**Tests:**

| Archivo | Casos |
|---|---|
| `androidTest/.../share/ShareCardsLayoutTest.kt` | `aScalelessCardFitsWithoutChartOrStability`: dentro del marco están `share_scaleless` y `share_scaleless_metrics`, no hay gráfica ni estabilidad, y la cita y el pie caben, con notas largas y a 1,3 de escala de letra. |
| `androidTest/.../ui/history/BrewDetailScreenTest.kt` | `sharingAScalelessBrewBuildsTheScalelessCard`, si el test ya tiene un gancho para el contenido compartido; si no, se cubre con el caso anterior. |

**Verificación:** `./gradlew :app:compileDebugKotlin`.
`ANDROID_SERIAL=emulator-5556 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.josueamaya.pourline.share.ShareCardsLayoutTest,dev.josueamaya.pourline.share.ShareIntentTest`.

Commit sugerido: `feat(share): share a brew made without a scale`.

### F10. Verificación

**Objetivo:** el recorrido completo del §5, las suites, lint, el rendimiento de la sesión con báscula y la prueba
manual por adb.

**Archivos:**

| Archivo | Cambio |
|---|---|
| `androidTest/.../ui/session/ScalelessJourneyTest.kt` (nuevo) | `aScalelessV60FromPrepareToTheHistory`, con `TestApp(env)`, reloj de prueba y sin báscula: Preparar → elegir la V60 clásica → `prepare_start_scaleless` → `scaleless_start` → adelantar a 116 s (`scaleless_done_hint`) → `session_finish` → resumen con `summary_scaleless` → `summary_details` → 4 estrellas → guardar → `summary_done` → Historial, con una fila con `history_row_scaleless` y la valoración. Al final, el selector de referencia de Preparar no tiene la pestaña de preparaciones. `aScalelessSessionEndsAtFifteenMinutes`: adelantar a 900 s → resumen con `MAX_DURATION` en Experto y la preparación guardada. |

**Verificación:** el §7 completo.

Commit sugerido: `test(session): cover the scaleless brew journey end to end`.

## 4. Trazabilidad con el §5 del spec

| Criterio | Fase | Prueba |
|---|---|---|
| Sin báscula, "Empezar sin báscula" visible y habilitado con receta; con la báscula `Ready`, también visible | F5 | `PrepareScreenTest.theScalelessStartIsVisibleAndEnabledWithARecipeAndNoScale` y `…VisibleWithTheScaleReady`; §7.3, paso 1 |
| Sin receta y con la dosis o el ratio inválidos, deshabilitado | F5 | `FreeBrewInputTest` y `PrepareScreenTest.theScalelessStartIsDisabledWithAnInvalidFreeDoseOrRatio` |
| Sin pasos de tara y dosis: el primer paso y "Empezar timer" | F3, F4 | `ScaleSessionManagerScalelessTest` (`prep = null`) y `ScalelessSessionScreenTest.armedShowsTheFirstStep…` |
| En `Active` con receta, el dial, la línea de acción y "paso n/N · quedan m:ss" coinciden con `TargetGuide` | F1, F4 | `SessionFormatTest.the view agrees with TargetGuide every 100 ms` y `ScalelessSessionScreenTest.activeFollowsTheClock` |
| Avisos en los mismos instantes que con la báscula simulada; con la pantalla apagada, la notificación muestra el tiempo y el paso | F3, F6 | `ScaleSessionManagerScalelessTest.stage alerts fire at the same instants…`; `BrewKeepAliveTest`; §7.3, pasos 4 y 5 |
| Al completar el último paso, `scaleless_done_hint` y la sesión sigue en `Active` | F3, F4 | `…the done alert fires once…` y `ScalelessSessionScreenTest.whenAllStepsAreDone…` |
| A los 15 min termina sola con `MAX_DURATION` y se guarda | F1, F3, F10 | `TimedSessionTest`, `ScaleSessionManagerScalelessTest.at 15 minutes…` y `ScalelessJourneyTest.aScalelessSessionEndsAtFifteenMinutes` |
| Guardada con `scaleless = 1`, la dosis y el agua del plan, el ratio, el flujo a 0, la estabilidad y el ajuste en `null`, `GRAMS` y la copia de la curva objetivo | F2, F3 | `BrewDaoTest.aScalelessBrewIsStored…`, `BrewRepositoryMappingTest` y `ScaleSessionManagerScalelessTest.finish saves…` |
| Los pesos de una báscula conectada no cambian el estado (test JVM con una conexión simulada) | F3 | `ScaleSessionManagerScalelessTest.weights, tares and timer messages…` |
| La migración 5 → 6 conserva las preparaciones con `scaleless = 0` | F2 | `HistoryMigrationTest.migrates5To6AddingScalelessAsFalse` y la cadena 1 → 6 |
| Historial con la etiqueta y sin minicurva; Comparar, el selector de referencia y el progreso no las incluyen; el detalle oculta "Comparar" y "Guardar como receta" ("Usar como referencia" no existe: discrepancia 4) | F2, F7, F10 | `HistoryScreenTest`, `BrewDaoTest.progressSkipsScalelessBrews…`, `ComparePickScreenTest`, `BrewDetailScreenTest` y `ScalelessJourneyTest` (selector) |
| "Añadir detalles" edita la dosis y el agua y recalcula el ratio; con báscula no aparecen | F2, F8 | `BrewEditFormTest`, `BrewEditScreenTest` y `BrewDaoTest.applyDetailsWithAmounts…` |
| Compartir sin gráfica y con la marca "Sin báscula" | F9 | `ShareCardsLayoutTest.aScalelessCardFits…`; §7.3, paso 9 |
| Tests existentes en verde y tests nuevos (sesión cronometrada, gestor, repositorio y DAO, migración, E2E de Compose) | F1 a F10 | §7.1. El repositorio y el DAO tienen tests JVM del mapeo y tests instrumentados de Room (D18). El E2E es `ScalelessJourneyTest`. La única expectativa existente que cambia es la versión de la base de datos (discrepancia 10). |
| Prueba manual por adb: una receta completa sin báscula, con la pantalla apagada a mitad, guardada y valorada | F10 | §7.3, pasos 1 a 8 |
| detekt y lint sin avisos nuevos; compilar solo `:app` | Todas | `:app:lintDebug` en F6 y F10 (no hay detekt en el proyecto, §1.2); cada fase compila solo `:app` |

## 5. Riesgos

| ID | Riesgo | Mitigación |
|---|---|---|
| R1 | Sin tráfico BLE, la CPU se suspende con la pantalla apagada y los avisos se retrasan o no llegan. El emulador no lo reproduce. | D15: un `WakeLock` parcial mientras la sesión está en `Active`, con tiempo máximo. Los cálculos usan `elapsedRealtime`, así que un tick tardío no desplaza el tiempo. §7.3: `dumpsys deviceidle force-idle` con la pantalla apagada y, si el usuario lo autoriza, la misma prueba en un dispositivo físico. |
| R2 | El tipo `specialUse` necesita justificarse en Play Console. | P1. La alternativa (mantener `connectedDevice`) queda descrita, y el resto del plan no depende de la elección: solo cambian F6 y el manifiesto. |
| R3 | Android limite la vibración o el pitido en segundo plano. | Es el mismo `AndroidStageNotifier` que la sesión con báscula, con el servicio en primer plano. Si fallara, fallaría igual con báscula, y queda fuera del alcance. Se comprueba en §7.3 (`dumpsys vibrator_manager`). |
| R4 | Una regresión en la sesión con báscula por el enrutado del gestor. | Con `scaleless = false`, todas las ramas son las de hoy (D4), y `sessionUnit == unit`. `ActivePanel`, `PrepPanel`, `BrewDial` y `BrewSession` no se tocan. F3 ejecuta todos los `ScaleSessionManager*Test` sin cambios, y F10 las suites completas y `SessionPerformanceTest`. |
| R5 | Room rechace el esquema migrado por el valor por defecto. | `@ColumnInfo(defaultValue = "0")`, con el precedente de `recipe_stages.type` (v4); `runMigrationsAndValidate` lo compara con `6.json`. |
| R6 | El reloj de 100 ms cueste más de la cuenta o actualice la notificación a 10 Hz. | Solo corre en `Active` sin báscula y hace lo mismo que un peso de la báscula (un `publish`). La notificación solo cambia con el paso (`distinctUntilChanged`, D14). |
| R7 | Subir los textos de `FreeFields` cambie el comportamiento de Preparar. | `rememberSaveable` con la clave `ui.target == null` reproduce el reinicio actual (D10). `PrepareScreenTest.togglingTheRecipeResetsTheFreeTextsAsBefore` y los casos existentes. |
| R8 | Los tests instrumentados con el reloj real sean lentos u oscilen. | El reloj inyectable de D24. Los avances se esperan con `waitUntil`, nunca con `sleep`. |
| R9 | El emulador en `en-US` haga fallar comparaciones de textos. | `testTag` y `stringResource` del contexto de la app. |
| R10 | Una dosis capturada antes con la báscula escale la receta que se congela. | Es el comportamiento de Preparar hoy: el objetivo que se ve es el que se congela (D3). Se anota en el §8. |

## 6. Preguntas para el usuario

Cada una tiene una propuesta; si se aprueba el plan sin comentarios, se aplica la propuesta.

1. **P1. Tipo del servicio en Android 14 o superior (D16).** El tipo `connectedDevice` exige `BLUETOOTH_CONNECT`, y
   precisamente el usuario sin báscula puede no haberlo concedido. Sin servicio, los avisos con la pantalla apagada
   no están garantizados (§4 del spec). *Propuesta: tipo `specialUse` solo para la sesión sin báscula, con su
   permiso y la `property` del subtipo en el manifiesto; si la app se publica en Play, hay que justificarlo en la
   consola.* Alternativa: mantener `connectedDevice` y, sin el permiso, seguir sin servicio. En ese caso la sesión
   funciona con la app delante, pero no se garantizan los avisos con la pantalla apagada, y habría que rebajar el
   criterio correspondiente del §5.
2. **P2. Curva objetivo en el detalle (D21).** *Propuesta: solo en Experto, como las demás gráficas del detalle
   (SPEC-005). En Básico solo se ven los datos.* Alternativa: mostrarla también en Básico.
3. **P3. Dosis vacía al editar (D19 y D27).** *Propuesta: permitir dejar la dosis vacía (sin ratio) solo si la
   preparación se guardó sin dosis; si se escribe una dosis, se aplica el rango de Preparar.* Alternativa: exigir
   siempre una dosis, lo que obliga a inventarla si la referencia no la tenía.
4. **P4. Etiquetas de la edición (D25).** *Propuesta: "Dosis (g)" / "Dose (g)" y "Agua (g)" / "Water (g)", como el
   §3.5 y como "Temperatura (°C)".* Alternativa: "Dosis" y "Agua", como la tabla del §3.7.

## 7. Verificación final

### 7.1 Suites

```
./gradlew :app:testDebugUnitTest
ANDROID_SERIAL=emulator-5556 ./gradlew :app:connectedDebugAndroidTest
./gradlew :app:lintDebug
```

Se compara con la línea base de F0:

- **Tests:** solo suben los totales, por los tests nuevos.
- **Lint:** sin errores ni avisos nuevos, y `MissingTranslation`, `ExtraTranslation` y `UnusedResources` a cero en
  las cadenas nuevas.
- **Fallos que no bloquean:** `CoffeeDetailScreenTest.theFactsAreFoldedByDefaultAndOpenOnTap` y, si oscila, el caso
  Experto de `SessionPerformanceTest`.

Comprobaciones con `grep`:

- ningún `import android` ni `androidx` en `brew/TimedSession.kt`;
- `git diff develop -- app/src/main/kotlin/dev/josueamaya/pourline/brew/BrewSession.kt app/src/main/kotlin/dev/josueamaya/pourline/session/PrepFlow.kt app/src/main/kotlin/dev/josueamaya/pourline/ui/session/BrewDial.kt`
  solo muestra `TimedDial` nuevo en `BrewDial.kt`;
- los permisos nuevos del manifiesto son exactamente `WAKE_LOCK` y, con P1, `FOREGROUND_SERVICE_SPECIAL_USE`.

### 7.2 Rendimiento (`SessionPerformanceTest`, `emulator-5556`)

El spec no pide medidas nuevas, pero el gestor y `ScaleUiState` cambian, así que se comprueba que la sesión con
báscula no empeora:

- **Pasada:** una de cada caso en F10, con el montaje y los umbrales de SPEC-005 (p95 ≤ 32 ms, ≤ 5 % de fotogramas
  por encima de 32 ms y ninguno por encima de 250 ms).
- **Referencia:** las cifras de F0.
- **Si el p95 sube más de 1 ms:** se repiten tres pasadas y se compara la mediana, como en D32 de SPEC-006.

La pantalla sin báscula no tiene gráfica ni sparkline. Se mira a ojo con `adb shell dumpsys gfxinfo dev.josueamaya.pourline`
durante el paso 3 del §7.3.

### 7.3 E2E por adb (sin báscula, sin ARTEMIS)

**Antes de empezar:**

1. `adb devices -l`. Si hay un dispositivo físico, se pregunta si se puede usar para los pasos 4 y 5. Si no, se
   hacen en `emulator-5556`.
2. `adb -s emulator-5556 shell wm size`, para anotar el tamaño y convertir coordenadas.
3. Instalar: `ANDROID_SERIAL=emulator-5556 ./gradlew :app:installDebug`.
4. Limpiar los datos: `adb -s emulator-5556 shell pm clear dev.josueamaya.pourline`.
5. Ajustar los permisos:
   - `adb -s emulator-5556 shell pm grant dev.josueamaya.pourline android.permission.POST_NOTIFICATIONS`;
   - `adb -s emulator-5556 shell pm revoke dev.josueamaya.pourline android.permission.BLUETOOTH_CONNECT`, para el
     caso de un usuario sin báscula.

**Herramientas:**

- Localizar con `uiautomator dump`, tocar con `input tap` y capturar con `screencap -d <display id>`. El id sale de
  `adb shell dumpsys SurfaceFlinger --display-id`.
- `adb logcat -b crash -d` debe quedar vacío en todo el recorrido.
- El emulador está en inglés: los textos esperados son los de `values-en`.

| Paso | Recorrido | Comprobaciones |
|---|---|---|
| 1 | Abrir la app → Básico → Preparar, sin báscula | "Start" deshabilitado, con su ayuda; "Start without scale" visible y habilitado. Captura. |
| 2 | Elegir "V60 classic" → "Start without scale" | Sin pasos de tara ni de dosis: el bloom ("up to 40 g"), la lista de cinco pasos y "Start timer". Captura. |
| 3 | "Start timer" | El reloj en el dial, "Pour to 40 g" y "step 1/5 · 0:0x left". El servicio está en primer plano con el tipo `specialUse` (`dumpsys activity services dev.josueamaya.pourline`) y retiene el `WakeLock` (`dumpsys power \| grep pourline:scaleless`). Captura. |
| 4 | Hacia los 30 s: `adb shell dumpsys battery unplug`, `adb shell input keyevent KEYCODE_POWER` (pantalla apagada) y `adb shell dumpsys deviceidle force-idle` | Hacia los 50 s, `dumpsys notification --noredact` muestra el cronómetro y "Step 3/5 · … · to 150 g". `dumpsys vibrator_manager` registra las vibraciones de los 45 s (y de los 65 s más tarde). Si el AVD no tiene vibrador, se anota y el paso se repite en el dispositivo físico, si el usuario lo autoriza. |
| 5 | Hacia los 120 s: `adb shell input keyevent KEYCODE_WAKEUP` y `adb shell wm dismiss-keyguard` | La notificación decía "All steps done" desde los 115 s. En pantalla, `scaleless_done_hint` y la sesión sigue en curso. Captura. |
| 6 | "Finish" | Resumen con "No scale", el tiempo, 250 g, 15 g y el ratio 1:16.7 (el emulador está en `en-US`). El servicio y el `WakeLock` desaparecen. Captura. |
| 7 | "Add details" → 4 estrellas, una nota, dosis 16 y agua 260 → guardar → "Done" | Al volver, el resumen muestra 16 g, 260 g y 1:16.3. |
| 8 | Historial → la preparación | La fila tiene "No scale", sin minicurva, y la valoración. En el detalle, los datos y ni "Compare" ni "Save as recipe". En Experto (⋯ en Preparar), solo la curva objetivo. Capturas de la fila y del detalle. |
| 9 | Detalle → compartir | La imagen sin gráfica, con "No scale", el tiempo, la dosis, el agua, el ratio, las estrellas y la nota. Captura de la vista previa. |
| 10 | Preparar sin receta: 15 g y 1:16 → "Start without scale"; volver; poner el ratio en 25 | El estado listo muestra 15 g, 1:16 y 240 g. Con el ratio en 25, el botón está deshabilitado. |
| 11 | `pm grant … BLUETOOTH_CONNECT`, conectar la báscula simulada y empezar sin báscula | La sesión no muestra ningún aviso de desconexión, y la tarjeta de la báscula sigue viva en Preparar. "Terminar" y luego "Discard" no guardan nada (el historial no cambia). |

Al terminar: `adb shell dumpsys battery reset` y `adb shell dumpsys deviceidle unforce`.

### 7.4 Capturas

En claro y en oscuro (cambiando "Apariencia" en Ajustes): Preparar con los dos botones, el estado listo, el estado
en curso con receta y sin ella, el aviso de pasos completados, el resumen, la fila del historial, el detalle en
Experto y la imagen de compartir.

- **Dónde:** fuera del repo, con `adb pull` a una carpeta del usuario.
- **Qué revisar a ojo:**
  - que los textos no se corten a 360 dp ni con la escala de letra a 1,3
    (`adb shell settings put system font_scale 1.3`, y después `1.0`);
  - el contraste de la etiqueta "Sin báscula";
  - que el dial sin báscula tenga el mismo tamaño que el de la báscula.

## 8. Seguimiento fuera de este plan

- **Cambios que conviene llevar al spec al cerrar.** No se tocan ahora.
  - `UsedTarget.fit` se guarda con `score = null`, no como `fit = null` (D7).
  - El congelado al abrir la sesión (D3).
  - En el detalle no existe "Usar como referencia" (discrepancia 4).
  - Los tests de Room son instrumentados (D18).
  - `BrewShareData.doseG` (D23).
  - El tipo del servicio y el `WakeLock` (D15, D16 y P1).
  - Las etiquetas con la unidad (D25).
  - La dosis vacía al editar (D19 y P3).
- **Preparar hoy:** con receta, muestra `nominalDoseG` como dosis, mientras que el agua sale de la curva escalada por
  una dosis capturada antes. Es anterior a este spec, y la sesión sin báscula es coherente con lo que se ve (R10).
  Conviene revisarlo aparte.
- **Fuera del alcance (§1 del spec):** pausar y reanudar, pasar a la báscula a mitad de la sesión y unidades
  distintas de gramos.
- **Antes de fusionar en `develop`:** revisar la rama con `/code-review`.
