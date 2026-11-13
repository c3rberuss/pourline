package dev.dotscale.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.dotscale.R
import dev.dotscale.brew.fromGrams
import dev.dotscale.history.BrewRecord
import dev.dotscale.protocol.WeightUnit
import dev.dotscale.ui.components.AntAlert
import dev.dotscale.ui.components.AntCard
import dev.dotscale.ui.components.AntDivider
import dev.dotscale.ui.components.AntIconButton
import dev.dotscale.ui.components.AntIcons
import dev.dotscale.ui.components.AntSpin
import dev.dotscale.ui.components.AntStatus
import dev.dotscale.ui.components.AntText
import dev.dotscale.ui.components.AntTextType
import dev.dotscale.ui.components.AntTopBar
import dev.dotscale.ui.scale.formatDuration
import dev.dotscale.ui.scale.formatNumber
import dev.dotscale.ui.theme.AntTheme
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private const val MINUS = '−'

@Composable
fun BrewCompareScreen(viewModel: BrewCompareViewModel, onBack: () -> Unit) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  LaunchedEffect(state) { if (state is BrewCompareState.Gone) onBack() }

  Scaffold(
    containerColor = AntTheme.colors.bgLayout,
    topBar = {
      AntTopBar(
        title = stringResource(R.string.compare_title),
        navigationIcon = {
          AntIconButton(AntIcons.ArrowLeft, contentDescription = stringResource(R.string.scale_back), onClick = onBack)
        },
      )
    },
  ) { padding ->
    when (val current = state) {
      is BrewCompareState.Loaded -> Column(
        Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(AntTheme.spacing.base),
        verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.sm),
      ) {
        CompareContent(current.a, current.b)
      }
      BrewCompareState.Loading, BrewCompareState.Gone -> AntSpin(Modifier.padding(padding).padding(AntTheme.spacing.xl))
    }
  }
}

@Composable
private fun CompareContent(a: BrewRecord, b: BrewRecord) {
  val unit = a.entity.unit
  val colors = compareColors(AntTheme.colors)
  Legend(a, b, colors)
  if (a.curveError) AntAlert(message = stringResource(R.string.compare_curve_error, "A"), type = AntStatus.Warning, modifier = Modifier.testTag("compare_curve_error_a"))
  if (b.curveError) AntAlert(message = stringResource(R.string.compare_curve_error, "B"), type = AntStatus.Warning, modifier = Modifier.testTag("compare_curve_error_b"))

  // Las series con curva ilegible se omiten; si lo son las dos, solo queda la tabla.
  val series = remember(a, b, colors) {
    listOfNotNull(
      CompareSeries(colors.a, a.samples).takeIf { !a.curveError },
      CompareSeries(colors.b, b.samples).takeIf { !b.curveError },
    )
  }
  if (series.isNotEmpty()) {
    val xMaxMs = maxOf(a.summary.totalTimeMs, b.summary.totalTimeMs)
    val waterLabel = unit.symbol
    val flowLabel = stringResource(R.string.scale_flow_value, unit.symbol)
    AntCard(Modifier.fillMaxWidth(), title = stringResource(R.string.compare_water_title)) {
      CompareChart(
        series, CompareKind.WATER, unit, xMaxMs, waterLabel,
        description = stringResource(R.string.compare_water_description, waterLabel),
        modifier = Modifier.testTag("compare_chart_water"),
      )
    }
    AntCard(Modifier.fillMaxWidth(), title = stringResource(R.string.compare_flow_title)) {
      CompareChart(
        series, CompareKind.FLOW, unit, xMaxMs, flowLabel,
        description = stringResource(R.string.compare_flow_description, flowLabel),
        modifier = Modifier.testTag("compare_chart_flow"),
      )
    }
  }
  CompareTable(compareRows(a, b), unit)
}

/** A y B: un punto con su color, el nombre (o la fecha y hora) y el café. */
@Composable
private fun Legend(a: BrewRecord, b: BrewRecord, colors: CompareColors) {
  AntCard(Modifier.fillMaxWidth().testTag("compare_legend")) {
    LegendItem("A", a, colors.a, Modifier.testTag("compare_legend_a"))
    LegendItem("B", b, colors.b, Modifier.testTag("compare_legend_b"))
  }
}

@Composable
private fun LegendItem(letter: String, record: BrewRecord, color: Color, modifier: Modifier) {
  val entity = record.entity
  Row(modifier, horizontalArrangement = Arrangement.spacedBy(AntTheme.spacing.xs), verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.size(12.dp).background(color, CircleShape))
    Column(Modifier.weight(1f)) {
      AntText(stringResource(R.string.compare_legend_item, letter, entity.name ?: formatBrewDateTime(entity.startedAtEpochMs)), strong = true)
      entity.coffee?.let { AntText(it, type = AntTextType.Secondary) }
    }
  }
}

