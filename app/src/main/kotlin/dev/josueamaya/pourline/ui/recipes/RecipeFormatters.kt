package dev.josueamaya.pourline.ui.recipes

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import dev.josueamaya.pourline.R
import dev.josueamaya.pourline.recipe.share.TrimmedPart
import java.util.Locale

/** Gramos sin decimales si es entero y con uno si no (250, 16,4). */
internal fun formatGrams(value: Double): String =
  if (value % 1.0 == 0.0) value.toLong().toString() else String.format(Locale.getDefault(), "%.1f", value)

/** El ratio con un decimal, sin el "1:" (16,7). */
internal fun formatRatioNumber(value: Double): String = String.format(Locale.getDefault(), "%.1f", value)

/** "15 g · 250 g · 1:16,7 · 5 pasos · 1:55": [time] es la duración total en `m:ss`. */
@Composable
internal fun recipeSummary(doseG: Double, totalWaterG: Double, stageCount: Int, time: String): String = stringResource(
  R.string.recipe_summary,
  formatGrams(doseG),
  formatGrams(totalWaterG),
  stringResource(R.string.scale_ratio_value, if (doseG > 0) totalWaterG / doseG else 0.0),
  pluralStringResource(R.plurals.recipe_stage_count, stageCount, stageCount),
  time,
)

/**
 * Los textos de lo recortado, siempre en el orden notas, instrucciones y nombres. Con [partialInstructions] (se quitaron
 * algunas instrucciones, no todas), las instrucciones son "las instrucciones de algunos pasos" (enmienda 1 de SPEC-009).
 */
@StringRes
internal fun trimmedPartsRes(parts: Set<TrimmedPart>, partialInstructions: Boolean): List<Int> =
  TrimmedPart.entries.filter { it in parts }.map {
    when (it) {
      TrimmedPart.NOTES -> R.string.recipe_trimmed_notes
      TrimmedPart.INSTRUCTIONS -> if (partialInstructions) R.string.recipe_trimmed_some_instructions else R.string.recipe_trimmed_instructions
      TrimmedPart.STAGE_NAMES -> R.string.recipe_trimmed_stage_names
    }
  }

/** "las notas", "las notas y los nombres de los pasos"…: lo recortado según [trimmedPartsRes]. */
@Composable
internal fun trimmedPartsText(parts: Set<TrimmedPart>, partialInstructions: Boolean = false): String {
  val names = trimmedPartsRes(parts, partialInstructions).map { stringResource(it) }
  return when (names.size) {
    0 -> ""
    1 -> names[0]
    2 -> stringResource(R.string.recipe_trimmed_list_two, names[0], names[1])
    else -> stringResource(R.string.recipe_trimmed_list_three, names[0], names[1], names[2])
  }
}
