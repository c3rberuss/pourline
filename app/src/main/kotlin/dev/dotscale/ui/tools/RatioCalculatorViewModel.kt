package dev.dotscale.ui.tools

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import dev.dotscale.settings.FreeBrewDefaults
import dev.dotscale.tools.RatioCalculator
import dev.dotscale.tools.RatioCalculatorState
import dev.dotscale.tools.RatioField

/** La calculadora de ratio; empieza con la dosis y el ratio predeterminados de Preparar. */
class RatioCalculatorViewModel(defaults: FreeBrewDefaults) : ViewModel() {
  // Estado de snapshot y no un `StateFlow`: el campo que se escribe tiene que actualizarse en el mismo frame.
  var state: RatioCalculatorState by mutableStateOf(RatioCalculator.initial(defaults.defaultDoseG.value, defaults.defaultRatio.value))
    private set

  fun edit(field: RatioField, text: String) {
    state = RatioCalculator.edit(state, field, text)
  }
}
