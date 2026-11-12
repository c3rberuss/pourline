# SPEC-001: Gráfica de vertido en tiempo real con estabilidad del flujo

| Campo | Valor |
|---|---|
| Estado | Aprobado para implementación |
| Fecha | 2026-11-12 |
| Módulos afectados | `:app` (nuevo paquete `dev.dotscale.brew`, `ui/scale`, `settings`, `sim`) |
| Specs relacionados | SPEC-002 Historial, SPEC-003 Curva objetivo, SPEC-004 Compartir imagen (pendientes) |

## 1. Resumen y Contexto

- **Problema:** la app oficial de TIMEMORE (`com.timemore.blackmirror` v2.6.0) muestra durante la
  preparación una "Curva de peso y flujo" (重量与流速曲线) que esta app no tiene. Además, ninguna de
  las dos dice lo regular que es el chorro, que es lo que un entusiasta del pour-over quiere
  mejorar. La app se publicará para cualquier persona aficionada al café.
- **Solución:** añadir a la pantalla Scale una gráfica en vivo con dos series: agua vertida (g) y
  flujo (g/s). La curva de flujo se colorea según una métrica propia de estabilidad (0-100), con
  arranque y fin automáticos, y al terminar se muestra una tarjeta de resumen.
- **Alcance (In):**
  - Gráfica en vivo de doble eje en la pantalla Scale.
  - Métrica de estabilidad del flujo, con indicador en vivo y coloreado de la curva.
  - Inicio automático al detectar vertido, enviando `startTimer` a la báscula si hace falta.
  - Fin automático por inactividad (configurable), por temporizador, manual, por desconexión o por
    duración máxima.
  - Tarjeta de resumen efímera al terminar.
  - Pantalla de ajustes nueva con la preferencia de fin automático.
  - Simulador capaz de generar vertidos estables e irregulares sin depender del temporizador.
  - Textos de la app en español e inglés (incluidos los que ya existían).
- **Alcance (Out):**
  - Historial de preparaciones (SPEC-002).
  - Curva objetivo superpuesta, desde el historial o desde recetas editables (SPEC-003).
  - Compartir la gráfica como imagen (SPEC-004).
  - Modo espresso y etapas de preparación (`ScaleMessage.BrewModeStage`).
  - Reconexión BLE automática.
  - Validación con la báscula DOT física (seguimiento, ver §6).

### Referencia: comportamiento de la app oficial

Se obtuvo de la decompilación de la v2.6.0 y sirve como guía, no como requisito de igualdad:

- Las clases `ui.brew.BrewingActivity` y `views.linechart.DarkDynamicLineChartView` dibujan peso y
  flujo frente al tiempo con dos ejes Y y resolución de 100 ms.
- Con las básculas del protocolo 2025, el flujo no lo calcula la app: lo envía la báscula en la
  notificación de peso (`scaleWeightBean.getSpeed()`).
- Los ejes Y crecen por escalones cuando el valor pasa del 85 % del tope
  (`Utils.calcPourOverSpeedYMax` y `calcPourOverWeightYMax`).
- La app oficial no tiene ninguna métrica de estabilidad ni de "turbulencia"; esa métrica es propia
  de esta app. Lo más parecido en la oficial es el minijuego "控水大师" (control de agua).

La gráfica debe tener diseño propio (tema Ant Design de la app) y no reutilizar recursos gráficos ni
textos de TIMEMORE.

## 2. Historias de Usuario y Casos de Uso

- **US-1:** Como aficionado al pour-over, quiero que la gráfica empiece sola al verter, para no
  tocar el móvil con la tetera en la mano.
- **US-2:** Como aficionado, quiero ver en vivo cuánta agua llevo y a qué flujo vierto, para seguir
  mi receta.
- **US-3:** Como aficionado, quiero saber en vivo si mi chorro es estable, irregular o turbulento,
  para corregir el pulso mientras vierto.
- **US-4:** Como aficionado, quiero un resumen al terminar (tiempo, agua, flujos, estabilidad,
  vertidos y ratio), para evaluar la preparación.
- **US-5:** Como aficionado, quiero que retirar la taza o un goteo no me borre el resumen, y que la
  siguiente preparación empiece cuando yo haga tara o reinicie el temporizador.
