# Plan de implementación: SPEC-008 Recorrido guiado del primer uso

| Campo | Valor |
|---|---|
| Spec | [`spec-recorrido-guiado.md`](spec-recorrido-guiado.md) (SPEC-008) |
| Estado | Aprobado el 2026-11-17. El usuario respondió las preguntas P1 a P5 el 2026-11-17; sus respuestas son las decisiones D29, D30 y D33 a D35, y no quedan preguntas abiertas. |
| Fecha | 2026-11-17 |
| Rama | `feat/guided-tour`, creada desde `develop` (`1015b12`); el spec está en `4ebd192`. |
| Módulos | `:app` (`:protocol` no cambia) |

El spec no trae fases, así que este plan las define. El orden va de dentro afuera: primero la lógica pura (selección de
pasos, transiciones y colocación del globo, F1), después la persistencia y el catálogo de recorridos con todos sus
textos (F2), luego la infraestructura de la interfaz probada sobre una pantalla de prueba (F3), y por último los
recorridos de cada pantalla, agrupados por zonas (F4 a F8). F9 es la verificación.

Las dos preguntas abiertas del §6 del spec se resuelven aquí:

- **Redacción final de los textos:** §2.7, en español y en inglés, ajustada a lo que hace hoy cada pantalla. El usuario la
  revisa sobre las capturas de F9, antes de cerrar (§7.6, D34).
- **Estabilización de las anclas y desplazamiento en `LazyColumn`:** D9 y D10. Se espera a que pasen 300 ms sin altas ni
  bajas de anclas. Las listas perezosas declaran sus anclas con `TourLazyAnchor`, que las da por presentes y las busca
  desplazando la lista.

Lo que más condiciona el diseño:

- **El recorrido es una capa por encima de la navegación.** No vive en las pantallas: `TourRoot` envuelve `TabsNavigation`,
  dibuja el velo y el globo por encima de la barra de pestañas y tiene el único `BackHandler` del recorrido (D12 y D18).
  Cada entrada de la navegación tiene su propio registro de anclas (D20).
- **Fuera de la navegación, el recorrido no hace nada.** Sin `TourScreen` alrededor, `Modifier.tourAnchor` y `TourHost`
  son no-op. Así los tests que componen una pantalla suelta (`CoffeesScreenTest`, `HistoryScreenTest`...) no cambian.
  Los que montan la app entera apagan los recorridos desde `skipOnboarding()`, y solo hay que tocar los tres que no pasan
  por él (D30).

El código real contradice o no cubre algunos puntos del spec. Se listan en el §1.3 con el ajuste que propone este plan.
Lo más importante:

- Tres anclas del §3.7 tienen otro nombre en el código (`ratio_dose`, `recipe_stage_0` y `four_six_pour_0`).
- `recipe_active_tag` nunca aparece en la pestaña Recetas: Recetas marcará la receta activa (D29).
- `summary_rating` y `coffee_detail_days` no existen cuando se evalúa su recorrido y cambian de ancla, y
  `scaleless_plan_water` solo existe sin receta (D33).

## 1. Punto de partida

### 1.1 Se reutiliza

| Pieza | Uso en el recorrido |
|---|---|
| `ui/components/AntStatus.kt` (`AntStatusIcon`) | La técnica del recorte: `graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)` y un dibujo con `BlendMode.Clear`. |
| `ui/session/CountdownOverlay.kt` | El patrón para tragarse los toques: `pointerInput` con `awaitPointerEvent().changes.forEach { it.consume() }`, en el pase `Main`, con los botones como hijos. |
| `ui/components/AntMessage.kt` (`AntMessageHost`, `showAntMessage`) | El aviso `tour_skipped_hint`. |
| `ui/components/AntButton.kt` (`AntButtonType.Link`, `Default` y `Primary`; `AntSize.Small`), `AntCard`, `AntListItem` y `AntTheme` (`colors.bgMask`, `colors.bgElevated`, `radius.lg`, `spacing`, `typography`) | El globo, el velo y la fila "Ver recorrido". `bgMask` ya es el velo de `AntDrawer`. |
| `settings/ExperienceSettings.kt` y `BrewSettings.PREFS_NAME` | El patrón de `TourSettings`: el mismo archivo `"settings"` y un `StateFlow` por clave. |
| `settings/ExperienceMode.kt` (`LocalExperienceMode`) | El modo con el que se calcula `pendingSteps`. |
| `Navigation.kt` (`TabsNavigation`, `tabEntry`, `tabEntryProvider`) y `TabNavigation.kt` (`popToRoot`, `showsBottomBar`) | Dónde se monta la capa y desde dónde se sabe qué entrada es la visible. |
| `androidTest/.../ui/TestAppDependencies.kt` (`TestApp`, `skipOnboarding`), `ui/session/SessionDriver.kt`, `ui/grinders/GrindersUi.kt` y `grinder/TestGrinders.kt` | La base de los tests instrumentados nuevos. |
| `test/.../grinder/GrinderCatalogFileTest.kt` (`File("src/main/...")`) y `test/.../ui/session/SessionFormatTest.kt` (`R.string` en JVM) | El precedente para que un test JVM lea `strings.xml` y la clase `R`. |

### 1.2 Falta o hay que corregir

- No hay ningún registro de posiciones (`onGloballyPositioned`, `boundsInRoot`, `bringIntoView`), ni un globo o tooltip.
  Lo dice el §3.1 del spec y lo confirma una búsqueda en `main`.
- No hay forma de saber desde fuera si hay una hoja o un modal abierto. `AntDrawer` (`ModalBottomSheet`), `AntModal`,
  `AntChoiceModal` y `AntDatePicker` (`DatePickerDialog`) abren ventanas propias, y su estado vive en cada pantalla.
- No hay un host de mensajes común: cada pantalla tiene su `Scaffold` con su `AntMessageHost`.
- `AntTabBar` no deja añadir un modificador a cada pestaña: el `testTag` se aplica dentro.
- `tabEntryProvider` no sabe qué pestaña está activa, y las entradas no saben si son la cima de su pila.
- `CoffeesScreen`, `CoffeeDetailScreen`, `RecipesScreen` y `HistoryScreen` usan `LazyColumn` sin estado propio
  (`rememberLazyListState` no está elevado). No se pueden desplazar desde fuera.
- `SettingsScreen` no tiene "Ver recorrido".
- No hay Robolectric ni detekt (`build.gradle.kts` y `libs.versions.toml`). Como en los planes anteriores, el control
  estático es `:app:lintDebug`.
- 22 clases de tests instrumentados montan la app entera con `TestApp(env)`, y `SettingsNavigationTest` lanza la
  `MainActivity` real. En cuanto exista el recorrido general, todas verían el velo sobre Preparar. 21 de las 22 llaman a
  `env.skipOnboarding()` antes de `setContent`. Las excepciones son `NewUserJourneyTest`, que recorre el primer arranque
  en la interfaz, y `OnboardingTest`, que solo lo llama en uno de sus tests. `SettingsNavigationTest` tiene su propia regla
  `skipOnboarding` (D30).

### 1.3 Discrepancias entre el spec y el código

| # | Spec | Código real | Ajuste que propone el plan |
|---|---|---|---|
| 1 | §3.7 `ratio_calculator.fields`: ancla `ratio_coffee`. | El campo del café lleva `testTag("ratio_${field.name.lowercase()}")` con `RatioField.DOSE`, es decir, `ratio_dose`. `ratio_coffee` es el nombre de la cadena de su etiqueta. | Ancla `ratio_dose`. |
| 2 | §3.7 `recipe_edit.stage`: ancla `recipe_stage_1`. | `StageCard` lleva `testTag("recipe_stage_$index")` con el índice desde 0: el primer paso es `recipe_stage_0`. | Ancla `recipe_stage_0` (el primer paso, que es lo que pide el texto). |
| 3 | §3.7 `four_six.pours`: ancla `four_six_pour_1`. | `PlanCard` lleva `testTag("four_six_pour_$i")` con `i` desde 0. | Ancla `four_six_pour_0` (el primer vertido). |
| 4 | §3.7 `recipes.active`: ancla `recipe_active_tag`, "la receta elegida en Preparar". | `RecipesScreen` llama a `RecipeRow(item, onClick)` sin `active`. La etiqueta "Activa" solo sale en `TargetPicker`. En la pestaña Recetas no aparece nunca. | Resuelto (D29): Recetas marca la receta activa y el paso se mantiene. |
| 5 | §3.7 `summary.rating`: ancla `summary_rating`. | `summary_rating` solo existe cuando la preparación guardada ya tiene valoración (`entity?.rating`), es decir, después de "Añadir detalles". Al abrir el resumen no existe, y el paso no se mostraría. | Resuelto (D33): ancla `summary_details`, el botón donde se valora. |
| 6 | §3.7 `session_scaleless.plan`: ancla `scaleless_plan_water`. | `PlanCard` (con `scaleless_plan_water`) solo se compone sin receta. Con receta, el estado listo muestra `CurrentStep` y `StepList`. | Resuelto (D33): sin cambios. El paso sale en la primera sesión sin báscula y sin receta, y mientras tanto queda pendiente. |
| 7 | §3.7 `coffee_detail.status`: ancla `coffee_detail_days`, "días desde el tueste y café que queda". | `coffee_detail_days` solo existe con fecha de tueste. La tarjeta `StatusCard` (`coffee_detail_status`) tiene los días y el stock, y existe si hay cualquiera de los dos. | Resuelto (D33): ancla `coffee_detail_status`. |
| 8 | §3.7 `history.scaleless`: "ancla nueva en la etiqueta Sin báscula". | La etiqueta ya tiene `testTag("history_row_scaleless")` (SPEC-007). | Ancla `history_row_scaleless`, puesta solo en la primera fila sin báscula (D11). |
| 9 | §3.7 `converter.rows`: "primera `converter_row_<id>`". | El tag depende del id del molino, y un `TourStep` necesita un id fijo. | Ancla `converter_row`, puesta solo en la primera fila. El `testTag` de la fila no cambia. |
| 10 | §3.7 `brew_detail.chart` (E): "ancla nueva en la gráfica", también la objetivo sin báscula. | La tarjeta de la gráfica real no tiene `testTag`. La objetivo es `brew_detail_target_chart` (SPEC-007), y solo se compone una de las dos. | Ancla `brew_detail_chart` en las dos tarjetas. La real gana además `testTag("brew_detail_chart")`. |
| 11 | §3.7 `summary.expert` (E): "flujo, estabilidad y ajuste a la curva objetivo". | Sin báscula, `summary_expert` solo muestra el motivo de fin. | El ancla solo se pone con báscula (`!ui.scaleless`). Sin báscula el paso queda pendiente para un resumen con báscula. |
| 12 | §4: "la sesión activa no tiene anclas que actualicen el registro durante el vertido". | `session_close` y `session_options` están en la barra superior de la sesión, que también se compone en `Active`. | Las dos anclas solo se ponen fuera de `Active` y sin cuenta regresiva (D27). |
| 13 | §3.5 y §4: con rotación, "continúa en el mismo paso"; tras la muerte del proceso, "sigue desde el primer paso pendiente". | Los dos casos restauran el mismo estado guardado y no se pueden distinguir sin estado de proceso. | Se restaura el mismo paso en los dos casos (D23). Como Siguiente marca antes de avanzar, ese paso es el primero sin marcar, salvo que el usuario hubiera retrocedido con Anterior. Si es así, se restaura el paso al que retrocedió. |
| 14 | §3.3: "Tests JVM ... de `reset`". | `TourSettings` necesita `SharedPreferences`, y el proyecto no tiene Robolectric. | `TourSettings` tiene un constructor `internal` que recibe `SharedPreferences`. El test JVM usa una implementación en memoria (D4). No se añade Robolectric. |

## 2. Decisiones que completan el spec

Marcadas como "(concreta)" si eligen entre opciones que el spec deja abiertas, y como "(amplía)" si añaden algo que
el spec no dice.

### 2.1 Modelo, persistencia y catálogo

