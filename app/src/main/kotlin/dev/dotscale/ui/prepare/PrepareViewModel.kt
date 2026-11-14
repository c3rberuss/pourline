package dev.dotscale.ui.prepare

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.dotscale.ble.BluetoothEnvironment
import dev.dotscale.coffee.PickedCoffee
import dev.dotscale.scale.ConnectionState
import dev.dotscale.session.ActiveTarget
import dev.dotscale.session.BrewPlan
import dev.dotscale.session.RememberedScale
import dev.dotscale.session.ScaleError
import dev.dotscale.session.ScaleMemory
import dev.dotscale.session.ScaleSessionManager
import dev.dotscale.session.ScaleTarget
import dev.dotscale.session.ScaleUiState
import dev.dotscale.session.targetWaterG
import dev.dotscale.settings.FreeBrewDefaults
import dev.dotscale.settings.ReferenceKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Lo que muestra Preparar. Con receta ([target] no es `null`) la dosis, el ratio y el agua son de solo lectura y
 * salen de ella; sin receta se editan la dosis ([plan]) y el ratio, y el agua se calcula.
 */
data class PrepareUiState(
  val card: ScaleCardState,
  val target: ActiveTarget?,
  val coffee: PickedCoffee?,
  val plan: BrewPlan,
  val waterG: Double,
  /** La dosis de la receta; `null` si la receta (o la preparación elegida) no la tiene. */
  val recipeDoseG: Double?,
  val canStart: Boolean,
) {
  /** El ratio agua:café de la receta; `null` sin dosis. */
  val recipeRatio: Double? get() = recipeDoseG?.takeIf { it > 0 }?.let { waterG / it }
}

/**
 * Combina el gestor de la báscula, la báscula recordada, el Bluetooth y la dosis y el ratio de Preparar. Las
 * acciones de conexión, receta y café se delegan en el gestor, que sobrevive a la pantalla.
 */
class PrepareViewModel(
  private val manager: ScaleSessionManager,
  private val memory: ScaleMemory,
  private val defaults: FreeBrewDefaults,
  private val bluetooth: BluetoothEnvironment,
) : ViewModel() {
  private val permissions = MutableStateFlow(bluetooth.permissionsGranted())

  private val environment: Flow<Pair<Boolean, Boolean>> = combine(permissions, bluetooth.enabled) { p, e -> p to e }
  private val plan: Flow<BrewPlan> = combine(defaults.defaultDoseG, defaults.defaultRatio, ::BrewPlan)

  val ui: StateFlow<PrepareUiState> = combine(manager.ui, manager.target, memory.lastScale, environment, plan) { scale, target, remembered, env, brewPlan ->
    build(scale, target, remembered, env.first, env.second, brewPlan)
  }.stateIn(
    viewModelScope,
    SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
    build(
      manager.ui.value,
      manager.target.value,
      memory.lastScale.value,
      permissions.value,
      bluetooth.enabled.value,
      BrewPlan(defaults.defaultDoseG.value, defaults.defaultRatio.value),
    ),
  )

  /** Los errores del gestor (`TargetRemoved`, `SaveFailed`, `Command`); los muestra la pantalla. */
  val errors: Flow<ScaleError> get() = manager.errors

  private fun build(
    scale: ScaleUiState,
    scaleTarget: ScaleTarget?,
    remembered: RememberedScale?,
    permissionsGranted: Boolean,
    bluetoothOn: Boolean,
    plan: BrewPlan,
  ): PrepareUiState {
    val target = scale.target
    return PrepareUiState(
      card = scaleCardState(
        permissions = permissionsGranted,
        bluetoothOn = bluetoothOn,
        remembered = remembered,
        target = scaleTarget,
        connection = scale.connection,
        lastFailure = scale.lastFailure,
        batteryPercent = scale.batteryPercent,
      ),
      target = target,
      coffee = scale.coffee,
      plan = plan,
      waterG = targetWaterG(target, null, plan),
      recipeDoseG = target?.nominalDoseG,
      canStart = scale.connection == ConnectionState.Ready,
    )
  }

  /** Vuelve a leer los permisos (al volver de su diálogo o de Ajustes). */
  fun refreshPermissions() {
    permissions.value = bluetooth.permissionsGranted()
  }

  fun connect(target: ScaleTarget) = manager.connect(target)

  /** "Conectar" con la báscula desconectada: al último destino o, si no hay, a la recordada. */
  fun connectLast() {
    val last = manager.target.value ?: memory.lastScale.value?.let { ScaleTarget.Real(it.address, it.name) } ?: return
    manager.connect(last)
  }

  fun retry() = manager.retry()

  /** "Empezar": abre la sesión guiada con la dosis y el ratio de Preparar. */
  fun startSession() = manager.startSession(ui.value.plan)

  fun disconnect() = manager.disconnect()

  fun selectTarget(key: ReferenceKey?) = manager.selectTarget(key)

  fun selectCoffee(id: Long?) = manager.selectCoffee(id)

  fun requestNewCoffee() = manager.requestNewCoffee()

  /** Guarda la dosis si el texto vale (D27); devuelve si valía. */
  fun setDose(text: String): Boolean {
    val dose = parseFreeDose(text) ?: return false
    defaults.setDefaultDoseG(dose)
    return true
  }

  /** Guarda el ratio si el texto vale (D27); devuelve si valía. */
  fun setRatio(text: String): Boolean {
    val ratio = parseFreeRatio(text) ?: return false
    defaults.setDefaultRatio(ratio)
    return true
  }

  private companion object {
    const val STOP_TIMEOUT_MS = 5_000L
  }
}
