package dev.josueamaya.pourline

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import dev.josueamaya.pourline.grinder.GrindFix
import dev.josueamaya.pourline.session.ScaleUiState
import dev.josueamaya.pourline.settings.ReferenceKey
import dev.josueamaya.pourline.tools.FourSix
import dev.josueamaya.pourline.tools.FourSixParams
import dev.josueamaya.pourline.ui.coffees.CoffeeDetailScreen
import dev.josueamaya.pourline.ui.coffees.CoffeeDetailViewModel
import dev.josueamaya.pourline.ui.coffees.CoffeeEditScreen
import dev.josueamaya.pourline.ui.coffees.CoffeeEditViewModel
import dev.josueamaya.pourline.ui.coffees.CoffeePickerViewModel
import dev.josueamaya.pourline.ui.coffees.CoffeesScreen
import dev.josueamaya.pourline.ui.coffees.CoffeesViewModel
import dev.josueamaya.pourline.ui.components.AntMessageHost
import dev.josueamaya.pourline.ui.components.AntTabBar
import dev.josueamaya.pourline.ui.components.AntTabBarItem
import dev.josueamaya.pourline.ui.components.showAntMessage
import dev.josueamaya.pourline.ui.grinders.GrindConverterScreen
import dev.josueamaya.pourline.ui.grinders.GrindConverterViewModel
import dev.josueamaya.pourline.ui.grinders.GrinderCatalogScreen
import dev.josueamaya.pourline.ui.grinders.GrinderCatalogViewModel
import dev.josueamaya.pourline.ui.grinders.GrinderDetailScreen
import dev.josueamaya.pourline.ui.grinders.GrinderDetailViewModel
import dev.josueamaya.pourline.ui.grinders.GrinderEditScreen
import dev.josueamaya.pourline.ui.grinders.GrinderEditViewModel
import dev.josueamaya.pourline.ui.grinders.GrindersScreen
import dev.josueamaya.pourline.ui.grinders.GrindersViewModel
import dev.josueamaya.pourline.ui.history.BrewCompareScreen
import dev.josueamaya.pourline.ui.history.BrewCompareViewModel
import dev.josueamaya.pourline.ui.history.BrewDetailScreen
import dev.josueamaya.pourline.ui.history.BrewDetailViewModel
import dev.josueamaya.pourline.ui.history.BrewEditScreen
import dev.josueamaya.pourline.ui.history.BrewEditViewModel
import dev.josueamaya.pourline.ui.history.ComparePickScreen
import dev.josueamaya.pourline.ui.history.ComparePickViewModel
import dev.josueamaya.pourline.ui.history.HistoryScreen
import dev.josueamaya.pourline.ui.history.HistoryViewModel
import dev.josueamaya.pourline.ui.onboarding.OnboardingScreen
import dev.josueamaya.pourline.ui.onboarding.OnboardingViewModel
import dev.josueamaya.pourline.ui.onboarding.ScaleQuestionScreen
import dev.josueamaya.pourline.ui.prepare.PrepareScreen
import dev.josueamaya.pourline.ui.prepare.PrepareViewModel
import dev.josueamaya.pourline.ui.recipes.RecipeEditScreen
import dev.josueamaya.pourline.ui.recipes.RecipeEditViewModel
import dev.josueamaya.pourline.ui.recipes.RecipeImportEntryViewModel
import dev.josueamaya.pourline.ui.recipes.RecipeImportScreen
import dev.josueamaya.pourline.ui.recipes.RecipeImportViewModel
import dev.josueamaya.pourline.ui.recipes.catalog.CatalogRecipeDetailScreen
import dev.josueamaya.pourline.ui.recipes.catalog.CatalogRecipeDetailViewModel
import dev.josueamaya.pourline.ui.recipes.openSourceUrl
import dev.josueamaya.pourline.ui.recipes.RecipesScreen
import dev.josueamaya.pourline.ui.recipes.RecipesViewModel
import dev.josueamaya.pourline.ui.recipes.importNoticeStatus
import dev.josueamaya.pourline.ui.recipes.importNoticeText
import dev.josueamaya.pourline.ui.scale.TargetPickerViewModel
import dev.josueamaya.pourline.ui.session.SessionScreen
import dev.josueamaya.pourline.ui.settings.BackupViewModel
import dev.josueamaya.pourline.ui.settings.SettingsScreen
import dev.josueamaya.pourline.ui.tools.FourSixScreen
import dev.josueamaya.pourline.ui.tools.FourSixViewModel
import dev.josueamaya.pourline.ui.tools.RatioCalculatorScreen
import dev.josueamaya.pourline.ui.tools.RatioCalculatorViewModel
import dev.josueamaya.pourline.ui.tools.ToolsScreen
import dev.josueamaya.pourline.ui.tour.TourBar
import dev.josueamaya.pourline.ui.tour.TourRoot
import dev.josueamaya.pourline.ui.tour.TourScreen
import dev.josueamaya.pourline.ui.tour.Tours
import dev.josueamaya.pourline.ui.tour.tourAnchor
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * La app: la elección del primer arranque (D11) mientras `onboardingDone` sea falso y, después, las cinco
 * pestañas con su pila cada una (D30). Quien terminó el onboarding antes de que preguntara por la báscula ve esa
 * pregunta una vez antes de las pestañas.
 */