| ID | Tema | Decisión | Motivo |
|---|---|---|---|
| D1 | Archivos (concreta §3.1 a §3.4) | Paquete nuevo `dev.josueamaya.pourline.ui.tour`: `TourModels.kt` (puro), `TourPlacement.kt` (puro), `Tours.kt` (catálogo), `TourAnchors.kt` (registro, modificador y `TourLazyAnchor`), `TourHost.kt` (`TourScreen`, `TourHost` y la estabilización), `TourCoordinator.kt` y `TourOverlay.kt` (`TourRoot`, `TourBar` y la capa). Fuera del paquete: `settings/TourSettings.kt`, `ui/components/ModalPresence.kt` y `ui/session/SessionTours.kt`. | Lo puro queda aislado y se prueba en la JVM. `ModalPresence` va en `ui/components` porque lo usan los componentes, y así `ui/components` no depende de `ui/tour`. |
| D2 | Modelo y selección (concreta §3.2) | `TourStep` y `Tour` con los campos exactos del §3.2. `fun pendingSteps(tour: Tour, seen: Set<String>, mode: ExperienceMode, anchors: Set<String>): List<TourStep>` filtra en orden por las tres condiciones y, si ninguno de los que quedan tiene ancla, devuelve `emptyList()`. Los ids de `seen` que no son de `tour` no intervienen. | Es la firma del spec. La regla del paso centrado solitario queda en el mismo sitio que el resto de la selección. |
| D3 | Apertura y transiciones (concreta §3.5; amplía con `AnchorLost` y `Cancel`) | `data class TourRun(val tourId: String, val steps: List<TourStep>, val index: Int = 0)` con `current`, `position` (`index + 1`), `count` (`steps.size`), `isFirst` e `isLast`. `sealed interface TourAction { Next; Prev; Back; Skip; AnchorLost; Cancel }`. `data class TourTransition(val run: TourRun?, val markSeen: List<String> = emptyList(), val skippedHint: Boolean = false)`. `fun TourRun.reduce(action: TourAction): TourTransition`: **Next** marca `current` y avanza, y en el último paso cierra (es "Listo"). **Prev** retrocede sin marcar, y en el primer paso no hace nada. **Back** es `Prev` salvo en el primer paso, donde es `Skip`. **Skip** marca todos los `steps` de la apertura, cierra y pide el aviso. **AnchorLost** quita `current` de `steps` sin marcarlo, mantiene el índice (que pasa a apuntar al siguiente) y cierra si no queda ninguno, sin aviso. **Cancel** cierra sin marcar y sin aviso. `fun restore(tour: Tour, ids: List<String>, index: Int): TourRun?` descarta los ids que ya no existen y ajusta el índice. | Una máquina de estados pura permite probar en la JVM la semántica de Saltar que pide el §5. Como `AnchorLost` saca el paso de la lista, Saltar no marca los pasos que desaparecieron durante la apertura ("no toca los pasos invisibles"). El contador "n/N" es siempre `position/count` de la lista de esa apertura, que baja si se pierde un ancla. |
| D4 | Persistencia (concreta §3.3) | `interface TourStore { val seen: StateFlow<Set<String>>; fun markSeen(ids: Collection<String>); fun reset() }`. `class TourSettings internal constructor(private val prefs: SharedPreferences) : TourStore` con `constructor(context: Context, prefsName: String = BrewSettings.PREFS_NAME)`. Clave `KEY_SEEN = "tour_seen"` (`putStringSet`). Lectura: `runCatching { prefs.getStringSet(KEY_SEEN, null)?.toSet() }.getOrNull() ?: emptySet()`, que copia el conjunto y convierte un valor de otro tipo (`ClassCastException`) en vacío. `markSeen` hace la unión y solo escribe si cambia algo. `reset` hace `remove(KEY_SEEN)` y publica `emptySet()`. Los ids desconocidos se guardan como vienen, no se podan y no intervienen (D2). No lee ni escribe `onboarding_done`. | Así las preferencias corruptas no fallan (§4) y la clase se prueba en la JVM con un `SharedPreferences` en memoria (discrepancia 14). |
| D5 | Catálogo (concreta §3.7 y §3.8) | `object Tours` con un `val` por recorrido, en el orden del §3.7 (`prepare`, `sessionPrep`, `sessionScaleless`, `summary`, `coffees`, `coffeeEdit`, `coffeeDetail`, `recipes`, `recipeEdit`, `history`, `brewDetail`, `compare`, `tools`, `ratioCalculator`, `fourSix`, `converter` y `settings`), más `val all: List<Tour>` y `val allStepIds: Set<String>`. Cada paso se llama `"<recorrido>.<paso>"`, y sus recursos `tour_<recorrido>_<paso>_title` y `tour_<recorrido>_<paso>_body`, con el punto cambiado por un guion bajo: `prepare.grind_ruler` → `tour_prepare_grind_ruler_title`. Las anclas del §2.6. | Es la convención del §3.8. Un nombre deducible del id permite comprobar los recursos en un test. |
| D6 | Instancias (concreta §3.3) | `PourlineApp.tourSettings by lazy { TourSettings(this) }`. `AppDependencies` gana `val tourSettings: TourSettings`. `TestAppDependencies` gana `val tourSettings = TourSettings(context, PREFS)`, `fun skipTours(except: Collection<Tour> = emptyList())`, que marca como vistos todos los pasos de `Tours.all` salvo los de `except`, y el parámetro `showTours` de `skipOnboarding` (D30). | Es lo que pide el §3.3. Con `except`, un test que prueba un recorrido deja los demás apagados. |

### 2.2 Anclas, estabilización y desplazamiento

| ID | Tema | Decisión | Motivo |
|---|---|---|---|
| D7 | Registro (concreta §3.1) | `@Stable class TourAnchorRegistry`. `val ids: Set<String>` (estado de snapshot) solo cambia con las altas y bajas. `fun bounds(id: String): Rect?` lee el estado de los límites del ancla, y solo lo leen el dibujo y el layout de la capa. `internal fun isAttached(id: String): Boolean`. `internal suspend fun reveal(id: String): Rect?`. `val LocalTourAnchors = staticCompositionLocalOf<TourAnchorRegistry?> { null }`. Hay uno por entrada de la navegación (D20) y otro para la barra de pestañas (D12). Si dos nodos se registran con el mismo id, gana el último, y la baja solo borra si el nodo coincide. | Así las pantallas no se recomponen con cada movimiento (§4, rendimiento): nada en composición lee los límites. Con un registro por entrada, dos pantallas de la pila no comparten anclas (§3.1). |
| D8 | Modificador (concreta §3.1) | `fun Modifier.tourAnchor(id: String, enabled: Boolean = true): Modifier`. Con `enabled = false` devuelve `this`. Si no, añade `TourAnchorElement(id)` (un `ModifierNodeElement`). `TourAnchorNode : Modifier.Node(), GlobalPositionAwareModifierNode, CompositionLocalConsumerModifierNode`. En `onAttach` lee `currentValueOf(LocalTourAnchors)` y se registra. En `onDetach` se da de baja. En `onGloballyPositioned`, con `coordinates.boundsInRoot()`, escribe sus límites solo si cambian. `suspend fun reveal()` llama a `bringIntoView()` (`androidx.compose.ui.relocation`, sobre `DelegatableNode`). Sin registro (pantallas compuestas sueltas o previews) no hace nada. | Es el `onGloballyPositioned` / `boundsInRoot` del spec, sin `composed` y sin recomposiciones. `boundsInRoot` ya viene recortado por los contenedores, así que el hueco nunca invade la barra superior. |
| D9 | Estabilización (resuelve la pregunta abierta 2a) | `const val TOUR_STABLE_MS = 300L` en `TourHost.kt`. `suspend fun awaitStableAnchors(vararg registries: TourAnchorRegistry)` espera a que pasen `TOUR_STABLE_MS` sin cambios en `ids` de ningún registro (`snapshotFlow { registries.map { it.ids } }` con `debounce`, y el primer valor). Empieza a contar cuando se cumplen las condiciones de D21. Solo cuentan las altas y bajas, no los cambios de límites. Además, las pantallas cuyo contenido carga de forma asíncrona pasan `ready` (D21): por ejemplo, `ready = items != null` en Cafés. | Es el criterio orientativo del §3.5. Si contaran los límites, un contenido que se mueve (una transición o un texto que cambia) retrasaría el recorrido sin fin, y la capa ya los lee en vivo. `ready` evita que una lista que tarda en cargar deje pasos fuera. |
| D10 | Desplazamiento (resuelve la pregunta abierta 2b) | **Anclas compuestas** (`Column` con `verticalScroll` y elementos visibles de una `LazyColumn`): `node.reveal()`. **Anclas de `LazyColumn`:** `@Composable fun TourLazyAnchor(id: String, state: LazyListState, present: Boolean)` declara el id en el registro mientras `present`, aunque su elemento no esté compuesto, con una función para revelarlo. Si el nodo ya está registrado, se usa `node.reveal()`. Si no, se hace `state.scrollToItem(0)` y, hasta `MAX_REVEAL_STEPS = 30` veces, mientras el nodo no esté y `state.canScrollForward`: `state.scrollBy(viewport * 0.8f)` y se espera un fotograma. Al final, `node.reveal()`. **Validación:** tras revelar y esperar un fotograma, los límites deben existir, tener ancho y alto mayores que 0, y cortar la raíz. Si no, el paso se trata como ausente (`AnchorLost`, §4). Las cuatro pantallas con `LazyColumn` elevan `val listState = rememberLazyListState()`. | `bringIntoView` sirve para todo lo que está compuesto. Una `LazyColumn` no compone lo que no se ve, así que sin la declaración un ancla fuera de pantalla nunca contaría como presente: por ejemplo, "Nueva bolsa" bajo una bitácora larga, o el buscador de Cafés con la lista restaurada a mitad. Buscar desplazando no obliga a cada pantalla a calcular índices. |
| D11 | "La primera" fila (concreta §3.7) | Las anclas de fila se ponen en una sola fila, elegida en la pantalla: la primera de Cafés (`coffee_row`) y de Historial (`history_row`), la primera receta predefinida (`recipe_builtin_tag`), la primera fila sin báscula (`history_row_scaleless`) y la primera equivalencia del convertidor (`converter_row`). `HistoryRow` gana `scalelessAnchor: Boolean = false` y `RecipeRow` gana `builtInAnchor: Boolean = false`, y los dos ponen el ancla en su etiqueta. Las demás anclas de fila van por el `modifier` de la fila. | Un id por ancla, sin ambigüedad. Los parámetros por defecto dejan igual a los llamadores actuales y a sus tests. |

### 2.3 Overlay, globo y ciclo de vida

| ID | Tema | Decisión | Motivo |
|---|---|---|---|
| D12 | Raíz y capa (concreta §3.4) | `@Composable fun TourRoot(store: TourStore, content: @Composable () -> Unit)`, en `TourOverlay.kt`: crea el `TourCoordinator`, una `ModalPresence` y el registro de la barra, provee `LocalTourCoordinator` y `LocalModalPresence`, y dibuja `Box(Modifier.fillMaxSize()) { Box(contenido) ; TourLayer(coordinator) }`. Mientras hay un paso en pantalla, la caja del contenido lleva `Modifier.semantics { hideFromAccessibility() }`. `@Composable fun TourBar(content: @Composable () -> Unit)` provee el registro de la barra como `LocalTourAnchors`. `TabsNavigation` envuelve su `Column` en `TourRoot(deps.tourSettings)` y su `AntTabBar` en `TourBar`. | Una sola capa en la raíz queda por encima de la barra de pestañas, como pide el §3.4, y del `Scaffold` de cada pantalla. `MainActivity` y `TestApp` no cambian, porque los dos montan `MainNavigation`. |
| D13 | Coordinador (amplía) | `@Stable class TourCoordinator(val store: TourStore)` con `var presentation: TourPresentation?` (estado de snapshot), `val messages = SnackbarHostState()`, `val barAnchors = TourAnchorRegistry()`, `fun present(p: TourPresentation): Boolean` (lo rechaza si otro dueño ya está presentando), `fun dismiss(owner: Any)` y `fun skippedHint()` (lanza el aviso en el ámbito de `TourRoot`). `data class TourPresentation(val owner: Any, val step: TourStep, val position: Int, val count: Int, val bounds: () -> Rect?, val onAction: (TourAction) -> Unit)`. | Garantiza un solo recorrido a la vez (§3.5). El aviso se lanza en el ámbito de la raíz, así que no se corta aunque la pantalla que lo pidió salga de la composición. |
| D14 | Velo y bloqueo (concreta §3.4) | Una caja a pantalla completa con `testTag("tour_overlay")`, sin más semántica. Lleva `pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } } }` en el pase `Main`, que es el de por defecto: los botones del globo, que son hijos, reciben el toque antes, y nada llega al contenido de debajo. El dibujo usa `graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)` y `drawWithCache`: `drawRect(colors.bgMask)` y `drawRoundRect(Color.Black, …, cornerRadius = radius.lg + 8.dp, blendMode = BlendMode.Clear)` sobre los límites del ancla agrandados 8 dp y recortados a la raíz. Los límites se leen en el dibujo, así que un desplazamiento o una rotación solo redibujan la capa. Un paso sin ancla no tiene hueco. | Es el recorte de `AntStatus.kt` y el bloqueo de `CountdownOverlay.kt`, que ya funcionan en la app. Si consumiera en el pase `Initial`, el globo no recibiría sus propios toques. |
| D15 | Globo (concreta §3.4) | Una tarjeta con fondo `bgElevated`, `RoundedCornerShape(radius.lg)`, sombra de 6 dp (como `AntMessage`), relleno `spacing.base` y ancho máximo `min(ancho seguro - 2 * spacing.base, 360.dp)`. Lleva `testTag("tour_balloon")` (amplía). **Cabecera:** el título (`heading5`, `tour_title`) y el contador (`textSM` secundario, `tour_counter`, texto `tour_counter`). **Cuerpo:** `tour_body`. **Pie:** un `FlowRow` con Saltar (`AntButtonType.Link`, `tour_skip`) al principio y, al final, Anterior (`AntButtonType.Default`, `tour_prev`, solo desde el paso 2) y Siguiente o Listo (`AntButtonType.Primary`, `tour_next`). Los botones son `AntSize.Small`. Una flecha de 8 dp del color del globo apunta al centro del hueco. Claro y oscuro salen de `AntTheme`. | Son el estilo de Ant y los testTags del §3.4. El `FlowRow` deja que los botones bajen de línea a 360 dp con la letra a 1,3 (R10). |
| D16 | Colocación (concreta §3.4 y §4) | Función pura en `TourPlacement.kt`: `enum class BalloonSide { BELOW, ABOVE, END, START, CENTER }`, `data class BalloonPlacement(val offset: Offset, val side: BalloonSide, val arrow: Offset?)` y `fun placeBalloon(hole: Rect?, balloon: Size, safe: Rect, gap: Float): BalloonPlacement`. Reglas, en orden: sin hueco, `CENTER` en `safe`. `BELOW` si cabe entre `hole.bottom + gap` y `safe.bottom`. `ABOVE` si cabe por encima. `END` si cabe a la derecha, centrado en vertical sobre el hueco. `START` si cabe a la izquierda. Si no cabe en ninguno, el lado vertical con más espacio, dentro de `safe` aunque tape parte del hueco. En `BELOW` y `ABOVE`, la x se centra en el hueco y se ajusta a `safe`. La flecha apunta al centro del hueco, sin salirse del borde ni de las esquinas del globo. `safe` es la raíz menos `WindowInsets.safeDrawing`. La capa mide el globo y lo coloca en un `Layout` propio que lee los límites en la fase de layout. | Cubre el "debajo si hay espacio, encima si no" del §3.4 y el "a un lado en horizontal" del §4, siempre dentro de la zona segura. Al ser pura, se prueba en la JVM. |
| D17 | Accesibilidad (concreta §3.4) | El globo lleva `Modifier.semantics { paneTitle = título }`. El bloque del título y el cuerpo, `semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }`. Los botones son `AntButton` con texto, así que tienen nombre accesible. El velo no tiene semántica salvo el `testTag`, así que no es enfocable. El contenido de debajo queda oculto a los servicios de accesibilidad mientras hay un paso en pantalla (D12). | TalkBack anuncia el panel al aparecer y el texto al cambiar de paso, y con el contenido oculto el recorrido sigue siendo de solo lectura también con TalkBack. |
| D18 | "Atrás" (concreta §3.4) | En `TourLayer`: `if (presentation != null) BackHandler { presentation.onAction(TourAction.Back) }`. Solo se compone mientras hay un paso en pantalla. | Así se registra después de los `BackHandler` de la pantalla (el de `SessionScreen`, el de `CoffeeEditScreen` con cambios) y del de `NavDisplay`, y gana: "Atrás" retrocede un paso en lugar de salir. |
| D19 | Aviso de Saltar (concreta §3.5: "el plan decide dónde vive el host") | En `TourLayer`, compuesto siempre: `AntMessageHost(coordinator.messages, Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)))`. El aviso es `showAntMessage(tour_skipped_hint, AntStatus.Info)`, corto y sin acción. | Arriba y centrado, como el `message` de antd. Se ve en cualquier pantalla, también sin barra de pestañas, y no choca con los avisos de cada pantalla, que van abajo (deshacer un borrado, errores). `SnackbarHost` no bloquea los toques fuera del mensaje. |
| D20 | Pantalla visible (concreta §3.5) | `@Composable fun TourScreen(visible: Boolean, content: @Composable () -> Unit)` crea el registro de la entrada y provee `LocalTourAnchors` y `LocalTourScreen` (`TourScreenState(anchors, visible)`). `tabEntry` envuelve cada entrada: `TourScreen(visible = activeTab() == tab && stack.lastOrNull() == key) { content(key) }`. `tabEntryProvider` gana `activeTab: () -> Tab`, que `TabsNavigation` pasa como `{ tab }`. | "La pantalla superior de la pestaña activa, o una sin barra" queda definida sin depender de las transiciones de `NavDisplay`, durante las cuales se componen las dos entradas. |
| D21 | Evaluación (concreta §3.5) | `@Composable fun TourHost(tour: Tour?, ready: Boolean = true, enabled: Boolean = true)`. No hace nada sin `LocalTourScreen` o sin `LocalTourCoordinator`. Se evalúa una vez por apertura y por `tour?.id` (con una marca en `remember`, que no se guarda), cuando se cumplen todas estas condiciones: `tour != null`, la pantalla visible, `ready`, `enabled`, `ModalPresence.open == 0`, `LocalWindowInfo.current.isWindowFocused` y nadie presentando. Antes espera la estabilización (D9). Usa `pendingSteps(tour, store.seen.value, LocalExperienceMode.current, screen.ids + bar.ids)`. Si la lista no está vacía, crea el `TourRun` y presenta. | Cubre "solo con la pantalla visible", "no con una hoja o un modal" (también restaurados tras rotar) y el diálogo de permiso del sistema (§4): el foco de la ventana se pierde con cualquier diálogo, menú desplegable o ventana del sistema. Una apertura nueva es una composición nueva de la entrada: cambiar de pestaña, volver desde otra pantalla o rotar. |
| D22 | Presentar un paso (concreta §3.5 y §4) | Para cada paso: revelar el ancla (D10). Si los límites son válidos, `coordinator.present(...)`. Si no, `AnchorLost`, y se sigue con el siguiente. Mientras el paso está en pantalla, si su ancla falta del registro durante `TOUR_STABLE_MS` seguidos, `AnchorLost`. Cada transición aplica `store.markSeen(markSeen)` y, si `skippedHint`, `coordinator.skippedHint()`. | Es la regla del ancla que desaparece (§3.5 y §4). El margen evita saltar un paso por una baja y alta en el mismo fotograma, por ejemplo al recomponer una fila. |
| D23 | Rotación y muerte del proceso (concreta §3.5 y §4; discrepancia 13) | `rememberSaveable(tour?.id)` de `TourRunState(ids: List<String>, index: Int)` con un `listSaver`. Si hay estado guardado, no se vuelve a evaluar: se restaura con `TourRun.restore` y se presenta tras la estabilización, recolocando el hueco y el globo. Lo marcado ya está en `TourSettings`. | Es lo que pide el §3.5. Restaurar la lista de la apertura conserva el "n/N" y la semántica de Saltar. |
| D24 | Cerrar sin marcar (concreta §3.6) | Si `enabled` pasa a `false`, si la pantalla deja de ser visible o si `TourHost` sale de la composición, se aplica `Cancel`: se cierra sin marcar, sin aviso, y se borra el estado guardado. Que la ventana pierda el foco no cierra un recorrido abierto: solo impide empezar uno. | Cubre la cuenta regresiva, el paso a `Active` y una navegación por código (D44 de SPEC-005). Bajar la cortina de notificaciones no debe cerrar el recorrido. |
| D25 | Hojas y modales (concreta §3.5) | `ui/components/ModalPresence.kt`: `@Stable class ModalPresence { var open by mutableIntStateOf(0) }`, `val LocalModalPresence = staticCompositionLocalOf<ModalPresence?> { null }` y `@Composable fun ReportModalPresence()`, que suma 1 al entrar y resta 1 al salir con un `DisposableEffect`. Lo llaman `AntDrawer`, `AntModal`, `AntChoiceModal` y `AntDatePicker` (en la rama del diálogo). Sin proveedor (el primer arranque, los tests sueltos) no hace nada. | Todas las hojas del §1 (Alcance Out) son `AntDrawer`: `ConnectionSheet`, `TargetPicker`, `CoffeePicker`, `SessionOptionsSheet`, `DialLegendSheet` y `GrinderPicker`. Todos los diálogos son `AntModal`, `AntChoiceModal` o `AntDatePicker`. Es determinista en los tests, cosa que el foco de la ventana no garantiza. |

