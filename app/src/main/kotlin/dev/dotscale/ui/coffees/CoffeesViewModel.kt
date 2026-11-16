package dev.dotscale.ui.coffees

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.dotscale.coffee.CoffeeListItem
import dev.dotscale.coffee.CoffeeRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

/**
 * Lista de cafés. El texto de búsqueda y la pestaña viven en la pantalla (`rememberSaveable`) y se pasan con
 * [setQuery] y [setArchived]. `items` empieza en `null` (cargando) para no enseñar el estado vacío antes de
 * la primera emisión.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CoffeesViewModel(repository: CoffeeRepository) : ViewModel() {
  private val query = MutableStateFlow("")
  private val archived = MutableStateFlow(false)

  val items: StateFlow<List<CoffeeListItem>?> =
    combine(query, archived) { q, a -> q to a }
      .flatMapLatest { (q, a) -> repository.coffees(q, a) }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

  fun setQuery(text: String) {
    query.value = text
  }

  fun setArchived(value: Boolean) {
    archived.value = value
  }

  private companion object {
    const val STOP_TIMEOUT_MS = 5_000L
  }
}
