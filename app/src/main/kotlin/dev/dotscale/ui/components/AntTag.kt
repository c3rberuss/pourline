package dev.dotscale.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.dotscale.R
import dev.dotscale.ui.theme.AntColors
import dev.dotscale.ui.theme.AntTheme

/** Colores predefinidos de `Tag` (los de estado más el primario). */
enum class AntTagColor { Default, Primary, Success, Warning, Error }

private val TagHeight = 24.dp
private val TagIconSize = 12.dp

/**
 * `Tag` (el equivalente Ant Design de un chip). Si recibe [onClick] se comporta como chip de acción y
 * amplía su área táctil a 48 dp; con [onClose] muestra el aspa de cierre.
 */
@Composable
fun AntTag(
  text: String,
  modifier: Modifier = Modifier,
  color: AntTagColor = AntTagColor.Default,
  icon: ImageVector? = null,
  bordered: Boolean = true,
  onClick: (() -> Unit)? = null,
  onClose: (() -> Unit)? = null,
) {
  val colors = AntTheme.colors
  val (bg, border, fg) = tagColors(colors, color)
  val shape = RoundedCornerShape(AntTheme.radius.sm)
  Row(
    modifier
      .thenIf(onClick != null) { minimumInteractiveComponentSize() }
      .height(TagHeight)
      .clip(shape)
      .background(bg, shape)
      .thenIf(bordered) { border(1.dp, border, shape) }
      .thenIf(onClick != null) { clickable(role = Role.Button, onClick = onClick ?: {}) }
      .padding(horizontal = AntTheme.spacing.xs),
    horizontalArrangement = Arrangement.spacedBy(AntTheme.spacing.xxs),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    icon?.let { Icon(it, contentDescription = null, modifier = Modifier.size(TagIconSize), tint = fg) }
    Text(text, color = fg, style = AntTheme.typography.textSM, maxLines = 1)
    if (onClose != null) {
      Icon(
        AntIcons.Close,
        contentDescription = stringResource(R.string.action_close),
        modifier = Modifier.size(TagIconSize).clickable(role = Role.Button, onClick = onClose),
        tint = colors.textTertiary,
      )
    }
  }
}

/** `Tag.CheckableTag`: chip seleccionable (filtros, opciones múltiples). */
@Composable
fun AntCheckableTag(
  text: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
  icon: ImageVector? = null,
  enabled: Boolean = true,
) {
  val colors = AntTheme.colors
  val interactionSource = remember { MutableInteractionSource() }
  val pressed by interactionSource.collectIsPressedAsState()
  val bg by animateColorAsState(
    when {
      !enabled && checked -> colors.fill
      checked && pressed -> colors.primary.active
      checked -> colors.primary.base
      pressed -> colors.fillSecondary
      else -> Color.Transparent
    },
    tween(AntTheme.motion.durationMid),
    label = "AntCheckableTag",
  )
  val fg = when {
    !enabled -> colors.textDisabled
    checked -> colors.textLightSolid
    else -> colors.text
  }
  val shape = RoundedCornerShape(AntTheme.radius.sm)
  Row(
    modifier
      .minimumInteractiveComponentSize()
      .height(TagHeight)
      .clip(shape)
      .background(bg, shape)
      .toggleable(
        value = checked,
        interactionSource = interactionSource,
        indication = null,
        enabled = enabled,
        role = Role.Checkbox,
        onValueChange = onCheckedChange,
      )
      .padding(horizontal = AntTheme.spacing.xs),
    horizontalArrangement = Arrangement.spacedBy(AntTheme.spacing.xxs),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    icon?.let { Icon(it, contentDescription = null, modifier = Modifier.size(TagIconSize), tint = fg) }
    Text(text, color = fg, style = AntTheme.typography.textSM, maxLines = 1)
  }
}

private fun tagColors(c: AntColors, color: AntTagColor): Triple<Color, Color, Color> = when (color) {
  AntTagColor.Default -> Triple(c.fillQuaternary, c.border, c.text)
  AntTagColor.Primary -> Triple(c.primary.bg, c.primary.border, c.primary.text)
  AntTagColor.Success -> Triple(c.success.bg, c.success.border, c.success.text)
  AntTagColor.Warning -> Triple(c.warning.bg, c.warning.border, c.warning.text)
  AntTagColor.Error -> Triple(c.error.bg, c.error.border, c.error.text)
}
