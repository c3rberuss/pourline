package dev.josueamaya.pourline.history

import androidx.room.TypeConverter
import dev.josueamaya.pourline.brew.FinishReason
import dev.josueamaya.pourline.brew.StabilityLevel
import dev.josueamaya.pourline.protocol.ScaleModel
import dev.josueamaya.pourline.protocol.WeightUnit
import dev.josueamaya.pourline.recipe.StageType

/**
 * Los enums se guardan por nombre, no por ordinal. Un nombre desconocido (p. ej. tras volver a una
 * versión anterior de la app) cae en un valor neutro en vez de romper la lectura.
 */
class HistoryConverters {
  @TypeConverter fun brewMethodToString(value: BrewMethod): String = value.name

  @TypeConverter fun stringToBrewMethod(value: String): BrewMethod = BrewMethod.entries.firstOrNull { it.name == value } ?: BrewMethod.OTHER

  @TypeConverter fun referenceTypeToString(value: ReferenceType?): String? = value?.name

  /** Un nombre desconocido se lee como `null`: una preparación sin referencia. */
  @TypeConverter fun stringToReferenceType(value: String?): ReferenceType? = ReferenceType.entries.firstOrNull { it.name == value }

  @TypeConverter fun stabilityLevelToString(value: StabilityLevel): String = value.name

  @TypeConverter fun stringToStabilityLevel(value: String): StabilityLevel =
    StabilityLevel.entries.firstOrNull { it.name == value } ?: StabilityLevel.IRREGULAR

  @TypeConverter fun finishReasonToString(value: FinishReason): String = value.name

  @TypeConverter fun stringToFinishReason(value: String): FinishReason = FinishReason.entries.firstOrNull { it.name == value } ?: FinishReason.MANUAL

  @TypeConverter fun weightUnitToString(value: WeightUnit): String = value.name

  @TypeConverter fun stringToWeightUnit(value: String): WeightUnit = WeightUnit.entries.firstOrNull { it.name == value } ?: WeightUnit.GRAMS

  @TypeConverter fun scaleModelToString(value: ScaleModel): String = value.name

  @TypeConverter fun stringToScaleModel(value: String): ScaleModel = ScaleModel.entries.firstOrNull { it.name == value } ?: ScaleModel.UNKNOWN

  @TypeConverter fun stageTypeToString(value: StageType): String = value.name

  @TypeConverter fun stringToStageType(value: String): StageType = StageType.entries.firstOrNull { it.name == value } ?: StageType.POUR
}
