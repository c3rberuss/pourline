package dev.dotscale.ui.scale

import dev.dotscale.brew.BrewState
import dev.dotscale.protocol.ScaleMessage
import dev.dotscale.protocol.ScaleModel
import dev.dotscale.protocol.TimerStatus
import dev.dotscale.protocol.WeightUnit
import dev.dotscale.scale.ConnectionState

/** Estado del guardado de la última sesión terminada. */
sealed interface SavedBrew {
  data object Saving : SavedBrew

  data class Saved(val id: Long) : SavedBrew

  data object Failed : SavedBrew
}

/** Errores que el ViewModel publica y la pantalla traduce a texto. */
sealed interface ScaleError {
  /** Un comando no se pudo enviar a la báscula; `detail` es el mensaje de la excepción, si lo hay. */
  data class Command(val detail: String?) : ScaleError

  data object SaveFailed : ScaleError
}

data class ScaleUiState(
  val connection: ConnectionState = ConnectionState.Disconnected,
  val weight: Double = 0.0,
  val flowRate: Double = 0.0,
  val seconds: Int = 0,
  val unit: WeightUnit = WeightUnit.GRAMS,
  val outOfRange: Boolean = false,
  val timer: TimerStatus = TimerStatus.RESET,
  val batteryPercent: Int? = null,
  val model: ScaleModel? = null,
  val firmware: String? = null,
  val deviceName: String? = null,
  val brewStage: String? = null,
  /** Dosis de café fijada por el usuario, en la unidad actual. Funcionalidad propia de esta app. */
  val dose: Double? = null,
  val isSimulated: Boolean = false,
  /** Sesión de preparación (gráfica de vertido). La rellena el ViewModel; `reduce` no la toca. */
  val brew: BrewState = BrewState.Initial,
  /** `null` sin sesión terminada; vuelve a `null` al empezar otra sesión. */
  val savedBrew: SavedBrew? = null,
) {
  /** Ratio agua:café (p. ej. 15.0 → 1:15). */
  val ratio: Double? get() = dose?.takeIf { it > 0 && weight > 0 }?.let { weight / it }

  fun reduce(message: ScaleMessage): ScaleUiState = when (message) {
    is ScaleMessage.Weight -> copy(
      weight = message.weight,
      flowRate = message.flowRate,
      seconds = message.seconds,
      unit = message.unit,
      outOfRange = message.outOfRange,
    )
    is ScaleMessage.Timer -> copy(timer = message.status)
    is ScaleMessage.Battery -> copy(batteryPercent = message.percent)
    is ScaleMessage.UnitChanged -> if (message.unit != unit) copy(unit = message.unit, dose = null) else this
    is ScaleMessage.Model -> copy(model = message.model)
    is ScaleMessage.FirmwareVersion -> copy(firmware = message.version)
    is ScaleMessage.DeviceName -> copy(deviceName = message.name.ifBlank { deviceName })
    is ScaleMessage.BrewModeStage -> copy(brewStage = message.description)
    else -> this
  }
}
