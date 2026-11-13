# SPEC-003: Curva objetivo, recetas y comparación de preparaciones

| Campo | Valor |
|---|---|
| Estado | Implementado (F0–F7) en `feat/target-curve`; pendiente de validación con la DOT física. Plan: [`plan-curva-objetivo.md`](plan-curva-objetivo.md) |
| Fecha | 2026-11-12 |
| Deriva de | [SPEC-001 Gráfica de vertido](spec-grafica-vertido.md) (Anexo) y [SPEC-002 Historial](spec-historial.md) (alcance Out) |
| Módulos afectados | `:app` (nuevo paquete `dev.dotscale.recipe`, nuevo `ui/recipes`, `brew`, `history`, `settings`, `sim`, `ui/scale`, `ui/history`, `ui/settings`, `ui/components`, navegación) |
| Specs relacionados | SPEC-004 Compartir imagen (pendiente) |

## 1. Resumen y Contexto

- **Problema:** con SPEC-001 y SPEC-002 la app mide y guarda cada preparación, pero no ayuda a
  repetirla.
  - No hay forma de seguir una receta mientras se vierte.
  - No se puede usar una buena preparación anterior como guía.
  - No se pueden poner dos preparaciones guardadas lado a lado para ver qué cambió.
  - El ajuste de una receta (dial-in) se queda a ojo.
- **Solución:**
  - **Curva objetivo:** se superpone a la gráfica en vivo de Scale. Sale de una **receta** por
    etapas o de una **preparación guardada**.
  - **Guía por etapas:** muestra la etapa actual, una cuenta atrás, el desvío en gramos y avisos
    con vibración y sonido.
  - **Métrica de ajuste:** al terminar se calcula cuánto se ajustó la preparación al objetivo y se
    guarda en el historial, junto con una copia de la curva objetivo.
  - **Recetas:** pantalla nueva para gestionarlas, con dos recetas predefinidas.
  - **Comparar:** vista nueva que superpone dos preparaciones guardadas.
- **Usos previstos:**
  - Repetir una receta conocida.
  - Volver a clavar una preparación que salió bien.
  - Ajustar una receta comparando dos intentos.
- **Éxito:**
  - Una preparación que sigue la curva objetivo termina con el agua total dentro de **±3 %** y el
    tiempo dentro de **±5 s** del objetivo (verificable con el perfil fiel del simulador, §3.10).
  - Elegir una referencia y empezar a verter con ella cuesta **3 toques o menos** desde Scale.
  - Superponer dos preparaciones guardadas cuesta **3 toques o menos** desde el historial.
  - Cada preparación hecha con referencia guarda una **puntuación de ajuste** (0-100) y las
    diferencias finales de agua y de tiempo.
- **Alcance (In):**
  - **Recetas:**
    - Dosis nominal y de 1 a 10 etapas (inicio, agua acumulada y duración del vertido).
    - Nombre, método, molienda, temperatura y notas.
    - El ratio se deriva de la dosis y del agua de la última etapa.
  - **Pantalla Recetas:**
    - Crear, editar, duplicar y borrar con "Deshacer".
    - Acceso con un icono en la top bar de Scan y con "Gestionar recetas" en el selector de
      objetivo de Scale.
    - Dos recetas predefinidas editables, con "Restaurar recetas predefinidas" en Ajustes.
  - **Guardar como receta:** desde el detalle del historial, con el editor ya relleno a partir de los
    vertidos.
  - **Curva objetivo en Scale:**
    - Se elige con un selector (pestañas Recetas e Historial) y se recuerda entre sesiones.
    - El agua se escala con la dosis capturada.
  - **Guía en vivo:**
    - Etapa actual y siguiente, con cuenta atrás de 5 s.
    - Desvío en gramos con tres niveles.
    - Vibración y sonido al empezar cada etapa, configurables en Ajustes.
  - **Al guardar una preparación con referencia:**
    - Se guardan la referencia, una copia de la curva objetivo escalada y la métrica de ajuste.
    - Se prellenan sus datos manuales desde la receta o la preparación de referencia.
  - **Historial:** la fila muestra la puntuación de ajuste, y el detalle dibuja la curva objetivo
    guardada y muestra el ajuste.
  - **Comparar:**
    - "Comparar con…" desde el detalle, con un selector filtrado por el café de A.
    - Dos gráficas (agua y flujo de A y B) y una tabla de diferencias.
  - **Base de datos:** migración de `HistoryDatabase` de la versión 1 a la 2, conservando el
    historial.
  - **Simulador:** un vertido que sigue el objetivo, con un perfil fiel y otro desviado.
  - **Textos:** en español e inglés.
- **Alcance (Out):**
  - Guía adaptativa, en la que la etapa siguiente espera a que se alcance el agua de la actual.
  - Compartir la gráfica o la comparación como imagen (SPEC-004).
  - Comparar más de dos preparaciones, o una preparación con su curva objetivo en la vista de
    comparar. El detalle sí dibuja el objetivo.
  - Filtrar u ordenar el historial por receta o por ajuste.
  - Importar, exportar o compartir recetas.
  - Modo espresso y etapas de la báscula (`ScaleMessage.BrewModeStage`).
  - Validación con la báscula DOT física (heredado de SPEC-001 §6).

## 2. Historias de Usuario y Casos de Uso

- **US-1:** Como aficionado, quiero elegir una receta antes de verter y ver su curva sobre la mía,
  para saber en cada momento cuánta agua debería llevar.
- **US-2:** Como aficionado con la tetera en la mano, quiero que la app me diga en qué etapa estoy,
  cuánto falta para la siguiente y si voy por encima o por debajo, y que me avise al empezar cada
  etapa con vibración o sonido, para no tener que leer la pantalla todo el rato.
- **US-3:** Como aficionado, quiero que la receta se adapte a la dosis que he pesado, para no
  recalcular los gramos de cada vertido.
- **US-4:** Como aficionado, quiero usar una preparación que me salió bien como objetivo, o
  convertirla en una receta editable, para repetirla.
- **US-5:** Como aficionado, quiero crear, editar, duplicar y borrar mis recetas, y empezar con
  algunas ya hechas.
- **US-6:** Como aficionado que ajusta una receta, quiero ver al terminar cuánto me ajusté al
  objetivo, y verlo después en el historial.
- **US-7:** Como aficionado, quiero superponer dos preparaciones guardadas y ver sus diferencias,
  para entender por qué una salió mejor.
- **US-8:** Como desarrollador sin la báscula física, quiero simular un vertido que siga el
  objetivo, de forma fiel o desviada, para probar la guía y la métrica.

### Flujo principal (preparar con una receta)

1. En Scale, con la sesión en **Armada**, la tarjeta de la gráfica muestra el chip
   "Objetivo: ninguno".
2. El usuario pulsa el chip (toque 1) y se abre el selector en la pestaña Recetas. Elige "V60
   clásica" (toque 2) y el selector se cierra.
3. La gráfica dibuja la curva objetivo en gris discontinuo, y debajo aparece la primera etapa:
   "Etapa 1/3 · Bloom: hasta 40 g en 10 s".
4. El usuario pone el café y pulsa "Capturar dosis". Con 16,4 g en una receta de 15 g, la curva se
   escala ×1,093 y se redibuja.
5. El usuario empieza a verter. La sesión pasa a **Activa** (SPEC-001) y desde ese momento la escala
   queda fija.
6. Durante la sesión, la tarjeta muestra:
   - La curva real sobre la objetivo.
   - La etapa en curso y la siguiente.
   - Una cuenta atrás en los 5 s previos a cada etapa.
   - Un `AntTag` con el desvío ("+6 g").
   Al empezar cada etapa, el móvil vibra o pita según los ajustes.
7. La sesión termina (SPEC-001 §3.4). La tarjeta de resumen añade el bloque "Ajuste al objetivo",
   con la puntuación y las diferencias de agua y de tiempo.
8. La preparación se guarda (SPEC-002) con la referencia, la copia de la curva y el ajuste. Su
   método, molienda y temperatura salen de la receta. En el historial, su fila lleva el `AntTag`
   "Ajuste 86".

### Flujo: usar una preparación guardada

- **Como objetivo en vivo:** chip (1), pestaña Historial (2) y fila (3). La curva dibujada es la
  real guardada. Las etapas salen de sus vertidos (§3.3).
- **Como receta:** en el detalle, "Guardar como receta" abre el editor ya relleno (§3.8). El
  usuario revisa los datos y pulsa "Guardar".

### Flujo: comparar dos preparaciones

1. Desde el historial, el usuario abre la preparación A (toque 1).
2. En el detalle pulsa "Comparar con…" (toque 2). Se abre un selector con el historial sin A,
   filtrado por el café de A.
3. Elige B (toque 3) y se abre la vista de comparación: gráfica de agua, gráfica de flujo y tabla.
   Atrás vuelve al detalle de A.

## 3. Especificación Técnica

### 3.1 Ubicación del código

| Elemento | Ubicación | Tipo |
|---|---|---|
| Curva objetivo, escalado, guía, avisos de etapa y métrica de ajuste | `app/src/main/kotlin/dev/dotscale/brew/` (`TargetCurve`, `TargetGuide`, `StageAlertTracker`, `BrewFit`, `TargetConfig`) | Kotlin puro, sin `android.*` |
| Receta, validación y conversión desde una preparación | `app/src/main/kotlin/dev/dotscale/recipe/` (`Recipe`, `RecipeValidator`, `RecipeFromBrew`, `BuiltInRecipes`) | Kotlin puro |
| Entidades, DAO y repositorio de recetas | `app/src/main/kotlin/dev/dotscale/recipe/` (`RecipeEntity`, `RecipeStageEntity`, `RecipeDao`, `RecipeRepository`) | Room, en `HistoryDatabase` |
| Copia de la curva objetivo, columnas nuevas y migración 1→2 | `app/src/main/kotlin/dev/dotscale/history/` (`BrewTargetEntity`, `TargetCurveCodec`, `Migrations`) | Room y Kotlin puro |
| Referencia activa y preferencias de avisos | `dev.dotscale.settings` (`TargetSettings`) | `SharedPreferences "settings"` |
| Vibración y sonido | `dev.dotscale.brew` define la interfaz `StageNotifier`; la implementación Android va en `dev.dotscale.settings` o `ui/scale` (se decide en el plan) | Android |
| Pantallas de recetas y editor | `app/src/main/kotlin/dev/dotscale/ui/recipes/` | Compose |
| Selector de objetivo y guía en Scale | `ui/scale/` (`TargetPicker`, `BrewChartCard`, `BrewSummaryCard`) | Compose |
| Selector de B y vista de comparación | `ui/history/` (`ComparePickScreen`, `BrewCompareScreen`) | Compose |
| Curva objetivo en la gráfica | `ui/scale/BrewChart.kt` (parámetro `target`) | Existente, se amplía |
| Ajustes | `ui/settings/SettingsScreen.kt` | Existente, se amplía |
| Simulador | `sim/` (`SimulatedTargetPour`, `SimulatedScaleConnection.simulatePour`) | Existente, se amplía |
| Singletons (`RecipeRepository`, `TargetSettings`) | `DotScaleApp` | Existente |

