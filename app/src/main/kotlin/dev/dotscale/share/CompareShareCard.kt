package dev.dotscale.share

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.dotscale.R
import dev.dotscale.history.BrewRecord
import dev.dotscale.ui.components.AntText
import dev.dotscale.ui.components.AntTextType
import dev.dotscale.ui.history.CompareChart
import dev.dotscale.ui.history.CompareKind
import dev.dotscale.ui.history.CompareMetric
import dev.dotscale.ui.history.CompareRow
import dev.dotscale.ui.history.CompareSeries
import dev.dotscale.ui.history.compareColors
import dev.dotscale.ui.history.compareRows
import dev.dotscale.ui.history.formatDiff
import dev.dotscale.ui.history.formatValue
import dev.dotscale.ui.theme.AntTheme

private val DotSize = 12.dp
private val HeaderRowHeight = 20.dp
private val TableRowHeight = 22.dp
private val LabelWidth = 72.dp

/** Las cuatro métricas que caben en la imagen; las demás siguen en la pantalla. */
private val SHARED_METRICS = setOf(CompareMetric.TOTAL_TIME, CompareMetric.TOTAL_WATER, CompareMetric.RATIO, CompareMetric.STABILITY)

/**
 * La comparación de A y B: leyenda de dos líneas, la gráfica de agua en el alto que sobra, la tabla de cuatro
 * métricas y el pie con los cafés. Las series con curva ilegible se omiten; si lo son las dos, el hueco de la gráfica
 * lleva el texto de error.
 */
@Composable
fun CompareShareCard(data: CompareShareData, modifier: Modifier = Modifier) {
  val colors = compareColors(AntTheme.colors)
  ShareFrame(modifier, data.footer) {
    Legend("A", data.titleA, colors.a, Modifier.testTag("share_legend_a"))
    Legend("B", data.titleB, colors.b, Modifier.testTag("share_legend_b"))
    Spacer(Modifier.height(BODY_GAP))
    ShareChartSlot(Modifier.weight(1f)) { CompareWater(data.a, data.b, colors.a, colors.b) }
    Spacer(Modifier.height(BODY_GAP))
    ShareTable(remember(data.a, data.b) { compareRows(data.a, data.b).filter { it.metric in SHARED_METRICS } }, data.a.entity.unit)
  }
}

@Composable
private fun Legend(letter: String, title: String, color: Color, modifier: Modifier) {
  Row(modifier.fillMaxWidth().height(TableRowHeight), horizontalArrangement = Arrangement.spacedBy(AntTheme.spacing.xs), verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.size(DotSize).background(color, CircleShape))
    AntText(letter, strong = true)
    AntText(title, Modifier.weight(1f), type = AntTextType.Secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
  }
}

@Composable
private fun CompareWater(a: BrewRecord, b: BrewRecord, colorA: Color, colorB: Color) {
  val series = remember(a, b, colorA, colorB) {
    listOfNotNull(
      CompareSeries(colorA, a.samples).takeIf { !a.curveError },
      CompareSeries(colorB, b.samples).takeIf { !b.curveError },
    )
  }
  if (series.isEmpty()) {
    CurveError()
    return
  }
  val unit = a.entity.unit
  val label = unit.symbol
  CompareChart(
    series = series,
    kind = CompareKind.WATER,
    unit = unit,
    xMaxMs = maxOf(a.summary.totalTimeMs, b.summary.totalTimeMs),
    unitLabel = label,
    description = stringResource(R.string.compare_water_description, label),
    fillHeight = true,
  )
}

@Composable
private fun ShareTable(rows: List<CompareRow>, unit: dev.dotscale.protocol.WeightUnit) {
  Column(Modifier.fillMaxWidth().testTag("share_table")) {
    Row(Modifier.fillMaxWidth().height(HeaderRowHeight), verticalAlignment = Alignment.CenterVertically) {
      Spacer(Modifier.width(LabelWidth))
      HeaderCell(stringResource(R.string.compare_column_a), Modifier.weight(1f))
      HeaderCell(stringResource(R.string.compare_column_b), Modifier.weight(1f))
      HeaderCell(stringResource(R.string.compare_column_diff), Modifier.weight(1f))
    }
    rows.forEach { row ->
      val key = row.metric.name.lowercase()
      Row(Modifier.fillMaxWidth().height(TableRowHeight).testTag("share_row_$key"), verticalAlignment = Alignment.CenterVertically) {
        AntText(stringResource(row.metric.shareLabel()), Modifier.width(LabelWidth), type = AntTextType.Secondary, style = AntTheme.typography.textSM, maxLines = 1)
        ValueCell(formatValue(row.metric, row.a, unit), Modifier.weight(1f))
        ValueCell(formatValue(row.metric, row.b, unit), Modifier.weight(1f))
        ValueCell(formatDiff(row.metric, row.diff, unit), Modifier.weight(1f))
      }
    }
  }
}

private fun CompareMetric.shareLabel(): Int = when (this) {
  CompareMetric.TOTAL_TIME -> R.string.share_metric_time
  CompareMetric.TOTAL_WATER -> R.string.brew_summary_total_water
  CompareMetric.RATIO -> R.string.scale_ratio
  CompareMetric.STABILITY -> R.string.brew_summary_stability
  else -> R.string.brew_summary_total_time
}

@Composable
private fun HeaderCell(text: String, modifier: Modifier) {
  AntText(text, modifier, type = AntTextType.Secondary, style = AntTheme.typography.textSM, strong = true, textAlign = TextAlign.End, maxLines = 1)
}

@Composable
private fun ValueCell(text: String, modifier: Modifier) {
  Text(
    text,
    modifier = modifier,
    color = AntTheme.colors.text,
    style = AntTheme.typography.textSM.copy(fontFeatureSettings = "tnum", fontWeight = FontWeight.Normal),
    textAlign = TextAlign.End,
    maxLines = 1,
    overflow = TextOverflow.Ellipsis,
  )
}
