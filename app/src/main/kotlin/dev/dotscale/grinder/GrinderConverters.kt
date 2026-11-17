package dev.dotscale.grinder

import androidx.room.TypeConverter

/** Los enums de molinos se guardan por nombre; un nombre desconocido se lee con un valor por defecto (D20). */
class GrinderConverters {
  @TypeConverter fun directionToString(value: GrindDirection): String = value.name

  @TypeConverter fun stringToDirection(value: String): GrindDirection =
    GrindDirection.entries.firstOrNull { it.name == value } ?: GrindDirection.HIGHER_IS_COARSER

  @TypeConverter fun unitToString(value: GrindUnit): String = value.name

  @TypeConverter fun stringToUnit(value: String): GrindUnit = GrindUnit.entries.firstOrNull { it.name == value } ?: GrindUnit.CLICKS

  @TypeConverter fun levelToString(value: GrindLevel): String = value.name

  /** Un nivel desconocido se lee como `EXTRA_FINE` y se ignora al mapear (no existe `null` en una clave primaria). */
  @TypeConverter fun stringToLevel(value: String): GrindLevel = GrindLevel.entries.firstOrNull { it.name == value } ?: GrindLevel.EXTRA_FINE
}