- **US-6:** Como aficionado con blooms largos, quiero elegir cuánto tiempo sin verter da la
  preparación por terminada.
- **US-7:** Como desarrollador sin la báscula física, quiero simular vertidos estables e irregulares
  para probar la funcionalidad completa.

### Flujo principal

1. El usuario conecta la báscula (real o simulada) y entra en la pantalla Scale. La sesión empieza
   en estado **Armada** y la tarjeta de la gráfica muestra "Vierte agua para empezar".
2. El usuario empieza a verter. Cuando el flujo supera el umbral durante el tiempo de
   confirmación, la sesión pasa a **Activa**:
   - Si el temporizador no está `RUNNING`, la app envía `ScaleCommands.startTimer()`.
   - La gráfica empieza a dibujarse desde t = 0, el primer instante sobre el umbral.
3. Durante la sesión activa, la gráfica avanza en vivo, la curva de flujo se colorea por nivel de
   estabilidad y un indicador muestra la puntuación y el nivel actuales. Las pausas entre vertidos
   (bloom) no terminan la sesión.
4. La sesión pasa a **Terminada** con el primer evento de fin (§3.4). La gráfica se congela y bajo
   ella aparece la tarjeta de resumen.
5. Una tara (`ScaleMessage.Tared`) o un reinicio del temporizador (`Timer(RESET)`) en estado
   Terminada **rearma** la sesión. La gráfica y el resumen anteriores siguen visibles hasta que se
   detecta el siguiente vertido; entonces se borran y empieza una sesión nueva.

## 3. Especificación Técnica

### 3.1 Ubicación del código

| Elemento | Ubicación | Tipo |
|---|---|---|
| Lógica de sesión, métricas, ejes y reducción de muestreo | `app/src/main/kotlin/dev/dotscale/brew/` | Kotlin puro, sin imports de `android.*` |
| Preferencia de fin automático | `app/src/main/kotlin/dev/dotscale/settings/BrewSettings.kt` | `SharedPreferences`, mismo patrón que `ThemeSettings` |
| Pantalla de ajustes | `app/src/main/kotlin/dev/dotscale/ui/settings/SettingsScreen.kt` | Compose |
| Gráfica y tarjeta de resumen | `app/src/main/kotlin/dev/dotscale/ui/scale/` (`BrewChartCard`, `BrewChart`, `BrewSummaryCard`) | Compose, Canvas propio |
| Integración | `ScaleViewModel`, `ScaleUiState`, `ScaleScreen` | Existentes |
| Simulador | `SimulatedScaleConnection` | Existente |

No se añaden dependencias nuevas: la gráfica se dibuja con `Canvas` de Compose y la preferencia se
guarda en `SharedPreferences`.

### 3.2 Modelo de datos

Todas las magnitudes se guardan **en gramos y g/s** dentro de la sesión, sea cual sea la unidad de
la báscula. La conversión de onzas usa 1 oz = 28,349523125 g. La UI convierte a la unidad actual
(`ScaleUiState.unit`) solo al mostrar.

```kotlin
package dev.dotscale.brew

/** Una muestra de la sesión. `tMs` es relativo a t0 (primer instante sobre el umbral). */
data class BrewSample(
  val tMs: Long,
  val waterG: Double,        // agua vertida = peso actual − línea base
  val flowGps: Double,       // flujo enviado por la báscula, en g/s
  val stability: Int?,       // puntuación 0-100; null si no hay vertido o ventana insuficiente
  val valid: Boolean,        // false si outOfRange o peso negativo: se dibuja como hueco
)

enum class StabilityLevel { STABLE, IRREGULAR, TURBULENT }

data class Pour(val index: Int, val startMs: Long, val endMs: Long, val waterG: Double)

enum class FinishReason { INACTIVITY, TIMER_PAUSED, TIMER_RESET, MANUAL, DISCONNECTED, MAX_DURATION }

data class BrewSummary(
  val totalTimeMs: Long,
  val totalWaterG: Double,
  val avgFlowGps: Double,            // promedio ponderado por tiempo, solo durante vertidos
  val maxFlowGps: Double,
  val stabilityScore: Int?,          // promedio ponderado por tiempo de las puntuaciones no nulas
  val stabilityLevel: StabilityLevel?,
  val levelShare: Map<StabilityLevel, Double>, // fracción 0..1 del tiempo puntuado (stability != null) en cada nivel
  val pours: List<Pour>,
  val finalRatio: Double?,           // totalWaterG / dosis en gramos; null sin dosis
  val reason: FinishReason,
  val interrupted: Boolean,          // true si reason == DISCONNECTED
)

sealed interface BrewPhase {
  data object Armed : BrewPhase
  data class Active(val pouring: Boolean) : BrewPhase
  /** `pourIgnored`: se detectó vertido estando terminada y sin rearmar (§3.4, Vertido tras el fin). */
  data class Finished(val summary: BrewSummary, val rearmed: Boolean, val pourIgnored: Boolean) : BrewPhase
}

data class BrewState(
  val phase: BrewPhase,
  val samples: List<BrewSample>,      // vacía en Armed sin sesión previa
  val currentStability: Int?,
  val currentLevel: StabilityLevel?,
)
```

