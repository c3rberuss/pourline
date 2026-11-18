package dev.josueamaya.pourline

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import dev.josueamaya.pourline.tools.FourSix
import dev.josueamaya.pourline.ui.coffees.CoffeeDetailScreen
import dev.josueamaya.pourline.ui.coffees.CoffeeDetailViewModel
import dev.josueamaya.pourline.ui.coffees.CoffeeEditScreen
import dev.josueamaya.pourline.ui.coffees.CoffeeEditViewModel
import dev.josueamaya.pourline.ui.coffees.CoffeePickerViewModel
import dev.josueamaya.pourline.ui.coffees.CoffeesScreen
import dev.josueamaya.pourline.ui.coffees.CoffeesViewModel
import dev.josueamaya.pourline.ui.components.AntTabBar
import dev.josueamaya.pourline.ui.components.AntTabBarItem
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
import dev.josueamaya.pourline.ui.prepare.PrepareScreen
import dev.josueamaya.pourline.ui.prepare.PrepareViewModel
import dev.josueamaya.pourline.ui.recipes.RecipeEditScreen
import dev.josueamaya.pourline.ui.recipes.RecipeEditViewModel
import dev.josueamaya.pourline.ui.recipes.RecipesScreen
import dev.josueamaya.pourline.ui.recipes.RecipesViewModel
import dev.josueamaya.pourline.ui.scale.TargetPickerViewModel
import dev.josueamaya.pourline.ui.session.SessionScreen
import dev.josueamaya.pourline.ui.settings.SettingsScreen
import dev.josueamaya.pourline.ui.tools.FourSixScreen
import dev.josueamaya.pourline.ui.tools.FourSixViewModel
import dev.josueamaya.pourline.ui.tools.RatioCalculatorScreen
import dev.josueamaya.pourline.ui.tools.RatioCalculatorViewModel
import dev.josueamaya.pourline.ui.tools.ToolsScreen
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * La app: la elección del primer arranque (D11) mientras `onboardingDone` sea falso y, después, las cinco
 * pestañas con su pila cada una (D30).
 */
@Composable
fun MainNavigation(deps: AppDependencies) {
  val onboardingDone by deps.experienceSettings.onboardingDone.collectAsStateWithLifecycle()
  if (onboardingDone) {
    TabsNavigation(deps)
  } else {
    val viewModel = viewModel { OnboardingViewModel(deps.grinderRepository, deps.grinderSettings, deps.experienceSettings) }
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
      entryProvider = tabEntryProvider(t, stacks.getValue(t), deps) { tab = it },
    )
  }
  val activeStack = stacks.getValue(tab)
  val showBar = showsBottomBar(activeStack)

  Column(Modifier.fillMaxSize()) {
    NavDisplay(
      // Con las entradas de Preparar debajo, "Atrás" en la raíz de otra pestaña tiene a dónde ir (D30).
      entries = if (tab == Tab.PREPARE) entries.getValue(Tab.PREPARE) else entries.getValue(Tab.PREPARE) + entries.getValue(tab),
      modifier = Modifier
        .weight(1f)
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
    if (showBar) {
      val items = Tab.entries.map { AntTabBarItem(it, stringResource(it.label), it.icon, it.testTag) }
      AntTabBar(
        items = items,
        selected = tab,
        // Tocar la pestaña activa vuelve a su raíz.
        onSelect = { selected -> if (selected == tab) activeStack.popToRoot() else tab = selected },
      )
    }
  }
}

/** Una entrada cuyo `contentKey` lleva el prefijo de la pestaña: dos pilas con la misma clave no comparten `ViewModel` ni estado. */
private inline fun <reified K : NavKey> EntryProviderScope<NavKey>.tabEntry(tab: Tab, noinline content: @Composable (K) -> Unit) {
  entry<K>(clazzContentKey = { key -> "${tab.name}/$key" }, content = content)
}

