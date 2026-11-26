package dev.josueamaya.pourline.ui.recipes.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.josueamaya.pourline.grinder.GrindCalibration
import dev.josueamaya.pourline.grinder.GrindPick
import dev.josueamaya.pourline.grinder.GrindResolution
import dev.josueamaya.pourline.grinder.GrindTexts
import dev.josueamaya.pourline.grinder.GrinderRepository
import dev.josueamaya.pourline.grinder.defaultPick
import dev.josueamaya.pourline.recipe.Recipe
import dev.josueamaya.pourline.recipe.RecipeRepository
import dev.josueamaya.pourline.recipe.catalog.CatalogLanguageSource
import dev.josueamaya.pourline.recipe.catalog.CatalogRecipe
import dev.josueamaya.pourline.recipe.catalog.RecipeCatalogRepository
import dev.josueamaya.pourline.recipe.catalog.catalogCopy
import dev.josueamaya.pourline.recipe.catalog.catalogGrindResolution
import dev.josueamaya.pourline.recipe.catalog.hasCopy
import dev.josueamaya.pourline.recipe.catalog.toRecipe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface CatalogDetailUi {
  data object Loading : CatalogDetailUi

  /** La clave ya no está en el catálogo (una actualización con la pantalla abierta). */
  data object Missing : CatalogDetailUi

  /**
   * La receta en el idioma de ahora. [display] es la receta como [Recipe] (sin molienda); [resolution] y [pick], la molienda
   * y lo elegido en una [GrindResolution.Convertible]; [alreadySaved], que ya hay una copia (no impide guardar otra).
   */
  data class Ready(
    val recipe: CatalogRecipe,
    val display: Recipe,
    val resolution: GrindResolution,
    val pick: GrindPick?,
    val alreadySaved: Boolean,
    val saving: Boolean,
  ) : CatalogDetailUi
}

/**
 * El detalle de una receta del catálogo, de solo lectura (SPEC-012 §3.8, D36). Sigue al catálogo y a mis molinos, así que
 * una actualización o un molino nuevo se ve al instante. "Guardar en mis recetas" resuelve la molienda otra vez con los
 * molinos de ese momento (§4).
 */
class CatalogRecipeDetailViewModel(
  private val key: String,
  private val catalog: RecipeCatalogRepository,
  private val recipes: RecipeRepository,
  private val grinders: GrinderRepository,
  private val texts: GrindTexts,
  private val language: CatalogLanguageSource,
) : ViewModel() {
  private val picked = MutableStateFlow<GrindPick?>(null)
  private val saving = MutableStateFlow(false)
  private val _saved = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

  /** Se emite al guardar la copia: la pantalla muestra "Receta guardada en Mis recetas". */
  val saved: SharedFlow<Unit> = _saved

  @OptIn(ExperimentalCoroutinesApi::class)
  val ui: StateFlow<CatalogDetailUi> = catalog.entry(key).flatMapLatest { entry ->
    if (entry == null) flowOf(CatalogDetailUi.Missing) else ready(entry.inLanguage(language.current()))
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), CatalogDetailUi.Loading)

  init {
    catalog.ensureLoaded()
  }

  private fun ready(recipe: CatalogRecipe): Flow<CatalogDetailUi> = flow {
    val catalogGrinder = recipe.grinderRef?.let { grinders.byCatalogKey(it.catalogKey) }
    val calibrations: Flow<List<GrindCalibration>> = catalogGrinder?.let { grinders.calibrations(it.id) } ?: flowOf(emptyList())
    val resolutions = combine(grinders.owned(), calibrations) { owned, pairs -> catalogGrindResolution(recipe, owned, catalogGrinder, pairs, texts) }
    emitAll(
      combine(resolutions, recipes.catalogKeys(), picked, saving) { resolution, saved, choice, busy ->
        CatalogDetailUi.Ready(
          recipe = recipe,
          display = recipe.toRecipe(),
          resolution = resolution,
          pick = choice ?: resolution.defaultPick(),
          alreadySaved = hasCopy(recipe.key, saved),
          saving = busy,
        )
      },
    )
  }

  fun pick(choice: GrindPick) {
    picked.value = choice
  }

  fun save() {
    val current = ui.value as? CatalogDetailUi.Ready ?: return
    if (saving.value) return
    saving.value = true
    viewModelScope.launch {
      try {
        // Los molinos pudieron cambiar mientras se miraba el detalle: se resuelve otra vez (§4).
        val catalogGrinder = current.recipe.grinderRef?.let { grinders.byCatalogKey(it.catalogKey) }
        val pairs = catalogGrinder?.let { grinders.calibrations(it.id).first() }.orEmpty()
        val owned = grinders.owned().first()
        val resolution = catalogGrindResolution(current.recipe, owned, catalogGrinder, pairs, texts)
        val names = recipes.list().first().map { it.name }
        recipes.save(catalogCopy(current.recipe, resolution, current.pick, owned, names, texts))
        _saved.tryEmit(Unit)
      } finally {
        saving.value = false
      }
    }
  }

  private companion object {
    const val STOP_TIMEOUT_MS = 5_000L
  }
}
