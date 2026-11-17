package dev.josueamaya.pourline.ui.coffees

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.josueamaya.pourline.coffee.CoffeeDetail
import dev.josueamaya.pourline.coffee.CoffeeRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface CoffeeDetailState {
  data object Loading : CoffeeDetailState

  data class Loaded(val detail: CoffeeDetail) : CoffeeDetailState

  /** El café ya no existe (se borró): la pantalla vuelve atrás. */
  data object Gone : CoffeeDetailState
}

class CoffeeDetailViewModel(private val repository: CoffeeRepository, private val id: Long) : ViewModel() {
  val state: StateFlow<CoffeeDetailState> = repository.coffee(id)
    .map { detail -> if (detail == null) CoffeeDetailState.Gone else CoffeeDetailState.Loaded(detail) }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), CoffeeDetailState.Loading)

  fun setArchived(archived: Boolean) {
    viewModelScope.launch { repository.setArchived(id, archived) }
  }

  fun delete() {
    viewModelScope.launch { repository.delete(id) }
  }

  private companion object {
    const val STOP_TIMEOUT_MS = 5_000L
  }
}