### 2.4 Integración

| ID | Tema | Decisión | Motivo |
|---|---|---|---|
| D26 | Barra de pestañas (concreta §3.7) | `AntTabBar` gana `itemModifier: (T) -> Modifier = { Modifier }`, que se aplica junto al `testTag` de cada pestaña. `TabsNavigation` pasa `{ Modifier.tourAnchor(it.testTag) }` dentro de `TourBar`. Las anclas son `tab_coffees`, `tab_recipes`, `tab_history` y `tab_tools` (`Tab.testTag`). | `AntTabBar` no depende de `ui/tour`. Sin barra (sesión y editores) sus anclas se dan de baja solas. |
| D27 | Sesión (concreta §3.6) | `ui/session/SessionTours.kt` (puro): `internal fun sessionTour(ui: ScaleUiState): Tour?`: con `Finished` → `Tours.summary`; con `Armed` y `scaleless` → `Tours.sessionScaleless`; con `Armed` y `prep != null` → `Tours.sessionPrep`; si no, `null`. `internal fun sessionTourEnabled(ui: ScaleUiState): Boolean = ui.countdownS == null && ui.brew.phase !is BrewPhase.Active`. `SessionScreen` llama a `TourHost(sessionTour(ui), ready = …, enabled = sessionTourEnabled(ui))`. `ready` es `ui.savedBrew is SavedBrew.Saved \|\| ui.savedBrew is SavedBrew.Failed` en el resumen, y `true` en los demás. `SessionTopBar` gana `anchors: Boolean`, que vale `phase is BrewPhase.Armed && ui.countdownS == null`, y con él pone las anclas `session_close` y `session_options`. | Es la tabla del §3.6. En el resumen se espera al guardado porque `summary_details` solo existe al guardar. Una sesión restaurada sin preparación (D44) da `sessionTour == null` y se retira antes de los 300 ms. Sin anclas durante el vertido, `SessionPerformanceTest` no cambia (§4, discrepancia 12). |
| D28 | "Ver recorrido" (concreta §3.6 y §3.8) | En `SettingsScreen`, después de la tarjeta "Guía de pasos" y siempre visible: un `AntCard` sin relleno con un `AntListItem` (título `settings_tour_replay`, descripción `settings_tour_replay_hint`, el icono de flecha a la derecha como "Molinos", `testTag("settings_tour_replay")` y el ancla del mismo nombre). `SettingsScreen` gana el parámetro obligatorio `onReplayTour: () -> Unit`. En `Navigation.kt`: `onReplayTour = { deps.tourSettings.reset(); stack.popToRoot(); selectTab(Tab.PREPARE) }`. | Ajustes solo se abre desde Preparar, así que `stack` es la pila de Preparar y `popToRoot` vuelve a su raíz. La raíz se compone de nuevo, y la evaluación normal muestra el recorrido general desde el paso 1. |
| D29 | Receta activa en Recetas (amplía; respuesta a P2) | `RecipesScreen` gana `activeRecipeId: Long? = null` y pasa `active = item.id == activeRecipeId` a `RecipeRow`. `Navigation.kt` lo saca de `deps.scaleSession.ui` (`(it.target?.key as? ReferenceKey.Recipe)?.id`, con `distinctUntilChanged`). | Sin esto, el paso `recipes.active` del spec no tendría nunca ancla (discrepancia 4). El usuario eligió mantener el paso y añadir la marca. El parámetro por defecto deja igual `RecipesScreenTest`. |
| D30 | Tests existentes y tests de recorridos (concreta §3.3 y §5; respuesta a P1) | `fun skipOnboarding(mode: ExperienceMode = ExperienceMode.BASIC, showTours: Collection<Tour> = emptyList())` llama a `experienceSettings.completeOnboarding(mode)` y a `skipTours(except = showTours)`. Con el valor por defecto apaga todos los recorridos, así que las 21 clases que ya lo llaman no cambian. Se tocan solo tres tests existentes: `NewUserJourneyTest` y `OnboardingTest`, que recorren el primer arranque en la interfaz, llaman a `env.skipTours()` antes de `setContent` (en el test y en `setUp`, respectivamente); y la regla `skipOnboarding` de `SettingsNavigationTest` (la `MainActivity` real) marca también `Tours.allStepIds` en `PourlineApp.tourSettings`. `SettingsScreenTest` añade `onReplayTour` a su llamada (D28) y un caso para la fila nueva. Los tests nuevos de recorridos los activan de forma explícita: con la app ya arrancada, `env.skipOnboarding(showTours = listOf(Tours.coffees, …))` deja pendientes solo los recorridos que prueban; el que recorre el primer arranque en la interfaz usa `env.skipTours(except = listOf(Tours.prepare))`. Ayudantes nuevos en `androidTest/.../ui/tour/TourTestSupport.kt`: `waitForTour(counter: String? = null)`, `tourCounter()`, `tapTourNext()`, `tapTourPrev()`, `tapTourSkip()`, `completeTour()`, `assertNoTourFor(ms: Long = 1_500)` (comprueba cada 50 ms que no hay `tour_overlay`), `recreateWith(env)` e `InMemoryTourStore`. | Es la respuesta del usuario: un solo ayudante deja la app lista para probar otra cosa. Un parámetro, en lugar de un `reset` después de `skipOnboarding()`, deja escrito en cada test nuevo qué recorridos quedan activos y no depende del orden de las llamadas. `skipTours` sigue siendo público para los dos tests que no saltan el primer arranque. |
| D31 | Textos (resuelve la pregunta abierta 1) | Los del §2.7, en `values/strings.xml` y `values-en/strings.xml`, al final de cada archivo y en un bloque con un comentario. Comunes: `tour_next`, `tour_prev`, `tour_done`, `tour_skip`, `tour_counter` (`%1$d/%2$d`, también en `values-en`, porque el proyecto no usa `translatable="false"`), `tour_skipped_hint`, `settings_tour_replay` y `settings_tour_replay_hint`. Los términos son los de la interfaz: "Preparar" es "Brew" en inglés; "Convertidor" y no "conversor"; "báscula simulada"; "Guía de pasos". | El spec deja la redacción al plan. Cada cuerpo tiene una o dos frases y no hay emojis. |
| D32 | Rama y commits | Rama `feat/guided-tour` desde `develop` (`1015b12`). Un commit por fase, en Conventional Commits y en inglés, sin `Co-Authored-By` ni atribución, y solo si se piden. Nunca en `main` ni en `develop`. El del spec y el plan, cuando se apruebe: `docs(specs): add the guided tour spec and its implementation plan (SPEC-008)`. | Las normas del proyecto. |
| D33 | Anclas que no existen al evaluar (discrepancias 5 a 7; respuesta a P3) | a) `summary.rating` se ancla a `summary_details`: `rating` y `details` resaltan el mismo botón con dos textos. b) `session_scaleless.plan` sigue en `scaleless_plan_water`: con receta queda pendiente hasta la primera sesión sin báscula y sin receta. c) `coffee_detail.status` se ancla a `coffee_detail_status`, la tarjeta con los días y el stock. | Con las anclas del spec, `summary.rating` solo saldría en un resumen ya valorado, y `coffee_detail.status` no saldría para un café sin fecha de tueste. |
| D34 | Revisión de los textos (respuesta a P4) | Se implementan los del §2.7 tal cual y no se revisan antes. El usuario los revisa sobre las capturas de F9, en los dos idiomas (§7.6). Los cambios que pida se aplican en F9, y la fase no se cierra sin su visto bueno. | Sobre la pantalla real se ve si caben en el globo y si nombran bien cada elemento. |
| D35 | TalkBack (respuesta a P5) | F0 comprueba si el AVD tiene TalkBack. La semántica (título del panel, región viva y nombre de los botones) se prueba siempre en `TourOverlayTest`. Si el AVD tiene TalkBack, el §7.4 se hace en él. Si no, la escucha real queda como verificación manual del usuario en un teléfono físico, y no se instala nada en el AVD. | El test asegura lo que TalkBack recibe; la escucha real confirma la experiencia sin cambiar el AVD de pruebas. |

### 2.5 Constantes

| Nombre | Valor | Dónde |
|---|---|---|
| `TOUR_STABLE_MS` | 300 ms | `TourHost.kt` (estabilización y ancla perdida) |
| `MAX_REVEAL_STEPS` | 30 | `TourAnchors.kt` (búsqueda en `LazyColumn`) |
| Margen del hueco | 8 dp | `TourOverlay.kt` |
| Separación y flecha | 8 dp | `TourOverlay.kt` |
| Ancho máximo del globo | 360 dp | `TourOverlay.kt` |

### 2.6 Anclas por recorrido

"Nueva" significa `testTag` y ancla nuevos. "Lazy" significa que además se declara con `TourLazyAnchor` (D10), con la
condición de presencia entre paréntesis. `ready` es el parámetro de `TourHost` en esa pantalla.

