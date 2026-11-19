package dev.josueamaya.pourline.ui.scale

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.josueamaya.pourline.R
import dev.josueamaya.pourline.brew.BrewSample
import dev.josueamaya.pourline.brew.BrewSummary
import dev.josueamaya.pourline.brew.StabilityLevel
import dev.josueamaya.pourline.brew.TargetCurve
import dev.josueamaya.pourline.brew.fromGrams
import dev.josueamaya.pourline.protocol.WeightUnit
import dev.josueamaya.pourline.ui.components.AntTagColor

/** La gráfica con su descripción accesible; con [target] añade "Objetivo: X g en mm:ss". */
@Composable
internal fun Chart(
  samples: List<BrewSample>,
  unit: WeightUnit,
  stability: String,
  modifier: Modifier = Modifier,
  target: TargetCurve? = null,
  fillHeight: Boolean = false,
) {
  val last = samples.lastOrNull { it.valid }
  val base = stringResource(
    R.string.brew_chart_description,
    formatNumber(fromGrams(last?.waterG ?: 0.0, unit), unit),
    unit.symbol,
    formatNumber(fromGrams(last?.flowGps ?: 0.0, unit), unit),
    stability,
  )
  val description = if (target == null) {
    base
  } else {
    val goal = "${formatNumber(fromGrams(target.finalWaterG, unit), unit)} ${unit.symbol}"
    "$base. ${stringResource(R.string.target_description, goal, formatDuration(target.endMs))}"
  }
  BrewChart(
    samples = samples,
    unit = unit,
    waterUnitLabel = unit.symbol,
    flowUnitLabel = stringResource(R.string.scale_flow_value, unit.symbol),
    description = description,
    modifier = modifier,
    target = target,
    fillHeight = fillHeight,
  )
}

/**
 * Solo la curva objetivo, en gramos y sin eje de flujo: el detalle de una preparación sin báscula (SPEC-007), que no tiene
 * muestras. Su descripción accesible es la del objetivo ("Objetivo: X g en mm:ss").
 */
@Composable
internal fun TargetChart(target: TargetCurve, modifier: Modifier = Modifier) {
  val unit = WeightUnit.GRAMS
  val goal = "${formatNumber(target.finalWaterG, unit)} ${unit.symbol}"
  BrewChart(
    samples = emptyList(),
    unit = unit,
    waterUnitLabel = unit.symbol,
    flowUnitLabel = null,
    description = stringResource(R.string.target_description, goal, formatDuration(target.endMs)),
    modifier = modifier,
    target = target,
  )
}

@Composable
internal fun levelLabel(level: StabilityLevel): String = stringResource(
  when (level) {
    StabilityLevel.STABLE -> R.string.brew_level_stable
    StabilityLevel.IRREGULAR -> R.string.brew_level_irregular
    StabilityLevel.TURBULENT -> R.string.brew_level_turbulent
  },
)

@Composable
internal fun levelText(level: StabilityLevel, score: Int): String =
  stringResource(R.string.brew_level_value, levelLabel(level), score)

@Composable
internal fun summaryStability(summary: BrewSummary): String {
  val score = summary.stabilityScore
  val level = summary.stabilityLevel
  return if (score != null && level != null) levelText(level, score) else stringResource(R.string.brew_summary_stability_none)
}

internal fun StabilityLevel.tagColor(): AntTagColor = when (this) {
  StabilityLevel.STABLE -> AntTagColor.Success
  StabilityLevel.IRREGULAR -> AntTagColor.Warning
  StabilityLevel.TURBULENT -> AntTagColor.Error
}
