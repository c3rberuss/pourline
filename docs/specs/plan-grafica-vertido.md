# Plan de implementación: SPEC-001 Gráfica de vertido

| Campo | Valor |
|---|---|
| Spec | [`spec-grafica-vertido.md`](spec-grafica-vertido.md) (SPEC-001) |
| Estado | Completado (F0–F6). Decisiones de §2 incorporadas al spec (§3.9) |
| Fecha | 2026-11-12 |
| Rama | `feat/brew-chart`, creada desde `develop` |
| Módulos | `:app` (`:protocol` no cambia) |

Este plan baja el §8 del spec a archivos, tareas, pruebas y comandos. Antes de las fases recoge las
decisiones que el spec deja abiertas (§2). Casi todas afectan a `BrewSession` y a sus tests, así que
conviene revisarlas antes de empezar la fase 1.

## 1. Punto de partida

**Se reutiliza:**
- `ScaleViewModel` ya recoge `connection.messages` en `viewModelScope` (hilo principal) y reduce
  `ScaleUiState`. `BrewSession` se llamará desde ese mismo colector y desde el tick, ambos en el
  hilo principal, así que no necesita sincronización.
- `ScaleViewModel.send(frame)` ya publica los fallos en el canal `errors`. Sirve tal cual para el
  efecto `StartTimer` (caso límite "Fallo al enviar `startTimer`").
- Componentes Ant: `AntCard`, `AntTag` (`Success`, `Warning`, `Error`), `AntAlert`, `AntSegmented`,
  `AntStatistic`, `AntButton`, `AntIconButton` y `AntTopBar(actions = …)`.
- `AntPalette.contrast` y el patrón de `AntColorsContrastTest` para el test de contraste.
- `ThemeSettings` como modelo de `BrewSettings` (`SharedPreferences "settings"` + `StateFlow`).

**Falta o hay que corregir:**
- `app/build.gradle.kts` no declara `testInstrumentationRunner` y `app/src/androidTest/` está
  vacío, así que hoy no se pueden ejecutar tests instrumentados.
- `AntIcons` no tiene icono de engranaje.
- No hay clase `Application`. `ThemeSettings` se crea en cada `MainActivity` (ver D7).
- `SimulatedScaleConnection` usa `GRAMS_PER_OUNCE = 28.3495` y su vertido está guionizado sobre el
  temporizador (`tick()` con `seconds < 3`, `poured < 250`…).
- `formatNumber` y `formatTime` son privadas de `ScaleScreen.kt`, y `formatTime` trabaja con
  segundos enteros. La gráfica y el resumen necesitan versiones compartidas y en milisegundos.
- `ScanScreen` y `ScaleScreen` no reciben ningún callback para abrir ajustes.

## 2. Decisiones que completan el spec

Se aprobaron y están incorporadas al spec, en el §3.9.

