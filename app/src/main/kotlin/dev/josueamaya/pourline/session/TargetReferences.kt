package dev.josueamaya.pourline.session

import dev.josueamaya.pourline.history.BrewRecord
import dev.josueamaya.pourline.history.BrewRepository
import dev.josueamaya.pourline.recipe.Recipe
import dev.josueamaya.pourline.recipe.RecipeRepository
import kotlinx.coroutines.flow.Flow

/**
 * De dónde saca `ScaleViewModel` la receta o la preparación elegidas como objetivo. Cada flujo emite
 * `null` si el origen se borra. Lo implementa [RepositoryTargetReferences]; en los tests, un falso.
 */
interface TargetReferences {
  fun recipe(id: Long): Flow<Recipe?>

  fun brew(id: Long): Flow<BrewRecord?>
}

class RepositoryTargetReferences(
  private val recipes: RecipeRepository,
  private val brews: BrewRepository,
) : TargetReferences {
  override fun recipe(id: Long): Flow<Recipe?> = recipes.observe(id)

  override fun brew(id: Long): Flow<BrewRecord?> = brews.observe(id)
}