@Composable
fun MainNavigation(deps: AppDependencies) {
  val onboardingDone by deps.experienceSettings.onboardingDone.collectAsStateWithLifecycle()
  val scaleModeChosen by deps.scaleMode.scaleModeChosen.collectAsStateWithLifecycle()
  if (onboardingDone && !scaleModeChosen) {
    ScaleQuestionScreen(onChoose = deps.scaleMode::setUseScale)
  } else if (onboardingDone) {
    TabsNavigation(deps)
  } else {
    val viewModel = viewModel { OnboardingViewModel(deps.grinderRepository, deps.grinderSettings, deps.experienceSettings, deps.scaleMode) }
    val catalog by viewModel.catalog.collectAsStateWithLifecycle()
    OnboardingScreen(catalog = catalog, onFinish = viewModel::finish)
  }
}

@Composable
private fun TabsNavigation(deps: AppDependencies) {
  var tab by rememberSaveable { mutableStateOf(Tab.PREPARE) }
  val stacks = Tab.entries.associateWith { rememberNavBackStack(it.root) }
  val entries = Tab.entries.associateWith { t ->
    // Cada pila se decora por separado (con sus propios decoradores): así conserva sus ViewModels y su estado
    // guardado al cambiar de pestaña y no los comparte con otra pila.
    rememberDecoratedNavEntries(
      backStack = stacks.getValue(t),
      entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()),
      entryProvider = tabEntryProvider(t, stacks.getValue(t), deps, activeTab = { tab }) { tab = it },
    )
  }
  val activeStack = stacks.getValue(tab)
  val showBar = showsBottomBar(activeStack)

  // Una receta importada abre su vista previa en Recetas, salvo con una sesión en curso: entonces espera (D15).
  val hasSession by remember { deps.scaleSession.ui.map { it.hasSession }.distinctUntilChanged() }
    .collectAsStateWithLifecycle(remember { deps.scaleSession.ui.value.hasSession })
  val pendingImport by deps.recipeImports.pending.collectAsStateWithLifecycle()
  // Mientras se reemplazan los datos tampoco se abre: la vista previa leería una base a medio cambiar (SPEC-011 §3.5).
  val applying by deps.backup.applying.collectAsStateWithLifecycle()
  LaunchedEffect(pendingImport, hasSession, applying) {
    val uri = pendingImport ?: return@LaunchedEffect
    if (!hasSession && !applying && deps.recipeImports.consume(uri)) {
      tab = Tab.RECIPES
      stacks.getValue(Tab.RECIPES).openImport(uri)
    }
  }
  // Tras importar una copia, las pestañas distintas de Preparar vuelven a su raíz: lo que tenían abierto (un café, un editor)
  // apuntaba a datos que ya no son los mismos. La de Preparar es `[Prepare, Settings]` y se queda donde está (D22).
  LaunchedEffect(deps.backup) {
    deps.backup.imported.collect {
      Tab.entries.filter { it != Tab.PREPARE }.forEach { stacks.getValue(it).popToRoot() }
      // Una copia puede traer "Sin báscula": la conexión no debe quedar abierta sin que se sepa, como al cambiar el modo.
      if (!deps.scaleMode.useScale.value && !deps.scaleSession.isArmed) deps.scaleSession.disconnect()
    }
  }
  // Los mensajes de las importaciones (añadida, aplazada, errores) salen en un host propio, sobre cualquier pantalla (D21).
  val importMessages = remember { SnackbarHostState() }
  val resources = LocalResources.current
  LaunchedEffect(deps.recipeImports) {
    deps.recipeImports.notices.collect { notice ->
      importMessages.showAntMessage(resources.getString(importNoticeText(notice)), importNoticeStatus(notice))
    }
  }

  TourRoot(deps.tourSettings) {
    Column(Modifier.fillMaxSize()) {
      Box(Modifier.weight(1f)) {
        NavDisplay(
          // Con las entradas de Preparar debajo, "Atrás" en la raíz de otra pestaña tiene a dónde ir (D30).
          entries = if (tab == Tab.PREPARE) entries.getValue(Tab.PREPARE) else entries.getValue(Tab.PREPARE) + entries.getValue(tab),
          modifier = Modifier
            .fillMaxSize()
            // La barra inferior ya ocupa el inset de navegación: las pantallas de dentro no lo suman otra vez.
            .then(if (showBar) Modifier.consumeWindowInsets(WindowInsets.navigationBars) else Modifier),
          onBack = {
            when (backAction(tab, activeStack.size)) {
              BackAction.Pop -> activeStack.removeLastOrNull()
              BackAction.SelectPrepare -> tab = Tab.PREPARE
              BackAction.Exit -> Unit
            }
          },
        )
        AntMessageHost(
          importMessages,
          Modifier.align(Alignment.BottomCenter).then(if (showBar) Modifier else Modifier.navigationBarsPadding()),
        )
      }
      if (showBar) {
        val items = Tab.entries.map { AntTabBarItem(it, stringResource(it.label), it.icon, it.testTag) }
        // Las pestañas son anclas del recorrido general: su registro es el de la raíz, no el de una pantalla.
        TourBar {
          AntTabBar(
            items = items,
            selected = tab,
            // Tocar la pestaña activa vuelve a su raíz.
            onSelect = { selected -> if (selected == tab) activeStack.popToRoot() else tab = selected },
            itemModifier = { Modifier.tourAnchor(it.testTag) },
          )
        }
      }
    }
  }
}

