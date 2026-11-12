package dev.dotscale.history

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.dotscale.brew.BrewSample
import dev.dotscale.brew.BrewSummary
import dev.dotscale.brew.FinishReason
import dev.dotscale.brew.StabilityLevel
import dev.dotscale.protocol.ScaleModel
import dev.dotscale.protocol.WeightUnit

/**
 * Una preparación. La lista nunca carga la curva, que vive aparte en [BrewCurveEntity].
 * Todas las magnitudes van en gramos y g/s, sea cual sea [unit].
 *
 * `equals` compara [preview] por referencia (es un `ByteArray`): para comparar contenido, usa [BrewListItem].
 */
@Entity(tableName = "brews", indices = [Index("startedAtEpochMs")])
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
)

/** Detalle completo: la fila, la curva decodificada y el resumen reconstruido. */
data class BrewRecord(
  val entity: BrewEntity,
  val samples: List<BrewSample>,
  /** `pours` sale de la curva; el resto, de las columnas. */
  val summary: BrewSummary,
  /** `true` si la curva no se pudo leer: `samples` y `summary.pours` salen vacíos. */
  val curveError: Boolean,
)

/** Lo que `delete` deja en `pendingUndo` para poder restaurarlo con el mismo id. */
data class DeletedBrew(val entity: BrewEntity, val curve: BrewCurveEntity)