**Dependencias nuevas:** ninguna. **Permiso nuevo:** `android.permission.VIBRATE` (permiso normal, sin
diálogo).

### 3.2 Recetas: modelo, reglas y predefinidas

Todas las magnitudes se guardan en gramos, milisegundos y °C.

```kotlin
package dev.dotscale.recipe

data class Recipe(
  val id: Long = 0,
  val name: String,
  val method: BrewMethod? = null,
  val methodOther: String? = null,       // solo con method == OTHER
  val grind: String? = null,
  val waterTempC: Double? = null,
  val notes: String? = null,              // instrucciones, no notas de cata
  val doseG: Double,                      // dosis nominal
  val stages: List<RecipeStage>,          // ordenadas por startMs
  val builtInKey: String? = null,         // "v60_classic", "four_six" o null si la creó el usuario
  val localizedNames: Boolean = false,    // true: los nombres se muestran traducidos (§3.2, predefinidas)
) {
  val totalWaterG: Double get() = stages.last().targetWaterG
  val ratio: Double get() = totalWaterG / doseG
}

data class RecipeStage(
  val name: String?,                      // opcional ("Bloom")
  val startMs: Long,                      // instante de inicio relativo a t0
  val targetWaterG: Double,               // agua ACUMULADA al terminar el vertido de la etapa
  val durationMs: Long,                   // duración del vertido
)
```

**Fuente de verdad:** la dosis y las etapas. El agua total es la de la última etapa, y el ratio
(`totalWaterG / doseG`) solo se muestra; no se guarda.

**Reglas de validación** (`RecipeValidator.validate(recipe): List<RecipeError>`, función pura, también
usada por el editor):

| Regla | Valor |
|---|---|
| Nombre | Obligatorio, tras recortar espacios |
| Dosis | De 5 a 60 g, con un decimal como máximo |
| Número de etapas | De 1 a 10 |
| Agua acumulada | Mayor que 0, estrictamente creciente entre etapas y ≤ 1000 g |
| Inicio | Primera etapa ≥ 0; cada etapa empieza en el fin de la anterior o después (`startMs[i] ≥ startMs[i−1] + durationMs[i−1]`) |
| Duración del vertido | De 1 a 120 s |
| Inicio de la última etapa | Antes de 10:00 (`< 600 000 ms`) |
| Temperatura | Opcional, de 0 a 100 °C con un decimal, como en `BrewDetails` |

Los textos se recortan y un texto vacío se guarda como `null` (igual que `BrewDetails.normalized()`).

**Recetas predefinidas** (`BuiltInRecipes`). Las cantidades corresponden a la dosis nominal y las dos
usan el método `V60`:

| Receta | Dosis | Agua total | Etapas (inicio · hasta · vertido) |
|---|---|---|---|
| V60 clásica (`v60_classic`) | 15 g | 250 g (1:16,7) | Bloom 0:00 · 40 g · 10 s; 2.º vertido 0:45 · 150 g · 20 s; 3.er vertido 1:30 · 250 g · 25 s |
| Método 4:6 (`four_six`) | 20 g | 300 g (1:15) | 1.º 0:00 · 60 g · 10 s; 2.º 0:45 · 120 g · 10 s; 3.º 1:30 · 180 g · 10 s; 4.º 2:15 · 240 g · 10 s; 5.º 3:00 · 300 g · 10 s |

- Se insertan al crear la base de datos (`RoomDatabase.Callback.onCreate`) y en la migración 1→2.
- Son editables y borrables como cualquier otra receta.
- **"Restaurar recetas predefinidas"** (en Ajustes, con un `AntModal` de confirmación):
  - Devuelve a sus valores de fábrica las recetas con `builtInKey` y vuelve a crear las que se
    hayan borrado.
  - No toca las recetas del usuario.
  - Una receta duplicada de una predefinida lleva `builtInKey = null` y `localizedNames = false`.
- **Nombres traducidos:**
  - Las predefinidas se insertan con `localizedNames = true`. Mientras lo sea, el repositorio
    devuelve el nombre de la receta y los de sus etapas desde `strings.xml`, en el idioma actual
    (interfaz `BuiltInRecipeTexts`, implementada con `Resources`).
  - Las columnas guardan además el texto en el idioma de la inserción, como respaldo.
  - Si el usuario cambia en el editor el nombre de la receta o el de alguna etapa, se guardan los
    textos mostrados y `localizedNames` pasa a `false`. Cambiar solo las cantidades no lo altera.
  - "Restaurar recetas predefinidas" vuelve a poner `localizedNames = true`.
  - En inglés: "Classic V60" y "4:6 method"; las etapas, "Bloom", "2nd pour", "3rd pour"…
  - El `referenceName` de una preparación guardada es el nombre mostrado al empezar, y no se
    traduce después.

### 3.3 Curva objetivo

```kotlin
package dev.dotscale.brew

data class TargetPoint(val tMs: Long, val waterG: Double)

data class TargetStage(
  val index: Int,                          // 0-based
  val name: String?,
  val startMs: Long,
  val endMs: Long,                         // fin del vertido de la etapa
  val targetWaterG: Double,                // agua acumulada al terminar la etapa
)

data class TargetCurve(
  val points: List<TargetPoint>,           // ordenados por tMs, sin duplicados de tMs
  val stages: List<TargetStage>,           // al menos una
) {
  /** Fin del vertido de la última etapa. Es el "tiempo objetivo" de la métrica de ajuste. */
  val endMs: Long get() = stages.last().endMs
  /** Agua acumulada de la última etapa. */
  val finalWaterG: Double get() = stages.last().targetWaterG

  /** Interpolación lineal entre puntos. Antes del primero vale 0; después del último, el último valor. */
  fun waterAt(tMs: Long): Double

  /** Multiplica el agua de puntos y etapas por `factor`; los tiempos no cambian. */
  fun scaled(factor: Double): TargetCurve
}
```

**Desde una receta** (`TargetCurve.fromRecipe(recipe)`):
- Por cada etapa `i` se añaden los puntos `(startMs, aguaPrevia)` y
  `(startMs + durationMs, targetWaterG)`, donde `aguaPrevia` es el `targetWaterG` de la etapa
  anterior (0 en la primera). Si la primera etapa no empieza en 0, se añade `(0, 0)`.
- Resultado: una rampa durante cada vertido y una meseta durante cada espera.
- `stages` sale tal cual de la receta, con `endMs = startMs + durationMs`.

**Desde una preparación guardada** (`TargetCurve.fromBrew(samples, pours)`, devuelve `null` si no hay
muestras válidas):
- `points` son las muestras válidas (`valid = true`) como `(tMs, waterG)`, reducidas a una cada
  250 ms como máximo (la última de cada tramo). La curva dibujada es la real, no una idealizada.
- `stages` sale de los vertidos (`Pour`):
  - `startMs` y `endMs` son los del vertido.
  - `targetWaterG = waterAt(endMs)` de la propia curva. `Pour.waterG` es el agua de ese vertido y no
    la acumulada.
- Si no hay vertidos (preparación sin puntuación o muy corta), se usa una única etapa de 0 a
  `totalTimeMs`, hasta el agua total.

**Escalado por dosis:**
- `factor = dosisCapturada / dosisNominal`.
  - En una receta, la dosis nominal es `recipe.doseG`.
  - En una preparación guardada, la dosis nominal es su `doseG`.
- Si falta la dosis capturada o la nominal, `factor = 1`.
- El factor se recalcula al capturar o borrar la dosis mientras la sesión está en `Armed` o
  `Finished`.
- Al pasar a `Active` la curva escalada se congela: ni capturar la dosis ni editar la receta la
  cambian hasta que la sesión termina.
- La dosis se sigue guardando en la unidad de la báscula (`ScaleUiState.dose`) y se convierte a
  gramos con `toGrams`. Cambiar de unidad borra la dosis (comportamiento actual), y la curva vuelve a
  `factor = 1`.

**Alineación y reloj:**
- El t = 0 de la curva objetivo es el t0 de la sesión, el primer instante sobre el umbral
  (SPEC-001).
- La guía sigue un **reloj fijo**: las etapas avanzan por tiempo, aunque el usuario vaya atrasado.
- Pasada la última etapa, el objetivo se queda en la meseta final.
- `BrewState` gana `t0MonoMs: Long?` (no nulo en `Active`), para que el ViewModel calcule
  `tMs = clock() − t0MonoMs`.

### 3.4 Guía en vivo

```kotlin
enum class DeviationLevel { OK, WARNING, ERROR }

data class GuideState(
  val stage: TargetStage?,          // etapa en curso: desde su startMs hasta el startMs de la siguiente
  val nextStage: TargetStage?,
  val countdownS: Int?,             // 5..1 en los últimos TargetConfig.countdownMs antes de nextStage.startMs; null fuera
  val deviationG: Double?,          // agua actual − waterAt(tMs); null fuera de Active o sin muestra válida reciente
  val deviationLevel: DeviationLevel?,
)

object TargetGuide {
  fun at(target: TargetCurve, phase: BrewPhase, tMs: Long?, waterG: Double?, config: TargetConfig): GuideState
}
```

