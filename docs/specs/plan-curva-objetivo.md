# Plan de implementación: SPEC-003 Curva objetivo, recetas y comparación

| Campo | Valor |
|---|---|
| Spec | [`spec-curva-objetivo.md`](spec-curva-objetivo.md) (SPEC-003) |
| Estado | Completado (F0–F7). Decisiones de §2 incorporadas al spec (§3.12). Rendimiento: comparación 224 ms (mediana), lista 212 ms, detalle 201 ms, `history-perf.db` 4,4 MiB |
| Fecha | 2026-11-13 |
| Rama | `feat/target-curve`, creada desde `feat/brew-history` (`ebbd97d`). `develop` aún no tiene SPEC-001 ni SPEC-002 |
| Módulos | `:app` (`:protocol` no cambia) |

Este plan baja el §8 del spec a archivos, tareas, pruebas y comandos. Antes de las fases recoge las
decisiones que el spec deja abiertas (§2). Varias cambian contratos del spec (D2, D4, D5, D9, D11 y
D17), así que conviene revisarlas antes de empezar la fase 1.

## 1. Punto de partida

**Se reutiliza:**
- `BrewCurveCodec` ya tiene varint, zigzag, `Deflater`/`Inflater` y un `Reader` con control de
  recuentos. Esas piezas pasan a un `internal` compartido (`history/CodecIO.kt`) para que
  `TargetCurveCodec` no las duplique. `BrewCurveCodecTest` protege la extracción.
- `BrewRepository.observe(id)` (D7 de SPEC-002) decodifica la curva una sola vez por observación. Sirve
  para resolver una referencia de tipo preparación, para el detalle y para la comparación.
- El patrón `pendingUndo` + `clearPendingUndo(expected)` y las escrituras en
  `Dispatchers.IO + NonCancellable` (D8 de SPEC-002) se copian en `RecipeRepository`.
- `downsample`, `ChartAxis` y las piezas privadas de `BrewChart.kt` (`PlotArea`, rejilla, etiquetas,
  `segments()`) para la curva objetivo y para `CompareChart`.
- `Chart(samples, unit, stability)` y `summaryStability` (ya `internal`), `HistoryRow`, `FilterSaver` y
  los `LaunchedEffect` de "vuelve a Todos" de `HistoryScreen`.
- `parseWaterTemp` (`BrewEditForm.kt`) para la temperatura del editor de recetas, `BrewMethod.label()`
  y `formatBrewDateTime`.
- Componentes: `AntDrawer` (bottom sheet), `AntSegmented`, `AntSwitch`, `AntSelect`, `AntInput`
  (multilínea), `AntModal`, `AntMessage` con acción, `AntEmpty(action)`, `AntAlert`, `AntTag`,
  `AntButton` (ya recorta con elipsis).
- `AntPalette.generate` y `AntPalette.nearestAccessible` para el color de B en la comparación.
- Tests: `FakeScale` (`BrewTestFixtures.kt`), `FakeConnection` y `FakeSaver`
  (`ScaleViewModelBrewTest`), `TestBrews`, `HistoryDatabase.inMemory` y la metodología de
  `HistoryPerformanceTest`.
- El plugin `androidx.room` 2.8.5 ya copia los esquemas a los assets de `androidTest`
  (`copyRoomSchemasToAndroidTestAssets`, comprobado en el jar), así que `MigrationTestHelper` los
  encuentra sin tocar `sourceSets`. `room-testing-android` 2.8.5 está en la caché local.

**Falta o hay que corregir:**
- `BrewState` no expone t0: el ViewModel no puede calcular `tMs` para la guía.
- **Fallo actual:** `ScaleUiState.reduce(UnitChanged)` borra `dose`, pero `BrewSession` conserva la
  dosis anterior (`setDoseGrams` no se llama). El ratio final y, ahora, el factor de escala usarían una
  dosis obsoleta. Se corrige en F5.
- `ScaleViewModel` no tiene dependencias de objetivo, y `TargetSettings` y los repositorios necesitan
  Android. Hacen falta interfaces para probarlo en la JVM (D2).
- `HistoryDatabase` está en la versión 1, sin migraciones ni `Callback`. No hay `room-testing` en
  `libs.versions.toml`.
- `SimulatedScaleConnection` guarda un `SimulatedPour` concreto.
- `SettingsScreen` solo recibe `BrewSettings`. No hay permiso `VIBRATE`.
- `BrewSummaryCard` no tiene bloque de ajuste. `HistoryRow` no admite filas deshabilitadas.
- No hay icono de recetas en `AntIcons`.

