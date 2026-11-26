package dev.josueamaya.pourline.ui.grinders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import dev.josueamaya.pourline.R
import dev.josueamaya.pourline.grinder.GrindFormat
import dev.josueamaya.pourline.grinder.GrindSetting
import dev.josueamaya.pourline.grinder.Grinder
import dev.josueamaya.pourline.ui.components.AntTag
import dev.josueamaya.pourline.ui.components.AntTagColor
import dev.josueamaya.pourline.ui.components.AntText
import dev.josueamaya.pourline.ui.theme.AntTheme

/** `true` si [value] cae fuera de la escala de [grinder] (D30). */
fun isOutOfScale(value: Double?, grinder: Grinder): Boolean =
  value != null && (value < grinder.scaleMin - dev.josueamaya.pourline.grinder.EPS || value > grinder.scaleMax + dev.josueamaya.pourline.grinder.EPS)

/**
 * "Molino · ajuste" de un ajuste guardado (§3.7). Con el molino vivo ([grinder]) se formatea con su nombre y su
 * escala actuales y, si el valor ya queda fuera de la escala, lleva el aviso "Fuera de la escala" (D30). Con el
 * molino eliminado (`grinderId == null` con ajuste) muestra la copia de texto guardada y la etiqueta "molino eliminado"; una
 * molienda solo de texto ([GrindSetting.isTextOnly]) la muestra sin etiqueta.
 * No dibuja nada sin molienda.
 */
@Composable
fun GrindSettingText(setting: GrindSetting?, grinder: Grinder?, modifier: Modifier = Modifier, testTag: String = "grind_setting_text") {
  if (setting == null || setting.isEmpty) return
  val texts = rememberGrindTexts()
  val live = grinder?.takeIf { setting.grinderId == it.id }
  val text = if (live != null) GrindFormat.label(live, setting.value, texts) else setting.label.orEmpty()
  if (text.isBlank()) return
  Row(
    modifier,
    horizontalArrangement = Arrangement.spacedBy(AntTheme.spacing.xs),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    AntText(text, Modifier.testTag(testTag))
    when {
      live == null -> if (!setting.isTextOnly) AntTag(stringResource(R.string.grinder_tag_deleted), Modifier.testTag("${testTag}_deleted"), color = AntTagColor.Warning)
      isOutOfScale(setting.value, live) ->
        AntTag(stringResource(R.string.grind_out_of_scale), Modifier.testTag("${testTag}_out_of_scale"), color = AntTagColor.Warning)
    }
  }
}
