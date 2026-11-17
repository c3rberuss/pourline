package dev.josueamaya.pourline.ui.scale

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.josueamaya.pourline.R
import dev.josueamaya.pourline.coffee.PickedCoffee
import dev.josueamaya.pourline.coffee.daysSinceRoast
import dev.josueamaya.pourline.session.ActiveTarget
import dev.josueamaya.pourline.ui.components.AntIcons
import dev.josueamaya.pourline.ui.components.AntSelectTrigger
import dev.josueamaya.pourline.ui.components.AntText
import dev.josueamaya.pourline.ui.components.AntTextType
import dev.josueamaya.pourline.ui.theme.AntTheme
import java.time.LocalDate

private val SetupLabelWidth = 72.dp
private val SetupIconSize = 16.dp

/** Una fila de preparación (D5): la etiqueta a la izquierda y un disparador con el valor elegido. */
@Composable
private fun BrewSetupRow(label: String, modifier: Modifier, trigger: @Composable (Modifier) -> Unit) {
  Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AntTheme.spacing.xs), verticalAlignment = Alignment.CenterVertically) {
    AntText(label, Modifier.width(SetupLabelWidth), type = AntTextType.Secondary)
    trigger(Modifier.weight(1f))
  }
}

@Composable
internal fun TargetSelectRow(
  target: ActiveTarget?,
  enabled: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  label: String = stringResource(R.string.brew_setup_target),
) {
  BrewSetupRow(label, modifier) { triggerModifier ->
    AntSelectTrigger(
      value = target?.name ?: stringResource(R.string.brew_setup_target_none),
      onClick = onClick,
      modifier = triggerModifier,
      leading = { Icon(AntIcons.Recipe, contentDescription = null, modifier = Modifier.size(SetupIconSize), tint = AntTheme.colors.textTertiary) },
      enabled = enabled,
      description = target?.let { stringResource(R.string.target_chip, it.name) } ?: stringResource(R.string.target_chip_none),
      testTag = "target_chip",
    )
  }
}

@Composable
internal fun CoffeeSelectRow(
  coffee: PickedCoffee?,
  enabled: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  label: String = stringResource(R.string.brew_setup_coffee),
) {
  val days = coffee?.let { daysSinceRoast(it.roastDate, LocalDate.now()) }
  val value = when {
    coffee == null -> stringResource(R.string.brew_setup_coffee_none)
    days != null -> stringResource(R.string.brew_setup_coffee_value, coffee.name, days)
    else -> coffee.name
  }
  val description = when {
    coffee == null -> stringResource(R.string.brew_setup_coffee_description_none)
    days != null -> pluralStringResource(R.plurals.brew_setup_coffee_description, days, coffee.name, days)
    else -> stringResource(R.string.brew_setup_coffee_description_plain, coffee.name)
  }
  BrewSetupRow(label, modifier) { triggerModifier ->
    AntSelectTrigger(
      value = value,
      onClick = onClick,
      modifier = triggerModifier,
      leading = { Icon(AntIcons.Coffee, contentDescription = null, modifier = Modifier.size(SetupIconSize), tint = AntTheme.colors.textTertiary) },
      enabled = enabled,
      description = description,
      testTag = "coffee_trigger",
    )
  }
}
