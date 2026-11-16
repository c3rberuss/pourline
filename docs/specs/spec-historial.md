# SPEC-002: Historial de preparaciones

| Campo | Valor |
|---|---|
| Estado | Implementado (F0–F6). Plan: [`plan-historial.md`](plan-historial.md) |
| Fecha | 2026-11-12 |
| Deriva de | [SPEC-001 Gráfica de vertido](spec-grafica-vertido.md) (Anexo y punto P1 de su [plan](plan-grafica-vertido.md)) |
| Módulos afectados | `:app` (nuevo paquete `dev.dotscale.history`, nuevo `ui/history`, `brew`, `ui/scale`, `ui/components`, navegación) |
| Specs relacionados | [SPEC-003 Curva objetivo](spec-curva-objetivo.md), [SPEC-004 Catálogo de cafés y compartir imagen](spec-catalogo-cafes.md) |

## 1. Resumen y Contexto

- **Problema:** SPEC-001 no persiste nada. Toda preparación se pierde al salir de Scale o al cerrar
  la app (SPEC-001 §4). Sin historial no se puede ajustar una receta comparando preparaciones,
  llevar un diario de cata ni ver si el pulso mejora con el tiempo, y SPEC-003 no tiene de dónde
  sacar una curva de referencia.
- **Solución:** guardar automáticamente en una base de datos Room cada sesión terminada, con su
  curva completa y su resumen. Se añade una pantalla de historial con filtro, gráfica de progreso
  de la estabilidad y filas con minicurva, además de un detalle y una pantalla de edición de los
  datos manuales (nombre, café, método, molienda, temperatura y notas).
- **Usos previstos:** ajustar la receta (dial-in), diario de cata, medir el progreso de la
  estabilidad y servir de base para SPEC-003.
- **Éxito:**
  - Ninguna sesión terminada se pierde, ni al salir de Scale ni al cerrar la app.
  - Cualquier preparación se encuentra y se abre en tres toques o menos desde la pantalla de
    inicio (Scan).
- **Alcance (In):**
  - Guardado automático de toda sesión terminada, incluidas las de la báscula simulada, que quedan
    marcadas.
  - Salir de Scale con una sesión activa la termina y la guarda.
  - Pantalla de historial:
    - Acceso con un icono en las top bars de Scan y de Scale.
    - Filtro por café y por método.
    - Gráfica de progreso de la estabilidad.
    - Lista con minicurva, la más reciente primero.
  - Detalle con la gráfica y el resumen de SPEC-001.
  - Edición de los datos manuales con botón "Guardar", accesible desde la tarjeta de resumen de
    Scale ("Añadir detalles") y desde el detalle.
  - Borrado desde el detalle con "Deshacer".
  - Cierre del punto P1 del plan de SPEC-001: pausar el temporizador al terminar y reiniciarlo al
    rearmar con tara (§3.5).
  - Textos en español e inglés.
- **Alcance (Out):**
  - Comparar o superponer curvas de dos preparaciones (pasa a SPEC-003).
  - Exportar el historial a un archivo (CSV o JSON).
  - Catálogo de cafés con pantalla propia.
  - Recalcular resúmenes guardados con constantes nuevas (§3.3, versión de la métrica).
  - Recuperar una sesión activa tras la muerte del proceso.
  - Compartir como imagen ([SPEC-004](spec-catalogo-cafes.md)).

## 2. Historias de Usuario y Casos de Uso

- **US-1:** Como aficionado, quiero que cada preparación se guarde sola al terminar, para no perder
  ninguna aunque salga de la pantalla o cierre la app.
- **US-2:** Como aficionado, quiero apuntar el café, el método, la molienda, la temperatura y mis
  notas de cata justo al terminar, o más tarde, para recordar qué hice y qué me pareció.
- **US-3:** Como aficionado que ajusta una receta, quiero filtrar el historial por café y método, y
  reconocer cada preparación por su curva, para comparar mis intentos con el mismo café.
- **US-4:** Como aficionado, quiero ver cómo evoluciona la estabilidad de mi vertido a lo largo del
  tiempo, para saber si mejoro.
- **US-5:** Como aficionado, quiero abrir una preparación pasada y ver su gráfica y su resumen
  completos, en la unidad en que la hice.
- **US-6:** Como aficionado, quiero borrar una preparación y poder deshacerlo, para limpiar pruebas
  sin miedo a equivocarme.
- **US-7:** Como desarrollador sin la báscula física, quiero que las preparaciones simuladas se
  guarden marcadas y no ensucien la gráfica de progreso.

### Flujo principal

1. La sesión termina en Scale por cualquiera de los motivos de SPEC-001 §3.4, o porque el usuario
   sale de Scale con la sesión activa (§3.5).
2. La app guarda la preparación en segundo plano. Mientras guarda, el botón "Añadir detalles" de la
   tarjeta de resumen está deshabilitado; al terminar se habilita.
3. "Añadir detalles" abre la pantalla de edición de esa preparación. El usuario rellena los campos,
   pulsa "Guardar" y vuelve a Scale.
4. Desde Scan o Scale, el icono de historial abre la lista. Arriba van el filtro (café y método) y
   la gráfica de progreso; debajo, las filas.
5. Una fila abre el detalle: gráfica, resumen y datos manuales, con los botones "Editar" y "Borrar".
6. "Borrar" vuelve a la lista y muestra un aviso con "Deshacer" durante unos segundos.

## 3. Especificación Técnica

### 3.1 Ubicación del código

| Elemento | Ubicación | Tipo |
|---|---|---|
| Base de datos, entidades, DAO y repositorio | `app/src/main/kotlin/dev/dotscale/history/` | Room |
| Codificación de la curva y minicurva | `app/src/main/kotlin/dev/dotscale/history/` (`BrewCurveCodec`, `BrewPreview`) | Kotlin puro, sin `android.*` |
| Pantallas de historial, detalle y edición | `app/src/main/kotlin/dev/dotscale/ui/history/` | Compose |
| Singletons (base de datos, repositorio, `applicationScope`) | `DotScaleApp` | Existente |
| Efectos de fin y del temporizador | `dev.dotscale.brew.BrewSession` | Existente, cambia el contrato (§3.5) |
| Guardado al terminar y "Añadir detalles" | `ScaleViewModel`, `ScaleUiState`, `BrewSummaryCard` | Existentes |
| Navegación | `NavigationKeys.kt`, `Navigation.kt` | Existentes |
| Componentes nuevos | `ui/components/` (`AntSelect`, `AntAutoComplete`, acción en `AntMessage`, icono `AntIcons.History`) | Compose |

