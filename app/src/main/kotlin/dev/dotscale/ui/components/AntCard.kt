package dev.dotscale.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.dotscale.ui.theme.AntTheme

/** `Card size`. */
enum class AntCardSize { Default, Small }

/**
 * `Card` de Ant Design: cabecera opcional con [title] y [extra] separada por una línea, y cuerpo con
 * padding. El cuerpo es un `ColumnScope`, así que admite `Modifier.weight` cuando la card tiene altura fija.
 */
@Composable
fun AntCard(
  modifier: Modifier = Modifier,
  title: String? = null,
  extra: (@Composable RowScope.() -> Unit)? = null,
  size: AntCardSize = AntCardSize.Default,
  bordered: Boolean = true,
  onClick: (() -> Unit)? = null,
  contentPadding: PaddingValues = PaddingValues(if (size == AntCardSize.Small) AntTheme.spacing.sm else AntTheme.spacing.base),
  verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(AntTheme.spacing.sm),
  horizontalAlignment: Alignment.Horizontal = Alignment.Start,
  content: @Composable ColumnScope.() -> Unit,
) {
  val colors = AntTheme.colors
  val typography = AntTheme.typography
  val shape = RoundedCornerShape(AntTheme.radius.lg)
  val interactionSource = remember { MutableInteractionSource() }
  val pressed by interactionSource.collectIsPressedAsState()
  val background by animateColorAsState(
    if (pressed) colors.fillTertiary.compositeOver(colors.bgContainer) else colors.bgContainer,
    tween(AntTheme.motion.durationFast),
    label = "AntCardBackground",
  )
  val headerPadding = if (size == AntCardSize.Small) AntTheme.spacing.sm else AntTheme.spacing.base

  Column(
    modifier
      .clip(shape)
      .background(background, shape)
      .thenIf(bordered) { border(1.dp, colors.borderSecondary, shape) }
      .thenIf(onClick != null) { clickable(interactionSource, indication = null, onClick = onClick ?: {}) },
  ) {
    if (title != null || extra != null) {
      Row(
        Modifier
          .fillMaxWidth()
          .heightIn(min = if (size == AntCardSize.Small) 44.dp else 56.dp)
          .padding(horizontal = headerPadding),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        if (title != null) {
          Text(
            text = title,
            modifier = Modifier.weight(1f).semantics { heading() },
            color = colors.text,
            style = (if (size == AntCardSize.Small) typography.text else typography.textLG).copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        } else {
          Spacer(Modifier.weight(1f))
        }
        extra?.invoke(this)
      }
      AntDivider()
    }
    Column(
      Modifier.padding(contentPadding),
      verticalArrangement = verticalArrangement,
      horizontalAlignment = horizontalAlignment,
      content = content,
    )
  }
}
