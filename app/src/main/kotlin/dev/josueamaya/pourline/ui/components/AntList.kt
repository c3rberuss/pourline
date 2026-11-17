package dev.josueamaya.pourline.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.josueamaya.pourline.ui.theme.AntTheme

/**
 * `List.Item` con `List.Item.Meta` (avatar, título y descripción) y una zona [extra] a la derecha.
 * Pensado para usarse dentro de `LazyColumn`; [divider] dibuja la línea inferior de antd.
 */
@Composable
fun AntListItem(
  title: String,
  modifier: Modifier = Modifier,
  description: String? = null,
  avatar: (@Composable () -> Unit)? = null,
  extra: (@Composable () -> Unit)? = null,
  divider: Boolean = true,
  onClick: (() -> Unit)? = null,
) {
  val colors = AntTheme.colors
  val spacing = AntTheme.spacing
  val interactionSource = remember { MutableInteractionSource() }
  val pressed by interactionSource.collectIsPressedAsState()
  val background by animateColorAsState(
    if (pressed) colors.fillTertiary else Color.Transparent,
    tween(AntTheme.motion.durationFast),
    label = "AntListItemBackground",
  )
  val splitColor = colors.split

  Row(
    modifier
      .fillMaxWidth()
      .then(
        if (onClick != null) {
          Modifier.clickable(interactionSource, indication = null, onClick = onClick)
        } else {
          Modifier.semantics(mergeDescendants = true) {}
        },
      )
      .background(background)
      .drawBehind {
        if (divider) {
          val y = size.height - 0.5.dp.toPx()
          drawLine(splitColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
        }
      }
      .heightIn(min = 56.dp)
      .padding(horizontal = spacing.base, vertical = spacing.sm),
    horizontalArrangement = Arrangement.spacedBy(spacing.base),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    avatar?.invoke()
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
      Text(title, color = colors.text, style = AntTheme.typography.textLG, maxLines = 1, overflow = TextOverflow.Ellipsis)
      description?.let {
        Text(it, color = colors.textTertiary, style = AntTheme.typography.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
      }
    }
    extra?.invoke()
  }
}
