package dev.dotscale

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import dev.dotscale.ble.BleScaleConnection
import dev.dotscale.recipe.ResourcesRecipeTexts
import dev.dotscale.sim.SimulatedScaleConnection
import dev.dotscale.ui.coffees.CoffeeDetailScreen
import dev.dotscale.ui.coffees.CoffeeDetailViewModel
import dev.dotscale.ui.coffees.CoffeeEditScreen
import dev.dotscale.ui.coffees.CoffeeEditViewModel
import dev.dotscale.ui.coffees.CoffeePickerViewModel
import dev.dotscale.ui.coffees.CoffeesScreen
import dev.dotscale.ui.coffees.CoffeesViewModel
import dev.dotscale.ui.history.BrewDetailScreen
import dev.dotscale.ui.history.BrewCompareScreen
import dev.dotscale.ui.history.BrewCompareViewModel
import dev.dotscale.ui.history.BrewDetailViewModel
import dev.dotscale.ui.history.BrewEditScreen
import dev.dotscale.ui.history.BrewEditViewModel
import dev.dotscale.ui.history.ComparePickScreen
import dev.dotscale.ui.history.ComparePickViewModel
import dev.dotscale.ui.history.HistoryScreen
import dev.dotscale.ui.history.HistoryViewModel
import dev.dotscale.ui.recipes.RecipeEditScreen
import dev.dotscale.ui.recipes.RecipeEditViewModel
import dev.dotscale.ui.recipes.RecipesScreen
import dev.dotscale.ui.recipes.RecipesViewModel
import dev.dotscale.ui.scale.AndroidStageNotifier
import dev.dotscale.ui.scale.RepositoryScaleCoffees
import dev.dotscale.ui.scale.RepositoryTargetReferences
import dev.dotscale.ui.scale.ScaleScreen
import dev.dotscale.ui.scale.ScaleViewModel
import dev.dotscale.ui.scale.TargetPickerViewModel
import dev.dotscale.ui.scan.ScanScreen
import dev.dotscale.ui.settings.SettingsScreen

