# Plan de implementación: SPEC-002 Historial de preparaciones

| Campo | Valor |
|---|---|
| Spec | [`spec-historial.md`](spec-historial.md) (SPEC-002) |
| Estado | Completado (F0–F6). Rendimiento medido: lista 183 ms, detalle 149 ms, `history-perf.db` 3,5 MB |
| Fecha | 2026-11-12 |
| Rama | `feat/brew-history`, creada desde `docs-specs-derived-from` (`383d5a2`). `develop` aún no tiene SPEC-001 |
| Módulos | `:app` (`:protocol` no cambia) |

Este plan baja el §8 del spec a archivos, tareas, pruebas y comandos. Antes de las fases recoge las
decisiones que el spec deja abiertas (§2). Varias cambian contratos del spec (D2, D3, D7 y D12), así
que conviene revisarlas antes de empezar la fase 1.

## 1. Punto de partida

**Se reutiliza:**
- `BrewSession` ya devuelve `List<BrewEffect>` en `onWeight`. El resto de métodos devuelve `Unit`,
  así que hay que cambiarles la firma.
- `finishWith(reason, totalTimeMs)` es el único punto de fin. Ahí se emiten `Finished` y `PauseTimer`.
- `ScaleViewModel.send(frame)` publica los fallos en `errors`, pero lanza una corrutina por trama. Para
  garantizar el orden `resetTimer` → `startTimer` hace falta un ejecutor secuencial (§3.5 del spec).
- `PourlineApp` ya es el contenedor de singletons (`brewSettings`). Ahí van la base de datos, el
  repositorio y `applicationScope`.
- `BrewChart(samples, unit, …)`, con `testTag("brew_chart")`, y `BrewSummaryCard(summary, unit)` sirven
  tal cual para el detalle. La función privada `Chart` de `BrewChartCard.kt` (que construye la
  `contentDescription`) y `summaryStability` pasan a `internal`.
- `BrewChartColors.flowColor(stability)` da el color por nivel de la gráfica de progreso.
- Componentes: `AntCard`, `AntTag`, `AntEmpty`, `AntAlert`, `AntInput`, `AntButton`, `AntIconButton`,
  `AntTopBar(actions = …)`, `AntMessageHost`, `AntListItem` y `AntSegmented`.
- Patrón de tests: `FakeScale` (`BrewTestFixtures.kt`), `FakeConnection` (`ScaleViewModelBrewTest`),
  y `createComposeRule` más `targetContext.getString` (`BrewChartTest`, `SettingsNavigationTest`).

**Falta o hay que corregir:**
- No hay Room ni KSP en `libs.versions.toml`. En la caché local están Room 2.8.5 y KSP 2.3.12.
- `ScaleViewModel` no tiene forma de guardar y su `onCleared()` solo desconecta. Cuando se llama a
  `onCleared()`, `viewModelScope` ya está cancelado, así que el guardado debe ir en `applicationScope`.
- `errors` es un `Channel<String>` con textos fijos en español ("Error al enviar el comando"). Un
  error de guardado necesita un texto en es/en.
- `AntInput` tiene la altura fija (`height(controlHeight)`): no sirve para las notas multilínea.
- `AntMessageVisuals.actionLabel` es siempre `null`. No hay `AntSelect`, `AntAutoComplete`, diálogo
  modal ni icono de historial.
- La top bar de Scale está dentro de `ScaleScreen`. Hay que extraerla para `ScaleTopBarTest`.
  `ThemeModeButton` no se dibuja sin `LocalThemeSettings`, así que el test debe proveerlo.
- `kotlinx-coroutines-test` solo está en `testImplementation`. `BrewDaoTest` lo necesita en
  `androidTestImplementation`.

## 2. Decisiones que completan el spec

Aprobadas junto con el plan. Al terminar la implementación se pasan al spec como §3.9 (igual que en
SPEC-001).

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

## 3. Fases

Orden: F0 → F1 → (F2 ∥ F3) → F4 → F5 → F6.

