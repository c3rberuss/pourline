package dev.dotscale.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.dotscale.R
import dev.dotscale.history.BrewFilter
import dev.dotscale.history.BrewMethod
import dev.dotscale.ui.components.AntCard
import dev.dotscale.ui.components.AntEmpty
import dev.dotscale.ui.components.AntIconButton
import dev.dotscale.ui.components.AntIcons
import dev.dotscale.ui.components.AntMessageHost
import dev.dotscale.ui.components.AntSelect
import dev.dotscale.ui.components.AntSelectOption
import dev.dotscale.ui.components.AntStatus
import dev.dotscale.ui.components.AntText
import dev.dotscale.ui.components.AntTextType
import dev.dotscale.ui.components.AntTopBar
import dev.dotscale.ui.components.showAntMessage
import dev.dotscale.ui.theme.AntTheme

/** Guarda el filtro con cadenas vacías en lugar de `null`: el Bundle no admite nulos en las listas. */
private val FilterSaver = Saver<BrewFilter, List<String>>(
  save = { listOf(it.coffee.orEmpty(), it.method?.name.orEmpty()) },
  restore = { saved ->
    BrewFilter(
      coffee = saved[0].ifEmpty { null },
      method = saved[1].ifEmpty { null }?.let { name -> BrewMethod.entries.firstOrNull { it.name == name } },
    )
  },
)

@Composable
fun HistoryScreen(viewModel: HistoryViewModel, onBack: () -> Unit, onOpenBrew: (Long) -> Unit) {
  val items by viewModel.items.collectAsStateWithLifecycle()
  val progress by viewModel.progress.collectAsStateWithLifecycle()
  val coffees by viewModel.coffees.collectAsStateWithLifecycle()
  val methods by viewModel.methods.collectAsStateWithLifecycle()
  val pendingUndo by viewModel.pendingUndo.collectAsStateWithLifecycle()
  val snackbar = remember { SnackbarHostState() }
  var filter by rememberSaveable(stateSaver = FilterSaver) { mutableStateOf(BrewFilter()) }

  LaunchedEffect(filter) { viewModel.setFilter(filter) }
  // Si el valor elegido desaparece de una lista ya cargada, el filtro vuelve a "Todos".
  LaunchedEffect(coffees, filter.coffee) {
    val available = coffees ?: return@LaunchedEffect
    if (filter.coffee != null && filter.coffee !in available) filter = filter.copy(coffee = null)
  }
  LaunchedEffect(methods, filter.method) {
    val available = methods ?: return@LaunchedEffect
    if (filter.method != null && filter.method !in available) filter = filter.copy(method = null)
  }

  val deletedText = stringResource(R.string.history_deleted)
  val undoLabel = stringResource(R.string.history_undo)
  LaunchedEffect(pendingUndo) {
    val deleted = pendingUndo ?: return@LaunchedEffect
    val result = snackbar.showAntMessage(deletedText, AntStatus.Success, actionLabel = undoLabel)
    if (result == SnackbarResult.ActionPerformed) viewModel.restore(deleted) else viewModel.clearPendingUndo(deleted)
  }
  // Salir del historial vacía el deshacer.
  DisposableEffect(viewModel) { onDispose { viewModel.clearPendingUndo() } }

  val allLabel = stringResource(R.string.history_filter_all)
  Scaffold(
    containerColor = AntTheme.colors.bgLayout,
    topBar = {
      AntTopBar(
        title = stringResource(R.string.history_title),
        navigationIcon = {
          AntIconButton(AntIcons.ArrowLeft, contentDescription = stringResource(R.string.scale_back), onClick = onBack)
        },
      )
    },
    snackbarHost = { AntMessageHost(snackbar) },
  ) { padding ->
    LazyColumn(
      Modifier.fillMaxSize().padding(padding),
      contentPadding = PaddingValues(AntTheme.spacing.base),
      verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.sm),
    ) {
      item(key = "filters") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AntTheme.spacing.sm)) {
          val coffeeOptions = listOf(AntSelectOption<String?>(null, allLabel)) + (coffees.orEmpty()).map { AntSelectOption<String?>(it, it) }
          AntSelect(
            options = coffeeOptions,
            selected = filter.coffee,
            onSelect = { filter = filter.copy(coffee = it) },
            label = stringResource(R.string.history_filter_coffee),
            modifier = Modifier.weight(1f),
            testTag = "history_filter_coffee",
          )
          val methodOptions = listOf(AntSelectOption<BrewMethod?>(null, allLabel)) + (methods.orEmpty()).map { AntSelectOption<BrewMethod?>(it, it.label()) }
          AntSelect(
            options = methodOptions,
            selected = filter.method,
            onSelect = { filter = filter.copy(method = it) },
            label = stringResource(R.string.history_filter_method),
            modifier = Modifier.weight(1f),
            testTag = "history_filter_method",
          )
        }
      }
      progress?.let { points ->
        item(key = "progress") {
          AntCard(Modifier.fillMaxWidth(), title = stringResource(R.string.history_progress_title)) {
            if (points.size >= 2) {
              StabilityProgressChart(points)
            } else {
              AntText(
                stringResource(R.string.history_progress_empty),
                modifier = Modifier.fillMaxWidth().testTag("progress_empty"),
                type = AntTextType.Secondary,
                textAlign = TextAlign.Center,
              )
            }
          }
        }
      }
      val rows = items
      if (rows != null) {
        if (rows.isEmpty()) {
          item(key = "empty") {
            val filtered = filter != BrewFilter()
            AntEmpty(stringResource(if (filtered) R.string.history_empty_filtered else R.string.history_empty))
          }
        } else {
          items(rows, key = { it.id }) { item -> HistoryRow(item, onClick = { onOpenBrew(item.id) }) }
        }
      }
    }
  }
}