### 3.3 Contrato de la sesión

`BrewSession` es una máquina de estados pura y determinista. El tiempo se inyecta en cada llamada
(`nowMs`, reloj monotónico) para que sea testeable sin reloj real.

```kotlin
class BrewSession(private val config: BrewConfig = BrewConfig()) {
  val state: BrewState

  /** Devuelve los efectos que el ViewModel debe ejecutar. */
  fun onWeight(message: ScaleMessage.Weight, nowMs: Long, timer: TimerStatus): List<BrewEffect>
  fun onTimer(status: TimerStatus, nowMs: Long)
  fun onTared(nowMs: Long)
  fun onDisconnected(nowMs: Long)
  fun onTick(nowMs: Long, autoFinishMs: Long)   // evalúa inactividad y duración máxima
  fun finish(nowMs: Long)                       // botón "Terminar"
  fun setDoseGrams(dose: Double?)               // para el ratio final
}

sealed interface BrewEffect {
  data object StartTimer : BrewEffect   // el ViewModel envía ScaleCommands.startTimer()
}
```

Integración en `ScaleViewModel`:

- Reenvía a `BrewSession` los mensajes `ScaleMessage.Weight`, `Timer` y `Tared`, y los cambios de
  `ConnectionState` (de `Ready` a `Disconnected` o `Failed` → `onDisconnected`).
- Ejecuta un tick de 1 s con `viewModelScope` que llama a `onTick` con el valor vigente de
  `BrewSettings.autoFinishSeconds`. Así la inactividad se evalúa aunque dejen de llegar
  notificaciones.
- Expone el `BrewState` dentro de `ScaleUiState`, en un campo nuevo `brew: BrewState`.
- Añade `fun finishBrew()`, que llama a `BrewSession.finish`.

### 3.4 Reglas de la sesión

Constantes en `BrewConfig`. Son valores iniciales que se calibrarán con hardware (§6):

| Constante | Valor | Uso |
|---|---|---|
| `POUR_THRESHOLD_GPS` | 0,5 g/s | Umbral de vertido (el mismo que usa la app oficial) |
| `POUR_CONFIRM_MS` | 500 ms | Tiempo continuo sobre el umbral para confirmar el inicio |
| `POUR_GAP_MS` | 2000 ms | Tiempo bajo el umbral para cerrar un vertido |
| `STABILITY_WINDOW_MS` | 3000 ms | Ventana móvil de la métrica |
| `STABILITY_RAMP_MS` | 1000 ms | Rampa inicial de cada vertido excluida de la métrica |
| `STABILITY_MIN_WINDOW_MS` | 1000 ms | Datos mínimos en la ventana para dar puntuación |
| `CV_ZERO_SCORE` | 0,5 | CV con el que la puntuación llega a 0 |
| `LEVEL_STABLE_MIN` | 75 | Puntuación mínima del nivel Estable |
| `LEVEL_IRREGULAR_MIN` | 50 | Puntuación mínima del nivel Irregular |
| `MAX_DURATION_MS` | 900 000 ms (15 min) | Duración máxima de una sesión |

**Inicio.**
- En `Armed` (o `Finished` rearmada), la sesión empieza cuando hay muestras válidas con flujo
  mayor o igual a `POUR_THRESHOLD_GPS` durante `POUR_CONFIRM_MS` seguidos.