| Fase | Contenido | Tamaño |
|---|---|---|
| F0 | Preparación: rama, línea base de lint y tests en verde | S |
| F1 | Lógica pura: tipos nuevos, códec, minicurva y validación del formulario | M |
| F2 | Room: dependencias, base de datos, DAO, repositorio y singletons | M |
| F3 | Contrato de efectos de `BrewSession` y guardado en `ScaleViewModel` | M |
| F4 | Componentes: `AntSelect`, `AntAutoComplete`, `AntModal`, acción en `AntMessage`, `AntInput` multilínea e icono | M |
| F5 | Pantallas: historial, detalle, edición, navegación y "Añadir detalles" | L |
| F6 | Verificación: E2E por adb, tests instrumentados, rendimiento y checklist del §5 | M |

Commits: uno por fase, en Conventional Commits y en inglés, solo cuando se pidan. Se propone el
mensaje al final de cada fase.

### F0. Preparación

1. Crear `feat/brew-history` desde `docs-specs-derived-from`.
2. Ejecutar `./gradlew :app:testDebugUnitTest` y `./gradlew :app:lintDebug`. Anotar el número de avisos
   y errores (línea base para "sin avisos nuevos").

### F1. Lógica pura

**Archivos:**

| Archivo | Contenido |
|---|---|
| `brew/BrewModels.kt` | `BrewEffect` gana `PauseTimer`, `ResetTimer` y `Finished(brew)`. Nuevo `FinishedBrew(t0MonoMs, samples, summary, doseG)` (§3.5 del spec). |
| `brew/BrewConfig.kt` | `companion object { const val METRIC_VERSION = 1 }`, con un KDoc sobre cuándo incrementarla. |
| `history/HistoryModels.kt` | `BrewMethod`, `BrewFilter`, `ProgressPoint`, `BrewContext`, `BrewDetails(name, coffee, method, methodOther, grind, waterTempC, notes)` con `normalized()` (recorta, vacío → `null`, y `methodOther` solo con `OTHER`), `BrewSaver` (D2) y `canonicalCoffee` (D9). `BrewListItem`, `BrewRecord` y `DeletedBrew` dependen de las entidades y van en F2. |
| `history/BrewCurveCodec.kt` | `object BrewCurveCodec { fun encode(samples, pours): ByteArray; fun decode(bytes): Pair<List<BrewSample>, List<Pour>> }` y `class BrewCurveFormatException`. Usa `Deflater` e `Inflater`, byte de versión 1 y las reglas D3 y D4. |
| `history/BrewPreview.kt` | `object BrewPreview { const val SIZE = 48; fun compute(samples, totalTimeMs, totalWaterG): ByteArray; fun decode(bytes): List<Float> }` con la regla D5. |
| `ui/history/BrewEditForm.kt` | Kotlin puro: `data class BrewEditForm` (los textos tal como se escriben), `parseWaterTemp(text): TempResult` (`Empty`, `Valid(Double)`, `Invalid`; acepta `,` y `.`, de 0 a 100, un decimal como máximo), `isDirty(initial)` y `toDetails()`. |

`history/BrewCurveCodec.kt` y `history/BrewPreview.kt` no importan `android.*` ni `androidx.*`.

**Tests** (`app/src/test/kotlin/dev/josueamaya/pourline/`):

| Archivo | Casos |
|---|---|
| `history/BrewCurveCodecTest.kt` | Ida y vuelta con muestras válidas e inválidas, `stability` nula y no nula, y vertidos. `tMs`, `stability`, `valid` y los recuentos son exactos. Error ≤ 0,005 en `waterG` y `flowGps` con 9000 muestras (sin acumulación). Una sesión en onzas convertida a gramos con `FakeScale(unit = OUNCES)`. Lista vacía. BLOB truncado, bytes aleatorios y versión 2 → `BrewCurveFormatException`. |
| `history/BrewPreviewTest.kt` | 48 valores, máximo por tramo, normalización a 0-255, tramo vacío que repite el anterior, `totalWaterG = 0` → ceros, y `decode` a 0..1. |
| `history/HistoryModelsTest.kt` | `canonicalCoffee` ("etiopía " → "Etiopía"; sin coincidencia, se queda como está; vacío → `null`) y `BrewDetails.normalized()`. |
| `ui/history/BrewEditFormTest.kt` | Temperatura: "92", "92,5", "92.5", "100" y "0" son válidas; "100,1", "-1", "92,55", "abc" y "1e2" no. `isDirty` y `toDetails`. |

