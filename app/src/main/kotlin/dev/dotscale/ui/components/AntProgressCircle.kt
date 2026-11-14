package dev.dotscale.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.dotscale.ui.theme.AntTheme

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

private const val FULL_CIRCLE = 360f
private const val START_ANGLE = -90f
private const val GAP_DEGREES = 2f

/**
 * `Progress type="circle"` de Ant Design. [fraction] se recorta a 0..1 y se anima; [segments] son
 * fronteras (0..1) dibujadas como huecos de 2°. Lo que se pase en [content] se centra dentro del aro.
 */
@Composable
fun AntProgressCircle(
  fraction: Float,
  modifier: Modifier = Modifier,
  strokeWidth: Dp = 12.dp,
  segments: List<Float> = emptyList(),
  color: Color = AntTheme.colors.primary.base,
  trackColor: Color = AntTheme.colors.fillSecondary,
  content: @Composable BoxScope.() -> Unit = {},
) {
  val clamped = fraction.coerceIn(0f, 1f)
  val animated by animateFloatAsState(
    clamped,
    tween(AntTheme.motion.durationMid, easing = AntTheme.motion.easeOut),
    label = "AntProgressCircle",
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
      draw(sweepAngles(animated, segments, GAP_DEGREES), color)
    }
    Box(Modifier.padding(strokeWidth), contentAlignment = Alignment.Center, content = content)
  }
}
