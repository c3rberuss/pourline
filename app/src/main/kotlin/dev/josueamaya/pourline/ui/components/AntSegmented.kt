package dev.josueamaya.pourline.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.josueamaya.pourline.ui.theme.AntSize
import dev.josueamaya.pourline.ui.theme.AntTheme

@Immutable
data class AntSegmentedOption<T>(val value: T, val label: String, val icon: ImageVector? = null)

private val TrackPadding = 2.dp

private val IconOnlyItemWidth = 48.dp
private val IconOnlyIconSize = 20.dp

/**
 * `Segmented` de Ant Design: opciones del mismo ancho con un indicador que se desliza a la opción
 * elegida. Necesita un ancho acotado (por ejemplo `fillMaxWidth()` o `width(…)`).
 *
 * Con [iconOnly] cada opción es solo su icono (obligatorio), de 20 dp, y [AntSegmentedOption.label] pasa a ser su
 * descripción para TalkBack. El componente toma entonces su propio ancho: 48 dp por opción más el relleno de la pista.
 */
@Composable
fun <T> AntSegmented(
  options: List<AntSegmentedOption<T>>,
  selected: T,
  onSelect: (T) -> Unit,
  modifier: Modifier = Modifier,
  size: AntSize = AntSize.Middle,
  enabled: Boolean = true,
  iconOnly: Boolean = false,
) {
  if (iconOnly) options.forEach { requireNotNull(it.icon) { "iconOnly exige un icono en cada opción" } }
  val colors = AntTheme.colors
  val motion = AntTheme.motion
  val radius = AntTheme.radius.of(size)
  val trackShape = RoundedCornerShape(radius)
  val thumbShape = RoundedCornerShape((radius - TrackPadding).coerceAtLeast(0.dp))
  val density = LocalDensity.current
  var trackWidthPx by remember { mutableIntStateOf(0) }
  val selectedIndex = options.indexOfFirst { it.value == selected }.coerceAtLeast(0)
  val itemWidth = with(density) { (trackWidthPx / options.size.coerceAtLeast(1)).toDp() }
  // Se anima el índice (no el offset) para que la primera medición no provoque un deslizamiento.
  val animatedIndex by animateFloatAsState(
    selectedIndex.toFloat(),
    tween(motion.durationSlow, easing = motion.easeInOut),
    label = "AntSegmentedThumb",
  )

  val ownWidth = if (iconOnly) Modifier.width(IconOnlyItemWidth * options.size + TrackPadding * 2) else Modifier
  Box(
    modifier
      .then(ownWidth)
      .minimumInteractiveComponentSize()
      .height(AntTheme.controlHeight.of(size))
      .clip(trackShape)
      .background(colors.bgLayout, trackShape)
      .padding(TrackPadding)
      .onSizeChanged { trackWidthPx = it.width },
  ) {
    if (trackWidthPx > 0) {
      Box(
        Modifier
          .offset { IntOffset((itemWidth * animatedIndex).roundToPx(), 0) }
          .width(itemWidth)
          .fillMaxHeight()
          .shadow(1.dp, thumbShape)
          .background(colors.bgElevated, thumbShape),
      )
    }
    Row(Modifier.fillMaxSize().selectableGroup()) {
      options.forEachIndexed { index, option ->
        val isSelected = index == selectedIndex
        val contentColor by animateColorAsState(
          when {
            !enabled -> colors.textDisabled
            isSelected -> colors.text
            else -> colors.textSecondary
          },
          tween(motion.durationMid),
          label = "AntSegmentedItem",
        )
        Row(
          Modifier
            .weight(1f)
            .fillMaxHeight()
            .clip(thumbShape)
            .selectable(
              selected = isSelected,
              interactionSource = null,
              indication = null,
              enabled = enabled,
              role = Role.RadioButton,
              onClick = { onSelect(option.value) },
            )
            .then(if (iconOnly) Modifier.semantics { contentDescription = option.label } else Modifier)
            .padding(horizontal = AntTheme.spacing.xs),
          horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          option.icon?.let {
            Icon(it, contentDescription = null, modifier = Modifier.size(if (iconOnly) IconOnlyIconSize else 16.dp), tint = contentColor)
          }
          if (!iconOnly) Text(option.label, color = contentColor, style = AntTheme.typography.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
      }
    }
  }
}