**Verificación:** `./gradlew :app:testDebugUnitTest --tests "dev.josueamaya.pourline.history.*"` y
`grep -n "import android" app/src/main/kotlin/dev/josueamaya/pourline/history/BrewCurveCodec.kt app/src/main/kotlin/dev/josueamaya/pourline/history/BrewPreview.kt`
no devuelve nada.

Commit sugerido: `feat(history): add brew curve codec and preview`.

### F2. Room y repositorio

**Gradle:**
- `gradle/libs.versions.toml`: `room = "2.8.5"` y `ksp = "2.3.12"`, las librerías
  `androidx-room-runtime` y `androidx-room-compiler`, y los plugins `ksp` y `androidx-room`.
- `build.gradle.kts` (raíz): los dos plugins con `apply false`.
- `app/build.gradle.kts`:
  - Aplicar los plugins `ksp` y `androidx-room`.
  - `room { schemaDirectory("$projectDir/schemas") }`.
  - `implementation(libs.androidx.room.runtime)` y `ksp(libs.androidx.room.compiler)`.
  - `androidTestImplementation(libs.kotlinx.coroutines.test)`.

**Archivos** (`app/src/main/kotlin/dev/josueamaya/pourline/history/`):

| Archivo | Contenido |
|---|---|
| `BrewEntities.kt` | `BrewEntity` (tabla `brews`, columnas del §3.2, `@PrimaryKey(autoGenerate = true)` e índice en `startedAtEpochMs`). `BrewCurveEntity` (`brew_curves`, `brewId` PK y FK con `CASCADE`). `BrewListRow` (proyección de la lista con `preview: ByteArray`). `BrewListItem`, `BrewRecord` (con `curveError`, D6) y `DeletedBrew(entity, curve)`. |
| `HistoryConverters.kt` | `@TypeConverter` por nombre para `BrewMethod`, `StabilityLevel`, `FinishReason`, `WeightUnit` y `ScaleModel`. |
| `BrewDao.kt` | `abstract class` con `list(coffee, method): Flow<List<BrewListRow>>` (`ORDER BY startedAtEpochMs DESC, id DESC`), `progress(...)` (`simulated = 0 AND stabilityScore IS NOT NULL`, en orden cronológico), `coffees()` (`GROUP BY coffee ORDER BY MAX(startedAtEpochMs) DESC`), `methods()`, `observeBrew(id)`, `brew(id)`, `curve(id)`, `coffeesExcept(id)`, inserciones y borrado. Métodos `@Transaction`: `insertBrew(brew, curve)`, `applyDetails(id, details)` (D9) y `restore(deleted)`. |
| `HistoryDatabase.kt` | `@Database(entities = [...], version = 1, exportSchema = true)` con `create(context)` (`history.db`) y `inMemory(context)` para los tests. Sin `fallbackToDestructiveMigration`. |
| `BrewRepository.kt` | Contrato del §3.3 más `observe(id)` (D7). Implementa `BrewSaver`. `save` codifica y calcula la minicurva en `Dispatchers.Default` y escribe en una transacción. `list` mapea `BrewListRow` a `BrewListItem` (decodifica la minicurva). `get` y `observe` reconstruyen `BrewSummary` (los `levelShare` salen de las columnas `share*` y los vertidos, de la curva). `methods()` se ordena por el orden del enum. Aplica D8. |

**Singletons** (`PourlineApp.kt`):
- `val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)`.
- `val historyDatabase by lazy { HistoryDatabase.create(this) }`.
- `val brewRepository by lazy { BrewRepository(historyDatabase) }`.

**Esquema:** `app/schemas/dev.josueamaya.pourline.history.HistoryDatabase/1.json` se genera al compilar y se
añade al repositorio.

**Tests:** `app/src/androidTest/kotlin/dev/josueamaya/pourline/history/BrewDaoTest.kt`, con Room en memoria y
`runTest`. Casos del §5 del spec:
- Insertar y leer.
- `list` sin la curva y con la más reciente primero.
- Filtro por café y por método, incluido que `OTHER` agrupa todos los `methodOther`.
- `progress` sin simuladas ni puntuaciones nulas.
- `coffees()` sin duplicados y por uso más reciente.
- `updateDetails`: "etiopía" se guarda como "Etiopía" y la corrección de grafía en la única preparación (D9).
- `delete` con cascada a `brew_curves` y `pendingUndo` relleno. `restore` con el mismo id y la curva idéntica.
- `get` de un BLOB corrupto → `curveError`.