/** Las pantallas de la pestaña [tab]; los accesos cruzados se apilan en su propia pila [stack] (D31). */
private fun tabEntryProvider(
  tab: Tab,
  stack: NavBackStack<NavKey>,
  deps: AppDependencies,
  selectTab: (Tab) -> Unit,
): (NavKey) -> NavEntry<NavKey> = entryProvider {
  fun back() {
    stack.removeLastOrNull()
  }

  tabEntry<Prepare>(tab) {
    val viewModel = viewModel { PrepareViewModel(deps.scaleSession, deps.scaleMemory, deps.freeBrewDefaults, deps.bluetooth, deps.grindSelection) }
    PrepareScreen(
      viewModel = viewModel,
      scanner = deps.scanner,
      pickerViewModel = viewModel { TargetPickerViewModel(deps.recipeRepository, deps.brewRepository) },
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
  tabEntry<Session>(tab) {
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
  tabEntry<Coffees>(tab) {
    CoffeesScreen(
      viewModel { CoffeesViewModel(deps.coffeeRepository) },
      onBack = null,
      onOpenCoffee = { id -> stack.add(CoffeeDetail(id)) },
      onAddCoffee = { stack.add(CoffeeEdit()) },
    )
  }
  tabEntry<CoffeeDetail>(tab) { key ->
    CoffeeDetailScreen(
      viewModel { CoffeeDetailViewModel(deps.coffeeRepository, key.id) },
      onBack = ::back,
      onEdit = { id -> stack.add(CoffeeEdit(id = id)) },
      onNewBag = { id -> stack.add(CoffeeEdit(fromCoffeeId = id)) },
      onOpenBrew = { id -> stack.add(BrewDetail(id)) },
    )
  }
  tabEntry<CoffeeEdit>(tab) { key ->
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
  tabEntry<Settings>(tab) {
    SettingsScreen(
      deps.brewSettings,
      deps.targetSettings,
      deps.experienceSettings,
      deps.themeSettings,
      grinderRepository = deps.grinderRepository,
      grinderDefaults = deps.grinderSettings,
      onRestoreRecipes = { deps.recipeRepository.restoreBuiltIns() },
      onOpenGrinders = { stack.add(Grinders) },
      onBack = ::back,
    )
  }
  tabEntry<Grinders>(tab) {
    GrindersScreen(
      viewModel { GrindersViewModel(deps.grinderRepository, deps.grinderSettings) },
      onBack = ::back,
      onOpenGrinder = { id -> stack.add(GrinderDetail(id)) },
      onAddGrinder = { stack.add(GrinderCatalog) },
      onOpenConverter = { stack.add(GrindConverter()) },
    )
  }
  tabEntry<GrindConverter>(tab) { key ->
    GrindConverterScreen(
      viewModel { GrindConverterViewModel(deps.grinderRepository, key.sourceId, key.value) },
      onBack = ::back,
      onOpenGrinders = { stack.add(Grinders) },
      onFix = { fix, id -> stack.add(fixGrinderKey(fix, id)) },
    )
  }
  tabEntry<GrinderCatalog>(tab) {
    GrinderCatalogScreen(
      viewModel { GrinderCatalogViewModel(deps.grinderRepository) },
      onBack = ::back,
      onOpenGrinder = { id -> stack.add(GrinderDetail(id)) },
      onCreate = { stack.add(GrinderEdit()) },
    )
  }
  tabEntry<GrinderDetail>(tab) { key ->
    GrinderDetailScreen(
      viewModel { GrinderDetailViewModel(deps.grinderRepository, deps.grinderSettings, key.id) },
      onBack = ::back,
      onEdit = { id -> stack.add(GrinderEdit(id = id)) },
      onDuplicate = { id -> stack.add(GrinderEdit(fromId = id)) },
      toCalibrations = key.toCalibrations,
    )
  }
  tabEntry<GrinderEdit>(tab) { key ->
    GrinderEditScreen(
      viewModel { GrinderEditViewModel(deps.grinderRepository, key.id, key.fromId) },
      openRanges = key.openRanges,
      onBack = ::back,
    )
  }
  tabEntry<Recipes>(tab) {
    RecipesScreen(
      viewModel { RecipesViewModel(deps.recipeRepository) },
      onBack = null,
      onOpenRecipe = { id -> stack.add(RecipeEdit(id = id)) },
      onCreateRecipe = { stack.add(RecipeEdit()) },
    )
  }
  tabEntry<RecipeEdit>(tab) { key ->
    RecipeEditScreen(
      viewModel { RecipeEditViewModel(
          deps.recipeRepository, deps.brewRepository, deps.recipeTexts, deps.grinderRepository, deps.grinderSettings, deps.grindTexts, key.id, key.fromBrewId,
          draft = key.fourSix?.let(FourSix::plan)?.let { FourSix.recipe(it, deps.fourSixTexts) },
        ) },
      onBack = ::back,
      // Duplicar abre la copia en lugar de la original: atrás vuelve a la lista.
      onDuplicated = { copyId -> stack[stack.lastIndex] = RecipeEdit(id = copyId) },
      onOpenConverter = { stack.add(GrindConverter(it.sourceId, it.value)) },
    )
  }
  tabEntry<Tools>(tab) {
    ToolsScreen(
      onOpenRatio = { stack.add(RatioTool) },
      onOpenFourSix = { stack.add(FourSixTool) },
      onOpenConverter = { stack.add(GrindConverter()) },
    )
  }
  tabEntry<RatioTool>(tab) {
    RatioCalculatorScreen(viewModel { RatioCalculatorViewModel(deps.freeBrewDefaults) }, onBack = ::back)
  }
  tabEntry<FourSixTool>(tab) {
    FourSixScreen(
      viewModel { FourSixViewModel(deps.freeBrewDefaults) },
      onBack = ::back,
      onSaveAsRecipe = { params -> stack.add(RecipeEdit(fourSix = params)) },
    )
  }
  tabEntry<History>(tab) {
    HistoryScreen(
      viewModel { HistoryViewModel(deps.brewRepository) },
      onBack = null,
      onOpenBrew = { id -> stack.add(BrewDetail(id)) },
    )
  }
  tabEntry<BrewDetail>(tab) { key ->
    BrewDetailScreen(
      viewModel { BrewDetailViewModel(deps.brewRepository, deps.coffeeRepository, deps.grinderRepository, key.id) },
      onBack = ::back,
      onOpenCoffee = { id -> stack.add(CoffeeDetail(id)) },
      onEdit = { id -> stack.add(BrewEdit(id)) },
      onCompare = { id -> stack.add(ComparePick(id)) },
      onSaveAsRecipe = { id -> stack.add(RecipeEdit(fromBrewId = id)) },
    )
  }
  tabEntry<ComparePick>(tab) { key ->
    ComparePickScreen(
      viewModel { ComparePickViewModel(deps.brewRepository, key.aId) },
      onBack = ::back,
      // Atrás desde la comparación vuelve al detalle de A, no al selector.
      onPick = { bId -> stack[stack.lastIndex] = BrewCompare(key.aId, bId) },
    )
  }
  tabEntry<BrewCompare>(tab) { key ->
    BrewCompareScreen(
      viewModel { BrewCompareViewModel(deps.brewRepository, deps.coffeeRepository, key.aId, key.bId) },
      onBack = ::back,
    )
  }
  tabEntry<BrewEdit>(tab) { key ->
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