## 2. Decisiones que completan el spec

Aprobadas junto con el plan. Al terminar la implementación se pasan al spec como §3.12 (como en
SPEC-001 y SPEC-002).

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

## 3. Fases

Orden: F0 → F1 → F2 → (F3 ∥ F4) → F5 → F6 → F7.

| Fase | Contenido | Tamaño |
|---|---|---|
| F0 | Preparación: rama, línea base de lint y tests en verde | S |
| F1 | Lógica pura: curva, guía, avisos, ajuste, recetas, conversión y códec | M |
| F2 | Base de datos v2: migración, recetas, `brew_targets`, repositorios | M |
| F3 | Simulador que sigue el objetivo | S |
| F4 | Recetas: pantallas, navegación, icono, `TargetSettings` y ajustes | L |
| F5 | Scale: ViewModel, gráfica, guía, selector, avisos y resumen | L |
| F6 | Historial: etiqueta de ajuste, detalle, selector de B y comparación | L |
| F7 | Verificación: E2E por adb, rendimiento, tests y checklist del §5 | M |

Commits: uno por fase, en Conventional Commits y en inglés, solo cuando se pidan. Se propone el
mensaje al final de cada fase.

### F0. Preparación

1. Crear `feat/target-curve` desde `feat/brew-history`. Los cambios de `docs/specs/` sin commitear (el
   spec y los enlaces de SPEC-001 y SPEC-002) viajan con la rama. Commit propuesto, cuando se pida:
   `docs(specs): add target curve spec and implementation plan (SPEC-003)`.
2. Ejecutar `./gradlew :app:testDebugUnitTest` y `./gradlew :app:lintDebug`. Anotar avisos y errores
   (línea base para "sin avisos nuevos").

### F1. Lógica pura

**Archivos** (`app/src/main/kotlin/dev/dotscale/`):

| Archivo | Contenido |
|---|---|
| `brew/TargetCurve.kt` | `TargetPoint`, `TargetStage`, `TargetCurve` (§3.3). `waterAt` con búsqueda binaria. `fromRecipe(recipe)` con rampas y mesetas. `fromBrew(samples, pours, totalTimeMs)` (D4): muestras válidas reducidas a una cada 250 ms, etapas desde los vertidos con `targetWaterG = waterAt(endMs)`, o una única etapa sin vertidos; `null` sin muestras válidas. `scaled(factor)`. |
| `brew/TargetConfig.kt` | Las seis constantes del §3.4, con un KDoc que remite a `BrewConfig.METRIC_VERSION`. |
| `brew/TargetGuide.kt` | `DeviationLevel`, `GuideState` (con `allStagesDone`, D5) y `TargetGuide.at(...)`. |
| `brew/StageAlertTracker.kt` | Clase pura con la regla del §3.4 y D6. |
| `brew/BrewFit.kt` | `BrewFit` y `brewFit(samples, totalTimeMs, target, config)`: rejilla de 100 ms hasta `W` y recorrido de las muestras con dos punteros. |
| `brew/StageNotifier.kt` | La interfaz de D3. |
| `recipe/Recipe.kt` | `Recipe` y `RecipeStage` (§3.2). |
| `recipe/RecipeValidator.kt` | `sealed interface RecipeError` (con el índice de etapa cuando aplica) y `validate(recipe)`. |
| `recipe/BuiltInRecipes.kt` | Claves `v60_classic` y `four_six`, dosis y etapas de fábrica. Los nombres se piden a `RecipeTexts`. |
| `recipe/RecipeTexts.kt` | La interfaz de D11. |
| `recipe/RecipeFromBrew.kt` | `RecipeDraft`, `Adjustment` (`MERGED_POURS(n)`, `CLAMPED_DURATIONS`, `MISSING_DOSE`) y la conversión de D16. |
| `ui/recipes/RecipeEditForm.kt` | Formulario puro (D15): parseo de cada campo, `toRecipe()`, errores por campo (parseo + `RecipeValidator`), `isDirty`, "Añadir etapa" y la regla de `localizedNames` (D12). |
| `history/CodecIO.kt` | Varint, zigzag, `Reader` e `inflate`, sacados de `BrewCurveCodec`. |
| `history/TargetCurveCodec.kt` | `TargetCurveCodec` y `TargetCurveFormatException` (§3.6), con byte de versión 1. |

Nada de lo anterior importa `android.*` ni `androidx.*`.