Un helper compartido `androidTest/.../history/TestBrews.kt` genera `FinishedBrew` sintéticas y
deterministas (con ruido de ±0,05 g para no sobrestimar la compresión). Lo usan también F5 y F6.

**Verificación:** `./gradlew :app:compileDebugKotlin` (KSP genera el DAO y el esquema) y
`./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.josueamaya.pourline.history.BrewDaoTest`.

Commit sugerido: `feat(history): add Room database and brew repository`.

### F3. Sesión y ViewModel

**`brew/BrewSession.kt`** (§3.5 del spec y D11):
- `onTimer`, `onTared`, `onDisconnected`, `onTick` y `finish` devuelven `List<BrewEffect>`.
- Nuevo campo `lastTimer`, que se actualiza al entrar en `onWeight(…, timer)` y en `onTimer(status)`.
- `startSession`:
  - `RESET` → `[StartTimer]`.
  - `PAUSED` → `[ResetTimer, StartTimer]`.
  - `RUNNING` → `[]`.
- `finishWith` añade `PauseTimer` si el motivo es `INACTIVITY`, `MANUAL` o `MAX_DURATION` y
  `lastTimer == RUNNING`. Siempre añade `Finished(FinishedBrew(t0, snapshot, summary, doseG))`.
- `onTared` en `Finished` añade `ResetTimer` si `lastTimer != RESET`.
- Los efectos que se generan dentro de `finishWith` se acumulan en una lista local del evento, que
  devuelve el método público.

**`ui/scale/ScaleUiState.kt`:** `sealed interface SavedBrew { Saving; Saved(id); Failed }` y el campo
`savedBrew: SavedBrew? = null`.

**`ui/scale/ScaleViewModel.kt`:**
- Constructor:
  `ScaleViewModel(connection, autoFinishSeconds, saver: BrewSaver, applicationScope: CoroutineScope, clock = SystemClock::elapsedRealtime, wallClock = System::currentTimeMillis)`.
- `handle(effects)`:
  - Los efectos de temporizador se traducen a tramas y se envían en una sola corrutina, en orden. Cada
    fallo va a `errors` y se sigue con la siguiente trama.
  - `Finished` llama a `save(brew)`.
  - Se llama desde el colector de mensajes, el de conexión (`onDisconnected`), el tick y `finishBrew()`.
- `save(brew)`:
  1. `startedAtEpochMs = wallClock() − (clock() − brew.t0MonoMs)`.
  2. `BrewContext(ui.unit, ui.model, ui.isSimulated)`.
  3. Token (D10) y `savedBrew = Saving`.
  4. `applicationScope.launch { saver.save(...) }` → `Saved(id)`, o `Failed` + `ScaleError.SaveFailed`.
  Si la corrutina se cancela, la excepción se relanza.
- `errors` pasa a `Channel<ScaleError>` (D12). `ScaleScreen` traduce `Command` y `SaveFailed`.
- `onCleared()`: `session.finish(clock()).filterIsInstance<BrewEffect.Finished>().forEach(::save)` y
  después `connection.disconnect()`.

**`Navigation.kt`:** el `entry<Scale>` pasa `app.brewRepository` y `app.applicationScope`.

**Tests:**

