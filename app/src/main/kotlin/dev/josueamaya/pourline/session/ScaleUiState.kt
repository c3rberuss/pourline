package dev.josueamaya.pourline.session

import dev.josueamaya.pourline.brew.BrewFit
import dev.josueamaya.pourline.brew.BrewState
import dev.josueamaya.pourline.brew.GuideState
import dev.josueamaya.pourline.brew.TargetCurve
import dev.josueamaya.pourline.coffee.PickedCoffee
import dev.josueamaya.pourline.grinder.GrindSetting
import dev.josueamaya.pourline.history.BrewDetails
import dev.josueamaya.pourline.history.ReferenceType
import dev.josueamaya.pourline.protocol.ScaleMessage
import dev.josueamaya.pourline.protocol.ScaleModel
import dev.josueamaya.pourline.protocol.TimerStatus
import dev.josueamaya.pourline.protocol.WeightUnit
import dev.josueamaya.pourline.scale.ConnectionState
import dev.josueamaya.pourline.settings.ReferenceKey

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

  /** La referencia elegida se borró: ya no hay objetivo. */
  data object TargetRemoved : ScaleError

  /** La preparación elegida como referencia no tiene una curva legible: ya no hay objetivo. */
  data object TargetUnreadable : ScaleError
}

/**
 * El objetivo de la sesión: la curva ya escalada a la dosis (en gramos) y lo que prellena los datos de la
 * preparación. Es la misma instancia mientras no cambien la referencia ni la dosis.
 */
data class ActiveTarget(
  val key: ReferenceKey,
  val type: ReferenceType,
  /** Nombre mostrado al elegir la referencia. */
  val name: String,
  val curve: TargetCurve,
  /** `dosis capturada / dosis nominal`, o 1 si falta alguna. */
  val factor: Double,
  /** La dosis con la que se hizo la curva (la de la receta o de la preparación); `null` si no la había. */
  val nominalDoseG: Double? = null,
  /** La dosis (capturada o escrita en Preparar), en gramos, con la que se escaló; `null` si el factor no sale de una dosis. */
  val scaledDoseG: Double?,
  /** Datos manuales que se copian de la referencia al guardar (sin nombre ni notas). */
  val prefill: BrewDetails,
  /** El molino y el ajuste de la receta o de la preparación de referencia (SPEC-006); no se copian a la preparación. */
  val sourceGrind: GrindSetting? = null,
)

/** La última sesión guardada: el momento de inicio y el café con el que se hizo (lo necesita la imagen del resumen). */
data class LastBrew(val startedAtEpochMs: Long, val coffee: PickedCoffee?)

/** El ajuste de la última sesión con objetivo, con la curva contra la que se midió. */
data class SessionFit(val name: String, val curve: TargetCurve, val fit: BrewFit)

data class ScaleUiState(
  val connection: ConnectionState = ConnectionState.Disconnected,
  /** La última conexión falló antes de llegar a `Ready` (no es lo mismo que perder una ya establecida). */
  val lastFailure: Boolean = false,
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
  /** El objetivo en `Armed` y `Finished`; en `Active`, el congelado al empezar. */
  val target: ActiveTarget? = null,
  /** La guía de la etapa; `null` sin objetivo. La rellena el ViewModel. */
  val guide: GuideState? = null,
  /** `null` sin sesión terminada con objetivo; vuelve a `null` al empezar otra sesión. */
  val lastFit: SessionFit? = null,
  /** El café elegido fuera de `Active`; en `Active`, el congelado al empezar. `null` sin café. */
  val coffee: PickedCoffee? = null,
  /** `null` sin sesión guardada; vuelve a `null` al empezar otra sesión. */
  val lastBrew: LastBrew? = null,
  /** Los pasos previos de la sesión abierta; `null` sin sesión. En `POUR` la sesión ya está armada. */
  val prep: PrepState? = null,
  /** El tiempo de la sesión (D25): `reloj − t0` en `Active` y el tiempo total en `Finished`; `null` antes. */
  val elapsedMs: Long? = null,
  /** La dosis y el ratio de Preparar con los que se abrió la sesión; `null` sin sesión abierta. */
  val plan: BrewPlan? = null,
  /** El agua objetivo del aro (D26); `null` sin sesión abierta. */
  val targetWaterG: Double? = null,
  /** Sesión cronometrada, sin báscula (SPEC-007): desde "Empezar sin báscula" hasta "Listo" o "Descartar". */
  val scaleless: Boolean = false,
  /** La dosis del plan de la sesión sin báscula, en gramos; `null` sin sesión sin báscula o sin dosis. */
  val plannedDoseG: Double? = null,
  /** Los segundos que le quedan a la cuenta regresiva previa a la preparación; `null` sin cuenta en marcha. */
  val countdownS: Int? = null,
) {
  /** La unidad de la sesión: la de la báscula, o gramos sin báscula. */
  val sessionUnit: WeightUnit get() = if (scaleless) WeightUnit.GRAMS else unit

  /** Hay una sesión abierta: desde "Empezar" hasta "Listo", la X antes de `Active` o "Descartar". */
  val hasSession: Boolean get() = plan != null

  /** Ratio agua:café (p. ej. 15.0 → 1:15). */
  val ratio: Double? get() = dose?.takeIf { it > 0 && weight > 0 }?.let { weight / it }

  /** Una conexión nueva: el estado del dispositivo vuelve a su valor inicial; sesión, objetivo, café y dosis siguen. */
  fun resetDevice(isSimulated: Boolean): ScaleUiState = copy(
    connection = ConnectionState.Disconnected,
    lastFailure = false,
    weight = 0.0,
    flowRate = 0.0,
    seconds = 0,
    unit = WeightUnit.GRAMS,
    outOfRange = false,
    timer = TimerStatus.RESET,
    batteryPercent = null,
    model = null,
    firmware = null,
    deviceName = null,
    brewStage = null,
    isSimulated = isSimulated,
  )

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
