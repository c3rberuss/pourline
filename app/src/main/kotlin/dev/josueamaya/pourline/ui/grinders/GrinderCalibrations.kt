package dev.josueamaya.pourline.ui.grinders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import dev.josueamaya.pourline.R
import dev.josueamaya.pourline.grinder.GrindCalibration
import dev.josueamaya.pourline.grinder.GrindTexts
import dev.josueamaya.pourline.grinder.Grinder
import dev.josueamaya.pourline.ui.components.AntButton
import dev.josueamaya.pourline.ui.components.AntButtonType
import dev.josueamaya.pourline.ui.components.AntCard
import dev.josueamaya.pourline.ui.components.AntIconButton
import dev.josueamaya.pourline.ui.components.AntIcons
import dev.josueamaya.pourline.ui.components.AntTag
import dev.josueamaya.pourline.ui.components.AntTagColor
import dev.josueamaya.pourline.ui.components.AntText
import dev.josueamaya.pourline.ui.components.AntTextType
import dev.josueamaya.pourline.ui.theme.AntTheme

/** Los pares de un molino con [other], ordenados por el ajuste del molino de la pantalla. [editable] si los dos son míos. */
data class CalibrationGroup(val other: Grinder, val pairs: List<GrindCalibration>, val editable: Boolean)

/**
 * La sección "Equivalencias" del detalle de un molino (D54): sus pares agrupados por el otro molino, con editar (solo
 * si los dos molinos son míos) y borrar, y "Añadir equivalencia" si [canAdd]. Un valor fuera de la escala de su molino
 * (porque se editó la escala) lleva la marca de fuera de escala.
 */
@Composable
fun GrinderCalibrations(
  grinder: Grinder,
  groups: List<CalibrationGroup>,
  canAdd: Boolean,
  texts: GrindTexts,
  onAdd: () -> Unit,
  onEdit: (GrindCalibration) -> Unit,
  onDelete: (GrindCalibration) -> Unit,
  modifier: Modifier = Modifier,
) {
  AntCard(modifier.fillMaxWidth().testTag("grinder_calibrations"), title = stringResource(R.string.grinder_detail_calibrations)) {
    if (groups.isEmpty()) {
      AntText(
        stringResource(R.string.grinder_detail_calibrations_empty),
        Modifier.fillMaxWidth().testTag("grinder_calibrations_empty"),
        type = AntTextType.Secondary,
      )
    }
    groups.forEach { group ->
      Column(Modifier.fillMaxWidth().testTag("grinder_calibrations_group_${group.other.id}"), verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.xxs)) {
        AntText(group.other.displayName, type = AntTextType.Secondary, style = AntTheme.typography.textSM)
        group.pairs.forEach { pair -> PairRow(grinder, group, pair, texts, onEdit, onDelete) }
      }
    }
    if (canAdd) {
      AntButton(
        text = stringResource(R.string.grind_calibration_add),
        onClick = onAdd,
        modifier = Modifier.fillMaxWidth().testTag("grinder_calibrations_add"),
      )
    }
  }
}

@Composable
private fun PairRow(
  grinder: Grinder,
  group: CalibrationGroup,
  pair: GrindCalibration,
  texts: GrindTexts,
  onEdit: (GrindCalibration) -> Unit,
  onDelete: (GrindCalibration) -> Unit,
) {
  val other = group.other
  val text = calibrationPairText(pair, grinder, other, texts)
  Row(
    Modifier.fillMaxWidth().testTag("grinder_calibration_${pair.id}"),
    horizontalArrangement = Arrangement.spacedBy(AntTheme.spacing.xs),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.xxs)) {
      AntText(text)
      if (isOutOfScale(pair.valueOf(grinder.id), grinder) || isOutOfScale(pair.valueOf(other.id), other)) {
        AntTag(
          stringResource(R.string.grind_out_of_scale),
          Modifier.testTag("grinder_calibration_out_of_scale_${pair.id}"),
          color = AntTagColor.Warning,
        )
      }
    }
    if (group.editable) {
      AntIconButton(
        AntIcons.Edit,
        contentDescription = stringResource(R.string.grind_calibration_edit_description, text),
        onClick = { onEdit(pair) },
        modifier = Modifier.testTag("grinder_calibration_edit_${pair.id}"),
      )
    }
    AntIconButton(
      AntIcons.Delete,
      contentDescription = stringResource(R.string.grind_calibration_delete_description, text),
      onClick = { onDelete(pair) },
      modifier = Modifier.testTag("grinder_calibration_delete_${pair.id}"),
    )
  }
}
