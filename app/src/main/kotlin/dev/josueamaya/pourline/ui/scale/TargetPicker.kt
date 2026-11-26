package dev.josueamaya.pourline.ui.scale

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import dev.josueamaya.pourline.coffee.RoastLevel
import dev.josueamaya.pourline.history.BrewListItem
import dev.josueamaya.pourline.history.BrewMethod
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
import dev.josueamaya.pourline.ui.recipes.catalog.CatalogListUi
import dev.josueamaya.pourline.ui.recipes.catalog.CatalogMethodFilter
import dev.josueamaya.pourline.ui.recipes.catalog.CatalogRoastFilter
import dev.josueamaya.pourline.ui.recipes.catalog.CatalogNoticeBar
import dev.josueamaya.pourline.ui.recipes.catalog.CatalogRecipeRow
import dev.josueamaya.pourline.ui.recipes.catalog.CatalogSearchBar
import dev.josueamaya.pourline.ui.theme.AntSize
import dev.josueamaya.pourline.ui.theme.AntTheme

private enum class PickerTab { RECIPES, CATALOG, HISTORY }

private val ListMaxHeight = 360.dp

/**
 * Selector de objetivo, sobre un `AntDrawer` inferior: pestañas "Recetas | Recomendadas | Historial" (esta última solo con
 * [showBrews]), "Quitar objetivo" (si hay uno) y "Gestionar recetas". No tiene estado propio más allá de la pestaña: recibe
 * las listas y la referencia activa. Tocar una fila llama a [onSelect] y cierra el selector. La pestaña "Recomendadas"
 * (SPEC-012 D38) lleva su búsqueda y sus filtros por método y por tueste; [onCatalogShown] avisa al mostrarla para refrescar el catálogo.
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
  catalog: CatalogListUi,
  onCatalogQuery: (String) -> Unit,
  onCatalogMethod: (BrewMethod?) -> Unit,
  onCatalogRoast: (RoastLevel?) -> Unit,
  onCatalogClearFilters: () -> Unit,
  onCatalogShown: () -> Unit,
  showBrews: Boolean = true,
) {
  var tab by rememberSaveable { mutableStateOf(PickerTab.RECIPES) }
  // Sin preparaciones como objetivo (modo Básico) no hay pestaña Historial: si se quedó guardada, se vuelve a las recetas.
  val effectiveTab = if (!showBrews && tab == PickerTab.HISTORY) PickerTab.RECIPES else tab
  LaunchedEffect(effectiveTab) {
    if (effectiveTab == PickerTab.CATALOG) onCatalogShown()
  }
  AntDrawer(title = stringResource(R.string.target_picker_title), onClose = onClose, modifier = Modifier.testTag("target_picker")) {
    val tabs = buildList {
      add(AntSegmentedOption(PickerTab.RECIPES, stringResource(R.string.target_picker_recipes)))
      add(AntSegmentedOption(PickerTab.CATALOG, stringResource(R.string.recipes_segment_catalog)))
      if (showBrews) add(AntSegmentedOption(PickerTab.HISTORY, stringResource(R.string.target_picker_history)))
    }
    AntSegmented(
      options = tabs,
      selected = effectiveTab,
      onSelect = { tab = it },
      modifier = Modifier.fillMaxWidth().testTag("target_picker_tabs"),
      itemModifier = { Modifier.testTag("target_picker_tab_${it.name.lowercase()}") },
    )
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
      PickerTab.CATALOG -> CatalogPickerContent(
        catalog,
        activeKey = (active as? ReferenceKey.Catalog)?.key,
        onQuery = onCatalogQuery,
        onMethod = onCatalogMethod,
        onRoast = onCatalogRoast,
        onClearFilters = onCatalogClearFilters,
        onPick = { onSelect(ReferenceKey.Catalog(it)) },
      )
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

/** La pestaña "Recomendadas": búsqueda, filtros por método y tueste, aviso y la lista (D38). Sin "deslizar para actualizar". */
@Composable
private fun CatalogPickerContent(
  catalog: CatalogListUi,
  activeKey: String?,
  onQuery: (String) -> Unit,
  onMethod: (BrewMethod?) -> Unit,
  onRoast: (RoastLevel?) -> Unit,
  onClearFilters: () -> Unit,
  onPick: (String) -> Unit,
) {
  CatalogSearchBar(catalog.query, onQuery)
  CatalogMethodFilter(catalog.methods, catalog.method, onMethod)
  CatalogRoastFilter(catalog.roast, onRoast)
  CatalogNoticeBar(catalog)
  if (catalog.loaded && catalog.items.isEmpty()) {
    EmptyText(stringResource(R.string.catalog_no_results), tag = "target_picker_catalog_empty")
    if (catalog.filtered) {
      AntButton(
        text = stringResource(R.string.catalog_clear_filters),
        onClick = onClearFilters,
        modifier = Modifier.testTag("target_picker_catalog_clear_filters"),
        type = AntButtonType.Default,
      )
    }
  } else {
    LazyColumn(
      Modifier.heightIn(max = ListMaxHeight).testTag("target_picker_catalog_list"),
      verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.xs),
    ) {
      items(catalog.items, key = { it.key }) { item ->
        CatalogRecipeRow(item, saved = item.key in catalog.savedKeys, active = item.key == activeKey, onClick = { onPick(item.key) })
      }
    }
  }
}

@Composable
private fun EmptyText(text: String, tag: String = "target_picker_empty") {
  AntText(
    text = text,
    modifier = Modifier.fillMaxWidth().testTag(tag),
    type = AntTextType.Secondary,
    textAlign = TextAlign.Center,
  )
}