**Dependencias nuevas:**
- Room (`room-runtime`, `room-ktx`, `room-compiler`) con KSP y el plugin de Gradle de Room para
  exportar el esquema.
- La versión exacta, compatible con AGP 9 y Kotlin 2.3.20, se fija en el plan (§6).

### 3.2 Modelo de datos

Base de datos `HistoryDatabase`, archivo `history.db`, versión 1, con `exportSchema = true` y el
esquema en `app/schemas/`. Todas las magnitudes se guardan en gramos y g/s, como en SPEC-001.

**Tabla `brews`** (una fila por preparación; la lista nunca carga la curva):

| Columna | Tipo | Origen |
|---|---|---|
| `id` | `Long`, PK autogenerada | — |
| `startedAtEpochMs` | `Long`, indexada | Hora de pared de t0 (§3.5) |
| `name` | `String?` | Manual. `null` muestra la fecha y la hora con el formato del idioma actual |
| `coffee` | `String?` | Manual, texto libre recortado; vacío se guarda como `null` |
| `method` | `BrewMethod?` | Manual |
| `methodOther` | `String?` | Manual, solo con `method == OTHER` |
| `grind` | `String?` | Manual, texto libre |
| `waterTempC` | `Double?` | Manual, de 0 a 100 |
| `notes` | `String?` | Manual, texto libre multilínea |
| `totalTimeMs`, `totalWaterG`, `avgFlowGps`, `maxFlowGps` | | `BrewSummary` |
| `stabilityScore` | `Int?` | `BrewSummary` |
| `stabilityLevel` | `StabilityLevel?` | `BrewSummary` |
| `shareStable`, `shareIrregular`, `shareTurbulent` | `Double` | `BrewSummary.levelShare` (0 si falta el nivel) |
| `doseG` | `Double?` | Dosis de la sesión |
| `finalRatio` | `Double?` | `BrewSummary` |
| `finishReason` | `FinishReason` | `BrewSummary` |
| `interrupted` | `Boolean` | `BrewSummary` |
| `unit` | `WeightUnit` | Unidad vigente al terminar; la UI del historial la usa para mostrar |
| `scaleModel` | `ScaleModel?` | `ScaleUiState.model` |
| `simulated` | `Boolean` | `ScaleUiState.isSimulated` |
| `metricVersion` | `Int` | `BrewConfig.METRIC_VERSION` (constante nueva, vale 1) |
| `preview` | `ByteArray` | Minicurva (§3.4) |

Los enums se guardan por nombre (`TypeConverter`), no por ordinal.

**Tabla `brew_curves`** (1:1 con `brews`, `brewId` PK y FK con `ON DELETE CASCADE`):

| Columna | Tipo | Contenido |
|---|---|---|
| `brewId` | `Long` | — |
| `data` | `ByteArray` (BLOB) | Muestras y vertidos codificados (§3.4) |

```kotlin
package dev.dotscale.history

enum class BrewMethod { V60, KALITA_WAVE, CHEMEX, ORIGAMI, AEROPRESS, OTHER }

/** Fila de la lista: todo `brews` salvo los campos manuales largos. Nunca incluye `brew_curves`. */
data class BrewListItem(
  val id: Long,
  val startedAtEpochMs: Long,
  val name: String?,
  val coffee: String?,
  val totalTimeMs: Long,
  val totalWaterG: Double,
  val finalRatio: Double?,
  val stabilityScore: Int?,
  val stabilityLevel: StabilityLevel?,
  val unit: WeightUnit,
  val simulated: Boolean,
  /** Minicurva decodificada (48 valores de 0 a 1). Es una lista, no un `ByteArray`, para que la igualdad sea por contenido. */
  val preview: List<Float>,
)

/** Detalle completo: la fila, la curva decodificada y el resumen reconstruido. */
data class BrewRecord(
  val entity: BrewEntity,
  val samples: List<BrewSample>,
  val summary: BrewSummary,      // pours sale de la curva; el resto, de las columnas
)

data class BrewFilter(val coffee: String? = null, val method: BrewMethod? = null)

/** Punto de la gráfica de progreso. */
data class ProgressPoint(val brewId: Long, val startedAtEpochMs: Long, val score: Int)
```

### 3.3 Contrato del repositorio

`BrewRepository` es un singleton en `DotScaleApp`. Todas sus funciones `suspend` corren en
`Dispatchers.IO`.

```kotlin
class BrewRepository(private val db: HistoryDatabase) {
  /** Guarda una preparación terminada. Devuelve su id. Escribe `brews` y `brew_curves` en una transacción. */
  suspend fun save(finished: FinishedBrew, context: BrewContext): Long

  fun list(filter: BrewFilter): Flow<List<BrewListItem>>         // más reciente primero
  fun progress(filter: BrewFilter): Flow<List<ProgressPoint>>     // simulated = false y score no nulo, en orden cronológico
  fun coffees(): Flow<List<String>>                               // valores distintos, por uso más reciente
  fun methods(): Flow<List<BrewMethod>>                           // métodos presentes, para el filtro

  suspend fun get(id: Long): BrewRecord?
  suspend fun updateDetails(id: Long, details: BrewDetails)       // nombre, café, método, molienda, temperatura y notas
  suspend fun delete(id: Long)                                    // deja la fila y la curva en pendingUndo
  suspend fun restore(deleted: DeletedBrew)                       // reinserta con el mismo id y vacía pendingUndo
  val pendingUndo: StateFlow<DeletedBrew?>                        // último borrado que se puede deshacer (§3.6)
  fun clearPendingUndo()
}

/** Lo que aporta el ViewModel y la sesión no conoce. */
data class BrewContext(
  val startedAtEpochMs: Long,
  val unit: WeightUnit,
  val scaleModel: ScaleModel?,
  val simulated: Boolean,
)
```