| Recorrido (`ready`) | Paso | Ancla | Dónde |
|---|---|---|---|
| `prepare` (`true`) | `welcome` | centrado | |
| | `scale` | `scale_card` | `ScaleCard`, por su `modifier`, desde `PrepareScreen` |
| | `recipe` | `prepare_recipe` | `TargetSelectRow` de `PrepareScreen` |
| | `coffee` | `prepare_coffee` | `CoffeeSelectRow` de `PrepareScreen` |
| | `dose` | `prepare_dose_card` (nueva) | `AntCard` de `DoseCard` (`PrepareScreen.kt`) |
| | `grind` | `grind_card` | `GrindCard`, por su `modifier`, desde `PrepareScreen` |
| | `grind_ruler` (E) | `grind_card_ruler` | `GrindRuler` dentro de `GrindCard.kt`. Solo con molino y ajuste, en Experto. |
| | `start` | `prepare_start` | Botón de `PrepareScreen` |
| | `start_scaleless` | `prepare_start_scaleless` | Botón de `PrepareScreen` |
| | `settings` | `prepare_settings` | Icono de la barra superior |
| | `tab_coffees`, `tab_recipes`, `tab_history`, `tab_tools` | `tab_*` | `AntTabBar` (D26), registro de la barra |
| `session_prep` (`true`) | `stepper` | `prep_stepper` | `PrepStepper` (`SessionScreen.kt`) |
| | `weight` | `prep_weight` | Peso de `PrepPanel` |
| | `actions` | `prep_tare` | Botón "Tarar" (solo en los pasos de tara) |
| | `timer` | centrado | |
| | `options` | `session_options` | `SessionTopBar` con `anchors` (D27) |
| | `close` | `session_close` | `SessionTopBar` con `anchors` |
| `session_scaleless` (`true`) | `plan` | `scaleless_plan_water` | `PlanCard` (`ScalelessPanels.kt`), solo sin receta (D33) |
| | `start` | `scaleless_start` | Botón de `ScalelessReadyPanel` |
| | `dial` | centrado | |
| | `close` | `session_close` | `SessionTopBar` con `anchors` |
| `summary` (guardada o fallida) | `rating` | `summary_details` (D33) | Botón "Añadir detalles" de `SessionSummary` |
| | `details` | `summary_details` | Ídem |
| | `share` | `summary_share` | Botón de `SessionSummary` |
| | `expert` (E) | `summary_expert` | `ExpertSummary`, solo con báscula (discrepancia 11) |
| `coffees` (`items != null`) | `add` | `coffees_add` | Icono de la barra superior |
| | `search` | `coffees_search` | `SearchField`. Lazy (`true`) |
| | `tabs` | `coffees_tabs` | `AntSegmented`. Lazy (`true`) |
| | `row` | `coffee_row` | Primera `CoffeeRow`, por su `modifier`. Lazy (`rows` con elementos) |
| `coffee_edit` (`state == Ready`) | `photos` | `coffee_photo_slot_1` | `CoffeePhotoSlot` del hueco 1, por su `modifier` |
| | `read_label` | `coffee_edit_read_label` | Botón |
| | `save` | `coffee_edit_save` | Botón de la barra superior |
| `coffee_detail` (detalle cargado) | `status` | `coffee_detail_status` (D33) | `StatusCard`. Lazy (con fecha de tueste o stock) |
| | `log` | `coffee_detail_log` (nueva) | `Row` del elemento `log_header`. Lazy (`true`) |
| | `new_bag` | `coffee_detail_new_bag` | Botón del elemento `actions`. Lazy (`true`) |
| | `share` | `coffee_detail_share` | Icono de la barra superior |
| `recipes` (`items != null`) | `builtin` | `recipe_builtin_tag` | Etiqueta de la primera predefinida (`builtInAnchor`). Lazy (alguna predefinida) |
| | `active` | `recipe_active_tag` | Etiqueta de la receta activa (D29). Lazy (la activa está en la lista) |
| | `add` | `recipes_add` | Icono de la barra superior |
| `recipe_edit` (cargada) | `method` | `recipe_edit_method` | `AntSelect`, por su `modifier` |
| | `stage` | `recipe_stage_0` | Primera `StageCard` (discrepancia 2) |
| | `add_stage` | `recipe_add_stage` | Botón |
| | `totals` | `recipe_edit_totals` | Texto de los totales |
| | `preview` (E) | `recipe_edit_preview` | `Preview` |
| `history` (`items != null`) | `filters` | `history_filter_coffee` | `HistoryFilters`, por su `modifier`: el hueco cubre los dos filtros. Lazy (`true`) |
| | `progress` (E) | `history_progress` (nueva) | `AntCard` del elemento `progress`. Lazy (con progreso y en Experto) |
| | `row` | `history_row` | Primera `HistoryRow`, por su `modifier`. Lazy (`rows` con elementos) |
| | `scaleless` | `history_row_scaleless` | Etiqueta de la primera fila sin báscula (`scalelessAnchor`). Lazy (alguna sin báscula) |
| `brew_detail` (registro cargado) | `chart` (E) | `brew_detail_chart` (nueva) | Tarjeta de la gráfica real y tarjeta `brew_detail_target_chart` (discrepancia 10) |
| | `details` | `brew_detail_details` | `DetailsCard` |
| | `edit` | `brew_detail_edit` | Botón |
| | `compare` (E) | `brew_detail_compare` | Botón |
| | `save_recipe` (E) | `brew_detail_save_recipe` | Botón |
| | `share` | `brew_detail_share` | Icono de la barra superior |
| `compare` (las dos cargadas) | `chart`, `legend`, `table` | `compare_chart_water`, `compare_legend`, `compare_table` | `BrewCompareScreen` |
| `tools` (`true`) | `ratio`, `four_six`, `converter` | `tools_ratio`, `tools_four_six`, `tools_converter` | `ToolRow` (el `testTag` ya está en su `modifier`) |
| `ratio_calculator` (`true`) | `fields` | `ratio_dose` | Campo del café (discrepancia 1) |
| | `validation` | `ratio_water` | Campo del agua |
| `four_six` (`true`) | `dose` | `four_six_dose` | Campo de la dosis |
| | `sliders` | `four_six_levels` (nueva) | `Column` nueva que envuelve los dos `LevelSlider` de `ProfileCard` |
| | `pours` | `four_six_pour_0` | Primer vertido (discrepancia 3) |
| | `save` | `four_six_save` | Botón |
| `converter` (molinos cargados) | `source` | `converter_source` | `AntSelectTrigger` del origen, por su `modifier` |
| | `rows` | `converter_row` | Primera `ConverterRowItem` (discrepancia 9) |
| `settings` (`true`) | `mode` | `settings_mode` | `AntSegmented` |
| | `grinders` | `settings_grinders` | `AntListItem` de `GrindersRow` |
| | `theme` | `settings_theme` | `AntSegmented` |
| | `alerts` | `settings_stage_vibration` | Interruptor de vibración (`SwitchRow`) |
| | `countdown` | `settings_countdown` | `AntSegmented` |
| | `replay` | `settings_tour_replay` (nueva) | Fila "Ver recorrido" (D28) |

### 2.7 Textos finales

El título va en `tour_<recorrido>_<paso>_title` y el cuerpo en `tour_<recorrido>_<paso>_body` (D5). Los textos del
§3.7 del spec eran un borrador; estos se ajustan a la interfaz de hoy.

**Comunes**

| Recurso | Español | Inglés |
|---|---|---|
| `tour_next` | Siguiente | Next |
| `tour_prev` | Anterior | Previous |
| `tour_done` | Listo | Done |
| `tour_skip` | Saltar | Skip |
| `tour_counter` | %1$d/%2$d | %1$d/%2$d |
| `tour_skipped_hint` | Puedes volver a verlo en Ajustes > Ver recorrido | You can see it again in Settings > Replay tour |
| `settings_tour_replay` | Ver recorrido | Replay tour |
| `settings_tour_replay_hint` | Vuelve a mostrar los recorridos de todas las pantallas. | Shows the tours of every screen again. |

**`prepare`**

| Paso | Título | Cuerpo | Title | Body |
|---|---|---|---|---|
| `welcome` | Te damos la bienvenida | Pourline te guía en cada preparación con tu báscula. En un minuto te enseñamos lo básico. | Welcome to Pourline | Pourline guides every brew with your scale. Here are the basics, in a minute. |
| `scale` | Tu báscula | Conéctala aquí. Si no tienes una a mano, usa la báscula simulada. | Your scale | Connect it here. No scale at hand? Use the simulated scale. |
| `recipe` | Receta | Elige una receta y la sesión te guiará paso a paso. Sin receta, preparas libre. | Recipe | Pick a recipe and the session guides you step by step. Without one, you brew freely. |
| `coffee` | Café | Indica qué café preparas: su bitácora y lo que queda en la bolsa se actualizan solos. | Coffee | Choose the coffee you are brewing: its log and what is left in the bag update on their own. |
| `dose` | Dosis y ratio | La dosis y el ratio dan el agua. Con receta, vienen de ella. | Dose and ratio | Dose and ratio set the water. With a recipe, they come from it. |
| `grind` | Molienda | El ajuste para tu molino. Si eliges otro molino, Pourline convierte el ajuste. | Grind | The setting for your grinder. Pick another grinder and Pourline converts it. |
| `grind_ruler` | Regla de molienda | Muestra dónde cae el ajuste dentro del rango de tu molino. | Grind ruler | Shows where the setting sits within your grinder's range. |
| `start` | Empezar | Con la báscula conectada, Empezar abre la sesión guiada. | Start | With the scale connected, Start opens the guided session. |
| `start_scaleless` | Sin báscula | Sigue la receta solo con el reloj. | Without a scale | Follow the recipe with just the timer. |
| `settings` | Ajustes | Modo, apariencia, molinos, avisos y cuenta regresiva. | Settings | Mode, appearance, grinders, alerts and countdown. |
| `tab_coffees` | Cafés | Tus bolsas, con foto de la etiqueta, fecha de tueste y lo que queda. | Coffees | Your bags, with label photos, roast date and what is left. |
| `tab_recipes` | Recetas | Las predefinidas y las tuyas, paso a paso. | Recipes | Built-in recipes and your own, step by step. |
| `tab_history` | Historial | Cada preparación, con su valoración y sus datos. | History | Every brew, with its rating and details. |
| `tab_tools` | Herramientas | Calculadora de ratio, generador 4:6 y convertidor de molienda. | Tools | Ratio calculator, 4:6 generator and grind converter. |

**`session_prep`**

| Paso | Título | Cuerpo | Title | Body |
|---|---|---|---|---|
| `stepper` | Antes de verter | Cuatro pasos: tarar, pesar el café, tarar de nuevo y verter. | Before you pour | Four steps: tare, weigh the coffee, tare again and pour. |
| `weight` | Peso en vivo | Lo que marca la báscula ahora mismo. | Live weight | What the scale reads right now. |
| `actions` | Tarar o saltar | Tara desde aquí o en la báscula. Cada paso se puede saltar. | Tare or skip | Tare here or on the scale. You can skip any step. |
| `timer` | El reloj | Empieza solo al detectar el vertido, o con Empezar timer tras la cuenta regresiva. Mientras viertes, el dial muestra el agua, el tiempo y el paso. | The timer | It starts by itself when you pour, or with Start timer after the countdown. While you pour, the dial shows the water, the time and the step. |
| `options` | Opciones | Modo experto, unidad, reiniciar y, con la báscula simulada, vertidos de prueba. | Options | Expert mode, unit, restart and, with the simulated scale, test pours. |
| `close` | Salir | Sal de la sesión cuando quieras. | Leave | Leave the session whenever you want. |

**`session_scaleless`**

| Paso | Título | Cuerpo | Title | Body |
|---|---|---|---|---|
| `plan` | El plan | La dosis, el ratio y el agua objetivo. | The plan | Dose, ratio and target water. |
| `start` | Empezar timer | Púlsalo cuando estés listo. La cuenta regresiva se ajusta en Ajustes. | Start timer | Tap it when you are ready. You can set the countdown in Settings. |
| `dial` | Sin báscula | El dial muestra el tiempo de cada paso y avisa al cambiar de paso, también con la pantalla apagada. | No scale | The dial shows the time of each step and alerts you when the step changes, even with the screen off. |
| `close` | Salir | Sal de la sesión cuando quieras. | Leave | Leave the session whenever you want. |

**`summary`**

| Paso | Título | Cuerpo | Title | Body |
|---|---|---|---|---|
| `rating` | Valora y anota | Dale estrellas a la preparación y anota lo que notaste. | Rate it | Give the brew stars and note what you tasted. |
| `details` | Añadir detalles | Completa el nombre, el método, la temperatura y las notas de cata. | Add details | Fill in the name, method, temperature and tasting notes. |
| `share` | Compartir | Crea una imagen de la preparación para compartirla. | Share | Create an image of the brew to share it. |
| `expert` | Métricas | Flujo, estabilidad y ajuste a la curva objetivo. | Metrics | Flow, stability and fit to the target curve. |

**`coffees`**

| Paso | Título | Cuerpo | Title | Body |
|---|---|---|---|---|
| `add` | Registrar café | Añade una bolsa. Con una foto de la etiqueta, Pourline rellena los datos. | Add coffee | Add a bag. With a photo of the label, Pourline fills in the details. |
| `search` | Buscar | Por nombre, tostador u origen. | Search | By name, roaster or origin. |
| `tabs` | Activos y archivados | Los cafés terminados se archivan aquí. | Active and archived | Finished coffees are archived here. |
| `row` | Un café | Ábrelo para ver los días desde el tueste, lo que queda y su bitácora. | A coffee | Open it to see days since roast, what is left and its log. |

**`coffee_edit`**

| Paso | Título | Cuerpo | Title | Body |
|---|---|---|---|---|
| `photos` | Fotos | Fotografía el frente y el reverso de la bolsa. | Photos | Take a photo of the front and the back of the bag. |
| `read_label` | Leer etiqueta | Rellena los campos con el texto de las fotos. Revisa los que salen marcados como leídos. | Read label | Fills in the fields from the photos. Check the ones marked as read. |
| `save` | Guardar | Guarda el café. | Save | Save the coffee. |

**`coffee_detail`**

| Paso | Título | Cuerpo | Title | Body |
|---|---|---|---|---|
| `status` | Estado | Días desde el tueste y café que queda en la bolsa. | Status | Days since roast and coffee left in the bag. |
| `log` | Bitácora | Las preparaciones hechas con este café. | Brew log | The brews made with this coffee. |
| `new_bag` | Nueva bolsa | Empieza otra bolsa del mismo café, o archívalo cuando se acabe. | New bag | Start another bag of the same coffee, or archive it when it is finished. |
| `share` | Compartir | Comparte una ficha del café. | Share | Share a card of the coffee. |

**`recipes`**

