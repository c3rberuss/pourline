package dev.josueamaya.pourline.recipe

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import dev.josueamaya.pourline.grinder.GrinderEntity
import dev.josueamaya.pourline.history.BrewMethod

/**
 * Una receta. Las magnitudes van en gramos, milisegundos y °C.
 *
 * `builtInKey` y `localizedNames` ya no se usan (SPEC-012, versión 8): la migración los dejó en `NULL` y `0`. Las columnas
 * siguen porque SQLite no las borra sin reconstruir la tabla.
 */
@Entity(
  tableName = "recipes",
  foreignKeys = [
    ForeignKey(
      entity = GrinderEntity::class,
      parentColumns = ["id"],
      childColumns = ["grinderId"],
      onDelete = ForeignKey.SET_NULL,
    ),
  ],
  indices = [Index(value = ["builtInKey"], unique = true), Index("grinderId")],
)
data class RecipeEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val name: String,
  val method: BrewMethod? = null,
  val methodOther: String? = null,
  val waterTempC: Double? = null,
  val notes: String? = null,
  val doseG: Double,
  /** Único (varias filas pueden tenerlo `null`). */
  val builtInKey: String? = null,
  val localizedNames: Boolean = false,
  val updatedAtEpochMs: Long,
  // Molino y ajuste de molienda (SPEC-006). `grindLabel` es la copia de texto, con el molino y el ajuste formateados.
  /** El molino; `null` sin molino o si se borró (`SET NULL`: la receta conserva [grindValue] y [grindLabel]). */
  val grinderId: Long? = null,
  /** El ajuste en unidades base del molino. */
  val grindValue: Double? = null,
  val grindLabel: String? = null,
  // SPEC-012: quién creó la receta, dónde se publicó y de qué receta del catálogo viene.
  val author: String? = null,
  val sourceUrl: String? = null,
  /** La clave de catálogo de la que viene la receta (también la de las antiguas de fábrica); sin índice. */
  val catalogKey: String? = null,
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
  /** `DEFAULT 'POUR'` para que `ALTER TABLE` de la migración 3 → 4 deje los pasos existentes como vertidos. */
  @ColumnInfo(defaultValue = "POUR") val type: StageType = StageType.POUR,
  val instruction: String? = null,
)

data class RecipeWithStages(
  @Embedded val recipe: RecipeEntity,
  @Relation(parentColumn = "id", entityColumn = "recipeId") val stages: List<RecipeStageEntity>,
)

/** Lo que `delete` deja en `pendingUndo` para poder restaurarlo con el mismo id. */
data class DeletedRecipe(val recipe: RecipeEntity, val stages: List<RecipeStageEntity>)
