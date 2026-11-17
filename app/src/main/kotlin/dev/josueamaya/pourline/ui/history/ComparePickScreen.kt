package dev.josueamaya.pourline.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.josueamaya.pourline.R
import dev.josueamaya.pourline.history.BrewFilter
import dev.josueamaya.pourline.ui.components.AntEmpty
import dev.josueamaya.pourline.ui.components.AntIconButton
import dev.josueamaya.pourline.ui.components.AntIcons
import dev.josueamaya.pourline.ui.components.AntSpin
import dev.josueamaya.pourline.ui.components.AntTopBar
import dev.josueamaya.pourline.ui.theme.AntTheme

/**
 * Elegir B para comparar con A. El filtro de café empieza en el de A y es independiente del de `HistoryScreen`;
 * el contenido solo se crea cuando A ya está cargada, porque sin ella no se sabe con qué café empezar (y el
 * `rememberSaveable` fijaría "Todos").
 */
@Composable
fun ComparePickScreen(viewModel: ComparePickViewModel, onBack: () -> Unit, onPick: (Long) -> Unit) {
  val a by viewModel.a.collectAsStateWithLifecycle()
  LaunchedEffect(a) { if (a is ComparePickA.Gone) onBack() }

  Scaffold(
    containerColor = AntTheme.colors.bgLayout,
    topBar = {
      AntTopBar(
        title = stringResource(R.string.compare_pick_title),
        navigationIcon = {
          AntIconButton(AntIcons.ArrowLeft, contentDescription = stringResource(R.string.scale_back), onClick = onBack)
        },
      )
    },
  ) { padding ->
    when (val current = a) {
      is ComparePickA.Loaded -> PickContent(viewModel, current.coffeeId, onPick, Modifier.padding(padding))
      ComparePickA.Loading, ComparePickA.Gone -> AntSpin(Modifier.padding(padding).padding(AntTheme.spacing.xl))
    }
  }
}

@Composable
private fun PickContent(viewModel: ComparePickViewModel, aCoffeeId: Long?, onPick: (Long) -> Unit, modifier: Modifier) {
  val items by viewModel.items.collectAsStateWithLifecycle()
  val coffees by viewModel.coffees.collectAsStateWithLifecycle()
  val methods by viewModel.methods.collectAsStateWithLifecycle()
  val hasOthers by viewModel.hasOthers.collectAsStateWithLifecycle()
  var filter by rememberHistoryFilter(BrewFilter(coffeeId = aCoffeeId), coffees, methods)
  LaunchedEffect(filter) { viewModel.setFilter(filter) }

  LazyColumn(
    modifier.fillMaxSize().testTag("compare_pick_list"),
    contentPadding = PaddingValues(AntTheme.spacing.base),
    verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.sm),
  ) {
    when (hasOthers) {
      null -> Unit
      false -> item(key = "empty_none") { AntEmpty(stringResource(R.string.compare_pick_empty), Modifier.testTag("compare_pick_empty")) }
      true -> {
        item(key = "filters") { HistoryFilters(filter, coffees, methods, onChange = { filter = it }) }
        val rows = items
        if (rows != null) {
          if (rows.isEmpty()) {
            item(key = "empty_filtered") {
              AntEmpty(stringResource(R.string.history_empty_filtered), Modifier.testTag("compare_pick_empty_filtered"))
            }
          } else {
            items(rows, key = { it.id }) { row -> HistoryRow(row, onClick = { onPick(row.id) }) }
          }
        }
      }
    }
  }
}
