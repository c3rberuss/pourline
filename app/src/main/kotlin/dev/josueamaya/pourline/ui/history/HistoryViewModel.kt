package dev.josueamaya.pourline.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.josueamaya.pourline.coffee.CoffeeOption
import dev.josueamaya.pourline.history.BrewFilter
import dev.josueamaya.pourline.history.BrewListItem
import dev.josueamaya.pourline.history.BrewMethod
import dev.josueamaya.pourline.history.BrewRepository
import dev.josueamaya.pourline.history.DeletedBrew
import dev.josueamaya.pourline.history.ProgressPoint
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * `items`, `progress`, `coffees` y `methods` empiezan en `null` (cargando): con una lista vacía inicial, la
 * regla "si el valor del filtro desaparece, vuelve a Todos" borraría el filtro guardado antes de la primera emisión.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(private val repository: BrewRepository) : ViewModel() {
  private val filter = MutableStateFlow(BrewFilter())

  fun setFilter(value: BrewFilter) {
    filter.value = value
  }

  val items: StateFlow<List<BrewListItem>?> =
    filter.flatMapLatest { repository.list(it) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

  val progress: StateFlow<List<ProgressPoint>?> =
    filter.flatMapLatest { repository.progress(it) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

  val coffees: StateFlow<List<CoffeeOption>?> =
    repository.coffeeOptions().stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

  val methods: StateFlow<List<BrewMethod>?> =
    repository.methods().stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

  val pendingUndo: StateFlow<DeletedBrew?> = repository.pendingUndo

  fun restore(deleted: DeletedBrew) {
    viewModelScope.launch { repository.restore(deleted) }
  }

  /** Con [deleted], solo vacía el deshacer si sigue siendo ese borrado. */
  fun clearPendingUndo(deleted: DeletedBrew? = null) = repository.clearPendingUndo(deleted)

  private companion object {
    const val STOP_TIMEOUT_MS = 5_000L
  }
}
