package dev.josueamaya.pourline.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.josueamaya.pourline.R
import dev.josueamaya.pourline.ui.theme.AntSize
import dev.josueamaya.pourline.ui.theme.AntTheme

/**
 * `Alert` de Ant Design. Con [description] usa el formato grande (icono de 24 dp); en ese caso la
 * [action] se coloca debajo del texto para no apretar el contenido en pantallas estrechas.
 * El mensaje se anuncia como live region, útil para estados que cambian (conexión, errores).
 */
@Composable
fun AntAlert(
  message: String,
  modifier: Modifier = Modifier,
  type: AntStatus = AntStatus.Info,
  description: String? = null,
  showIcon: Boolean = true,
  icon: (@Composable () -> Unit)? = null,
  banner: Boolean = false,
  action: (@Composable () -> Unit)? = null,
  onClose: (() -> Unit)? = null,
) {
  val colors = AntTheme.colors
  val spacing = AntTheme.spacing
  val typography = AntTheme.typography
  val role = colors.roleOf(type)
  val hasDescription = description != null
  val shape = if (banner) RectangleShape else RoundedCornerShape(AntTheme.radius.lg)
  val iconSize = if (hasDescription) 24.dp else 16.dp

  Row(
    modifier
      .fillMaxWidth()
      .background(role.bg, shape)
      .thenIf(!banner) { border(1.dp, role.border, shape) }
      .padding(
        horizontal = if (hasDescription) spacing.base else spacing.sm,
        vertical = if (hasDescription) spacing.base else spacing.xs,
      ),
    horizontalArrangement = Arrangement.spacedBy(if (hasDescription) spacing.sm else spacing.xs),
    verticalAlignment = if (hasDescription) Alignment.Top else Alignment.CenterVertically,
  ) {
    if (showIcon) {
      Box(Modifier.size(iconSize), contentAlignment = Alignment.Center) {
        if (icon != null) icon() else AntStatusIcon(type, size = iconSize)
      }
    }
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
      Text(
        text = message,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        color = colors.text,
        style = if (hasDescription) typography.textLG else typography.text,
      )
      description?.let { Text(it, color = colors.textSecondary, style = typography.text) }
      if (hasDescription && action != null) {
        Box(Modifier.padding(top = spacing.xs)) { action() }
      }
    }
    if (!hasDescription) action?.invoke()
    onClose?.let {
      AntIconButton(AntIcons.Close, contentDescription = stringResource(R.string.action_close), onClick = it, size = AntSize.Small)
    }
  }
}
