package dev.dotscale.grinder

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import dev.dotscale.history.BrewEntity
import dev.dotscale.history.BrewMethod
import dev.dotscale.recipe.RecipeEntity

/**
 * Un molino (SPEC-006). Un predefinido tiene [catalogKey] (único; varias filas pueden tenerlo `null`) y es de solo
 * lectura; un molino propio tiene `catalogKey = null` y, si es un duplicado, [duplicatedFromKey].
 * Las magnitudes van en unidades base (las de la última unidad de `grinder_units`).
 */
@Entity(tableName = "grinders", indices = [Index(value = ["catalogKey"], unique = true)])
data class GrinderEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val catalogKey: String? = null,
  val brand: String,
  val model: String,
  val direction: GrindDirection,
  val scaleMin: Double,
  val scaleMax: Double,
  val step: Double,
  /** "Mis molinos": los propios siempre; un predefinido, cuando el usuario lo marca como "Lo tengo". */
  val owned: Boolean,
  /** Un predefinido que desapareció del catálogo y sigue en uso. */
  val retired: Boolean,
  val sourceUrl: String? = null,
  val sourceName: String? = null,
  val duplicatedFromKey: String? = null,
  val updatedAtEpochMs: Long,
)

/** La jerarquía de unidades, de la más gruesa (posición 0, la primaria) a la más fina (la base). */
@Entity(
  tableName = "grinder_units",
  primaryKeys = ["grinderId", "position"],
  foreignKeys = [
    ForeignKey(entity = GrinderEntity::class, parentColumns = ["id"], childColumns = ["grinderId"], onDelete = ForeignKey.CASCADE),
  ],
)
data class GrinderUnitEntity(
  val grinderId: Long,
  val position: Int,
  val unit: GrindUnit,
  val perParent: Int?,
)

@Entity(
  tableName = "grinder_method_ranges",
  primaryKeys = ["grinderId", "method"],
  foreignKeys = [
    ForeignKey(entity = GrinderEntity::class, parentColumns = ["id"], childColumns = ["grinderId"], onDelete = ForeignKey.CASCADE),
  ],
)
data class GrinderMethodRangeEntity(
  val grinderId: Long,
  val method: BrewMethod,
  val min: Double,
  val max: Double,
)

@Entity(
  tableName = "grinder_anchors",
  primaryKeys = ["grinderId", "level"],
  foreignKeys = [
    ForeignKey(entity = GrinderEntity::class, parentColumns = ["id"], childColumns = ["grinderId"], onDelete = ForeignKey.CASCADE),
  ],
)
data class GrinderAnchorEntity(
  val grinderId: Long,
  val level: GrindLevel,
  val value: Double,
)

data class GrinderWithDetails(
  @Embedded val grinder: GrinderEntity,
  @Relation(parentColumn = "id", entityColumn = "grinderId") val units: List<GrinderUnitEntity>,
  @Relation(parentColumn = "id", entityColumn = "grinderId") val ranges: List<GrinderMethodRangeEntity>,
  @Relation(parentColumn = "id", entityColumn = "grinderId") val anchors: List<GrinderAnchorEntity>,
)

/** Las filas de un molino, para guardarlas juntas. */
data class GrinderRows(
  val grinder: GrinderEntity,
  val units: List<GrinderUnitEntity>,
  val ranges: List<GrinderMethodRangeEntity>,
  val anchors: List<GrinderAnchorEntity>,
)

fun GrinderWithDetails.toGrinder(): Grinder = Grinder(
  id = grinder.id,
  catalogKey = grinder.catalogKey,
  brand = grinder.brand,
  model = grinder.model,
  units = units.sortedBy { it.position }.map { GrindUnitLevel(it.unit, it.perParent) },
  scaleMin = grinder.scaleMin,
  scaleMax = grinder.scaleMax,
  step = grinder.step,
  direction = grinder.direction,
  ranges = ranges.associate { it.method to GrindRange(it.min, it.max) },
  anchors = anchors.associate { it.level to it.value },
  owned = grinder.owned,
  retired = grinder.retired,
  sourceUrl = grinder.sourceUrl,
  sourceName = grinder.sourceName,
  duplicatedFromKey = grinder.duplicatedFromKey,
)

/** Las filas de un molino; con `id = 0` Room asigna el id al insertar y el repositorio reparte el recibido en los hijos. */
fun Grinder.toRows(nowMs: Long): GrinderRows = GrinderRows(
  grinder = GrinderEntity(
    id = id,
    catalogKey = catalogKey,
    brand = brand.trim(),
    model = model.trim(),
    direction = direction,
    scaleMin = scaleMin,
    scaleMax = scaleMax,
    step = step,
    owned = owned,
    retired = retired,
    sourceUrl = sourceUrl,
    sourceName = sourceName,
    duplicatedFromKey = duplicatedFromKey,
    updatedAtEpochMs = nowMs,
  ),
  units = units.mapIndexed { i, u -> GrinderUnitEntity(id, i, u.unit, u.perParent) },
  ranges = ranges.map { (m, r) -> GrinderMethodRangeEntity(id, m, r.min, r.max) },
  anchors = anchors.map { (l, v) -> GrinderAnchorEntity(id, l, v) },
)

/** El ajuste guardado en las tres columnas de una receta o una preparación, o `null` si están vacías. */
fun grindSettingOf(grinderId: Long?, value: Double?, label: String?): GrindSetting? =
  GrindSetting(grinderId, value, label).takeUnless { it.isEmpty }

fun RecipeEntity.toGrindSetting(): GrindSetting? = grindSettingOf(grinderId, grindValue, grindLabel)

fun BrewEntity.toGrindSetting(): GrindSetting? = grindSettingOf(grinderId, grindValue, grindLabel)