| ID | Tema | Decisión | Motivo |
|---|---|---|---|
| D1 | Muestras bajo el umbral con un vertido abierto | Siguen dentro del vertido (no lo cierran y cuentan para el flujo medio), pero llevan `stability = null` y no entran en la ventana de estabilidad. | Si entraran, el final de cada vertido se marcaría como turbulento durante los 2 s del hueco de cierre. Como contrapartida, un bajón por debajo de 0,5 g/s no penaliza por sí solo (sí lo hacen las transiciones). Se revisa con hardware (§6.1 y §6.4 del spec). |
| D2 | Indicador con el vertido abierto | `currentStability` conserva la última puntuación del vertido abierto. Pasa a `null` en la rampa del vertido siguiente. "Sin vertido" aparece cuando el vertido se cierra, con 2 s de retraso. | Evita que el indicador parpadee a "Midiendo…" en cada bajón breve. |
| D3 | Límites de `Pour` | `endMs` es el `tMs` de la última muestra con flujo ≥ umbral. `waterG` es el `waterG` de la última muestra válida antes del cierre (incluye la cola del hueco) menos el de la última muestra válida anterior a su inicio (0 en el primero). El cierre se evalúa en `onWeight` y en `onTick`. | La suma de los vertidos coincide casi con el agua total, y "Sin vertido" aparece aunque dejen de llegar notificaciones. |
| D4 | Recorte al terminar | Al terminar se descartan las muestras con `tMs > totalTimeMs + POUR_GAP_MS`. `totalWaterG` es el `waterG` de la última muestra válida conservada. | Con `INACTIVITY` la gráfica tendría 60 s de línea plana al final. Con los otros motivos no hay muestras posteriores al evento, así que el recorte no cambia nada. |
| D5 | Peso negativo | Una muestra es inválida si `outOfRange` o si su peso es menor que −1,0 g (`negativeWeightToleranceG`). | El ruido alrededor de 0 g tras una tara abriría huecos de una muestra. Al retirar la taza el peso baja mucho más. |
| D6 | Ponderación por tiempo | Cada muestra pesa el Δt respecto a la anterior, con un tope de 500 ms (`maxSampleGapMs`). Se aplica a `avgFlowGps`, `stabilityScore` y `levelShare`. | Un corte de notificaciones no debe pesar como segundos de vertido. |
| D7 | Ámbito de `BrewSettings` | Una sola instancia por proceso, en una nueva `DotScaleApp : Application`. La clase se adelanta a la fase 3, y la pantalla sigue en la 5. | `ScaleViewModel` sobrevive a la rotación. Con una instancia por `Activity`, tras rotar la pantalla de ajustes escribiría en un `StateFlow` que el ViewModel ya no observa, y el cambio no llegaría a la sesión en curso. |
| D8 | Archivos de textos | Todos los textos van en `values/strings.xml` y `values-en/strings.xml`, y se traducen también al inglés los 56 textos que ya existían. | Al crear `values-en/`, lint marcaría como error `MissingTranslation` los textos sin traducir. Traducirlos evita excepciones y deja la app coherente en inglés. Amplía el alcance del §1 (antes fuera de alcance). *Cambio posterior: la primera versión usaba `strings_brew.xml` con `tools:ignore`.* |
| D9 | Dosis | El ViewModel llama a `setDoseGrams` solo en `captureDose()` y `clearDose()`. `finalRatio` se fija al terminar. | `ScaleUiState.reduce(UnitChanged)` borra hoy la dosis. Como la sesión la guarda en gramos, cambiar de unidad a mitad no pierde el ratio. |
| D10 | Factor de onzas | `GRAMS_PER_OUNCE = 28.349523125` vive en `dev.dotscale.brew` y lo usa también el simulador. | Hoy el simulador usa 28,3495 y el spec pide el valor exacto. |
| D11 | Tara | En `Armed` (o `Finished` rearmada), una tara descarta la racha de confirmación y la última muestra válida. En `Active`, se aplica `baselineG -= lastRawWeightG` (último peso bruto recibido). | Supone que `Tared` llega antes de la primera muestra posterior a la tara, como en el simulador. Con la DOT está sin verificar (R1). |
| D12 | Simulador | `simulatePour` se ignora si ya hay un vertido simulado en curso. Perfil: bloom de 9 s a 5 g/s (~45 g), pausa de 20 s y vertido principal a 4 g/s hasta ~250 g. `IRREGULAR` multiplica el flujo objetivo por un factor U(0,35; 1,65) que cambia cada 5 ticks, con semilla fija. | Con una pausa de menos de 30 s, el E2E con fin a 30 s no termina en el bloom. El factor mínimo deja el flujo por encima del umbral, así que el vertido no se parte. |
| D13 | Publicación del estado | `BrewSession.state` es una instancia cacheada que solo se reconstruye cuando algo cambia. `samples` es una copia inmutable de la lista interna. | Con la misma instancia, la igualdad de `StateFlow` se resuelve por identidad. La copia cuesta O(n) por muestra con n ≤ 9000, lo que es despreciable. Si el perfilado muestra jank, se cambia por una vista de solo-anexar. |
| D14 | Accesibilidad del indicador | El `AntTag` del nivel no es `liveRegion`. El nivel se anuncia en el `contentDescription` del `Canvas`. | A 10 Hz, una live region saturaría TalkBack. El texto del tag ya evita que el color sea el único indicador (WCAG 1.4.1). |

**Punto abierto (no bloquea):**
- **P1. Temporizador al terminar.** El spec no pausa ni reinicia el temporizador al terminar.
- Tras un fin por inactividad, el temporizador de la báscula sigue contando.
- Si después se rearma con tara, la siguiente sesión no envía `startTimer` porque ya está `RUNNING`,
  y el reloj de `WeightCard` no coincide con el eje X.
- Propuesta: mantener el spec y decidir en SPEC-002. La alternativa es enviar `resetTimer` antes de
  `startTimer` al confirmar el inicio.