**Tests** (`app/src/test/kotlin/dev/dotscale/`): los del §5 del spec (`TargetCurveTest`,
`TargetGuideTest`, `StageAlertTrackerTest`, `BrewFitTest`, `RecipeValidatorTest`, `RecipeFromBrewTest` y
`TargetCurveCodecTest`). Además:
- `recipe/BuiltInRecipesTest.kt`: las dos pasan `validate`, con 250 g · 1:16,7 y 300 g · 1:15.
- `ui/recipes/RecipeEditFormTest.kt`: "1:30" y "90" → 90 000 ms; "1:75", "abc" y "16,45" no valen.
  Errores por campo. "Añadir etapa" propone fin + 30 s, agua + 50 g y 10 s, y se deshabilita con 10
  etapas. `localizedNames` cambia con un nombre o con una etapa de más, pero no con solo cantidades.
- `BrewCurveCodecTest` sigue en verde tras extraer `CodecIO`.

**Verificación:** `./gradlew :app:testDebugUnitTest` y
`grep -rn "import android" app/src/main/kotlin/dev/dotscale/brew app/src/main/kotlin/dev/dotscale/recipe app/src/main/kotlin/dev/dotscale/history/TargetCurveCodec.kt app/src/main/kotlin/dev/dotscale/history/CodecIO.kt`
no devuelve nada.

Commit sugerido: `feat(brew): add target curve, stage guide, brew fit and recipe model`.

### F2. Base de datos v2 y repositorios

**Gradle:** `room-testing = { module = "androidx.room:room-testing", version.ref = "room" }` y
`androidTestImplementation(libs.androidx.room.testing)`.

**Archivos:**

| Archivo | Contenido |
|---|---|
| `history/HistoryModels.kt` | `ReferenceType { RECIPE, BREW }`, `UsedTarget` y los campos nuevos de `BrewContext` (D24). |
| `history/BrewEntities.kt` | Las seis columnas anulables de `BrewEntity`. `BrewTargetEntity` (`brew_targets`, `brewId` PK y FK con `CASCADE`). `fitScore` en `BrewListRow` y `BrewListItem`. `target` y `targetError` en `BrewRecord`. `target: BrewTargetEntity?` en `DeletedBrew`. |
| `history/HistoryConverters.kt` | `ReferenceType` por nombre. El conversor es anulable: un nombre desconocido se lee como `null` (preparación sin referencia). |
| `history/BrewDao.kt` | `fitScore` en `list`. `target(id)`. `insertBrew(brew, curve, target?)`. `deleteBrew` y `restore` con el objetivo. `count(): Flow<Int>` (D22). |
| `history/BrewRepository.kt` | `save` escribe las columnas de referencia y ajuste, los datos de `context.details` y `brew_targets` (codificado en `Dispatchers.Default`), todo en una transacción. `get` y `observe` decodifican también el objetivo; `TargetCurveFormatException` → `targetError`. `count()`. |
| `recipe/RecipeEntities.kt` | `RecipeEntity` (`recipes`, índice único en `builtInKey`), `RecipeStageEntity` (`recipe_stages`, PK `recipeId` + `position`, FK con `CASCADE`), `RecipeWithStages` y `DeletedRecipe`. |
| `recipe/RecipeDao.kt` | `observeAll()`, `observe(id)` y `get(id)` con `@Transaction`. Escrituras de D14. `byBuiltInKey(key)`. |
| `recipe/RecipeRepository.kt` | Contrato del §3.7 con `RecipeTexts` (D11, D13). `duplicate` usa `texts.copyName`. Escrituras con `NonCancellable`. |
| `recipe/BuiltInRecipeSeeder.kt` | Inserción SQL de las predefinidas con `localizedNames = 1` (D14). |
| `recipe/ResourcesRecipeTexts.kt` | Implementación con los recursos de la aplicación. |
| `history/Migrations.kt` | `migration1To2(texts, clock)`: seis `ALTER TABLE`, tres `CREATE TABLE` con sus índices (copiados de `2.json`) y la siembra. |
| `history/HistoryDatabase.kt` | `version = 2` con las entidades nuevas, `recipeDao()`, la migración y un `Callback.onCreate` que siembra, en `create` y en `inMemory`. |
| `DotScaleApp.kt` | `val recipeRepository by lazy { RecipeRepository(historyDatabase, ResourcesRecipeTexts(this)) }`. |
| `res/values*/strings.xml` | Nombres de las predefinidas (`recipe_builtin_*`), sus etapas en `<string-array>`, `recipe_copy_name` y `recipe_from_brew_name`, en es y en. |

**Esquema:** `app/schemas/dev.dotscale.history.HistoryDatabase/2.json` se genera al compilar y se añade
al repositorio. `1.json` no se toca.

