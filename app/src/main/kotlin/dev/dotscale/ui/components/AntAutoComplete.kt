package dev.dotscale.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import dev.dotscale.ui.theme.AntTheme

/** Sugerencias que empiezan por [text] sin distinguir mayúsculas, salvo las que ya son exactamente el texto. */
internal fun autoCompleteMatches(text: String, suggestions: List<String>): List<String> {
  val typed = text.trim()
  if (suggestions.any { it.equals(typed, ignoreCase = true) }) return emptyList()
  return suggestions.filter { it.startsWith(typed, ignoreCase = true) }
}

/**
 * `AutoComplete` de Ant Design: un [AntInput] editable con un desplegable de [suggestions] filtradas por
 * prefijo. El menú se oculta si no hay coincidencias o si el texto ya coincide con una sugerencia.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AntAutoComplete(
  value: String,
  onValueChange: (String) -> Unit,
  suggestions: List<String>,
  modifier: Modifier = Modifier,
  placeholder: String? = null,
  enabled: Boolean = true,
) {
  val colors = AntTheme.colors
  var expanded by remember { mutableStateOf(false) }
  val matches = remember(value, suggestions) { autoCompleteMatches(value, suggestions) }
  val showMenu = expanded && enabled && matches.isNotEmpty()

  ExposedDropdownMenuBox(expanded = showMenu, onExpandedChange = { expanded = it }, modifier = modifier) {
    AntInput(
      value = value,
      onValueChange = {
        onValueChange(it)
        expanded = true
      },
      modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable, enabled),
      placeholder = placeholder,
      enabled = enabled,
      keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
    )
    ExposedDropdownMenu(
      expanded = showMenu,
      onDismissRequest = { expanded = false },
      containerColor = colors.bgElevated,
      shape = RoundedCornerShape(AntTheme.radius.lg),
    ) {
      matches.forEach { suggestion ->
        DropdownMenuItem(
          text = { Text(suggestion, color = colors.text, style = AntTheme.typography.text) },
          onClick = {
            onValueChange(suggestion)
            expanded = false
          },
          modifier = Modifier.background(colors.bgElevated),
          colors = MenuDefaults.itemColors(textColor = colors.text),
        )
      }
    }
  }
}