- *Resuelto en [SPEC-002](spec-historial.md) §3.5: `pauseTimer` al terminar por inactividad, de forma
  manual o por duración máxima, y `resetTimer` al rearmar con tara.*

## 3. Fases

Orden: F0 → F1 → (F2 ∥ F3) → F4 → F5 → F6. F5 puede hacerse en paralelo con F4 una vez terminada
F3.

| Fase | Contenido | Tamaño |
|---|---|---|
| F0 | Preparación: rama, runner instrumentado y línea base de lint | S |
| F1 | Lógica pura: modelo, sesión, estabilidad, resumen, ejes y reducción de muestreo | L |
| F2 | Simulador desacoplado del temporizador | S |
| F3 | Integración en `ScaleViewModel` y `BrewSettings` | M |
| F4 | UI: gráfica, tarjeta de la gráfica y resumen | L |
| F5 | Pantalla de ajustes y navegación | M |
| F6 | Verificación: exploración por adb, tests instrumentados y checklist del §5 | M |

Commits: uno por fase, en Conventional Commits y en inglés, solo cuando se pidan. Se proponen los
mensajes al final de cada fase. *Al final se agruparon en tres: `bfead5e` (F0), `c63fc32` (F1 y
F2) y `7e917ac` (F3 a F6).*

### F0. Preparación

1. Crear la rama `feat/brew-chart` desde `develop`.
2. En `app/build.gradle.kts`, `defaultConfig`, añadir
   `testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"`.
3. Tomar la línea base de lint para poder comprobar "sin avisos nuevos": ejecutar
   `./gradlew :app:lintDebug` y anotar el número de avisos y errores del informe.
4. Comprobar que `./gradlew :app:testDebugUnitTest` pasa antes de tocar nada.

Commit sugerido: `build(app): add AndroidJUnitRunner for instrumented tests`.

### F1. Lógica pura (`app/src/main/kotlin/dev/dotscale/brew/`)

Kotlin puro: solo puede importar de `kotlin.*` y `dev.dotscale.protocol` (módulo JVM).

**Archivos:**

| Archivo | Contenido |
|---|---|
| `BrewModels.kt` | `BrewSample`, `StabilityLevel`, `Pour`, `FinishReason`, `BrewSummary`, `BrewPhase`, `BrewState` tal como el §3.2, más `BrewState.Initial` (`Armed`, sin muestras). |
| `BrewConfig.kt` | `data class BrewConfig` con las constantes del §3.4 en camelCase (`pourThresholdGps = 0.5`, `pourConfirmMs = 500`, …, `maxDurationMs = 900_000`) y las internas `maxSampleGapMs = 500` (D6) y `negativeWeightToleranceG = 1.0` (D5). Incluye `fun levelOf(score: Int): StabilityLevel`. |
| `Units.kt` | `GRAMS_PER_OUNCE` (D10) y las conversiones `toGrams(value, unit)` y `fromGrams(valueG, unit)`. |
| `Stability.kt` | `fun stabilityScore(flows: List<Double>, cvZeroScore: Double): Int`, que calcula CV con σ poblacional y `round(100 × (1 − min(CV / cvZero, 1)))`, con guarda para μ ≤ 0. |
| `BrewSummaryCalculator.kt` | `fun summarize(samples, pours, totalTimeMs, reason, doseG, config): BrewSummary`, una función pura con la ponderación de D6. |
| `BrewSession.kt` | La máquina de estados del §3.3 (ver abajo). |
| `ChartAxis.kt` | `object ChartAxis` con `timeTopMs(maxTMs)`, `waterTop(max, unit)` y `flowTop(max, unit)`. Cada uno devuelve el menor escalón `s` que cumple `max ≤ 0,85 × s`. |
| `Downsampling.kt` | `data class ChartPoint(tMs, value, stability, breakBefore)` y `fun downsample(samples, widthPx, xTopMs, value: (BrewSample) -> Double): List<ChartPoint>`. |

**`ChartAxis`.** Recibe el máximo acumulado de la sesión. Como ese máximo nunca baja, el tope
tampoco: no hace falta guardar estado y se reinicia solo con la sesión nueva. Para un cambio de
unidad, la UI convierte el máximo a la unidad nueva y usa su tabla.

**`downsample`.**
- Asigna cada muestra a la columna `floor(tMs / xTopMs × widthPx)` y emite el mínimo y el máximo de
  cada columna en orden temporal.
- Los huecos (`valid = false`) no generan puntos: el siguiente punto válido lleva
  `breakBefore = true`. Así la salida nunca pasa de `2 × widthPx` puntos.