| Archivo | Cambios |
|---|---|
| `brew/BrewTestFixtures.kt` | `FakeScale` acumula los efectos de todos los métodos y simula la báscula: aplica los efectos de temporizador a `timer` y devuelve la notificación con `onTimer`. Nuevos contadores `pauseTimerCount` y `resetTimerCount`, y `finishedBrews`. |
| `brew/BrewSessionTest.kt` | Los casos actuales se adaptan a las firmas nuevas. Se añade: `Finished` exactamente una vez con cada uno de los seis motivos, con muestras recortadas y `t0MonoMs`. `PauseTimer` solo con `INACTIVITY`, `MANUAL` y `MAX_DURATION` y el temporizador `RUNNING`. Sin `PauseTimer` con `TIMER_PAUSED`, `TIMER_RESET` o `DISCONNECTED`. Tara que rearma → `ResetTimer`, salvo si ya está `RESET`. `Timer(RESET)` que rearma → nada. Inicio desde `PAUSED` → `[ResetTimer, StartTimer]` en ese orden. Inicio con `RUNNING` → nada. |
| `ui/scale/ScaleViewModelBrewTest.kt` | `FakeSaver` (registra las llamadas y puede suspender con un `CompletableDeferred` o fallar) y `applicationScope` sobre el `TestCoroutineScheduler`. Se crea con `ViewModelProvider.create(store, viewModelFactory { … })` para poder llamar a `store.clear()` (dispara `onCleared()` sin API restringida). Casos: un vertido terminado llama a `save` una vez con el `BrewContext` correcto y `startedAtEpochMs` del reloj de pared. `savedBrew` pasa por `Saving` y `Saved`. Un fallo llega a `errors` como `SaveFailed` y deja `Failed`. `store.clear()` con una sesión activa guarda con `MANUAL`. Con la sesión terminada no se guarda otra vez. Una sesión nueva durante un guardado lento deja `savedBrew == null` al terminar el anterior. Inicio desde `PAUSED` → la conexión recibe `resetTimer` y después `startTimer`. Si `resetTimer` falla, `startTimer` se envía igualmente y el error llega a `errors`. |
| `androidTest/.../ui/BrewChartTest.kt` | Se adapta al nuevo constructor con un `BrewSaver` falso. |

**Verificación:** `./gradlew :app:testDebugUnitTest`.

Commit sugerido: `feat(scale): save finished brews and drive the scale timer on finish`.

### F4. Componentes (`ui/components/`)

| Archivo | Cambio |
|---|---|
| `AntIcons.kt` | `History`: reloj con flecha antihoraria, trazo propio (sin recursos de TIMEMORE). |
| `AntMessage.kt` | `AntMessageVisuals(message, type, duration, actionLabel: String? = null)`. `showAntMessage(…, actionLabel)` devuelve `SnackbarResult`. `AntMessageHost` dibuja la acción como `AntButton(type = Link)` y llama a `data.performAction()`. |
| `AntInput.kt` | `minLines: Int = 1`. Con `singleLine = false` usa `heightIn(min = controlHeight)`, padding vertical y el placeholder arriba. |
| `AntSelect.kt` (nuevo) | `AntSelectOption<T>(value, label)` y `AntSelect(options, selected, onSelect, label, modifier, placeholder, enabled)`. `ExposedDropdownMenuBox` con un ancla de solo lectura con aspecto de `AntInput`, y un icono `Down` que gira al abrirse. El menú usa `bgElevated`, `radius.lg` y la opción elegida en `primary.bg`. |
| `AntAutoComplete.kt` (nuevo) | `AntAutoComplete(value, onValueChange, suggestions, modifier, placeholder)`. `AntInput` como ancla editable. Filtra por prefijo sin distinguir mayúsculas y se oculta si no hay coincidencias o si el texto ya coincide con una sugerencia. |
| `AntModal.kt` (nuevo) | `AntModal(title, text, confirmText, cancelText, onConfirm, onDismiss, danger)` sobre `Dialog`, con `AntCard` y botones Ant (D13). |
| `AntComponentsPreview.kt` | Previews de `AntSelect`, `AntAutoComplete`, `AntModal`, `AntMessage` con acción y `AntInput` multilínea. |

Accesibilidad: `AntSelect` y `AntAutoComplete` exponen la etiqueta del campo y `Role.DropdownList`. El
icono de historial siempre va con `contentDescription`.

**Verificación:** `./gradlew :app:compileDebugKotlin`, revisión de las previews en claro y oscuro, y
`./gradlew :app:lintDebug`.

Commit sugerido: `feat(ui): add select, autocomplete, modal and message action components`.

### F5. Pantallas y navegación

**Navegación:**
- `NavigationKeys.kt`: `History`, `BrewDetail(id)` y `BrewEdit(id)` (§3.6 del spec).
- `Navigation.kt`:
  - Tres `entry` nuevas con sus ViewModels: `viewModel { HistoryViewModel(repo) }`, etc.
  - `onOpenHistory = { backStack.add(History) }` en Scan y en Scale.
  - `onAddDetails = { id -> backStack.add(BrewEdit(id)) }` en Scale.

