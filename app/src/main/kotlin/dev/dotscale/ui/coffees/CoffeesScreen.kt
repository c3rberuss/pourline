package dev.dotscale.ui.coffees

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.dotscale.R
import dev.dotscale.coffee.CoffeeListItem
import dev.dotscale.ui.components.AntButton
import dev.dotscale.ui.components.AntButtonType
import dev.dotscale.ui.components.AntEmpty
import dev.dotscale.ui.components.AntIconButton
import dev.dotscale.ui.components.AntIcons
import dev.dotscale.ui.components.AntInput
import dev.dotscale.ui.components.AntSegmented
import dev.dotscale.ui.components.AntSegmentedOption
import dev.dotscale.ui.components.AntTopBar
import dev.dotscale.ui.theme.AntTheme
import dev.dotscale.ui.theme.DotScaleTheme
import java.time.LocalDate

@Composable
fun CoffeesScreen(
  viewModel: CoffeesViewModel,
  onBack: () -> Unit,
  onOpenCoffee: (Long) -> Unit,
  onAddCoffee: () -> Unit,
) {
  val items by viewModel.items.collectAsStateWithLifecycle()
  var query by rememberSaveable { mutableStateOf("") }
  var archived by rememberSaveable { mutableStateOf(false) }
  LaunchedEffect(query, archived) {
    viewModel.setQuery(query)
    viewModel.setArchived(archived)
  }
  CoffeesContent(items, query, { query = it }, archived, { archived = it }, onBack, onOpenCoffee, onAddCoffee)
}

@Composable
private fun CoffeesContent(
  items: List<CoffeeListItem>?,
  query: String,
  onQueryChange: (String) -> Unit,
  archived: Boolean,
  onArchivedChange: (Boolean) -> Unit,
  onBack: () -> Unit,
  onOpenCoffee: (Long) -> Unit,
  onAddCoffee: () -> Unit,
) {
  Scaffold(
    containerColor = AntTheme.colors.bgLayout,
    topBar = {
      AntTopBar(
        title = stringResource(R.string.coffees_title),
        navigationIcon = {
          AntIconButton(AntIcons.ArrowLeft, contentDescription = stringResource(R.string.scale_back), onClick = onBack)
        },
        actions = {
          AntIconButton(
            AntIcons.Plus,
            contentDescription = stringResource(R.string.coffees_add),
            onClick = onAddCoffee,
            modifier = Modifier.testTag("coffees_add"),
          )
        },
      )
    },
  ) { padding ->
    LazyColumn(
      Modifier.fillMaxSize().padding(padding).testTag("coffees_list"),
      contentPadding = PaddingValues(AntTheme.spacing.base),
      verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.sm),
    ) {
      item(key = "search") { SearchField(query, onQueryChange) }
      item(key = "tabs") {
        AntSegmented(
          options = listOf(
            AntSegmentedOption(false, stringResource(R.string.coffees_tab_active)),
            AntSegmentedOption(true, stringResource(R.string.coffees_tab_archived)),
          ),
          selected = archived,
          onSelect = onArchivedChange,
          modifier = Modifier.fillMaxWidth().testTag("coffees_tabs"),
        )
      }
      val rows = items ?: return@LazyColumn
      if (rows.isEmpty()) {
        item(key = "empty") { EmptyState(query, archived, onAddCoffee) }
      } else {
        items(rows, key = { it.id }) { item -> CoffeeRow(item, onClick = { onOpenCoffee(item.id) }) }
      }
    }
  }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
  AntInput(
    value = query,
    onValueChange = onQueryChange,
    modifier = Modifier.fillMaxWidth().testTag("coffees_search"),
    placeholder = stringResource(R.string.coffees_search_hint),
    prefix = { Icon(AntIcons.Search, contentDescription = null, modifier = Modifier.size(16.dp), tint = AntTheme.colors.textTertiary) },
    suffix = if (query.isEmpty()) {
      null
    } else {
      {
        AntIconButton(
          AntIcons.Close,
          contentDescription = stringResource(R.string.coffees_search_clear),
          onClick = { onQueryChange("") },
          modifier = Modifier.testTag("coffees_search_clear"),
        )
      }
    },
  )
}

@Composable
private fun EmptyState(query: String, archived: Boolean, onAddCoffee: () -> Unit) {
  when {
    query.isNotBlank() -> AntEmpty(
      stringResource(R.string.coffees_empty_search, query.trim()),
      modifier = Modifier.testTag("coffees_empty_search"),
      icon = AntIcons.Coffee,
    )
    archived -> AntEmpty(
      stringResource(R.string.coffees_empty_archived),
      modifier = Modifier.testTag("coffees_empty_archived"),
      icon = AntIcons.Coffee,
    )
    else -> AntEmpty(
      stringResource(R.string.coffees_empty),
      modifier = Modifier.testTag("coffees_empty"),
      icon = AntIcons.Coffee,
      action = {
        AntButton(
          text = stringResource(R.string.coffees_add),
          onClick = onAddCoffee,
          modifier = Modifier.testTag("coffees_empty_add"),
          type = AntButtonType.Primary,
          icon = AntIcons.Plus,
        )
      },
    )
  }
}

private val previewItems = listOf(
  CoffeeListItem(1, "Etiopía Guji Hambela", "Café de Altura", "Etiopía", "Guji", LocalDate.of(2026, 10, 12), 9, 180.0, false, null, false),
  CoffeeListItem(2, "Kenia Kiambu AA", "Origen Tostadores", "Kenia", null, LocalDate.of(2026, 10, 2), 19, 0.0, true, null, false),
)

@Preview(name = "Cafés claro", showBackground = true)
@Composable
private fun CoffeesPreview() {
  DotScaleTheme(darkTheme = false) { CoffeesContent(previewItems, "", {}, false, {}, {}, {}, {}) }
}

@Preview(name = "Cafés oscuro", showBackground = true)
@Composable
private fun CoffeesPreviewDark() {
  DotScaleTheme(darkTheme = true) { CoffeesContent(previewItems, "", {}, false, {}, {}, {}, {}) }
}
