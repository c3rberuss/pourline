package dev.josueamaya.pourline.ui.grinders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.josueamaya.pourline.R
import dev.josueamaya.pourline.grinder.GrindFormat
import dev.josueamaya.pourline.grinder.GrindStepper
import dev.josueamaya.pourline.grinder.Grinder
import dev.josueamaya.pourline.grinder.decimalsOf
import dev.josueamaya.pourline.ui.components.AntStepper
import dev.josueamaya.pourline.ui.theme.AntTheme

/**
 * La introducción de un ajuste (§3.2): un `AntStepper` por unidad de la jerarquía, de la más gruesa a la más fina, con
 * " · " entre columnas. El valor es absoluto (en unidades base), así que el acarreo y el préstamo salen solos.
 * Con [value] `null` cada parte muestra "—" y el primer toque fija [start] sin sumar (D13).
 *
 * Con [clampToScale] el resultado se limita a la escala del molino; sin él, solo a `>= 0` (es lo que usa el editor
 * para la propia escala). Los tags son `<testTag>_<i>_inc`, `_dec` y `_value`.
 */
@Composable
fun GrindInput(
  value: Double?,
  grinder: Grinder,
  onChange: (Double) -> Unit,
  start: Double,
  modifier: Modifier = Modifier,
  testTag: String = "grind_input",
  clampToScale: Boolean = true,
) {
  val texts = rememberGrindTexts()
  val limits = if (clampToScale) grinder else grinder.copy(scaleMin = if (grinder.units.size > 1) 0.0 else -UNBOUNDED, scaleMax = UNBOUNDED)
  val parts = value?.let { GrindFormat.decompose(it, grinder.units) }
  val description = if (value == null) stringResource(R.string.grind_input_empty) else GrindFormat.describe(value, grinder, texts)
  val placeholder = stringResource(R.string.grind_input_placeholder)

  Row(
    modifier.semantics { contentDescription = description },
    horizontalArrangement = Arrangement.spacedBy(AntTheme.spacing.xs, Alignment.CenterHorizontally),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    grinder.units.forEachIndexed { i, level ->
      if (i > 0) Text("·", color = AntTheme.colors.textTertiary, style = AntTheme.typography.heading4)
      val unitName = level.unit.label()
      val shown = parts?.get(i)?.let { texts.number(it, if (i == grinder.units.lastIndex) decimalsOf(grinder.step) else 0) } ?: placeholder
      AntStepper(
        value = shown,
        label = unitName,
        onIncrement = { onChange(GrindStepper.step(value, i, +1, limits, start)) },
        onDecrement = { onChange(GrindStepper.step(value, i, -1, limits, start)) },
        incrementDescription = stringResource(R.string.grind_input_increase, unitName),
        decrementDescription = stringResource(R.string.grind_input_decrease, unitName),
        modifier = Modifier.widthIn(min = 72.dp).padding(horizontal = 2.dp),
        incrementEnabled = GrindStepper.canStep(value, i, +1, limits, start),
        decrementEnabled = GrindStepper.canStep(value, i, -1, limits, start),
        testTag = "${testTag}_$i",
      )
    }
  }
}

private const val UNBOUNDED = 1_000_000.0