**Archivos** (`app/src/main/kotlin/dev/josueamaya/pourline/ui/history/`):

| Archivo | Contenido |
|---|---|
| `HistoryFormatters.kt` | `formatBrewDateTime(epochMs, locale)` y `formatBrewDate` (D15), y `BrewMethod.label()` (D15). |
| `HistoryViewModel.kt` | `setFilter(filter)`. `items`, `progress`, `coffees` y `methods` como `StateFlow` (`stateIn(WhileSubscribed(5_000))`, los dos últimos con `null` inicial, D14). `pendingUndo`, `restore(deleted)` y `clearPendingUndo(deleted?)`. |
| `HistoryScreen.kt` | `AntTopBar` "Historial" con flecha atrás. Dos `AntSelect` con "Todos" (`LaunchedEffect` que vuelve a "Todos" si el valor desaparece de una lista ya cargada). `StabilityProgressChart` o el texto de menos de 2 puntos. `LazyColumn` de `HistoryRow` (`testTag("history_row")`, `key = id`) y los `AntEmpty` de lista vacía y de filtro sin resultados. Deshacer: `LaunchedEffect(pendingUndo)` muestra `AntMessage` con la acción "Deshacer" (`SnackbarDuration.Short`). Si se pulsa, llama a `restore`. Si no, a `clearPendingUndo(deleted)`. `DisposableEffect` lo vacía al salir. |
| `StabilityProgressChart.kt` | `Canvas` con X por índice (intervalos iguales) e Y de 0 a 100. Líneas de referencia en 75 y 50 con el color `split`. Puntos con `flowColor(score)` y las fechas de la primera y la última preparación. La `contentDescription` lleva el número de preparaciones, la media y la última. |
| `HistoryRow.kt` | Una `AntCard` pulsable: `BrewPreviewLine` (`Canvas` decorativo en `primary`, sin semántica), nombre o fecha y hora, fecha, café, agua y tiempo totales en la unidad guardada, `1:x` si hay ratio, `AntTag` de nivel ("Estable · 82", con `levelText`) o "Sin puntuación", y `AntTag` "Simulada". |
| `BrewDetailViewModel.kt` | Observa `repository.observe(id)` como `Loading`, `Loaded(record)` o `Gone`. `delete()`. |
| `BrewDetailScreen.kt` | `AntTopBar` con el nombre. `Gone` → `onBack()`. `BrewChart` (vía la `Chart` interna) con la unidad guardada. `AntAlert` si `curveError`. `BrewSummaryCard` sin botón. Tarjeta "Detalles" que omite los campos vacíos. `AntTag` "Simulada". Aviso de `metricVersion`. Botones "Editar" y "Borrar" (este último con `danger`). |
| `BrewEditViewModel.kt` | Carga `get(id)` en `BrewEditForm`, con el `initial` aparte. `null` → `Gone`. `save()` llama a `updateDetails`, y después `Saved` → la pantalla vuelve atrás. Expone `coffees()` para las sugerencias. |
| `BrewEditScreen.kt` | Campos del §3.6 del spec. Temperatura con `KeyboardType.Decimal` y estado `Error` si no es válida. "Guardar" deshabilitado si hay un error o se está guardando. `BackHandler(enabled = dirty)` y la flecha de la top bar abren `AntModal` ("Descartar" / "Seguir editando"). |

**Cambios en Scan y Scale:**
- `ScanScreen.kt`: el parámetro `onOpenHistory` y `AntIconButton(AntIcons.History)` antes del engranaje.
- `ScaleScreen.kt`:
  - Se extrae `internal fun ScaleTopBar(title, subtitle, batteryPercent, onBack, onOpenHistory, onOpenSettings)`.
  - Se añaden `onOpenHistory` y `onAddDetails`.
  - Los errores se traducen como indica D12.
- `BrewSummaryCard.kt`: parámetros opcionales `savedBrew: SavedBrew? = null` y `onAddDetails: (Long) -> Unit = {}`. El botón "Añadir detalles" (`testTag("brew_add_details")`) está deshabilitado con `Saving`, habilitado con `Saved` y oculto con `Failed` o `null`.
- `BrewChartCard.kt`: `Chart` y `summaryStability` pasan a `internal`.