@Composable
private fun CompareTable(rows: List<CompareRow>, unit: WeightUnit) {
  AntCard(Modifier.fillMaxWidth().testTag("compare_table"), title = stringResource(R.string.compare_table_title)) {
    Row(Modifier.fillMaxWidth()) {
      Cell(null, Modifier.weight(LABEL_WEIGHT))
      Cell(stringResource(R.string.compare_column_a), Modifier.weight(1f), header = true)
      Cell(stringResource(R.string.compare_column_b), Modifier.weight(1f), header = true)
      Cell(stringResource(R.string.compare_column_diff), Modifier.weight(1f), header = true)
    }
    rows.forEach { row ->
      AntDivider()
      val key = row.metric.name.lowercase()
      Row(Modifier.fillMaxWidth().testTag("compare_row_$key"), verticalAlignment = Alignment.CenterVertically) {
        Cell(stringResource(row.metric.labelRes()), Modifier.weight(LABEL_WEIGHT), secondary = true)
        Cell(formatValue(row.metric, row.a, unit), Modifier.weight(1f).testTag("compare_cell_a_$key"))
        Cell(formatValue(row.metric, row.b, unit), Modifier.weight(1f).testTag("compare_cell_b_$key"))
        Cell(formatDiff(row.metric, row.diff, unit), Modifier.weight(1f).testTag("compare_cell_diff_$key"))
      }
    }
  }
}

private const val LABEL_WEIGHT = 1.3f

@Composable
private fun Cell(text: String?, modifier: Modifier, header: Boolean = false, secondary: Boolean = false) {
  AntText(
    text = text.orEmpty(),
    modifier = modifier,
    type = if (header || secondary) AntTextType.Secondary else AntTextType.Default,
    style = AntTheme.typography.textSM,
    strong = header,
    textAlign = if (text == null || secondary) TextAlign.Start else TextAlign.End,
  )
}

private fun CompareMetric.labelRes(): Int = when (this) {
  CompareMetric.TOTAL_TIME -> R.string.brew_summary_total_time
  CompareMetric.TOTAL_WATER -> R.string.brew_summary_total_water
  CompareMetric.DOSE -> R.string.scale_dose
  CompareMetric.RATIO -> R.string.scale_ratio
  CompareMetric.AVG_FLOW -> R.string.brew_summary_avg_flow
  CompareMetric.MAX_FLOW -> R.string.brew_summary_max_flow
  CompareMetric.STABILITY -> R.string.brew_summary_stability
  CompareMetric.POURS -> R.string.brew_summary_pours
}

@Composable
private fun formatValue(metric: CompareMetric, value: Double?, unit: WeightUnit): String {
  if (value == null) return stringResource(R.string.compare_none)
  return when (metric) {
    CompareMetric.TOTAL_TIME -> formatDuration(value.toLong())
    CompareMetric.TOTAL_WATER, CompareMetric.DOSE -> "${formatNumber(value, unit)} ${unit.symbol}"
    CompareMetric.RATIO -> stringResource(R.string.scale_ratio_value, value)
    CompareMetric.AVG_FLOW, CompareMetric.MAX_FLOW -> "${formatNumber(value, unit)} ${stringResource(R.string.scale_flow_value, unit.symbol)}"
    CompareMetric.STABILITY, CompareMetric.POURS -> value.roundToInt().toString()
  }
}

/** B−A con "+" o "−" (U+2212); una diferencia nula se escribe "—" y una que se redondea a cero, sin signo. */
@Composable
private fun formatDiff(metric: CompareMetric, diff: Double?, unit: WeightUnit): String {
  if (diff == null) return stringResource(R.string.compare_none)
  val magnitude = abs(diff)
  val body = when (metric) {
    CompareMetric.TOTAL_TIME -> formatDuration(magnitude.toLong())
    CompareMetric.TOTAL_WATER, CompareMetric.DOSE -> "${formatNumber(magnitude, unit)} ${unit.symbol}"
    CompareMetric.RATIO -> String.format(Locale.getDefault(), "%.1f", magnitude)
    CompareMetric.AVG_FLOW, CompareMetric.MAX_FLOW -> "${formatNumber(magnitude, unit)} ${stringResource(R.string.scale_flow_value, unit.symbol)}"
    CompareMetric.STABILITY, CompareMetric.POURS -> magnitude.roundToInt().toString()
  }
  val isZero = body.takeWhile { !it.isWhitespace() }.all { it == '0' || it == '.' || it == ',' || it == ':' }
  val sign = if (isZero) "" else if (diff > 0) "+" else MINUS.toString()
  return sign + body
}
