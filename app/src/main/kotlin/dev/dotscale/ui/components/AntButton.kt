package dev.dotscale.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.dotscale.ui.theme.AntColors
import dev.dotscale.ui.theme.AntSize
import dev.dotscale.ui.theme.AntTheme

/** `Button type`. */
enum class AntButtonType { Primary, Default, Dashed, Text, Link }

/** `Button shape`. */
enum class AntButtonShape { Default, Round, Circle }

/**
 * `Button` de Ant Design. Sin ripple: el feedback táctil es el cambio a los colores `active`, como en antd.
 * Mientras `loading` es true muestra el spinner y no acepta pulsaciones.
 */
@Composable
fun AntButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  type: AntButtonType = AntButtonType.Default,
  size: AntSize = AntSize.Middle,
  shape: AntButtonShape = AntButtonShape.Default,
  danger: Boolean = false,
  enabled: Boolean = true,
  loading: Boolean = false,
  icon: ImageVector? = null,
) {
  val textStyle = if (size == AntSize.Large) AntTheme.typography.textLG else AntTheme.typography.text
  AntButtonBase(onClick, modifier, type, size, shape, danger, enabled, loading, iconOnly = false) { color, iconSize ->
    if (loading) {
      AntLoadingIcon(size = iconSize, color = color)
    } else if (icon != null) {
      Icon(icon, contentDescription = null, modifier = Modifier.size(iconSize), tint = color)
    }
    Text(text, color = color, style = textStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
  }
}

/** Botón solo con icono. [contentDescription] es obligatorio para TalkBack salvo que sea decorativo. */
@Composable
fun AntIconButton(
  icon: ImageVector,
  contentDescription: String?,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  type: AntButtonType = AntButtonType.Text,
  size: AntSize = AntSize.Middle,
  shape: AntButtonShape = AntButtonShape.Circle,
  danger: Boolean = false,
  enabled: Boolean = true,
  loading: Boolean = false,
) {
  AntButtonBase(onClick, modifier, type, size, shape, danger, enabled, loading, iconOnly = true) { color, iconSize ->
    if (loading) {
      AntLoadingIcon(size = iconSize, color = color)
    } else {
      Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(iconSize), tint = color)
    }
  }
}

@Composable
private fun AntButtonBase(
  onClick: () -> Unit,
  modifier: Modifier,
  type: AntButtonType,
  size: AntSize,
  shape: AntButtonShape,
  danger: Boolean,
  enabled: Boolean,
  loading: Boolean,
  iconOnly: Boolean,
  content: @Composable RowScope.(contentColor: Color, iconSize: Dp) -> Unit,
) {
  val motion = AntTheme.motion
  val spacing = AntTheme.spacing
  val interactionSource = remember { MutableInteractionSource() }
  val pressed by interactionSource.collectIsPressedAsState()
  val style = buttonStyle(AntTheme.colors, type, danger, enabled, pressed)
  val spec = tween<Color>(motion.durationMid, easing = motion.easeInOut)
  val container by animateColorAsState(style.container, spec, label = "AntButtonContainer")
  val contentColor by animateColorAsState(style.content, spec, label = "AntButtonContent")
  val borderColor by animateColorAsState(style.border, spec, label = "AntButtonBorder")

  val height = AntTheme.controlHeight.of(size)
  val radius = if (shape == AntButtonShape.Default) AntTheme.radius.of(size) else height / 2
  val cornerShape = RoundedCornerShape(radius)
  val horizontalPadding = when {
    iconOnly -> 0.dp
    size == AntSize.Small -> spacing.xs
    else -> spacing.base
  }
  val iconSize = if (size == AntSize.Large) 18.dp else 16.dp

  Row(
    modifier = modifier
      .minimumInteractiveComponentSize()
      .height(height)
      .then(if (iconOnly) Modifier.width(height) else Modifier.defaultMinSize(minWidth = height))
      .clip(cornerShape)
      .background(container, cornerShape)
      .antBorder(borderColor, radius, dashed = style.dashed)
      .clickable(
        interactionSource = interactionSource,
        indication = null,
        enabled = enabled && !loading,
        role = Role.Button,
        onClick = onClick,
      )
      .padding(horizontal = horizontalPadding),
    horizontalArrangement = Arrangement.spacedBy(spacing.xs, Alignment.CenterHorizontally),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    content(contentColor, iconSize)
  }
}

private data class ButtonStyle(
  val container: Color,
  val content: Color,
  val border: Color,
  val dashed: Boolean = false,
)

private fun buttonStyle(c: AntColors, type: AntButtonType, danger: Boolean, enabled: Boolean, pressed: Boolean): ButtonStyle {
  val accent = if (danger) c.error else c.primary
  val dashed = type == AntButtonType.Dashed
  if (!enabled) {
    return when (type) {
      AntButtonType.Text, AntButtonType.Link -> ButtonStyle(Color.Transparent, c.textDisabled, Color.Transparent)
      else -> ButtonStyle(c.bgContainerDisabled, c.textDisabled, c.border, dashed)
    }
  }
  return when (type) {
    AntButtonType.Primary -> {
      val bg = if (pressed) accent.active else accent.base
      ButtonStyle(bg, c.textLightSolid, bg)
    }
    AntButtonType.Default, AntButtonType.Dashed -> {
      val fg = when {
        pressed -> accent.active
        danger -> accent.base
        else -> c.text
      }
      val border = when {
        pressed -> accent.active
        danger -> accent.base
        else -> c.border
      }
      ButtonStyle(c.bgContainer, fg, border, dashed)
    }
    AntButtonType.Text -> {
      val fg = when {
        !danger -> c.text
        pressed -> accent.active
        else -> accent.base
      }
      ButtonStyle(if (pressed) c.fillSecondary else Color.Transparent, fg, Color.Transparent)
    }
    AntButtonType.Link -> ButtonStyle(Color.Transparent, if (pressed) accent.active else accent.base, Color.Transparent)
  }
}
