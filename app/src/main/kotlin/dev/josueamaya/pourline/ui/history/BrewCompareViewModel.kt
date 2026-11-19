package dev.josueamaya.pourline.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.josueamaya.pourline.coffee.CoffeeEntity
import dev.josueamaya.pourline.coffee.CoffeeRepository
import dev.josueamaya.pourline.history.BrewRecord
import dev.josueamaya.pourline.history.BrewRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface BrewCompareState {
  data object Loading : BrewCompareState

  /** [coffeeA] y [coffeeB] son los cafés vinculados (`null` sin vínculo); solo los usa el pie de la imagen para compartir. */
  data class Loaded(
    val a: BrewRecord,
    val b: BrewRecord,
    val coffeeA: CoffeeEntity? = null,
    val coffeeB: CoffeeEntity? = null,
  ) : BrewCompareState

  /** Una de las dos filas ya no existe (borrada aquí o en otro sitio): la pantalla vuelve atrás. */
  data object Gone : BrewCompareState
}

@OptIn(ExperimentalCoroutinesApi::class)
class BrewCompareViewModel(repository: BrewRepository, private val coffees: CoffeeRepository, aId: Long, bId: Long) : ViewModel() {
  val state: StateFlow<BrewCompareState> = combine(repository.observe(aId), repository.observe(bId)) { a, b -> a to b }
    .flatMapLatest { (a, b) ->
      if (a == null || b == null) {
        flowOf(BrewCompareState.Gone)
      } else {
        combine(coffee(a.entity.coffeeId), coffee(b.entity.coffeeId)) { ca, cb -> BrewCompareState.Loaded(a, b, ca, cb) }
      }
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), BrewCompareState.Loading)

  private fun coffee(id: Long?): Flow<CoffeeEntity?> = if (id == null) flowOf(null) else coffees.coffee(id).map { it?.coffee }

  private companion object {
    const val STOP_TIMEOUT_MS = 5_000L
  }
}
