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
import dev.dotscale.sim.SimulatedScaleConnection
import dev.dotscale.ui.history.BrewDetailScreen
import dev.dotscale.ui.history.BrewDetailViewModel
import dev.dotscale.ui.history.BrewEditScreen
import dev.dotscale.ui.history.BrewEditViewModel
import dev.dotscale.ui.history.HistoryScreen
import dev.dotscale.ui.history.HistoryViewModel
import dev.dotscale.ui.scale.ScaleScreen
import dev.dotscale.ui.scale.ScaleViewModel
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
          )
        }
        ScaleScreen(
          viewModel,
          title = key.name,
          onBack = { backStack.removeLastOrNull() },
          onOpenSettings = { backStack.add(Settings) },
          onOpenHistory = { backStack.add(History) },
          onAddDetails = { id -> backStack.add(BrewEdit(id)) },
        )
      }
      entry<Settings> {
        SettingsScreen((appContext as DotScaleApp).brewSettings, onBack = { backStack.removeLastOrNull() })
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
        val repository = (appContext as DotScaleApp).brewRepository
        BrewDetailScreen(
          viewModel { BrewDetailViewModel(repository, key.id) },
          onBack = { backStack.removeLastOrNull() },
          onEdit = { id -> backStack.add(BrewEdit(id)) },
        )
      }
      entry<BrewEdit> { key ->
        val repository = (appContext as DotScaleApp).brewRepository
        BrewEditScreen(
          viewModel { BrewEditViewModel(repository, key.id) },
          onBack = { backStack.removeLastOrNull() },
        )
      }
    },
  )
}