- Cada punto conserva la `stability` de la muestra de la que sale, y con eso se colorea la curva de
  flujo.

**`BrewSession`.** Estado interno:
- En cualquier fase: `lastValid` (última muestra válida), `run` (racha de muestras válidas con
  flujo ≥ umbral todavía sin confirmar) y `lastRawWeightG`.
- Con sesión: `t0`, `baselineG`, `samples`, `pours`, `openPour` (inicio, última muestra ≥ umbral,
  agua al inicio), `lastAboveMs`, `startTimerSent` y `doseG`.

`onWeight(msg, now, timer)`:
1. Convierte a g y g/s y calcula `valid` (D5, D10).
2. Actualiza `run`: añade la muestra si es válida con flujo ≥ umbral y, si no, vacía la racha. La
   racha queda confirmada cuando `último.t − primero.t ≥ pourConfirmMs`.
3. Según la fase:
   - **`Armed` o `Finished(rearmed)`.** Si la racha se confirma, se inicia la sesión:
     - Se borran las muestras y los vertidos.
     - `t0` es la primera muestra de la racha.
     - La línea base es la última muestra válida anterior a la racha o, si no la hay, la primera de
       la racha.
     - Las muestras de la racha se añaden a la gráfica.
     - Se emite `StartTimer` si `timer != RUNNING` y no se había enviado ya en esta sesión.
   - **`Finished` sin rearmar.** Si la racha se confirma, `pourIgnored = true` y no cambia nada más.
   - **`Active`.** `tMs = now − t0`. Si `tMs ≥ maxDurationMs`, termina con `MAX_DURATION`; si no,
     llama a `appendSample`.
4. Actualiza `lastValid` y `lastRawWeightG`, y reconstruye `state` solo si algo cambió (D13).

`appendSample`:
- Calcula `waterG = peso − baselineG`.
- Abre un vertido cuando el flujo cruza el umbral y lo cierra tras `pourGapMs` por debajo (D1, D3).
- Calcula la estabilidad según el §3.4 y D1:
  - Rampa → `null`.
  - Ventana `(t − 3000, t]` con solo muestras válidas, ≥ umbral, del vertido actual y fuera de su
    rampa.
  - Cobertura (`último.t − primero.t`) menor que 1000 ms → `null`.
- Actualiza `currentStability` y `currentLevel` (D2).

Resto de métodos:

| Método | `Armed` | `Active` | `Finished` |
|---|---|---|---|
| `onTimer(PAUSED)` | — | Fin `TIMER_PAUSED` | — |
| `onTimer(RESET)` | — | Fin `TIMER_RESET`, sin rearmar | `rearmed = true`, `pourIgnored = false` |
| `onTared` | Descarta `run` y `lastValid` (D11) | `baselineG -= lastRawWeightG` (D11) | `rearmed = true`, `pourIgnored = false`, descarta `run` |
| `onDisconnected` | Descarta `run` | Fin `DISCONNECTED`, `totalTimeMs` = última muestra | — |
| `onTick(now, autoFinishMs)` | — | Cierra el vertido abierto si toca (D3); fin `INACTIVITY` si `now − lastAbove ≥ autoFinishMs`; fin `MAX_DURATION` si `now − t0 ≥ maxDurationMs` | — |
| `finish(now)` | — | Fin `MANUAL` | — |
| `setDoseGrams(dose)` | Guarda | Guarda | Guarda para la sesión siguiente; no toca el resumen (D9) |

Todos los fines pasan por un único `finishWith(reason, totalTimeMs)`:
1. Cierra el vertido abierto.
2. Recorta las muestras (D4).
3. Calcula el resumen con `summarize`.
4. Pasa a `Finished(rearmed = false, pourIgnored = false)`.

**Tests** (`app/src/test/kotlin/dev/dotscale/brew/`):

`BrewTestFixtures.kt` contiene un `FakeScale` que genera `ScaleMessage.Weight` cada 100 ms, acumula
el peso según el flujo y cuantiza como la báscula (×10 en g, ×100 en oz). Tiene `pour(gps, ms)`,
`idle(ms)`, `tare()` y `unit = OUNCES`.

