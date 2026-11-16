package dev.dotscale.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.dotscale.coffee.CoffeeEntity
import dev.dotscale.coffee.CoffeeRepository
import dev.dotscale.grinder.Grinder
import dev.dotscale.grinder.GrinderRepository
import dev.dotscale.history.BrewRecord
import dev.dotscale.history.BrewRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface BrewDetailState {
  data object Loading : BrewDetailState

  /** [coffee] es el café vinculado ahora mismo; `null` sin vínculo (o mientras carga). */
  data class Loaded(val record: BrewRecord, val coffee: CoffeeEntity? = null, val grinder: Grinder? = null) : BrewDetailState

  /** La fila ya no existe (borrada aquí o en otro sitio): la pantalla vuelve atrás. */
  data object Gone : BrewDetailState
}

@OptIn(ExperimentalCoroutinesApi::class)
class BrewDetailViewModel(
  private val repository: BrewRepository,
  coffees: CoffeeRepository,
  grinders: GrinderRepository,
  private val id: Long,
) : ViewModel() {
  val state: StateFlow<BrewDetailState> = repository.observe(id)
    .flatMapLatest { record ->
      if (record == null) {
        flowOf(BrewDetailState.Gone)
      } else {
        val coffeeId = record.entity.coffeeId
        val coffee: Flow<CoffeeEntity?> = if (coffeeId == null) flowOf(null) else coffees.coffee(coffeeId).map { it?.coffee }
        combine(coffee, grinders.grinder(record.entity.grinderId)) { c, g -> BrewDetailState.Loaded(record, c, g) }
      }
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), BrewDetailState.Loading)

  /** Borra la preparación; al desaparecer la fila, [state] pasa a `Gone` y la pantalla vuelve a la lista. */
  fun delete() {
    viewModelScope.launch { repository.delete(id) }
  }

  private companion object {
    const val STOP_TIMEOUT_MS = 5_000L
  }
}