| Paso | Título | Cuerpo | Title | Body |
|---|---|---|---|---|
| `builtin` | Predefinidas | Úsalas tal cual o duplícalas para ajustarlas. | Built-in | Use them as they are or duplicate them to adjust them. |
| `active` | Activa | La receta elegida en Preparar. | Active | The recipe chosen in Brew. |
| `add` | Crear receta | Crea tu propia receta paso a paso. | Create recipe | Build your own recipe step by step. |

**`recipe_edit`**

| Paso | Título | Cuerpo | Title | Body |
|---|---|---|---|---|
| `method` | Método | El método de preparación. | Method | The brewing method. |
| `stage` | Pasos | Cada paso es un vertido, un removido o una espera, con su tiempo. | Steps | Each step is a pour, a swirl or a wait, with its timing. |
| `add_stage` | Añadir paso | Añade más pasos a la receta. | Add step | Add more steps to the recipe. |
| `totals` | Totales | El agua, el ratio y la duración de la receta. | Totals | The recipe's water, ratio and duration. |
| `preview` | Curva objetivo | La curva que seguirá la sesión. | Target curve | The curve the session will follow. |

**`history`**

| Paso | Título | Cuerpo | Title | Body |
|---|---|---|---|---|
| `filters` | Filtros | Filtra por café y por método. | Filters | Filter by coffee and by method. |
| `progress` | Progreso | Cómo evoluciona la estabilidad de tus vertidos. | Progress | How the stability of your pours evolves. |
| `row` | Una preparación | Ábrela para ver sus datos. | A brew | Open it to see its details. |
| `scaleless` | Sin báscula | Hechas sin báscula: sin curva y fuera de las comparaciones. | No scale | Made without a scale: no curve, and left out of comparisons. |

**`brew_detail`**

| Paso | Título | Cuerpo | Title | Body |
|---|---|---|---|---|
| `chart` | Gráfica | La curva real del vertido, o la objetivo si fue sin báscula. | Chart | The actual pour curve, or the target one if it was made without a scale. |
| `details` | Detalles | La molienda y el resto de datos de la preparación. | Details | The grind and the rest of the brew's details. |
| `edit` | Editar | Cambia la valoración, las notas, el método y la temperatura. Sin báscula, también la dosis y el agua. | Edit | Change the rating, notes, method and temperature. Without a scale, also the dose and water. |
| `compare` | Comparar | Compárala con otra preparación. | Compare | Compare it with another brew. |
| `save_recipe` | Guardar como receta | Conviértela en una receta. | Save as recipe | Turn it into a recipe. |
| `share` | Compartir | Comparte una imagen de la preparación. | Share | Share an image of the brew. |

**`compare`**

| Paso | Título | Cuerpo | Title | Body |
|---|---|---|---|---|
| `chart` | Curvas | El agua de las dos preparaciones, superpuesta. | Curves | The water of both brews, overlaid. |
| `legend` | Leyenda | Qué color es cada preparación. | Legend | Which color is each brew. |
| `table` | Diferencias | Los datos de las dos, uno a uno. | Differences | Both brews, detail by detail. |

**`tools`**

| Paso | Título | Cuerpo | Title | Body |
|---|---|---|---|---|
| `ratio` | Calculadora de ratio | Calcula el café, el ratio y el agua. | Ratio calculator | Work out coffee, ratio and water. |
| `four_six` | Generador 4:6 | Genera una receta 4:6 según el sabor y la intensidad. | 4:6 generator | Builds a 4:6 recipe from taste and strength. |
| `converter` | Convertidor de molienda | Pasa un ajuste de molienda de un molino a otro. | Grind converter | Translates a grind setting from one grinder to another. |

**`ratio_calculator`**

| Paso | Título | Cuerpo | Title | Body |
|---|---|---|---|---|
| `fields` | Tres campos | Edita cualquiera: con el café o el ratio se recalcula el agua, y con el agua, el café. | Three fields | Edit any of them: coffee or ratio recalculate the water, and water recalculates the coffee. |
| `validation` | Valores no válidos | Un campo que no vale se marca, y el resto se calcula con los otros dos. | Invalid values | An invalid field is marked, and the rest is worked out from the other two. |

**`four_six`**

| Paso | Título | Cuerpo | Title | Body |
|---|---|---|---|---|
| `dose` | Dosis y ratio | La dosis y el ratio dan el agua total. | Dose and ratio | Dose and ratio set the total water. |
| `sliders` | Sabor e intensidad | El sabor reparte el primer 40 %, más dulce o más brillante. La intensidad decide en cuántos vertidos va el 60 % restante. | Taste and strength | Taste splits the first 40%, sweeter or brighter. Strength sets how many pours the remaining 60% takes. |
| `pours` | Vertidos | Cada vertido con su tiempo y su peso acumulado. | Pours | Each pour with its time and running total. |
| `save` | Guardar como receta | Guárdalo como receta para usarlo en Preparar. | Save as recipe | Save it as a recipe to use it in Brew. |

**`converter`**

| Paso | Título | Cuerpo | Title | Body |
|---|---|---|---|---|
| `source` | Origen | El molino y el ajuste de partida. | Source | The grinder and the setting to start from. |
| `rows` | Equivalencias | El ajuste equivalente en tus otros molinos. | Equivalents | The matching setting on your other grinders. |

**`settings`**

| Paso | Título | Cuerpo | Title | Body |
|---|---|---|---|---|
| `mode` | Modo | Básico o Experto. Experto añade gráficas, estabilidad y comparaciones. | Mode | Basic or Expert. Expert adds charts, stability and comparisons. |
| `grinders` | Molinos | Tus molinos y el predeterminado. | Grinders | Your grinders and the default one. |
| `theme` | Apariencia | Claro, oscuro o el del sistema. | Appearance | Light, dark or the system's. |
| `alerts` | Guía de pasos | Vibración y sonido al empezar cada paso. | Step guide | Vibration and sound when each step starts. |
| `countdown` | Cuenta regresiva | Segundos antes de que arranque el reloj. | Countdown | Seconds before the timer starts. |
| `replay` | Ver recorrido | Vuelve a ver todos los recorridos. | Replay tour | See all the tours again. |

## 3. Fases

Orden: F0 → F1 → F2 → F3 → F4 → F5 → F6 → F7 → F8 → F9. F5 a F8 dependen de F4 (desde F4 hay recorridos en
pantalla y todos los tests existentes los apagan), pero no dependen entre sí.

| Fase | Contenido | Tamaño | Estado |
|---|---|---|---|
| F0 | Línea base: dispositivos, tests, lint, rendimiento y TalkBack | S | Pendiente |
| F1 | Lógica pura: modelo, `pendingSteps`, `TourRun` y colocación del globo | S | Pendiente |
| F2 | Persistencia (`TourSettings`), catálogo (`Tours`) y textos | M | Pendiente |
| F3 | Infraestructura: anclas, presencia de modales, coordinador, capa, globo y `TourHost`, con una pantalla de prueba | L | Pendiente |
| F4 | Recorrido general en Preparar, recorrido de Ajustes y "Ver recorrido"; `skipTours()` en los tres tests existentes que no pasan por `skipOnboarding()` | S | Pendiente |
| F5 | Cafés: lista, alta y edición, y detalle | M | Pendiente |
| F6 | Sesión: pasos previos, sin báscula y resumen | M | Pendiente |
| F7 | Recetas, editor de recetas, Historial, detalle y comparación | M | Pendiente |
| F8 | Herramientas, calculadora, 4:6 y convertidor | S | Pendiente |
| F9 | Verificación: suites, lint, rendimiento, prueba manual por adb, TalkBack y revisión de los textos por el usuario | M | Pendiente |

Cada fase deja `:app` compilando y sus tests en verde. Se compila y se prueba solo `:app`, nunca con
`./gradlew build` ni con builds limpios.

- **Tests instrumentados:** siempre con `ANDROID_SERIAL=emulator-5556` (AVD `SG_API34`, en `en-US`). El
  `emulator-5554` es del usuario y no se usa. Si `adb devices -l` muestra otro dispositivo, se pregunta.
- **Sin ARTEMIS.** Los tests de interfaz localizan por `testTag` y leen los textos esperados con
  `context.getString(...)`, por el idioma del emulador.
- **Fallos previos que no bloquean:** `CoffeeDetailScreenTest.theFactsAreFoldedByDefaultAndOpenOnTap` (idioma),
  `GrinderCatalogSeederTest` (pasa aislado), la oscilación de `SessionPerformanceTest` (p95 cerca de 32 ms) y el
  fallo ocasional del runtime de Compose en `SettingsScreenTest` (pasa aislado).
- **Esperas:** los tests esperan el recorrido con `waitUntil` (hasta 5 s), nunca con `sleep`. Para afirmar que no aparece,
  `assertNoTourFor(1_500)`, que cubre de sobra los 300 ms de estabilización.
- **Recorridos activos en los tests nuevos:** cada caso llama a `env.skipOnboarding(showTours = …)` con los recorridos que
  prueba, y el resto queda marcado (D30). Los casos que recorren el primer arranque usan `env.skipTours(except = …)`.

### F0. Línea base

**Objetivo:** conocer el punto de partida antes de tocar código.

**Pasos:**

1. `adb devices -l`: confirmar `emulator-5556`. Si hay más dispositivos además de `emulator-5554`, preguntar.
2. `./gradlew :app:testDebugUnitTest`: anotar el número de tests, fallos y omitidos.
3. `ANDROID_SERIAL=emulator-5556 ./gradlew :app:connectedDebugAndroidTest`: anotar el total y los fallos conocidos.
4. `./gradlew :app:lintDebug`: anotar los errores y los avisos.
5. Tres pasadas de `SessionPerformanceTest` (filtro de clase): anotar p50, p95 y el porcentaje de fotogramas por encima de
   32 ms de los dos casos, y la mediana de las tres (§7.2).
6. `adb -s emulator-5556 shell pm list packages | grep -i -e talkback -e accessibility`: anotar si hay TalkBack (D35). Si no lo hay, no se instala nada.
7. `adb -s emulator-5556 shell dumpsys SurfaceFlinger --display-id`: anotar el id del display para `screencap -d`.

Las cifras van a la cabecera del plan. No hay cambios de código ni commit.

### F1. Lógica pura

**Objetivo:** el modelo, la selección de pasos, las transiciones y la colocación del globo, sin Android y con tests JVM.

**Archivos:**

| Archivo | Cambio |
|---|---|
| `ui/tour/TourModels.kt` (nuevo) | `TourStep`, `Tour`, `pendingSteps` (D2), `TourRun`, `TourAction`, `TourTransition`, `reduce` y `TourRun.restore` (D3). Sin imports de `android.*` ni de Compose UI (solo `androidx.annotation.StringRes` y `ExperienceMode`). |
| `ui/tour/TourPlacement.kt` (nuevo) | `BalloonSide`, `BalloonPlacement` y `placeBalloon` (D16), con `Rect`, `Size` y `Offset` de `androidx.compose.ui.geometry`. |

**Tests:**

| Archivo | Casos |
|---|---|
| `test/.../ui/tour/PendingStepsTest.kt` (nuevo) | `` `unseen steps are pending and seen ones are not` ``. `` `expert only steps are hidden in basic and shown in expert` ``. `` `a step whose anchor is missing is left out and not consumed` ``. `` `a centered step alone is not shown` `` (centrado con todos los anclados vistos, y con todos sin ancla presente). `` `a centered step is shown when an anchored step is pending` ``. `` `the order is the tour order` ``. `` `the counter is over the pending list of the opening` `` (`position/count` del `TourRun` con 2 de 5 vistos da 1/3). `` `unknown seen ids do not change anything` ``. `` `after a reset every visible step is pending again` `` (`seen = emptySet()`). |
| `test/.../ui/tour/TourRunTest.kt` (nuevo) | `` `next marks the current step and moves on` ``. `` `next on the last step marks it and closes without a hint` ``. `` `previous goes back without marking and does nothing on the first step` ``. `` `back on the first step skips with the hint` ``. `` `back on a later step goes to the previous one` ``. `` `skip marks only the steps of this opening` `` (no marca pasos de otro recorrido, ni los E en Básico, ni los que no tenían ancla). `` `skip after an anchor was lost does not mark the lost step` ``. `` `anchor lost drops the step unmarked and shows the next one` ``. `` `anchor lost on the only remaining step closes without a hint` ``. `` `cancel closes without marking and without a hint` ``. `` `restore drops unknown ids and clamps the index` ``. |
| `test/.../ui/tour/TourPlacementTest.kt` (nuevo) | `` `without a hole the balloon is centered in the safe area` ``. `` `below when it fits` ``. `` `above when it does not fit below` ``. `` `beside when it fits neither above nor below (landscape)` ``. `` `x is centered on the hole and clamped to the safe area` ``. `` `the balloon never leaves the safe area even if it covers the hole` `` (un hueco que ocupa casi toda la pantalla). `` `the arrow points to the hole center within the balloon edge` ``. |

**Verificación:** `./gradlew :app:testDebugUnitTest --tests "dev.josueamaya.pourline.ui.tour.*"`.
`grep -n "import android" app/src/main/kotlin/dev/josueamaya/pourline/ui/tour/TourModels.kt app/src/main/kotlin/dev/josueamaya/pourline/ui/tour/TourPlacement.kt`
no encuentra nada.

Commit sugerido: `feat(tour): add the tour model, step selection and balloon placement`.

### F2. Persistencia, catálogo y textos

**Objetivo:** guardar lo visto, declarar los 17 recorridos con sus anclas y tener todos los textos en los dos idiomas.
Aún no se ve nada en la app.

**Archivos:**

| Archivo | Cambio |
|---|---|
| `settings/TourSettings.kt` (nuevo) | `TourStore` y `TourSettings` (D4). |
| `ui/tour/Tours.kt` (nuevo) | `object Tours` con los 17 recorridos del §2.6, `all` y `allStepIds` (D5). |
| `res/values/strings.xml`, `res/values-en/strings.xml` | Los recursos del §2.7: 154 de pasos y 8 comunes por idioma (D31). |
| `PourlineApp.kt` | `val tourSettings by lazy { TourSettings(this) }` y el campo en `dependencies`. |
| `AppDependencies.kt` | `val tourSettings: TourSettings`. |
| `androidTest/.../ui/TestAppDependencies.kt` | `tourSettings`, `skipTours(except)` y el parámetro `showTours` de `skipOnboarding` (D6 y D30). Desde aquí `skipOnboarding()` apaga los recorridos, aunque todavía no se muestre ninguno. |