**Reglas:**
- **Café sin duplicados por mayúsculas.**
  - Al guardar los detalles, si ya existe un café igual sin distinguir mayúsculas (comparado en
    Kotlin con `lowercase()`, que pliega también las vocales acentuadas), se guarda con la grafía
    existente.
  - Así, "etiopía" se guarda como "Etiopía" si esta ya existía.
  - El filtro compara el texto exacto, tras recortar espacios. No se usa `COLLATE NOCASE`, que solo
    pliega ASCII.
- El filtro por método compara `method`. Con `OTHER` se incluyen todas las preparaciones de método
  "Otro", sea cual sea `methodOther`.
- La lista y el progreso son `Flow` de Room: reflejan al instante el guardado, la edición y el
  borrado. No se usa Paging (ver el objetivo de rendimiento en §5).
- **Versión de la métrica.** El resumen se guarda tal como se calculó y no se recalcula al abrirlo.
  `metricVersion` permite que un spec futuro recalcule a partir de las muestras si cambian las
  constantes de `BrewConfig` al calibrar con la DOT física (SPEC-001 §6). `BrewConfig.METRIC_VERSION`
  se incrementa cuando cambia cualquier constante que afecte a la estabilidad o al resumen.
- **Migraciones.** Cualquier cambio de esquema posterior a la versión 1 exige una `Migration` con
  su test. No se usa `fallbackToDestructiveMigration`.

### 3.4 Codificación de la curva y minicurva

**`BrewCurveCodec`** (Kotlin puro, `encode(samples, pours): ByteArray` y
`decode(bytes): Pair<List<BrewSample>, List<Pour>>`):
- Byte inicial de versión del formato (1).
- Muestras: número de muestras y, por cada una, en varint:
  - Δ`tMs` respecto a la anterior.
  - Δ de `waterG` × 100 y Δ de `flowGps` × 100, redondeados y en zigzag.
  - Un byte con `stability` (0-100, o 255 para `null`) y `valid` en el bit alto.
- Vertidos: número de vertidos y, por cada uno, `startMs`, `endMs` y `waterG` × 100.
- El conjunto se comprime con `Deflater` / `Inflater` de `java.util.zip`.
- **Precisión:** `tMs`, `stability`, `valid` y el número de muestras y vertidos se conservan
  exactos. `waterG` y `flowGps` se conservan con un error de 0,005 o menos (cuantización a 0,01 g
  y 0,01 g/s). En onzas, la resolución de la báscula (0,01 oz ≈ 0,28 g) es más gruesa que esa
  cuantización.
- Un BLOB que no se puede decodificar no hace fallar la app. El detalle muestra el resumen de las
  columnas y un `AntAlert` "No se pudo leer la curva de esta preparación". Como los vertidos viven
  en el BLOB, en ese caso la lista de vertidos del resumen sale vacía.

**Minicurva (`BrewPreview`):**
- Al guardar se calculan 48 valores del agua vertida, el máximo de cada tramo de tiempo,
  normalizados a 0-255 respecto a `totalWaterG`.
- Se guardan en `brews.preview` (48 bytes). El repositorio los entrega como `List<Float>` de 0 a 1
  en `BrewListItem.preview`, y la fila los dibuja como una línea con el color
  `primary`, sin ejes.
- Es decorativa: no lleva `contentDescription`, porque la fila ya dice el agua, el tiempo y la
  estabilidad en texto.

### 3.5 Cambios en `BrewSession` y en `ScaleViewModel`

**Contrato de `BrewSession`.** Modifica SPEC-001 §3.3. Todos los métodos de evento devuelven
efectos, y la sesión recuerda el último `TimerStatus` recibido (de `onTimer` y del parámetro
`timer` de `onWeight`):

```kotlin
fun onWeight(message: ScaleMessage.Weight, nowMs: Long, timer: TimerStatus): List<BrewEffect>
fun onTimer(status: TimerStatus, nowMs: Long): List<BrewEffect>
fun onTared(nowMs: Long): List<BrewEffect>
fun onDisconnected(nowMs: Long): List<BrewEffect>
fun onTick(nowMs: Long, autoFinishMs: Long): List<BrewEffect>
fun finish(nowMs: Long): List<BrewEffect>

sealed interface BrewEffect {
  data object StartTimer : BrewEffect
  data object PauseTimer : BrewEffect
  data object ResetTimer : BrewEffect
  /** Se emite una sola vez por sesión, al pasar a Finished. */
  data class Finished(val brew: FinishedBrew) : BrewEffect
}

data class FinishedBrew(
  val t0MonoMs: Long,              // reloj monotónico de t0, para calcular la hora de pared
  val samples: List<BrewSample>,   // ya recortadas (D4 de SPEC-001)
  val summary: BrewSummary,
  val doseG: Double?,
)
```

**Temporizador al terminar (cierra P1 del plan de SPEC-001):**

| Evento | Efecto | Condición |
|---|---|---|
| Fin por `INACTIVITY`, `MANUAL` o `MAX_DURATION` | `PauseTimer` | Último estado `RUNNING` |
| Fin por `TIMER_PAUSED`, `TIMER_RESET` o `DISCONNECTED` | Ninguno | — |
| Tara que rearma una sesión `Finished` | `ResetTimer` | Último estado distinto de `RESET` |
| `Timer(RESET)` que rearma | Ninguno | — |
| Inicio confirmado | `StartTimer` (como en SPEC-001) | Último estado `RESET` |
| Inicio confirmado | `ResetTimer` y después `StartTimer` | Último estado `PAUSED` (p. ej., la primera sesión con el reloj en 0:42 de un uso anterior) |
| Inicio confirmado | Ninguno | Último estado `RUNNING`: podría ser la DOT arrancándolo sola (SPEC-001 §6.3) |

- El `Timer(PAUSED)` o `Timer(RESET)` que devuelve la báscula llega con la sesión ya en
  `Finished` y no tiene más efecto que el de SPEC-001 (un `RESET` rearma, lo que es idempotente).
- La tara de `captureDose()` también rearma y, por tanto, reinicia el temporizador.
- **Desfase aceptado con `INACTIVITY`:** la pausa se envía cuando se detecta el fin, `autoFinishSeconds`
  después del último vertido. El reloj de la báscula queda en la duración más ese margen (por
  ejemplo, 2:25 en vez de 1:25). La duración válida es la del resumen y la del historial; la
  báscula no permite fijar su reloj.

