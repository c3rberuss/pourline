package dev.dotscale.ui.grinders

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import dev.dotscale.R
import dev.dotscale.grinder.GrindFormat
import dev.dotscale.grinder.GrindLevel
import dev.dotscale.grinder.GrindRulerModel
import dev.dotscale.grinder.Grinder
import dev.dotscale.history.BrewMethod
import dev.dotscale.ui.history.label
import dev.dotscale.ui.theme.AntTheme

/**
 * La regla de molienda (§3.7, D3): pista horizontal con **el extremo grueso a la izquierda y el fino a la derecha**
 * en las dos direcciones del molino. Dibuja el rango de [method] como banda con su nombre, el ajuste actual como
 * marcador triangular con su valor y, con [showAnchors], las anclas como puntos con su abreviatura.
 */
@Composable
fun GrindRuler(
  grinder: Grinder,
  method: BrewMethod?,
  value: Double?,
  showAnchors: Boolean,
  modifier: Modifier = Modifier,
  testTag: String = "grind_ruler",
) {
  val colors = AntTheme.colors
  val texts = rememberGrindTexts()
  val measurer = rememberTextMeasurer()
  val ticks = remember(grinder.scaleMin, grinder.scaleMax, grinder.step, grinder.units) { GrindRulerModel.ticks(grinder) }
  val range = method?.let { grinder.ranges[it] }
  val methodName = method?.label()
  val anchorLabels = GrindLevel.entries.associateWith { it.abbreviation() }
  val valueText = value?.let { GrindFormat.format(it, grinder, texts) }

  val description = buildString {
    append(stringResource(R.string.grind_ruler_description))
    if (range != null && methodName != null) {
      append(' ')
      append(stringResource(R.string.grind_ruler_range, methodName, GrindFormat.format(range.min, grinder, texts), GrindFormat.format(range.max, grinder, texts)))
    }
    if (valueText != null) {
      append(' ')
      append(stringResource(R.string.grind_ruler_value, GrindFormat.format(value, grinder, texts)))
    }
  }
  val small = TextStyle(fontSize = 10.sp, color = colors.textTertiary)

  Column(modifier.testTag(testTag).semantics { contentDescription = description }, verticalArrangement = Arrangement.spacedBy(2.dp)) {
    Canvas(Modifier.fillMaxWidth().height(64.dp)) {
      val left = 8.dp.toPx()
      val width = size.width - 2 * left
      fun x(v: Double) = left + GrindRulerModel.fraction(v, grinder) * width
      val trackY = 40.dp.toPx()

      // Banda del rango del método.
      if (range != null) {
        val a = x(range.min)
        val b = x(range.max)
        val start = minOf(a, b)
        drawRect(colors.primary.bg, Offset(start, 22.dp.toPx()), Size(kotlin.math.abs(b - a), 12.dp.toPx()))
        methodName?.let { drawLabel(measurer, it, start + 2.dp.toPx(), 21.dp.toPx(), small.copy(color = colors.primary.text), maxWidth = kotlin.math.abs(b - a)) }
      }
      // Pista y marcas.
      drawLine(colors.border, Offset(left, trackY), Offset(left + width, trackY), 1.dp.toPx())
      for (tick in ticks) {
        val tx = x(tick.value)
        val len = if (tick.major) 6.dp.toPx() else 3.dp.toPx()
        drawLine(colors.borderSecondary, Offset(tx, trackY), Offset(tx, trackY + len), 1.dp.toPx())
        tick.label?.let { drawLabel(measurer, it, tx, trackY + 7.dp.toPx(), small, center = true) }
      }
      // Anclas.
      if (showAnchors) {
        for ((level, anchor) in grinder.anchors) {
          val ax = x(anchor)
          drawCircle(colors.textSecondary, 3.dp.toPx(), Offset(ax, trackY))
          drawLabel(measurer, anchorLabels.getValue(level), ax, trackY + 17.dp.toPx(), small, center = true)
        }
      }
      // Marcador del ajuste.
      if (value != null) {
        val mx = x(value).coerceIn(left, left + width)
        val path = Path().apply {
          moveTo(mx - 5.dp.toPx(), 12.dp.toPx())
          lineTo(mx + 5.dp.toPx(), 12.dp.toPx())
          lineTo(mx, trackY)
          close()
        }
        drawPath(path, colors.primary.base)
        valueText?.let { drawLabel(measurer, it, mx.coerceIn(left + 24.dp.toPx(), left + width - 24.dp.toPx()), 0f, small.copy(color = colors.text), center = true) }
      }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      Text(stringResource(R.string.grind_ruler_coarse), color = colors.textTertiary, style = AntTheme.typography.textSM)
      Text(stringResource(R.string.grind_ruler_fine), color = colors.textTertiary, style = AntTheme.typography.textSM)
    }
  }
}

private fun DrawScope.drawLabel(
  measurer: TextMeasurer,
  text: String,
  x: Float,
  y: Float,
  style: TextStyle,
  center: Boolean = false,
  maxWidth: Float = Float.MAX_VALUE,
) {
  val layout = measurer.measure(text, style, maxLines = 1)
  if (layout.size.width > maxWidth) return
  drawText(layout, topLeft = Offset(if (center) x - layout.size.width / 2f else x, y))
}
