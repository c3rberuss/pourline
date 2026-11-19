package dev.josueamaya.pourline.ui.scale

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import dev.josueamaya.pourline.R
import dev.josueamaya.pourline.brew.BrewSummary
import dev.josueamaya.pourline.brew.FinishReason
import dev.josueamaya.pourline.brew.StabilityLevel
import dev.josueamaya.pourline.brew.fromGrams
import dev.josueamaya.pourline.protocol.WeightUnit
import dev.josueamaya.pourline.session.SavedBrew
import dev.josueamaya.pourline.ui.components.AntAlert
import dev.josueamaya.pourline.ui.components.AntButton
import dev.josueamaya.pourline.ui.components.AntCard
import dev.josueamaya.pourline.ui.components.AntDivider
import dev.josueamaya.pourline.ui.components.AntIcons
import dev.josueamaya.pourline.ui.components.AntStatistic
import dev.josueamaya.pourline.ui.components.AntStatus
import dev.josueamaya.pourline.ui.components.AntTag
import dev.josueamaya.pourline.ui.components.AntTagColor
import dev.josueamaya.pourline.ui.components.AntText
import dev.josueamaya.pourline.ui.components.AntTextType
import dev.josueamaya.pourline.ui.theme.AntTheme
import kotlin.math.roundToInt

/**
 * Resumen de una preparación terminada. En Scale, [savedBrew] controla el botón "Añadir detalles": deshabilitado
 * mientras guarda, habilitado al guardar y oculto si falla o no hay guardado (el detalle del historial). Con [fit]
 * añade el bloque "Ajuste al objetivo". Con [onShare] añade el botón "Compartir" (imagen del resumen), junto a "Añadir
 * detalles" o, si ese no se muestra, a todo el ancho.
 */
@Composable
fun BrewSummaryCard(
  summary: BrewSummary,
  unit: WeightUnit,
  modifier: Modifier = Modifier,
  savedBrew: SavedBrew? = null,
  fit: FitView? = null,
  onAddDetails: (Long) -> Unit = {},
  onShare: (() -> Unit)? = null,
) {
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
    if (fit != null) {
      AntDivider()
      FitBlock(fit, unit)
    }
    val canAddDetails = savedBrew == SavedBrew.Saving || savedBrew is SavedBrew.Saved
    if (canAddDetails || onShare != null) {
      AntDivider()
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AntTheme.spacing.xs)) {
        if (canAddDetails) {
          AntButton(
            text = stringResource(R.string.brew_summary_add_details),
            onClick = { (savedBrew as? SavedBrew.Saved)?.let { onAddDetails(it.id) } },
            modifier = Modifier.weight(1f).testTag("brew_add_details"),
            enabled = savedBrew is SavedBrew.Saved,
          )
        }
        if (onShare != null) {
          AntButton(
            text = stringResource(R.string.share_action),
            onClick = onShare,
            modifier = Modifier.weight(1f).testTag("brew_summary_share_image"),
            icon = AntIcons.Share,
          )
        }
      }
    }
  }
}

/** La estabilidad de la preparación: la puntuación, el nivel y el reparto del tiempo por nivel. */
@Composable
internal fun StabilitySection(summary: BrewSummary) {
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

internal fun FinishReason.textRes(): Int = when (this) {
  FinishReason.INACTIVITY -> R.string.brew_reason_inactivity
  FinishReason.TIMER_PAUSED -> R.string.brew_reason_timer_paused
  FinishReason.TIMER_RESET -> R.string.brew_reason_timer_reset
  FinishReason.MANUAL -> R.string.brew_reason_manual
  FinishReason.DISCONNECTED -> R.string.brew_reason_disconnected
  FinishReason.MAX_DURATION -> R.string.brew_reason_max_duration
}
