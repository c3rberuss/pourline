package dev.josueamaya.pourline.ui.tools

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import dev.josueamaya.pourline.settings.FreeBrewDefaults
import dev.josueamaya.pourline.tools.FourSix
import dev.josueamaya.pourline.tools.FourSixPace
import dev.josueamaya.pourline.tools.FourSixParams
import dev.josueamaya.pourline.tools.FourSixPlan
import dev.josueamaya.pourline.tools.FourSixStrength
import dev.josueamaya.pourline.tools.FourSixTaste
import dev.josueamaya.pourline.tools.RatioCalculator
import dev.josueamaya.pourline.ui.prepare.parseOneDecimal

/** Por qué no hay plan. */
enum class FourSixInputError {
  /** Dosis o ratio fuera de rango, o con más de un decimal. */
  RANGE,

  /** Ritmo personalizado fuera de rango o con más de un decimal. */
  POUR_RATE,

  /** Dosis × ratio supera el agua máxima de una receta. */
  WATER,
}

/**
 * El generador 4:6; empieza con la dosis predeterminada de Preparar, a 1:15, equilibrado, intensidad media y ritmo
 * normal.
 */
class FourSixViewModel(defaults: FreeBrewDefaults) : ViewModel() {
  // Estado de snapshot y no un `StateFlow`: el campo que se escribe tiene que actualizarse en el mismo frame.
  var dose: String by mutableStateOf(RatioCalculator.format(defaults.defaultDoseG.value))
  var ratio: String by mutableStateOf(RatioCalculator.format(FourSix.DEFAULT_RATIO))
  var taste: FourSixTaste by mutableStateOf(FourSixTaste.BALANCED)
  var strength: FourSixStrength by mutableStateOf(FourSixStrength.STANDARD)
  var pace: FourSixPace by mutableStateOf(FourSixPace.NORMAL)
    private set

  /** El ritmo personalizado, en g/s, tal como se escribe. */
  var customPourRate: String by mutableStateOf(RatioCalculator.format(FourSix.DEFAULT_POUR_RATE_G_PER_S))

  /** Al pasar a [FourSixPace.CUSTOM], el campo empieza con el ritmo que había elegido. */
  fun selectPace(value: FourSixPace) {
    if (value == FourSixPace.CUSTOM && pace != FourSixPace.CUSTOM) {
      pace.gramsPerSecond?.let { customPourRate = RatioCalculator.format(it) }
    }
    pace = value
  }

  /** El ritmo elegido en g/s, o `null` si el personalizado no vale. */
  val pourRate: Double?
    get() = pace.gramsPerSecond ?: parseOneDecimal(customPourRate)?.takeIf(FourSix::isValidPourRate)

  private val doseG: Double? get() = parseOneDecimal(dose)?.takeIf(FourSix::isValidDose)

  private val ratioValue: Double? get() = parseOneDecimal(ratio)?.takeIf(FourSix::isValidRatio)

  /** Lo elegido, o `null` si la dosis, el ratio o el ritmo no valen. */
  val params: FourSixParams?
    get() = FourSixParams(doseG ?: return null, ratioValue ?: return null, taste, strength, pourRate ?: return null)

  val plan: FourSixPlan? get() = params?.let(FourSix::plan)

  val error: FourSixInputError?
    get() = when {
      doseG == null || ratioValue == null -> FourSixInputError.RANGE
      pourRate == null -> FourSixInputError.POUR_RATE
      plan == null -> FourSixInputError.WATER
      else -> null
    }
}
