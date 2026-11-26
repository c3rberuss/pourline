package dev.josueamaya.pourline.ui.recipes.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.josueamaya.pourline.R
import dev.josueamaya.pourline.history.BrewMethod
import dev.josueamaya.pourline.recipe.catalog.CatalogListItem
import dev.josueamaya.pourline.ui.components.AntAlert
import dev.josueamaya.pourline.ui.components.AntButton
import dev.josueamaya.pourline.ui.components.AntButtonType
import dev.josueamaya.pourline.ui.components.AntCard
import dev.josueamaya.pourline.ui.components.AntCardSize
import dev.josueamaya.pourline.ui.components.AntEmpty
import dev.josueamaya.pourline.ui.components.AntIcons
import dev.josueamaya.pourline.ui.components.AntInput
import dev.josueamaya.pourline.ui.components.AntSelect
import dev.josueamaya.pourline.ui.components.AntSelectOption
import dev.josueamaya.pourline.ui.components.AntStatus
import dev.josueamaya.pourline.ui.components.AntTag
import dev.josueamaya.pourline.ui.components.AntTagColor
import dev.josueamaya.pourline.ui.components.AntText
import dev.josueamaya.pourline.ui.components.AntTextType
import dev.josueamaya.pourline.ui.history.formatBrewDateTime
import dev.josueamaya.pourline.ui.history.label
import dev.josueamaya.pourline.ui.recipes.recipeSummary
import dev.josueamaya.pourline.ui.session.formatClock
import dev.josueamaya.pourline.ui.theme.AntTheme

/** El campo de búsqueda por nombre o autor (D33). */
@Composable
fun CatalogSearchBar(query: String, onQuery: (String) -> Unit, modifier: Modifier = Modifier) {
  AntInput(
    value = query,
    onValueChange = onQuery,
    modifier = modifier.fillMaxWidth().testTag("catalog_search"),
    placeholder = stringResource(R.string.catalog_search_placeholder),
    prefix = { Icon(AntIcons.Search, contentDescription = null, modifier = Modifier.size(16.dp), tint = AntTheme.colors.textTertiary) },
  )
}

/** El filtro por método: "Todos" y los métodos que hay en la lista. */
@Composable
fun CatalogMethodFilter(methods: List<BrewMethod>, selected: BrewMethod?, onSelect: (BrewMethod?) -> Unit, modifier: Modifier = Modifier) {
  val all = AntSelectOption<BrewMethod?>(null, stringResource(R.string.catalog_filter_all))
  val options = listOf(all) + methods.map { AntSelectOption<BrewMethod?>(it, it.label()) }
  AntSelect(
    options = options,
    selected = selected,
    onSelect = onSelect,
    label = null,
    modifier = modifier.fillMaxWidth(),
    testTag = "catalog_method_filter",
  )
}

/** El aviso discreto de encima de la lista (D34); nada si [CatalogListUi.notice] es `null`. */
@Composable
fun CatalogNoticeBar(ui: CatalogListUi, modifier: Modifier = Modifier) {
  val notice = ui.notice ?: return
  val date = ui.lastDownloadMs?.let { formatBrewDateTime(it) }.orEmpty()
  val box = modifier.fillMaxWidth().testTag("catalog_notice")
  when (notice) {
    CatalogNotice.UPDATE_FAILED -> AntAlert(stringResource(R.string.catalog_update_failed, date), box, type = AntStatus.Warning)
    CatalogNotice.USING_BUNDLED -> AntAlert(stringResource(R.string.catalog_using_bundled), box, type = AntStatus.Warning)
    CatalogNotice.NEWER_SCHEMA -> AntAlert(stringResource(R.string.catalog_newer_schema), box, type = AntStatus.Warning)
    CatalogNotice.LAST_UPDATE -> AntText(stringResource(R.string.catalog_last_update, date), box, type = AntTextType.Secondary)
  }
}

