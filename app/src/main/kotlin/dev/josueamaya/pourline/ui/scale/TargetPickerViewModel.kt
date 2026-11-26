package dev.josueamaya.pourline.ui.scale

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.josueamaya.pourline.history.BrewFilter
import dev.josueamaya.pourline.history.BrewListItem
import dev.josueamaya.pourline.history.BrewRepository
import dev.josueamaya.pourline.recipe.RecipeListItem
import dev.josueamaya.pourline.recipe.RecipeRepository
import dev.josueamaya.pourline.recipe.catalog.CatalogLanguageSource
import dev.josueamaya.pourline.recipe.catalog.RecipeCatalogRepository
import dev.josueamaya.pourline.ui.recipes.catalog.CatalogBrowser
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Las listas del selector de objetivo: recetas, preparaciones y recomendadas (SPEC-012 D38). Vive en la entrada de Scale,
 * pero sus flujos solo se recogen con el selector abierto (`WhileSubscribed`), así que Scale no carga listas que casi nunca
 * se muestran.
 */
class TargetPickerViewModel(
  recipeRepository: RecipeRepository,
  brewRepository: BrewRepository,
  catalogRepository: RecipeCatalogRepository,
  language: CatalogLanguageSource,
) : ViewModel() {
  val catalog = CatalogBrowser(catalogRepository, recipeRepository.catalogKeys(), language, viewModelScope)

  val recipes: StateFlow<List<RecipeListItem>> =
    recipeRepository.list().stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

  val brews: StateFlow<List<BrewListItem>> =
    // Las hechas sin báscula no tienen curva real: no sirven de referencia (SPEC-007 §3.6).
    brewRepository.list(BrewFilter()).map { rows -> rows.filter { !it.scaleless } }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

  private companion object {
    const val STOP_TIMEOUT_MS = 5_000L
  }
}
