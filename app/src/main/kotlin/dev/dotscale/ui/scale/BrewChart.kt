package dev.dotscale.ui.scale

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import dev.dotscale.brew.BrewSample
import dev.dotscale.brew.ChartAxis
import dev.dotscale.brew.ChartPoint
import dev.dotscale.brew.downsample
import dev.dotscale.brew.fromGrams
import dev.dotscale.protocol.WeightUnit
import dev.dotscale.ui.theme.AntTheme
import kotlin.math.ceil

private val ChartHeight = 200.dp
private val GutterWidth = 40.dp
private val TopPadding = 30.dp
private val BottomPadding = 22.dp
private val LabelGap = 4.dp
private val LineWidth = 2.dp
private const val HORIZONTAL_DIVISIONS = 4
private const val TIME_TICK_BASE_MS = 30_000L
private const val MAX_TIME_TICKS = 4

private data class ChartScale(val xTopMs: Long, val waterTop: Double, val flowTop: Double)

private fun chartScale(samples: List<BrewSample>, unit: WeightUnit): ChartScale {
  var maxT = 0L
  var maxWater = 0.0
  var maxFlow = 0.0
  for (s in samples) {
    if (s.tMs > maxT) maxT = s.tMs
    if (!s.valid) continue
    maxWater = maxOf(maxWater, fromGrams(s.waterG, unit))
    maxFlow = maxOf(maxFlow, fromGrams(s.flowGps, unit))
  }
  return ChartScale(ChartAxis.timeTopMs(maxT), ChartAxis.waterTop(maxWater, unit), ChartAxis.flowTop(maxFlow, unit))
}

/**
 * Gráfica en vivo de doble eje: agua (izquierda, con relleno) y flujo (derecha, coloreado por nivel
 * de estabilidad). Los topes salen de [ChartAxis] y las series se reducen con [downsample] según el
 * ancho disponible.
 */
@Composable
fun BrewChart(
  samples: List<BrewSample>,
  unit: WeightUnit,
  waterUnitLabel: String,
  flowUnitLabel: String,
  description: String,
  modifier: Modifier = Modifier,
) {
  val colors = brewChartColors(AntTheme.colors)
  val labelStyle = AntTheme.typography.textSM.copy(color = colors.axisLabel)
  val measurer = rememberTextMeasurer()
  val density = LocalDensity.current
  var widthPx by remember { mutableIntStateOf(0) }
  val plotWidthPx = (widthPx - 2 * with(density) { GutterWidth.roundToPx() }).coerceAtLeast(0)

  val scale = remember(samples, unit) { chartScale(samples, unit) }
  val water = remember(samples, unit, plotWidthPx, scale.xTopMs) {
    downsample(samples, plotWidthPx, scale.xTopMs) { fromGrams(it.waterG, unit).coerceAtLeast(0.0) }
  }
  val flow = remember(samples, unit, plotWidthPx, scale.xTopMs) {
    downsample(samples, plotWidthPx, scale.xTopMs) { fromGrams(it.flowGps, unit).coerceAtLeast(0.0) }
  }

  Canvas(
    modifier
      .fillMaxWidth()
      .height(ChartHeight)
      .onSizeChanged { widthPx = it.width }
      .testTag("brew_chart")
      .semantics { contentDescription = description },
  ) {
    val plot = PlotArea(
      left = GutterWidth.toPx(),
      right = size.width - GutterWidth.toPx(),
      top = TopPadding.toPx(),
      bottom = size.height - BottomPadding.toPx(),
    )
    drawGridAndLabels(plot, scale, colors, measurer, labelStyle, waterUnitLabel, flowUnitLabel)
    drawWater(plot, scale, water, colors)
    drawFlow(plot, scale, flow, colors)
  }
}

private class PlotArea(val left: Float, val right: Float, val top: Float, val bottom: Float) {
  val width get() = right - left
  val height get() = bottom - top

  fun x(tMs: Long, xTopMs: Long) = left + width * (tMs.toFloat() / xTopMs).coerceIn(0f, 1f)

  fun y(value: Double, top: Double) = bottom - height * (value / top).toFloat().coerceIn(0f, 1f)
}

