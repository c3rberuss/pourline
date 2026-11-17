package dev.josueamaya.pourline.ui.recipes

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import dev.josueamaya.pourline.R
import java.util.Locale

/** Gramos sin decimales si es entero y con uno si no (250, 16,4). */
internal fun formatGrams(value: Double): String =
  if (value % 1.0 == 0.0) value.toLong().toString() else String.format(Locale.getDefault(), "%.1f", value)

/** El ratio con un decimal, sin el "1:" (16,7). */
internal fun formatRatioNumber(value: Double): String = String.format(Locale.getDefault(), "%.1f", value)

/** "15 g · 250 g · 1:16,7 · 3 etapas". */
@Composable
internal fun recipeSummary(doseG: Double, totalWaterG: Double, stageCount: Int): String = stringResource(
  R.string.recipe_summary,
  formatGrams(doseG),
  formatGrams(totalWaterG),
  stringResource(R.string.scale_ratio_value, if (doseG > 0) totalWaterG / doseG else 0.0),
  pluralStringResource(R.plurals.recipe_stage_count, stageCount, stageCount),
)
