package dev.dotscale.recipe

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
abstract class RecipeDao {

  @Transaction
  @Query("SELECT * FROM recipes")
  abstract fun observeAll(): Flow<List<RecipeWithStages>>

  @Transaction
  @Query("SELECT * FROM recipes WHERE id = :id")
  abstract fun observe(id: Long): Flow<RecipeWithStages?>

  @Transaction
  @Query("SELECT * FROM recipes WHERE id = :id")
  abstract suspend fun get(id: Long): RecipeWithStages?

  @Query("SELECT * FROM recipes WHERE builtInKey = :key")
  abstract suspend fun byBuiltInKey(key: String): RecipeEntity?

  @Insert
  protected abstract suspend fun insertRecipeRow(recipe: RecipeEntity): Long

  @Update
  protected abstract suspend fun updateRecipeRow(recipe: RecipeEntity)

  @Insert
  protected abstract suspend fun insertStageRows(stages: List<RecipeStageEntity>)

  @Query("DELETE FROM recipe_stages WHERE recipeId = :id")
  protected abstract suspend fun deleteStageRows(id: Long)

  @Query("DELETE FROM recipes WHERE id = :id")
  protected abstract suspend fun deleteRecipeRow(id: Long)

  /**
   * Inserta (`id = 0`) o actualiza la fila y reemplaza sus etapas en una transacción. Nunca borra la
   * receta, así que quien la observe no ve `null` en medio. [stages] llevan `recipeId = 0` y se
   * numeran por orden. Devuelve el id.
   */
  @Transaction
  open suspend fun save(recipe: RecipeEntity, stages: List<RecipeStageEntity>): Long {
    val id = if (recipe.id == 0L) {
      insertRecipeRow(recipe)
    } else {
      updateRecipeRow(recipe)
      deleteStageRows(recipe.id)
      recipe.id
    }
    insertStageRows(stages.mapIndexed { i, stage -> stage.copy(recipeId = id, position = i) })
    return id
  }

  /** Borra la receta (y sus etapas, por la cascada) y devuelve lo borrado, o `null` si no existía. */
  @Transaction
  open suspend fun deleteRecipe(id: Long): DeletedRecipe? {
    val existing = get(id) ?: return null
    deleteRecipeRow(id)
    return DeletedRecipe(existing.recipe, existing.stages.sortedBy { it.position })
  }

  /** Reinserta la fila y las etapas con el mismo id. */
  @Transaction
  open suspend fun restore(deleted: DeletedRecipe) {
    insertRecipeRow(deleted.recipe)
    insertStageRows(deleted.stages)
  }

  /**
   * Devuelve a fábrica las predefinidas que existen (conservan su id) y crea las que faltan. No toca el
   * resto de recetas. Cada elemento es la receta de fábrica con `id = 0` y sus etapas.
   */
  @Transaction
  open suspend fun restoreBuiltIns(factory: List<Pair<RecipeEntity, List<RecipeStageEntity>>>) {
    for ((recipe, stages) in factory) {
      val existing = recipe.builtInKey?.let { byBuiltInKey(it) }
      save(recipe.copy(id = existing?.id ?: 0), stages)
    }
  }
}
