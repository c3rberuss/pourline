package dev.dotscale.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import dev.dotscale.ui.theme.AntSize
import dev.dotscale.ui.theme.AntTheme

/** `Input status`. */
enum class AntInputStatus { Default, Warning, Error }

private val FocusRing = 2.dp

/**
 * `Input` de Ant Design: borde que pasa al color primario (o al de [status]) con el foco, más el halo
 * exterior `controlOutline`. Admite [prefix]/[suffix] como antd.
 */
@Composable
fun AntInput(
  value: String,
  onValueChange: (String) -> Unit,
  modifier: Modifier = Modifier,
  placeholder: String? = null,
  prefix: (@Composable () -> Unit)? = null,
  suffix: (@Composable () -> Unit)? = null,
  size: AntSize = AntSize.Middle,
  status: AntInputStatus = AntInputStatus.Default,
  enabled: Boolean = true,
  singleLine: Boolean = true,
  /** Líneas visibles mínimas con `singleLine = false` (como `TextArea` de antd). */
  minLines: Int = 1,
  keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
  keyboardActions: KeyboardActions = KeyboardActions.Default,
  visualTransformation: VisualTransformation = VisualTransformation.None,
) {
  val colors = AntTheme.colors
  val radius = AntTheme.radius.of(size)
  val shape = RoundedCornerShape(radius)
  val interactionSource = remember { MutableInteractionSource() }
  val focused by interactionSource.collectIsFocusedAsState()
  val accent = when (status) {
    AntInputStatus.Default -> colors.primary
    AntInputStatus.Warning -> colors.warning
    AntInputStatus.Error -> colors.error
  }
  val borderColor by animateColorAsState(
    when {
      !enabled -> colors.border
      focused || status != AntInputStatus.Default -> accent.base
      else -> colors.border
    },
    tween(AntTheme.motion.durationMid),
    label = "AntInputBorder",
  )
  val ringColor = if (focused && enabled) accent.base.copy(alpha = 0.15f) else Color.Transparent
  val textColor = if (enabled) colors.text else colors.textDisabled
  val textStyle = (if (size == AntSize.Large) AntTheme.typography.textLG else AntTheme.typography.text).copy(color = textColor)

  BasicTextField(
    value = value,
    onValueChange = onValueChange,
    modifier = modifier.minimumInteractiveComponentSize(),
    enabled = enabled,
    textStyle = textStyle,
    keyboardOptions = keyboardOptions,
    keyboardActions = keyboardActions,
    singleLine = singleLine,
    minLines = if (singleLine) 1 else minLines,
    visualTransformation = visualTransformation,
    interactionSource = interactionSource,
    cursorBrush = SolidColor(accent.base),
    decorationBox = { innerTextField ->
      Row(
        Modifier
          .thenIf(singleLine) { height(AntTheme.controlHeight.of(size)) }
          .thenIf(!singleLine) { heightIn(min = AntTheme.controlHeight.of(size)) }
          .drawBehind {
            if (ringColor.alpha > 0f) {
              val ring = FocusRing.toPx()
              drawRoundRect(
                color = ringColor,
                topLeft = Offset(-ring, -ring),
                size = Size(this.size.width + ring * 2, this.size.height + ring * 2),
                cornerRadius = CornerRadius(radius.toPx() + ring),
              )
            }
          }
          .background(if (enabled) colors.bgContainer else colors.bgContainerDisabled, shape)
          .antBorder(borderColor, radius)
          .padding(horizontal = AntTheme.spacing.sm, vertical = if (singleLine) 0.dp else AntTheme.spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(AntTheme.spacing.xxs),
        verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
      ) {
        prefix?.invoke()
        Box(Modifier.weight(1f), contentAlignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart) {
          if (value.isEmpty() && placeholder != null) {
            Text(placeholder, color = colors.textPlaceholder, style = textStyle, maxLines = 1)
          }
          innerTextField()
        }
        suffix?.invoke()
      }
    },
  )
}