- t0 es el instante de la primera de esas muestras, y las muestras de confirmación se incluyen en
  la gráfica.
- La línea base es el peso de la última muestra válida anterior a t0, o el de la primera muestra si
  no hay ninguna anterior.
- Si `timer != RUNNING`, se emite `BrewEffect.StartTimer`, una sola vez por sesión.

**Agua vertida.**
- `waterG` = peso actual − línea base.
- Una tara durante la sesión no reinicia la curva: se ajusta la línea base para que el agua
  acumulada sea continua.

**Vertidos.**
- Un vertido abre cuando el flujo cruza el umbral y cierra tras `POUR_GAP_MS` por debajo de él.
- `Pour.waterG` es la diferencia de `waterG` entre su inicio y su fin.
- `Active(pouring)` refleja si hay un vertido abierto.

**Estabilidad.** Para cada muestra dentro de un vertido:
1. Si la muestra cae en los primeros `STABILITY_RAMP_MS` de su vertido (rampa de subida),
   `stability = null` y el indicador muestra "Midiendo…".
2. Se toman las muestras válidas de los últimos `STABILITY_WINDOW_MS` que pertenecen **solo al
   vertido actual** y quedan fuera de su rampa. La ventana nunca mezcla vertidos.
3. Si cubren menos de `STABILITY_MIN_WINDOW_MS`, `stability = null`. Por tanto, un vertido de
   menos de `STABILITY_RAMP_MS` + `STABILITY_MIN_WINDOW_MS` no tiene puntuación.
4. Si no, `CV = σ / μ` (desviación estándar poblacional / media) y
   `score = round(100 × (1 − min(CV / CV_ZERO_SCORE, 1)))`.
5. Nivel: `STABLE` si `score ≥ 75`, `IRREGULAR` si `50 ≤ score < 75` y `TURBULENT` si
   `score < 50`.
6. Fuera de vertidos, `stability = null` y el tramo se dibuja en color neutro.

**Fin.** La sesión activa termina con el primer evento que ocurra:

| Evento | `FinishReason` | Fin de `totalTimeMs` |
|---|---|---|
| Pasan `autoFinishSeconds` (preferencia; 60 s por defecto) desde la última muestra con flujo mayor o igual a `POUR_THRESHOLD_GPS` | `INACTIVITY` | Final del último vertido |
| `Timer(PAUSED)` | `TIMER_PAUSED` | Instante del evento |
| `Timer(RESET)` | `TIMER_RESET` | Instante del evento |
| Botón "Terminar" | `MANUAL` | Instante del evento |
| Conexión pasa a `Disconnected` o `Failed` | `DISCONNECTED` (`interrupted = true`) | Última muestra recibida |
| `tMs ≥ MAX_DURATION_MS` | `MAX_DURATION` | `MAX_DURATION_MS` |

**Rearme.**
- En `Finished`, un `Tared(success = true)` o un `Timer(RESET)` pone `rearmed = true`.
- El siguiente inicio confirmado borra las muestras y el resumen anteriores.
- Un `Timer(RESET)` que termina una sesión activa no la rearma en el mismo evento; hace falta otra
  tara u otro reinicio. Así el resumen recién generado no queda armado para borrarse.

**Vertido tras el fin.**
- Si en `Finished` sin rearmar se confirma un vertido (misma regla que el inicio), no se crea
  sesión ni se modifican muestras ni resumen.
- Se pone `pourIgnored = true`, que dura hasta el siguiente rearme.

**Unidades.**
- Las muestras en onzas se convierten a gramos al entrar.
- Un `UnitChanged` durante la sesión no la interrumpe; la UI reescala.
- La dosis (`ScaleUiState.dose`, en la unidad actual) se pasa en gramos a `setDoseGrams`.

**Muestras inválidas.**
- Las muestras con `outOfRange = true` o peso negativo después de t0 se guardan con
  `valid = false`.
- No cuentan para las métricas ni para la ventana de estabilidad, y se dibujan como hueco.

### 3.5 Ejes y dibujo

**Eje X (tiempo).**
- Rango inicial de 0 a 60 s.
- Cuando `tMs` supera el 85 % del tope, el tope crece de 30 s en 30 s.
- Las etiquetas van en `mm:ss`.