**`ScaleViewModel`:**
- Recibe `BrewRepository`, `applicationScope: CoroutineScope` y `wallClock: () -> Long`
  (por defecto `System::currentTimeMillis`), además de lo que ya recibe.
- Ejecuta `StartTimer`, `PauseTimer` y `ResetTimer` con `connection.send(...)`.
  - Los efectos de un mismo evento se ejecutan en una sola corrutina y en el orden de la lista, así
    que `resetTimer` siempre sale antes que `startTimer`.
  - El orden no depende de `Main.immediate` ni del `gattMutex` de `BleScaleConnection`.
  - Si un comando falla, el error va al canal `errors` y se sigue con el siguiente.
- Con `Finished(brew)`:
  - Calcula `startedAtEpochMs = wallClock() − (clock() − brew.t0MonoMs)`.
  - Construye `BrewContext` con `ui.unit`, `ui.model` y `ui.isSimulated`.
  - Lanza `repository.save(...)` en `applicationScope`, no en `viewModelScope`, para que salir de
    Scale no cancele el guardado.
  - Si el ViewModel sigue vivo, publica el resultado en `ScaleUiState.savedBrew`. Si falla, envía
    el mensaje al canal `errors`.
- Nuevo campo en `ScaleUiState`:

  ```kotlin
  sealed interface SavedBrew {
    data object Saving : SavedBrew
    data class Saved(val id: Long) : SavedBrew
    data object Failed : SavedBrew
  }
  val savedBrew: SavedBrew? = null   // null sin sesión terminada; se vuelve null al empezar otra sesión
  ```

- **Salir de Scale con una sesión activa:** `onCleared()` llama a `session.finish(clock())` antes de
  `connection.disconnect()`. Solo se procesa el efecto `Finished` (se guarda con `MANUAL`); los
  comandos del temporizador no se envían porque la conexión se cierra. La rotación no llama a
  `onCleared()` y no termina la sesión.

### 3.6 Navegación e interfaz

**Navegación** (`NavigationKeys.kt` y `Navigation.kt`):

```kotlin
@Serializable data object History : NavKey
@Serializable data class BrewDetail(val id: Long) : NavKey
@Serializable data class BrewEdit(val id: Long) : NavKey
```

- Icono de historial (`AntIcons.History`, trazo propio, sin recursos de TIMEMORE) en la
  `AntTopBar` de `ScanScreen` y de `ScaleScreen`, antes del engranaje.
- Desde Scan: historial (1) → fila (2). Cumple el objetivo de tres toques o menos.
- Ir de Scale al historial apila la pantalla y no destruye `ScaleViewModel`, así que no termina la
  sesión.

**Tarjeta de resumen en Scale** (`BrewSummaryCard`):
- Nuevo botón "Añadir detalles" que abre `BrewEdit(id)`.
- Está deshabilitado con `savedBrew == Saving`, habilitado con `Saved` y oculto con `Failed`.
- El resto de la tarjeta no cambia.

**Historial** (`HistoryScreen`):
- `AntTopBar` "Historial" con flecha atrás.
- **Filtro:** dos `AntSelect` (componente nuevo de opción única), "Café" y "Método".
  - Cada uno tiene la opción "Todos".
  - Las opciones salen de `coffees()` y `methods()`.
  - El filtro elegido se conserva al volver del detalle (`rememberSaveable`).
  - Si el valor elegido desaparece de las opciones (por ejemplo, al borrar la última preparación de
    ese café), el filtro vuelve a "Todos".
- **Gráfica de progreso:** una `AntCard` con un punto por preparación (`progress(filter)`), con
  el eje X en orden cronológico a intervalos iguales y el eje Y de 0 a 100.
  - Cada punto lleva el color de su nivel, el mismo que en la curva de flujo de SPEC-001.
  - Hay líneas de referencia en 75 y 50, y la fecha de la primera y la última preparación.
  - Con menos de 2 puntos se sustituye por el texto "Haz al menos dos preparaciones reales con
    puntuación para ver tu progreso".
  - `contentDescription`: número de preparaciones, puntuación media y la última.
- **Lista:** `LazyColumn` con `list(filter)`. Cada fila incluye:
  - La minicurva.
  - El nombre (o la fecha y la hora si es `null`) y la fecha.
  - El café, si hay.
  - El agua total y el tiempo total.
  - El ratio `1:x`, si hay dosis.
  - Un `AntTag` de estabilidad ("Estable · 82"), o "Sin puntuación".
  - Un `AntTag` "Simulada" si `simulated`.
- **Estados vacíos:**
  - Sin preparaciones: `AntEmpty` "Aún no hay preparaciones".
  - Con un filtro sin resultados: `AntEmpty` "Ninguna preparación coincide con el filtro".

**Detalle** (`BrewDetailScreen`):
- `AntTopBar` con el nombre y flecha atrás.
- `BrewChart` (reutilizado) con las muestras y la unidad guardada.
- `BrewSummaryCard` (reutilizado) con el resumen reconstruido, sin el botón "Añadir detalles".
- Una tarjeta "Detalles" con café, método, molienda, temperatura y notas. Los vacíos se omiten.
- Si `simulated`, un `AntTag` "Simulada". Si `metricVersion` es distinta de la actual, un texto
  "Puntuación calculada con una versión anterior de la métrica".
- Botones "Editar" (abre `BrewEdit(id)`) y "Borrar".

**Borrar y deshacer:**
- "Borrar" llama a `delete(id)` y vuelve a la lista.
- `delete` guarda la fila y la curva borradas en `BrewRepository.pendingUndo:
  StateFlow<DeletedBrew?>`.
  - Vive en el repositorio, el singleton de `DotScaleApp`, porque el detalle y la lista tienen
    ViewModels distintos y Navigation 3 no tiene una API para devolver resultados a la pantalla
    anterior.
- `HistoryScreen` observa `pendingUndo` y, cuando no es `null`, muestra un `AntMessage`
  "Preparación borrada" con la acción "Deshacer" durante `SnackbarDuration.Short`.
