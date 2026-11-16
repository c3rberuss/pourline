package dev.dotscale.ui.grinders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.dotscale.R
import dev.dotscale.ui.components.AntButton
import dev.dotscale.ui.components.AntButtonType
import dev.dotscale.ui.components.AntEmpty
import dev.dotscale.ui.components.AntIconButton
import dev.dotscale.ui.components.AntIcons
import dev.dotscale.ui.components.AntInput
import dev.dotscale.ui.components.AntListItem
import dev.dotscale.ui.components.AntTag
import dev.dotscale.ui.components.AntTagColor
import dev.dotscale.ui.components.AntTopBar
import dev.dotscale.ui.theme.AntSize
import dev.dotscale.ui.theme.AntTheme

/** "Añadir molino": el catálogo con búsqueda, "Lo tengo" sin salir de la lista y "Crear molino propio" al final. */
@Composable
fun GrinderCatalogScreen(
  viewModel: GrinderCatalogViewModel,
  onBack: () -> Unit,
  onOpenGrinder: (Long) -> Unit,
  onCreate: () -> Unit,
) {
  val rows by viewModel.rows.collectAsStateWithLifecycle()
  var query by rememberSaveable { mutableStateOf("") }
  LaunchedEffect(query) { viewModel.setQuery(query) }
  val texts = rememberGrindTexts()

  Scaffold(
    containerColor = AntTheme.colors.bgLayout,
    topBar = {
      AntTopBar(
        title = stringResource(R.string.grinder_catalog_title),
        navigationIcon = {
          AntIconButton(AntIcons.ArrowLeft, contentDescription = stringResource(R.string.scale_back), onClick = onBack)
        },
      )
    },
  ) { padding ->
    LazyColumn(
      Modifier.fillMaxSize().padding(padding).testTag("grinder_catalog_list"),
      contentPadding = PaddingValues(vertical = AntTheme.spacing.base),
      verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.sm),
    ) {
      item(key = "search") {
        AntInput(
          value = query,
          onValueChange = { query = it },
          modifier = Modifier.fillMaxWidth().padding(horizontal = AntTheme.spacing.base).testTag("grinder_catalog_search"),
          placeholder = stringResource(R.string.grinder_catalog_search_hint),
          prefix = { Icon(AntIcons.Search, contentDescription = null, modifier = Modifier.size(16.dp), tint = AntTheme.colors.textTertiary) },
          suffix = if (query.isEmpty()) {
            null
          } else {
            {
              AntIconButton(
                AntIcons.Close,
                contentDescription = stringResource(R.string.grinder_catalog_search_clear),
                onClick = { query = "" },
                modifier = Modifier.testTag("grinder_catalog_search_clear"),
              )
            }
          },
        )
      }
      val list = rows
      if (list != null) {
        if (list.isEmpty()) {
          item(key = "empty") {
            AntEmpty(
              if (query.isBlank()) stringResource(R.string.grinder_catalog_empty) else stringResource(R.string.grinder_catalog_empty_search, query.trim()),
              modifier = Modifier.testTag("grinder_catalog_empty"),
              icon = AntIcons.Grinder,
            )
          }
        } else {
          items(list, key = { it.grinder.id }) { row ->
            val key = row.grinder.catalogKey
            AntListItem(
              title = row.grinder.displayName,
              description = row.grinder.unitsSummary(texts),
              modifier = Modifier.testTag("grinder_catalog_$key"),
              extra = {
                if (row.owned) {
                  AntTag(stringResource(R.string.grinder_catalog_owned), Modifier.testTag("grinder_catalog_owned_$key"), color = AntTagColor.Success, icon = AntIcons.Check)
                } else {
                  AntButton(
                    text = stringResource(R.string.grinder_catalog_own),
                    onClick = { viewModel.own(row.grinder.id) },
                    modifier = Modifier.testTag("grinder_catalog_own_$key"),
                    size = AntSize.Small,
                  )
                }
              },
              onClick = { onOpenGrinder(row.grinder.id) },
            )
          }
        }
        item(key = "create") {
          Row(Modifier.fillMaxWidth().padding(horizontal = AntTheme.spacing.base)) {
            AntButton(
              text = stringResource(R.string.grinder_catalog_create),
              onClick = onCreate,
              modifier = Modifier.fillMaxWidth().testTag("grinder_catalog_create"),
              type = AntButtonType.Dashed,
              icon = AntIcons.Plus,
            )
          }
        }
      }
    }
  }
}
