package dev.josueamaya.pourline.session

import dev.josueamaya.pourline.history.BrewRecord
import dev.josueamaya.pourline.history.BrewRepository
import dev.josueamaya.pourline.recipe.Recipe
import dev.josueamaya.pourline.recipe.RecipeRepository
import dev.josueamaya.pourline.recipe.catalog.CatalogTargetSource
import kotlinx.coroutines.flow.Flow

/**
 * De dónde saca `ScaleViewModel` la receta o la preparación elegidas como objetivo. Cada flujo emite
 * `null` si el origen se borra. Lo implementa [RepositoryTargetReferences]; en los tests, un falso.
 */
interface TargetReferences {
  fun recipe(id: Long): Flow<Recipe?>

  fun brew(id: Long): Flow<BrewRecord?>

  /** La receta del catálogo con esa [key]; emite `null` si no está (cuando ya se leyó la copia local). */
  fun catalog(key: String): Flow<CatalogTarget?>
}

/**
 * Una receta del catálogo lista para usarla como referencia: la receta (con la molienda de origen en `grind`) y el molino
 * que Preparar preselecciona al moler (SPEC-012 §3.7).
 */
data class CatalogTarget(val recipe: Recipe, val preferredGrinderId: Long?)

class RepositoryTargetReferences(
  private val recipes: RecipeRepository,
  private val brews: BrewRepository,
  private val catalog: CatalogTargetSource,
) : TargetReferences {
  override fun recipe(id: Long): Flow<Recipe?> = recipes.observe(id)

  override fun brew(id: Long): Flow<BrewRecord?> = brews.observe(id)

  override fun catalog(key: String): Flow<CatalogTarget?> = catalog.target(key)
}
