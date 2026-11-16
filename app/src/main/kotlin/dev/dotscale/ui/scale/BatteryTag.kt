package dev.dotscale.ui.scale

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import dev.dotscale.R
import dev.dotscale.ui.components.AntIcons
import dev.dotscale.ui.components.AntTag
import dev.dotscale.ui.components.AntTagColor
import dev.dotscale.ui.theme.AntTheme

private const val LOW_BATTERY_PERCENT = 20

/** La batería de la báscula en una etiqueta; en rojo con poca carga. */
@Composable
internal fun BatteryTag(percent: Int) {
  val description = stringResource(R.string.scale_battery, percent)
  AntTag(
    text = stringResource(R.string.scale_battery_short, percent),
    modifier = Modifier
      .padding(end = AntTheme.spacing.xxs)
      .clearAndSetSemantics { contentDescription = description },
    color = if (percent <= LOW_BATTERY_PERCENT) AntTagColor.Error else AntTagColor.Default,
    icon = AntIcons.Battery,
  )
}
