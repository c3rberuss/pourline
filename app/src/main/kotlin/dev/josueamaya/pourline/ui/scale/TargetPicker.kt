package dev.josueamaya.pourline.ui.scale

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.josueamaya.pourline.R
import dev.josueamaya.pourline.history.BrewListItem
import dev.josueamaya.pourline.recipe.RecipeListItem
import dev.josueamaya.pourline.settings.ReferenceKey
import dev.josueamaya.pourline.ui.components.AntButton
import dev.josueamaya.pourline.ui.components.AntButtonType
import dev.josueamaya.pourline.ui.components.AntDrawer
import dev.josueamaya.pourline.ui.components.AntSegmented
import dev.josueamaya.pourline.ui.components.AntSegmentedOption
import dev.josueamaya.pourline.ui.components.AntText
import dev.josueamaya.pourline.ui.components.AntTextType
import dev.josueamaya.pourline.ui.history.HistoryRow
import dev.josueamaya.pourline.ui.recipes.RecipeRow
import dev.josueamaya.pourline.ui.theme.AntSize
import dev.josueamaya.pourline.ui.theme.AntTheme

private enum class PickerTab { RECIPES, HISTORY }

private val ListMaxHeight = 360.dp

/**
 * Selector de objetivo, sobre un `AntDrawer` inferior: pestañas "Recetas | Historial", "Quitar objetivo" (si hay
 * uno) y "Gestionar recetas". No tiene estado propio más allá de la pestaña: recibe las listas y la referencia
 * activa. Tocar una fila llama a [onSelect] y cierra el selector.
 *
 * Las preparaciones sin agua no pueden ser objetivo y se muestran deshabilitadas.
 */
@Composable
fun TargetPicker(
  recipes: List<RecipeListItem>,
  brews: List<BrewListItem>,
  active: ReferenceKey?,
  onSelect: (ReferenceKey) -> Unit,
  onClear: () -> Unit,
  onManageRecipes: () -> Unit,
  onClose: () -> Unit,
  showBrews: Boolean = true,
) {
  var tab by rememberSaveable { mutableStateOf(PickerTab.RECIPES) }
  // Sin preparaciones como objetivo (modo Básico) solo hay recetas y no hace falta el segmentado.
  val effectiveTab = if (showBrews) tab else PickerTab.RECIPES
  AntDrawer(title = stringResource(R.string.target_picker_title), onClose = onClose, modifier = Modifier.testTag("target_picker")) {
    if (showBrews) {
      AntSegmented(
        options = listOf(
          AntSegmentedOption(PickerTab.RECIPES, stringResource(R.string.target_picker_recipes)),
          AntSegmentedOption(PickerTab.HISTORY, stringResource(R.string.target_picker_history)),
        ),
        selected = tab,
        onSelect = { tab = it },
        modifier = Modifier.fillMaxWidth().testTag("target_picker_tabs"),
      )
    }
    when (effectiveTab) {
      PickerTab.RECIPES -> if (recipes.isEmpty()) {
        EmptyText(stringResource(R.string.target_picker_empty_recipes))
      } else {
        LazyColumn(Modifier.heightIn(max = ListMaxHeight), verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.xs)) {
          items(recipes, key = { it.id }) { item ->
            RecipeRow(item, onClick = { onSelect(ReferenceKey.Recipe(item.id)) }, active = active == ReferenceKey.Recipe(item.id))
          }
        }
      }
      PickerTab.HISTORY -> if (brews.isEmpty()) {
        EmptyText(stringResource(R.string.target_picker_empty_history))
      } else {
        LazyColumn(Modifier.heightIn(max = ListMaxHeight), verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.xs)) {
          items(brews, key = { it.id }) { item ->
            HistoryRow(item, onClick = { onSelect(ReferenceKey.Brew(item.id)) }, enabled = item.totalWaterG > 0)
          }
        }
      }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AntTheme.spacing.xs)) {
      if (active != null) {
        AntButton(
          text = stringResource(R.string.target_picker_clear),
          onClick = onClear,
          modifier = Modifier.weight(1f).testTag("target_picker_clear"),
          size = AntSize.Middle,
          danger = true,
        )
      }
      AntButton(
        text = stringResource(R.string.target_picker_manage),
        onClick = onManageRecipes,
        modifier = Modifier.weight(1f).testTag("target_picker_manage"),
        type = AntButtonType.Default,
      )
    }
  }
}

@Composable
private fun EmptyText(text: String) {
  AntText(
    text = text,
    modifier = Modifier.fillMaxWidth().testTag("target_picker_empty"),
    type = AntTextType.Secondary,
    textAlign = TextAlign.Center,
  )
}