**Tests:**

| Archivo | Casos |
|---|---|
| `test/.../settings/FakeSharedPreferences.kt` (nuevo) | `SharedPreferences` en memoria, con su `Editor` (`putStringSet`, `putString`, `remove`, `apply`, `commit`). |
| `test/.../settings/TourSettingsTest.kt` (nuevo) | `` `starts empty` ``. `` `mark seen adds and publishes the ids` ``. `` `mark seen twice keeps the union and writes once` ``. `` `reset empties the set and removes the key` ``. `` `a new instance reads what the previous one wrote` ``. `` `a value of another type under tour_seen reads as empty` ``. `` `unknown ids are kept but do not matter` ``. `` `onboarding_done does not change the seen steps` ``. |
| `test/.../ui/tour/ToursCatalogTest.kt` (nuevo) | `` `step ids are unique across all tours` ``. `` `every step id starts with its tour id` ``. `` `there are the seventeen tours of the spec in order` `` (ids y número de pasos). `` `every step points to a title and a body named after its id` ``: con `R.string::class.java.fields` se pasa del id del recurso a su nombre, y se compara con `tour_<recorrido>_<paso>_title`. `` `every resource exists in values and values-en` ``: lee `src/main/res/values/strings.xml` y `src/main/res/values-en/strings.xml` con `DocumentBuilderFactory`. `` `bodies have at most two sentences and no emoji` ``. `` `expert only steps match the spec` `` (`prepare.grind_ruler`, `summary.expert`, `recipe_edit.preview`, `history.progress`, `brew_detail.chart`, `brew_detail.compare` y `brew_detail.save_recipe`). `` `every tour has at least one anchored step` ``. |
| `androidTest/.../settings/TourSettingsPersistenceTest.kt` (nuevo) | `aSecondInstanceReadsTheSeenSteps`. `aCorruptValueReadsAsEmpty` (un `String` bajo `tour_seen`). `completingOnboardingDoesNotTouchTheTours` (`ExperienceSettings.completeOnboarding` y `TourSettings` en el mismo archivo). Usa un archivo de preferencias propio y lo borra al terminar. |

**Verificación:** `./gradlew :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin`.
`./gradlew :app:testDebugUnitTest --tests "dev.josueamaya.pourline.settings.*" --tests "dev.josueamaya.pourline.ui.tour.*"`.
`ANDROID_SERIAL=emulator-5556 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.josueamaya.pourline.settings.TourSettingsPersistenceTest`.
`./gradlew :app:lintDebug` sin `MissingTranslation` ni `ExtraTranslation` nuevos.

Commit sugerido: `feat(tour): persist seen tour steps and declare every tour with its texts`.

### F3. Infraestructura

**Objetivo:** el recorrido funciona sobre una pantalla de prueba: anclas, estabilización, desplazamiento, velo, globo,
botones, "Atrás", aviso, accesibilidad, hojas y modales. La app aún no tiene ningún `TourHost`, así que los tests
existentes no cambian.

**Archivos:**

| Archivo | Cambio |
|---|---|
| `ui/tour/TourAnchors.kt` (nuevo) | `TourAnchorRegistry`, `LocalTourAnchors`, `Modifier.tourAnchor`, `TourAnchorElement`, `TourAnchorNode`, `TourLazyAnchor` y `MAX_REVEAL_STEPS` (D7, D8 y D10). |
| `ui/tour/TourCoordinator.kt` (nuevo) | `TourCoordinator`, `TourPresentation` y `LocalTourCoordinator` (D13). |
| `ui/tour/TourHost.kt` (nuevo) | `TourScreenState`, `LocalTourScreen`, `TourScreen`, `TourHost`, `awaitStableAnchors`, `TOUR_STABLE_MS` y el `Saver` de `TourRunState` (D9 y D20 a D24). |
| `ui/tour/TourOverlay.kt` (nuevo) | `TourRoot`, `TourBar`, `TourLayer` (velo, globo, flecha, `BackHandler` y `AntMessageHost`) (D12, D14, D15 y D17 a D19). |
| `ui/components/ModalPresence.kt` (nuevo) | `ModalPresence`, `LocalModalPresence` y `ReportModalPresence` (D25). |
| `ui/components/AntDrawer.kt`, `AntModal.kt` (`AntModal` y `AntChoiceModal`), `AntDatePicker.kt` | Llaman a `ReportModalPresence()`. |
| `ui/components/AntTabBar.kt` | `itemModifier` (D26). |
| `Navigation.kt` | `TabsNavigation` envuelve su `Column` en `TourRoot(deps.tourSettings)` y la barra en `TourBar`, con `itemModifier = { Modifier.tourAnchor(it.testTag) }`. `tabEntry` envuelve el contenido en `TourScreen` (D20). `tabEntryProvider` gana `activeTab`. |

**Tests:**

| Archivo | Casos |
|---|---|
| `androidTest/.../ui/tour/TourTestSupport.kt` (nuevo) | Los ayudantes de D30, e `InMemoryTourStore`. `TourHarness(store, tour, visible, ready, enabled)`: `TourRoot(store) { TourScreen(visible) { FakeScreen(); TourHost(tour, ready, enabled) } }`. `FakeScreen` tiene un botón anclado con un contador de toques, un `BackHandler` propio con contador, un elemento anclado al final de una `Column` larga con `verticalScroll`, una `LazyColumn` de 100 elementos con un ancla en el 60 declarada con `TourLazyAnchor`, y un interruptor para abrir un `AntModal`. Los recorridos de prueba usan cadenas del catálogo. |
| `androidTest/.../ui/tour/TourOverlayTest.kt` (nuevo) | `aPendingTourShowsTheFirstStepWithTheCounter` (`tour_overlay`, `tour_title`, `tour_body` y `tour_counter` "1/3"; sin `tour_prev`). `nextMarksAndDoneCloses` ("Listo" en el último). `previousGoesBackWithoutMarking`. `skipMarksOnlyThisOpeningAndShowsTheHint` (un E en Básico y un ancla ausente quedan sin marcar; el aviso `tour_skipped_hint` se ve). `systemBackStepsBackAndOnTheFirstStepSkipsWithTheHint` (el `BackHandler` de la pantalla no se llama). `tapsOnTheVeilAndOnTheHighlightedElementDoNothing` (`performClick` sobre el botón anclado y sobre una esquina: el contador sigue a 0). `aCenteredStepAloneIsNotShown`. `anOpenModalDelaysTheTourUntilItCloses`. `notReadyDelaysTheTour`. `disablingTheHostClosesTheTourWithoutMarking`. `aVanishingAnchorIsSkippedWithoutMarking`. `anAnchorBelowTheFoldIsScrolledIntoView` (los límites del ancla quedan dentro de la raíz). `aLazyAnchorIsFoundByScrolling`. `theBalloonStaysInsideTheRoot` (límites de `tour_balloon`). `theBalloonIsAnnounced` (`SemanticsProperties.PaneTitle` y `LiveRegion` en el globo; `tour_next`, `tour_prev` y `tour_skip` tienen texto). `theContentStillHasItsNodesForTests` (con el velo puesto, `onNodeWithTag` sigue encontrando el botón, R4). `withoutTourScreenNothingHappens`. `rotationKeepsTheStep` (`recreateWith`, en el paso 2 sigue en "2/3"). |

**Verificación:** `./gradlew :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin`.
`ANDROID_SERIAL=emulator-5556 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.josueamaya.pourline.ui.tour.TourOverlayTest,dev.josueamaya.pourline.ui.TabsNavigationTest,dev.josueamaya.pourline.ui.components.AntComponentsTest`.
Si `TourOverlayTest` no ve el recorrido, comprobar el foco con
`adb -s emulator-5556 shell dumpsys window | grep mCurrentFocus` durante el test (R2).

Commit sugerido: `feat(tour): show tours over the real UI with a spotlight overlay`.

### F4. Recorrido general, Ajustes y tests existentes

**Objetivo:** el recorrido general en Preparar tras el primer arranque, el de Ajustes con "Ver recorrido", y los tests
existentes en verde. Los que llaman a `skipOnboarding()` ya apagan los recorridos desde F2; aquí se tocan los tres que no
pasan por él (D30).

**Archivos:**

| Archivo | Cambio |
|---|---|
| `ui/prepare/PrepareScreen.kt` | `TourHost(Tours.prepare)`. Las anclas del §2.6: `scale_card` y `grind_card` por el `modifier`, `prepare_dose_card` (nueva) en `DoseCard`, y el resto en su sitio. |
| `ui/grinders/GrindCard.kt` | Ancla `grind_card_ruler` en el `modifier` de `GrindRuler`. |
| `ui/settings/SettingsScreen.kt` | `onReplayTour`, la fila "Ver recorrido" (D28), `TourHost(Tours.settings)` y sus anclas. |
| `Navigation.kt` | `onReplayTour` (D28). |
| `androidTest/.../ui/session/NewUserJourneyTest.kt` | `env.skipTours()` antes de `setContent`. |
| `androidTest/.../ui/onboarding/OnboardingTest.kt` | `env.skipTours()` en `setUp`. |
| `androidTest/.../ui/SettingsNavigationTest.kt` | La regla `skipOnboarding` marca también `Tours.allStepIds` en `PourlineApp.tourSettings`. |
| `androidTest/.../ui/SettingsScreenTest.kt` | `onReplayTour` en la llamada. |

**Tests:**

| Archivo | Casos |
|---|---|
| `androidTest/.../ui/tour/GeneralTourTest.kt` (nuevo; `TestApp(env)`) | `theGeneralTourFollowsTheFirstLaunchAndDoesNotReturnAfterARestart`: sin `skipOnboarding` y con `env.skipTours(except = listOf(Tours.prepare))`, el primer arranque se salta con su botón, como en `OnboardingTest.skipOnTheFirstStep…`. Sale `tour_overlay` con "1/13" (Básico: todos menos `grind_ruler`). `completeTour()` lo cierra y deja los 13 ids en `env.tourSettings.seen`. `recreateWith(env)` y `assertNoTourFor()`. `skipClosesTheGeneralTourAndShowsTheHint`. `previousGoesBackAndBackOnTheFirstStepClosesWithTheHint`: Siguiente, Anterior (vuelve a "1/13"), "Atrás" (cierra, aviso). Después, pasar a Herramientas (con su recorrido marcado), volver a Preparar y `assertNoTourFor()`. `afterTheBasicTourExpertShowsOnlyTheRuler`: con el molino y la receta de `GrindCardTest` (`env.db.addGrinder(TestGrinders.uno)` y `selectTarget`) y `env.skipOnboarding(showTours = listOf(Tours.prepare))`, completar el general en Básico, pasar a Experto en Ajustes y volver: "1/1" con el título de `grind_ruler`. `replayInSettingsGoesBackToPrepareFromStepOne`: con `env.skipOnboarding()` (todo marcado), abrir Ajustes y pulsar `settings_tour_replay`: Preparar en la raíz con "1/13". `tappingTheHighlightedRecipeDoesNotOpenThePicker`: en el paso `prepare.recipe`, `performClick` sobre `prepare_recipe` y no aparece `target_picker`. `theTabBarStepsHighlightTheTabs`: en el paso `tab_coffees`, los límites del hueco contienen los de `tab_coffees` (con `tour_balloon` por encima). `theSettingsTourHasSixStepsAndTheReplayRow` (`showTours = listOf(Tours.settings)`). `rotationKeepsTheCurrentStep` (en "3/13", `recreateWith`, sigue en "3/13"). |
| `androidTest/.../ui/SettingsScreenTest.kt` | `theReplayRowCallsBack`. |

**Verificación:** `./gradlew :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin`.
`ANDROID_SERIAL=emulator-5556 ./gradlew :app:connectedDebugAndroidTest` (la suite completa: es la primera fase con un
recorrido en pantalla, y el cambio de `skipOnboarding()` afecta a 21 clases). Se compara con F0: solo suben los totales.
`grep -L -e "skipOnboarding" -e "skipTours" $(grep -rl "TestApp(env)" app/src/androidTest)` no muestra ninguna clase.

Commit sugerido: `feat(tour): add the general tour on Brew and replay it from settings`.

### F5. Cafés

**Objetivo:** los recorridos `coffees`, `coffee_edit` y `coffee_detail`, y el criterio de Saltar que necesita Cafés.

**Archivos:**

| Archivo | Cambio |
|---|---|
| `ui/coffees/CoffeesScreen.kt` | `rememberLazyListState()` elevado, `TourHost(Tours.coffees, ready = items != null)`, anclas y `TourLazyAnchor` del §2.6. |
| `ui/coffees/CoffeeEditScreen.kt` | `TourHost(Tours.coffeeEdit, ready = state == CoffeeEditState.Ready)` y anclas (la del hueco 1 por el `modifier` de `CoffeePhotoSlot`). |
| `ui/coffees/CoffeeDetailScreen.kt` | `rememberLazyListState()` elevado, `TourHost(Tours.coffeeDetail, ready = …)`, `coffee_detail_log` (nueva) y las anclas y declaraciones del §2.6. |

**Tests:**

| Archivo | Casos |
|---|---|
| `androidTest/.../ui/tour/CoffeesTourTest.kt` (nuevo; `skipOnboarding(showTours = …)` con los recorridos de Cafés) | `skippingTheGeneralTourStillShowsTheCoffeesTour` (criterio 4, con `showTours = listOf(Tours.prepare, Tours.coffees)`: Saltar el general, aviso, pestaña Cafés con su recorrido). `withoutCoffeesTheRowStepWaitsForALaterOpening`: "1/3", completar; guardar un café; ir a otra pestaña y volver: "1/1" con `coffees.row`. `aScrolledListStillShowsTheSearchStep` (lista larga desplazada, recorrido sin ver: `coffees_search` se revela). `theEditTourShowsItsThreeSteps`. `theDetailTourRevealsNewBagBelowALongLog` (un café con 20 preparaciones: en el paso `new_bag`, el botón está en pantalla). `aCoffeeWithoutRoastOrStockSkipsTheStatusStep`. |

**Verificación:** `./gradlew :app:compileDebugKotlin`.
`ANDROID_SERIAL=emulator-5556 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.josueamaya.pourline.ui.tour.CoffeesTourTest,dev.josueamaya.pourline.ui.coffees.CoffeesScreenTest,dev.josueamaya.pourline.ui.coffees.CoffeeEditScreenTest,dev.josueamaya.pourline.ui.coffees.CoffeeDetailScreenTest,dev.josueamaya.pourline.ui.coffees.CoffeePerformanceTest`.

