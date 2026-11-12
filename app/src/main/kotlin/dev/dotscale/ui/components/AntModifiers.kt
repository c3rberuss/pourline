package dev.dotscale.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Borde de 1 dp (`lineWidth`) con soporte para el estilo discontinuo de los botones `dashed`. */
internal fun Modifier.antBorder(
  color: Color,
  radius: Dp,
  width: Dp = 1.dp,
  dashed: Boolean = false,
): Modifier = drawWithCache {
  val strokePx = width.toPx()
  val inset = strokePx / 2
  val effect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())) else null
  val stroke = Stroke(width = strokePx, pathEffect = effect)
  val corner = CornerRadius((radius.toPx() - inset).coerceAtLeast(0f))
  onDrawWithContent {
    drawContent()
    if (color.alpha > 0f) {
      drawRoundRect(
        color = color,
        topLeft = Offset(inset, inset),
        size = Size(size.width - strokePx, size.height - strokePx),
        cornerRadius = corner,
        style = stroke,
      )
    }
  }
}

internal inline fun Modifier.thenIf(condition: Boolean, block: Modifier.() -> Modifier): Modifier =
  if (condition) then(block(Modifier)) else this