| Archivo | Casos |
|---|---|
| `BrewSessionTest.kt` | Inicio tras 500 ms y t0 en la primera muestra de la racha. Pico de 400 ms rechazado. Línea base sin tara previa (el agua parte de 0). Tara en `Active` sin salto en el agua, y tara en `Armed` (D11). Segmentación con `POUR_GAP_MS`, más un bajón de menos de 2 s que no parte el vertido. Rampa excluida, con "Midiendo…" (`currentStability == null` y `pouring`). Ventana que no mezcla vertidos: dos vertidos de flujos distintos separados por 2000 ms puntúan 100 los dos. `pourIgnored` sin cambios en muestras ni resumen. Los seis motivos de fin, con el `totalTimeMs` de cada uno. Rearme con tara y con `RESET`, y `RESET` que termina sin rearmar. Onzas convertidas. Muestras inválidas como hueco y fuera de las métricas. `StartTimer` una sola vez, y ninguna si el temporizador estaba `RUNNING`. Recorte D4. |
| `StabilityTest.kt` | Constante → 100; 3,0 y 5,0 alternos (CV 0,25) → 50; 2,0 y 6,0 alternos (CV 0,5) → 0; CV > 0,5 → 0. Ventana con menos de 1000 ms de cobertura → `null` (a nivel de sesión). |
| `BrewSummaryCalculatorTest.kt` | Flujo medio y estabilidad ponderados por tiempo, `levelShare` solo sobre el tiempo puntuado (suma 1), tope de Δt (D6), `finalRatio` con y sin dosis, `interrupted`. |
| `ChartAxisTest.kt` | Escalones de los tres ejes en g y oz, frontera del 85 % (51 g → 60; 51,1 g → 120), crecimiento de 200 g en 200 g y de 8 oz en 8 oz, y que con máximos crecientes el tope nunca decrece. |
| `DownsamplingTest.kt` | Salida de como máximo `2 × widthPx` puntos con 9000 muestras, conserva el mínimo y el máximo de cada columna, `breakBefore` tras un hueco, y `widthPx ≤ 0` devuelve una lista vacía. |

**Verificación:**
- `./gradlew :app:testDebugUnitTest --tests "dev.dotscale.brew.*"`.
- `grep -rn "import android" app/src/main/kotlin/dev/dotscale/brew` no devuelve nada.

Commit sugerido: `feat(brew): add brew session, stability metric and chart axes`.

### F2. Simulador

**Archivos:**
- `sim/SimulatedPour.kt` (nuevo, Kotlin puro): `enum class PourProfile { STABLE, IRREGULAR }` y
  `class SimulatedPour(profile, seed = 42)`.
  - `next(): Double?` devuelve el flujo suavizado de cada tick (`flow += (objetivo − flow) × 0,3`,
    igual que hoy) y `null` al acabar el guion (D12).
  - Se separa del simulador para poder probarlo en la JVM.
- `sim/SimulatedScaleConnection.kt`:
  - Elimina el guion ligado al temporizador y `poured`.
  - `tick()` usa `pour?.next()`. Sin vertido, el flujo decae ×0,5 como hoy. El temporizador solo
    avanza `tenths`.
  - Añade `suspend fun simulatePour(profile)`, que pone `pour` bajo el `lock` y se ignora si ya hay
    uno en curso.
  - Usa `GRAMS_PER_OUNCE` de `brew` (D10) y actualiza el KDoc de la clase.

**Tests:** `app/src/test/kotlin/dev/dotscale/sim/SimulatedPourTest.kt` pasa cada perfil por
`BrewSession` con la cuantización del payload. Con esto el criterio de `levelShare` del §5 se puede
comprobar de forma determinista en la JVM, además de en el emulador.
- `STABLE`: `levelShare[STABLE] ≥ 0,7`, dos vertidos y unos 250 g en total.
- `IRREGULAR`: `levelShare[IRREGULAR] + levelShare[TURBULENT] ≥ 0,7`.
- Ninguno de los dos termina en la pausa con `autoFinish` de 60 s ni de 30 s.

Commit sugerido: `feat(sim): decouple simulated pours from the timer`.

### F3. Integración en el ViewModel

**Archivos:**
- `settings/BrewSettings.kt` (nuevo):
  - Mismo patrón que `ThemeSettings`: archivo `"settings"` y clave `"auto_finish_seconds"`.
  - Expone `ALLOWED = listOf(30, 45, 60, 90, 120)`, `autoFinishSeconds: StateFlow<Int>` y
    `setAutoFinishSeconds(Int)`, que hace `require(value in ALLOWED)`.
  - Un valor guardado fuera de `ALLOWED` se lee como 60.
