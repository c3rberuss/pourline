package dev.josueamaya.pourline.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.josueamaya.pourline.ui.theme.AntTheme

@Immutable
data class AntTabBarItem<T>(val id: T, val label: String, val icon: ImageVector, val testTag: String)

private val TabBarHeight = 56.dp

/**
 * Barra de pestañas inferior de móvil (`Tabs` de Ant Design): icono de 24 dp y texto, la pestaña activa en
 * `primary` y las demás en `textSecondary`. Suma el inset de la barra de navegación del sistema.
 */
@Composable
fun <T> AntTabBar(
  items: List<AntTabBarItem<T>>,
  selected: T,
  onSelect: (T) -> Unit,
  modifier: Modifier = Modifier,
  /** Un modificador extra para cada pestaña (el recorrido guiado pone aquí sus anclas). */
  itemModifier: (T) -> Modifier = { Modifier },
) {
  val colors = AntTheme.colors
  Column(
    modifier
      .fillMaxWidth()
      .background(colors.bgContainer)
      .windowInsetsPadding(WindowInsets.navigationBars),
  ) {
    AntDivider()
    Row(
      Modifier.fillMaxWidth().height(TabBarHeight).selectableGroup(),
      horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
      items.forEach { item ->
        val isSelected = item.id == selected
        val tint = if (isSelected) colors.primary.base else colors.textSecondary
        Column(
          Modifier
            .weight(1f)
            .fillMaxHeight()
            .testTag(item.testTag)
            .then(itemModifier(item.id))
            .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(item.id) })
            .padding(horizontal = AntTheme.spacing.xxs),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center,
        ) {
          Icon(item.icon, contentDescription = null, modifier = Modifier.size(24.dp), tint = tint)
          Text(item.label, color = tint, style = AntTheme.typography.textSM, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
      }
    }
  }
}
