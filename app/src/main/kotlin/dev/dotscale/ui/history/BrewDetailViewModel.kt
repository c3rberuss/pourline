package dev.dotscale.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.dotscale.history.BrewRecord
import dev.dotscale.history.BrewRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface BrewDetailState {
  data object Loading : BrewDetailState

  data class Loaded(val record: BrewRecord) : BrewDetailState

  /** La fila ya no existe (borrada aquí o en otro sitio): la pantalla vuelve atrás. */
  data object Gone : BrewDetailState
}

class BrewDetailViewModel(private val repository: BrewRepository, private val id: Long) : ViewModel() {
  val state: StateFlow<BrewDetailState> = repository.observe(id)
    .map { if (it == null) BrewDetailState.Gone else BrewDetailState.Loaded(it) }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), BrewDetailState.Loading)

  /** Borra la preparación; al desaparecer la fila, [state] pasa a `Gone` y la pantalla vuelve a la lista. */
  fun delete() {
    viewModelScope.launch { repository.delete(id) }
  }

  private companion object {
    const val STOP_TIMEOUT_MS = 5_000L
  }
}