- `DotScaleApp.kt` (nuevo): `class DotScaleApp : Application` con
  `val brewSettings by lazy { BrewSettings(this) }`. Se registra en el manifiesto con
  `android:name=".DotScaleApp"` (D7).
- `ui/scale/ScaleUiState.kt`: añade el campo `brew: BrewState = BrewState.Initial`. `reduce` no
  cambia.
- `ui/scale/ScaleViewModel.kt`:
  - Constructor:
    `ScaleViewModel(connection, autoFinishSeconds: StateFlow<Int>, clock: () -> Long = SystemClock::elapsedRealtime)`.
  - En el colector de mensajes, primero `reduce` y después `Weight` → `onWeight(m, clock(), ui.timer)`,
    `Timer` → `onTimer` y `Tared(success = true)` → `onTared`.
  - Ejecuta los efectos: `StartTimer` → `send(ScaleCommands.startTimer())`.
  - Publica con `_ui.update { it.copy(brew = session.state) }`.
  - En el colector de conexión, guarda el estado anterior y llama a `onDisconnected` en la
    transición de `Ready` a `Disconnected` o `Failed`.
  - Tick: `viewModelScope.launch { while (isActive) { delay(1_000); session.onTick(clock(), autoFinishSeconds.value * 1_000L); publish() } }`.
  - Nuevas funciones: `finishBrew()` y `simulatePour(profile)`.
  - `captureDose()` y `clearDose()` llaman a `setDoseGrams` (D9).
- `Navigation.kt`: el `entry<Scale>` construye el ViewModel con
  `(appContext as DotScaleApp).brewSettings.autoFinishSeconds`.

**Tests (opcional):** `ScaleViewModelBrewTest`, con una `ScaleConnection` falsa, el reloj inyectado
y `Dispatchers.setMain`. Comprobaría que un vertido produce exactamente un `startTimer` enviado.
Hay que cancelar el tick al terminar el test. La integración también se cubre en F6.

**Verificación:** `./gradlew :app:compileDebugKotlin` y `./gradlew :app:testDebugUnitTest`.

Commit sugerido: `feat(scale): track brew sessions in ScaleViewModel`.

### F4. UI de la gráfica y del resumen

**Archivos** (en `app/src/main/kotlin/dev/dotscale/ui/scale/`):

| Archivo | Contenido |
|---|---|
| `Formatters.kt` | Se mueve `formatNumber` desde `ScaleScreen.kt` y se añaden `formatDuration(ms)` (`mm:ss`) y la conversión de g a la unidad mostrada. |
| `BrewChartColors.kt` | `data class BrewChartColors` y `fun brewChartColors(colors: AntColors)`. Agua: `primary.base`. Estable, Irregular y Turbulento: `success.text`, `warning.text` y `error.text` (los tonos `base` de `success` y `warning` no llegan a 3:1 sobre blanco). Neutro: `textSecondary`. Relleno: `primary.base` al 15 %. Rejilla: `split`. |
| `BrewChart.kt` | `Canvas` con eje izquierdo de agua, eje derecho de flujo y eje X en `mm:ss`, cuatro divisiones horizontales y etiquetas con `rememberTextMeasurer`. Los topes salen de `ChartAxis` con los máximos de las muestras. Hace `downsample` por serie con el ancho del área de dibujo, envuelto en `remember(samples, unit, width)`. El agua se dibuja con relleno; el flujo, por tramos coloreados por nivel y cortados en los huecos. El `contentDescription` lleva el agua, el flujo y el nivel actuales. |
| `BrewChartCard.kt` | En `Armed`: "Vierte agua para empezar". En `Active`: gráfica, `AntTag` con el nivel ("Estable · 82", "Midiendo…" o "Sin vertido") y botón "Terminar". En `Finished`: gráfica congelada, el texto "Lista para la siguiente preparación" si `rearmed` y un `AntAlert` informativo si `pourIgnored`. |
| `BrewSummaryCard.kt` | Tiempo total, agua total, flujo medio y máximo, estabilidad (puntuación, nivel y porcentaje por nivel), lista de vertidos (número, gramos y duración), ratio `1:x` solo con dosis (reutiliza `scale_ratio_value`) y un `AntAlert` de aviso si `interrupted`. |
| `ScaleScreen.kt` | Inserta `BrewChartCard` tras `BrewControls` y `BrewSummaryCard` debajo en `Finished`. `SimulationCard` gana una segunda fila con "Simular vertido estable" y "Simular vertido irregular". |

