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
import dev.dotscale.ui.scale.ScaleScreen
import dev.dotscale.ui.scale.ScaleViewModel
import dev.dotscale.ui.scan.ScanScreen

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
        )
      }
      entry<Scale> { key ->
        val viewModel = viewModel {
          ScaleViewModel(
            key.address?.let { BleScaleConnection(appContext, it) } ?: SimulatedScaleConnection(),
          )
        }
        ScaleScreen(viewModel, title = key.name, onBack = { backStack.removeLastOrNull() })
      }
    },
  )
}