@Composable
fun MainNavigation() {
  val backStack = rememberNavBackStack(Scan)
  val appContext = LocalContext.current.applicationContext
  val simulatedName = stringResource(R.string.scale_simulation)

  NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    entryDecorators = listOf(
      rememberSaveableStateHolderNavEntryDecorator(),
      rememberViewModelStoreNavEntryDecorator(),
    ),
    entryProvider = entryProvider {
      entry<Scan> {
        ScanScreen(
          onDeviceSelected = { address, name -> backStack.add(Scale(address, name)) },
          onSimulatedSelected = { backStack.add(Scale(address = null, name = simulatedName)) },
          onOpenSettings = { backStack.add(Settings) },
          onOpenHistory = { backStack.add(History) },
          onOpenRecipes = { backStack.add(Recipes) },
          onOpenCoffees = { backStack.add(Coffees) },
        )
      }
      entry<Scale> { key ->
        val app = appContext as DotScaleApp
        val viewModel = viewModel {
          ScaleViewModel(
            key.address?.let { BleScaleConnection(appContext, it) } ?: SimulatedScaleConnection(),
            app.brewSettings.autoFinishSeconds,
            app.brewRepository,
            app.applicationScope,
            references = app.targetSettings,
            targets = RepositoryTargetReferences(app.recipeRepository, app.brewRepository),
            notifier = AndroidStageNotifier(appContext, app.targetSettings),
            coffeeStore = app.brewSettings,
            coffees = RepositoryScaleCoffees(app.coffeeRepository),
          )
        }
        val pickerViewModel = viewModel { TargetPickerViewModel(app.recipeRepository, app.brewRepository) }
        val coffeePickerViewModel = viewModel { CoffeePickerViewModel(app.coffeeRepository) }
        ScaleScreen(
          viewModel,
          pickerViewModel,
          title = key.name,
          onBack = { backStack.removeLastOrNull() },
          onOpenSettings = { backStack.add(Settings) },
          onOpenHistory = { backStack.add(History) },
          onOpenCoffees = { backStack.add(Coffees) },
          onAddDetails = { id -> backStack.add(BrewEdit(id)) },
          onManageRecipes = { backStack.add(Recipes) },
          coffeePickerViewModel = coffeePickerViewModel,
          onNewCoffee = { backStack.add(CoffeeEdit()) },
        )
      }
      entry<Coffees> {
        val repository = (appContext as DotScaleApp).coffeeRepository
        CoffeesScreen(
          viewModel { CoffeesViewModel(repository) },
          onBack = { backStack.removeLastOrNull() },
          onOpenCoffee = { id -> backStack.add(CoffeeDetail(id)) },
          onAddCoffee = { backStack.add(CoffeeEdit()) },
        )
      }
      entry<CoffeeDetail> { key ->
        val repository = (appContext as DotScaleApp).coffeeRepository
        CoffeeDetailScreen(
          viewModel { CoffeeDetailViewModel(repository, key.id) },
          onBack = { backStack.removeLastOrNull() },
          onEdit = { id -> backStack.add(CoffeeEdit(id = id)) },
          onNewBag = { id -> backStack.add(CoffeeEdit(fromCoffeeId = id)) },
          onOpenBrew = { id -> backStack.add(BrewDetail(id)) },
        )
      }
      entry<CoffeeEdit> { key ->
        val app = appContext as DotScaleApp
        CoffeeEditScreen(
          viewModel { CoffeeEditViewModel(app.coffeeRepository, app.coffeePhotoStore, app.labelOcr, key.id, key.fromCoffeeId) },
          newCameraTarget = app.coffeePhotoStore::newCameraTarget,
          onBack = { backStack.removeLastOrNull() },
          // Una bolsa nueva se abre en su detalle (atrás vuelve al café original); en los demás casos, la pantalla
          // de origen ya muestra el resultado.
          onSaved = { savedId ->
            if (key.fromCoffeeId != null) backStack[backStack.lastIndex] = CoffeeDetail(savedId) else backStack.removeLastOrNull()
          },
          isEdit = key.id != null,
        )
      }
      entry<Settings> {
        val app = appContext as DotScaleApp
        SettingsScreen(
          app.brewSettings,
          app.targetSettings,
          onRestoreRecipes = { app.recipeRepository.restoreBuiltIns() },
          onBack = { backStack.removeLastOrNull() },
        )
      }
      entry<Recipes> {
        val repository = (appContext as DotScaleApp).recipeRepository
        RecipesScreen(
          viewModel { RecipesViewModel(repository) },
          onBack = { backStack.removeLastOrNull() },
          onOpenRecipe = { id -> backStack.add(RecipeEdit(id = id)) },
          onCreateRecipe = { backStack.add(RecipeEdit()) },
        )
      }
      entry<RecipeEdit> { key ->
        val app = appContext as DotScaleApp
        RecipeEditScreen(
          viewModel { RecipeEditViewModel(app.recipeRepository, app.brewRepository, ResourcesRecipeTexts(appContext), key.id, key.fromBrewId) },
          onBack = { backStack.removeLastOrNull() },
          // Duplicar abre la copia en lugar de la original: atrás vuelve a la lista.
          onDuplicated = { copyId -> backStack[backStack.lastIndex] = RecipeEdit(id = copyId) },
        )
      }
      entry<History> {
        val repository = (appContext as DotScaleApp).brewRepository
        HistoryScreen(
          viewModel { HistoryViewModel(repository) },
          onBack = { backStack.removeLastOrNull() },
          onOpenBrew = { id -> backStack.add(BrewDetail(id)) },
        )
      }
      entry<BrewDetail> { key ->
        val app = appContext as DotScaleApp
        BrewDetailScreen(
          viewModel { BrewDetailViewModel(app.brewRepository, app.coffeeRepository, key.id) },
          onBack = { backStack.removeLastOrNull() },
          onOpenCoffee = { id -> backStack.add(CoffeeDetail(id)) },
          onEdit = { id -> backStack.add(BrewEdit(id)) },
          onCompare = { id -> backStack.add(ComparePick(id)) },
          onSaveAsRecipe = { id -> backStack.add(RecipeEdit(fromBrewId = id)) },
        )
      }
      entry<ComparePick> { key ->
        val repository = (appContext as DotScaleApp).brewRepository
        ComparePickScreen(
          viewModel { ComparePickViewModel(repository, key.aId) },
          onBack = { backStack.removeLastOrNull() },
          // Atrás desde la comparación vuelve al detalle de A, no al selector.
          onPick = { bId -> backStack[backStack.lastIndex] = BrewCompare(key.aId, bId) },
        )
      }
      entry<BrewCompare> { key ->
        val app = appContext as DotScaleApp
        BrewCompareScreen(
          viewModel { BrewCompareViewModel(app.brewRepository, app.coffeeRepository, key.aId, key.bId) },
          onBack = { backStack.removeLastOrNull() },
        )
      }
      entry<BrewEdit> { key ->
        val app = appContext as DotScaleApp
        BrewEditScreen(
          viewModel { BrewEditViewModel(app.brewRepository, app.coffeeRepository, key.id) },
          onBack = { backStack.removeLastOrNull() },
          coffeePickerViewModel = viewModel { CoffeePickerViewModel(app.coffeeRepository) },
          onNewCoffee = { backStack.add(CoffeeEdit()) },
        )
      }
    },
  )
}
