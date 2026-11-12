package dev.dotscale.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.dotscale.ui.theme.AntColorRole
import dev.dotscale.ui.theme.AntColors
import dev.dotscale.ui.theme.AntTheme

/** Tipo semántico compartido por Alert, Message, Result… */
enum class AntStatus { Success, Info, Warning, Error }

fun AntColors.roleOf(status: AntStatus): AntColorRole = when (status) {
  AntStatus.Success -> success
  AntStatus.Info -> info
  AntStatus.Warning -> warning
  AntStatus.Error -> error
}

/**
 * Icono relleno de estado (CheckCircleFilled, InfoCircleFilled, ExclamationCircleFilled,
 * CloseCircleFilled). El símbolo se recorta del círculo, así que siempre muestra el fondo de debajo.
 */
@Composable
fun AntStatusIcon(
  status: AntStatus,
  modifier: Modifier = Modifier,
  size: Dp = 16.dp,
  color: Color = AntTheme.colors.roleOf(status).base,
) {
  Canvas(modifier.size(size).graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)) {
    val w = this.size.minDimension
    val stroke = w * 0.1f
    drawCircle(color)
    fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(
      color = Color.Black,
      start = Offset(x1 * w, y1 * w),
      end = Offset(x2 * w, y2 * w),
      strokeWidth = stroke,
      cap = StrokeCap.Round,
      blendMode = BlendMode.Clear,
    )
    fun dot(x: Float, y: Float) =
      drawCircle(Color.Black, radius = stroke * 0.65f, center = Offset(x * w, y * w), blendMode = BlendMode.Clear)
    when (status) {
      AntStatus.Success -> {
        val check = Path().apply {
          moveTo(0.29f * w, 0.52f * w)
          lineTo(0.44f * w, 0.66f * w)
          lineTo(0.71f * w, 0.38f * w)
        }
        drawPath(check, Color.Black, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round), blendMode = BlendMode.Clear)
      }
      AntStatus.Info -> {
        dot(0.5f, 0.3f)
        line(0.5f, 0.45f, 0.5f, 0.72f)
      }
      AntStatus.Warning -> {
        line(0.5f, 0.27f, 0.5f, 0.56f)
        dot(0.5f, 0.71f)
      }
      AntStatus.Error -> {
        line(0.36f, 0.36f, 0.64f, 0.64f)
        line(0.64f, 0.36f, 0.36f, 0.64f)
      }
    }
  }
}