- **Niveles del desvío**, en valor absoluto y relativos a `finalWaterG` de la curva escalada:
  - `OK` hasta el 5 %.
  - `WARNING` por encima del 5 % y hasta el 10 %.
  - `ERROR` por encima del 10 %.
  - Por ejemplo, con 250 g de objetivo: hasta 12,5 g, hasta 25 g y más de 25 g.
- **Signo:** "+12 g" significa por encima del objetivo y "−8 g", por debajo.
- **Etapas:**
  - En `Armed`, `stage` es la primera etapa, para anunciar qué hay que hacer.
  - En `Active`, `stage` se calcula con `tMs`.
  - En `Finished`, todo es `null`.
- **Avisos de etapa** (`StageAlertTracker`, puro):
  - Emite `StageStarted(stage)` una sola vez por etapa, cuando `tMs` cruza su `startMs`.
  - No avisa de una etapa con `startMs = 0`, porque el usuario ya está vertiendo.
  - Se reinicia con cada sesión nueva.
  - Si un salto de tiempo cruza varias etapas, solo avisa de la última.
- **Ejecución de los avisos** (`StageNotifier`):
  - **Vibración:** corta (unos 150 ms, `VibrationEffect.createOneShot`) si
    `TargetSettings.stageVibration` está activo.
  - **Sonido:** un pitido corto del sistema (`ToneGenerator.TONE_PROP_BEEP` en
    `STREAM_NOTIFICATION`, sin recursos propios) si `TargetSettings.stageSound` está activo y
    `AudioManager.ringerMode == RINGER_MODE_NORMAL`. Así se respeta el modo silencio y el modo
    vibración.
  - Los dos avisos vienen activados por defecto.
  - Si el dispositivo no tiene vibrador, el aviso se omite sin error.
- **Cálculo:** la guía se calcula en cada `publish()` del ViewModel, al ritmo de las notificaciones
  de peso y del tick. La curva objetivo y su versión reducida para dibujar se calculan solo al
  cambiar la referencia, el factor de escala o la unidad, nunca en cada muestra.

**`TargetConfig`** (constantes, en `dev.dotscale.brew`):

| Constante | Valor |
|---|---|
| `deviationWarnShare` | 0,05 |
| `deviationErrorShare` | 0,10 |
| `countdownMs` | 5000 |
| `fitZeroErrorShare` | 0,10 |
| `fitGridMs` | 100 |
| `brewTargetMinStepMs` | 250 |

### 3.5 Métrica de ajuste

```kotlin
data class BrewFit(
  val score: Int?,                 // 0-100; null si no se puede calcular
  val targetWaterG: Double,        // finalWaterG de la curva escalada
  val targetEndMs: Long,           // endMs de la curva
) {
  fun waterDiffG(totalWaterG: Double) = totalWaterG - targetWaterG
  fun waterDiffShare(totalWaterG: Double) = waterDiffG(totalWaterG) / targetWaterG
  fun timeDiffMs(totalTimeMs: Long) = totalTimeMs - targetEndMs
}

fun brewFit(samples: List<BrewSample>, totalTimeMs: Long, target: TargetCurve, config: TargetConfig): BrewFit
```

- **Un solo cálculo:** el ViewModel llama a `brewFit` una vez, al recibir `Finished`. El mismo
  `BrewFit` se muestra en la tarjeta de resumen y viaja en `BrewContext` hasta el repositorio, que
  lo guarda sin recalcularlo.

- **Ventana:** `W = max(totalTimeMs, target.endMs)`. Si la preparación se para antes que la receta,
  penaliza.
- **Rejilla:** `t = 0, 100, 200 … W` (`fitGridMs`).
- **Agua real en `t`:** el `waterG` de la última muestra válida con `tMs ≤ min(t, totalTimeMs)`, o 0
  si no hay ninguna. Tras el fin se mantiene el último valor.
- **Error medio:** `MAE = media(|real(t) − target.waterAt(t)|)`.
- **Puntuación:** `score = round(100 × clamp(1 − MAE / (fitZeroErrorShare × target.finalWaterG), 0, 1))`.
  Una curva calcada da 100, y un error medio del 10 % del agua objetivo o más da 0.
- `score = null` si la preparación no tiene muestras válidas.
- **Diferencias finales:**
  - Agua: `totalWaterG − finalWaterG`, en gramos y en %.
  - Tiempo: `totalTimeMs − endMs`.
  - Con el fin por inactividad, `totalTimeMs` acaba en el último vertido (SPEC-001), así que se
    compara fin de vertido con fin de vertido.
- **Preparaciones interrumpidas** (`DISCONNECTED`): el ajuste se calcula igual, con la ventana ya
  descrita.
- **Versión de la métrica:** si cambia cualquier constante de `TargetConfig` que afecte al ajuste, se
  incrementa `BrewConfig.METRIC_VERSION` (SPEC-002 §3.3).

### 3.6 Cambios en la base de datos (versión 2)

`HistoryDatabase` pasa a la versión 2, con el esquema exportado en `app/schemas/` y la migración
explícita `MIGRATION_1_2`. No se usa `fallbackToDestructiveMigration` (SPEC-002 §3.3).

**Columnas nuevas en `brews`** (todas anulables, `null` en las preparaciones sin referencia y en
las existentes):

| Columna | Tipo | Contenido |
|---|---|---|
| `referenceType` | `ReferenceType?` (`RECIPE`, `BREW`), por nombre | Tipo de la referencia usada |
| `referenceId` | `Long?` | Id de la receta o preparación de referencia. Sin FK: el origen puede borrarse |
| `referenceName` | `String?` | Nombre de la referencia al empezar; se muestra aunque el origen ya no exista |
| `targetWaterG` | `Double?` | `BrewFit.targetWaterG` |
| `targetEndMs` | `Long?` | `BrewFit.targetEndMs` |
| `fitScore` | `Int?` | `BrewFit.score` |

Las diferencias se derivan de estas columnas y del resumen; no se guardan aparte. `BrewListRow` y
`BrewListItem` ganan `fitScore`, para el `AntTag` de la fila.

**Tabla nueva `brew_targets`** (1:1 opcional con `brews`, `brewId` PK y FK con `ON DELETE CASCADE`):

| Columna | Tipo | Contenido |
|---|---|---|
| `brewId` | `Long` | — |
| `data` | `ByteArray` (BLOB) | Curva objetivo escalada, codificada con `TargetCurveCodec` |

**`TargetCurveCodec`** (Kotlin puro, mismo estilo que `BrewCurveCodec`):
- Lleva un byte de versión del formato (1).
- **Puntos:** su número y, por cada uno, Δ`tMs` y Δ de `waterG × 100` cuantizado, en zigzag + varint
  (D4 de SPEC-002).
- **Etapas:** su número y, por cada una, `startMs`, `endMs`, `targetWaterG × 100` y el nombre en
  UTF-8 con su longitud (0 = `null`).
- El conjunto se comprime con `Deflater`.
- Un BLOB ilegible no hace fallar la app. El detalle omite la curva objetivo y muestra el ajuste a
  partir de las columnas.

**Tablas nuevas de recetas:**

| Tabla | Columnas |
|---|---|
| `recipes` | `id` (PK autogenerada), `name`, `method`, `methodOther`, `grind`, `waterTempC`, `notes`, `doseG`, `builtInKey` (único, anulable), `localizedNames`, `updatedAtEpochMs` |
| `recipe_stages` | `recipeId` (FK a `recipes` con `ON DELETE CASCADE`), `position`, `name`, `startMs`, `targetWaterG`, `durationMs`; PK (`recipeId`, `position`) |

**`MIGRATION_1_2`:**
- `ALTER TABLE brews ADD COLUMN …` para las seis columnas.
- `CREATE TABLE` de `brew_targets`, `recipes` y `recipe_stages`, con sus índices.
- Inserción de las recetas predefinidas.
- Todo dentro de la transacción de la migración. El historial existente se conserva intacto.

**Deshacer el borrado de una preparación:** `DeletedBrew` gana `target: BrewTargetEntity?`, y
`restore` lo reinserta con el resto.

### 3.7 Contratos de repositorios, ajustes y ViewModels

```kotlin
package dev.dotscale.recipe

class RecipeRepository(db: HistoryDatabase) {
  fun list(): Flow<List<RecipeListItem>>           // por nombre, sin distinguir mayúsculas
  fun observe(id: Long): Flow<Recipe?>
  suspend fun get(id: Long): Recipe?
  suspend fun save(recipe: Recipe): Long           // inserta (id = 0) o reemplaza receta y etapas en una transacción; actualiza updatedAtEpochMs
  suspend fun duplicate(id: Long): Long            // copia "<nombre> (copia)" con builtInKey = null
  suspend fun delete(id: Long)                     // deja la receta en pendingUndo
  suspend fun restore(deleted: DeletedRecipe)      // reinserta con el mismo id
  val pendingUndo: StateFlow<DeletedRecipe?>
  fun clearPendingUndo(expected: DeletedRecipe? = null)
  suspend fun restoreBuiltIns()                    // §3.2
}

data class RecipeListItem(
  val id: Long, val name: String, val method: BrewMethod?, val methodOther: String?,
  val doseG: Double, val totalWaterG: Double, val stageCount: Int, val builtIn: Boolean,
)
```

- Las escrituras corren en `Dispatchers.IO + NonCancellable`, como en D8 de SPEC-002.
- `BrewRepository` cambia en dos puntos:
  - `save(finished, context)` escribe también `brew_targets` y las columnas nuevas.
  - `BrewRecord` gana `target: TargetCurve?` y `targetError: Boolean`.

```kotlin
package dev.dotscale.settings

sealed interface ReferenceKey {
  data class Recipe(val id: Long) : ReferenceKey
  data class Brew(val id: Long) : ReferenceKey
}

class TargetSettings(context: Context) {           // mismo archivo "settings" que BrewSettings
  val reference: StateFlow<ReferenceKey?>           // claves "target_ref_type" y "target_ref_id"
  fun setReference(key: ReferenceKey?)
  val stageVibration: StateFlow<Boolean>            // por defecto true
  fun setStageVibration(enabled: Boolean)
  val stageSound: StateFlow<Boolean>                // por defecto true
  fun setStageSound(enabled: Boolean)
}
```

