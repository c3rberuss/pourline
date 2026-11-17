package dev.josueamaya.pourline.ui.components

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import dev.josueamaya.pourline.ui.theme.AntTheme

/** `Badge status`. */
enum class AntBadgeStatus { Success, Processing, Default, Error, Warning }

/**
 * `Badge` en modo estado: punto de color con texto opcional. `Processing` emite una onda continua,
 * útil para indicar actividad (escaneando, cronómetro en marcha…).
 */
@Composable
fun AntBadge(
  status: AntBadgeStatus,
  modifier: Modifier = Modifier,
  text: String? = null,
) {
  val colors = AntTheme.colors
  val dotColor = when (status) {
    AntBadgeStatus.Success -> colors.success.base
    AntBadgeStatus.Processing -> colors.primary.base
    AntBadgeStatus.Default -> colors.textQuaternary
    AntBadgeStatus.Error -> colors.error.base
    AntBadgeStatus.Warning -> colors.warning.base
  }
  Row(modifier, horizontalArrangement = Arrangement.spacedBy(AntTheme.spacing.xs), verticalAlignment = Alignment.CenterVertically) {
    StatusDot(dotColor, processing = status == AntBadgeStatus.Processing)
    text?.let { Text(it, color = colors.text, style = AntTheme.typography.text) }
  }
}

@Composable
private fun StatusDot(color: Color, processing: Boolean) {
  val ripple = if (processing) {
    rememberInfiniteTransition(label = "AntBadge")
      .animateFloat(0f, 1f, infiniteRepeatable(tween(1200, easing = AntTheme.motion.easeInOut)), label = "ripple")
  } else {
    null
  }
  Canvas(Modifier.size(6.dp)) {
    val radius = size.minDimension / 2
    ripple?.value?.let { t ->
      drawCircle(
        color = color.copy(alpha = color.alpha * 0.5f * (1f - t)),
        radius = radius * (0.8f + 1.6f * t),
        style = Stroke(1.dp.toPx()),
      )
    }
    drawCircle(color, radius)
  }
}