**Textos:** los prefijos `history_`, `brew_detail_`, `brew_edit_`, `brew_method_other`,
`brew_summary_add_details`, `history_open` y `scale_error_*` en `values/strings.xml` y
`values-en/strings.xml`.

**Tests de Compose** (`app/src/androidTest/kotlin/dev/josueamaya/pourline/ui/history/`, con Room en memoria y
`TestBrews`):

| Archivo | Casos |
|---|---|
| `HistoryScreenTest.kt` | Estado vacío, filas en orden, filtro por café y por método, vuelta a "Todos" al borrar la última preparación del café filtrado, y la gráfica oculta con menos de 2 puntos. |
| `BrewDetailScreenTest.kt` | `brew_chart` y `brew_summary` visibles, la tarjeta de detalles, y borrar → vuelta atrás. `HistoryScreen` con "Deshacer" → la fila vuelve con el mismo id. |
| `BrewEditScreenTest.kt` | Temperatura "120" → error y "Guardar" deshabilitado. Guardar → los datos se persisten. Atrás con cambios → `AntModal`. "Seguir editando" se queda y "Descartar" sale. Atrás sin cambios sale directamente (con `Espresso.pressBack()`). |
| `ScaleTopBarTest.kt` | 360 dp de ancho, título de 40 caracteres con elipsis y `LocalThemeSettings` provisto. Batería, historial, ajustes y tema siguen visibles y pulsables. |

**Verificación:** `./gradlew :app:compileDebugKotlin`, `./gradlew :app:lintDebug` (sin
`MissingTranslation`) y los cuatro tests anteriores con `connectedDebugAndroidTest`.

Commit sugerido: `feat(history): add history, brew detail and edit screens`.

### F6. Verificación

1. **Dispositivo.** Ejecutar `adb devices -l`. Si hay más de uno, preguntar cuál usar. Después,
   `./gradlew :app:installDebug`. *No se usa ARTEMIS.*
2. **E2E por adb** (`uiautomator dump`, `input` y `screencap`) con la báscula simulada:
   - Antes: `adb shell pm clear dev.josueamaya.pourline` y `pm grant` de `BLUETOOTH_SCAN` y `BLUETOOTH_CONNECT`.
     "Fin automático" se pone en 30 s desde Ajustes.

   | Paso | Recorrido | Comprobaciones |
   |---|---|---|
   | 1 | Scale → "Simular vertido estable" → esperar el fin | Resumen visible. El reloj de la tarjeta de peso se detiene (`pauseTimer`). "Añadir detalles" pasa de deshabilitado a habilitado |
   | 2 | "Añadir detalles" → café, método, temperatura y notas → "Guardar" | Vuelta a Scale |
   | 3 | "Tara" → simular otro vertido → "Terminar" | Tras la tara el reloj vuelve a 0:00 (`resetTimer`). El vertido arranca el reloj desde 0:00 |
   | 4 | Atrás hasta Scan → icono de historial | Dos filas, con minicurva, la más reciente primero, la etiqueta "Simulada" y la gráfica sustituida por el texto (las simuladas no puntúan) |
   | 5 | Filtrar por el café | Solo queda la fila del paso 2 |
   | 6 | Abrir la fila | Gráfica, resumen y detalles (dos toques desde Scan) |
   | 7 | Inicio → `adb shell am kill dev.josueamaya.pourline` → abrir de nuevo | El historial conserva las dos preparaciones |
   | 8 | Detalle → "Borrar" → "Deshacer" | La fila vuelve |
   | 9 | Scale → simular vertido → atrás antes del fin → historial | Aparece con el motivo "Manual" (`onCleared`) |

3. **`HistoryPerformanceTest`** (`androidTest`, emulador con API 36 o superior):
   - Base de datos en archivo (`history-perf.db`) con 500 preparaciones de 4 min creadas con `TestBrews`.
   - La lista se mide desde `setContent` hasta el primer `history_row`. El detalle, desde el clic
     hasta `brew_chart`.
   - Una apertura de calentamiento y la mediana de 5.
   - Tamaño de `.db`, `-wal` y `-shm` < 10 MB. Se borran en `@After`.