**`BrewContext`** (SPEC-002) gana dos campos:

```kotlin
data class BrewContext(
  // … campos de SPEC-002 …
  val target: UsedTarget?,           // null sin referencia
  val details: BrewDetails?,         // prellenado; null sin referencia
)

data class UsedTarget(
  val type: ReferenceType,
  val id: Long,
  val name: String,
  val curve: TargetCurve,            // la curva escalada y congelada al empezar
  val fit: BrewFit,                  // calculado una sola vez en el ViewModel (§3.5)
)
```

**`ScaleViewModel`:**
- Recibe además `TargetSettings`, `RecipeRepository`, el `BrewRepository` existente (para leer la
  referencia de tipo `BREW`) y un `StageNotifier`. Para probarlo en la JVM se le pasan falsos, como
  `BrewSaver` en D2 de SPEC-002.
- **Resuelve la referencia activa** observando `TargetSettings.reference`, combinada con
  `RecipeRepository.observe(id)` o con `BrewRepository.observe(id)`. Construye la `TargetCurve` y
  aplica el factor de escala.
- **Referencia que ya no existe:**
  - Si el origen se borra, o una preparación de referencia tiene la curva ilegible o sin muestras
    válidas, `TargetSettings.setReference(null)`.
  - Se emite `ScaleError.TargetRemoved`, que la pantalla muestra como "El objetivo elegido ya no
    existe".
- **Nuevos campos en `ScaleUiState`:**

  ```kotlin
  val target: ActiveTarget? = null        // nombre, tipo, curva escalada (en gramos) y factor
  val guide: GuideState? = null
  ```

- **Congelación:** al pasar a `Active`, congela `target` y no lo actualiza hasta `Finished`.
- **Avisos:** en cada `publish()` calcula `guide` y pasa el estado al `StageAlertTracker`. Los
  `StageStarted` resultantes van al `StageNotifier`.
- **Fin:** con `Finished(brew)` calcula `brewFit` y añade al `BrewContext`:
  - `target`, con el `fit`.
  - `details`, prellenado desde la referencia (sin nombre ni notas):
    - Con una receta: `method`, `methodOther`, `grind` y `waterTempC`. Las notas de la receta son
      instrucciones, no notas de cata.
    - Con una preparación: `method`, `methodOther`, `grind`, `waterTempC` y `coffee`. Repetir una
      preparación suele ser con el mismo café.
- `setReference` solo se acepta en `Armed` o `Finished`. En `Active` el chip está deshabilitado.

### 3.8 Navegación e interfaz

**Navegación** (`NavigationKeys.kt`):

```kotlin
@Serializable data object Recipes : NavKey
/** `id == null` crea una receta; `fromBrewId` la prellena desde una preparación (§3.8, Guardar como receta). */
@Serializable data class RecipeEdit(val id: Long? = null, val fromBrewId: Long? = null) : NavKey
@Serializable data class ComparePick(val aId: Long) : NavKey
@Serializable data class BrewCompare(val aId: Long, val bId: Long) : NavKey
```

- Icono de recetas (`AntIcons.Recipe`, trazo propio) solo en la `AntTopBar` de `ScanScreen`, junto
  al de historial.
  - La top bar de Scale no cambia: desde Scale se llega con "Gestionar recetas" en el selector de
    objetivo.
- Al elegir B, `ComparePick` se sustituye por `BrewCompare` en la pila, así que atrás vuelve al
  detalle de A.

**Scale: selector de objetivo** (`TargetPicker`, sobre `AntDrawer` inferior):
- **Chip:** en la cabecera de `BrewChartCard` hay un chip "Objetivo: <nombre>" o "Objetivo:
  ninguno". Está habilitado en `Armed` y `Finished`, y deshabilitado en `Active`.
- **Pestañas:** un `AntSegmented` "Recetas | Historial" que se abre en Recetas.
  - **Recetas:** filas de `RecipeRepository.list()` con el nombre, el método, la dosis, el agua
    total y el ratio. La receta activa va marcada.
  - **Historial:** filas de `BrewRepository.list(BrewFilter())` con el mismo aspecto que
    `HistoryRow`. Las preparaciones con la curva ilegible se muestran deshabilitadas.
- **Acciones:** "Quitar objetivo" (si hay uno) y "Gestionar recetas", que abre `Recipes`.
- **Elegir:** tocar una fila fija la referencia y cierra el selector.
- **Toques:** chip (1) y receta (2); o chip (1), Historial (2) y fila (3).

**Scale: tarjeta de la gráfica** (`BrewChartCard`):
- **`Armed` con objetivo:**
  - La gráfica dibuja solo la curva objetivo, con el eje X hasta su `endMs`.
  - Debajo aparece "Etapa 1/N · <nombre>: hasta X g en Y s" y, si el factor no es 1, "Escalada a
    16,4 g".
  - Se mantiene el texto "Vierte agua para empezar".
- **`Active` con objetivo:** la guía va en un bloque destacado **encima** de la gráfica, pensado
  para leerse de reojo con la tetera en la mano:

  ```
  ┌ Preparación · Objetivo: V60 clásica ─┐
  │ 2/3 · 2.º vertido                    │   texto pequeño (secundario)
  │   Hasta 150 g          +6 g          │   tipografía grande; el desvío lleva el color de su nivel
  │   Siguiente en 4 s                   │   grande, solo durante la cuenta atrás
  │ ──────────────────────────────────── │
  │  [gráfica: real + objetivo - - -]    │
  │  Estable · 82                        │
  └──────────────────────────────────────┘
  ```

  - La etapa ("2/3 · 2.º vertido") va en texto pequeño y secundario.
  - El objetivo de la etapa ("Hasta 150 g") y el desvío ("+6 g") van en la tipografía de cifras
    grande de la app, la misma que el peso de Scale.
  - Color del desvío: `success` con `OK`, `warning` con `WARNING` y `error` con `ERROR`, con
    contraste AA en los dos temas.
  - "Siguiente en 4 s" aparece grande solo durante la cuenta atrás.
  - Pasada la última etapa, el bloque muestra "Última etapa completada" y el desvío.
  - Debajo van la gráfica, con la curva real sobre la objetivo, y el `AntTag` de estabilidad de
    SPEC-001, que se mantiene.
- **Sin objetivo:** la tarjeta queda exactamente como en SPEC-001.
- **Accesibilidad:**
  - La `contentDescription` de la gráfica añade "Objetivo: X g en m:ss".
  - El cambio de etapa se anuncia con una región `liveRegion = Polite`.

**`BrewChart`:**
- Nuevo parámetro `target: TargetCurve? = null`, en gramos. Se convierte con `fromGrams` como el
  resto.
- La curva objetivo se dibuja en el eje del agua, discontinua y con un color nuevo
  `BrewChartColors.target`.
- `chartScale` incluye en los topes el agua máxima y el `endMs` del objetivo.
- El color nuevo y los de la comparación (A y B) cumplen el contraste del patrón de
  `AntColorsContrastTest`, en tema claro y oscuro.

**Scale: tarjeta de resumen** (`BrewSummaryCard`):
- Si la sesión tenía objetivo, se añade el bloque "Ajuste al objetivo" con:
  - La puntuación ("86 / 100").
  - "Agua −4 g (−1,6 %)".
  - "Tiempo +3 s".
  - El nombre de la referencia.
- Ese bloque muestra el `BrewFit` calculado al terminar, el mismo que se guarda (§3.5).

**Fila del historial** (`HistoryRow`):
- Si `fitScore` no es nulo, se añade un `AntTag` "Ajuste 86" junto al de estabilidad, con el color
  `Default`.
- Las filas sin referencia no cambian.

**Recetas** (`RecipesScreen`):
- `AntTopBar` "Recetas" con flecha atrás y el botón "+", que abre `RecipeEdit()`.
- Cada fila muestra el nombre, el método, "15 g · 250 g · 1:16,7 · 3 etapas" y un `AntTag`
  "Predefinida" si `builtIn`.
- Tocar una fila abre `RecipeEdit(id)`.
- Desde el editor se puede duplicar y borrar.
- Borrar vuelve a la lista con un `AntMessage` "Receta borrada" y la acción "Deshacer", con el mismo
  patrón de `pendingUndo` que SPEC-002 §3.6.
- Sin recetas: `AntEmpty` "Aún no hay recetas", con un botón "Crear receta".

**Editor de recetas** (`RecipeEditScreen`):
- **Campos:**
  - Nombre (obligatorio).
  - Método: `AntSelect` con "Otro".
  - Molienda.
  - Temperatura.
  - Notas, multilínea.
  - Dosis, en g.
- **Etapas:** una lista de filas con nombre (opcional), inicio (`m:ss`), "Hasta" (g) y vertido (s),
  y un botón para quitar la fila.
  - "Añadir etapa" propone el inicio en el fin de la anterior más 30 s, el agua anterior más 50 g y
    10 s de vertido.
  - Está deshabilitado con 10 etapas.
- **Derivados en vivo:** "Agua total 250 g · Ratio 1:16,7" y una vista previa con `BrewChart` (sin
  muestras y con `target`).
- **Validación:** los errores de `RecipeValidator` se muestran en cada campo con el estado `Error`,
  y "Guardar" queda deshabilitado mientras haya alguno.
- **Unidades:** el editor trabaja siempre en gramos, sea cual sea la unidad de la báscula. Las
  recetas de pour-over se escriben en gramos, y Scale convierte al mostrar.
- **Acciones:** "Guardar", "Duplicar" y "Borrar". Estas dos últimas solo aparecen al editar una
  receta existente.
- **Salir:** atrás con cambios sin guardar abre el `AntModal` "¿Descartar los cambios?", como en
  `BrewEditScreen`.

**Guardar como receta** (`RecipeEdit(fromBrewId = id)`, función pura `RecipeFromBrew`):
- Abre el editor relleno, sin guardar nada hasta pulsar "Guardar".
- **Datos de la receta:**
  - Nombre: el de la preparación (o su fecha y hora) más " (receta)".
  - Método, molienda y temperatura: los de la preparación. Las notas de cata no se copian.
  - Dosis: `doseG` si está entre 5 y 60 g; si no, vacía.
