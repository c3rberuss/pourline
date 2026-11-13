package dev.dotscale.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.dotscale.R
import dev.dotscale.ui.theme.AntTheme

/**
 * Un panel del `Collapse` de Ant Design, sin estado propio: [expanded] y [onExpandedChange] los lleva el
 * llamador. La cabecera (título y [extra] a la derecha) es un botón; el contenido no se compone plegado.
 *
 * @param testTag la cabecera es `"${testTag}_header"` y el contenido, `"${testTag}_content"`.
 */
@Composable
fun AntCollapse(
  title: String,
  expanded: Boolean,
  onExpandedChange: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
  extra: String? = null,
  testTag: String = "ant_collapse",
  content: @Composable ColumnScope.() -> Unit,
) {
  val colors = AntTheme.colors
  val motion = AntTheme.motion
  val shape = RoundedCornerShape(AntTheme.radius.lg)
  val rotation by animateFloatAsState(
    if (expanded) 90f else 0f,
    tween(motion.durationMid, easing = motion.easeInOut),
    label = "AntCollapseChevron",
  )
  val stateText = stringResource(if (expanded) R.string.collapse_expanded else R.string.collapse_collapsed)
  val actionLabel = stringResource(if (expanded) R.string.collapse_collapse else R.string.collapse_expand)

  Column(modifier.clip(shape).antBorder(colors.border, AntTheme.radius.lg)) {
    Row(
      Modifier
        .fillMaxWidth()
        .background(colors.fillQuaternary)
        .heightIn(min = 48.dp)
        .clickable(role = Role.Button, onClickLabel = actionLabel) { onExpandedChange(!expanded) }
        .semantics { stateDescription = stateText }
        .padding(horizontal = AntTheme.spacing.base, vertical = AntTheme.spacing.xs)
        .testTag("${testTag}_header"),
      horizontalArrangement = Arrangement.spacedBy(AntTheme.spacing.xs),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        AntIcons.Right,
        contentDescription = null,
        modifier = Modifier.size(14.dp).rotate(rotation),
        tint = colors.textSecondary,
      )
      Text(
        title,
        modifier = Modifier.weight(1f),
        color = colors.text,
        style = AntTheme.typography.text.copy(fontWeight = FontWeight.SemiBold),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      if (extra != null) {
        Text(
          extra,
          color = colors.textSecondary,
          style = AntTheme.typography.textSM,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
    AnimatedVisibility(
      visible = expanded,
      enter = expandVertically(tween(motion.durationMid)) + fadeIn(tween(motion.durationMid)),
      exit = shrinkVertically(tween(motion.durationMid)) + fadeOut(tween(motion.durationMid)),
    ) {
      Column(Modifier.fillMaxWidth()) {
        AntDivider()
        Column(
          Modifier
            .fillMaxWidth()
            .background(colors.bgContainer)
            .padding(AntTheme.spacing.base)
            .testTag("${testTag}_content"),
          content = content,
        )
      }
    }
  }
}