**Tests** (`app/src/androidTest/kotlin/dev/dotscale/`):
- `history/HistoryMigrationTest.kt` (`MigrationTestHelper`): crea la v1 con dos preparaciones por SQL,
  migra y valida. Las filas se conservan con todas sus columnas, las nuevas valen `null`, `brew_curves`
  sigue igual y existen las dos predefinidas con sus etapas. Después abre la base con Room y lee las dos
  preparaciones por el DAO.
- `recipe/RecipeDaoTest.kt`: los casos del §5 del spec, más el orden de la lista con nombres traducidos
  (con un `RecipeTexts` falso) y que `save` no hace emitir `null` a `observe(id)`.
- `history/BrewDaoTest.kt` (ampliado): guardado con objetivo, cascada a `brew_targets`, `restore` con el
  objetivo, `fitScore` en la lista y un BLOB de objetivo corrupto → `targetError` con la curva real intacta.
- `history/TestBrews.kt`: `context(target = …, details = …)` y un `usedTarget(...)` determinista.

**Verificación:** `./gradlew :app:compileDebugKotlin` (genera `2.json`) y
`./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.dotscale.history.HistoryMigrationTest,dev.dotscale.history.BrewDaoTest,dev.dotscale.recipe.RecipeDaoTest`.

Commit sugerido: `feat(history): migrate the database to v2 with recipes and brew targets`.

### F3. Simulador

| Archivo | Cambio |
|---|---|
| `sim/SimulatedPour.kt` | `fun interface PourScript { fun next(): Double? }`, que implementa `SimulatedPour`. |
| `sim/SimulatedTargetPour.kt` (nuevo) | `FollowMode { FAITHFUL, DEVIATED }` y `SimulatedTargetPour(target, mode, seed = 42)`: ticks de 100 ms, flujo base por etapa, factor de ±10 % cada 5 ticks y el suavizado 0,3 de `SimulatedPour`. Termina tras la última etapa, cuando el flujo baja de 0,05 g/s. `DEVIATED` según D17. |
| `sim/SimulatedScaleConnection.kt` | `pour: PourScript?` y la sobrecarga `simulatePour(target, mode)`. |

**Tests:** `sim/SimulatedTargetPourTest.kt` alimenta `FakeScale` con el flujo del guion, termina por
inactividad y calcula `brewFit` con "V60 clásica". Fiel: agua dentro de ±3 %, tiempo dentro de ±5 s y
ajuste ≥ 90. Desviado: fuera de las dos tolerancias y ajuste ≤ 60. Mismo resultado con la misma semilla.

**Verificación:** `./gradlew :app:testDebugUnitTest --tests "dev.dotscale.sim.*"`.

Commit sugerido: `feat(sim): simulate pours that follow the target curve`.

### F4. Recetas, ajustes y navegación

**Navegación:**
- `NavigationKeys.kt`: `Recipes` y `RecipeEdit(id: Long? = null, fromBrewId: Long? = null)` (§3.8).
- `Navigation.kt`: entradas con sus ViewModels. `onOpenRecipes` en Scan. Al duplicar se sustituye la
  última clave de la pila (D15).

**Archivos:**

| Archivo | Contenido |
|---|---|
| `ui/components/AntIcons.kt` | `Recipe`: hoja con líneas y una gota, trazo propio. |
| `ui/scan/ScanScreen.kt` | Parámetro `onOpenRecipes` y el icono antes del de historial. |
| `ui/recipes/RecipeFormatters.kt` | "15 g · 250 g · 1:16,7 · 3 etapas" y `m:ss`. |
| `ui/recipes/RecipeRow.kt` | `AntCard` pulsable con nombre, método, el resumen, `AntTag` "Predefinida" y una marca opcional de "activa" (la usa también el selector, D10). |
| `ui/recipes/RecipesViewModel.kt` / `RecipesScreen.kt` | Lista, `AntEmpty` con "Crear receta", "+" en la top bar y "Deshacer" con el patrón de `HistoryScreen`. |
| `ui/recipes/RecipeEditViewModel.kt` | Modos crear, editar y desde preparación (`RecipeFromBrew` + `texts.fromBrewName`, con el nombre o la fecha). Estados `Loading`, `Ready`, `Saving`, `Saved`, `Duplicated(id)` y `Gone`. |
| `ui/recipes/RecipeEditScreen.kt` | Campos del §3.8 con su error. Filas de etapa con quitar (`AntIcons.Close` y `contentDescription` "Quitar etapa N"). "Añadir etapa". "Agua total · Ratio" en vivo. Vista previa con `BrewChart(samples = emptyList(), target = …)`. `AntAlert` de ajustes (D16). "Guardar", "Duplicar" y "Borrar" (borrar sin modal, con deshacer en la lista). Descarte con `AntModal`. |
| `settings/TargetSettings.kt` | §3.7: `ReferenceKey` (`sealed interface`) y `TargetSettings`, que implementa `ReferenceStore` (D2). Claves `target_ref_type`, `target_ref_id`, `stage_vibration` y `stage_sound`. |
| `ui/settings/SettingsScreen.kt` | Sección "Guía de etapas" con dos `AntSwitch` y "Restaurar recetas predefinidas" con `AntModal` (D23). |
| `DotScaleApp.kt` | `val targetSettings by lazy { TargetSettings(this) }`. |