- **Etapas:** una por vertido (`Pour`).
  - `startMs` y `durationMs` se redondean a segundos enteros, con una duración mínima de 1 s.
  - El agua acumulada al final de cada vertido, redondeada a 1 g.
- **Ajustes automáticos:**
  - Con más de 10 vertidos, se fusionan los dos consecutivos separados por la pausa más corta (el
    inicio del primero, el fin del segundo y el agua del segundo), hasta quedar en 10.
  - Las duraciones de más de 120 s se recortan a 120 s.
  - Sin vertidos, se crea una única etapa de 0 s con la duración y el agua total, recortada a los
    límites.
- **Avisos:**
  - Si hubo ajustes, el editor muestra un `AntAlert` informativo con lo que se ajustó: vertidos
    fusionados, duraciones recortadas y dosis que falta.
  - Si algún valor sigue fuera de los límites (por ejemplo, más de 1000 g), aparece como error de
    validación normal.

**Detalle del historial** (`BrewDetailScreen`):
- `BrewChart` recibe `target` desde `brew_targets`.
- Si `fitScore` o `referenceType` no son nulos, una tarjeta "Ajuste al objetivo" muestra la
  referencia (`referenceName`), la puntuación y las diferencias de agua y de tiempo.
- Si la copia de la curva es ilegible, se muestra el ajuste sin la curva y un `AntAlert` "No se pudo
  leer la curva objetivo".
- Botones nuevos "Comparar con…" (abre `ComparePick(id)`) y "Guardar como receta" (abre
  `RecipeEdit(fromBrewId = id)`), junto a "Editar" y "Borrar".
- "Guardar como receta" se deshabilita con `curveError`.

**Selector de B** (`ComparePickScreen`):
- `AntTopBar` "Comparar con…".
- Los mismos dos `AntSelect` del historial ("Café" y "Método"):
  - "Café" empieza con el café de A, o en "Todos" si A no tiene café. "Método" empieza en "Todos".
  - Este filtro es independiente del de `HistoryScreen`, que no cambia.
  - Si el valor elegido desaparece de las opciones, vuelve a "Todos" (SPEC-002 §3.6).
- Debajo, la lista del historial (`HistoryRow`) filtrada y sin A.
- Tocar una fila abre `BrewCompare(aId, bId)`.

**Comparación** (`BrewCompareScreen`):
- **Cabecera:** una leyenda con A y B (color, nombre o fecha y hora, café).
- **Gráficas:** dos, con el mismo eje X (hasta el mayor de los dos `totalTimeMs`):
  - **Agua:** A y B.
  - **Flujo:** A y B. Las curvas de flujo no se colorean por estabilidad: cada serie lleva el color
    de su preparación.
  - Las dos usan un componente nuevo `CompareChart`, basado en las piezas de `BrewChart` (rejilla,
    `ChartAxis` y `downsample`).
- **Tabla:** columnas A, B y B−A; filas tiempo total, agua total, dosis, ratio, flujo medio, flujo
  máximo, estabilidad y número de vertidos. Un dato nulo se muestra como "—" y su diferencia también.
- **Unidad:** todo en la unidad guardada de A.
- **Curva ilegible:**
  - Si una de las dos curvas es ilegible, sus series se omiten y aparece un `AntAlert`. La tabla se
    muestra igual, con el número de vertidos como "—".
  - Si las dos son ilegibles, solo se muestra la tabla.
- Si A o B se borran mientras la vista está abierta, la pantalla vuelve atrás (patrón D7 de SPEC-002).

**Ajustes** (`SettingsScreen`):
- Sección "Guía de etapas" con dos `AntSwitch`: "Vibrar al empezar cada etapa" y "Sonido al empezar
  cada etapa".
- Botón "Restaurar recetas predefinidas", con confirmación.

**Textos:**
- Todos en `values/strings.xml` y `values-en/strings.xml`.
- Prefijos `target_`, `recipe_`, `compare_` y `fit_`.

### 3.9 Flujo de ejecución (sesión con objetivo)

```
TargetSettings.reference ─┐
RecipeRepository.observe ─┼─▶ ScaleViewModel: TargetCurve.fromRecipe/fromBrew ─▶ scaled(factor) ─▶ ui.target
BrewRepository.observe  ──┘                      (Armed/Finished: se recalcula; Active: congelada)

onWeight / tick ─▶ BrewSession ─▶ publish()
                                   ├─ TargetGuide.at(target, phase, tMs, waterG) ─▶ ui.guide
                                   └─ StageAlertTracker ─▶ StageStarted ─▶ StageNotifier (vibración/sonido)

BrewSession ──[Finished(brew)]──▶ ScaleViewModel
                                   ├─ brewFit(samples, totalTimeMs, curve) ─▶ BrewSummaryCard
                                   └─ BrewContext(target = UsedTarget(..., fit), details = de la referencia)
                                        └─ applicationScope: BrewRepository.save
                                             ├─ fit ─▶ columnas fitScore/targetWaterG/targetEndMs
                                             ├─ TargetCurveCodec.encode(curve) ─▶ brew_targets
                                             └─ transacción: brews + brew_curves + brew_targets
```

### 3.10 Simulador

- **Clase nueva:** `SimulatedTargetPour(target: TargetCurve, mode: FollowMode, seed: Int = 42)`.
  - Es Kotlin puro y determinista, con ticks de 100 ms, como `SimulatedPour`.
  - Por cada etapa, vierte el agua de la etapa durante su ventana con un flujo de base constante
    (`agua de la etapa / duración`). Le aplica un factor aleatorio de ±10 % que cambia cada pocos
    ticks y el suavizado de `SimulatedPour`. No vierte en las esperas.
- **Modos:**
  - `FAITHFUL`: sigue la curva. Debe terminar con el agua dentro de ±3 %, el tiempo dentro de ±5 s y
    `fitScore ≥ 90`.
  - `DEVIATED`: cada etapa empieza 8 s tarde y vierte el 80 % de su agua. Debe quedar fuera de esas
    tolerancias y con `fitScore ≤ 60`.
- **`SimulatedScaleConnection.simulatePour`** acepta también un `TargetCurve` y un `FollowMode`.
- **`SimulationCard`** gana dos botones, "Seguir objetivo" y "Seguir con desvío". Solo están
  habilitados con un objetivo activo y usan la curva escalada vigente.

### 3.11 Copia de seguridad, datos y permisos

- Las recetas viven en `history.db`, así que entran en la copia automática de Android, como el
  historial.
- La copia de la curva objetivo añade pocos KB por preparación con referencia. Se mantiene el
  objetivo de menos de 10 MB con 500 preparaciones (SPEC-002 §5), ahora con objetivo en todas.
- **Permiso nuevo:** `VIBRATE`, que es normal y no pide confirmación. El sonido no necesita permisos.
- No hay datos sensibles nuevos.

### 3.12 Decisiones de implementación

Aprobadas junto con el plan (`plan-curva-objetivo.md`) y aplicadas al implementarlo. Donde difieren de lo
descrito en §3.1–§3.11, mandan estas.

