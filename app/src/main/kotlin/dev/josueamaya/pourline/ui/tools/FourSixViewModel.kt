package dev.josueamaya.pourline.ui.tools

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import dev.josueamaya.pourline.settings.FreeBrewDefaults
import dev.josueamaya.pourline.tools.FourSix
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

  /** Dosis × ratio supera el agua máxima de una receta. */
  WATER,
}

/** El generador 4:6; empieza con la dosis predeterminada de Preparar, a 1:15, equilibrado e intensidad media. */
class FourSixViewModel(defaults: FreeBrewDefaults) : ViewModel() {
  // Estado de snapshot y no un `StateFlow`: el campo que se escribe tiene que actualizarse en el mismo frame.
  var dose: String by mutableStateOf(RatioCalculator.format(defaults.defaultDoseG.value))
  var ratio: String by mutableStateOf(RatioCalculator.format(FourSix.DEFAULT_RATIO))
  var taste: FourSixTaste by mutableStateOf(FourSixTaste.BALANCED)
  var strength: FourSixStrength by mutableStateOf(FourSixStrength.STANDARD)

  /** Lo elegido, o `null` si la dosis o el ratio no valen. */
  val params: FourSixParams?
    get() {
      val doseG = parseOneDecimal(dose)?.takeIf(FourSix::isValidDose) ?: return null
      val ratio = parseOneDecimal(ratio)?.takeIf(FourSix::isValidRatio) ?: return null
      return FourSixParams(doseG, ratio, taste, strength)
    }

  val plan: FourSixPlan? get() = params?.let(FourSix::plan)

  val error: FourSixInputError?
    get() = when {
      params == null -> FourSixInputError.RANGE
      plan == null -> FourSixInputError.WATER
      else -> null
    }
}
