package dev.dotscale.ui.scale

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.dotscale.history.BrewFilter
import dev.dotscale.history.BrewListItem
import dev.dotscale.history.BrewRepository
import dev.dotscale.recipe.RecipeListItem
import dev.dotscale.recipe.RecipeRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * Las dos listas del selector de objetivo. Vive en la entrada de Scale, pero sus flujos solo se recogen con
 * el selector abierto (`WhileSubscribed`), así que Scale no carga dos listas que casi nunca se muestran.
 */
class TargetPickerViewModel(recipeRepository: RecipeRepository, brewRepository: BrewRepository) : ViewModel() {
  val recipes: StateFlow<List<RecipeListItem>> =
    recipeRepository.list().stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

  val brews: StateFlow<List<BrewListItem>> =
    brewRepository.list(BrewFilter()).stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

  private companion object {
    const val STOP_TIMEOUT_MS = 5_000L
  }
}