**Textos:** prefijos `recipe_` y `settings_stage_*` en `values/` y `values-en/`.

**Tests de Compose** (`androidTest/.../ui/recipes/`, Room en memoria):
- `RecipesScreenTest.kt`: las dos predefinidas con su etiqueta, estado vacío tras borrarlas y borrar con
  "Deshacer" (mismo id).
- `RecipeEditScreenTest.kt`: los casos del §5 del spec, guardar y volver a la lista, y duplicar.
- `SettingsScreenTest.kt` (ampliado): los dos interruptores persisten y restaurar devuelve una
  predefinida editada a sus valores.

**Verificación:** `./gradlew :app:compileDebugKotlin`, `./gradlew :app:lintDebug` (sin
`MissingTranslation`) y los tests anteriores con `connectedDebugAndroidTest`.

Commit sugerido: `feat(recipes): add recipe list, editor and stage alert settings`.

### F5. Scale

**Modelo y sesión:**
- `brew/BrewModels.kt` y `BrewSession.publish()`: `BrewState.t0MonoMs` (D6).
- `ui/scale/ScaleUiState.kt`: `target: ActiveTarget?`, `guide: GuideState?`, `lastFit: SessionFit?` y los
  errores de D9. `ActiveTarget(key, type, name, curve, factor, prefill)`.
- `ui/scale/TargetReferences.kt`: la interfaz de D2 y `RepositoryTargetReferences`.

**`ScaleViewModel`:**
- Constructor:
  `ScaleViewModel(connection, autoFinishSeconds, saver, applicationScope, references: ReferenceStore, targets: TargetReferences, notifier: StageNotifier, clock, wallClock)`.
- Resolución, memoria, congelación y ajuste según D7. Una referencia que desaparece llama a
  `setReference(null)` y emite el error de D9. En `Active` la curva congelada se mantiene.
- `publish()` calcula `guide` (D5) y, después de `_ui.update`, pasa el estado al `StageAlertTracker` y
  los avisos al notifier (D6).
- `save()` añade `UsedTarget` y `details` (§3.7) al `BrewContext` y deja `lastFit`.
- **Corrección:** con `UnitChanged` que borra la dosis, también `session.setDoseGrams(null)`.
- `selectTarget(key)` y `simulateTargetPour(mode)`. `onCleared()` llama a `notifier.release()`.

**Interfaz** (`ui/scale/`):

| Archivo | Cambio |
|---|---|
| `ChartDrawing.kt` (nuevo) | Piezas compartidas de D20. |
| `BrewChart.kt` | `target: TargetCurve? = null`. Topes con el agua máxima y el `endMs` del objetivo. Línea discontinua. |
| `BrewChartColors.kt` | `target` (D19). |
| `BrewChartCard.kt` | Chip "Objetivo: …" en `extra` (`AntButton` pequeño, habilitado salvo en `Active`). `Armed` con objetivo: gráfica, "Etapa 1/N…" y "Escalada a …". `Active`: `TargetGuideBlock` encima de la gráfica. `Finished`: curva de la sesión (D8). `contentDescription` con "Objetivo: X g en m:ss". |
| `TargetGuideBlock.kt` (nuevo) | Bloque de la guía (D5, D18), con `liveRegion = Polite` en el texto de la etapa. |
| `FitBlock.kt` (nuevo) | `FitView` y `FitBlock` (D21). |
| `BrewSummaryCard.kt` | Parámetro `fit: FitView? = null`. |
| `TargetPicker.kt` / `TargetPickerViewModel.kt` (nuevos) | D10, sobre `AntDrawer`, con `AntSegmented` "Recetas \| Historial", "Quitar objetivo" y "Gestionar recetas". |
| `AndroidStageNotifier.kt` (nuevo) | D3. |
| `ScaleScreen.kt` | Estado del selector (`rememberSaveable`), `onManageRecipes`, textos de los errores nuevos, `internal fun scaleFigureStyle(size)` (D18, la usan `WeightCard` y la guía) y los botones "Seguir objetivo" y "Seguir con desvío" en `SimulationCard`, habilitados solo con objetivo. |