/**
 * Una entrada cuyo `contentKey` lleva el prefijo de la pestaña: dos pilas con la misma clave no comparten `ViewModel` ni
 * estado. Cada entrada tiene su registro de anclas del recorrido guiado, y es la visible si es la cima de la pila de la
 * pestaña activa (D20).
 */
private inline fun <reified K : NavKey> EntryProviderScope<NavKey>.tabEntry(
  tab: Tab,
  stack: NavBackStack<NavKey>,
  noinline activeTab: () -> Tab,
  noinline content: @Composable (K) -> Unit,
) {
  entry<K>(clazzContentKey = { key -> "${tab.name}/$key" }) { key ->
    TourScreen(visible = activeTab() == tab && stack.lastOrNull() == key) { content(key) }
  }
}

/** Las pantallas de la pestaña [tab]; los accesos cruzados se apilan en su propia pila [stack] (D31). */
private fun tabEntryProvider(
  tab: Tab,
  stack: NavBackStack<NavKey>,
  deps: AppDependencies,
  activeTab: () -> Tab,
  selectTab: (Tab) -> Unit,
): (NavKey) -> NavEntry<NavKey> = entryProvider {
  fun back() {
    stack.removeLastOrNull()
  }

  tabEntry<Prepare>(tab, stack, activeTab) {
    val viewModel = viewModel { PrepareViewModel(deps.scaleSession, deps.scaleMemory, deps.freeBrewDefaults, deps.bluetooth, deps.grindSelection, deps.scaleMode) }
    PrepareScreen(
      viewModel = viewModel,
      scanner = deps.scanner,
      pickerViewModel = viewModel { TargetPickerViewModel(deps.recipeRepository, deps.brewRepository, deps.recipeCatalog, deps.catalogLanguage) },
      coffeePickerViewModel = viewModel { CoffeePickerViewModel(deps.coffeeRepository) },
      onOpenSettings = { stack.add(Settings) },
      onStart = { stack.add(Session) },
      onManageRecipes = { selectTab(Tab.RECIPES) },
      onNewCoffee = { stack.add(CoffeeEdit()) },
      onAddGrinder = { stack.add(Grinders) },
      onFixGrinder = { fix, id -> stack.add(fixGrinderKey(fix, id)) },
      onOpenConverter = { stack.add(GrindConverter(it.sourceId, it.value)) },
    )
  }
  tabEntry<Session>(tab, stack, activeTab) {
    val session = deps.scaleSession
    val hasSession by remember { session.ui.map { it.hasSession }.distinctUntilChanged() }
      .collectAsStateWithLifecycle(remember { session.ui.value.hasSession })
    // D44: una `Session` restaurada sin sesión abierta (proceso muerto) se retira sola.
    LaunchedEffect(hasSession) {
      if (!hasSession && stack.lastOrNull() is Session) stack.removeLastOrNull()
    }
    SessionScreen(
      session = session,
      modeStore = deps.experienceSettings,
      brews = deps.brewRepository,
      onAddDetails = { id -> stack.add(BrewEdit(id)) },
      onDone = { stack.popToRoot() },
      grind = deps.grindSelection,
      onAddGrinder = { stack.add(Grinders) },
      onFixGrinder = { fix, id -> stack.add(fixGrinderKey(fix, id)) },
      onOpenConverter = { stack.add(GrindConverter(it.sourceId, it.value)) },
    )
  }
  tabEntry<Coffees>(tab, stack, activeTab) {
    CoffeesScreen(
      viewModel { CoffeesViewModel(deps.coffeeRepository) },
      onBack = null,
      onOpenCoffee = { id -> stack.add(CoffeeDetail(id)) },
      onAddCoffee = { stack.add(CoffeeEdit()) },
    )
  }
  tabEntry<CoffeeDetail>(tab, stack, activeTab) { key ->
    CoffeeDetailScreen(
      viewModel { CoffeeDetailViewModel(deps.coffeeRepository, key.id) },
      onBack = ::back,
      onEdit = { id -> stack.add(CoffeeEdit(id = id)) },
      onNewBag = { id -> stack.add(CoffeeEdit(fromCoffeeId = id)) },
      onOpenBrew = { id -> stack.add(BrewDetail(id)) },
    )
  }
  tabEntry<CoffeeEdit>(tab, stack, activeTab) { key ->
    CoffeeEditScreen(
      viewModel { CoffeeEditViewModel(deps.coffeeRepository, deps.coffeePhotoStore, deps.labelOcr, key.id, key.fromCoffeeId) },
      newCameraTarget = deps.coffeePhotoStore::newCameraTarget,
      onBack = ::back,
      // Una bolsa nueva se abre en su detalle (atrás vuelve al café original); en los demás casos, la pantalla
      // de origen ya muestra el resultado.
      onSaved = { savedId ->
        if (key.fromCoffeeId != null) stack[stack.lastIndex] = CoffeeDetail(savedId) else back()
      },
      isEdit = key.id != null,
    )
  }
  tabEntry<Settings>(tab, stack, activeTab) {
    SettingsScreen(
      deps.brewSettings,
      deps.targetSettings,
      deps.experienceSettings,
      deps.themeSettings,
      grinderRepository = deps.grinderRepository,
      grinderDefaults = deps.grinderSettings,
      scaleMode = deps.scaleMode,
      // Sin báscula no se ve la tarjeta: la conexión se cierra para que no quede abierta sin que se sepa.
      onScaleOff = { if (!deps.scaleSession.isArmed) deps.scaleSession.disconnect() },
      onOpenGrinders = { stack.add(Grinders) },
      // Ajustes solo se abre desde Preparar: su pila es la de Preparar y su raíz, donde sale el recorrido general.
      onReplayTour = {
        deps.tourSettings.reset()
        // Ya se pidió el recorrido: no se vuelve a preguntar.
        deps.tourSettings.markSeen(listOf(Tours.INVITATION_ID))
        stack.popToRoot()
        selectTab(Tab.PREPARE)
      },
      onBack = ::back,
      backup = viewModel {
        BackupViewModel(
          deps.backup,
          sessionBusy = deps.scaleSession.ui.map { it.blocksDataImport }.distinctUntilChanged(),
          isSessionBusy = { deps.scaleSession.ui.value.blocksDataImport },
        )
      },
    )
  }
  tabEntry<Grinders>(tab, stack, activeTab) {
    GrindersScreen(
      viewModel { GrindersViewModel(deps.grinderRepository, deps.grinderSettings) },
      onBack = ::back,
      onOpenGrinder = { id -> stack.add(GrinderDetail(id)) },
      onAddGrinder = { stack.add(GrinderCatalog) },
      onOpenConverter = { stack.add(GrindConverter()) },
    )
  }
  tabEntry<GrindConverter>(tab, stack, activeTab) { key ->
    GrindConverterScreen(
      viewModel { GrindConverterViewModel(deps.grinderRepository, key.sourceId, key.value) },
      onBack = ::back,
      onOpenGrinders = { stack.add(Grinders) },
      onFix = { fix, id -> stack.add(fixGrinderKey(fix, id)) },
    )
  }
  tabEntry<GrinderCatalog>(tab, stack, activeTab) {
    GrinderCatalogScreen(
      viewModel { GrinderCatalogViewModel(deps.grinderRepository) },
      onBack = ::back,
      onOpenGrinder = { id -> stack.add(GrinderDetail(id)) },
      onCreate = { stack.add(GrinderEdit()) },
    )
  }
  tabEntry<GrinderDetail>(tab, stack, activeTab) { key ->
    GrinderDetailScreen(
      viewModel { GrinderDetailViewModel(deps.grinderRepository, deps.grinderSettings, key.id) },
      onBack = ::back,
      onEdit = { id -> stack.add(GrinderEdit(id = id)) },
      onDuplicate = { id -> stack.add(GrinderEdit(fromId = id)) },
      toCalibrations = key.toCalibrations,
    )
  }
  tabEntry<GrinderEdit>(tab, stack, activeTab) { key ->
    GrinderEditScreen(
      viewModel { GrinderEditViewModel(deps.grinderRepository, key.id, key.fromId) },
      openRanges = key.openRanges,
      onBack = ::back,
    )
  }
  tabEntry<Recipes>(tab, stack, activeTab) {
    // D29: la receta elegida en Preparar se marca como "Activa" en la lista.
    val session = deps.scaleSession
    val activeRecipeId by remember { session.ui.map(::activeRecipeIdOf).distinctUntilChanged() }
      .collectAsStateWithLifecycle(remember { activeRecipeIdOf(session.ui.value) })
    val activeCatalogKey by remember { session.ui.map(::activeCatalogKeyOf).distinctUntilChanged() }
      .collectAsStateWithLifecycle(remember { activeCatalogKeyOf(session.ui.value) })
    val importer = viewModel {
      RecipeImportEntryViewModel(deps.recipeImports, deps.qrImageReader) { deps.scaleSession.ui.value.hasSession }
    }
    RecipesScreen(
      viewModel { RecipesViewModel(deps.recipeRepository, deps.recipeCatalog, deps.catalogLanguage) },
      onBack = null,
      onOpenRecipe = { id -> stack.add(RecipeEdit(id = id)) },
      onCreateRecipe = { stack.add(RecipeEdit()) },
      activeRecipeId = activeRecipeId,
      activeCatalogKey = activeCatalogKey,
      onOpenCatalogRecipe = { key -> stack.add(CatalogRecipeDetail(key)) },
      importer = importer,
    )
  }
  tabEntry<CatalogRecipeDetail>(tab, stack, activeTab) { key ->
    val context = LocalContext.current
    CatalogRecipeDetailScreen(
      viewModel {
        CatalogRecipeDetailViewModel(key.key, deps.recipeCatalog, deps.recipeRepository, deps.grinderRepository, deps.grindTexts, deps.catalogLanguage)
      },
      onBack = ::back,
      // D37: "Preparar" elige la receta sin guardarla y cambia de pestaña; con una sesión activa solo cambia de pestaña.
      onPrepare = { catalogKey ->
        deps.scaleSession.selectTarget(ReferenceKey.Catalog(catalogKey))
        selectTab(Tab.PREPARE)
      },
      openUrl = { url -> openSourceUrl(context, url) },
    )
  }
  tabEntry<RecipeEdit>(tab, stack, activeTab) { key ->
    RecipeEditScreen(
      viewModel { RecipeEditViewModel(
          deps.recipeRepository, deps.brewRepository, deps.recipeTexts, deps.grinderRepository, deps.grinderSettings, deps.grindTexts, key.id, key.fromBrewId,
          draft = key.fourSix?.let(FourSix::plan)?.let { FourSix.recipe(it, deps.fourSixTexts) },
        ) },
      onBack = ::back,
      // Duplicar abre la copia en lugar de la original: atrás vuelve a la lista.
      onDuplicated = { copyId -> stack[stack.lastIndex] = RecipeEdit(id = copyId) },
      onOpenConverter = { stack.add(GrindConverter(it.sourceId, it.value)) },
      onOpenFourSix = if (key.id == null && key.fromBrewId == null) { { stack.add(FourSixTool) } } else null,
    )
  }
  tabEntry<RecipeImport>(tab, stack, activeTab) { key ->
    RecipeImportScreen(
      viewModel { RecipeImportViewModel(key.uri, deps.recipeRepository, deps.grinderRepository, deps.grindTexts, deps.recipeImports) },
      // Guardar y Cancelar quitan solo la vista previa: un editor con cambios que hubiera debajo no se descarta.
      onDone = ::back,
    )
  }
  tabEntry<Tools>(tab, stack, activeTab) {
    ToolsScreen(
      onOpenRatio = { stack.add(RatioTool) },
      onOpenFourSix = { stack.add(FourSixTool) },
      onOpenConverter = { stack.add(GrindConverter()) },
    )
  }
  tabEntry<RatioTool>(tab, stack, activeTab) {
    RatioCalculatorScreen(viewModel { RatioCalculatorViewModel(deps.freeBrewDefaults) }, onBack = ::back)
  }
  tabEntry<FourSixTool>(tab, stack, activeTab) {
    FourSixScreen(
      viewModel { FourSixViewModel(deps.freeBrewDefaults) },
      onBack = ::back,
      onSaveAsRecipe = { params -> stack.saveFourSixAsRecipe(params) },
    )
  }
  tabEntry<History>(tab, stack, activeTab) {
    HistoryScreen(
      viewModel { HistoryViewModel(deps.brewRepository) },
      onBack = null,
      onOpenBrew = { id -> stack.add(BrewDetail(id)) },
    )
  }
  tabEntry<BrewDetail>(tab, stack, activeTab) { key ->
    BrewDetailScreen(
      viewModel { BrewDetailViewModel(deps.brewRepository, deps.coffeeRepository, deps.grinderRepository, key.id) },
      onBack = ::back,
      onOpenCoffee = { id -> stack.add(CoffeeDetail(id)) },
      onEdit = { id -> stack.add(BrewEdit(id)) },
      onCompare = { id -> stack.add(ComparePick(id)) },
      onSaveAsRecipe = { id -> stack.add(RecipeEdit(fromBrewId = id)) },
    )
  }
  tabEntry<ComparePick>(tab, stack, activeTab) { key ->
    ComparePickScreen(
      viewModel { ComparePickViewModel(deps.brewRepository, key.aId) },
      onBack = ::back,
      // Atrás desde la comparación vuelve al detalle de A, no al selector.
      onPick = { bId -> stack[stack.lastIndex] = BrewCompare(key.aId, bId) },
    )
  }
  tabEntry<BrewCompare>(tab, stack, activeTab) { key ->
    BrewCompareScreen(
      viewModel { BrewCompareViewModel(deps.brewRepository, deps.coffeeRepository, key.aId, key.bId) },
      onBack = ::back,
    )
  }
  tabEntry<BrewEdit>(tab, stack, activeTab) { key ->
    BrewEditScreen(
      viewModel { BrewEditViewModel(deps.brewRepository, deps.coffeeRepository, deps.grinderRepository, deps.grindTexts, key.id) },
      onBack = ::back,
      coffeePickerViewModel = viewModel { CoffeePickerViewModel(deps.coffeeRepository) },
      onNewCoffee = { stack.add(CoffeeEdit()) },
    )
  }
}

