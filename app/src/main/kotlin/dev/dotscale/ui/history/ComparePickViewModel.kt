package dev.dotscale.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.dotscale.history.BrewFilter
import dev.dotscale.history.BrewListItem
import dev.dotscale.history.BrewMethod
import dev.dotscale.history.BrewRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** La preparación A del selector: hasta que carga no se sabe con qué café empieza el filtro. */
sealed interface ComparePickA {
  data object Loading : ComparePickA

  data class Loaded(val coffee: String?) : ComparePickA

  /** A ya no existe (borrada aquí o en otro sitio): la pantalla vuelve atrás. */
  data object Gone : ComparePickA
}

/**
 * Datos del selector de B. El filtro es propio y no toca el de `HistoryScreen`. `items`, `coffees` y `methods`
 * empiezan en `null` (cargando), como en el historial, para que la regla "si el valor desaparece, vuelve a Todos"
 * no actúe antes de la primera emisión.
 *
 * Los cafés y métodos son los de todo el historial, A incluida: el filtro empieza en el café de A, que tiene que
 * seguir siendo una opción aunque A sea la única con ese café.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ComparePickViewModel(repository: BrewRepository, private val aId: Long) : ViewModel() {
  private val filter = MutableStateFlow(BrewFilter())

  fun setFilter(value: BrewFilter) {
    filter.value = value
  }

  val a: StateFlow<ComparePickA> = repository.observe(aId)
    .map { if (it == null) ComparePickA.Gone else ComparePickA.Loaded(it.entity.coffee) }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ComparePickA.Loading)

  /** La lista filtrada, sin A. */
  val items: StateFlow<List<BrewListItem>?> = filter
    .flatMapLatest { repository.list(it) }
    .map { rows -> rows.filter { it.id != aId } }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

  val coffees: StateFlow<List<String>?> =
    repository.coffees().stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

  val methods: StateFlow<List<BrewMethod>?> =
    repository.methods().stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

  /** Hay alguna preparación además de A. `null` mientras carga. */
  val hasOthers: StateFlow<Boolean?> = repository.count()
    .map { it > 1 }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

  private companion object {
    const val STOP_TIMEOUT_MS = 5_000L
  }
}
