package dev.dotscale.ui.tools

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import dev.dotscale.settings.FreeBrewDefaults
import dev.dotscale.tools.FourSix
import dev.dotscale.tools.FourSixParams
import dev.dotscale.tools.FourSixPlan
import dev.dotscale.tools.FourSixStrength
import dev.dotscale.tools.FourSixTaste
import dev.dotscale.tools.RatioCalculator
import dev.dotscale.ui.prepare.parseOneDecimal

/** Por qué no hay plan. */
enum class FourSixInputError {
  /** Dosis o ratio fuera de rango, o con más de un decimal. */
  RANGE,

  /** Dosis × ratio supera el agua máxima de una receta. */
  WATER,
}

/** El generador 4:6; empieza con la dosis predeterminada de Preparar, a 1:15, balanceado y estándar. */
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