- `testTag`: `brew_chart`, `brew_level`, `brew_finish` y `brew_summary`, para los tests de F6.
- Previews de `Armed`, `Active` y `Finished` en claro y oscuro, con un `BrewState` de ejemplo
  privado del archivo.
- Textos (D8): añadir a `values/strings.xml` los textos con prefijos `brew_` y `sim_pour_`, y crear
  `values-en/strings.xml` con todos los textos de la app traducidos.

**Tests:** `app/src/test/kotlin/dev/dotscale/ui/scale/BrewChartColorsTest.kt` comprueba un contraste
≥ 3:1 de cada color de serie contra `bgContainer`, en claro y en oscuro. Los colores con alfa, como
`textSecondary`, se componen antes sobre el fondo con `compositeOver`, porque `AntPalette.contrast`
espera colores opacos.

**Verificación:**
- `./gradlew :app:testDebugUnitTest` y `./gradlew :app:lintDebug` (sin `MissingTranslation` ni
  avisos nuevos).
- Revisión visual rápida en el emulador con la báscula simulada.

Commit sugerido: `feat(ui): add live brew chart and summary card`.

### F5. Pantalla de ajustes

**Archivos:**
- `NavigationKeys.kt`: `@Serializable data object Settings : NavKey`.
- `Navigation.kt`: `entry<Settings> { SettingsScreen(brewSettings, onBack = …) }`, y
  `onOpenSettings = { backStack.add(Settings) }` en `Scan` y `Scale`.
- `ui/settings/SettingsScreen.kt` (nuevo): `SettingsScreen(settings: BrewSettings, onBack)`.
  - `AntTopBar` con flecha atrás.
  - `AntCard` "Fin automático" con un `AntSegmented` de 30, 45, 60, 90 y 120 s, y texto de ayuda
    sobre la pausa del bloom.
  - Recibe `BrewSettings` por parámetro, sin `CompositionLocal`, para poder probarla aislada.
- `ui/components/AntIcons.kt`: icono `Settings` con trazo propio, sin recursos de TIMEMORE.
- `ScanScreen.kt` y `ScaleScreen.kt`: nuevo parámetro `onOpenSettings` y un `AntIconButton` con el
  engranaje en `actions`, antes de `ThemeModeButton`, que no se mueve.
- Textos `settings_*` en los dos `strings.xml`.

**Verificación:** `./gradlew :app:compileDebugKotlin` y `./gradlew :app:lintDebug`.

Commit sugerido: `feat(settings): add settings screen with auto-finish preference`.

### F6. Verificación

1. **Dispositivo.** Ejecutar `adb devices -l`. Si hay más de un dispositivo o emulador, preguntar
   cuál usar. Instalar con
   `./gradlew :app:installDebug`.
2. **Exploración en el emulador** con la báscula simulada, por adb (`uiautomator dump`, `input`, `screencap`). *No se usa ARTEMIS: no funciona.*

   | Escenario | Recorrido | Comprobaciones |
   |---|---|---|
   | E1 | Ajustes → 30 s → Scale → "Simular vertido estable" → esperar el fin por inactividad | Arranque sin tocar nada, el temporizador empieza a contar, la pausa del bloom no termina la sesión, la curva queda casi toda en verde y el resumen aparece |
   | E2 | "Simular vertido irregular" → "Terminar" a mitad | Tramos ámbar o rojos, el indicador cambia de nivel y el resumen muestra el motivo manual |
   | E3 | Tras E2, sin tara, "Simular vertido estable" → "Tara" → simular otra vez | Aviso de `pourIgnored` con el resumen conservado, después "Lista para la siguiente preparación" y, al verter, una gráfica nueva |

   El perfil estable dura unos 80 s más el fin automático.
3. **Tests instrumentados** (`app/src/androidTest/kotlin/dev/dotscale/ui/`), escritos a partir de
   lo verificado en el paso 2:
   - `BrewChartTest`: `createComposeRule()` con `ScaleScreen(ScaleViewModel(SimulatedScaleConnection(), MutableStateFlow(60)), …)`
     dentro de `DotScaleTheme`, sin permisos BLE.
     - "Simular vertido estable" → `waitUntil(5_000)` hasta que existe `brew_chart`.
     - "Terminar" → `brew_summary` visible.
     - En `@After`, `connection.disconnect()`.
   - `SettingsScreenTest`: `SettingsScreen(BrewSettings(targetContext))` → pulsar "30 s" y comprobar
     que `SharedPreferences` y `autoFinishSeconds` valen 30. Restaura el valor en `@After`.
   - `SettingsNavigationTest`: `createAndroidComposeRule<MainActivity>()`. Los permisos BLE se
     conceden antes de lanzar la `Activity` con `uiAutomation.grantRuntimePermission`, que no añade
     dependencias.
     - El engranaje abre Ajustes desde Scan.
     - Tras "Probar con báscula simulada", el engranaje abre Ajustes desde Scale.
   - Los localizadores usan `testTag` y textos leídos con `targetContext.getString(…)`, nunca
     coordenadas.