Commit sugerido: `feat(coffees): add guided tours to the coffee screens`.

### F6. Sesión

**Objetivo:** los recorridos de la sesión antes de empezar y del resumen, nunca durante la cuenta regresiva ni el vertido.

**Archivos:**

| Archivo | Cambio |
|---|---|
| `ui/session/SessionTours.kt` (nuevo) | `sessionTour` y `sessionTourEnabled` (D27). |
| `ui/session/SessionScreen.kt` | `TourHost(sessionTour(ui), ready = …, enabled = sessionTourEnabled(ui))`. `SessionTopBar(…, anchors)`. Anclas de `PrepPanel`, `PrepStepper` y `PrepButtons`. |
| `ui/session/ScalelessPanels.kt` | Anclas `scaleless_plan_water` y `scaleless_start`. |
| `ui/session/SessionSummary.kt` | Anclas `summary_details`, `summary_share` y `summary_expert` (este, solo con báscula). |

**Tests:**

| Archivo | Casos |
|---|---|
| `test/.../ui/session/SessionToursTest.kt` (nuevo) | `` `prep steps with a scale give the prep tour` ``. `` `a scaleless armed session gives the scaleless tour` ``. `` `finished gives the summary tour` ``. `` `active gives no tour` ``. `` `armed without prep and not scaleless gives no tour` `` (D44). `` `a visible countdown disables the tour` ``. `` `active disables the tour` ``. |
| `androidTest/.../ui/tour/SessionTourTest.kt` (nuevo; reloj de prueba como `ScalelessSessionScreenTest`; `skipOnboarding(showTours = …)` con los recorridos de la sesión que prueba cada caso) | `thePrepTourShowsBeforePouring` (`SessionDriver.connect()` y `start()`: "1/6"). `goingActiveClosesThePrepTourWithoutMarking`: con el recorrido en "2/6", llevar la preparación a `Active` desde el gestor (`prepSkip` hasta el vertido y `prepStartTimer` con cuenta 0): el velo desaparece y solo `session_prep.stepper` queda en `seen`. `systemBackDuringThePrepTourDoesNotLeaveTheSession`. `theScalelessTourShowsWhenReady`: sin receta "1/4"; con la V60, "1/3" (D33). `noTourDuringTheCountdown`: con `countdownSeconds = 5` y `env.skipOnboarding(showTours = listOf(Tours.sessionScaleless, Tours.summary))`, completar el recorrido sin báscula, pulsar `scaleless_start`. Con `session_countdown` visible, `env.tourSettings.reset()` y `recreateWith(env)` (apertura nueva con la cuenta en marcha): `assertNoTourFor()` mientras dura la cuenta y ya en `Active`. `theSummaryTourWaitsForTheSave`: al terminar, el recorrido del resumen sale con `summary_details` ya presente. |
| `androidTest/.../ui/session/SessionScreenTest.kt`, `ScalelessSessionScreenTest.kt`, `CountdownSessionTest.kt` y `SessionLayoutTest.kt` | Sin cambios (`skipOnboarding()` ya apaga los recorridos desde F2). Se ejecutan para comprobar que la sesión no cambia. |

**Verificación:** `./gradlew :app:testDebugUnitTest --tests "dev.josueamaya.pourline.ui.session.*"`.
`ANDROID_SERIAL=emulator-5556 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.josueamaya.pourline.ui.tour.SessionTourTest,dev.josueamaya.pourline.ui.session.SessionScreenTest,dev.josueamaya.pourline.ui.session.ScalelessSessionScreenTest,dev.josueamaya.pourline.ui.session.CountdownSessionTest,dev.josueamaya.pourline.ui.session.SessionLayoutTest,dev.josueamaya.pourline.ui.session.SessionTopBarTest`.
Tres pasadas de `SessionPerformanceTest`, comparadas con F0 (§7.2).

Commit sugerido: `feat(session): add guided tours before brewing and on the summary`.

### F7. Recetas e Historial

**Objetivo:** los recorridos `recipes`, `recipe_edit`, `history`, `brew_detail` y `compare`.

**Archivos:**

| Archivo | Cambio |
|---|---|
| `ui/recipes/RecipesScreen.kt` | `rememberLazyListState()` elevado, `activeRecipeId` (D29), `TourHost(Tours.recipes, ready = items != null)` y declaraciones. |
| `ui/recipes/RecipeRow.kt` | `builtInAnchor` (D11) y el ancla `recipe_active_tag` en la etiqueta "Activa". |
| `ui/recipes/RecipeEditScreen.kt` | `TourHost(Tours.recipeEdit, ready = status != RecipeEditStatus.Loading)` y anclas. |
| `ui/history/HistoryScreen.kt` | `rememberLazyListState()` elevado, `TourHost(Tours.history, ready = items != null)`, `history_progress` (nueva), anclas y declaraciones. |
| `ui/history/HistoryRow.kt` | `scalelessAnchor` (D11). |
| `ui/history/BrewDetailScreen.kt` | `TourHost(Tours.brewDetail, ready = …)`, `brew_detail_chart` (nueva) en las dos tarjetas, y anclas. |
| `ui/history/BrewCompareScreen.kt` | `TourHost(Tours.compare, ready = …)` y anclas. |
| `Navigation.kt` | `activeRecipeId` para `RecipesScreen` (D29). |

**Tests:**

| Archivo | Casos |
|---|---|
| `androidTest/.../ui/tour/RecipesHistoryTourTest.kt` (nuevo; `skipOnboarding(showTours = …)` con los recorridos que prueba cada caso) | `theRecipesTourShowsBuiltInAndAdd` ("1/2" sin receta activa). `theActiveRecipeStepShowsWhenARecipeIsChosen` (con una receta elegida en Preparar, una apertura posterior: "1/1"). `theRecipeEditTourHasFourStepsInBasicAndFiveInExpert`. `anEmptyHistoryShowsOnlyTheFilters` y, con una preparación, `theRowStepShowsInALaterOpening`. `theScalelessStepPointsToTheFirstScalelessRow` (diez con báscula y una sin báscula al final: se revela). `theProgressStepIsExpertOnly`. `theBrewDetailTourHasThreeStepsInBasicAndSixInExpert`. `theCompareTourShowsItsThreeSteps` (Experto). |
| `androidTest/.../ui/recipes/RecipesScreenTest.kt` | `theActiveRecipeIsTagged`. |

**Verificación:** `./gradlew :app:compileDebugKotlin`.
`ANDROID_SERIAL=emulator-5556 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.josueamaya.pourline.ui.tour.RecipesHistoryTourTest,dev.josueamaya.pourline.ui.recipes.RecipesScreenTest,dev.josueamaya.pourline.ui.recipes.RecipeEditScreenTest,dev.josueamaya.pourline.ui.history.HistoryScreenTest,dev.josueamaya.pourline.ui.history.BrewDetailScreenTest,dev.josueamaya.pourline.ui.history.BrewCompareScreenTest,dev.josueamaya.pourline.ui.history.HistoryPerformanceTest,dev.josueamaya.pourline.ui.history.ComparePerformanceTest`.

Commit sugerido: `feat(history): add guided tours to recipes, history, brew detail and compare`.

### F8. Herramientas

**Objetivo:** los recorridos `tools`, `ratio_calculator`, `four_six` y `converter`.

**Archivos:**

| Archivo | Cambio |
|---|---|
| `ui/tools/ToolsScreen.kt` | `TourHost(Tours.tools)`. `ToolRow` pone el ancla con el mismo `tag`. |
| `ui/tools/RatioCalculatorScreen.kt` | `TourHost(Tours.ratioCalculator)`. Anclas en `RatioInput` para `DOSE` y `WATER`. |
| `ui/tools/FourSixScreen.kt` | `TourHost(Tours.fourSix)`, la `Column` con `four_six_levels` (nueva) y las anclas. |
| `ui/grinders/GrindConverterScreen.kt` | `TourHost(Tours.converter, ready = …)`, `converter_source` y `converter_row` en la primera fila. |

**Tests:**

| Archivo | Casos |
|---|---|
| `androidTest/.../ui/tour/ToolsTourTest.kt` (nuevo; `skipOnboarding(showTours = …)` con los recorridos de Herramientas) | `theToolsTourShowsItsThreeRows`. `theRatioTourShowsItsTwoFields`. `theFourSixTourShowsItsFourSteps` (en el paso `sliders`, los dos sliders dentro del hueco). `theConverterTourWithoutASourceShowsOnlyTheSource` y, con origen y otro molino, `theRowsStepShowsInALaterOpening`. |

**Verificación:** `./gradlew :app:compileDebugKotlin`.
`ANDROID_SERIAL=emulator-5556 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.josueamaya.pourline.ui.tour.ToolsTourTest,dev.josueamaya.pourline.ui.tools.ToolsScreenTest,dev.josueamaya.pourline.ui.grinders.GrindConverterScreenTest`.

Commit sugerido: `feat(tools): add guided tours to the tools`.

### F9. Verificación

**Objetivo:** el §7 completo, y cerrar con el visto bueno del usuario a los textos.

**Pasos:**

1. Suites y lint (§7.1).
2. Rendimiento contra F0 (§7.2).
3. Prueba manual por adb (§7.3), con las capturas del §7.5.
4. TalkBack (§7.4): en el AVD si F0 lo encontró. Si no, se anota como verificación manual pendiente del usuario en un
   teléfono físico (D35).
5. **Revisión de los textos por el usuario con las capturas (§7.6, D34).** Es el último paso: F9 no se cierra sin su visto
   bueno.

**Archivos:** solo los arreglos que salgan de la verificación, los textos que cambie el usuario (`values/strings.xml`,
`values-en/strings.xml` y el §2.7 de este plan), y las cifras y los resultados en la cabecera de este plan.

Commit sugerido, si hay cambios: `fix(tour): …` o `test(tour): …`, según lo que salga.

## 4. Trazabilidad con el §5 del spec

| Criterio | Fase | Prueba |
|---|---|---|
| Tests JVM de `pendingSteps`: vistos y no vistos, Básico y Experto, anclas ausentes, paso centrado solitario, orden y contador | F1 | `PendingStepsTest` |
| Tests JVM de Saltar (solo los pasos visibles de esa apertura) y de `reset` | F1, F2 | `TourRunTest` (`skip marks only the steps of this opening`, `skip after an anchor was lost…`) y `TourSettingsTest` (`reset…`); `PendingStepsTest.after a reset…` |
| Tras el primer arranque, el general en Preparar con "1/N"; completado y reiniciada la actividad, no reaparece | F4 | `GeneralTourTest.theGeneralTourFollowsTheFirstLaunchAndDoesNotReturnAfterARestart` |
| Saltar cierra el general, muestra `tour_skipped_hint` y el recorrido de Cafés aparece igualmente | F4, F5 | `GeneralTourTest.skipClosesTheGeneralTourAndShowsTheHint` y `CoffeesTourTest.skippingTheGeneralTourStillShowsTheCoffeesTour` |
| Anterior retrocede; "Atrás" en el primer paso cierra con el aviso; la siguiente apertura no lo muestra | F3, F4 | `TourOverlayTest.previousGoesBackWithoutMarking` y `…systemBackStepsBack…`; `GeneralTourTest.previousGoesBackAndBackOnTheFirstStepClosesWithTheHint` |
| Con el general completado en Básico, pasar a Experto y volver a Preparar muestra solo `prepare.grind_ruler` | F4 | `GeneralTourTest.afterTheBasicTourExpertShowsOnlyTheRuler` |
| "Ver recorrido" vuelve a Preparar y muestra el general desde el paso 1 | F4 | `GeneralTourTest.replayInSettingsGoesBackToPrepareFromStepOne` |
| El recorrido sin báscula aparece en el estado listo y no con `session_countdown` visible | F6 | `SessionTourTest.theScalelessTourShowsWhenReady` y `…noTourDuringTheCountdown`; `SessionToursTest` |
| Con el globo visible, tocar el elemento resaltado no ejecuta su acción | F3, F4 | `TourOverlayTest.tapsOnTheVeilAndOnTheHighlightedElementDoNothing` y `GeneralTourTest.tappingTheHighlightedRecipeDoesNotOpenThePicker` |
| Textos de cada recorrido en `values` y `values-en`; un test comprueba los recursos en los dos idiomas y la unicidad de los ids | F2, F9 | `ToursCatalogTest`; `:app:lintDebug` sin `MissingTranslation`; revisión del usuario sobre las capturas (§7.6, D34) |
| Los tests instrumentados existentes pasan, con `skipTours()` donde haga falta | F2, F4, F9 | `skipOnboarding()` apaga los recorridos (F2, D30) y los tres tests que no pasan por él llaman a `skipTours()` (F4); suite completa en F4 y en §7.1; la comprobación con `grep` de F4 |
| Verificación manual por adb, sin ARTEMIS: cada recorrido en claro y en oscuro, el globo dentro de la pantalla, en vertical y un caso en horizontal | F9 | §7.3 |
| Con TalkBack, el título y el texto se anuncian al cambiar de paso | F3, F9 | `TourOverlayTest.theBalloonIsAnnounced` (semántica, siempre); §7.4 en el AVD si tiene TalkBack, o verificación manual del usuario en un teléfono físico si no (D35) |
| `SessionPerformanceTest` no empeora respecto a `develop` (`1015b12`) | F0, F6, F9 | §7.2: tres pasadas en F0, F6 y F9 |

## 5. Riesgos