**Eje Y de agua.**
- Escalones en gramos: 60, 120, 200, 300, 400, 600 y 800, y a partir de ahí de 200 en 200 (1000,
  1200…).
- En onzas: 2, 4, 8, 12, 16, 24 y 32, y a partir de ahí de 8 en 8.
- Se sube al siguiente escalón cuando el valor máximo pasa del 85 % del tope actual.

**Eje Y de flujo.**
- Escalones en g/s: 8, 12, 16 y 20, y a partir de ahí de 4 en 4.
- En oz/s: 0,25, 0,5, 0,75 y 1, y a partir de ahí de 0,25 en 0,25.
- Misma regla del 85 %.

**Reglas comunes de los ejes.**
- Los escalones solo crecen durante una sesión; se reinician con la sesión nueva.
- La lógica de escalones vive en `dev.dotscale.brew.ChartAxis` (Kotlin puro).

**Dibujo** (`BrewChart`, con `Canvas`):
- **Agua:** línea con relleno suave, color `primary` del tema Ant.
- **Flujo:** línea por tramos, cada uno con el color de su nivel: `success` (Estable), `warning`
  (Irregular) o `error` (Turbulento). Los tramos sin puntuación usan el color neutro de texto
  secundario.
- **Reducción de muestreo:** solo para dibujar, se envían como máximo 2 puntos por píxel horizontal
  (mínimo y máximo de cada columna). Las métricas siempre usan todas las muestras.
- **Accesibilidad:** el `Canvas` lleva `contentDescription` con el agua, el flujo y el nivel
  actuales. Los colores de las series cumplen un contraste de 3:1 contra el fondo de la tarjeta en
  claro y en oscuro (WCAG 1.4.11), verificado como en `AntColorsContrastTest`.

### 3.6 Interfaz

**Pantalla Scale** (`ScaleScreen`). Se añade `BrewChartCard` justo después de `BrewControls`:
- **En `Armed` sin sesión previa:** mensaje "Vierte agua para empezar".
- **En `Active`:**
  - La gráfica.
  - Un `AntTag` con el nivel en texto y la puntuación (por ejemplo, "Estable · 82"). Muestra
    "Midiendo…" durante la rampa o con la ventana insuficiente de un vertido, y "Sin vertido"
    entre vertidos.
  - Un botón "Terminar".
- **En `Finished`:**
  - La gráfica congelada.
  - Debajo, `BrewSummaryCard` con:
    - Tiempo total.
    - Agua total.
    - Flujo medio y máximo.
    - Estabilidad: puntuación, nivel y porcentaje de tiempo en cada nivel.
    - La lista de vertidos (número, gramos y duración).
    - El ratio final (`1:x`), solo si hay dosis.
    - Si `interrupted`, un `AntAlert` de aviso "Preparación interrumpida por desconexión".
  - Si `rearmed`, un texto indica "Lista para la siguiente preparación".
  - Si `pourIgnored`, un `AntAlert` informativo indica "Haz tara o reinicia el temporizador para
    empezar una nueva preparación". El resumen se conserva.

**Pantalla de ajustes:**
- Nueva `NavKey`: `@Serializable data object Settings : NavKey` en `NavigationKeys.kt`, con su
  `entry<Settings>` en `Navigation.kt`.
- Se abre con un icono de engranaje en la `AntTopBar` de `ScanScreen` y de `ScaleScreen`.
- Contenido en este spec: una sola preferencia, "Fin automático", con `AntSegmented` de 30, 45,
  60, 90 y 120 s y un texto de ayuda sobre la pausa del bloom.
- `ThemeModeButton` se queda donde está.

**`BrewSettings`:**
- `SharedPreferences` con nombre `"settings"` (el mismo archivo que `ThemeSettings`) y clave
  `"auto_finish_seconds"`.
- Expone `autoFinishSeconds: StateFlow<Int>` y `setAutoFinishSeconds(Int)`.
- Un valor guardado fuera del conjunto permitido se trata como 60.

**Textos:**
- Todos los textos van en `app/src/main/res/values/strings.xml` (español, idioma por defecto) y en
  `app/src/main/res/values-en/strings.xml` (inglés, archivo nuevo).
- Se traducen también al inglés los textos que ya existían, para que lint no necesite excepciones
  de `MissingTranslation`.

### 3.7 Simulador

