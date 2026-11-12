package dev.dotscale.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.dotscale.ui.theme.AntTheme

/** `Divider orientation` del texto. */
enum class AntDividerOrientation { Left, Center, Right }

/** `Divider` horizontal (`colorSplit`), opcionalmente discontinuo y con texto. Sin márgenes propios. */
@Composable
fun AntDivider(
  modifier: Modifier = Modifier,
  text: String? = null,
  orientation: AntDividerOrientation = AntDividerOrientation.Center,
  dashed: Boolean = false,
  plain: Boolean = false,
) {
  val colors = AntTheme.colors
  if (text == null) {
    Line(modifier.fillMaxWidth().height(1.dp), colors.split, dashed, vertical = false)
    return
  }
  val (startWeight, endWeight) = when (orientation) {
    AntDividerOrientation.Left -> 0.05f to 0.95f
    AntDividerOrientation.Center -> 1f to 1f
    AntDividerOrientation.Right -> 0.95f to 0.05f
  }
  Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    Line(Modifier.weight(startWeight).height(1.dp), colors.split, dashed, vertical = false)
    Text(
      text = text,
      modifier = Modifier.padding(horizontal = AntTheme.spacing.base),
      color = colors.text,
      style = if (plain) AntTheme.typography.text else AntTheme.typography.text.copy(fontWeight = FontWeight.Medium),
    )
    Line(Modifier.weight(endWeight).height(1.dp), colors.split, dashed, vertical = false)
  }
}

/** `Divider type="vertical"`. */
@Composable
fun AntVerticalDivider(
  modifier: Modifier = Modifier,
  height: Dp = 16.dp,
  dashed: Boolean = false,
) {
  Line(
    modifier.padding(horizontal = AntTheme.spacing.xs).width(1.dp).height(height),
    AntTheme.colors.split,
    dashed,
    vertical = true,
  )
}

@Composable
private fun Line(modifier: Modifier, color: Color, dashed: Boolean, vertical: Boolean) {
  Canvas(modifier) {
    val effect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())) else null
    if (vertical) {
      drawLine(color, Offset(center.x, 0f), Offset(center.x, size.height), strokeWidth = size.width, pathEffect = effect)
    } else {
      drawLine(color, Offset(0f, center.y), Offset(size.width, center.y), strokeWidth = size.height, pathEffect = effect)
    }
  }
}
