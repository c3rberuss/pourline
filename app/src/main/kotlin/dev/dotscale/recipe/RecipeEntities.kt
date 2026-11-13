package dev.dotscale.recipe

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import dev.dotscale.history.BrewMethod

/**
 * Una receta. Las magnitudes van en gramos, milisegundos y °C.
 *
 * `name` guarda el texto en el idioma de la inserción como respaldo: con [localizedNames] y
 * [builtInKey], el repositorio devuelve el nombre traducido en el idioma actual.
 */
@Entity(tableName = "recipes", indices = [Index(value = ["builtInKey"], unique = true)])
data class RecipeEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val name: String,
  val method: BrewMethod? = null,
  val methodOther: String? = null,
  val grind: String? = null,
  val waterTempC: Double? = null,
  val notes: String? = null,
  val doseG: Double,
  /** Único (varias filas pueden tenerlo `null`). */
  val builtInKey: String? = null,
  val localizedNames: Boolean = false,
  val updatedAtEpochMs: Long,
)

@Entity(
  tableName = "recipe_stages",
  primaryKeys = ["recipeId", "position"],
  foreignKeys = [
    ForeignKey(
      entity = RecipeEntity::class,
      parentColumns = ["id"],
      childColumns = ["recipeId"],
      onDelete = ForeignKey.CASCADE,
    ),
  ],
)
data class RecipeStageEntity(
  val recipeId: Long,
  val position: Int,
  val name: String?,
  val startMs: Long,
  val targetWaterG: Double,
  val durationMs: Long,
)

data class RecipeWithStages(
  @Embedded val recipe: RecipeEntity,
  @Relation(parentColumn = "id", entityColumn = "recipeId") val stages: List<RecipeStageEntity>,
)

/** Lo que `delete` deja en `pendingUndo` para poder restaurarlo con el mismo id. */
data class DeletedRecipe(val recipe: RecipeEntity, val stages: List<RecipeStageEntity>)
