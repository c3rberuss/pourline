package dev.josueamaya.pourline.ui.scale

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import dev.josueamaya.pourline.brew.ChartPoint
import kotlin.math.ceil

/**
 * Piezas de dibujo compartidas por [BrewChart] y `CompareChart`: el área de trazado, la rejilla con sus
 * etiquetas y el troceado de series con huecos.
 */
internal val ChartHeight = 200.dp
internal val GutterWidth = 40.dp
internal val TopPadding = 30.dp
internal val BottomPadding = 22.dp
internal val LabelGap = 4.dp
internal val LineWidth = 2.dp
internal val TargetDashOn = 6.dp
internal val TargetDashOff = 4.dp
private const val HORIZONTAL_DIVISIONS = 4
private const val TIME_TICK_BASE_MS = 30_000L
private const val MAX_TIME_TICKS = 4

/** Topes de los ejes: tiempo, agua (izquierda) y flujo (derecha). */
internal data class ChartScale(val xTopMs: Long, val waterTop: Double, val flowTop: Double)

internal class PlotArea(val left: Float, val right: Float, val top: Float, val bottom: Float) {
  val width get() = right - left
  val height get() = bottom - top

  fun x(tMs: Long, xTopMs: Long) = left + width * (tMs.toFloat() / xTopMs).coerceIn(0f, 1f)

  fun y(value: Double, top: Double) = bottom - height * (value / top).toFloat().coerceIn(0f, 1f)
}

/** Rejilla horizontal, etiquetas de los dos ejes, títulos de las unidades y marcas de tiempo. */
internal fun DrawScope.drawGridAndLabels(
  plot: PlotArea,
  scale: ChartScale,
  colors: BrewChartColors,
  measurer: TextMeasurer,
  style: TextStyle,
  waterUnitLabel: String,
  flowUnitLabel: String,
  showFlowAxis: Boolean = true,
  leftTitleColor: Color = colors.water,
) {
  val gap = LabelGap.toPx()
  for (i in 0..HORIZONTAL_DIVISIONS) {
    val fraction = i.toFloat() / HORIZONTAL_DIVISIONS
    val y = plot.bottom - plot.height * fraction
    drawLine(colors.grid, Offset(plot.left, y), Offset(plot.right, y), strokeWidth = 1.dp.toPx())

    val left = measurer.measure(formatAxisValue(scale.waterTop * fraction), style)
    drawText(left, topLeft = Offset(plot.left - gap - left.size.width, y - left.size.height / 2f))
    if (showFlowAxis) {
      val right = measurer.measure(formatAxisValue(scale.flowTop * fraction), style)
      drawText(right, topLeft = Offset(plot.right + gap, y - right.size.height / 2f))
    }
  }

  val waterTitle = measurer.measure(waterUnitLabel, style.copy(color = leftTitleColor))
  drawText(waterTitle, topLeft = Offset(plot.left - gap - waterTitle.size.width, 0f))
  if (showFlowAxis) {
    val flowTitle = measurer.measure(flowUnitLabel, style)
    drawText(flowTitle, topLeft = Offset(plot.right + gap, 0f))
  }

  val stepMs = TIME_TICK_BASE_MS * ceil(scale.xTopMs.toDouble() / TIME_TICK_BASE_MS / MAX_TIME_TICKS).toLong()
  var t = 0L
  while (t <= scale.xTopMs) {
    val label = measurer.measure(formatDuration(t), style)
    val x = (plot.x(t, scale.xTopMs) - label.size.width / 2f).coerceIn(0f, size.width - label.size.width)
    drawText(label, topLeft = Offset(x, plot.bottom + gap))
    t += stepMs
  }
}

/** Trozos continuos de una serie: un hueco (`breakBefore`) abre un trozo nuevo. */
internal fun List<ChartPoint>.segments(): List<List<ChartPoint>> {
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

/** Una serie del eje del agua como línea continua del color indicado. */
internal fun DrawScope.drawLineSeries(plot: PlotArea, scale: ChartScale, points: List<ChartPoint>, top: Double, color: Color) {
  val stroke = Stroke(LineWidth.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
  for (segment in points.segments()) {
    val xs = segment.map { plot.x(it.tMs, scale.xTopMs) }
    val ys = segment.map { plot.y(it.value, top) }
    if (segment.size == 1) {
      drawCircle(color, LineWidth.toPx() / 2, Offset(xs[0], ys[0]))
      continue
    }
    val line = Path().apply {
      moveTo(xs[0], ys[0])
      for (i in 1 until xs.size) lineTo(xs[i], ys[i])
    }
    drawPath(line, color, style = stroke)
  }
}

/** La curva objetivo: línea discontinua (6 dp / 4 dp) sobre el eje del agua. */
internal fun DrawScope.drawTarget(plot: PlotArea, scale: ChartScale, points: List<ChartPoint>, color: Color) {
  if (points.size < 2) return
  val path = Path().apply {
    moveTo(plot.x(points[0].tMs, scale.xTopMs), plot.y(points[0].value, scale.waterTop))
    for (i in 1 until points.size) lineTo(plot.x(points[i].tMs, scale.xTopMs), plot.y(points[i].value, scale.waterTop))
  }
  val dash = PathEffect.dashPathEffect(floatArrayOf(TargetDashOn.toPx(), TargetDashOff.toPx()))
  drawPath(path, color, style = Stroke(LineWidth.toPx(), cap = StrokeCap.Butt, join = StrokeJoin.Round, pathEffect = dash))
}
