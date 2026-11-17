package dev.josueamaya.pourline.share

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.josueamaya.pourline.R
import dev.josueamaya.pourline.brew.BrewSummary
import dev.josueamaya.pourline.brew.StabilityLevel
import dev.josueamaya.pourline.brew.fromGrams
import dev.josueamaya.pourline.protocol.WeightUnit
import dev.josueamaya.pourline.ui.components.AntRate
import dev.josueamaya.pourline.ui.components.AntTag
import dev.josueamaya.pourline.ui.components.AntText
import dev.josueamaya.pourline.ui.components.AntTextType
import dev.josueamaya.pourline.ui.scale.Chart
import dev.josueamaya.pourline.ui.scale.formatDuration
import dev.josueamaya.pourline.ui.scale.formatNumber
import dev.josueamaya.pourline.ui.scale.summaryStability
import dev.josueamaya.pourline.ui.theme.AntColors
import dev.josueamaya.pourline.ui.theme.AntTheme

internal const val SHARE_NOTES_MAX_LINES = 3
private val RateStarSize = 14.dp
private val QuoteBarWidth = 2.dp
private val QuoteIndent = 10.dp

/**
 * El resumen en Scale (sin nota) y el detalle de una preparación (con nota): título con el ajuste al objetivo,
 * subtítulo con la valoración, la gráfica en el alto que sobra, cuatro métricas, la nota de cata y el pie del café.
 */
@Composable
fun BrewShareCard(data: BrewShareData, modifier: Modifier = Modifier) {
  ShareFrame(modifier, data.footer) {
    BrewShareHeader(data)
    Spacer(Modifier.height(BODY_GAP))
    ShareChartSlot(Modifier.weight(1f)) {
      if (data.curveError) {
        CurveError()
      } else {
        Chart(data.samples, data.unit, summaryStability(data.summary), target = data.target, fillHeight = true)
      }
    }
    Spacer(Modifier.height(BODY_GAP))
    ShareMetrics(data.summary, data.unit)
    data.notes?.let { notes ->
      Spacer(Modifier.height(BODY_GAP))
      ShareQuote(notes)
    }
  }
}

internal val BODY_GAP = 12.dp

/** El hueco de la gráfica: lleva `share_chart` para medirlo y ocupa el alto que dejan los demás elementos. */
@Composable
internal fun ShareChartSlot(modifier: Modifier, content: @Composable () -> Unit) {
  Box(modifier.fillMaxWidth().testTag("share_chart"), contentAlignment = Alignment.Center) { content() }
}

@Composable
internal fun CurveError() {
  AntText(
    stringResource(R.string.brew_detail_curve_error),
    Modifier.testTag("share_curve_error"),
    type = AntTextType.Secondary,
    textAlign = TextAlign.Center,
  )
}

@Composable
private fun BrewShareHeader(data: BrewShareData) {
  Column(Modifier.fillMaxWidth()) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AntTheme.spacing.xs), verticalAlignment = Alignment.CenterVertically) {
      AntText(
        data.title,
        Modifier.weight(1f).testTag("share_title"),
        style = AntTheme.typography.heading4,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      data.fitScore?.let { AntTag(stringResource(R.string.history_row_fit, it), Modifier.testTag("history_row_fit")) }
    }
    if (data.subtitle != null || data.rating != null) {
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AntTheme.spacing.xs), verticalAlignment = Alignment.CenterVertically) {
        if (data.subtitle != null) {
          AntText(data.subtitle, Modifier.weight(1f).testTag("share_subtitle"), type = AntTextType.Secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        } else {
          Box(Modifier.weight(1f))
        }
        data.rating?.let { AntRate(it, onValueChange = null, starSize = RateStarSize, testTag = "share_rating") }
      }
    }
  }
}

/** Tiempo, agua, ratio y estabilidad (con el color de su nivel). */
@Composable
private fun ShareMetrics(summary: BrewSummary, unit: WeightUnit) {
  val none = stringResource(R.string.compare_none)
  Row(Modifier.fillMaxWidth().testTag("share_metrics"), horizontalArrangement = Arrangement.spacedBy(AntTheme.spacing.xs)) {
    ShareMetric(stringResource(R.string.brew_summary_total_time), formatDuration(summary.totalTimeMs), Modifier.weight(1f))
    ShareMetric(
      stringResource(R.string.brew_summary_total_water),
      "${formatNumber(fromGrams(summary.totalWaterG, unit), unit)} ${unit.symbol}",
      Modifier.weight(1f),
    )
    ShareMetric(stringResource(R.string.scale_ratio), summary.finalRatio?.let { stringResource(R.string.scale_ratio_value, it) } ?: none, Modifier.weight(1f))
    ShareMetric(
      stringResource(R.string.brew_summary_stability),
      summary.stabilityScore?.toString() ?: none,
      Modifier.weight(1f),
      color = summary.stabilityLevel?.let { levelTextColor(AntTheme.colors, it) } ?: AntTheme.colors.text,
    )
  }
}

private fun levelTextColor(colors: AntColors, level: StabilityLevel): Color = when (level) {
  StabilityLevel.STABLE -> colors.success.text
  StabilityLevel.IRREGULAR -> colors.warning.text
  StabilityLevel.TURBULENT -> colors.error.text
}

@Composable
private fun ShareMetric(label: String, value: String, modifier: Modifier, color: Color = AntTheme.colors.text) {
  Column(modifier) {
    AntText(label, type = AntTextType.Secondary, style = AntTheme.typography.textSM, maxLines = 1, overflow = TextOverflow.Ellipsis)
    androidx.compose.material3.Text(
      value,
      color = color,
      style = AntTheme.typography.textLG.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

/** La nota de cata como cita: barra de 2 dp a la izquierda, 10 dp de sangría y 3 líneas como máximo. */
@Composable
private fun ShareQuote(notes: String) {
  Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
    Box(Modifier.width(QuoteBarWidth).fillMaxHeight().background(AntTheme.colors.border))
    Spacer(Modifier.width(QuoteIndent))
    AntText(notes, Modifier.weight(1f).testTag("share_notes"), maxLines = SHARE_NOTES_MAX_LINES, overflow = TextOverflow.Ellipsis)
  }
}