4. **Ejecutar:**
   - `./gradlew :app:testDebugUnitTest`
   - `./gradlew :app:connectedDebugAndroidTest`
   - `./gradlew :app:lintDebug`, comparado con la línea base de F0.
5. Marcar el checklist del §5 en el spec y pasar al spec las decisiones aprobadas de §2. *Hecho:
   §5 marcado y decisiones en el §3.9 del spec.*

Commit sugerido: `test(ui): add instrumented tests for brew chart and settings`.

## 4. Trazabilidad con el §5 del spec

| Criterio | Fase | Prueba |
|---|---|---|
| Inicio en ≤ `POUR_CONFIRM_MS` + 1 muestra | F1, F3 | `BrewSessionTest` (inicio), `BrewChartTest`, E1 |
| `startTimer` exactamente una vez | F1, F3 | `BrewSessionTest` (emisión única), E1 |
| Ejes escalonados y curva coloreada | F1, F4 | `ChartAxisTest`, `DownsamplingTest`, E1 y E2 |
| `levelShare` ≥ 0,7 con los dos perfiles | F2 | `SimulatedPourTest`, E1 y E2 |
| Verter tras terminar muestra el aviso | F1, F4 | `BrewSessionTest` (`pourIgnored`), E3 |
| El bloom no termina la sesión con 60 s | F2 | `SimulatedPourTest`, E1 |
| Seis motivos de fin y métricas del resumen | F1, F4 | `BrewSessionTest`, `BrewSummaryCalculatorTest`, E1 y E2 |
| Rearme sin borrar el resumen | F1 | `BrewSessionTest` (rearme), E3 |
| Ajustes desde Scan y Scale, aplicados en curso | F3, F5 | `SettingsScreenTest`, `SettingsNavigationTest`, E1 |
| Textos en `values/` y `values-en/` | F4, F5 | `lintDebug` con `MissingTranslation` activo (D8) |
| Contraste de 3:1 de las series | F4 | `BrewChartColorsTest` |
| Sin avisos nuevos de lint | F0, F6 | `lintDebug` frente a la línea base |

## 5. Riesgos

| ID | Riesgo | Mitigación |
|---|---|---|
| R1 | La DOT podría enviar la primera muestra posterior a la tara antes que `Tared` (D11), y la curva saltaría. | Test con el orden del simulador. Con hardware, comprobarlo en `adb logcat -s BleScale`. Si ocurre, corregir hacia atrás las muestras entre el salto y `Tared`. |
| R2 | En onzas, el flujo llega con una resolución de 0,01 oz/s (≈ 0,28 g/s), y a flujos bajos el CV se infla por la cuantización. | Documentarlo. Se resuelve junto con el §6.1 del spec, valorando un flujo derivado del peso. |
| R3 | Si el firmware suaviza el flujo, la estabilidad sale casi siempre alta (§6.1). | Las constantes están en `BrewConfig`, así que calibrar no toca la lógica. |
| R4 | Doble inicio si la DOT arranca sola el temporizador (§6.3). | `StartTimer` solo se envía si `timer != RUNNING`. Verificar con hardware. |
| R5 | El E2E es largo: unos 80 s de perfil más el fin automático. | E1 usa 30 s. Los tests instrumentados no esperan al fin por inactividad y usan "Terminar". |
| R6 | Recomposición a 10 Hz con hasta 9000 muestras. | `downsample` dentro de `remember`, estado cacheado (D13) y medición con el Layout Inspector si aparece jank. |

## 6. Seguimiento fuera de este plan

- Preguntas abiertas del §6 del spec, para cuando llegue la DOT física.
- P1 (temporizador al terminar), para decidir antes de SPEC-002. *Resuelto en SPEC-002 §3.5.*
- SPEC-002 necesita persistencia (Room). Este plan no deja nada guardado más allá de la preferencia.
