package dev.dotscale.ui.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.dotscale.R
import dev.dotscale.protocol.WeightUnit
import dev.dotscale.sim.FollowMode
import dev.dotscale.sim.PourProfile
import dev.dotscale.ui.components.AntButton
import dev.dotscale.ui.components.AntButtonType
import dev.dotscale.ui.components.AntDivider
import dev.dotscale.ui.components.AntDrawer
import dev.dotscale.ui.components.AntSegmented
import dev.dotscale.ui.components.AntSegmentedOption
import dev.dotscale.ui.components.AntSwitch
import dev.dotscale.ui.components.AntText
import dev.dotscale.ui.components.AntTextType
import dev.dotscale.ui.theme.AntSize
import dev.dotscale.ui.theme.AntTheme

/** Los controles de la báscula simulada (los de la antigua `SimulationCard`). */
class SimulationActions(
  val hasTarget: Boolean,
  val onAdd: (Double) -> Unit,
  val onPour: (PourProfile) -> Unit,
  val onFollow: (FollowMode) -> Unit,
)

/**
 * El menú ⋯ de la sesión (D2): "Modo experto", "Unidad", "Reiniciar" y, con la báscula simulada, la sección de
 * simulación.
 *
 * @param unitEnabled la unidad solo se cambia con la báscula `Ready`.
 * @param restartEnabled "Reiniciar" está deshabilitado en `Active` (la X ya ofrece terminar o descartar).
 * @param simulation `null` con una báscula real.
 */
@Composable
fun SessionOptionsSheet(
  expert: Boolean,
  onExpertChange: (Boolean) -> Unit,
  unit: WeightUnit,
  unitEnabled: Boolean,
  onUnitChange: (WeightUnit) -> Unit,
  restartEnabled: Boolean,
  onRestart: () -> Unit,
  simulation: SimulationActions?,
  onClose: () -> Unit,
) {
  AntDrawer(title = stringResource(R.string.session_options), onClose = onClose, modifier = Modifier.testTag("session_options_sheet")) {
    OptionRow(stringResource(R.string.session_options_expert)) {
      AntSwitch(checked = expert, onCheckedChange = onExpertChange, modifier = Modifier.testTag("session_expert_switch"))
    }
    OptionRow(stringResource(R.string.session_options_unit)) {
      AntSegmented(
        options = WeightUnit.entries.map { AntSegmentedOption(it, it.symbol) },
        selected = unit,
        onSelect = { if (it != unit) onUnitChange(it) },
        modifier = Modifier.width(160.dp).testTag("session_unit"),
        enabled = unitEnabled,
      )
    }
    AntButton(
      text = stringResource(R.string.session_options_restart),
      onClick = {
        onRestart()
        onClose()
      },
      modifier = Modifier.fillMaxWidth().testTag("session_restart"),
      enabled = restartEnabled,
    )
    if (simulation != null) SimulationSection(simulation)
  }
}

@Composable
private fun OptionRow(label: String, control: @Composable () -> Unit) {
  Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    AntText(label, Modifier.weight(1f))
    control()
  }
}

@Composable
private fun ColumnScope.SimulationSection(simulation: SimulationActions) {
  AntDivider()
  AntText(stringResource(R.string.session_options_simulation), type = AntTextType.Secondary)
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AntTheme.spacing.xs)) {
    listOf(15, 100, 300).forEach { grams ->
      AntButton(
        text = stringResource(R.string.scale_sim_add, grams),
        onClick = { simulation.onAdd(grams.toDouble()) },
        modifier = Modifier.weight(1f).testTag("sim_add_$grams"),
        type = AntButtonType.Dashed,
        size = AntSize.Small,
      )
    }
  }
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AntTheme.spacing.xs)) {
    AntButton(
      text = stringResource(R.string.sim_pour_stable),
      onClick = { simulation.onPour(PourProfile.STABLE) },
      modifier = Modifier.weight(1f).testTag("sim_pour_stable"),
      type = AntButtonType.Dashed,
      size = AntSize.Small,
    )
    AntButton(
      text = stringResource(R.string.sim_pour_irregular),
      onClick = { simulation.onPour(PourProfile.IRREGULAR) },
      modifier = Modifier.weight(1f).testTag("sim_pour_irregular"),
      type = AntButtonType.Dashed,
      size = AntSize.Small,
    )
  }
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AntTheme.spacing.xs)) {
    AntButton(
      text = stringResource(R.string.target_sim_follow),
      onClick = { simulation.onFollow(FollowMode.FAITHFUL) },
      modifier = Modifier.weight(1f).testTag("sim_follow_target"),
      type = AntButtonType.Dashed,
      size = AntSize.Small,
      enabled = simulation.hasTarget,
    )
    AntButton(
      text = stringResource(R.string.target_sim_deviated),
      onClick = { simulation.onFollow(FollowMode.DEVIATED) },
      modifier = Modifier.weight(1f).testTag("sim_follow_deviated"),
      type = AntButtonType.Dashed,
      size = AntSize.Small,
      enabled = simulation.hasTarget,
    )
  }
}
