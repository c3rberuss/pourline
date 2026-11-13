package dev.dotscale.ui.coffees

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.dotscale.R
import dev.dotscale.coffee.CoffeeListItem
import dev.dotscale.coffee.CoffeeSearch
import dev.dotscale.ui.components.AntButton
import dev.dotscale.ui.components.AntButtonType
import dev.dotscale.ui.components.AntDrawer
import dev.dotscale.ui.components.AntIcons
import dev.dotscale.ui.components.AntInput
import dev.dotscale.ui.components.AntText
import dev.dotscale.ui.components.AntTextType
import dev.dotscale.ui.theme.AntTheme

private val ListMaxHeight = 360.dp

/** Con más cafés que esto aparece el buscador. */
private const val SEARCH_THRESHOLD = 5

/**
 * Selector de café sobre un `AntDrawer` inferior (D6): buscador (con más de 5 cafés), filas con miniatura,
 * tostador, fecha de tueste y stock, "Sin café" (solo si [onClear] no es `null`, es decir, si hay algo elegido) y
 * "Nuevo café". Elegir una fila llama a [onSelect] y cierra el selector. [extra] es el café vinculado aunque esté
 * archivado ("Editar preparación"); se muestra con su etiqueta "Archivado".
 */
@Composable
fun CoffeePicker(
  coffees: List<CoffeeListItem>,
  selectedId: Long?,
  onSelect: (Long) -> Unit,
  onClear: (() -> Unit)?,
  onNew: () -> Unit,
  onClose: () -> Unit,
  extra: CoffeeListItem? = null,
) {
  var query by rememberSaveable { mutableStateOf("") }
  val all = remember(coffees, extra) { if (extra != null && coffees.none { it.id == extra.id }) coffees + extra else coffees }
  val visible = remember(all, query) { all.filter { CoffeeSearch.matches(it, query) } }
  AntDrawer(title = stringResource(R.string.coffee_picker_title), onClose = onClose, modifier = Modifier.testTag("coffee_picker")) {
    if (all.size > SEARCH_THRESHOLD) {
      AntInput(
        value = query,
        onValueChange = { query = it },
        modifier = Modifier.fillMaxWidth().testTag("coffee_picker_search"),
        placeholder = stringResource(R.string.coffee_picker_search),
        prefix = {
          Icon(AntIcons.Search, contentDescription = null, modifier = Modifier.size(16.dp), tint = AntTheme.colors.textTertiary)
        },
      )
    }
    if (all.isEmpty()) {
      AntText(
        stringResource(R.string.coffee_picker_empty),
        Modifier.fillMaxWidth().testTag("coffee_picker_empty"),
        type = AntTextType.Secondary,
        textAlign = TextAlign.Center,
      )
    } else {
      LazyColumn(Modifier.heightIn(max = ListMaxHeight), verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.xs)) {
        items(visible, key = { it.id }) { item ->
          CoffeeRow(
            item = item,
            onClick = {
              onSelect(item.id)
              onClose()
            },
            compact = true,
            selected = item.id == selectedId,
            archivedTag = true,
            testTag = "coffee_picker_row",
          )
        }
      }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AntTheme.spacing.xs)) {
      if (onClear != null) {
        AntButton(
          text = stringResource(R.string.coffee_picker_clear),
          onClick = {
            onClear()
            onClose()
          },
          modifier = Modifier.weight(1f).testTag("coffee_picker_clear"),
          danger = true,
        )
      }
      AntButton(
        text = stringResource(R.string.coffee_picker_new),
        onClick = onNew,
        modifier = Modifier.weight(1f).testTag("coffee_picker_new"),
        type = AntButtonType.Default,
        icon = AntIcons.Plus,
      )
    }
  }
}