**Otros:** `<uses-permission android:name="android.permission.VIBRATE" />` en el manifest.
`Navigation.kt` pasa `app.targetSettings`, `RepositoryTargetReferences(...)`,
`AndroidStageNotifier(appContext, app.targetSettings)` y `TargetPickerViewModel`, y
`onManageRecipes = { backStack.add(Recipes) }`. Textos `target_` y `fit_`.

**Tests:**

| Archivo | Casos |
|---|---|
| `test/.../ui/scale/ScaleViewModelTargetTest.kt` (nuevo) | Con `FakeReferenceStore`, `FakeTargetReferences` y `FakeNotifier`: los casos del §5 del spec, y además el factor 16,4/15, `selectTarget` ignorado en `Active`, la receta editada en `Active` (el ajuste usa la congelada y la nueva llega en `Finished`), un cambio de unidad que deja el factor en 1 y borra la dosis de la sesión, `lastFit` vacío al empezar otra sesión, `TargetUnreadable` y el contador de construcciones (una por cambio de referencia o de dosis, ninguna por muestra). |
| `test/.../ui/scale/ScaleViewModelBrewTest.kt` | Se adapta al constructor nuevo. Sin objetivo, los casos actuales no cambian. |
| `test/.../ui/scale/BrewChartColorsTest.kt` | `target` y los colores A y B con ≥ 3:1 en los dos temas. |
| `androidTest/.../ui/scale/TargetPickerTest.kt` | Los casos del §5 del spec. |
| `androidTest/.../ui/scale/BrewChartCardTargetTest.kt` | Los casos del §5 del spec, a 360 dp, y sin objetivo igual que antes. |
| `androidTest/.../ui/BrewChartTest.kt` | Se adapta al constructor nuevo. |

**Verificación:** `./gradlew :app:testDebugUnitTest`, `./gradlew :app:lintDebug` y los tests de Compose
anteriores.

Commit sugerido: `feat(scale): add target curve, stage guide and fit to the live brew`.

### F6. Historial y comparación

**Navegación:** `ComparePick(aId)` y `BrewCompare(aId, bId)`. Al elegir B, la última clave de la pila
se sustituye. El detalle recibe `onCompare` y `onSaveAsRecipe` (`RecipeEdit(fromBrewId = id)`).

**Archivos** (`ui/history/`):

| Archivo | Contenido |
|---|---|
| `HistoryRow.kt` | `AntTag` "Ajuste N" con `fitScore` y el parámetro `enabled` (D10). |
| `HistoryFilters.kt` (nuevo) | `HistoryFilters` y `rememberHistoryFilter`, sacados de `HistoryScreen` (D22). |
| `BrewDetailScreen.kt` | `Chart(..., target = record.target)`. Tarjeta "Ajuste al objetivo" con `FitBlock` si `fitScore` o `referenceType` no son nulos. `AntAlert` con `targetError`. Botones en dos filas (D21); "Guardar como receta" deshabilitado con `curveError`. |
| `ComparePickViewModel.kt` / `ComparePickScreen.kt` (nuevos) | D22, con los dos `AntEmpty` del §4 del spec. |
| `CompareRows.kt` (nuevo, puro) | Filas de la tabla con A, B y B−A en la unidad de A. Un nulo → "—" en el valor y en la diferencia. |
| `CompareColors.kt` / `CompareChart.kt` (nuevos) | D19 y D20. Dos series con eje X común y tope común; el flujo sin colorear por estabilidad. |
| `BrewCompareViewModel.kt` / `BrewCompareScreen.kt` (nuevos) | `combine(observe(a), observe(b))`; si una es `null`, `Gone` y vuelta atrás. Leyenda, gráficas de agua y de flujo (`testTag` `compare_chart_water` y `compare_chart_flow`), tabla y `AntAlert` por curva ilegible. |

**Textos:** prefijo `compare_` y los del detalle.

**Tests:**
- `test/.../ui/history/CompareRowsTest.kt`: diferencias, unidad de A con B en otra unidad, "—" y número
  de vertidos "—" con curva ilegible.
- Compose (`androidTest/.../ui/history/`): `ComparePickScreenTest`, `BrewCompareScreenTest` y
  `HistoryScreenTest` ampliado, con los casos del §5 del spec. `BrewDetailScreenTest` ampliado: curva
  objetivo y tarjeta de ajuste, `AntAlert` con un objetivo corrupto y los dos botones nuevos.

