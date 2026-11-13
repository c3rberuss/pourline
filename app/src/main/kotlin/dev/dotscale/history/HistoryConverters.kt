package dev.dotscale.history

import androidx.room.TypeConverter
import dev.dotscale.brew.FinishReason
import dev.dotscale.brew.StabilityLevel
import dev.dotscale.protocol.ScaleModel
import dev.dotscale.protocol.WeightUnit

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
}
