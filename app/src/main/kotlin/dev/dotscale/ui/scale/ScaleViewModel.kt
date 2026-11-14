package dev.dotscale.ui.scale

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.dotscale.brew.StageNotifier
import dev.dotscale.brew.TargetConfig
import dev.dotscale.history.BrewSaver
import dev.dotscale.session.ScaleSessionManager
import dev.dotscale.scale.ScaleConnection
import dev.dotscale.settings.CoffeeStore
import dev.dotscale.settings.NoCoffeeStore
import dev.dotscale.settings.ReferenceKey
import dev.dotscale.settings.ReferenceStore
import dev.dotscale.sim.FollowMode
import dev.dotscale.sim.PourProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Adaptador provisional: la lógica vive en [ScaleSessionManager] y este `ViewModel` solo le da el ámbito de
 * `viewModelScope` y lo cierra en `onCleared`. Desaparece cuando el gestor pase a la app (F2).
 */
class ScaleViewModel(
  connection: ScaleConnection,
  autoFinishSeconds: StateFlow<Int>,
  saver: BrewSaver,
  applicationScope: CoroutineScope,
  references: ReferenceStore,
  targets: TargetReferences,
  notifier: StageNotifier,
  coffeeStore: CoffeeStore = NoCoffeeStore,
  coffees: ScaleCoffees = NoScaleCoffees,
  clock: () -> Long = SystemClock::elapsedRealtime,
  wallClock: () -> Long = System::currentTimeMillis,
  targetConfig: TargetConfig = TargetConfig(),
) : ViewModel() {

  private val manager = ScaleSessionManager(
    connection = connection,
    scope = viewModelScope,
    autoFinishSeconds = autoFinishSeconds,
    saver = saver,
    applicationScope = applicationScope,
    references = references,
    targets = targets,
    notifier = notifier,
    coffeeStore = coffeeStore,
    coffees = coffees,
    clock = clock,
    wallClock = wallClock,
    targetConfig = targetConfig,
  )

  val ui: StateFlow<ScaleUiState> get() = manager.ui

  val errors: Flow<ScaleError> get() = manager.errors

  /** Cuántas veces se ha construido el objetivo escalado. Lo lee el test de rendimiento. */
  internal val targetBuilds: Int get() = manager.targetBuilds

  fun connect() = manager.connect()

  fun tare() = manager.tare()

  fun toggleTimer() = manager.toggleTimer()

  fun resetTimer() = manager.resetTimer()

  fun toggleUnit() = manager.toggleUnit()

  /** Guarda el peso actual como dosis de café y hace tara para empezar a verter. */
  fun captureDose() = manager.captureDose()

  fun clearDose() = manager.clearDose()

  /** Botón "Terminar" de la gráfica. */
  fun finishBrew() = manager.finishBrew()

  fun selectTarget(key: ReferenceKey?) = manager.selectTarget(key)

  fun selectCoffee(id: Long?) = manager.selectCoffee(id)

  fun requestNewCoffee() = manager.requestNewCoffee()

  fun simulateAdd(grams: Double) = manager.simulateAdd(grams)

  fun simulatePour(profile: PourProfile) = manager.simulatePour(profile)

  fun simulateTargetPour(mode: FollowMode) = manager.simulateTargetPour(mode)

  override fun onCleared() = manager.close()
}
