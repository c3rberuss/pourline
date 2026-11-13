package dev.dotscale.ui.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import dev.dotscale.brew.BrewSample
import dev.dotscale.brew.ChartAxis
import dev.dotscale.brew.downsample
import dev.dotscale.brew.fromGrams
import dev.dotscale.protocol.WeightUnit
import dev.dotscale.ui.scale.BottomPadding
import dev.dotscale.ui.scale.ChartHeight
import dev.dotscale.ui.scale.ChartScale
import dev.dotscale.ui.scale.GutterWidth
import dev.dotscale.ui.scale.PlotArea
import dev.dotscale.ui.scale.TopPadding
import dev.dotscale.ui.scale.brewChartColors
import dev.dotscale.ui.scale.drawGridAndLabels
import dev.dotscale.ui.scale.drawLineSeries
import dev.dotscale.ui.theme.AntTheme

/** Qué mide el eje Y de una [CompareChart]. */
enum class CompareKind { WATER, FLOW }

/** Una serie de la comparación: las muestras de una preparación y el color con el que se dibuja. */
@Immutable
class CompareSeries(val color: Color, val samples: List<BrewSample>)

private val RightPadding = 8.dp

private fun CompareKind.valueOf(sample: BrewSample, unit: WeightUnit): Double =
  fromGrams(if (this == CompareKind.WATER) sample.waterG else sample.flowGps, unit).coerceAtLeast(0.0)

/**
 * Topes de la comparación: el tiempo llega hasta [xMaxMs] y el eje Y hasta el máximo de las dos series (con
 * [ChartAxis]), así que las dos preparaciones se leen contra la misma escala. En un solo eje, `waterTop` y
 * `flowTop` valen lo mismo.
 */
internal fun compareScale(series: List<CompareSeries>, kind: CompareKind, unit: WeightUnit, xMaxMs: Long): ChartScale {
  var max = 0.0
  for (s in series) for (sample in s.samples) if (sample.valid) max = maxOf(max, kind.valueOf(sample, unit))
  val top = if (kind == CompareKind.WATER) ChartAxis.waterTop(max, unit) else ChartAxis.flowTop(max, unit)
  return ChartScale(ChartAxis.timeTopMs(xMaxMs), top, top)
}

/**
 * Las series de agua o de flujo de dos preparaciones sobre los mismos ejes, cada una de su color. Reutiliza las
 * piezas de [dev.dotscale.ui.scale.BrewChart] (rejilla, ejes y reducción de puntos) con un solo eje Y; el flujo no se colorea por
 * estabilidad.
 */
@Composable
fun CompareChart(
  series: List<CompareSeries>,
  kind: CompareKind,
  unit: WeightUnit,
  xMaxMs: Long,
  unitLabel: String,
  description: String,
  modifier: Modifier = Modifier,
  fillHeight: Boolean = false,
) {
  val colors = brewChartColors(AntTheme.colors)
  val labelStyle = AntTheme.typography.textSM.copy(color = colors.axisLabel)
  val measurer = rememberTextMeasurer()
  val density = LocalDensity.current
  var widthPx by remember { mutableIntStateOf(0) }
  val plotWidthPx = (widthPx - with(density) { GutterWidth.roundToPx() + RightPadding.roundToPx() }).coerceAtLeast(0)

  val scale = remember(series, kind, unit, xMaxMs) { compareScale(series, kind, unit, xMaxMs) }
  val lines = remember(series, kind, unit, plotWidthPx, scale.xTopMs) {
    series.map { s -> downsample(s.samples, plotWidthPx, scale.xTopMs) { kind.valueOf(it, unit) } }
  }

  Canvas(
    modifier
      .fillMaxWidth()
      .then(if (fillHeight) Modifier.fillMaxHeight() else Modifier.height(ChartHeight))
      .onSizeChanged { widthPx = it.width }
      .semantics { contentDescription = description },
  ) {
    val plot = PlotArea(
      left = GutterWidth.toPx(),
      right = size.width - RightPadding.toPx(),
      top = TopPadding.toPx(),
      bottom = size.height - BottomPadding.toPx(),
    )
    drawGridAndLabels(plot, scale, colors, measurer, labelStyle, unitLabel, unitLabel, showFlowAxis = false, leftTitleColor = colors.axisLabel)
    series.forEachIndexed { i, s -> drawLineSeries(plot, scale, lines[i], scale.waterTop, s.color) }
  }
}
