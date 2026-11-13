package dev.dotscale.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.dotscale.R
import dev.dotscale.history.BrewFilter
import dev.dotscale.history.BrewMethod
import dev.dotscale.ui.components.AntSelect
import dev.dotscale.ui.components.AntSelectOption
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

/**
 * El filtro del historial, que sobrevive a girar la pantalla. [initial] solo cuenta la primera vez.
 *
 * Si el valor elegido desaparece de una lista ya cargada ([coffees] o [methods] distintos de `null`), el
 * filtro vuelve a "Todos". Mientras la lista no ha llegado (`null`) no se toca: con una lista vacía inicial, la
 * regla borraría un filtro guardado antes de la primera emisión.
 */
@Composable
fun rememberHistoryFilter(initial: BrewFilter, coffees: List<String>?, methods: List<BrewMethod>?): MutableState<BrewFilter> {
  val state = rememberSaveable(stateSaver = FilterSaver) { mutableStateOf(initial) }
  val filter = state.value
  LaunchedEffect(coffees, filter.coffee) {
    val available = coffees ?: return@LaunchedEffect
    if (filter.coffee != null && filter.coffee !in available) state.value = state.value.copy(coffee = null)
  }
  LaunchedEffect(methods, filter.method) {
    val available = methods ?: return@LaunchedEffect
    if (filter.method != null && filter.method !in available) state.value = state.value.copy(method = null)
  }
  return state
}

/** Los dos `AntSelect` del historial ("Café" y "Método"), también usados por el selector de B de la comparación. */
@Composable
fun HistoryFilters(
  filter: BrewFilter,
  coffees: List<String>?,
  methods: List<BrewMethod>?,
  onChange: (BrewFilter) -> Unit,
  modifier: Modifier = Modifier,
) {
  val allLabel = stringResource(R.string.history_filter_all)
  Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AntTheme.spacing.sm)) {
    val coffeeOptions = listOf(AntSelectOption<String?>(null, allLabel)) + coffees.orEmpty().map { AntSelectOption<String?>(it, it) }
    AntSelect(
      options = coffeeOptions,
      selected = filter.coffee,
      onSelect = { onChange(filter.copy(coffee = it)) },
      label = stringResource(R.string.history_filter_coffee),
      modifier = Modifier.weight(1f),
      testTag = "history_filter_coffee",
    )
    val methodOptions = listOf(AntSelectOption<BrewMethod?>(null, allLabel)) + methods.orEmpty().map { AntSelectOption<BrewMethod?>(it, it.label()) }
    AntSelect(
      options = methodOptions,
      selected = filter.method,
      onSelect = { onChange(filter.copy(method = it)) },
      label = stringResource(R.string.history_filter_method),
      modifier = Modifier.weight(1f),
      testTag = "history_filter_method",
    )
  }
}
