package dev.josueamaya.pourline.ui.coffees

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.josueamaya.pourline.coffee.CoffeeListItem
import dev.josueamaya.pourline.coffee.CoffeeRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * Los cafés del selector (los no archivados, también los agotados). Vive en la entrada de la pantalla que lo
 * abre, pero la lista solo se recoge con el selector abierto (`WhileSubscribed`). La búsqueda del selector es
 * local ([dev.josueamaya.pourline.coffee.CoffeeSearch.matches]).
 */
class CoffeePickerViewModel(repository: CoffeeRepository) : ViewModel() {
  val pickable: StateFlow<List<CoffeeListItem>> =
    repository.pickable().stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

  private companion object {
    const val STOP_TIMEOUT_MS = 5_000L
  }
}