4. **Ejecutar:** `./gradlew :app:testDebugUnitTest`, `./gradlew :app:connectedDebugAndroidTest` y
   `./gradlew :app:lintDebug` comparado con la línea base de F0.
5. **Cierre:** marcar el checklist del §5 del spec, pasar D1–D17 al spec (§3.9) y marcar este plan
   como completado.

Commit sugerido: `test(history): add history performance test and verify the history flow`.

## 4. Trazabilidad con el §5 del spec

| Criterio | Fase | Prueba |
|---|---|---|
| Toda sesión terminada se guarda sola | F3 | `ScaleViewModelBrewTest`, E2E 1 |
| Salir de Scale con sesión activa guarda `MANUAL` | F3 | `ScaleViewModelBrewTest` (`store.clear()`), E2E 9 |
| Sobrevive a `am kill` | F2 | E2E 7 |
| Dos toques desde Scan | F5 | E2E 4 y 6 |
| "Añadir detalles" → edición → detalle y fila | F5 | `BrewEditScreenTest`, E2E 2 y 6 |
| Filtro por café y método en lista y progreso | F2, F5 | `BrewDaoTest`, `HistoryScreenTest`, E2E 5 |
| Progreso sin simuladas ni nulas; oculto con < 2 | F2, F5 | `BrewDaoTest`, `HistoryScreenTest`, E2E 4 |
| Detalle en la unidad guardada | F5 | `BrewDetailScreenTest` (preparación en onzas) |
| Borrar y "Deshacer" con el mismo id y la curva | F2, F5 | `BrewDaoTest`, `BrewDetailScreenTest`, E2E 8 |
| Diálogo de descarte solo con cambios | F5 | `BrewEditScreenTest` |
| `pauseTimer`, `resetTimer` y `startTimer` una vez | F3 | `BrewSessionTest`, `ScaleViewModelBrewTest`, E2E 1 y 3 |
| Inicio desde `PAUSED`: `resetTimer` y después `startTimer` | F3 | `BrewSessionTest`, `ScaleViewModelBrewTest` |
| Textos en `values/` y `values-en/` | F5 | `lintDebug` (`MissingTranslation`) |
| Rendimiento (< 1 s, < 500 ms, < 10 MB) | F6 | `HistoryPerformanceTest` |
| Códec, minicurva, sesión y ViewModel | F1, F3 | Tests JVM del §5 |
| Sin avisos nuevos de lint | F0, F6 | `lintDebug` frente a la línea base |

## 5. Riesgos

| ID | Riesgo | Mitigación |
|---|---|---|
| R1 | El plugin de Room o KSP falla con el Kotlin integrado de AGP 9.0.1 o con la configuration cache. | KSP ≥ 2.3.10 (D1). Si el plugin `androidx.room` falla, usar `ksp { arg("room.schemaLocation", …) }` sin el plugin. Comprobarlo al principio de F2 con `compileDebugKotlin`. |
| R2 | `BackHandler` dentro de una entrada de Navigation 3 podría no tener prioridad sobre el `onBack` de `NavDisplay`. | `BrewEditScreenTest` lo prueba con `Espresso.pressBack()`. Alternativa: `NavigationBackHandler` de `navigationevent-compose`, que ya llega como dependencia de Navigation 3. |
| R3 | Cerrar desde recientes no garantiza `onCleared()`. | Aceptado en el spec (§4). |
| R4 | No se sabe cómo responde el temporizador de la DOT a `pauseTimer` y `resetTimer` (§6.3 del spec). | En el simulador se verifica. Con hardware, revisar `adb logcat -s BleScale`. |
| R5 | Las medidas de rendimiento en el emulador son ruidosas. | Calentamiento y mediana de 5. Si falla por poco, repetir en frío antes de optimizar. |
| R6 | Una minicurva o una curva mal formada de una versión futura. | Byte de versión en el códec (D3, D4) y `curveError` (D6). |

## 6. Seguimiento fuera de este plan

- §6.2 del spec: al calibrar con la DOT, incrementar `METRIC_VERSION` y decidir en otro spec si se
  recalculan las preparaciones guardadas.
- SPEC-003 (curva objetivo) leerá las curvas con `BrewRepository.get` y `BrewCurveCodec`.
- Fusionar `feat/brew-chart` en `develop` antes que `feat/brew-history`.
