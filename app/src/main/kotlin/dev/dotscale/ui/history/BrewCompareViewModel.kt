package dev.dotscale.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.dotscale.history.BrewRecord
import dev.dotscale.history.BrewRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

sealed interface BrewCompareState {
  data object Loading : BrewCompareState

  data class Loaded(val a: BrewRecord, val b: BrewRecord) : BrewCompareState

  /** Una de las dos filas ya no existe (borrada aquí o en otro sitio): la pantalla vuelve atrás. */
  data object Gone : BrewCompareState
}

class BrewCompareViewModel(repository: BrewRepository, aId: Long, bId: Long) : ViewModel() {
  val state: StateFlow<BrewCompareState> = combine(repository.observe(aId), repository.observe(bId)) { a, b ->
    if (a == null || b == null) BrewCompareState.Gone else BrewCompareState.Loaded(a, b)
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), BrewCompareState.Loading)

  private companion object {
    const val STOP_TIMEOUT_MS = 5_000L
  }
}
