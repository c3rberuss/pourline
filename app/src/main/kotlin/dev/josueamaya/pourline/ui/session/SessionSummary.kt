package dev.josueamaya.pourline.ui.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import dev.josueamaya.pourline.R
import dev.josueamaya.pourline.brew.BrewPhase
import dev.josueamaya.pourline.brew.BrewSummary
import dev.josueamaya.pourline.brew.fromGrams
import dev.josueamaya.pourline.brew.toGrams
import dev.josueamaya.pourline.history.BrewRecord
import dev.josueamaya.pourline.session.SavedBrew
import dev.josueamaya.pourline.session.ScaleUiState
import dev.josueamaya.pourline.settings.ExpertFeature
import dev.josueamaya.pourline.settings.ExperienceMode
import dev.josueamaya.pourline.ui.components.AntAlert
import dev.josueamaya.pourline.ui.components.AntButton
import dev.josueamaya.pourline.ui.components.AntButtonType
import dev.josueamaya.pourline.ui.components.AntCard
import dev.josueamaya.pourline.ui.components.AntDivider
import dev.josueamaya.pourline.ui.components.AntIcons
import dev.josueamaya.pourline.ui.components.AntRate
import dev.josueamaya.pourline.ui.components.AntStatistic
import dev.josueamaya.pourline.ui.components.AntStatus
import dev.josueamaya.pourline.ui.components.AntTag
import dev.josueamaya.pourline.ui.components.AntText
import dev.josueamaya.pourline.ui.components.AntTextType
import dev.josueamaya.pourline.ui.scale.FitBlock
import dev.josueamaya.pourline.ui.scale.StabilitySection
import dev.josueamaya.pourline.ui.scale.fitView
import dev.josueamaya.pourline.ui.scale.formatDuration
import dev.josueamaya.pourline.ui.scale.formatNumber
import dev.josueamaya.pourline.ui.scale.textRes
import dev.josueamaya.pourline.ui.theme.AntSize
import dev.josueamaya.pourline.ui.theme.AntTheme

/**
 * El resumen de la sesión (D8), que sustituye al dial y a la lista en `Finished`. Básico: tiempo total, agua, dosis,
 * ratio, café y, tras "Añadir detalles", la valoración y las notas de la preparación guardada ([record]). Experto añade
 * el flujo medio y máximo, la estabilidad, el ajuste y el motivo de fin.
 */
@Composable
internal fun SessionSummary(
  ui: ScaleUiState,
  summary: BrewSummary,
  record: BrewRecord?,
  mode: ExperienceMode,
  onAddDetails: (Long) -> Unit,
  onShare: () -> Unit,
  onDone: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val unit = ui.unit
  val grams = { value: Double -> "${formatNumber(fromGrams(value, unit), unit)} ${unit.symbol}" }
  val doseG = ui.dose?.let { toGrams(it, unit) }
  val ratio = summary.finalRatio?.let { stringResource(R.string.scale_ratio_value, it) }
  val coffee = ui.lastBrew?.coffee?.name ?: ui.coffee?.name
  val saved = ui.savedBrew

  Column(
    modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(AntTheme.spacing.base)
      .testTag("session_summary"),
    verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.sm),
  ) {
    SaveBanner(saved)
    if (summary.interrupted) AntAlert(message = stringResource(R.string.brew_summary_interrupted), type = AntStatus.Warning)
    AntCard(Modifier.fillMaxWidth()) {
      Row(Modifier.fillMaxWidth()) {
        AntStatistic(
          value = formatDuration(summary.totalTimeMs),
          title = stringResource(R.string.brew_summary_total_time),
          modifier = Modifier.weight(1f).testTag("summary_time"),
        )
        AntStatistic(
          value = formatNumber(fromGrams(summary.totalWaterG, unit), unit),
          title = stringResource(R.string.brew_summary_total_water),
          suffix = unit.symbol,
          modifier = Modifier.weight(1f).testTag("summary_water"),
        )
        AntStatistic(
          value = doseG?.let { formatNumber(fromGrams(it, unit), unit) } ?: stringResource(R.string.scale_ratio_empty),
          title = stringResource(R.string.summary_dose),
          suffix = doseG?.let { unit.symbol },
          modifier = Modifier.weight(1f).testTag("summary_dose"),
        )
      }
      val line = when {
        ratio != null && coffee != null -> stringResource(R.string.summary_ratio_coffee, ratio, coffee)
        ratio != null -> stringResource(R.string.summary_ratio, ratio)
        coffee != null -> coffee
        else -> null
      }
      line?.let { AntText(it, Modifier.testTag("summary_ratio"), type = AntTextType.Secondary) }
      val entity = record?.entity
      entity?.rating?.let { rating ->
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
          AntText(stringResource(R.string.brew_detail_rating), Modifier.weight(1f), type = AntTextType.Secondary)
          AntRate(rating, onValueChange = null, testTag = "summary_rating")
        }
      }
      entity?.notes?.let { notes ->
        Column(verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.xxs)) {
          AntText(stringResource(R.string.brew_detail_notes), type = AntTextType.Secondary)
          AntText(notes, Modifier.testTag("summary_notes"))
        }
      }
    }
    ExpertSummary(ui, summary, mode)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AntTheme.spacing.xs)) {
      val canAddDetails = saved == SavedBrew.Saving || saved is SavedBrew.Saved
      if (canAddDetails) {
        AntButton(
          text = stringResource(R.string.summary_details),
          onClick = { (saved as? SavedBrew.Saved)?.let { onAddDetails(it.id) } },
          modifier = Modifier.weight(1f).testTag("summary_details"),
          enabled = saved is SavedBrew.Saved,
        )
      }
      AntButton(
        text = stringResource(R.string.summary_share),
        onClick = onShare,
        modifier = Modifier.weight(1f).testTag("summary_share"),
        icon = AntIcons.Share,
      )
    }
    AntButton(
      text = stringResource(R.string.summary_done),
      onClick = onDone,
      modifier = Modifier.fillMaxWidth().testTag("summary_done"),
      type = AntButtonType.Primary,
      size = AntSize.Large,
    )
  }
}

