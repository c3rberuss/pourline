package dev.dotscale.ui.scale

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxHeight
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
import dev.dotscale.brew.TargetCurve
import dev.dotscale.brew.downsample
import dev.dotscale.brew.fromGrams
import dev.dotscale.protocol.WeightUnit
import dev.dotscale.ui.theme.AntTheme
import kotlin.math.ceil

/** Topes con las muestras y, si hay, el agua máxima y el `endMs` del objetivo. */
private fun chartScale(samples: List<BrewSample>, unit: WeightUnit, target: TargetCurve?): ChartScale {
  var maxT = target?.endMs ?: 0L
  var maxWater = target?.let { t -> fromGrams(t.points.maxOf { it.waterG }, unit) } ?: 0.0
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
 *
 * Con [fillHeight] ocupa todo el alto que le da el padre en lugar de [ChartHeight] (las imágenes para compartir).
 *
 * Con [target] (en gramos) dibuja además la curva objetivo, discontinua, sobre el eje del agua, y los
 * topes incluyen su agua máxima y su `endMs`. Con [samples] vacío solo se ve el objetivo.
 */
@Composable
fun BrewChart(
  samples: List<BrewSample>,
  unit: WeightUnit,
  waterUnitLabel: String,
  flowUnitLabel: String,
  description: String,
  modifier: Modifier = Modifier,
  target: TargetCurve? = null,
  fillHeight: Boolean = false,
) {
  val colors = brewChartColors(AntTheme.colors)
  val labelStyle = AntTheme.typography.textSM.copy(color = colors.axisLabel)
  val measurer = rememberTextMeasurer()
  val density = LocalDensity.current
  var widthPx by remember { mutableIntStateOf(0) }
  val plotWidthPx = (widthPx - 2 * with(density) { GutterWidth.roundToPx() }).coerceAtLeast(0)

  val scale = remember(samples, unit, target) { chartScale(samples, unit, target) }
  val water = remember(samples, unit, plotWidthPx, scale.xTopMs) {
    downsample(samples, plotWidthPx, scale.xTopMs) { fromGrams(it.waterG, unit).coerceAtLeast(0.0) }
  }
  val flow = remember(samples, unit, plotWidthPx, scale.xTopMs) {
    downsample(samples, plotWidthPx, scale.xTopMs) { fromGrams(it.flowGps, unit).coerceAtLeast(0.0) }
  }

  // La curva objetivo reducida se recalcula solo si cambia el objetivo, la unidad o el ancho: nunca por muestra.
  val targetLine = remember(target, unit, plotWidthPx, scale.xTopMs) {
    target?.let { curve ->
      val asSamples = curve.points.map { BrewSample(it.tMs, it.waterG, 0.0, null, valid = true) }
      downsample(asSamples, plotWidthPx, scale.xTopMs) { fromGrams(it.waterG, unit).coerceAtLeast(0.0) }
    }.orEmpty()
  }

  Canvas(
    modifier
      .fillMaxWidth()
      .then(if (fillHeight) Modifier.fillMaxHeight() else Modifier.height(ChartHeight))
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
    drawTarget(plot, scale, targetLine, colors.target)
    drawWater(plot, scale, water, colors)
    drawFlow(plot, scale, flow, colors)
  }
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