/**
 * Fila de una receta del catálogo: nombre, autor, método, "15 g · 250 g · 1:16,7 · 5 pasos · 1:55" y las etiquetas
 * "Guardada" (si el usuario ya tiene una copia) y "Activa" (si es la referencia de Preparar).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CatalogRecipeRow(
  item: CatalogListItem,
  saved: Boolean,
  active: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  AntCard(modifier.testTag("catalog_row_${item.key}"), size = AntCardSize.Small, onClick = onClick) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.xxs)) {
      AntText(item.name, Modifier.testTag("catalog_row_name"), maxLines = 1, overflow = TextOverflow.Ellipsis)
      item.author?.let { AntText(it, Modifier.testTag("catalog_row_author"), type = AntTextType.Secondary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
      val method = if (item.method == BrewMethod.OTHER) item.methodOther ?: item.method.label() else item.method.label()
      AntText(method, Modifier.testTag("catalog_row_method"), type = AntTextType.Secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
      val time = formatClock(item.totalDurationMs)
      val timeDescription = stringResource(R.string.recipe_total_time_description, time)
      val summary = recipeSummary(item.doseG, item.totalWaterG, item.stageCount, time)
      val summaryDescription = recipeSummary(item.doseG, item.totalWaterG, item.stageCount, timeDescription)
      AntText(summary, Modifier.testTag("catalog_row_summary").semantics { contentDescription = summaryDescription }, type = AntTextType.Secondary)
      if (saved || active) {
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(AntTheme.spacing.xxs),
          verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.xxs),
          modifier = Modifier.fillMaxWidth(),
        ) {
          if (saved) AntTag(stringResource(R.string.catalog_saved_tag), Modifier.testTag("catalog_saved_tag"), color = AntTagColor.Success)
          if (active) AntTag(stringResource(R.string.recipe_active_tag), Modifier.testTag("catalog_active_tag"), color = AntTagColor.Primary)
        }
      }
    }
  }
}

/**
 * La pestaña "Recomendadas": búsqueda y filtro arriba, el aviso, y la lista con "deslizar para actualizar". Sin resultados
 * dice por qué y, si hay filtros, ofrece quitarlos.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogRecipeList(
  ui: CatalogListUi,
  browser: CatalogBrowser,
  activeKey: String?,
  onOpen: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(modifier.fillMaxSize()) {
    Column(
      Modifier.padding(horizontal = AntTheme.spacing.base),
      verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.xs),
    ) {
      CatalogSearchBar(ui.query, browser::setQuery)
      CatalogMethodFilter(ui.methods, ui.method, browser::setMethod)
      CatalogNoticeBar(ui)
    }
    val state = rememberPullToRefreshState()
    PullToRefreshBox(
      isRefreshing = ui.refreshing,
      onRefresh = browser::refresh,
      modifier = Modifier.fillMaxSize(),
      state = state,
      indicator = {
        PullToRefreshDefaults.Indicator(
          state = state,
          isRefreshing = ui.refreshing,
          modifier = Modifier.align(Alignment.TopCenter),
          containerColor = AntTheme.colors.bgElevated,
          color = AntTheme.colors.primary.base,
        )
      },
    ) {
      LazyColumn(
        Modifier.fillMaxSize().testTag("catalog_list"),
        contentPadding = PaddingValues(AntTheme.spacing.base),
        verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.sm),
      ) {
        if (ui.loaded && ui.items.isEmpty()) {
          item(key = "empty") {
            AntEmpty(
              stringResource(R.string.catalog_no_results),
              Modifier.testTag("catalog_empty"),
              icon = AntIcons.Recipe,
              action = if (ui.filtered) {
                {
                  AntButton(
                    text = stringResource(R.string.catalog_clear_filters),
                    onClick = browser::clearFilters,
                    modifier = Modifier.testTag("catalog_clear_filters"),
                    type = AntButtonType.Default,
                  )
                }
              } else {
                null
              },
            )
          }
        } else {
          items(ui.items, key = { it.key }) { item ->
            CatalogRecipeRow(
              item,
              saved = item.key in ui.savedKeys,
              active = item.key == activeKey,
              onClick = { onOpen(item.key) },
            )
          }
        }
      }
    }
  }
}