/** Lo que solo se ve en Experto: flujo medio y máximo, estabilidad, ajuste al objetivo y motivo de fin (§3.2). */
@Composable
private fun ExpertSummary(ui: ScaleUiState, summary: BrewSummary, mode: ExperienceMode) {
  val unit = ui.unit
  val showFlow = mode.shows(ExpertFeature.SUMMARY_FLOW)
  val showStability = mode.shows(ExpertFeature.SUMMARY_STABILITY)
  val fit = ui.lastFit?.takeIf { mode.shows(ExpertFeature.SUMMARY_FIT) }?.let {
    fitView(it.fit.score, it.name, it.fit.targetWaterG, it.fit.targetEndMs, summary.totalWaterG, summary.totalTimeMs)
  }
  val showReason = mode.shows(ExpertFeature.SUMMARY_FINISH_REASON)
  if (!showFlow && !showStability && fit == null && !showReason) return
  AntCard(Modifier.fillMaxWidth().testTag("summary_expert")) {
    if (showFlow) {
      Row(Modifier.fillMaxWidth()) {
        AntStatistic(
          value = formatNumber(fromGrams(summary.avgFlowGps, unit), unit),
          title = stringResource(R.string.brew_summary_avg_flow),
          suffix = stringResource(R.string.scale_flow_value, unit.symbol),
          modifier = Modifier.weight(1f).testTag("summary_avg_flow"),
        )
        AntStatistic(
          value = formatNumber(fromGrams(summary.maxFlowGps, unit), unit),
          title = stringResource(R.string.brew_summary_max_flow),
          suffix = stringResource(R.string.scale_flow_value, unit.symbol),
          modifier = Modifier.weight(1f).testTag("summary_max_flow"),
        )
      }
    }
    if (showStability) {
      if (showFlow) AntDivider()
      Column(Modifier.testTag("summary_stability"), verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.xxs)) {
        StabilitySection(summary)
      }
    }
    if (fit != null) {
      AntDivider()
      FitBlock(fit, unit)
    }
    if (showReason) {
      AntDivider()
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        AntText(stringResource(R.string.summary_reason), Modifier.weight(1f), type = AntTextType.Secondary)
        AntTag(stringResource(summary.reason.textRes()), Modifier.testTag("summary_reason"))
      }
    }
  }
}

@Composable
private fun SaveBanner(saved: SavedBrew?) {
  when (saved) {
    SavedBrew.Saving -> AntAlert(message = stringResource(R.string.summary_saving), modifier = Modifier.testTag("summary_save_state"))
    is SavedBrew.Saved ->
      AntAlert(message = stringResource(R.string.summary_saved), modifier = Modifier.testTag("summary_save_state"), type = AntStatus.Success)
    SavedBrew.Failed ->
      AntAlert(message = stringResource(R.string.summary_failed), modifier = Modifier.testTag("summary_save_state"), type = AntStatus.Error)
    null -> Unit
  }
}

/** El resumen solo existe en `Finished`: lo que da la fase. */
internal fun BrewPhase.finishedSummary(): BrewSummary? = (this as? BrewPhase.Finished)?.summary
