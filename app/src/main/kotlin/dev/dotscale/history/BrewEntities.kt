package dev.dotscale.history

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.dotscale.coffee.CoffeeEntity
import dev.dotscale.brew.BrewSample
import dev.dotscale.brew.BrewSummary
import dev.dotscale.brew.FinishReason
import dev.dotscale.brew.StabilityLevel
import dev.dotscale.brew.TargetCurve
import dev.dotscale.protocol.ScaleModel
import dev.dotscale.protocol.WeightUnit

/**
 * Una preparación. La lista nunca carga la curva, que vive aparte en [BrewCurveEntity].
 * Todas las magnitudes van en gramos y g/s, sea cual sea [unit].
 *
 * `equals` compara [preview] por referencia (es un `ByteArray`): para comparar contenido, usa [BrewListItem].
 */
@Entity(
  tableName = "brews",
  foreignKeys = [
    ForeignKey(
      entity = CoffeeEntity::class,
      parentColumns = ["id"],
      childColumns = ["coffeeId"],
      onDelete = ForeignKey.SET_NULL,
    ),
  ],
  indices = [Index("startedAtEpochMs"), Index("coffeeId")],
)
data class BrewEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val startedAtEpochMs: Long,
  // Datos manuales.
  val name: String? = null,
  val coffee: String? = null,
  val method: BrewMethod? = null,
  val methodOther: String? = null,
  val grind: String? = null,
  val waterTempC: Double? = null,
  val notes: String? = null,
  // BrewSummary.
  val totalTimeMs: Long,
  val totalWaterG: Double,
  val avgFlowGps: Double,
  val maxFlowGps: Double,
  val stabilityScore: Int?,
  val stabilityLevel: StabilityLevel?,
  val shareStable: Double,
  val shareIrregular: Double,
  val shareTurbulent: Double,
  val doseG: Double?,
  val finalRatio: Double?,
  val finishReason: FinishReason,
  val interrupted: Boolean,
  /** Unidad vigente al terminar; la UI del historial la usa para mostrar. */
  val unit: WeightUnit,
  val scaleModel: ScaleModel?,
  val simulated: Boolean,
  val metricVersion: Int,
  /** Minicurva: 48 bytes (ver [BrewPreview]). */
  val preview: ByteArray,
  // Referencia usada y ajuste a ella (SPEC-003). `null` sin referencia y en las anteriores a la versión 2.
  val referenceType: ReferenceType? = null,
  /** Sin clave foránea: el origen puede borrarse. */
  val referenceId: Long? = null,
  val referenceName: String? = null,
  /** `BrewFit.targetWaterG`. */
  val targetWaterG: Double? = null,
  /** `BrewFit.targetEndMs`. */
  val targetEndMs: Long? = null,
  /** `BrewFit.score`. */
  val fitScore: Int? = null,
  // Catálogo de cafés (SPEC-004). `coffee` pasa a ser la copia del nombre del café vinculado.
  /** El café del catálogo; `null` sin vínculo, o si el café se borró (`SET NULL`: la preparación conserva el nombre). */
  val coffeeId: Long? = null,
  /** Valoración de 1 a 5; `null` sin valorar. */
  val rating: Int? = null,
)

/** Muestras y vertidos codificados con [BrewCurveCodec]. 1:1 con [BrewEntity]. */
@Entity(
  tableName = "brew_curves",
  foreignKeys = [
    ForeignKey(
      entity = BrewEntity::class,
      parentColumns = ["id"],
      childColumns = ["brewId"],
      onDelete = ForeignKey.CASCADE,
    ),
  ],
)
data class BrewCurveEntity(
  @PrimaryKey val brewId: Long,
  val data: ByteArray,
)

/** Copia de la curva objetivo escalada, codificada con [TargetCurveCodec]. 1:1 opcional con [BrewEntity]. */
@Entity(
  tableName = "brew_targets",
  foreignKeys = [
    ForeignKey(
      entity = BrewEntity::class,
      parentColumns = ["id"],
      childColumns = ["brewId"],
      onDelete = ForeignKey.CASCADE,
    ),
  ],
)
data class BrewTargetEntity(
  @PrimaryKey val brewId: Long,
  val data: ByteArray,
)

/** Proyección de la lista: todo `brews` salvo los campos manuales largos. Nunca incluye la curva. */
data class BrewListRow(
  val id: Long,
  val startedAtEpochMs: Long,
  val name: String?,
  val coffee: String?,
  val totalTimeMs: Long,
  val totalWaterG: Double,
  val finalRatio: Double?,
  val stabilityScore: Int?,
  val stabilityLevel: StabilityLevel?,
  val unit: WeightUnit,
  val simulated: Boolean,
  val preview: ByteArray,
  val fitScore: Int?,
)

/** Fila de la lista. */
data class BrewListItem(
  val id: Long,
  val startedAtEpochMs: Long,
  val name: String?,
  val coffee: String?,
  val totalTimeMs: Long,
  val totalWaterG: Double,
  val finalRatio: Double?,
  val stabilityScore: Int?,
  val stabilityLevel: StabilityLevel?,
  val unit: WeightUnit,
  val simulated: Boolean,
  /** Minicurva decodificada (48 valores de 0 a 1). Es una lista, no un `ByteArray`, para que la igualdad sea por contenido. */
  val preview: List<Float>,
  /** Puntuación de ajuste al objetivo; `null` sin referencia. */
  val fitScore: Int?,
)

/** Detalle completo: la fila, la curva decodificada y el resumen reconstruido. */
data class BrewRecord(
  val entity: BrewEntity,
  val samples: List<BrewSample>,
  /** `pours` sale de la curva; el resto, de las columnas. */
  val summary: BrewSummary,
  /** `true` si la curva no se pudo leer: `samples` y `summary.pours` salen vacíos. */
  val curveError: Boolean,
  /** La curva objetivo guardada; `null` sin referencia o si no se pudo leer (ver [targetError]). */
  val target: TargetCurve? = null,
  /** `true` si hay una copia de la curva objetivo pero no se pudo leer. */
  val targetError: Boolean = false,
)

/** Lo que `delete` deja en `pendingUndo` para poder restaurarlo con el mismo id. */
data class DeletedBrew(val entity: BrewEntity, val curve: BrewCurveEntity, val target: BrewTargetEntity? = null)
