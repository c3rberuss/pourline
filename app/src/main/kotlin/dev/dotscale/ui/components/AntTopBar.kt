package dev.dotscale.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.dotscale.ui.theme.AntTheme

/**
 * Cabecera de página (`Layout.Header` / `PageHeader` de antd): fondo de contenedor, título con
 * subtítulo opcional y línea inferior. Respeta la barra de estado (edge-to-edge).
 */
@Composable
fun AntTopBar(
  title: String,
  modifier: Modifier = Modifier,
  subtitle: String? = null,
  navigationIcon: (@Composable () -> Unit)? = null,
  actions: @Composable RowScope.() -> Unit = {},
) {
  val colors = AntTheme.colors
  val spacing = AntTheme.spacing
  Column(
    modifier
      .fillMaxWidth()
      .background(colors.bgContainer)
      .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
  ) {
    Row(
      Modifier
        .fillMaxWidth()
        .height(56.dp)
        .padding(start = if (navigationIcon != null) spacing.xxs else spacing.base, end = spacing.xxs),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      navigationIcon?.invoke()
      Column(Modifier.weight(1f).padding(start = if (navigationIcon != null) spacing.xxs else 0.dp)) {
        Text(
          text = title,
          modifier = Modifier.semantics { heading() },
          color = colors.text,
          style = AntTheme.typography.heading5,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        subtitle?.let {
          Text(it, color = colors.textTertiary, style = AntTheme.typography.textSM, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
      }
      Row(verticalAlignment = Alignment.CenterVertically, content = actions)
    }
    AntDivider()
  }
}
