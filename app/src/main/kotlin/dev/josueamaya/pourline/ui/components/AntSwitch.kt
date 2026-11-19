package dev.josueamaya.pourline.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.josueamaya.pourline.ui.theme.AntTheme

/** `Switch size`. */
enum class AntSwitchSize(
  internal val width: Dp,
  internal val height: Dp,
  internal val handle: Dp,
  internal val stretch: Dp,
) {
  Small(width = 28.dp, height = 16.dp, handle = 12.dp, stretch = 4.dp),
  Default(width = 44.dp, height = 24.dp, handle = 20.dp, stretch = 6.dp),
}

private val SwitchPadding = 2.dp
private val Pill = RoundedCornerShape(percent = 50)

/**
 * `Switch` de Ant Design: pista azul cuando está activo y gris translúcida cuando no; al pulsar el
 * tirador se estira como en antd. Con `onCheckedChange = null` es solo visual (cuando la fila entera
 * es la que alterna el valor).
 */
@Composable
fun AntSwitch(
  checked: Boolean,
  onCheckedChange: ((Boolean) -> Unit)?,
  modifier: Modifier = Modifier,
  size: AntSwitchSize = AntSwitchSize.Default,
  enabled: Boolean = true,
  loading: Boolean = false,
) {
  val colors = AntTheme.colors
  val motion = AntTheme.motion
  val interactionSource = remember { MutableInteractionSource() }
  val pressed by interactionSource.collectIsPressedAsState()
  val track by animateColorAsState(
    when {
      checked && pressed -> colors.primary.active
      checked -> colors.primary.base
      pressed -> colors.textTertiary
      else -> colors.textQuaternary
    },
    tween(motion.durationMid),
    label = "AntSwitchTrack",
  )
  val targetHandleWidth = size.handle + if (pressed && !loading) size.stretch else 0.dp
  val handleWidth by animateDpAsState(targetHandleWidth, tween(motion.durationMid, easing = motion.easeInOut), label = "AntSwitchHandleWidth")
  val handleOffset by animateDpAsState(
    if (checked) size.width - SwitchPadding - targetHandleWidth else SwitchPadding,
    tween(motion.durationMid, easing = motion.easeInOut),
    label = "AntSwitchHandleOffset",
  )

  Box(
    modifier
      .then(
        if (onCheckedChange != null) {
          Modifier
            .minimumInteractiveComponentSize()
            .toggleable(
              value = checked,
              interactionSource = interactionSource,
              indication = null,
              enabled = enabled && !loading,
              role = Role.Switch,
              onValueChange = onCheckedChange,
            )
        } else {
          Modifier
        },
      )
      .alpha(if (enabled) 1f else 0.65f)
      .size(size.width, size.height)
      .clip(Pill)
      .background(track),
  ) {
    Box(
      Modifier
        .align(Alignment.CenterStart)
        .offset { IntOffset(handleOffset.roundToPx(), 0) }
        .size(width = handleWidth, height = size.handle)
        .shadow(2.dp, Pill)
        .background(Color.White, Pill),
      contentAlignment = Alignment.Center,
    ) {
      if (loading) {
        AntLoadingIcon(size = size.handle * 0.6f, color = if (checked) colors.primary.base else Color.Black.copy(alpha = 0.45f))
      }
    }
  }
}
