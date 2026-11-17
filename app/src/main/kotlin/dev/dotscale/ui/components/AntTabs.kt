package dev.dotscale.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.dotscale.ui.theme.AntTheme

@Immutable
data class AntTabItem<T>(val key: T, val label: String, val testTag: String)

private val TabHeight = 48.dp
private val IndicatorHeight = 2.dp

/**
 * `Tabs` de Ant Design (pestañas de línea): las pestañas comparten el ancho y la elegida lleva un indicador
 * inferior de 2 dp en `primary.base`. Marcan `Role.Tab` y `selected` en la semántica.
 */
@Composable
fun <T> AntTabs(
  items: List<AntTabItem<T>>,
  selected: T,
  onSelect: (T) -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = AntTheme.colors
  Box(modifier.fillMaxWidth()) {
    Box(Modifier.fillMaxWidth().height(1.dp).background(colors.split).align(Alignment.BottomStart))
    Row(Modifier.fillMaxWidth().selectableGroup()) {
      items.forEach { item ->
        val isSelected = item.key == selected
        Box(
          Modifier
            .weight(1f)
            .height(TabHeight)
            .testTag(item.testTag)
            .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(item.key) }),
          contentAlignment = Alignment.Center,
        ) {
          Text(
            item.label,
            Modifier.padding(horizontal = AntTheme.spacing.xs),
            color = if (isSelected) colors.primary.text else colors.textSecondary,
            style = AntTheme.typography.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          if (isSelected) {
            Box(Modifier.fillMaxWidth().height(IndicatorHeight).background(colors.primary.base).align(Alignment.BottomCenter))
          }
        }
      }
    }
  }
}