- "Deshacer" llama a `restore(deleted)`, que reinserta la fila y la curva con el mismo id.
- `pendingUndo` vuelve a `null` al pulsar "Deshacer", al expirar el aviso o al salir del historial.
  Un segundo borrado sustituye al anterior, que ya no se puede deshacer.
- `AntMessage` gana soporte de acción (hoy `actionLabel` es siempre `null`).

**Edición** (`BrewEditScreen`):
- **Campos:**
  - Nombre: `AntInput` con la fecha y la hora como placeholder.
  - Café: `AntAutoComplete` (componente nuevo: un `AntInput` con una lista desplegable) con las
    sugerencias de `coffees()`, filtradas por prefijo sin distinguir mayúsculas.
  - Método: `AntSelect` con V60, Kalita Wave, Chemex, Origami, Aeropress y Otro. "Otro" muestra un
    `AntInput` para `methodOther`.
  - Molienda: `AntInput` de texto libre.
  - Temperatura: `AntInput` numérico en °C, opcional, de 0 a 100 con un decimal como máximo. Fuera
    de rango usa el estado `Error` y deshabilita "Guardar".
  - Notas: `AntInput` multilínea.
- Todos los campos se recortan y un texto vacío se guarda como `null`.
- "Guardar" llama a `updateDetails` y vuelve atrás.
- Atrás con cambios sin guardar abre un diálogo "¿Descartar los cambios?" con "Descartar" y "Seguir
  editando". Sin cambios, vuelve directamente.

**Textos:** todos en `values/strings.xml` (español) y `values-en/strings.xml` (inglés), con
prefijos `history_`, `brew_detail_` y `brew_edit_`. Los nombres de los métodos no se traducen,
salvo "Otro" / "Other".

### 3.7 Flujo de ejecución (fin de una sesión)

```
BrewSession ──[Finished(brew), PauseTimer?]──▶ ScaleViewModel
                                                ├─ send(pauseTimer)                         (si aplica)
                                                ├─ _ui.update { savedBrew = Saving }
                                                └─ applicationScope.launch {
                                                     repository.save(brew, BrewContext(...))
                                                       ├─ BrewCurveCodec.encode + BrewPreview
                                                       └─ transacción: brews + brew_curves
                                                   } ─▶ savedBrew = Saved(id) | Failed + errors
HistoryScreen ◀── Flow<List<BrewListItem>> ── BrewDao (se actualiza solo)
```

### 3.8 Copia de seguridad y datos

- `allowBackup="true"` se mantiene, así que `history.db` entra en la copia automática de Android
  (límite de 25 MB). Con la curva comprimida, 500 preparaciones de 4 min deben ocupar menos de
  10 MB (§5).
- No se limita el número de preparaciones.
- No se añaden permisos. Los datos (café, notas) son del usuario, no son sensibles y no salen del
  dispositivo salvo por la copia de Android.

### 3.9 Decisiones de implementación

Tomadas en el [plan](plan-historial.md) y durante la implementación.

