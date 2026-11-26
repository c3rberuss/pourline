package dev.josueamaya.pourline.ui.recipes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.josueamaya.pourline.recipe.DeletedRecipe
import dev.josueamaya.pourline.recipe.RecipeListItem
import dev.josueamaya.pourline.recipe.RecipeRepository
import dev.josueamaya.pourline.recipe.catalog.CatalogLanguageSource
import dev.josueamaya.pourline.recipe.catalog.RecipeCatalogRepository
import dev.josueamaya.pourline.ui.recipes.catalog.CatalogBrowser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Las dos listas de la pestaña Recetas: las del usuario y las recomendadas del catálogo (SPEC-012). */
enum class RecipesSegment { MINE, CATALOG }

/**
 * `items` empieza en `null` (cargando) para no mostrar el estado vacío antes de la primera emisión. El `ViewModel` de la
 * raíz de Recetas vive mientras viva la app: [segment] se recuerda durante la sesión.
 */
class RecipesViewModel(
  private val repository: RecipeRepository,
  catalogRepository: RecipeCatalogRepository,
  language: CatalogLanguageSource,
) : ViewModel() {
  private val _segment = MutableStateFlow(RecipesSegment.MINE)
  val segment: StateFlow<RecipesSegment> = _segment

  val catalog = CatalogBrowser(catalogRepository, repository.catalogKeys(), language, viewModelScope)

  fun selectSegment(value: RecipesSegment) {
    _segment.value = value
  }

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