private fun DrawScope.drawGridAndLabels(
  plot: PlotArea,
  scale: ChartScale,
  colors: BrewChartColors,
  measurer: TextMeasurer,
  style: androidx.compose.ui.text.TextStyle,
  waterUnitLabel: String,
  flowUnitLabel: String,
) {
  val gap = LabelGap.toPx()
  for (i in 0..HORIZONTAL_DIVISIONS) {
    val fraction = i.toFloat() / HORIZONTAL_DIVISIONS
    val y = plot.bottom - plot.height * fraction
    drawLine(colors.grid, Offset(plot.left, y), Offset(plot.right, y), strokeWidth = 1.dp.toPx())

    val left = measurer.measure(formatAxisValue(scale.waterTop * fraction), style)
    drawText(left, topLeft = Offset(plot.left - gap - left.size.width, y - left.size.height / 2f))
    val right = measurer.measure(formatAxisValue(scale.flowTop * fraction), style)
    drawText(right, topLeft = Offset(plot.right + gap, y - right.size.height / 2f))
  }

  val waterTitle = measurer.measure(waterUnitLabel, style.copy(color = colors.water))
  drawText(waterTitle, topLeft = Offset(plot.left - gap - waterTitle.size.width, 0f))
  val flowTitle = measurer.measure(flowUnitLabel, style)
  drawText(flowTitle, topLeft = Offset(plot.right + gap, 0f))

  val stepMs = TIME_TICK_BASE_MS * ceil(scale.xTopMs.toDouble() / TIME_TICK_BASE_MS / MAX_TIME_TICKS).toLong()
  var t = 0L
  while (t <= scale.xTopMs) {
    val label = measurer.measure(formatDuration(t), style)
    val x = (plot.x(t, scale.xTopMs) - label.size.width / 2f).coerceIn(0f, size.width - label.size.width)
    drawText(label, topLeft = Offset(x, plot.bottom + gap))
    t += stepMs
  }
}

private fun List<ChartPoint>.segments(): List<List<ChartPoint>> {
  val out = ArrayList<List<ChartPoint>>()
  var current = ArrayList<ChartPoint>()
  for (p in this) {
    if (p.breakBefore && current.isNotEmpty()) {
      out.add(current)
      current = ArrayList()
    }
    current.add(p)
  }
  if (current.isNotEmpty()) out.add(current)
  return out
}

private fun DrawScope.drawWater(plot: PlotArea, scale: ChartScale, points: List<ChartPoint>, colors: BrewChartColors) {
  val stroke = Stroke(LineWidth.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
  for (segment in points.segments()) {
    val xs = segment.map { plot.x(it.tMs, scale.xTopMs) }
    val ys = segment.map { plot.y(it.value, scale.waterTop) }
    if (segment.size == 1) {
      drawCircle(colors.water, LineWidth.toPx() / 2, Offset(xs[0], ys[0]))
      continue
    }
    val line = Path().apply {
      moveTo(xs[0], ys[0])
      for (i in 1 until xs.size) lineTo(xs[i], ys[i])
    }
    val fill = Path().apply {
      addPath(line)
      lineTo(xs.last(), plot.bottom)
      lineTo(xs.first(), plot.bottom)
      close()
    }
    drawPath(fill, colors.waterFill)
    drawPath(line, colors.water, style = stroke)
  }
}

private fun DrawScope.drawFlow(plot: PlotArea, scale: ChartScale, points: List<ChartPoint>, colors: BrewChartColors) {
  val width = LineWidth.toPx()
  for (segment in points.segments()) {
    fun offset(p: ChartPoint) = Offset(plot.x(p.tMs, scale.xTopMs), plot.y(p.value, scale.flowTop))
    if (segment.size == 1) {
      drawCircle(colors.flowColor(segment[0].stability), width / 2, offset(segment[0]))
      continue
    }
    for (i in 1 until segment.size) {
      drawLine(colors.flowColor(segment[i].stability), offset(segment[i - 1]), offset(segment[i]), strokeWidth = width, cap = StrokeCap.Round)
    }
  }
}
