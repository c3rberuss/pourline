package dev.dotscale.ui.coffees

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import dev.dotscale.R
import dev.dotscale.ui.components.AntAutoComplete
import dev.dotscale.ui.components.AntButtonType
import dev.dotscale.ui.components.AntIconButton
import dev.dotscale.ui.components.AntIcons
import dev.dotscale.ui.components.AntTag
import dev.dotscale.ui.theme.AntTheme

/**
 * Variedades como etiquetas con cierre y, debajo, un campo con sugerencias para añadir otra: "Añadir" con
 * `ImeAction.Done`, con el botón + o al elegir una sugerencia.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VarietiesField(
  varieties: List<String>,
  text: String,
  suggestions: List<String>,
  onTextChange: (String) -> Unit,
  onAdd: (String) -> Unit,
  onRemove: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.xs)) {
    if (varieties.isNotEmpty()) {
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(AntTheme.spacing.xxs),
        verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.xxs),
        modifier = Modifier.fillMaxWidth().testTag("coffee_edit_varieties"),
      ) {
        varieties.forEach { variety -> AntTag(variety, onClose = { onRemove(variety) }) }
      }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(AntTheme.spacing.xxs), verticalAlignment = Alignment.CenterVertically) {
      AntAutoComplete(
        value = text,
        onValueChange = onTextChange,
        suggestions = suggestions.filter { s -> varieties.none { it.equals(s, ignoreCase = true) } },
        modifier = Modifier.weight(1f).testTag("coffee_edit_variety_input"),
        placeholder = stringResource(R.string.coffee_edit_variety_hint),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onAdd(text) }),
        onSelect = onAdd,
      )
      AntIconButton(
        AntIcons.Plus,
        contentDescription = stringResource(R.string.coffee_edit_variety_add),
        onClick = { onAdd(text) },
        modifier = Modifier.testTag("coffee_edit_variety_add"),
        type = AntButtonType.Default,
        enabled = text.isNotBlank(),
      )
    }
  }
}
