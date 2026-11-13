package dev.dotscale.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.dotscale.ui.theme.AntSize
import dev.dotscale.ui.theme.AntTheme

data class AntSelectOption<T>(val value: T, val label: String)

/**
 * `Select` de Ant Design de opción única, sobre `ExposedDropdownMenuBox` de Material 3 (foco, teclado y
 * accesibilidad del desplegable). Muestra [label] encima y expone `Role.DropdownList` con la etiqueta.
 *
 * @param testTag se aplica al campo que se pulsa para abrir el menú.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> AntSelect(
  options: List<AntSelectOption<T>>,
  selected: T?,
  onSelect: (T) -> Unit,
  label: String,
  modifier: Modifier = Modifier,
  placeholder: String? = null,
  enabled: Boolean = true,
  testTag: String? = null,
) {
  val colors = AntTheme.colors
  val radius = AntTheme.radius.base
  val shape = RoundedCornerShape(radius)
  var expanded by remember { mutableStateOf(false) }
  val selectedLabel = options.firstOrNull { it.value == selected }?.label
  val borderColor by animateColorAsState(
    if (expanded && enabled) colors.primary.base else colors.border,
    tween(AntTheme.motion.durationMid),
    label = "AntSelectBorder",
  )
  val description = if (selectedLabel != null) "$label: $selectedLabel" else label

  Column(modifier, verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.xxs)) {
    Text(label, color = colors.textSecondary, style = AntTheme.typography.textSM)
    ExposedDropdownMenuBox(expanded = expanded && enabled, onExpandedChange = { expanded = it }) {
      Row(
        Modifier
          .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled)
          .fillMaxWidth()
          .height(AntTheme.controlHeight.of(AntSize.Middle))
          .background(if (enabled) colors.bgContainer else colors.bgContainerDisabled, shape)
          .antBorder(borderColor, radius)
          .padding(horizontal = AntTheme.spacing.sm)
          .semantics(mergeDescendants = true) {
            role = Role.DropdownList
            contentDescription = description
          }
          .let { if (testTag != null) it.testTag(testTag) else it },
        horizontalArrangement = Arrangement.spacedBy(AntTheme.spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          selectedLabel ?: placeholder.orEmpty(),
          modifier = Modifier.weight(1f),
          color = when {
            !enabled -> colors.textDisabled
            selectedLabel != null -> colors.text
            else -> colors.textPlaceholder
          },
          style = AntTheme.typography.text,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Icon(
          AntIcons.Down,
          contentDescription = null,
          modifier = Modifier.size(14.dp).rotate(if (expanded) 180f else 0f),
          tint = colors.textTertiary,
        )
      }
      ExposedDropdownMenu(
        expanded = expanded && enabled,
        onDismissRequest = { expanded = false },
        containerColor = colors.bgElevated,
        shape = RoundedCornerShape(AntTheme.radius.lg),
      ) {
        options.forEach { option ->
          val isSelected = option.value == selected
          DropdownMenuItem(
            text = {
              Text(
                option.label,
                color = if (isSelected) colors.primary.text else colors.text,
                style = AntTheme.typography.text,
              )
            },
            onClick = {
              onSelect(option.value)
              expanded = false
            },
            modifier = Modifier
              .background(if (isSelected) colors.primary.bg else Color.Transparent)
              .semantics { this.selected = isSelected },
            colors = MenuDefaults.itemColors(textColor = colors.text),
          )
        }
      }
    }
  }
}