| ID | Tema | Decisión | Motivo |
|---|---|---|---|
| D1 | Versiones (cierra §6.1) | Room **2.8.5** (`room-runtime`, `room-compiler` y plugin `androidx.room`) y KSP **2.3.12** (plugin `com.google.devtools.ksp`). No se añade `room-ktx`. | `room-ktx` se fusionó en `room-runtime` en 2.7.0 y hoy es un artefacto vacío. KSP 2.3.1 añadió soporte para el Kotlin integrado de AGP 9, y la 2.3.10 corrigió la resolución de la clase `R` en ese modo. KSP 2.3.x ya no está atado a la versión de Kotlin. Room 2.8 exige minSdk 23 y la app usa 26. |
| D2 | Dependencia del ViewModel | `ScaleViewModel` recibe `fun interface BrewSaver { suspend fun save(finished: FinishedBrew, context: BrewContext): Long }`, que implementa `BrewRepository`. | Permite probarlo en la JVM con un falso. Room necesita Android, y no hay Robolectric ni mockk. |
| D3 | Byte de estabilidad del códec | Bits 0-6: `stability` de 0 a 100, o 127 (`0x7F`) para `null`. Bit 7: `valid`. | El spec decía 255 para `null`, que choca con el bit alto de `valid`. |
| D4 | Deltas del códec | Se cuantizan los valores absolutos (`q = round(v × 100)`) y se guarda `q[i] − q[i−1]`. Todas las deltas, incluida la de `tMs`, van en zigzag + varint. En los vertidos, `startMs` y `endMs` van en varint y `waterG × 100` en zigzag. | La cuantización absoluta no acumula error, así que se cumple el ≤ 0,005. El zigzag elimina el fallo de un Δ negativo a cambio de un bit. |
| D5 | Minicurva | 48 tramos iguales de `[0, totalTimeMs]`. Las muestras posteriores (el margen del recorte D4 de SPEC-001) caen en el último tramo. Un tramo sin muestras válidas repite el valor anterior (0 al principio). Con `totalWaterG ≤ 0`, todo vale 0. | El agua es acumulada: repetir el valor evita caídas falsas en la línea. |
| D6 | Curva ilegible | `BrewRecord` gana `curveError: Boolean`. El repositorio captura `BrewCurveFormatException` (y `DataFormatException`) y devuelve muestras y vertidos vacíos. | Es lo que necesita el `AntAlert` del detalle (§3.4 del spec). |
| D7 | Detalle reactivo | Se añade `fun observe(id: Long): Flow<BrewRecord?>` al repositorio. Observa la fila y decodifica la curva una sola vez por id. `get(id)` se mantiene para la edición. | Al volver de la edición, el detalle debe mostrar los datos nuevos. Si la fila desaparece (borrada aquí o en otro sitio), el flujo emite `null` y la pantalla vuelve atrás. Es un único camino para el borrado y el caso límite "fila borrada con el detalle abierto". |
| D8 | Escrituras que no se cancelan | `updateDetails`, `delete` y `restore` corren en `withContext(Dispatchers.IO + NonCancellable)`. `clearPendingUndo(expected)` solo vacía el valor si sigue siendo el mismo (compare-and-set). | Salir de la pantalla no puede cortar una escritura. Además, un aviso que expira no debe borrar el deshacer de un borrado posterior. |
| D9 | Café sin duplicados | `canonicalCoffee(input, existing)` es una función pura: recorta y, si hay una grafía igual con `lowercase()`, devuelve esa. `existing` excluye la propia preparación (`id != :id`). Todo ocurre en una `@Transaction` del DAO. | Si no se excluyera, no se podría corregir "etiopía" a "Etiopía" en la única preparación que lo usa. |
| D10 | `savedBrew` tardío | El ViewModel guarda un token de generación (`AtomicLong`) que se incrementa en cada `Finished` y al empezar una sesión. El resultado del guardado solo actualiza `savedBrew` si el token coincide. `publish()` pone `savedBrew = null` cuando la fase pasa a `Active`. | Así se implementa la fila "Nueva sesión iniciada mientras se guarda la anterior" (§4 del spec) sin depender del hilo. |
| D11 | Efectos de `BrewSession` | `lastTimer` empieza en `RESET`, el mismo valor por defecto que `ScaleUiState`. Orden: `[ResetTimer, StartTimer]` al iniciar desde `PAUSED` y `[PauseTimer, Finished]` al terminar. Una tara en `Finished` emite `ResetTimer` siempre que `lastTimer != RESET`, aunque la sesión ya estuviera rearmada. | Es idempotente en la báscula y la regla queda simple. |
| D12 | Errores localizados | `errors` pasa a `Channel<ScaleError>`, con `ScaleError.Command(detail: String?)` y `ScaleError.SaveFailed`. `ScaleScreen` los convierte en textos `R.string`. | El spec pide todos los textos en es/en. Hoy el ViewModel no tiene `Context` y usa literales en español. |
| D13 | Componentes extra | Nuevo `AntModal` (diálogo de confirmación sobre `Dialog`, con estilo Ant) para "¿Descartar los cambios?". `AntInput` gana `minLines` y usa `heightIn(min = controlHeight)` cuando `singleLine = false`. `AntSelect` y `AntAutoComplete` se basan en `ExposedDropdownMenuBox` de M3 con colores y radios Ant. | §3.1 no los listaba, pero sin ellos no hay diálogo ni campo de notas. `ExposedDropdownMenuBox` ya resuelve el foco, el teclado y la accesibilidad del desplegable. |
| D14 | Filtro | El filtro lo guarda `HistoryScreen` con `rememberSaveable` (un `Saver` propio) y lo pasa a `HistoryViewModel.setFilter`, que usa `flatMapLatest`. `coffees` y `methods` empiezan en `null` (cargando). | Si empezaran en `emptyList()`, la regla "el filtro vuelve a Todos" borraría el filtro guardado antes de la primera emisión. |
| D15 | Textos de método y fecha | Los nombres fijos de los métodos se definen en Kotlin (`BrewMethod.displayName`). Solo "Otro"/"Other" va en `strings.xml` (`brew_method_other`). La fecha usa `DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)` con el idioma actual y la zona del sistema. | Evita textos `translatable="false"`. `java.time` está disponible desde minSdk 26. |
| D16 | Orden de fases | El códec va antes que la base de datos, al revés que en el §8 del spec. | `BrewRepository.save` depende de `BrewCurveCodec`, `BrewPreview` y `FinishedBrew`. |
| D17 | Aislamiento de los tests | Los tests instrumentados usan Room en memoria. Los que lanzan `MainActivity` no crean preparaciones. El E2E sí usa el `history.db` real. | El `history.db` del emulador no se ensucia con datos de los tests. |
| D18 | Reinicio al empezar desde `PAUSED` | `BrewSession` ignora el primer `Timer(RESET)` que llega mientras está `Active` tras emitir su propio `ResetTimer` (flag `ignoreTimerReset`, se limpia con `RUNNING`). | Sin esto, la respuesta de la báscula al `resetTimer` terminaría la sesión recién iniciada con `TIMER_RESET`. |
| D19 | Tests JVM | `testOptions.unitTests.isReturnDefaultValues = true`. | El `ScaleViewModel` llama a `android.util.Log`, que lanza en la JVM. |
| D20 | Verificación en el emulador | `am kill` no mata un proceso en primer plano: el E2E usó `am force-stop`. `BackHandler` dentro de Navigation 3 sí tiene prioridad sobre `NavDisplay` (R2 descartado). | Comprobado con `input keyevent BACK` en el emulador. |

## 4. Casos Límite y Manejo de Errores

| Escenario | Comportamiento esperado |
|---|---|
| Sesión terminada por cualquiera de los seis motivos de SPEC-001 | Se guarda una vez; `Finished` se emite una sola vez por sesión |
| Salir de Scale con la sesión activa | Se termina como `MANUAL` y se guarda; el guardado no se cancela al destruir el ViewModel |
| Salir de Scale con la sesión terminada | Ya estaba guardada; no se guarda otra vez |
| Rotación de pantalla con la sesión activa | No termina ni guarda (no hay `onCleared()`) |
| Muerte del proceso con una sesión activa | La sesión se pierde (fuera de alcance) |
| Cerrar la app desde recientes con una sesión activa | Android suele matar el proceso sin garantizar `onCleared()`: la sesión se pierde, como con la muerte del proceso. No es un fallo |
| Muerte del proceso tras terminar | La preparación ya está en la base de datos |
| Desconexión BLE a mitad | Se guarda con `interrupted = true` y la fila y el detalle muestran el aviso de SPEC-001 |
| Fallo al guardar (disco lleno, error de SQLite) | Mensaje en el canal `errors`, el resumen sigue en pantalla y "Añadir detalles" se oculta |
| "Añadir detalles" pulsado antes de terminar el guardado | No es posible: el botón está deshabilitado con `Saving` |
| Nueva sesión iniciada mientras se guarda la anterior | `savedBrew` vuelve a `null` y el resultado tardío del guardado anterior se ignora en la UI; el guardado se completa |
| Sesión simulada | Se guarda con `simulated = true`, aparece en la lista con su etiqueta y queda fuera de `progress()` |
| Sesión sin puntuación (vertidos de menos de 2 s) | Se guarda; la fila muestra "Sin puntuación" y queda fuera de `progress()` |
| Sin dosis | `doseG` y `finalRatio` son `null`; la fila y el detalle no muestran ratio |
| Preparación hecha en onzas | Se guarda en gramos con `unit = OUNCES`; la fila y el detalle se muestran en onzas |
| BLOB de curva corrupto o con una versión de formato desconocida | El detalle muestra el resumen de las columnas y un `AntAlert` de error; la app no se cierra |
| Fila borrada mientras su detalle o su edición están abiertos | `get(id)` devuelve `null`; la pantalla vuelve atrás |
| Deshacer tras salir del historial | No disponible: `pendingUndo` se vacía al salir |
| Dos borrados seguidos | Solo se puede deshacer el último; el anterior queda borrado |
| Filtro con un valor que ya no existe | El filtro vuelve a "Todos" |
| Temperatura fuera de rango o no numérica | Estado de error en el campo y "Guardar" deshabilitado |
| Café escrito con otra variante ("Etiopía" frente a "etiopía ") | Se recortan los espacios y se guarda con la grafía existente ("Etiopía"); el filtro no muestra duplicados |
| Historial vacío o filtro sin resultados | `AntEmpty` con el texto correspondiente (§3.6) |
| Menos de 2 puntos de progreso | La gráfica se sustituye por el texto explicativo |
| Constantes recalibradas tras guardar | Las puntuaciones guardadas no cambian; el detalle avisa si `metricVersion` difiere |
| Reloj de la báscula tras un fin por inactividad | Queda pausado en la duración más `autoFinishSeconds`; desfase aceptado (§3.5) |
| Cambio de la hora del sistema durante una sesión | `startedAtEpochMs` usa la hora de pared al terminar menos la duración monotónica; la duración no se ve afectada |

