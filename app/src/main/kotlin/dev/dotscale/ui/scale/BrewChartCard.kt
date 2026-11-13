package dev.dotscale.ui.scale

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.dotscale.R
import dev.dotscale.brew.BrewConfig
import dev.dotscale.brew.BrewPhase
import dev.dotscale.brew.BrewSample
import dev.dotscale.brew.BrewState
import dev.dotscale.brew.BrewSummary
import dev.dotscale.brew.FinishReason
import dev.dotscale.brew.Pour
import dev.dotscale.brew.StabilityLevel
import dev.dotscale.brew.fromGrams
import dev.dotscale.protocol.WeightUnit
import dev.dotscale.ui.components.AntAlert
import dev.dotscale.ui.components.AntButton
import dev.dotscale.ui.components.AntCard
import dev.dotscale.ui.components.AntStatus
import dev.dotscale.ui.components.AntTag
import dev.dotscale.ui.components.AntTagColor
import dev.dotscale.ui.components.AntText
import dev.dotscale.ui.components.AntTextType
import dev.dotscale.ui.theme.AntSize
import dev.dotscale.ui.theme.AntTheme
import dev.dotscale.ui.theme.DotScaleTheme
import kotlin.math.sin

private val Levels = BrewConfig()

/** Tarjeta de la gráfica según la fase de la sesión: armada, activa o terminada. */
@Composable
fun BrewChartCard(brew: BrewState, unit: WeightUnit, onFinish: () -> Unit, modifier: Modifier = Modifier) {
  AntCard(modifier.fillMaxWidth(), title = stringResource(R.string.brew_chart_title)) {
    when (val phase = brew.phase) {
      BrewPhase.Armed -> AntText(
        text = stringResource(R.string.brew_chart_empty),
        modifier = Modifier.fillMaxWidth().padding(vertical = AntTheme.spacing.lg),
        type = AntTextType.Secondary,
        textAlign = TextAlign.Center,
      )
      is BrewPhase.Active -> {
        val indicator = activeIndicator(brew, phase)
        Chart(brew.samples, unit, indicator.text)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
          AntTag(indicator.text, Modifier.testTag("brew_level"), color = indicator.color)
          Spacer(Modifier.weight(1f))
          AntButton(
            text = stringResource(R.string.brew_chart_finish),
            onClick = onFinish,
            modifier = Modifier.testTag("brew_finish"),
            size = AntSize.Small,
          )
        }
      }
      is BrewPhase.Finished -> {
        val summary = phase.summary
        Chart(brew.samples, unit, summaryStability(summary))
        if (phase.rearmed) AntText(stringResource(R.string.brew_chart_ready), type = AntTextType.Secondary)
        if (phase.pourIgnored) AntAlert(message = stringResource(R.string.brew_chart_ignored), type = AntStatus.Info)
      }
    }
  }
}

@Composable
private fun Chart(samples: List<BrewSample>, unit: WeightUnit, stability: String) {
  val last = samples.lastOrNull { it.valid }
  val description = stringResource(
    R.string.brew_chart_description,
    formatNumber(fromGrams(last?.waterG ?: 0.0, unit), unit),
    unit.symbol,
    formatNumber(fromGrams(last?.flowGps ?: 0.0, unit), unit),
    stability,
  )
  BrewChart(
    samples = samples,
    unit = unit,
    waterUnitLabel = unit.symbol,
    flowUnitLabel = stringResource(R.string.scale_flow_value, unit.symbol),
    description = description,
  )
}

private class Indicator(val text: String, val color: AntTagColor)

@Composable
private fun activeIndicator(brew: BrewState, phase: BrewPhase.Active): Indicator {
  val score = brew.currentStability
  val level = brew.currentLevel
  return when {
    !phase.pouring -> Indicator(stringResource(R.string.brew_level_idle), AntTagColor.Default)
    score == null || level == null -> Indicator(stringResource(R.string.brew_level_measuring), AntTagColor.Default)
    else -> Indicator(levelText(level, score), level.tagColor())
  }
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
private fun summaryStability(summary: BrewSummary): String {
  val score = summary.stabilityScore
  val level = summary.stabilityLevel
  return if (score != null && level != null) levelText(level, score) else stringResource(R.string.brew_summary_stability_none)
}

internal fun StabilityLevel.tagColor(): AntTagColor = when (this) {
  StabilityLevel.STABLE -> AntTagColor.Success
  StabilityLevel.IRREGULAR -> AntTagColor.Warning
  StabilityLevel.TURBULENT -> AntTagColor.Error
}

// --- Previews ---

/** Un vertido de ejemplo: bloom, pausa y vertido principal, con la estabilidad que correspondería. */
private fun previewSamples(): List<BrewSample> {
  var water = 0.0
  return List(600) { i ->
    val flow = when {
      i < 90 -> 5.0
      i < 290 -> 0.0
      else -> 4.0 + 1.2 * sin(i / 3.0)
    }
    water += flow * 0.1
    val stability = when {
      i in 10..89 -> 100
      i in 300..420 -> 72
      i > 420 -> 40
      else -> null
    }
    BrewSample(i * 100L, water, flow, stability, valid = true)
  }
}

private val PreviewSummary = BrewSummary(
  totalTimeMs = 59_900,
  totalWaterG = water(previewSamples()),
  avgFlowGps = 4.3,
  maxFlowGps = 5.2,
  stabilityScore = 68,
  stabilityLevel = StabilityLevel.IRREGULAR,
  levelShare = mapOf(StabilityLevel.STABLE to 0.4, StabilityLevel.IRREGULAR to 0.35, StabilityLevel.TURBULENT to 0.25),
  pours = listOf(Pour(1, 0, 8_900, 45.0), Pour(2, 29_000, 59_900, 205.0)),
  finalRatio = 16.7,
  reason = FinishReason.MANUAL,
  interrupted = false,
)

private fun water(samples: List<BrewSample>) = samples.last().waterG

internal val PreviewFinishedState = BrewState(
  BrewPhase.Finished(PreviewSummary, rearmed = false, pourIgnored = true),
  previewSamples(),
  currentStability = null,
  currentLevel = null,
)

@Composable
private fun BrewChartPreviews() {
  Column(Modifier.padding(AntTheme.spacing.base), verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.sm)) {
    BrewChartCard(BrewState.Initial, WeightUnit.GRAMS, onFinish = {})
    BrewChartCard(
      BrewState(BrewPhase.Active(pouring = true), previewSamples().take(450), currentStability = 72, currentLevel = StabilityLevel.IRREGULAR),
      WeightUnit.GRAMS,
      onFinish = {},
    )
    BrewChartCard(PreviewFinishedState, WeightUnit.GRAMS, onFinish = {})
    BrewSummaryCard(PreviewSummary, WeightUnit.GRAMS)
  }
}

@Preview(name = "Gráfica · Claro", showBackground = true, heightDp = 1500)
@Composable
private fun BrewChartLightPreview() {
  DotScaleTheme(darkTheme = false) { BrewChartPreviews() }
}

@Preview(
  name = "Gráfica · Oscuro",
  showBackground = true,
  backgroundColor = 0xFF000000,
  heightDp = 1500,
  uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun BrewChartDarkPreview() {
  DotScaleTheme(darkTheme = true) { BrewChartPreviews() }
}
