package dev.josueamaya.pourline.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import dev.josueamaya.pourline.ui.theme.AntTheme

/** `Typography.Text type`. */
enum class AntTextType { Default, Secondary, Success, Warning, Danger, Disabled }

@Composable
@ReadOnlyComposable
fun antTextColor(type: AntTextType): Color {
  val colors = AntTheme.colors
  return when (type) {
    AntTextType.Default -> colors.text
    AntTextType.Secondary -> colors.textTertiary
    AntTextType.Success -> colors.success.text
    AntTextType.Warning -> colors.warning.text
    AntTextType.Danger -> colors.error.text
    AntTextType.Disabled -> colors.textDisabled
  }
}

/** `Typography.Text`. */
@Composable
fun AntText(
  text: String,
  modifier: Modifier = Modifier,
  type: AntTextType = AntTextType.Default,
  style: TextStyle = AntTheme.typography.text,
  strong: Boolean = false,
  textAlign: TextAlign = TextAlign.Unspecified,
  maxLines: Int = Int.MAX_VALUE,
  overflow: TextOverflow = TextOverflow.Clip,
) {
  Text(
    text = text,
    modifier = modifier,
    color = antTextColor(type),
    style = if (strong) style.copy(fontWeight = FontWeight.SemiBold) else style,
    textAlign = textAlign,
    maxLines = maxLines,
    overflow = overflow,
  )
}

/** `Typography.Title` con `level` 1–5. Se marca como encabezado para TalkBack. */
@Composable
fun AntTitle(
  text: String,
  modifier: Modifier = Modifier,
  level: Int = 1,
  type: AntTextType = AntTextType.Default,
  textAlign: TextAlign = TextAlign.Unspecified,
) {
  val typography = AntTheme.typography
  val style = when (level.coerceIn(1, 5)) {
    1 -> typography.heading1
    2 -> typography.heading2
    3 -> typography.heading3
    4 -> typography.heading4
    else -> typography.heading5
  }
  Text(
    text = text,
    modifier = modifier.semantics { heading() },
    color = antTextColor(type),
    style = style,
    textAlign = textAlign,
  )
}
