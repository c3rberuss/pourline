package dev.dotscale.ui.scale

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import dev.dotscale.R
import dev.dotscale.brew.BrewSummary
import dev.dotscale.brew.FinishReason
import dev.dotscale.brew.StabilityLevel
import dev.dotscale.brew.fromGrams
import dev.dotscale.protocol.WeightUnit
import dev.dotscale.ui.components.AntAlert
import dev.dotscale.ui.components.AntCard
import dev.dotscale.ui.components.AntDivider
import dev.dotscale.ui.components.AntStatistic
import dev.dotscale.ui.components.AntStatus
import dev.dotscale.ui.components.AntTag
import dev.dotscale.ui.components.AntTagColor
import dev.dotscale.ui.components.AntText
import dev.dotscale.ui.components.AntTextType
import dev.dotscale.ui.theme.AntTheme
import kotlin.math.roundToInt

/** Resumen efímero de la preparación terminada. */
@Composable
fun BrewSummaryCard(summary: BrewSummary, unit: WeightUnit, modifier: Modifier = Modifier) {
  AntCard(
    modifier.fillMaxWidth().testTag("brew_summary"),
    title = stringResource(R.string.brew_summary_title),
    extra = { AntTag(stringResource(summary.reason.textRes())) },
  ) {
    if (summary.interrupted) {
      AntAlert(message = stringResource(R.string.brew_summary_interrupted), type = AntStatus.Warning)
    }
    Row(Modifier.fillMaxWidth()) {
      AntStatistic(
        value = formatDuration(summary.totalTimeMs),
        title = stringResource(R.string.brew_summary_total_time),
        modifier = Modifier.weight(1f),
      )
      AntStatistic(
        value = formatNumber(fromGrams(summary.totalWaterG, unit), unit),
        title = stringResource(R.string.brew_summary_total_water),
        suffix = unit.symbol,
        modifier = Modifier.weight(1f),
      )
    }
    Row(Modifier.fillMaxWidth()) {
      AntStatistic(
        value = formatNumber(fromGrams(summary.avgFlowGps, unit), unit),
        title = stringResource(R.string.brew_summary_avg_flow),
        suffix = stringResource(R.string.scale_flow_value, unit.symbol),
        modifier = Modifier.weight(1f),
      )
      AntStatistic(
        value = formatNumber(fromGrams(summary.maxFlowGps, unit), unit),
        title = stringResource(R.string.brew_summary_max_flow),
        suffix = stringResource(R.string.scale_flow_value, unit.symbol),
        modifier = Modifier.weight(1f),
      )
    }
    summary.finalRatio?.let { ratio ->
      AntStatistic(
        value = stringResource(R.string.scale_ratio_value, ratio),
        title = stringResource(R.string.scale_ratio),
        valueColor = AntTheme.colors.primary.base,
      )
    }
    AntDivider()
    StabilitySection(summary)
    if (summary.pours.isNotEmpty()) {
      AntDivider()
      AntText(stringResource(R.string.brew_summary_pours), type = AntTextType.Secondary)
      summary.pours.forEach { pour ->
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
          AntText(stringResource(R.string.brew_summary_pour, pour.index), Modifier.weight(1f))
          AntText(
            text = stringResource(
              R.string.brew_summary_pour_detail,
              "${formatNumber(fromGrams(pour.waterG, unit), unit)} ${unit.symbol}",
              formatDuration(pour.endMs - pour.startMs),
            ),
            type = AntTextType.Secondary,
          )
        }
      }
    }
  }
}

@Composable
private fun StabilitySection(summary: BrewSummary) {
  val score = summary.stabilityScore
  val level = summary.stabilityLevel
  AntText(stringResource(R.string.brew_summary_stability), type = AntTextType.Secondary)
  if (score == null || level == null) {
    AntText(stringResource(R.string.brew_summary_stability_none))
    return
  }
  Row(horizontalArrangement = Arrangement.spacedBy(AntTheme.spacing.xs), verticalAlignment = Alignment.CenterVertically) {
    AntStatistic(value = score.toString())
    AntTag(levelLabel(level), color = level.tagColor())
  }
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AntTheme.spacing.xs)) {
    StabilityLevel.entries.forEach { entry ->
      val percent = ((summary.levelShare[entry] ?: 0.0) * 100).roundToInt()
      AntTag(
        text = stringResource(R.string.brew_summary_share, levelLabel(entry), percent),
        color = if (percent > 0) entry.tagColor() else AntTagColor.Default,
      )
    }
  }
}

private fun FinishReason.textRes(): Int = when (this) {
  FinishReason.INACTIVITY -> R.string.brew_reason_inactivity
  FinishReason.TIMER_PAUSED -> R.string.brew_reason_timer_paused
  FinishReason.TIMER_RESET -> R.string.brew_reason_timer_reset
  FinishReason.MANUAL -> R.string.brew_reason_manual
  FinishReason.DISCONNECTED -> R.string.brew_reason_disconnected
  FinishReason.MAX_DURATION -> R.string.brew_reason_max_duration
}