| ID | Tema | Decisión | Motivo |
|---|---|---|---|
| D1 | Orden de fases | Recetas (F4) va antes que Scale (F5), al revés que en el §8 del spec. | El selector de objetivo reutiliza `RecipeRow` y navega a `Recipes`, y `TargetSettings` hace falta ya para los ajustes de avisos. |
| D2 | Dependencias del ViewModel | `ScaleViewModel` recibe tres interfaces: `ReferenceStore` (`reference: StateFlow<ReferenceKey?>` y `setReference`, implementada por `TargetSettings`), `TargetReferences` (`recipe(id): Flow<Recipe?>` y `brew(id): Flow<BrewRecord?>`, implementada por `RepositoryTargetReferences(recipeRepository, brewRepository)`) y `StageNotifier`. | Igual que `BrewSaver` (D2 de SPEC-002): `SharedPreferences` y Room necesitan Android, y no hay Robolectric ni mockk. |
| D3 | `StageNotifier` (cierra la opción abierta del §3.1) | Interfaz `StageNotifier { fun stageStarted(stage: TargetStage); fun release() }` en `dev.dotscale.brew`. Implementación `AndroidStageNotifier(context, settings)` en `ui/scale`. Vibración con `VibratorManager.defaultVibrator` en API 31+ (y `Vibrator` antes), solo si `hasVibrator()`. `ToneGenerator(STREAM_NOTIFICATION, 80)` creado la primera vez que hace falta; si su constructor lanza, el sonido se omite. Lee los dos ajustes en cada aviso. `release()` se llama en `ScaleViewModel.onCleared()`. | Solo lo usa Scale, y `ToneGenerator` guarda recursos nativos que hay que liberar. |
| D4 | `fromBrew` | La firma es `TargetCurve.fromBrew(samples, pours, totalTimeMs)`. | El caso sin vertidos necesita `totalTimeMs` para su única etapa (§3.3). |
| D5 | Guía | `GuideState` gana `allStagesDone: Boolean` (`tMs ≥ target.endMs`), que pinta "Última etapa completada". Antes de una primera etapa con `startMs > 0`: `stage = null` y `nextStage` = la primera. Cuenta atrás: `ceil((next.startMs − tMs) / 1000)` si queda entre 1 ms y 5000 ms. Niveles con tolerancia `1e-9`, inclusivos en el 5 % y el 10 %. `tMs = clock() − t0MonoMs`; `waterG` es el de la última muestra si es válida y `null` si no. | El spec no dice cómo pintar el final ni el hueco previo a la primera etapa, y los límites se prueban en sus bordes. |
| D6 | Reloj y avisos | `BrewState.t0MonoMs` no es nulo en `Active` ni en `Finished` (por defecto `null`, para no romper previews y tests). `StageAlertTracker.update(t0MonoMs, target, tMs): TargetStage?` se reinicia cuando cambia `t0MonoMs`. El tracker y el notifier se llaman después de `_ui.update`, nunca dentro. | El bloque de `update` puede reintentarse, y un efecto ahí dentro repetiría avisos. Usar t0 como clave reinicia el tracker con cada sesión sin más estado. |
| D7 | Ciclo de vida del objetivo en el ViewModel | (1) La resolución (`reference` + `flatMapLatest` + `distinctUntilChanged`) produce una base sin escalar: tipo, id, nombre, curva, dosis nominal y datos para prellenar. (2) `ui.target` se memoriza por `(base, dosis en g)`: la misma instancia mientras no cambien. (3) Al pasar a `Active` se congela en `sessionTarget`. (4) `save()` calcula `brewFit` con `sessionTarget` y deja `ui.lastFit` (nombre, curva y `BrewFit`). (5) `publish()` vacía `lastFit` al empezar otra sesión, como `savedBrew`. Un contador `internal` de construcciones de la curva sirve al test de rendimiento. | `publish()` corre antes que `handle()`, así que el ajuste no puede leer `ui.target` ya descongelado. La instancia estable evita que Compose recalcule la curva reducida. |
| D8 | Gráfica en `Finished` | Dibuja la curva de la sesión (`lastFit.curve`). El chip muestra la referencia elegida para la siguiente. | El spec no dice qué dibujar en `Finished`. Cambiar de objetivo tras terminar no debe mover la línea que se compara con la curva real. |
| D9 | Referencia ilegible | Dos errores: `ScaleError.TargetRemoved` ("El objetivo elegido ya no existe") y `ScaleError.TargetUnreadable` ("No se pudo leer la curva de esa preparación"), para `curveError` o sin muestras válidas. En el selector se deshabilitan las filas con `totalWaterG ≤ 0`. Un BLOB corrupto solo se detecta al resolver la referencia. | La lista no decodifica curvas (D1 de SPEC-002), y un BLOB corrupto es muy raro. El texto del spec sería falso para una curva ilegible. |
| D10 | Selector de objetivo | `TargetPicker` es un composable sin estado (listas, referencia activa y callbacks). `TargetPickerViewModel(recipeRepository, brewRepository)` vive en la entrada de Scale, y sus flujos solo se recogen con el selector abierto. Elegir pasa por `ScaleViewModel.selectTarget(key)`, que lo ignora en `Active`. `HistoryRow` gana `enabled`. | El test no necesita base de datos, y `ScaleViewModel` no carga dos listas que casi nunca se muestran. |
| D11 | Textos de recetas | La interfaz del spec `BuiltInRecipeTexts` pasa a ser `RecipeTexts`: `builtInName(key)`, `builtInStageNames(key)`, `copyName(name)` ("<nombre> (copia)") y `fromBrewName(base)` ("<base> (receta)"). Implementación `ResourcesRecipeTexts(context)`, que lee los recursos de la aplicación en cada llamada. Las etapas van en `<string-array>`. | Duplicar y "Guardar como receta" también necesitan textos traducidos fuera de Compose. |
| D12 | Regla de `localizedNames` | Sigue en `true` solo si el nombre y la lista de nombres de etapa son iguales a los mostrados al cargar (mismo número de etapas). Añadir o quitar una etapa también la pone en `false`. Al guardar, las columnas reciben siempre los textos mostrados. | Los nombres traducidos van por posición: con otra cantidad de etapas dejarían de cuadrar. |
| D13 | Orden de la lista de recetas | Se ordena en Kotlin, tras traducir, con `Collator.getInstance(Locale.getDefault())` (intensidad `SECONDARY`) y después por id. | SQL solo ve el texto de respaldo, no el nombre mostrado. |
| D14 | Base de datos | `RecipeDao` con `@Relation` (`RecipeWithStages`). `save` actualiza la fila y reemplaza las etapas en una `@Transaction`, sin borrar la receta. `restore` reinserta con el mismo id. `restoreBuiltIns` actualiza por `builtInKey` o inserta. `BuiltInRecipeSeeder` (SQL sobre `SupportSQLiteDatabase`) lo usan `Callback.onCreate` y la migración. La migración es una factoría `migration1To2(texts, clock)`. Los `CREATE TABLE` se copian del `2.json` exportado. `inMemory` también siembra. | Borrar la fila haría que `observe(id)` emitiese `null` y Scale quitase la referencia. La migración necesita `RecipeTexts`, así que no puede ser una constante. |
| D15 | Formulario del editor | `RecipeEditForm` puro, con los textos tal como se escriben. Inicio: `m:ss` o segundos enteros. "Hasta" y dosis: `,` o `.` y un decimal como máximo. Vertido: segundos enteros. Una receta nueva empieza con una etapa (0:00, 50 g, 10 s) y la dosis vacía. Con una sola etapa, el botón de quitar está deshabilitado. "Duplicar" está deshabilitado con cambios sin guardar; al duplicar, `RecipeEdit(id)` se sustituye por `RecipeEdit(copia)` en la pila. | El spec no fija el formato de los campos ni qué duplica el editor con cambios pendientes. |
| D16 | "Guardar como receta" | `RecipeFromBrew` parte de las etapas de `TargetCurve.fromBrew` (agua acumulada). Orden: fusionar (más de 10), redondear (inicio y duración a segundos, mínimo 1 s; agua a 1 g) y recortar a 120 s. La dosis se redondea a un decimal antes de comprobar 5-60 g. Devuelve `RecipeDraft(recipe, adjustments)`. | Misma definición de agua acumulada que la curva objetivo, y un orden que no deja duraciones de más de 120 s tras fusionar. |
| D17 | Perfil desviado del simulador | La primera etapa no puede ir tarde porque marca t0. Desde la segunda, cada etapa empieza 8 s después de su `startMs`, y todas vierten el 80 % de su agua. Las dos clases implementan `PourScript { fun next(): Double? }`, y la conexión gana la sobrecarga `simulatePour(target, mode)`. | Retrasar todas las etapas lo mismo no cambia nada respecto a t0, así que el perfil no se desviaría en tiempo. |
| D18 | Tipografía de la guía | `scaleFigureStyle(size)`: el estilo del peso de Scale (`heading1`, `Light`) con tamaño variable. La guía usa 40 sp; el peso sigue en 64 sp. Desvío en enteros en g y con 2 decimales en oz, con "+" o "−" (U+2212). | A 64 sp no caben "Hasta 150 g" y "+6 g" en 360 dp. |
| D19 | Colores | `BrewChartColors.target = textSecondary`, discontinua (6 dp / 4 dp). Comparación: A = `primary.base` y B = el tono más cercano al naranja de Ant (`#FA8C16`) con ≥ 3:1 sobre `bgContainer` en cada tema. Se amplía `BrewChartColorsTest`. | El spec pide gris discontinuo. Azul y naranja se distinguen con los daltonismos más comunes. |
| D20 | Piezas de la gráfica | `PlotArea`, la rejilla con etiquetas y `segments()` pasan a `ui/scale/ChartDrawing.kt` (`internal`). La curva objetivo se reduce con `downsample` en `remember(target, unit, plotWidthPx, xTopMs)`. | `CompareChart` las necesita, y así la curva reducida no se recalcula con cada muestra. |
| D21 | Ajuste en el detalle y en Scale | Un único `FitBlock(fit: FitView, unit)`. `FitView` es puro y se construye desde `BrewFit` + resumen (Scale) o desde las columnas (detalle). Los botones del detalle van en dos filas: "Editar" / "Borrar" y "Comparar con…" / "Guardar como receta". | Mismas cifras en los dos sitios (criterio del §5). Cuatro botones no caben en una fila de 360 dp. |
| D22 | Selector de B | Se extraen `HistoryFilters` y `rememberHistoryFilter(initial, coffees, methods)` de `HistoryScreen`. `ComparePickViewModel(repository, aId)` observa A (si desaparece, vuelve atrás) y expone la lista filtrada sin A, los cafés, los métodos y `hasOthers` (nueva consulta `count()`). El filtro inicial se crea cuando A ya está cargada. | Sin A cargada no se sabe con qué café empezar, y el `rememberSaveable` fijaría "Todos". |
| D23 | Ajustes | `SettingsScreen(brewSettings, targetSettings, onRestoreRecipes: suspend () -> Unit, onBack)`. Tras restaurar se muestra el `AntMessage` "Recetas predefinidas restauradas". | Se sigue pudiendo probar aislada, sin `CompositionLocal`. |
| D24 | `BrewContext` | Gana `target: UsedTarget? = null` y `details: BrewDetails? = null`. El repositorio guarda `details?.normalized()`. El café de una preparación de referencia no se vuelve a pasar por `canonicalCoffee`. | Los valores por defecto evitan tocar los sitios que no usan objetivo. El café copiado ya es una grafía existente. |

**Ajustes durante la implementación** (no cambian el comportamiento del §5):

- `ActiveTarget` lleva además `scaledDoseG` (la dosis capturada, en g, con la que se escaló): "Escalada a 16,4 g"
  la necesita y no se puede deducir del factor.
- `ScaleViewModel` recibe un último parámetro `targetConfig: TargetConfig = TargetConfig()`; `ScaleScreen` recibe
  también el `TargetPickerViewModel` y `onManageRecipes`.
- `RecipeRepository.duplicate` devuelve `Long?` (`null` si la receta ya no existe), y `RecipeFromBrew` marca la
  dosis que falta con `doseG = 0` en el borrador, que el editor muestra como campo vacío.
- `<uses-permission android:name="android.permission.VIBRATE" />` se añadió en F2 y no en F5, como pedía el plan.
- `kotlinx-serialization-core` 1.8.1 se fija solo en `debugImplementation`: `room-migration` (de `room-testing`)
  necesita 1.8 o más, pero el classpath de debug resuelve 1.7.3 por lifecycle y AGP alinea `androidTest` con él.
  No afecta a release. Es el aviso de lint `NewerVersionAvailable` de más respecto a la línea base de F0.
- `drawGridAndLabels` gana `showFlowAxis` y `leftTitleColor` para que `CompareChart` dibuje un solo eje Y.
- El selector de B y el historial comparten `HistoryFilters`/`rememberHistoryFilter`. Los cafés y métodos del
  selector son los de todo el historial, A incluida: si no, el café inicial de A desaparecería de las opciones y
  la regla "si el valor desaparece, vuelve a Todos" lo borraría al cargar.
