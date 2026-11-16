package dev.dotscale.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.dotscale.ui.theme.AntTheme
import kotlin.math.cos
import kotlin.math.sin

/** Tramo del aro en grados desde el inicio (arriba, en sentido horario). */
internal data class ArcSpan(val start: Float, val sweep: Float)

/**
 * Tramos del arco relleno de un aro cuya fracción es [fraction] (0..1) y que lleva un hueco de
 * [gapDegrees] centrado en cada frontera de [segments] (fracciones en 0..1). Las fronteras en 0 o 1 y
 * los huecos más allá del arco relleno no se dibujan.
 */
internal fun sweepAngles(fraction: Float, segments: List<Float>, gapDegrees: Float): List<ArcSpan> {
  val filledEnd = fraction.coerceIn(0f, 1f) * FULL_CIRCLE
  val boundaries = segments.filter { it > 0f && it < 1f }.distinct().sorted().map { it * FULL_CIRCLE }
  val spans = mutableListOf<ArcSpan>()
  var start = 0f
  for (boundary in boundaries + FULL_CIRCLE) {
    val isEnd = boundary >= FULL_CIRCLE
    val spanEnd = if (isEnd) FULL_CIRCLE else boundary - gapDegrees / 2f
    val clipped = minOf(spanEnd, filledEnd)
    if (clipped > start) spans += ArcSpan(start, clipped - start)
    start = if (isEnd) FULL_CIRCLE else boundary + gapDegrees / 2f
  }
  return spans
}

/**
 * Los trozos de [spans] que caen entre [fromDegrees] y [toDegrees]: sirve para pintar de un color solo la parte del
 * arco relleno que corresponde a un tramo.
 */
internal fun clipSpans(spans: List<ArcSpan>, fromDegrees: Float, toDegrees: Float): List<ArcSpan> = spans.mapNotNull { span ->
  val start = maxOf(span.start, fromDegrees)
  val end = minOf(span.start + span.sweep, toDegrees)
  if (end > start) ArcSpan(start, end - start) else null
}

/** Un tramo del arco relleno que va del final del tramo anterior (o de 0) hasta [end] (0..1) y se pinta de [color]. */
@Immutable
data class AntProgressStop(val end: Float, val color: Color)

private const val FULL_CIRCLE = 360f
private const val START_ANGLE = -90f
private const val GAP_DEGREES = 2f
private val MARKER_WIDTH = 3.dp

/** Lo que la marca entra hacia dentro del aro, más allá del grosor del trazo, para que se vea sobre el relleno. */
private val MARKER_OVERHANG = 4.dp

/** Marca radial en [fraction] del aro: del borde exterior hasta [overhang] por dentro del borde interior. */
private fun DrawScope.drawMarker(fraction: Float, stroke: Float, overhang: Float, width: Float, color: Color) {
  val radians = Math.toRadians((START_ANGLE + fraction * FULL_CIRCLE).toDouble())
  val direction = Offset(cos(radians).toFloat(), sin(radians).toFloat())
  val outer = size.minDimension / 2f
  val inner = outer - stroke - overhang
  drawLine(color, center + direction * inner, center + direction * outer, strokeWidth = width, cap = StrokeCap.Butt)
}

/**
 * `Progress type="circle"` de Ant Design. [fraction] se recorta a 0..1 y se anima; [segments] son
 * fronteras (0..1) dibujadas como huecos de 2°. Lo que se pase en [content] se centra dentro del aro.
 *
 * Con [stops] el arco relleno se pinta por tramos, cada uno de su color; lo que quede después del último tramo va en
 * [color]. Con [marker] (0..1, animado) se dibuja una marca radial de [markerColor] que cruza el aro en esa fracción.
 */
@Composable
fun AntProgressCircle(
  fraction: Float,
  modifier: Modifier = Modifier,
  strokeWidth: Dp = 12.dp,
  segments: List<Float> = emptyList(),
  color: Color = AntTheme.colors.primary.base,
  trackColor: Color = AntTheme.colors.fillSecondary,
  stops: List<AntProgressStop> = emptyList(),
  marker: Float? = null,
  markerColor: Color = AntTheme.colors.text,
  content: @Composable BoxScope.() -> Unit = {},
) {
  val clamped = fraction.coerceIn(0f, 1f)
  val animated by animateFloatAsState(
    clamped,
    tween(AntTheme.motion.durationMid, easing = AntTheme.motion.easeOut),
    label = "AntProgressCircle",
  )
  val animatedMarker by animateFloatAsState(
    marker?.coerceIn(0f, 1f) ?: 0f,
    tween(AntTheme.motion.durationMid, easing = AntTheme.motion.easeOut),
    label = "AntProgressCircleMarker",
  )
  Box(
    modifier.semantics { progressBarRangeInfo = ProgressBarRangeInfo(clamped, 0f..1f) },
    contentAlignment = Alignment.Center,
  ) {
    Canvas(Modifier.fillMaxSize()) {
      val stroke = strokeWidth.toPx()
      val inset = stroke / 2f
      val arcSize = Size(size.width - stroke, size.height - stroke)
      val topLeft = Offset(inset, inset)
      fun draw(spans: List<ArcSpan>, tint: Color) {
        spans.forEach { span ->
          drawArc(
            color = tint,
            startAngle = START_ANGLE + span.start,
            sweepAngle = span.sweep,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Butt),
          )
        }
      }
      draw(sweepAngles(1f, segments, GAP_DEGREES), trackColor)
      val filled = sweepAngles(animated, segments, GAP_DEGREES)
      var from = 0f
      stops.forEach { stop ->
        val to = stop.end.coerceIn(0f, 1f) * FULL_CIRCLE
        if (to > from) {
          draw(clipSpans(filled, from, to), stop.color)
          from = to
        }
      }
      draw(clipSpans(filled, from, FULL_CIRCLE), color)
      if (marker != null) drawMarker(animatedMarker, stroke, MARKER_OVERHANG.toPx(), MARKER_WIDTH.toPx(), markerColor)
    }
    Box(Modifier.padding(strokeWidth), contentAlignment = Alignment.Center, content = content)
  }
}
