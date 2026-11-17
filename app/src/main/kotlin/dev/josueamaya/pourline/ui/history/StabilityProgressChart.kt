package dev.josueamaya.pourline.ui.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import dev.josueamaya.pourline.R
import dev.josueamaya.pourline.history.ProgressPoint
import dev.josueamaya.pourline.ui.scale.brewChartColors
import dev.josueamaya.pourline.ui.theme.AntTheme
import kotlin.math.roundToInt

private val ChartHeight = 140.dp
private val GutterWidth = 28.dp
private val TopPadding = 8.dp
private val BottomPadding = 22.dp
private val PointRadius = 4.dp
private const val REFERENCE_STABLE = 75
private const val REFERENCE_IRREGULAR = 50

/**
 * Gráfica de progreso: un punto por preparación (eje X por orden cronológico a intervalos iguales, eje Y de 0 a
 * 100) con el color de su nivel. Líneas de referencia en 75 y 50 y las fechas de la primera y la última.
 */
@Composable
fun StabilityProgressChart(points: List<ProgressPoint>, modifier: Modifier = Modifier) {
  val colors = brewChartColors(AntTheme.colors)
  val labelStyle = AntTheme.typography.textSM.copy(color = colors.axisLabel)
  val measurer = rememberTextMeasurer()
  val average = if (points.isEmpty()) 0 else points.map { it.score }.average().roundToInt()
  val description = pluralStringResource(R.plurals.history_progress_description, points.size, points.size, average, points.lastOrNull()?.score ?: 0)
  val firstDate = points.firstOrNull()?.let { formatBrewDate(it.startedAtEpochMs) }.orEmpty()
  val lastDate = points.lastOrNull()?.let { formatBrewDate(it.startedAtEpochMs) }.orEmpty()

  Canvas(
    modifier
      .fillMaxWidth()
      .height(ChartHeight)
      .testTag("progress_chart")
      .semantics { contentDescription = description },
  ) {
    val left = GutterWidth.toPx()
    val right = size.width - PointRadius.toPx()
    val top = TopPadding.toPx()
    val bottom = size.height - BottomPadding.toPx()
    fun y(score: Int) = bottom - (bottom - top) * score / 100f
    fun x(index: Int) = if (points.size < 2) left else left + (right - left) * index / (points.size - 1)

    for (reference in listOf(REFERENCE_STABLE, REFERENCE_IRREGULAR)) {
      drawLine(colors.grid, Offset(left, y(reference)), Offset(right, y(reference)), strokeWidth = 1.dp.toPx())
      val label = measurer.measure(reference.toString(), labelStyle)
      drawText(label, topLeft = Offset(left - 4.dp.toPx() - label.size.width, y(reference) - label.size.height / 2f))
    }
    drawLine(colors.grid, Offset(left, bottom), Offset(right, bottom), strokeWidth = 1.dp.toPx())

    val path = Path()
    points.forEachIndexed { i, p -> if (i == 0) path.moveTo(x(i), y(p.score)) else path.lineTo(x(i), y(p.score)) }
    drawPath(path, colors.neutral.copy(alpha = 0.4f), style = Stroke(width = 1.dp.toPx(), cap = StrokeCap.Round))
    points.forEachIndexed { i, p -> drawCircle(colors.flowColor(p.score), PointRadius.toPx(), Offset(x(i), y(p.score))) }

    val first = measurer.measure(firstDate, labelStyle)
    drawText(first, topLeft = Offset(left, bottom + 4.dp.toPx()))
    val last = measurer.measure(lastDate, labelStyle)
    drawText(last, topLeft = Offset(right - last.size.width, bottom + 4.dp.toPx()))
  }
}
