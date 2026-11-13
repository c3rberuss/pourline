package dev.dotscale.ui.recipes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.dotscale.recipe.DeletedRecipe
import dev.dotscale.recipe.RecipeListItem
import dev.dotscale.recipe.RecipeRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** `items` empieza en `null` (cargando) para no mostrar el estado vacío antes de la primera emisión. */
class RecipesViewModel(private val repository: RecipeRepository) : ViewModel() {
  val items: StateFlow<List<RecipeListItem>?> =
    repository.list().stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

  val pendingUndo: StateFlow<DeletedRecipe?> = repository.pendingUndo

  fun restore(deleted: DeletedRecipe) {
    viewModelScope.launch { repository.restore(deleted) }
  }

  /** Con [deleted], solo vacía el deshacer si sigue siendo ese borrado. */
  fun clearPendingUndo(deleted: DeletedRecipe? = null) = repository.clearPendingUndo(deleted)

  private companion object {
    const val STOP_TIMEOUT_MS = 5_000L
  }
}