/**
 * Completar los rangos abre el editor del molino propio; en un predefinido, una copia suya con los rangos abiertos;
 * añadir otra equivalencia abre el detalle del molino (la sección "Equivalencias" llega en F7).
 */
private fun fixGrinderKey(fix: GrindFix, id: Long): NavKey = when (fix) {
  GrindFix.COMPLETE_RANGES -> GrinderEdit(id = id, openRanges = true)
  GrindFix.DUPLICATE -> GrinderEdit(fromId = id, openRanges = true)
  GrindFix.ADD_CALIBRATION -> GrinderDetail(id, toCalibrations = true)
}

/** El id de la receta elegida en Preparar, o `null` si no hay objetivo o es una preparación anterior (D29). */
/**
 * "Guardar como receta" del generador 4:6 abre el editor con la receta generada. Si se llegó al generador desde el
 * editor de una receta nueva, ese editor se sustituye por el nuevo: la receta generada ocupa su lugar y "atrás" vuelve a
 * la lista. Desde Herramientas el generador se queda debajo, para volver a él.
 */
private fun NavBackStack<NavKey>.saveFourSixAsRecipe(params: FourSixParams) {
  val below = getOrNull(lastIndex - 1)
  if (lastOrNull() == FourSixTool && below is RecipeEdit && below.id == null && below.fromBrewId == null) {
    removeAt(lastIndex)
    removeAt(lastIndex)
  }
  add(RecipeEdit(fourSix = params))
}

private fun activeRecipeIdOf(ui: ScaleUiState): Long? = (ui.target?.key as? ReferenceKey.Recipe)?.id

private fun activeCatalogKeyOf(ui: ScaleUiState): String? {
  val reference = ui.target?.key
  return if (reference is ReferenceKey.Catalog) reference.key else null
}
