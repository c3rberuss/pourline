package dev.josueamaya.pourline.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import dev.josueamaya.pourline.ui.theme.AntSize
import dev.josueamaya.pourline.ui.theme.AntTheme

data class AntSelectOption<T>(val value: T, val label: String)

/**
 * Aspecto común de `Select`: el campo con el valor (o el marcador), un [leading] opcional y el chevron o el
 * [trailing]. Lo comparten `AntSelect` (con menú) y `AntSelectTrigger` (sin menú).
 */
@Composable
private fun SelectAnchor(
  value: String?,
  placeholder: String?,
  enabled: Boolean,
  borderColor: Color,
  chevronRotation: Float,
  modifier: Modifier,
  leading: (@Composable () -> Unit)? = null,
  trailing: (@Composable () -> Unit)? = null,
) {
  val colors = AntTheme.colors
  val radius = AntTheme.radius.base
  Row(
    modifier
      .fillMaxWidth()
      .height(AntTheme.controlHeight.of(AntSize.Middle))
      .background(if (enabled) colors.bgContainer else colors.bgContainerDisabled, RoundedCornerShape(radius))
      .antBorder(borderColor, radius)
      .padding(horizontal = AntTheme.spacing.sm),
    horizontalArrangement = Arrangement.spacedBy(AntTheme.spacing.xxs),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    leading?.invoke()
    Text(
      value ?: placeholder.orEmpty(),
      modifier = Modifier.weight(1f),
      color = when {
        !enabled -> colors.textDisabled
        value != null -> colors.text
        else -> colors.textPlaceholder
      },
      style = AntTheme.typography.text,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
    if (trailing != null) {
      trailing()
    } else {
      Icon(
        AntIcons.Down,
        contentDescription = null,
        modifier = Modifier.size(14.dp).rotate(chevronRotation),
        tint = colors.textTertiary,
      )
    }
  }
}

private fun selectDescription(label: String?, value: String?): String? = when {
  label != null && value != null -> "$label: $value"
  else -> label ?: value
}

/**
 * `Select` de Ant Design de opción única, sobre `ExposedDropdownMenuBox` de Material 3 (foco, teclado y
 * accesibilidad del desplegable). Muestra [label] encima (si hay) y expone `Role.DropdownList` con la etiqueta.
 *
 * @param testTag se aplica al campo que se pulsa para abrir el menú.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> AntSelect(
  options: List<AntSelectOption<T>>,
  selected: T?,
  onSelect: (T) -> Unit,
  label: String?,
  modifier: Modifier = Modifier,
  placeholder: String? = null,
  enabled: Boolean = true,
  testTag: String? = null,
) {
  val colors = AntTheme.colors
  var expanded by remember { mutableStateOf(false) }
  val selectedLabel = options.firstOrNull { it.value == selected }?.label
  val borderColor by animateColorAsState(
    if (expanded && enabled) colors.primary.base else colors.border,
    tween(AntTheme.motion.durationMid),
    label = "AntSelectBorder",
  )
  val description = selectDescription(label, selectedLabel)

  Column(modifier, verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.xxs)) {
    if (label != null) Text(label, color = colors.textSecondary, style = AntTheme.typography.textSM)
    ExposedDropdownMenuBox(expanded = expanded && enabled, onExpandedChange = { expanded = it }) {
      SelectAnchor(
        value = selectedLabel,
        placeholder = placeholder,
        enabled = enabled,
        borderColor = borderColor,
        chevronRotation = if (expanded) 180f else 0f,
        modifier = Modifier
          .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled)
          .semantics(mergeDescendants = true) {
            role = Role.DropdownList
            if (description != null) contentDescription = description
          }
          .let { if (testTag != null) it.testTag(testTag) else it },
      )
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

/**
 * Disparador con el aspecto de `Select` pero sin menú: al pulsarlo el llamador abre su propio selector (un
 * cajón, un diálogo…). Se expone como botón; [description] sustituye a la descripción por defecto
 * ("etiqueta: valor").
 *
 * @param label se muestra encima del campo si no es `null`.
 * @param leading icono o miniatura antes del valor.
 * @param trailing sustituye al chevron.
 */
@Composable
fun AntSelectTrigger(
  value: String?,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  label: String? = null,
  placeholder: String? = null,
  leading: (@Composable () -> Unit)? = null,
  trailing: (@Composable () -> Unit)? = null,
  enabled: Boolean = true,
  description: String? = null,
  testTag: String? = null,
  status: AntInputStatus = AntInputStatus.Default,
) {
  val colors = AntTheme.colors
  val spoken = description ?: selectDescription(label, value)
  Column(modifier, verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.xxs)) {
    if (label != null) Text(label, color = colors.textSecondary, style = AntTheme.typography.textSM)
    SelectAnchor(
      value = value,
      placeholder = placeholder,
      enabled = enabled,
      borderColor = if (status == AntInputStatus.Error && enabled) colors.error.base else colors.border,
      chevronRotation = 0f,
      modifier = Modifier
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .semantics(mergeDescendants = true) { if (spoken != null) contentDescription = spoken }
        .let { if (testTag != null) it.testTag(testTag) else it },
      leading = leading,
      trailing = trailing,
    )
  }
}
