package dev.dotscale.ui.coffees

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.dotscale.R
import dev.dotscale.coffee.CoffeeEntity
import dev.dotscale.ui.components.AntText
import dev.dotscale.ui.components.AntTextType
import dev.dotscale.ui.theme.AntTheme

private val LABEL_WIDTH = 112.dp

/** Una fila de "Datos del café": la etiqueta en 112 dp y el valor. */
@Composable
fun CoffeeFact(label: String, value: String, testTag: String, modifier: Modifier = Modifier) {
  Row(modifier.fillMaxWidth().testTag(testTag), horizontalArrangement = Arrangement.spacedBy(AntTheme.spacing.xs)) {
    AntText(label, Modifier.width(LABEL_WIDTH), type = AntTextType.Secondary)
    AntText(value, Modifier.weight(1f))
  }
}

private class FactRow(val field: String, val label: Int, val value: String)

/** Las filas de datos del café, sin las vacías. */
@Composable
private fun factRows(coffee: CoffeeEntity): List<FactRow> {
  val rows = mutableListOf<FactRow>()
  coffee.roaster?.let { rows += FactRow("roaster", R.string.coffee_field_roaster, it) }
  val origin = listOfNotNull(coffee.originCountry, coffee.originRegion).joinToString(" · ")
  if (origin.isNotEmpty()) rows += FactRow("origin", R.string.coffee_field_origin, origin)
  coffee.farm?.let { rows += FactRow("farm", R.string.coffee_field_farm, it) }
  if (coffee.varieties.isNotEmpty()) rows += FactRow("varieties", R.string.coffee_field_varieties, coffee.varieties.joinToString(", "))
  processText(coffee.process, coffee.processOther)?.let { rows += FactRow("process", R.string.coffee_field_process, it) }
  formatAltitude(coffee.altitudeMinM, coffee.altitudeMaxM)?.let { rows += FactRow("altitude", R.string.coffee_field_altitude_short, it) }
  val roast = listOfNotNull(coffee.roastLevel?.let { it.label() }, coffee.roastDate?.let(::formatCoffeeDate)).joinToString(" · ")
  if (roast.isNotEmpty()) rows += FactRow("roast", R.string.coffee_field_roast, roast)
  coffee.roasterNotes?.let { rows += FactRow("roaster_notes", R.string.coffee_field_roaster_notes, it) }
  val bag = listOfNotNull(coffee.bagWeightG?.let(::formatGrams), coffee.price?.let(::formatPrice)).joinToString(" · ")
  if (bag.isNotEmpty()) rows += FactRow("bag", R.string.coffee_field_bag, bag)
  coffee.openedDate?.let { rows += FactRow("opened", R.string.coffee_field_opened, formatCoffeeDate(it)) }
  return rows
}

/** `true` si el café tiene algún dato que mostrar en el plegable (el mismo criterio que [factRows]). */
fun hasFacts(coffee: CoffeeEntity): Boolean = with(coffee) {
  roaster != null || originCountry != null || originRegion != null || farm != null || varieties.isNotEmpty() ||
    process != null || altitudeMinM != null || altitudeMaxM != null || roastLevel != null || roastDate != null ||
    roasterNotes != null || bagWeightG != null || price != null || openedDate != null
}

/** Las filas `coffee_fact_<campo>` del plegable, sin las vacías. */
@Composable
fun CoffeeFacts(coffee: CoffeeEntity, modifier: Modifier = Modifier) {
  Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.xs)) {
    factRows(coffee).forEach { row -> CoffeeFact(stringResource(row.label), row.value, "coffee_fact_${row.field}") }
  }
}

/** El `extra` de la cabecera del plegable: "Natural · Claro", con las partes que haya. */
@Composable
fun factsSummary(coffee: CoffeeEntity): String? {
  val parts = listOfNotNull(processText(coffee.process, coffee.processOther), coffee.roastLevel?.label())
  return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}
