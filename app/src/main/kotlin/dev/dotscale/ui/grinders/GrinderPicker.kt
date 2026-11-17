package dev.dotscale.ui.grinders

import androidx.compose.foundation.layout.Arrangement
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
import dev.dotscale.coffee.fold
import dev.dotscale.grinder.GrindTexts
import dev.dotscale.grinder.Grinder
import dev.dotscale.ui.components.AntDrawer
import dev.dotscale.ui.components.AntIcons
import dev.dotscale.ui.components.AntInput
import dev.dotscale.ui.components.AntListItem
import dev.dotscale.ui.components.AntText
import dev.dotscale.ui.components.AntTextType
import dev.dotscale.ui.theme.AntTheme

private val ListMaxHeight = 400.dp

/**
 * El selector del molino de origen del convertidor (D26): un `AntDrawer` con buscador y dos secciones, "Mis molinos"
 * y "Catálogo". Elegir una fila llama a [onSelect] y cierra el selector. Un molino mío no se repite en el catálogo.
 */
@Composable
fun GrinderPicker(
  owned: List<Grinder>,
  catalog: List<Grinder>,
  selectedId: Long?,
  onSelect: (Long) -> Unit,
  onClose: () -> Unit,
) {
  var query by rememberSaveable { mutableStateOf("") }
  val texts = rememberGrindTexts()
  val needle = fold(query.trim())
  val mine = remember(owned, needle) { owned.filter { needle.isEmpty() || fold(it.displayName).contains(needle) } }
  val ownedIds = remember(owned) { owned.mapTo(HashSet()) { it.id } }
  val others = remember(catalog, ownedIds, needle) {
    catalog.filter { it.id !in ownedIds && (needle.isEmpty() || fold(it.displayName).contains(needle)) }
  }
  AntDrawer(title = stringResource(R.string.converter_picker_title), onClose = onClose, modifier = Modifier.testTag("grinder_picker")) {
    AntInput(
      value = query,
      onValueChange = { query = it },
      modifier = Modifier.fillMaxWidth().testTag("grinder_picker_search"),
      placeholder = stringResource(R.string.converter_picker_search),
      prefix = { Icon(AntIcons.Search, contentDescription = null, modifier = Modifier.size(16.dp), tint = AntTheme.colors.textTertiary) },
    )
    if (mine.isEmpty() && others.isEmpty()) {
      AntText(
        stringResource(R.string.converter_picker_empty),
        Modifier.fillMaxWidth().testTag("grinder_picker_empty"),
        type = AntTextType.Secondary,
        textAlign = TextAlign.Center,
      )
    } else {
      LazyColumn(Modifier.heightIn(max = ListMaxHeight), verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.xxs)) {
        if (mine.isNotEmpty()) {
          item(key = "owned_header") { SectionTitle(stringResource(R.string.converter_picker_owned), "grinder_picker_owned") }
          items(mine, key = { "o${it.id}" }) { grinder -> PickerRow(grinder, selectedId, texts, onSelect, onClose) }
        }
        if (others.isNotEmpty()) {
          item(key = "catalog_header") { SectionTitle(stringResource(R.string.converter_picker_catalog), "grinder_picker_catalog") }
          items(others, key = { "c${it.id}" }) { grinder -> PickerRow(grinder, selectedId, texts, onSelect, onClose) }
        }
      }
    }
  }
}

@Composable
private fun SectionTitle(text: String, tag: String) {
  AntText(text, Modifier.fillMaxWidth().testTag(tag), type = AntTextType.Secondary, style = AntTheme.typography.textSM)
}

@Composable
private fun PickerRow(
  grinder: Grinder,
  selectedId: Long?,
  texts: GrindTexts,
  onSelect: (Long) -> Unit,
  onClose: () -> Unit,
) {
  AntListItem(
    title = grinder.displayName + if (grinder.id == selectedId) " ✓" else "",
    description = grinder.unitsSummary(texts),
    modifier = Modifier.testTag("grinder_picker_row_${grinder.id}"),
    onClick = {
      onSelect(grinder.id)
      onClose()
    },
  )
}