`SimulatedScaleConnection` deja de vincular el vertido al temporizador:

- Nuevo `suspend fun simulatePour(profile: PourProfile)` con `enum class PourProfile { STABLE,
  IRREGULAR }`. Genera un bloom, una pausa y un segundo vertido con independencia del estado del
  temporizador.
  - `STABLE`: flujo suave, como el actual.
  - `IRREGULAR`: añade al flujo una variación aleatoria con semilla fija, para que la puntuación
    quede por debajo de 75.
- El perfil guionizado que hoy se activa con el temporizador se elimina. El temporizador simulado
  sigue respondiendo a `startTimer`, `pauseTimer` y `resetTimer`.
- `SimulationCard` añade dos botones, "Simular vertido estable" y "Simular vertido irregular",
  junto a los de añadir peso que ya existen.

### 3.8 Flujo de ejecución (por notificación de peso)

```
BLE/Sim ──ScaleMessage.Weight──▶ ScaleViewModel
                                   ├─ ScaleUiState.reduce(message)            (existente)
                                   ├─ BrewSession.onWeight(msg, now, timer)
                                   │     ├─ convierte a g, valida y añade la muestra
                                   │     ├─ detecta inicio, vertidos y fin
                                   │     ├─ calcula la estabilidad de la ventana
                                   │     └─ devuelve efectos (StartTimer)
                                   ├─ ejecuta los efectos → connection.send(startTimer)
                                   └─ _ui.update { copy(brew = session.state) }
ScaleScreen ◀── StateFlow<ScaleUiState> ── BrewChartCard / BrewSummaryCard
```

## 4. Casos Límite y Manejo de Errores

| Escenario | Comportamiento esperado |
|---|---|
| Picos al apoyar el gotero o la tetera (flujo alto menos de 500 ms) | No inicia la sesión (`POUR_CONFIRM_MS`) |
| Pausa del bloom de 30-45 s | La sesión sigue activa; el vertido se cierra y el siguiente se cuenta aparte |
| El usuario no hizo tara antes de verter | El agua parte de 0 gracias a la línea base |
| Tara durante la sesión | La curva de agua continúa sin saltos |
| Se retira la taza tras terminar (peso negativo) | La sesión está terminada y el resumen se conserva |
| Se vuelve a verter tras terminar, sin tara ni reinicio | Se ignora; aviso `pourIgnored` en la tarjeta y el resumen se conserva |
| Subida del flujo al empezar cada vertido | Primer segundo excluido de la métrica; el indicador muestra "Midiendo…" |
| Cola de un vertido dentro de la ventana del siguiente | No ocurre: la ventana solo usa el vertido actual |
| Se retira la taza durante la sesión o hay sobrepeso | Muestras inválidas: hueco en la gráfica, excluidas de las métricas |
| Desconexión BLE a mitad | Fin con `DISCONNECTED`; gráfica congelada; resumen hasta la última muestra con aviso de interrupción |
| Dejan de llegar notificaciones sin desconexión | El tick de 1 s aplica el fin por inactividad igualmente |
| Fallo al enviar `startTimer` | La sesión sigue con su reloj local; el error se muestra por el canal `errors` existente |
| La báscula ya inicia sola el temporizador | Si `timer == RUNNING` no se envía `startTimer` |
| Cambio de unidad a mitad | Sin pérdida de datos; los ejes se reescalan con la tabla de la nueva unidad |
| Sin dosis fijada | `finalRatio = null`; el resumen no muestra ratio |
| Sesión de 15 min | Fin automático `MAX_DURATION` |
| Rotación de pantalla | La sesión sobrevive (vive en `ScaleViewModel`) |
| Salir de Scale o muerte del proceso | La sesión se pierde. Es aceptable: SPEC-001 no persiste datos |
| Volumen de datos | Unas 9000 muestras como máximo (15 min a 10 Hz); el dibujo usa reducción de muestreo |
| Permisos y datos sensibles | No se añaden permisos ni se guardan datos personales |

## 5. Criterios de Aceptación (Definition of Done)

**Funcionales** (verificables con la báscula simulada):
- [x] Con la sesión armada, un vertido simulado inicia la gráfica en un máximo de
      `POUR_CONFIRM_MS` + 1 muestra tras cruzar 0,5 g/s, sin pulsar nada.