**Verificación:** `./gradlew :app:compileDebugKotlin`, `./gradlew :app:lintDebug` y los tests anteriores.

Commit sugerido: `feat(history): show brew fit and compare two brews`.

### F7. Verificación

1. **Dispositivo.** `adb devices -l`. Si hay más de uno, preguntar cuál usar. Después,
   `./gradlew :app:installDebug`. *No se usa ARTEMIS:* `uiautomator dump`, `input tap` y
   `screencap -d <display id>`.
2. **E2E por adb** con la báscula simulada. Antes: `adb shell pm clear dev.dotscale`, `pm grant` de
   `BLUETOOTH_SCAN` y `BLUETOOTH_CONNECT` y "Fin automático" en 30 s.

   | Paso | Recorrido | Comprobaciones |
   |---|---|---|
   | 1 | Scale → chip → "V60 clásica" | 2 toques. Curva discontinua y "Etapa 1/3 · Bloom: hasta 40 g en 10 s" |
   | 2 | "Simular +15 g" → "Capturar dosis" | La curva se escala con la dosis capturada (≈ 15 g; por el ruido del simulador puede aparecer "Escalada a 15,1 g"). El escalado exacto ×16,4/15 lo cubre `ScaleViewModelTargetTest` |
   | 3 | "Seguir objetivo" → esperar el fin | Etapa, cuenta atrás y desvío en verde. Si el emulador tiene vibrador, `adb shell dumpsys vibrator_manager` registra una vibración en las etapas 2 y 3. Resumen con ajuste ≥ 90, agua dentro de ±3 % y tiempo dentro de ±5 s |
   | 4 | Historial → la preparación | `AntTag` "Ajuste N" en la fila. El detalle dibuja la curva objetivo y la misma puntuación que el resumen |
   | 5 | "Guardar como receta" → "Guardar" | Editor con 3 etapas; la receta aparece en Recetas |
   | 6 | Scale → "Seguir con desvío" → esperar el fin | Desvío en rojo, ajuste ≤ 60 |
   | 7 | Detalle de la 2.ª → "Comparar con…" → la 1.ª | 3 toques desde el historial. Filtro con su café (o "Todos"), dos gráficas y tabla con B−A. Atrás vuelve al detalle de A |
   | 8 | Elegir una preparación como objetivo | 3 toques desde Scale |
   | 9 | `adb shell am force-stop dev.dotscale` → abrir Scale | La referencia sigue activa |
   | 10 | Móvil en inglés (`cmd locale` o Ajustes) | "Classic V60" y "4:6 method" |

3. **Rendimiento** (emulador con API 36 o superior):
   - `ComparePerformanceTest` (nuevo): base en archivo con dos preparaciones de 10 min, medido desde
     `setContent` hasta `compare_chart_water` y `compare_chart_flow`. Calentamiento y mediana de 5.
     Menos de 500 ms.
   - `HistoryPerformanceTest`: las 500 preparaciones se crean con objetivo. `.db`, `-wal` y `-shm` por
     debajo de 10 MB, y los tiempos de lista y detalle siguen dentro de sus objetivos.
4. **Ejecutar:** `./gradlew :app:testDebugUnitTest`, `./gradlew :app:connectedDebugAndroidTest` y
   `./gradlew :app:lintDebug` frente a la línea base de F0.
5. **Cierre:** marcar el checklist del §5 del spec, pasar D1–D24 al spec (§3.12) y marcar este plan
   como completado.

Commit sugerido: `test(history): add compare performance test and verify the target curve flow`.

## 4. Trazabilidad con el §5 del spec

