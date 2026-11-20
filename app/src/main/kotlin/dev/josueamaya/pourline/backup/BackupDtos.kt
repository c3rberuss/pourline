package dev.josueamaya.pourline.backup

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.util.Base64

/**
 * Los DTO del formato (SPEC-011 §3.1): uno por tabla, con un campo por columna, con el nombre de la columna y sin
 * valores por defecto, para que un campo obligatorio ausente sea un error y no un cero inventado. No son las entidades
 * de Room: un cambio de esquema no altera el formato sin querer (§3.8).
 */

/** Los `ByteArray` van en Base64 sin saltos de línea; se decodifica directamente a `ByteArray`. */
internal object Base64ByteArraySerializer : KSerializer<ByteArray> {
  override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Base64ByteArray", PrimitiveKind.STRING)

  override fun serialize(encoder: Encoder, value: ByteArray) = encoder.encodeString(Base64.getEncoder().encodeToString(value))

  override fun deserialize(decoder: Decoder): ByteArray = Base64.getDecoder().decode(decoder.decodeString())
}

@Serializable
internal data class CountsDto(
  val coffees: Int,
  val grinders: Int,
  val grindCalibrations: Int,
  val recipes: Int,
  val brews: Int,
  val photos: Int,
)

@Serializable
internal data class ManifestDto(
  val app: String,
  val formatVersion: Int,
  val schemaVersion: Int,
  val appVersionName: String,
  val appVersionCode: Long,
  val createdAt: String,
  val counts: CountsDto,
)

@Serializable
internal data class BrewDto(
  val id: Long,
  val startedAtEpochMs: Long,
  val name: String?,
  val coffee: String?,
  val method: String?,
  val methodOther: String?,
  val waterTempC: Double?,
  val notes: String?,
  val totalTimeMs: Long,
  val totalWaterG: Double,
  val avgFlowGps: Double,
  val maxFlowGps: Double,
  val stabilityScore: Int?,
  val stabilityLevel: String?,
  val shareStable: Double,
  val shareIrregular: Double,
  val shareTurbulent: Double,
  val doseG: Double?,
  val finalRatio: Double?,
  val finishReason: String,
  val interrupted: Boolean,
  val unit: String,
  val scaleModel: String?,
  val simulated: Boolean,
  val metricVersion: Int,
  @Serializable(with = Base64ByteArraySerializer::class) val preview: ByteArray,
  val referenceType: String?,
  val referenceId: Long?,
  val referenceName: String?,
  val targetWaterG: Double?,
  val targetEndMs: Long?,
  val fitScore: Int?,
  val coffeeId: Long?,
  val rating: Int?,
  val grinderId: Long?,
  val grindValue: Double?,
  val grindLabel: String?,
  val scaleless: Boolean,
)

@Serializable
internal data class BrewCurveDto(
  val brewId: Long,
  @Serializable(with = Base64ByteArraySerializer::class) val data: ByteArray,
)

@Serializable
internal data class BrewTargetDto(
  val brewId: Long,
  @Serializable(with = Base64ByteArraySerializer::class) val data: ByteArray,
)

@Serializable
internal data class RecipeDto(
  val id: Long,
  val name: String,
  val method: String?,
  val methodOther: String?,
  val waterTempC: Double?,
  val notes: String?,
  val doseG: Double,
  val builtInKey: String?,
  val localizedNames: Boolean,
  val updatedAtEpochMs: Long,
  val grinderId: Long?,
  val grindValue: Double?,
  val grindLabel: String?,
)

@Serializable
internal data class RecipeStageDto(
  val recipeId: Long,
  val position: Int,
  val name: String?,
  val startMs: Long,
  val targetWaterG: Double,
  val durationMs: Long,
  val type: String,
  val instruction: String?,
)