| ID | Riesgo | Mitigación |
|---|---|---|
| R1 | El `BackHandler` del recorrido pierde frente al de `SessionScreen`, el de un editor con cambios o el de `NavDisplay`, y "Atrás" sale de la pantalla. | Se compone solo mientras hay un paso en pantalla, así que se registra el último y gana (D18). `TourOverlayTest` prueba un `BackHandler` de pantalla siempre activo, y `SessionTourTest.systemBackDuringThePrepTourDoesNotLeaveTheSession` lo prueba en la sesión. |
| R2 | `isWindowFocused` es `false` en los tests del emulador y el recorrido no aparece nunca. | F3 lo detecta enseguida (`TourOverlayTest`) y se comprueba con `dumpsys window`. Plan B: quitar la condición del foco y cubrir el diálogo del permiso de notificaciones con una marca propia (`PrepareScreen` la activa al lanzar el permiso y la pasa a la sesión), que se suma a `ModalPresence`. Los menús desplegables no pueden abrirse con el velo puesto. |
| R3 | `DelegatableNode.bringIntoView` no existe en la versión de Compose de la BOM `2026.03.01`. | Alternativa: `Modifier.bringIntoViewRequester(requester)` con un `BringIntoViewRequester` creado en el nodo o en un `@Composable` que envuelve el modificador. Solo cambia `TourAnchors.kt`. |
| R4 | `hideFromAccessibility()` saque los nodos del árbol que usan los tests (`onNodeWithTag`). | `TourOverlayTest.theContentStillHasItsNodesForTests`. Si falla, se cambia por `isTraversalGroup` y `traversalIndex = -1f` en el globo, que lo deja primero para TalkBack. El contenido seguiría siendo accesible, pero el spec no exige ocultarlo. |
| R5 | La búsqueda en una `LazyColumn` da tirones o no encuentra el ancla. | Un máximo de 30 pasos del 80 % del alto. Si no lo encuentra, el paso se trata como ausente, sin marcar (§4). Lo prueban `aLazyAnchorIsFoundByScrolling`, `theDetailTourRevealsNewBagBelowALongLog` y `theScalelessStepPointsToTheFirstScalelessRow`. |
| R6 | El registro de anclas empeora el rendimiento (escrituras al desplazar, sesión). | Nada en composición lee los límites, y se escriben solo si cambian (D7 y D8). Durante el vertido no hay anclas (D27). `SessionPerformanceTest` en F0, F6 y F9; `HistoryPerformanceTest`, `ComparePerformanceTest` y `CoffeePerformanceTest` en F5 y F7. |
| R7 | Los 300 ms de estabilización hacen lentos u oscilantes los tests. | `waitUntil` hasta 5 s para que aparezca, y `assertNoTourFor(1_500)` para afirmar que no aparece. |
| R8 | Un test que monta la app sin pasar por `skipOnboarding()` no apaga los recorridos y se bloquea con el velo. | Hoy solo son `NewUserJourneyTest` y `OnboardingTest`, que llaman a `skipTours()` (D30). La comprobación con `grep` de F4 y la suite completa en F4 y en F9 detectan un caso nuevo. |
| R15 | Un test nuevo de recorridos olvida `showTours` y el recorrido que prueba no aparece. | Falla de forma visible: `waitForTour` agota los 5 s con un mensaje que nombra el recorrido esperado y el contenido de `env.tourSettings.seen`. |
| R16 | Las capturas en español para la revisión dejan la app del AVD en español y cambian los tests siguientes. | El idioma se cambia solo para la app y se vuelve a `en-US` al terminar (§7.5), con la comprobación de `cmd locale get-app-locales`. |
| R9 | Un error en los 324 recursos nuevos (nombre, traducción que falta o una frase de más). | `ToursCatalogTest` y `lintDebug` (`MissingTranslation`). |
| R10 | El globo no cabe a 360 dp con la letra a 1,3. | Pie en `FlowRow` (D15) y ancho máximo con margen. §7.3 lo mira con `wm density` y `font_scale`. |
| R11 | `ActivityScenario.recreate()` seguido de `activity.setContent` no funciona con `createAndroidComposeRule<ComponentActivity>()`. | No hay precedente en el proyecto. Plan B para el criterio del reinicio: recomponer `TestApp` con una clave nueva y comprobar que una instancia nueva de `TourSettings` sobre `"settings_test"` lee los ids. Para la rotación: `rule.activity.requestedOrientation` y esperar a la recreación. |
| R12 | Durante la transición de `NavDisplay` el globo aparece con la pantalla aún entrando. | Los 300 ms cubren casi toda la transición por defecto, y los límites se leen en vivo, así que el hueco sigue al elemento. Si molesta en la prueba manual, se espera también al fin de la transición con `LocalNavAnimatedContentScope`. |
| R13 | `SettingsNavigationTest` marca los recorridos en las preferencias reales del emulador. | Es lo mismo que ya hace con `onboarding_done`. Solo afecta a `emulator-5556`, que es de pruebas. |
| R14 | El AVD no tiene TalkBack y no se puede oír el anuncio sin pantalla. | La semántica se prueba siempre en `TourOverlayTest`. Si el AVD tiene TalkBack, se escucha por registro (§7.4). Si no, la escucha real la hace el usuario en un teléfono físico, y no se instala nada en el AVD (D35). |

## 6. Preguntas para el usuario

No quedan preguntas abiertas. El usuario respondió las cinco el 2026-11-17, y sus respuestas son decisiones del §2:

| Pregunta | Respuesta | Decisión |
|---|---|---|
| P1. Cómo apagar los recorridos en los tests existentes | Dentro de `skipOnboarding()`; los tests nuevos de recorridos los activan de forma explícita | D30 (y D6) |
| P2. La etiqueta "Activa" en Recetas | Se añade la marca de la receta activa y el paso `recipes.active` se mantiene | D29 |
| P3. Anclas que no existen al evaluar su recorrido | a) `summary.rating` en `summary_details`; b) `session_scaleless.plan` sin cambios; c) `coffee_detail.status` en `coffee_detail_status` | D33 |
| P4. Revisión de los textos | Sobre las capturas de F9, no antes | D34 |
| P5. TalkBack | F0 lo comprueba; la semántica se prueba siempre en `TourOverlayTest`; sin TalkBack en el AVD, escucha real del usuario en un teléfono físico, sin instalar nada | D35 |

## 7. Verificación final

### 7.1 Suites

```
./gradlew :app:testDebugUnitTest
ANDROID_SERIAL=emulator-5556 ./gradlew :app:connectedDebugAndroidTest
./gradlew :app:lintDebug
```

Se compara con la línea base de F0:

- **Tests:** solo suben los totales, por los tests nuevos.
- **Lint:** sin errores ni avisos nuevos, y `MissingTranslation`, `ExtraTranslation` y `UnusedResources` a cero en las
  cadenas nuevas.
- **Fallos que no bloquean:** los de la lista de §3.

Comprobaciones con `grep`:

- ningún `import android` en `ui/tour/TourModels.kt` ni en `ui/tour/TourPlacement.kt`;
- `grep -rn "tourAnchor" app/src/main/kotlin/dev/josueamaya/pourline/ui/session/SessionScreen.kt` solo muestra anclas de
  `PrepPanel`, de `PrepStepper`, de `PrepButtons` y de `SessionTopBar` condicionadas por `anchors`;
- cada `TestApp(env)` de `androidTest` está en una clase que llama a `skipOnboarding(...)` o a `skipTours(...)`.

### 7.2 Rendimiento (`SessionPerformanceTest`, `emulator-5556`)

- **Pasadas:** tres de cada caso en F0, F6 y F9, con los umbrales de SPEC-005 (p95 ≤ 32 ms, ≤ 5 % de fotogramas por
  encima de 32 ms y ninguno por encima de 250 ms).
- **Comparación:** la mediana de las tres contra la de F0. Si el p95 sube más de 1 ms, se repite y se busca la causa con
  `FrameMetrics` por fase, como en SPEC-005. El caso Experto ya oscila en `develop`; lo que cuenta es la mediana.

### 7.3 Prueba manual por adb (sin ARTEMIS)

**Antes de empezar:**

1. `adb devices -l`.
2. `adb -s emulator-5556 shell wm size`, para convertir coordenadas.
3. `ANDROID_SERIAL=emulator-5556 ./gradlew :app:installDebug`.
4. `adb -s emulator-5556 shell pm clear dev.josueamaya.pourline`.
5. `adb -s emulator-5556 shell pm grant dev.josueamaya.pourline android.permission.POST_NOTIFICATIONS`.

**Herramientas:**

- Localizar con `uiautomator dump` y por texto (la app no expone los `testTag` como `resource-id`), tocar con `input tap` y
  capturar con `screencap -d <display id>` (el id de F0).
- `adb logcat -b crash -d` debe quedar vacío en todo el recorrido.
- El emulador está en inglés: los textos esperados son los de `values-en` (§2.7).
- En cada paso: el globo entero dentro de la pantalla y fuera de las barras del sistema, el hueco sobre el elemento
  correcto y el texto sin cortar.

| Paso | Recorrido | Comprobaciones |
|---|---|---|
| 1 | Abrir la app → saltar el primer arranque | "Welcome to Pourline" centrado, sin hueco, "1/13". Siguiente hasta "Done": cada hueco sobre su elemento, la pantalla se desplaza a "Start without scale", y los cuatro pasos de pestañas resaltan la barra. Capturas del paso 1, de uno con el globo encima del elemento y de uno de la barra. |
| 2 | Pestañas Coffees, Recipes, History y Tools | Cada una muestra su recorrido. Sin cafés ni preparaciones, faltan los pasos de filas. Capturas. |
| 3 | Coffees → "Add coffee" → guardar uno con fecha y stock → abrirlo | `coffee_edit` y `coffee_detail` (en `new_bag`, la lista se desplaza). Capturas. |
| 4 | Recipes → una receta | `recipe_edit` con 4 pasos en Básico. |
| 5 | Brew → sin receta → "Start without scale" | `session_scaleless` "1/4". "Done" → "Start timer" → durante la cuenta regresiva y el vertido no aparece nada → "Finish" → `summary`. Capturas. |
| 6 | Brew → conectar la báscula simulada → V60 → "Start" | `session_prep` "1/6". Tocar el hueco de "Tare": no tara. "Atrás" retrocede un paso. Captura. |
| 7 | History → la preparación → Settings → Expert → History otra vez → detalle → "Compare with…" | Solo los pasos E nuevos de History y del detalle; `compare` con 3 pasos. Capturas. |
| 8 | Tools → las tres herramientas | `ratio_calculator`, `four_six` (el hueco cubre los dos sliders) y `converter`. |
| 9 | Settings | `settings` "1/6" (o lo pendiente). "Replay tour" → Brew en la raíz con el recorrido desde el paso 1: "1/14" en Experto si hay molino con ajuste (la regla), "1/13" si no. Saltar → el aviso arriba, con "Settings > Replay tour". Captura del aviso. |
| 10 | Settings → Appearance → Dark → "Replay tour", y repetir los pasos 1 a 9 | Lo mismo en oscuro: contraste del globo, del velo y de la flecha. Capturas de cada recorrido. |
| 11 | Horizontal: `adb -s emulator-5556 shell settings put system accelerometer_rotation 0` y `settings put system user_rotation 1`, con el recorrido general abierto en el paso 3 | Sigue en "3/13", con el hueco recolocado y el globo al lado o encima, dentro de la zona segura. Un paso de la barra también. Captura. Después, `user_rotation 0`. |
| 12 | Pantalla pequeña y letra grande: `adb -s emulator-5556 shell wm density 480` (unos 360 dp de ancho) y `settings put system font_scale 1.3` | El pie del globo baja de línea sin cortar botones. Captura. Después, `wm density reset` y `font_scale 1.0`. |

Al acabar, el usuario revisa los textos sobre las capturas (§7.6).

### 7.4 TalkBack

Si el AVD tiene TalkBack (F0):

1. `adb -s emulator-5556 shell settings put secure enabled_accessibility_services com.google.android.marvin.talkback/com.google.android.marvin.talkback.TalkBackService`
   y `adb -s emulator-5556 shell settings put secure accessibility_enabled 1`.
2. Activar en los ajustes de desarrollador de TalkBack el registro de la voz, y seguir `adb logcat | grep -i talkback`
   mientras se cambia de paso con doble toque en "Next".
3. Comprobar que se dicen el título y el cuerpo de cada paso, que los botones se anuncian con su nombre, y que no se puede
   llegar al contenido de debajo.
4. Desactivar TalkBack: `settings put secure enabled_accessibility_services ""` y `accessibility_enabled 0`.

Si no lo tiene, no se instala nada en el AVD (D35). La semántica ya está probada en `TourOverlayTest`, y la escucha
real queda como verificación manual del usuario en un teléfono físico: activar TalkBack en Ajustes > Accesibilidad,
recorrer el recorrido general y comprobar el punto 3. Se anota en la cabecera del plan como pendiente de su resultado.

### 7.5 Capturas

Fuera del repo, con `adb pull` a una carpeta del usuario:

- en inglés: el paso 1 y un paso de cada recorrido en claro y en oscuro, el aviso de Saltar, el caso horizontal y el de
  360 dp con letra grande;
- en español, para la revisión de los textos: todos los pasos de cada recorrido, en claro. Se repiten los pasos 1 a 9 del
  §7.3 con el idioma de la app cambiado solo para ella
  (`adb -s emulator-5556 shell cmd locale set-app-locales dev.josueamaya.pourline --locales es-ES`). Al terminar se vuelve
  con `--locales en-US` y se comprueba con `cmd locale get-app-locales dev.josueamaya.pourline` (R16).

### 7.6 Revisión de los textos por el usuario (D34)

Es el último paso de F9:

1. Se entregan al usuario las capturas del §7.5, ordenadas por recorrido, junto con la tabla del §2.7.
2. El usuario marca los textos que quiere cambiar, en cualquiera de los dos idiomas.
3. Los cambios se aplican en `values/strings.xml`, en `values-en/strings.xml` y en el §2.7. Después se pasan
   `ToursCatalogTest` (una o dos frases por cuerpo, sin emojis) y `./gradlew :app:lintDebug`.
4. Se repiten las capturas de los pasos cambiados, para comprobar que caben en el globo a 360 dp con letra a 1,3.
5. F9 se cierra cuando el usuario da el visto bueno.

## 8. Seguimiento fuera de este plan

- **Cambios que conviene llevar al spec al cerrar.** No se tocan ahora.
  - Las anclas renombradas (discrepancias 1 a 3, 8 a 10) y las de D33.
  - La etiqueta "Activa" en Recetas (D29).
  - La restauración tras la muerte del proceso (discrepancia 13).
  - Las anclas de la sesión solo fuera de `Active` (discrepancia 12) y `summary_expert` solo con báscula (discrepancia 11).
  - El aviso arriba (D19), el `tour_balloon` (D15) y `TourLazyAnchor` (D10).
- **Fuera del alcance (§1 del spec):** recorridos dentro de hojas y modales, en Molinos, en "Elegir preparaciones a
  comparar" y en editar una preparación; recorridos durante el vertido; telemetría.
- **Antes de fusionar en `develop`:** revisar la rama con `/code-review`.
