package dev.josueamaya.pourline.recipe.catalog

import dev.josueamaya.pourline.grinder.GrindCalibration
import dev.josueamaya.pourline.grinder.GrindTexts
import dev.josueamaya.pourline.grinder.Grinder
import dev.josueamaya.pourline.grinder.GrinderRepository
import dev.josueamaya.pourline.session.CatalogTarget
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

/**
 * La receta del catálogo como referencia de Preparar (SPEC-012 §3.7, D23): combina la entrada del catálogo (en el idioma de
 * ahora), mis molinos y el molino del catálogo de molinos con sus pares de calibración. La molienda de origen apunta a la
 * fila de ese molino (D30) y el molino preferido sale de la resolución de la molienda (el mismo molino o el primer
 * convertible).
 */
class CatalogTargetSource(
  private val catalog: RecipeCatalogRepository,
  private val grinders: GrinderRepository,
  private val language: CatalogLanguageSource,
  private val texts: GrindTexts,
) {
  /** Emite `null` si la clave no está en el catálogo (una vez leída la copia local). */
  @OptIn(ExperimentalCoroutinesApi::class)
  fun target(key: String): Flow<CatalogTarget?> = catalog.entry(key).flatMapLatest { entry ->
    if (entry == null) flowOf(null) else resolved(entry.inLanguage(language.current()))
  }

  private fun resolved(recipe: CatalogRecipe): Flow<CatalogTarget?> = flow {
    val catalogGrinder = recipe.grinderRef?.let { grinders.byCatalogKey(it.catalogKey) }
    val calibrations: Flow<List<GrindCalibration>> = catalogGrinder?.let { grinders.calibrations(it.id) } ?: flowOf(emptyList())
    emitAll(combine(grinders.owned(), calibrations) { owned, pairs -> target(recipe, owned, catalogGrinder, pairs) })
  }

  private fun target(recipe: CatalogRecipe, owned: List<Grinder>, catalogGrinder: Grinder?, calibrations: List<GrindCalibration>): CatalogTarget {
    val resolution = catalogGrindResolution(recipe, owned, catalogGrinder, calibrations, texts)
    return CatalogTarget(
      recipe = recipe.toRecipe().copy(grind = catalogSourceGrind(recipe, catalogGrinder, texts)),
      preferredGrinderId = resolution.preferredGrinderId(),
    )
  }
}
