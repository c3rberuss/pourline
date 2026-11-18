package dev.josueamaya.pourline.ui.recipes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.josueamaya.pourline.grinder.GrindPick
import dev.josueamaya.pourline.grinder.GrindResolution
import dev.josueamaya.pourline.grinder.GrindTexts
import dev.josueamaya.pourline.grinder.GrinderRepository
import dev.josueamaya.pourline.grinder.defaultPick
import dev.josueamaya.pourline.grinder.resolveSharedGrind
import dev.josueamaya.pourline.grinder.settingFor
import dev.josueamaya.pourline.recipe.RecipeRepository
import dev.josueamaya.pourline.recipe.share.DecodeResult
import dev.josueamaya.pourline.recipe.share.ImportError
import dev.josueamaya.pourline.recipe.share.ImportNotice
import dev.josueamaya.pourline.recipe.share.PendingRecipeImport
import dev.josueamaya.pourline.recipe.share.RecipeShareCodec
import dev.josueamaya.pourline.recipe.share.SharedRecipe
import dev.josueamaya.pourline.recipe.share.isIdentical
import dev.josueamaya.pourline.recipe.share.uniqueName
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface RecipeImportUi {
  data object Loading : RecipeImportUi

  /**
   * La receta decodificada. [identical] es que ya hay una igual (no impide guardar); [grind] es la resolución de la
   * molienda y [pick] lo elegido en una [GrindResolution.Convertible].
   */
  data class Ready(
    val shared: SharedRecipe,
    val identical: Boolean,
    val grind: GrindResolution,
    val pick: GrindPick?,
    val saving: Boolean = false,
  ) : RecipeImportUi

  /** Guardada: la pantalla se cierra. */
  data object Saved : RecipeImportUi

  /** El URI ya no se puede leer: se avisó con un mensaje y la pantalla se cierra. */
  data class Failed(val error: ImportError) : RecipeImportUi
}

/**
 * La vista previa de una receta importada (SPEC-009 §3.6). Decodifica [uri] al crearse, así que una recreación de la
 * pantalla vuelve a mostrar la misma receta sin guardar nada más. Al guardar vuelve a resolver la molienda con los
 * molinos de ese momento (§4).
 */
class RecipeImportViewModel(
  private val uri: String,
  private val recipes: RecipeRepository,
  private val grinders: GrinderRepository,
  private val texts: GrindTexts,
  private val imports: PendingRecipeImport,
) : ViewModel() {
  private val _ui = MutableStateFlow<RecipeImportUi>(RecipeImportUi.Loading)
  val ui: StateFlow<RecipeImportUi> = _ui.asStateFlow()

  init {
    viewModelScope.launch { load() }
  }

  private suspend fun load() {
    val shared = when (val decoded = RecipeShareCodec.decode(uri)) {
      is DecodeResult.Ok -> decoded.shared
      DecodeResult.NotPourline -> return fail(ImportError.NOT_POURLINE)
      DecodeResult.NewerVersion -> return fail(ImportError.NEWER_VERSION)
      DecodeResult.Corrupt -> return fail(ImportError.CORRUPT)
      DecodeResult.Invalid -> return fail(ImportError.INVALID)
    }
    val identical = recipes.all().any { isIdentical(it, shared.recipe) }
    val grind = resolve(shared)
    _ui.value = RecipeImportUi.Ready(shared, identical, grind, grind.defaultPick())
  }

  private fun fail(error: ImportError) {
    imports.notify(ImportNotice.Failed(error))
    _ui.value = RecipeImportUi.Failed(error)
  }

  private suspend fun resolve(shared: SharedRecipe): GrindResolution {
    val owned = grinders.owned().first()
    val catalog = shared.grind?.catalogKey?.let { grinders.byCatalogKey(it) }
    val calibrations = catalog?.let { grinders.calibrations(it.id).first() }.orEmpty()
    return resolveSharedGrind(shared.grind, owned, catalog, calibrations, texts)
  }

  fun pick(choice: GrindPick) {
    _ui.update { if (it is RecipeImportUi.Ready) it.copy(pick = choice) else it }
  }

  fun save() {
    val current = _ui.value as? RecipeImportUi.Ready ?: return
    if (current.saving) return
    _ui.value = current.copy(saving = true)
    viewModelScope.launch {
      // Los molinos pudieron cambiar mientras se miraba la vista previa: se resuelve otra vez (§4).
      val owned = grinders.owned().first()
      val resolution = resolve(current.shared)
      val setting = resolution.settingFor(current.pick, owned, texts)
      val recipe = current.shared.recipe
      val name = uniqueName(recipe.name, recipes.list().first().map { it.name })
      recipes.save(recipe.copy(id = 0, name = name, grind = setting, builtInKey = null, localizedNames = false))
      imports.notify(ImportNotice.Added)
      _ui.value = RecipeImportUi.Saved
    }
  }
}
