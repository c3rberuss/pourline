package dev.josueamaya.pourline.ui.grinders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import dev.josueamaya.pourline.R
import dev.josueamaya.pourline.grinder.GrindCalibration
import dev.josueamaya.pourline.grinder.GrindFormat
import dev.josueamaya.pourline.grinder.GrindTexts
import dev.josueamaya.pourline.grinder.Grinder
import dev.josueamaya.pourline.ui.components.AntAlert
import dev.josueamaya.pourline.ui.components.AntButton
import dev.josueamaya.pourline.ui.components.AntButtonType
import dev.josueamaya.pourline.ui.components.AntDrawer
import dev.josueamaya.pourline.ui.components.AntFormItem
import dev.josueamaya.pourline.ui.components.AntSelect
import dev.josueamaya.pourline.ui.components.AntSelectOption
import dev.josueamaya.pourline.ui.components.AntStatus
import dev.josueamaya.pourline.ui.components.AntText
import dev.josueamaya.pourline.ui.theme.AntTheme

/**
 * El texto de un par (D55): "Chestnut S3 20 clics = A2 1 · 20", con el modelo y el ajuste de cada lado. [first] va
 * a la izquierda; el par puede tener guardados los molinos en el otro orden.
 */
@Composable
fun calibrationPairText(pair: GrindCalibration, first: Grinder, second: Grinder, texts: GrindTexts): String =
  stringResource(
    R.string.grind_calibration_pair,
    first.model,
    GrindFormat.format(pair.valueOf(first.id), first, texts),
    second.model,
    GrindFormat.format(pair.valueOf(second.id), second, texts),
  )

/**
 * La hoja para guardar un par de calibración (D53), la misma en el convertidor y en el detalle del molino. Con
 * [CalibrationDraft.lockOther] el otro molino viene dado; si además no se está editando, el ajuste de [grinder] también
 * (es el del convertidor) y se muestra como texto. Si no, los dos ajustes se introducen. Un conflicto o un par inválido
 * se explican dentro de la hoja, que sigue abierta.
 *
 * @param others los molinos con los que se puede emparejar [grinder]; [CalibrationDraft.otherId] está entre ellos.
 */
@Composable
fun CalibrationSheet(
  draft: CalibrationDraft,
  grinder: Grinder,
  others: List<Grinder>,
  texts: GrindTexts,
  onChange: ((CalibrationDraft) -> CalibrationDraft) -> Unit,
  onSave: () -> Unit,
  onReplace: () -> Unit,
  onDismiss: () -> Unit,
) {
  val other = others.firstOrNull { it.id == draft.otherId }
  val valueFixed = draft.lockOther && draft.editingId == null
  AntDrawer(title = stringResource(R.string.grind_calibration_sheet_title), onClose = onDismiss, modifier = Modifier.testTag("calibration_sheet")) {
    Column(
      Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.sm),
    ) {
      if (!draft.lockOther) {
        AntSelect(
          options = others.map { AntSelectOption(it.id, it.displayName) },
          selected = draft.otherId,
          onSelect = { id -> onChange { it.copy(otherId = id, otherValue = null) } },
          label = stringResource(R.string.grind_calibration_other),
          modifier = Modifier.fillMaxWidth(),
          testTag = "calibration_grinder",
        )
      }
      if (valueFixed) {
        AntText(
          grinder.model + ": " + GrindFormat.format(draft.value ?: 0.0, grinder, texts),
          Modifier.fillMaxWidth().testTag("calibration_source"),
          strong = true,
        )
      } else {
        AntFormItem(label = grinder.displayName) {
          Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            GrindInput(
              value = draft.value,
              grinder = grinder,
              onChange = { v -> onChange { it.copy(value = v) } },
              start = startValue(grinder, null),
              testTag = "calibration_value",
            )
          }
        }
      }
      if (other != null) {
        AntFormItem(label = other.displayName) {
          Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            GrindInput(
              value = draft.otherValue,
              grinder = other,
              onChange = { v -> onChange { it.copy(otherValue = v) } },
              start = startValue(other, null),
              testTag = "calibration_other_value",
            )
          }
        }
      }
      if (draft.conflicts.isNotEmpty() && other != null) {
        val list = draft.conflicts.map { calibrationPairText(it, grinder, other, texts) }.joinToString(", ")
        AntAlert(
          message = pluralStringResource(R.plurals.grind_calibration_conflict, draft.conflicts.size, list),
          modifier = Modifier.fillMaxWidth().testTag("calibration_conflict"),
          type = AntStatus.Error,
          action = {
            AntButton(
              text = stringResource(R.string.grind_calibration_replace),
              onClick = onReplace,
              modifier = Modifier.testTag("calibration_replace"),
              type = AntButtonType.Link,
            )
          },
        )
      }
      if (draft.invalid) {
        AntAlert(
          message = stringResource(R.string.grind_calibration_invalid),
          modifier = Modifier.fillMaxWidth().testTag("calibration_invalid"),
          type = AntStatus.Error,
        )
      }
      AntButton(
        text = stringResource(R.string.grind_calibration_save),
        onClick = onSave,
        modifier = Modifier.fillMaxWidth().testTag("calibration_save"),
        type = AntButtonType.Primary,
        enabled = draft.complete,
      )
    }
  }
}