@Serializable
internal data class CoffeeDto(
  val id: Long,
  val name: String,
  val roaster: String?,
  val originCountry: String?,
  val originRegion: String?,
  val farm: String?,
  val varieties: List<String>,
  val process: String?,
  val processOther: String?,
  val altitudeMinM: Int?,
  val altitudeMaxM: Int?,
  val roastLevel: String?,
  /** `yyyy-MM-dd`. */
  val roastDate: String?,
  val roasterNotes: String?,
  val bagWeightG: Double?,
  val price: Double?,
  /** `yyyy-MM-dd`. */
  val openedDate: String?,
  val photo1: String?,
  val photo2: String?,
  val archived: Boolean,
  val createdAtEpochMs: Long,
  val lastUsedAtEpochMs: Long?,
)

@Serializable
internal data class GrinderDto(
  val id: Long,
  val catalogKey: String?,
  val brand: String,
  val model: String,
  val direction: String,
  val scaleMin: Double,
  val scaleMax: Double,
  val step: Double,
  val owned: Boolean,
  val retired: Boolean,
  val sourceUrl: String?,
  val sourceName: String?,
  val duplicatedFromKey: String?,
  val updatedAtEpochMs: Long,
)

@Serializable
internal data class GrinderUnitDto(
  val grinderId: Long,
  val position: Int,
  val unit: String,
  val perParent: Int?,
)

@Serializable
internal data class GrinderMethodRangeDto(
  val grinderId: Long,
  val method: String,
  val min: Double,
  val max: Double,
)

@Serializable
internal data class GrinderAnchorDto(
  val grinderId: Long,
  val level: String,
  val value: Double,
)

@Serializable
internal data class GrindCalibrationDto(
  val id: Long,
  val grinderAId: Long,
  val valueA: Double,
  val grinderBId: Long,
  val valueB: Double,
  val updatedAtEpochMs: Long,
)

/** `data.json`: una clave por tabla, con el nombre de la tabla de Room, y una lista de filas ordenadas por clave primaria. */
@Serializable
internal data class DataDto(
  @SerialName("brews") val brews: List<BrewDto>,
  @SerialName("brew_curves") val brewCurves: List<BrewCurveDto>,
  @SerialName("brew_targets") val brewTargets: List<BrewTargetDto>,
  @SerialName("recipes") val recipes: List<RecipeDto>,
  @SerialName("recipe_stages") val recipeStages: List<RecipeStageDto>,
  @SerialName("coffees") val coffees: List<CoffeeDto>,
  @SerialName("grinders") val grinders: List<GrinderDto>,
  @SerialName("grinder_units") val grinderUnits: List<GrinderUnitDto>,
  @SerialName("grinder_method_ranges") val grinderMethodRanges: List<GrinderMethodRangeDto>,
  @SerialName("grinder_anchors") val grinderAnchors: List<GrinderAnchorDto>,
  @SerialName("grind_calibrations") val grindCalibrations: List<GrindCalibrationDto>,
)

/**
 * `settings.json`: una clave por ajuste, con el nombre de la clave de `SharedPreferences` y el tipo guardado. `null` es
 * "clave ausente" y no se escribe.
 */
@Serializable
internal data class SettingsDto(
  @SerialName("theme_mode") val themeMode: String?,
  @SerialName("experience_mode") val experienceMode: String?,
  @SerialName("onboarding_done") val onboardingDone: Boolean?,
  @SerialName("use_scale") val useScale: Boolean?,
  @SerialName("last_scale_address") val lastScaleAddress: String?,
  @SerialName("last_scale_name") val lastScaleName: String?,
  @SerialName("auto_finish_seconds") val autoFinishSeconds: Int?,
  @SerialName("countdown_seconds") val countdownSeconds: Int?,
  @SerialName("last_coffee_id") val lastCoffeeId: Long?,
  @SerialName("default_dose_g") val defaultDoseG: Float?,
  @SerialName("default_ratio") val defaultRatio: Float?,
  @SerialName("target_ref_type") val targetRefType: String?,
  @SerialName("target_ref_id") val targetRefId: Long?,
  @SerialName("stage_vibration") val stageVibration: Boolean?,
  @SerialName("stage_sound") val stageSound: Boolean?,
  @SerialName("default_grinder_id") val defaultGrinderId: Long?,
  @SerialName("grinder_catalog_version") val grinderCatalogVersion: Int?,
)
