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
import dev.dotscale.coffee.CoffeeOption
import dev.dotscale.history.BrewMethod
import dev.dotscale.ui.coffees.formatCoffeeDate
import dev.dotscale.ui.components.AntSelect
import dev.dotscale.ui.components.AntSelectOption
import dev.dotscale.ui.theme.AntTheme

/** Guarda el filtro con cadenas vacías en lugar de `null` (el id del café, como texto): el Bundle no admite nulos en las listas. */
private val FilterSaver = Saver<BrewFilter, List<String>>(
  save = { listOf(it.coffeeId?.toString().orEmpty(), it.method?.name.orEmpty()) },
  restore = { saved ->
    BrewFilter(
      coffeeId = saved[0].toLongOrNull(),
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
fun rememberHistoryFilter(initial: BrewFilter, coffees: List<CoffeeOption>?, methods: List<BrewMethod>?): MutableState<BrewFilter> {
  val state = rememberSaveable(stateSaver = FilterSaver) { mutableStateOf(initial) }
  val filter = state.value
  LaunchedEffect(coffees, filter.coffeeId) {
    val available = coffees ?: return@LaunchedEffect
    if (filter.coffeeId != null && available.none { it.id == filter.coffeeId }) state.value = state.value.copy(coffeeId = null)
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
  coffees: List<CoffeeOption>?,
  methods: List<BrewMethod>?,
  onChange: (BrewFilter) -> Unit,
  modifier: Modifier = Modifier,
) {
  val allLabel = stringResource(R.string.history_filter_all)
  Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AntTheme.spacing.sm)) {
    val labels = coffeeLabels(coffees.orEmpty())
    val coffeeOptions = listOf(AntSelectOption<Long?>(null, allLabel)) + coffees.orEmpty().map { AntSelectOption<Long?>(it.id, labels.getValue(it.id)) }
    AntSelect(
      options = coffeeOptions,
      selected = filter.coffeeId,
      onSelect = { onChange(filter.copy(coffeeId = it)) },
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

/**
 * Etiqueta de cada café del filtro: su nombre y, si otro tiene el mismo nombre (sin distinguir mayúsculas), la
 * fecha de tueste para distinguir las bolsas (D33).
 */
@Composable
private fun coffeeLabels(coffees: List<CoffeeOption>): Map<Long, String> {
  val repeated = coffees.groupingBy { it.name.trim().lowercase() }.eachCount().filterValues { it > 1 }.keys
  return coffees.associate { option ->
    option.id to when {
      option.name.trim().lowercase() !in repeated -> option.name
      option.roastDate != null -> stringResource(R.string.history_filter_coffee_roasted, option.name, formatCoffeeDate(option.roastDate))
      else -> stringResource(R.string.history_filter_coffee_no_date, option.name)
    }
  }
}