- [x] Al iniciar, si el temporizador no estaba en marcha, se envía `startTimer` exactamente una vez
      y el temporizador de la UI empieza a contar.
- [x] La gráfica muestra agua (g) y flujo (g/s) con ejes escalonados según §3.5, y la curva de flujo
      coloreada por nivel.
- [x] El indicador en vivo muestra nivel y puntuación. Con "Simular vertido estable", al menos el
      70 % del tiempo puntuado queda en "Estable" (`levelShare[STABLE] ≥ 0,7`). Con "Simular
      vertido irregular", al menos el 70 % queda en "Irregular" o "Turbulento"
      (`levelShare[IRREGULAR] + levelShare[TURBULENT] ≥ 0,7`).
- [x] Verter de nuevo tras terminar, sin tara ni reinicio, muestra el aviso y no altera la gráfica
      ni el resumen.
- [x] La pausa del bloom del vertido simulado no termina la sesión con el ajuste de 60 s.
- [x] La sesión termina con cada uno de los seis eventos de §3.4 y el resumen muestra todas las
      métricas de §3.6.
- [x] Tras terminar, una tara o un reinicio rearman la sesión sin borrar el resumen, y el siguiente
      vertido empieza una gráfica nueva.
- [x] La pantalla de ajustes es accesible desde Scan y Scale, guarda el valor y el cambio se aplica
      a la sesión en curso.
- [x] Todos los textos nuevos existen en `values/` y `values-en/`.

**Pruebas:**
- [x] Unitarias de `BrewSession` en `app/src/test/kotlin/dev/dotscale/brew/`, que cubren:
  - Confirmación de inicio y rechazo de picos.
  - Línea base y tara durante la sesión.
  - Segmentación de vertidos con `POUR_GAP_MS`.
  - Estabilidad con secuencias conocidas: flujo constante → 100; CV de 0,25 → 50; CV de 0,5 o
    mayor → 0; ventana insuficiente → `null`.
  - Rampa de 1000 ms excluida, y que la ventana no mezcla vertidos (un vertido estable seguido de
    otro estable puntúa 100 en ambos aunque estén separados solo por `POUR_GAP_MS`).
  - Vertido tras el fin sin rearmar → `pourIgnored = true`, sin cambios en muestras ni resumen.
  - `levelShare` calculado solo sobre el tiempo puntuado.
  - Los seis motivos de fin.
  - Rearme (incluido que `RESET` no rearma en el mismo evento).
  - Conversión de onzas y muestras inválidas.
  - Cálculo del resumen.
  - Emisión única de `StartTimer`.
- [x] Unitarias de `ChartAxis` y de la reducción de muestreo:
  - Escalones de los tres ejes en g y oz, incluida la regla del 85 % y que no decrecen.
  - La salida de la reducción tiene como máximo 2 × ancho puntos.
- [x] Unitarias de contraste de los colores de las series, en claro y en oscuro.
- [x] UI instrumentada (Compose, `app/src/androidTest/kotlin/dev/dotscale/ui/`) con la báscula
      simulada:
  - La gráfica aparece al simular un vertido.
  - "Terminar" muestra la tarjeta de resumen.
  - Los ajustes cambian el valor guardado.
- [x] Recorrido E2E verificado en emulador con la báscula simulada, sin ARTEMIS (decisión del 2026-11-12:
      no funciona, ver el historial de la sesión):
  - Revisión manual por adb: vertido estable e irregular, "Terminar" y tarjeta de resumen.
  - Las interacciones se plasman en los tests instrumentados.
  - Fin por inactividad con el ajuste de 30 s, en el emulador y sin tocar nada durante el vertido:
    2 vertidos (45,0 g y 205,1 g), 250,1 g, 01:25, "Terminó por inactividad" y Estable 99 %.
    La pausa del bloom (20 s) no terminó la sesión.
  - Aviso `pourIgnored` en pantalla: `BrewChartTest` (el guion del simulador sigue vertiendo tras
    "Terminar"). El cambio de ajuste en curso lo cubre `ScaleViewModelBrewTest`.
  - Observación: si el hilo principal se congela más de 2 s (por ejemplo, con `uiautomator dump`), el
    vertido en curso se parte en varios, porque el tiempo de cada muestra es el de su llegada a la
    app. No ocurre en uso normal; a tener en cuenta con la DOT física (§6.2).
