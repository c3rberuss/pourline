package dev.dotscale.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.dotscale.ui.theme.AntTheme
import kotlin.math.abs

enum class AntSpinSize(val dp: Dp) { Small(14.dp), Default(20.dp), Large(32.dp) }

/** `Spin`: los cuatro puntos giratorios de Ant Design. Para cargas de sección o pantalla. */
@Composable
fun AntSpin(
  modifier: Modifier = Modifier,
  size: AntSpinSize = AntSpinSize.Default,
  color: Color = AntTheme.colors.primary.base,
) {
  val transition = rememberInfiniteTransition(label = "AntSpin")
  val rotation by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(1200, easing = LinearEasing)), label = "rotation")
  val phase by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1000, easing = LinearEasing)), label = "phase")
  Canvas(modifier.size(size.dp).semantics { progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate }) {
    val w = this.size.minDimension
    val radius = w * 0.225f
    val near = radius
    val far = w - radius
    val centers = listOf(Offset(near, near), Offset(far, near), Offset(far, far), Offset(near, far))
    rotate(45f + rotation) {
      centers.forEachIndexed { i, center ->
        val t = (phase + i * 0.25f) % 1f
        drawCircle(color.copy(alpha = color.alpha * (0.3f + 0.7f * abs(1f - 2f * t))), radius, center)
      }
    }
  }
}

/** `LoadingOutlined`: arco giratorio usado dentro de botones, switches y alertas. */
@Composable
fun AntLoadingIcon(
  modifier: Modifier = Modifier,
  size: Dp = 16.dp,
  color: Color = AntTheme.colors.primary.base,
) {
  val transition = rememberInfiniteTransition(label = "AntLoading")
  val rotation by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(1000, easing = LinearEasing)), label = "rotation")
  Canvas(modifier.size(size).semantics { progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate }) {
    val stroke = this.size.minDimension * 0.12f
    rotate(rotation) {
      drawArc(
        color = color,
        startAngle = -90f,
        sweepAngle = 270f,
        useCenter = false,
        topLeft = Offset(stroke / 2, stroke / 2),
        size = this.size.copy(width = this.size.width - stroke, height = this.size.height - stroke),
        style = Stroke(stroke, cap = StrokeCap.Round),
      )
    }
  }
}