## 5. Criterios de Aceptación (Definition of Done)

**Funcionales** (verificables con la báscula simulada):
- [x] Toda sesión terminada aparece en el historial sin pulsar nada, con su curva y su resumen.
- [x] Salir de Scale con una sesión activa la guarda como `MANUAL`.
- [x] Tras `adb shell am kill dev.dotscale` y volver a abrir la app, las preparaciones siguen en el
      historial.
- [x] Desde Scan, una preparación se abre en dos toques (icono de historial y fila).
- [x] "Añadir detalles" en el resumen abre la edición de esa preparación, y los datos guardados
      aparecen en el detalle y en la fila.
- [x] El filtro por café y por método reduce la lista y la gráfica de progreso.
- [x] La gráfica de progreso excluye las preparaciones simuladas y las que no tienen puntuación, y
      se oculta con menos de 2 puntos.
- [x] El detalle muestra la gráfica y el resumen en la unidad guardada.
- [x] Borrar y pulsar "Deshacer" restaura la preparación con el mismo id, curva incluida.
- [x] Salir de la edición con cambios pide confirmación; sin cambios, no.
- [x] Al terminar por inactividad, de forma manual o por duración máxima, se envía `pauseTimer` una
      vez. Al rearmar con tara se envía `resetTimer` una vez, y el siguiente vertido envía
      `startTimer`.
- [x] Un vertido con el temporizador en `PAUSED` envía `resetTimer` y después `startTimer`, y el
      reloj empieza desde 0:00.
- [x] Todos los textos nuevos existen en `values/` y `values-en/`.

**Rendimiento** (en un emulador con API 36 o superior y 500 preparaciones de 4 min cargadas por el
test). Cada tiempo se mide desde que se abre la pantalla hasta que existe el primer nodo con
`testTag` (`history_row` o `brew_chart`). Se hace una apertura previa de calentamiento que no
cuenta, y el resultado es la mediana de 5 aperturas:
- [x] La lista muestra la primera fila en menos de 1 s desde que se abre el historial.
- [x] El detalle muestra la gráfica en menos de 500 ms desde que se pulsa la fila.
- [x] `history.db` (con `-wal` y `-shm`) ocupa menos de 10 MB.

**Pruebas JVM** (`app/src/test/kotlin/dev/dotscale/`):
- [x] `BrewCurveCodecTest`:
  - Ida y vuelta con muestras válidas e inválidas, `stability` nula y no nula, y vertidos.
  - Error de 0,005 o menos en `waterG` y `flowGps`.
  - Ida y vuelta de una sesión en onzas convertida a gramos.
  - Rechazo controlado de un BLOB corrupto o de versión desconocida.
- [x] `BrewPreviewTest`: 48 valores, máximo por tramo y normalización a 0-255.
- [x] `BrewSessionTest` (ampliado):
  - `Finished` se emite una sola vez por sesión, con cada uno de los seis motivos.
  - `PauseTimer`, `ResetTimer` y `StartTimer` según la tabla de §3.5, incluido que no se emiten
    cuando el estado ya es el pedido, y el orden `ResetTimer`, `StartTimer` al iniciar desde
    `PAUSED`.
- [x] `ScaleViewModelBrewTest` (ampliado):
  - Un vertido simulado terminado llama a `save` una vez con el `BrewContext` correcto.
  - `onCleared()` con una sesión activa guarda como `MANUAL`.
  - `savedBrew` pasa por `Saving` y `Saved`.
  - Un fallo de `save` llega a `errors`.
  - Al iniciar desde `PAUSED`, la conexión falsa recibe `resetTimer` y después `startTimer`. Si
    `resetTimer` falla, `startTimer` se envía igualmente y el error llega a `errors`.
- [x] Lógica de la serie de progreso y del filtro, si queda fuera del DAO.

**Pruebas instrumentadas** (`app/src/androidTest/kotlin/dev/dotscale/`):
- [x] `BrewDaoTest` con Room en memoria:
  - Insertar y leer.
  - `list` sin la curva, más reciente primero.
  - Filtro por café y por método.
  - `progress` sin simuladas ni puntuaciones nulas.
  - `coffees()` sin duplicados.
  - `updateDetails`, incluido que "etiopía" se guarda como "Etiopía" si esta ya existe.
  - `delete` con cascada a `brew_curves`, `pendingUndo` relleno y `restore` con el mismo id.