- [x] `./gradlew :app:testDebugUnitTest` y los tests instrumentados pasan; el módulo compila sin
      avisos nuevos de lint.

## 6. Preguntas abiertas

No bloquean el cierre de SPEC-001. Se resuelven cuando llegue la DOT física, siguiendo
`docs/PROTOCOL.md` §14, y capturando con `adb logcat -s BleScale`.

1. **Suavizado del flujo de la DOT.** Si el firmware suaviza mucho el flujo, la estabilidad saldría
   casi siempre alta. Habría que decidir si calcularla con un flujo derivado del peso
   (Δpeso / Δt).
2. **Frecuencia real de las notificaciones de peso.** Se asume unos 10 Hz, como el simulador
   (`TICK_MS = 100`). Afecta a la ventana de estabilidad y al volumen de datos.
3. **Inicio automático del temporizador en la DOT.** Si la báscula lo inicia sola al detectar
   vertido, hay que confirmar que no se produce un doble inicio ni una carrera con `startTimer`.
4. **Calibración de constantes** con vertidos reales: `POUR_THRESHOLD_GPS`, `POUR_CONFIRM_MS`,
   `POUR_GAP_MS`, `CV_ZERO_SCORE` y los umbrales 75/50.
5. **Tiempo de drawdown.** Con el fin por inactividad, `totalTimeMs` acaba en el último vertido,
   porque la báscula no ve el drawdown (el peso total no cambia). Queda por decidir si interesa
   medirlo de otra forma.

## 7. Alternativas descartadas

- **Librería Vico para la gráfica:** se prefirió un `Canvas` propio. No añade dependencias y da
  control total del doble eje, el escalado por saltos, el tema Ant claro/oscuro y la accesibilidad.
- **Banda ±σ o solo una puntuación numérica para la estabilidad:** se eligió colorear la curva y
  añadir una puntuación, porque se lee más rápido mientras se vierte.
- **Ventana de estabilidad que abarca varios vertidos o incluye la rampa de subida:** se descartó
  porque marcaba como irregular el arranque de cada vertido aunque el pulso fuera constante.
- **Reabrir la sesión al volver a verter tras el fin:** se prefirió conservar el resumen y avisar de
  que hay que hacer tara o reiniciar el temporizador.
- **Esperar a la reconexión tras una desconexión:** no hay reconexión automática; la sesión termina
  como interrumpida.
- **Iniciar una sesión nueva con cualquier vertido tras terminar:** se descartó porque retirar la
  taza o un goteo borrarían el resumen.
- **Un único spec con historial, curva objetivo y compartir:** se dividió en SPEC-001 a SPEC-004.

## 8. Plan de implementación por fases

1. **Lógica pura:** `BrewConfig`, `BrewSession`, `ChartAxis` y la reducción de muestreo, con sus
   tests unitarios.
2. **Simulador:** `simulatePour(PourProfile)` y los botones en `SimulationCard`.
3. **Integración en ViewModel:** campo `brew` en `ScaleUiState`, tick de 1 s, `finishBrew()` y
   efecto `StartTimer`.
4. **UI:** `BrewChart`, `BrewChartCard` y `BrewSummaryCard`, con textos en es/en y tests de
   contraste.
5. **Ajustes:** `BrewSettings`, `Settings` NavKey, `SettingsScreen` y el acceso desde las top bars.
6. **Verificación:** exploración manual por adb, tests instrumentados de Compose y checklist de §5.

## Anexo: decisiones para los specs siguientes

Se registran aquí para no tener que volver a entrevistar:

- **SPEC-002 Historial:** guardar cada preparación con:
  - La curva completa.
  - Los datos automáticos: fecha, duración, agua, dosis, ratio, flujos, estabilidad y modelo de
    báscula.
  - Un nombre editable, que por defecto es la fecha y la hora.
  - Campos estructurados: café u origen, molienda, temperatura del agua y método.
  - Notas de cata en texto libre.
  - Requiere añadir persistencia (Room).
- **SPEC-003 Curva objetivo:** la referencia puede salir del historial o de recetas editables
  (vertidos por etapas en gramos y tiempo).
- **SPEC-004 Compartir:** exportar la gráfica como imagen.