- El E2E usa "Fin automático" en 60 s y no en 30 s: entre el primer y el segundo vertido de "V60 clásica" hay
  35 s sin verter, y con 30 s la sesión terminaría a mitad de la receta.
- `SimulatedScaleConnection` programa cada tick contra un plazo fijo (`System.nanoTime`) y no con `delay(100)` suelto.
  Con el `delay` suelto, el trabajo de cada tick alargaba el guion un ~4 %: "Seguir objetivo" terminaba en 02:00
  y daba un ajuste de 74 en el E2E, y con el plazo fijo termina en 01:55 y da 97.
- El color del desvío de la guía se pasa con `AntText(type = …)`. `figure.copy(color = …)` no tenía efecto, porque
  `AntText` pasa `color = antTextColor(type)` a `Text` y ese parámetro pisa el del estilo. Lo cubre
  `BrewChartCardTargetTest.theDeviationIsColoredByItsLevel`.
- Con el perfil desviado el ajuste sale 0 y no "≤ 60 por poco": falta un 20 % del agua, y el error medio supera
  el 10 % del agua final (§3.5), que ya da 0.

## 4. Casos Límite y Manejo de Errores

| Escenario | Comportamiento esperado |
|---|---|
| Sin objetivo elegido | Scale, la tarjeta de resumen y el guardado se comportan exactamente como en SPEC-001 y SPEC-002 |
| Se borra la receta activa (en Recetas, con Scale en la pila) | Scale quita la referencia y muestra "El objetivo elegido ya no existe". Deshacer el borrado restaura la receta, pero no vuelve a activarla |
| Se borra la preparación usada como referencia | Igual que con una receta borrada |
| Se edita la receta activa en `Armed` o `Finished` | La curva y la guía se actualizan al volver a Scale |
| Se edita la receta activa en `Active` | La sesión sigue con la curva congelada; la nueva se aplica a la siguiente |
| Referencia de tipo preparación con la curva ilegible o sin muestras válidas | No se puede elegir (fila deshabilitada). Si ya estaba activa, se quita con el aviso |
| Referencia de tipo preparación sin vertidos | Una única etapa de 0 a `totalTimeMs` (§3.3) |
| Referencia de tipo preparación sin dosis, o sin dosis capturada | `factor = 1`; la curva se usa tal cual |
| Capturar la dosis durante `Active` | Se actualiza el ratio, como hoy; la curva no se reescala |
| Cambio de unidad con objetivo | La dosis se borra (comportamiento actual), la curva vuelve a `factor = 1` y se muestra en la unidad nueva |
| El usuario empieza tarde o vierte de más | La guía sigue el reloj fijo; el desvío y su nivel lo reflejan |
| La preparación sigue tras la última etapa | El objetivo se queda en la meseta final; "Última etapa completada"; la ventana del ajuste se alarga hasta el fin real |
| La preparación termina antes que la receta | Ventana hasta `target.endMs` con el último agua real: el ajuste penaliza y la diferencia de tiempo es negativa |
| Desconexión a mitad con objetivo | Se guarda con `interrupted = true`, la referencia y el ajuste calculado sobre lo recibido |
| Un tick tardío cruza varias etapas a la vez | Un solo aviso, el de la última etapa cruzada |
| Etapa con `startMs = 0` | Sin aviso de vibración ni sonido; la guía la muestra desde `Armed` |
| Móvil en silencio o en vibración | Sin sonido aunque esté activado; la vibración depende solo de su ajuste |
| Dispositivo sin vibrador | El aviso de vibración se omite sin error |
| Rotación de pantalla en `Active` | El tracker de avisos vive en el ViewModel, así que no repite avisos |
| Muestra inválida (fuera de rango) | `deviationG = null` hasta la siguiente muestra válida; la etapa y la cuenta atrás siguen |
| Copia de la curva objetivo ilegible en el detalle | Ajuste sin curva y `AntAlert`; la app no se cierra |
| Migración 1→2 sobre una base con preparaciones | Se conservan todas, con las columnas nuevas a `null`, y se insertan las dos predefinidas |
| Instalación nueva | La base se crea en la versión 2 con las dos predefinidas |
| "Restaurar recetas predefinidas" con la predefinida activa y editada | Vuelve a los valores de fábrica; Scale recalcula la curva si no está en `Active` |
| Receta inválida en el editor | Error por campo y "Guardar" deshabilitado |
| Guardar como receta con más de 10 vertidos, vertidos de más de 120 s o sin dosis | Se ajusta según §3.8 y se avisa en el editor; sin dosis válida no se puede guardar |
| Guardar como receta con la curva ilegible | Botón deshabilitado |
| Comparar con una sola preparación en el historial | El selector de B muestra `AntEmpty` "No hay otras preparaciones para comparar" |
| El café de A no tiene más preparaciones | El selector de B muestra `AntEmpty` "Ninguna preparación coincide con el filtro"; se puede cambiar a "Todos" |
| Cambiar el idioma del móvil con predefinidas sin renombrar | Sus nombres y los de sus etapas se muestran en el idioma nuevo; las preparaciones ya guardadas conservan su `referenceName` |
| Comparar preparaciones en unidades distintas | Todo se muestra en la unidad de A |
| A o B borradas con la comparación abierta | La pantalla vuelve atrás |
| Duplicar una receta | Se crea "<nombre> (copia)" con `builtInKey = null` |
| Dos recetas con el mismo nombre | Se permite; se distinguen por el id |
| Cambiar el objetivo durante `Active` | No es posible: el chip está deshabilitado |

## 5. Criterios de Aceptación (Definition of Done)

**Funcionales** (verificables con la báscula simulada):
- [x] Con "V60 clásica" activa, la gráfica de `Armed` muestra la curva objetivo y la etapa 1.
      Elegirla cuesta 2 toques desde Scale.
- [x] Elegir una preparación del historial como objetivo cuesta 3 toques desde Scale.
- [x] La referencia elegida sigue activa tras salir de Scale y tras `am force-stop` de la app.
- [x] Capturar 16,4 g con una receta de 15 g escala el agua de todas las etapas ×16,4/15. Tras el
      inicio, capturar otra dosis no cambia la curva.
- [x] Durante `Active` se ven la etapa en curso, la cuenta atrás de 5 s y el desvío en gramos con
      su nivel (5 % y 10 %).
- [x] Al empezar cada etapa con `startMs > 0` hay una vibración, si está activada, y un pitido, si
      está activado y el móvil no está en silencio. Hay un solo aviso por etapa. (Verificado con
      `StageAlertTrackerTest` y `ScaleViewModelTargetTest` y un notifier falso: la vibración y el pitido reales
      no se observaron en el emulador.)
- [x] El perfil fiel del simulador con "V60 clásica" termina con el agua dentro de ±3 %, el tiempo
      dentro de ±5 s y un ajuste ≥ 90. El perfil desviado da un ajuste ≤ 60.
- [x] La tarjeta de resumen y el detalle muestran el mismo ajuste, y el detalle dibuja la curva
      objetivo guardada aunque la receta se edite o se borre después.
- [x] Una preparación hecha con receta se guarda con el método, la molienda y la temperatura de la
      receta. Con una preparación de referencia, también con su café.
- [x] Las filas del historial de preparaciones con referencia muestran el `AntTag` "Ajuste N".
- [x] Con el móvil en inglés, las predefinidas sin renombrar se llaman "Classic V60" y "4:6 method".
- [x] En Recetas se puede crear, editar, duplicar y borrar con "Deshacer". La validación de §3.2
      bloquea "Guardar".
- [x] "Restaurar recetas predefinidas" devuelve las dos a sus valores de fábrica sin tocar las del
      usuario.
- [x] "Guardar como receta" sobre una preparación simulada abre el editor con una etapa por vertido.
- [x] Desde el historial, superponer dos preparaciones cuesta 3 toques (fila A, "Comparar con…",
      fila B). El selector de B empieza filtrado por el café de A. La vista muestra dos gráficas y
      la tabla con B−A.
- [x] Sin objetivo, Scale y el guardado no cambian respecto a SPEC-002 (no hay regresiones en sus
      tests).
- [x] Todos los textos nuevos existen en `values/` y `values-en/`.

**Datos:**
- [x] `MIGRATION_1_2` pasa un test con `MigrationTestHelper`:
  - Las preparaciones de la versión 1 se conservan con todas sus columnas.
  - Las columnas nuevas quedan a `null`.
  - Existen las dos recetas predefinidas.
- [x] El esquema de la versión 2 está exportado en `app/schemas/`.

**Rendimiento** (emulador con API 36 o superior, misma metodología que SPEC-002 §5):
- [x] La vista de comparación de dos preparaciones de 10 min muestra sus gráficas en menos de
      500 ms (mediana de 5 aperturas, con una de calentamiento).
- [x] La curva objetivo y su versión para dibujar no se recalculan con cada muestra (verificado con
      un test del ViewModel que cuenta las construcciones).
- [x] `history.db` sigue por debajo de 10 MB con 500 preparaciones de 4 min, todas con curva
      objetivo.

**Pruebas JVM** (`app/src/test/kotlin/dev/dotscale/`):
- [x] `TargetCurveTest`:
  - `fromRecipe` con rampas y mesetas, y con la primera etapa empezando después de 0.
  - `waterAt` antes, dentro y después de la curva.
  - `fromBrew` con reducción a 250 ms, etapas desde los vertidos (agua acumulada) y el caso sin
    vertidos.
  - `scaled`.
- [x] `TargetGuideTest`:
  - Etapa en `Armed`, `Active` y `Finished`.
  - Cuenta atrás de 5 a 1.
  - Desvío con su signo y los tres niveles en sus límites.
  - `deviationG = null` con una muestra inválida.
- [x] `StageAlertTrackerTest`:
  - Un aviso por etapa y ninguno con `startMs = 0`.
  - Un solo aviso al cruzar varias etapas.
  - Reinicio con cada sesión.
- [x] `BrewFitTest`:
  - Una curva calcada da 100.
  - Un error medio del 10 % o más da 0.
  - Ventana alargada cuando la preparación para antes.
  - Diferencias finales.
  - `score = null` sin muestras válidas.