| Criterio | Fase | Prueba |
|---|---|---|
| "V60 clásica" en `Armed` en 2 toques | F5 | `TargetPickerTest`, `BrewChartCardTargetTest`, E2E 1 |
| Preparación como objetivo en 3 toques | F5 | `TargetPickerTest`, E2E 8 |
| La referencia sobrevive a salir y a `force-stop` | F4, F5 | E2E 9 |
| Escalado ×16,4/15 y curva congelada en `Active` | F1, F5 | `TargetCurveTest`, `ScaleViewModelTargetTest` |
| Etapa, cuenta atrás y desvío con niveles | F1, F5 | `TargetGuideTest`, `BrewChartCardTargetTest`, E2E 3 |
| Un aviso por etapa, sin sonido en silencio | F1, F5 | `StageAlertTrackerTest`, `ScaleViewModelTargetTest`, E2E 3 |
| Perfil fiel (±3 %, ±5 s, ≥ 90) y desviado (≤ 60) | F3 | `SimulatedTargetPourTest`, E2E 3 y 6 |
| Mismo ajuste en resumen y detalle; curva guardada | F2, F5, F6 | `BrewDaoTest`, `BrewDetailScreenTest`, E2E 4 |
| Datos prellenados desde la referencia | F5 | `ScaleViewModelTargetTest` (`BrewContext.details`) |
| `AntTag` "Ajuste N" en la fila | F6 | `HistoryScreenTest`, E2E 4 |
| Predefinidas en inglés | F2, F4 | `RecipeDaoTest` (textos falsos), E2E 10 |
| Crear, editar, duplicar, borrar y validación | F1, F4 | `RecipeValidatorTest`, `RecipeEditScreenTest`, `RecipesScreenTest` |
| Restaurar predefinidas | F2, F4 | `RecipeDaoTest`, `SettingsScreenTest` |
| "Guardar como receta" | F1, F4, F6 | `RecipeFromBrewTest`, `RecipeEditScreenTest`, E2E 5 |
| Comparar en 3 toques, filtro por café y tabla B−A | F6 | `ComparePickScreenTest`, `BrewCompareScreenTest`, E2E 7 |
| Sin objetivo, sin regresiones | F5 | `ScaleViewModelBrewTest`, `BrewChartCardTargetTest` |
| Textos en `values/` y `values-en/` | F2–F6 | `lintDebug` (`MissingTranslation`) |
| `MIGRATION_1_2` y esquema v2 exportado | F2 | `HistoryMigrationTest`, `2.json` en el repositorio |
| Comparación < 500 ms | F7 | `ComparePerformanceTest` |
| Curva no recalculada por muestra | F5 | `ScaleViewModelTargetTest` (contador, D7) |
| < 10 MB con 500 preparaciones con objetivo | F7 | `HistoryPerformanceTest` |
| Tests JVM e instrumentados del §5 | F1–F6 | Los listados en cada fase |
| Sin avisos nuevos de lint | F0, F7 | `lintDebug` frente a la línea base |

## 5. Riesgos

| ID | Riesgo | Mitigación |
|---|---|---|
| R1 | El perfil fiel no llega a un ajuste ≥ 90 por la deriva aleatoria del ±10 %. | Se ve en F3 con `SimulatedTargetPourTest`. Si falla, el flujo base de cada tick se calcula con el agua y el tiempo que faltan de la etapa (lazo cerrado), y se anota como desviación del §3.10. |
| R2 | Room rechaza la migración por una diferencia de esquema (índices, `DEFAULT`, FK). | Los `CREATE TABLE` se copian de `2.json` y las columnas nuevas no llevan `defaultValue`. `runMigrationsAndValidate` lo detecta en F2. |
| R3 | Un ViewModel retenido muestra los nombres de las predefinidas en el idioma anterior hasta que la lista vuelve a emitir. | Aceptado: al reabrir la pantalla se ven bien. Si molesta, `RecipeRepository` combina sus flujos con un `StateFlow` de idioma que actualiza `DotScaleApp.onConfigurationChanged`. |
| R4 | `ToneGenerator` falla o suena distinto en algunos dispositivos. | Se captura la excepción y se omite el sonido (D3). La vibración no depende del tono. |
| R5 | `ModalBottomSheet` en tests de Compose (animaciones, foco). | `TargetPickerTest` prueba el contenido sin estado y, aparte, una apertura con `mainClock.autoAdvance`. |
| R6 | Con referencia de tipo preparación, cada guardado de otra preparación invalida la tabla `brews` y la base se reconstruye. | `distinctUntilChanged` sobre la base (curva y datos comparables por valor) evita el trabajo extra, y nunca ocurre en `Active` (D7). |
| R7 | Las tolerancias (5 %, 10 %, ajuste) no encajan con vertidos reales. | Heredado de §6.7 del spec: se calibran con la DOT física y se incrementa `METRIC_VERSION`. |
| R8 | Las medidas de rendimiento en el emulador son ruidosas. | Calentamiento y mediana de 5, como en SPEC-002. |

## 6. Seguimiento fuera de este plan

- SPEC-004 ([catálogo de cafés y compartir imagen](spec-catalogo-cafes.md)) reutilizará `CompareChart` y `BrewChart` con `target`.
- Calibración con la DOT física (SPEC-001 §6 y §6.7 de este spec).
- Fusionar en orden en `develop`: `feat/brew-chart`, `feat/brew-history` y `feat/target-curve`.