- [x] Tests de Compose:
  - `HistoryScreenTest`: estado vacío, filas, filtro y vuelta a "Todos" al desaparecer el valor
    filtrado.
  - `BrewDetailScreenTest`: gráfica, resumen y borrar con deshacer.
  - `BrewEditScreenTest`: validación de la temperatura, guardar y diálogo de descarte.
  - `ScaleTopBarTest`: la barra superior de Scale, con un ancho fijo de 360 dp y un nombre de
    báscula de 40 caracteres, recorta el título con elipsis, y la batería, el historial, los
    ajustes y el tema siguen visibles y pulsables. No se hace por E2E porque el nombre de la
    báscula simulada es fijo ("DOT simulada").
- [x] `HistoryPerformanceTest`: los tres objetivos de rendimiento.

**E2E por adb** en el emulador con la báscula simulada, sin ARTEMIS:
- [x] Recorrido, con "Fin automático" en 30 s para que cada vertido simulado no tarde unos
      2,5 min:
  1. Simular un vertido estable y esperar el fin.
  2. "Añadir detalles" y guardar.
  3. Salir de Scale y abrir el historial.
  4. Filtrar por el café.
  5. Abrir el detalle.
  6. `am kill` y volver a abrir.
  7. Borrar y deshacer.
- [x] Las interacciones verificadas se plasman en los tests instrumentados.
- [x] `./gradlew :app:testDebugUnitTest`, los tests instrumentados y `./gradlew :app:lintDebug` pasan
      sin avisos nuevos.

## 6. Preguntas abiertas

1. **Versión de Room y KSP.** Hay que confirmar en el plan qué versiones son compatibles con AGP
   9.0.1 (Kotlin integrado) y Kotlin 2.3.20, y cómo se aplica el plugin de KSP en ese escenario.
   *Resuelto en D1 (§3.9): Room 2.8.5 sin `room-ktx` y KSP 2.3.12.*
2. **Heredadas de SPEC-001 §6.** La calibración con la DOT física cambiará las constantes. Al
   hacerlo, hay que incrementar `BrewConfig.METRIC_VERSION` y decidir en otro spec si se recalculan
   las preparaciones guardadas.
3. **Comportamiento del temporizador de la DOT** ante `pauseTimer` y `resetTimer` enviados desde la
   app (SPEC-001 §6.3). Está sin verificar con hardware.

## 7. Alternativas descartadas

- **Guardado manual con botón "Guardar":** lo que no se guardara se perdería al rearmar o salir.
- **Guardado automático con filtro de sesiones triviales:** se prefiere guardarlo todo y poder borrar.
- **Barra inferior de navegación o acceso solo desde Scan:** el icono en las top bars no cambia la
  navegación actual y cumple el objetivo de toques.
- **Datos manuales solo en el historial, o elegidos antes de verter:** se prefiere poder rellenarlos
  justo al terminar y también después.
- **Catálogo de cafés:** más alcance; el texto con sugerencias basta para filtrar.
- **Todo como texto libre, o molienda dividida en molinillo y ajuste numérico:** se eligió una
  lista fija para el método, texto libre para la molienda y un número para la temperatura.
- **Módulo `:history` aparte:** se sigue el patrón de `brew` en SPEC-001 (paquete dentro de `:app`).
- **Tabla de muestras (una fila por muestra):** hasta 9000 filas por preparación; el BLOB
  comprimido ocupa mucho menos y se lee de una vez.
- **Recalcular el resumen al abrir:** las puntuaciones antiguas cambiarían al recalibrar.
- **No guardar las sesiones simuladas, o guardarlas sin distinción:** impediría probar sin la DOT,
  o ensuciaría la gráfica de progreso.
- **Varias métricas o pantalla propia de progreso:** se empieza por la estabilidad encima de la lista.
- **Filas sin minicurva:** la curva ayuda a reconocer una preparación de un vistazo.
- **Borrado con diálogo o con selección múltiple:** "Deshacer" es más rápido y igual de seguro.
- **Autoguardado en la edición:** se prefiere un botón "Guardar" explícito con diálogo de descarte.
- **Mostrar siempre en gramos o con una preferencia global:** se usa la unidad de la preparación.
- **P1, reiniciar al empezar o dejarlo como está:** con "reiniciar al empezar", el reloj seguiría
  contando tras terminar. Dejarlo como está mantenía el desajuste entre el reloj y la gráfica.
- **P1, enviar `resetTimer` en vez de `pauseTimer` al terminar por inactividad:** se acepta el
  desfase del reloj y se mantiene un único comportamiento al terminar.
- **Excluir la base de datos de la copia de Android o limitar el número de preparaciones:** con la
  curva comprimida no hace falta.
- **Comparar dos preparaciones y exportar:** fuera de SPEC-002; comparar pasa a SPEC-003.
- **Dejar el temporizador como está al iniciar desde `PAUSED`:** el reloj seguiría desde el valor
  anterior, desajustado con la gráfica.
- **Filtrar el café sin distinguir mayúsculas, guardando cada variante:** las variantes seguirían
  en la base de datos. Se prefiere reutilizar la grafía existente al guardar.
- **Guardar la copia para deshacer en el ViewModel del historial:** el borrado ocurre en el
  detalle, que tiene otro ViewModel, y Navigation 3 no devuelve resultados.
- **Detener los comandos de un evento si uno falla:** un `resetTimer` fallido no debe impedir
  `startTimer`.

## 8. Plan de implementación por fases

1. **Dependencias y base de datos:** Room y KSP, `HistoryDatabase`, entidades, `TypeConverter`, DAO y
   `BrewRepository`, con `BrewDaoTest`. Se incluye el esquema exportado.
2. **Codificación:** `BrewCurveCodec` y `BrewPreview`, con sus tests JVM.
3. **Sesión y ViewModel:** nuevo contrato de efectos en `BrewSession` (`Finished`, `PauseTimer` y
   `ResetTimer`), guardado en `applicationScope`, `savedBrew` y `onCleared()`, con tests JVM.
4. **Componentes:** `AntSelect`, `AntAutoComplete`, acción en `AntMessage` y el icono
   `AntIcons.History`.
5. **Pantallas:** historial (filtro, progreso y lista), detalle y edición, la navegación y
   "Añadir detalles" en el resumen. Textos en es/en y tests de Compose.
6. **Verificación:** E2E por adb, `HistoryPerformanceTest` y checklist de §5.
