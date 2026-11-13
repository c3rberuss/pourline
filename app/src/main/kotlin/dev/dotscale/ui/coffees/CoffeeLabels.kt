package dev.dotscale.ui.coffees

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.dotscale.R
import dev.dotscale.coffee.CoffeeError
import dev.dotscale.coffee.CoffeeProcess
import dev.dotscale.coffee.RoastLevel

@Composable
fun CoffeeProcess.label(): String = stringResource(
  when (this) {
    CoffeeProcess.WASHED -> R.string.coffee_process_washed
    CoffeeProcess.NATURAL -> R.string.coffee_process_natural
    CoffeeProcess.HONEY -> R.string.coffee_process_honey
    CoffeeProcess.ANAEROBIC -> R.string.coffee_process_anaerobic
    CoffeeProcess.WET_HULLED -> R.string.coffee_process_wet_hulled
    CoffeeProcess.OTHER -> R.string.coffee_process_other
  },
)

@Composable
fun RoastLevel.label(): String = stringResource(
  when (this) {
    RoastLevel.LIGHT -> R.string.coffee_roast_light
    RoastLevel.MEDIUM -> R.string.coffee_roast_medium
    RoastLevel.DARK -> R.string.coffee_roast_dark
  },
)

/** El texto del error de validación de un campo del editor. */
@Composable
fun CoffeeError.message(): String = stringResource(
  when (this) {
    CoffeeError.NAME_REQUIRED -> R.string.coffee_error_name
    CoffeeError.ALTITUDE_INVALID -> R.string.coffee_error_altitude_invalid
    CoffeeError.ALTITUDE_RANGE -> R.string.coffee_error_altitude_range
    CoffeeError.BAG_WEIGHT_INVALID -> R.string.coffee_error_bag_weight
    CoffeeError.PRICE_INVALID -> R.string.coffee_error_price
    CoffeeError.ROAST_DATE_FUTURE, CoffeeError.OPENED_DATE_FUTURE -> R.string.coffee_error_date_future
  },
)

/** El proceso para mostrarlo: con `OTHER`, el texto propio o "Otro". */
@Composable
fun processText(process: CoffeeProcess?, processOther: String?): String? = when {
  process == null -> null
  process == CoffeeProcess.OTHER -> processOther?.takeIf { it.isNotBlank() } ?: process.label()
  else -> process.label()
}
