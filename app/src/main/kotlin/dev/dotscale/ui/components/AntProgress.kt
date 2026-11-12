package dev.dotscale.ui.components

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.dotscale.ui.theme.AntTheme
import kotlin.math.roundToInt

/** `Progress status`. */
enum class AntProgressStatus { Normal, Active, Success, Exception }

/**
 * `Progress` en línea. [percent] va de 0 a 100; al llegar a 100 pasa a estado de éxito como en antd.
 * `Active` añade el brillo que recorre la barra.
 */
@Composable
fun AntProgress(
  percent: Float,
  modifier: Modifier = Modifier,
  status: AntProgressStatus = AntProgressStatus.Normal,
  showInfo: Boolean = true,
  strokeWidth: Dp = 8.dp,
) {
  val colors = AntTheme.colors
  val motion = AntTheme.motion
  val clamped = percent.coerceIn(0f, 100f)
  val resolved = if (clamped >= 100f && (status == AntProgressStatus.Normal || status == AntProgressStatus.Active)) {
    AntProgressStatus.Success
  } else {
    status
  }
  val barColor = when (resolved) {
    AntProgressStatus.Success -> colors.success.base
    AntProgressStatus.Exception -> colors.error.base
    else -> colors.info.base
  }
  val trackColor = colors.fillSecondary
  val fraction by animateFloatAsState(clamped / 100f, tween(motion.durationSlow, easing = motion.easeOut), label = "AntProgress")
  val shine = if (resolved == AntProgressStatus.Active) {
    rememberInfiniteTransition(label = "AntProgressActive")
      .animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = motion.easeOut)), label = "shine")
  } else {
    null
  }

  Row(
    modifier.semantics { progressBarRangeInfo = ProgressBarRangeInfo(clamped / 100f, 0f..1f) },
    horizontalArrangement = Arrangement.spacedBy(AntTheme.spacing.xs),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Canvas(Modifier.weight(1f).height(strokeWidth)) {
      val corner = CornerRadius(size.height / 2)
      drawRoundRect(trackColor, cornerRadius = corner)
      val width = size.width * fraction
      if (width > 0f) {
        drawRoundRect(barColor, size = Size(width, size.height), cornerRadius = corner)
        shine?.value?.let { t ->
          drawRoundRect(Color.White.copy(alpha = 0.4f * (1f - t)), size = Size(width * t, size.height), cornerRadius = corner)
        }
      }
    }
    if (showInfo) {
      when (resolved) {
        AntProgressStatus.Success -> AntStatusIcon(AntStatus.Success)
        AntProgressStatus.Exception -> AntStatusIcon(AntStatus.Error)
        else -> Text(
          text = "${clamped.roundToInt()}%",
          modifier = Modifier.widthIn(min = 36.dp),
          color = colors.text,
          style = AntTheme.typography.text,
          textAlign = TextAlign.End,
        )
      }
    }
  }
}