- [x] `RecipeValidatorTest`: cada regla de §3.2, en sus límites.
- [x] Nombres traducidos: `localizedNames` pasa a `false` al cambiar un nombre y no al cambiar solo
      cantidades.
- [x] `RecipeFromBrewTest`:
  - Una etapa por vertido.
  - Fusión por la pausa más corta.
  - Recorte a 120 s.
  - Dosis vacía fuera de 5-60 g.
  - Caso sin vertidos.
  - Redondeos.
- [x] `TargetCurveCodecTest`:
  - Ida y vuelta con nombres nulos y no nulos.
  - Error de 0,005 o menos en el agua.
  - Rechazo controlado de un BLOB corrupto.
- [x] `SimulatedTargetPourTest`: con `BrewSession` y `brewFit`, el perfil fiel cumple ±3 %, ±5 s y
      un ajuste ≥ 90, y el desviado da un ajuste ≤ 60.
- [x] `ScaleViewModelTargetTest`, con falsos de `TargetSettings`, del repositorio y de
      `StageNotifier`:
  - Resolución y escalado de la referencia.
  - Congelación en `Active`.
  - Referencia borrada → `TargetRemoved` y `setReference(null)`.
  - `BrewContext` con `target` y `details`.
  - Avisos hacia el notifier.

**Pruebas instrumentadas** (`app/src/androidTest/kotlin/dev/dotscale/`):
- [x] `HistoryMigrationTest` (`MigrationTestHelper`, versión 1 → 2).
- [x] `RecipeDaoTest` con Room en memoria:
  - Guardar con etapas y leer.
  - Reemplazar las etapas al editar.
  - Duplicar.
  - Borrar con cascada y `restore` con el mismo id.
  - `restoreBuiltIns`.
- [x] `BrewDaoTest` (ampliado):
  - Guardado con `brew_targets` y las columnas nuevas.
  - Cascada al borrar.
  - `restore` con el objetivo.
- [x] Tests de Compose:
  - `TargetPickerTest`: pestañas, elegir una receta (2 toques) y una preparación (3 toques),
    "Quitar objetivo" y filas deshabilitadas.
  - `BrewChartCardTargetTest`: guía en `Armed` y `Active`, cuenta atrás y niveles del desvío.
  - `RecipeEditScreenTest`: errores de validación, "Añadir etapa", el límite de 10 etapas, el aviso
    de "Guardar como receta" y el diálogo de descarte.
  - `RecipesScreenTest`: lista, estado vacío y borrar con deshacer.
  - `BrewCompareScreenTest`: leyenda, dos gráficas, tabla con B−A y "—" para los nulos.
  - `ComparePickScreenTest`: filtro inicial con el café de A, A excluida y estado vacío.
  - `HistoryScreenTest` (ampliado): `AntTag` "Ajuste N" solo en las filas con referencia.
- [x] `ComparePerformanceTest`: el objetivo de rendimiento de la comparación.

**E2E por adb** en el emulador con la báscula simulada, sin ARTEMIS, y con "Fin automático" en 30 s:
- [x] Recorrido:
  1. Elegir "V60 clásica".
  2. Capturar la dosis.
  3. "Seguir objetivo" y esperar el fin. Comprobar el ajuste en el resumen.
  4. Abrir el historial y el detalle. Comprobar la curva objetivo y el ajuste.
  5. "Guardar como receta" y guardar.
  6. Repetir con "Seguir con desvío".
  7. Comparar las dos preparaciones.
  8. `am force-stop`, volver a abrir y comprobar que la referencia sigue activa.
- [x] Las interacciones verificadas se plasman en los tests instrumentados.
- [x] `./gradlew :app:testDebugUnitTest`, los tests instrumentados y `./gradlew :app:lintDebug`
      pasan sin avisos nuevos.

## 6. Preguntas abiertas

1. **Sonido del aviso.** *Resuelto en la revisión: tono del sistema (`ToneGenerator.TONE_PROP_BEEP`),
   sin recursos propios (§3.4).*
2. **Nombres de las predefinidas en inglés e idioma de los nombres guardados.** *Resuelto en la
   revisión: "Classic V60" y "4:6 method", traducidos mientras la receta no se renombre
   (`localizedNames`, §3.2).*
3. **Valores por defecto de los avisos.** *Resuelto en la revisión: vibración y sonido activados
   (§3.7).*
4. **Unidad del editor de recetas.** *Resuelto en la revisión: siempre gramos (§3.8).*
5. **Ajuste en la fila del historial.** *Resuelto en la revisión: `AntTag` "Ajuste N" en la fila
   (§3.8).*
6. **Prellenado con una preparación como referencia.** *Resuelto en la revisión: se copian el
   método, la molienda, la temperatura y el café (§3.7).*
7. **Heredadas de SPEC-001 §6.** Las constantes del umbral y del flujo se calibrarán con la DOT
   física. Las tolerancias de §3.4 y §3.5 también pueden necesitar ajuste con vertidos reales.

## 7. Alternativas descartadas

- **Dividir SPEC-003 en curva objetivo, recetas y comparar:** se prefirió un único spec. El plan
  deberá partir la implementación en fases (§8).
- **Curva objetivo solo como referencia pasiva, o con desvío pero sin etapas:** se eligió la guía
  completa con etapas, cuenta atrás y avisos.
- **Guía adaptativa** (la etapa siguiente espera a que se alcance el agua): es más compleja, desplaza
  la curva y deja sin sentido la métrica de ajuste.
- **Etapas como agua + flujo + espera, o solo inicio + agua:** se eligió inicio + agua acumulada +
  duración, que define la rampa sin ambigüedad.
- **Recetas en gramos fijos o en porcentajes:** se eligió escalar con la dosis, que encaja con
  "Capturar dosis".
- **Dosis y ratio como fuente de verdad:** obligaría a mantener la última etapa igual a dosis × ratio.
  Se deriva el ratio.
- **Usar siempre la dosis nominal:** con una dosis real distinta, la curva no serviría.
- **Referencia por sesión o por preparación:** se prefiere recordarla hasta que se cambie.
- **Guía en una fila compacta bajo la gráfica:** se lee mal de reojo mientras se vierte. Se eligió
  un bloque grande encima.
- **Puntuación de ajuste más tolerante (0 con un 20 % de error medio):** las notas se separarían
  menos. Se mantiene el 10 %, a recalibrar con vertidos reales (§6.7).
- **Mostrar el desvío en segundos, o en gramos y segundos:** en las mesetas el desvío en tiempo es
  ambiguo.
- **Márgenes de ±3 % o en gramos fijos:** se eligieron el 5 % y el 10 % del agua final objetivo.
- **Guardar solo el id y el nombre de la referencia:** al editar o borrar la receta se perdería
  contra qué se preparó.
- **Migración destructiva o base de datos aparte para recetas:** se conserva el historial con una
  migración explícita en la misma base.
- **Predefinidas de solo lectura, o sin pantalla de recetas:** se prefieren editables, con
  restauración, y una pantalla propia.
- **Sin recetas predefinidas, o con una sola de ejemplo:** la función debe servir desde el primer
  uso.
- **Bloquear "Guardar como receta" si la preparación no cumple los límites:** se ajusta y se avisa.
- **Puntuar el ajuste solo sobre la duración real:** una preparación cortada sacaría buena nota.
- **Solo diferencias finales, o solo la puntuación:** se guardan las dos cosas.
- **Una sola gráfica de comparación con cuatro series, o con un selector agua/flujo:** se leen peor
  en el móvil que dos gráficas con el mismo eje X.
- **Comparar seleccionando filas en la lista del historial:** se eligió "Comparar con…" desde el
  detalle, que cumple los 3 toques sin un modo de selección.
- **Selector de B sin filtro, o con el filtro vacío:** con cientos de preparaciones costaría
  encontrar B. Lo habitual es comparar intentos del mismo café.
- **Icono de recetas también en la top bar de Scale, o acceso solo desde Ajustes:** la top bar de
  Scale ya va justa a 360 dp. "Gestionar recetas" en el selector cubre ese acceso.
- **Calcular el ajuste en el repositorio al guardar:** la tarjeta de resumen necesita el valor antes
  de que termine el guardado, y calcularlo dos veces podría dar números distintos.
- **Guardar los nombres de las predefinidas como texto fijo:** no se traducirían al cambiar el idioma.
- **No simular vertidos que sigan el objetivo:** sin ellos no se pueden verificar en el emulador los
  criterios de ±3 % y ±5 s.

## 8. Plan de implementación por fases

1. **Lógica pura:** `TargetCurve`, `TargetGuide`, `StageAlertTracker`, `brewFit`, `TargetConfig`,
   `Recipe`, `RecipeValidator`, `RecipeFromBrew`, `BuiltInRecipes` y `TargetCurveCodec`, con sus
   tests JVM.
2. **Base de datos:**
   - Versión 2: `MIGRATION_1_2`, entidades de recetas, `brew_targets` y columnas nuevas.
   - `RecipeRepository` y los cambios en `BrewRepository` y `DeletedBrew`.
   - Siembra de las predefinidas, `HistoryMigrationTest` y `RecipeDaoTest`.
3. **Simulador:** `SimulatedTargetPour` y `simulatePour` con objetivo, con `SimulatedTargetPourTest`.
4. **Scale:**
   - `TargetSettings`, `StageNotifier` y el permiso `VIBRATE`.
   - Integración en `ScaleViewModel` (referencia, escalado, congelación, guía, avisos y
     `BrewContext`).
   - `target` en `BrewChart`, guía en `BrewChartCard`, ajuste en `BrewSummaryCard`, `TargetPicker`
     y botones del simulador.
5. **Recetas:** icono en Scan, `RecipesScreen`, `RecipeEditScreen` (incluido "Guardar como
   receta" y los nombres traducidos), y ajustes de avisos y restauración.
6. **Historial:** `AntTag` de ajuste en la fila, curva objetivo y ajuste en el detalle,
   `ComparePickScreen` con filtro, `BrewCompareScreen` y `CompareChart`.
7. **Verificación:** E2E por adb, `ComparePerformanceTest`, tests de Compose y checklist de §5.
