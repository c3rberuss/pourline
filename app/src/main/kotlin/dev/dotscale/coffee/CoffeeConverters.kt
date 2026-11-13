package dev.dotscale.coffee

import androidx.room.TypeConverter
import java.time.LocalDate

/**
 * Las listas se guardan unidas con `\n` (las variedades se recortan y no llevan saltos de línea), las fechas
 * como día epoch y los enums por nombre. Un nombre desconocido se lee como `null`.
 */
class CoffeeConverters {
  @TypeConverter fun listToString(value: List<String>): String = value.joinToString("\n")

  @TypeConverter fun stringToList(value: String): List<String> = if (value.isEmpty()) emptyList() else value.split('\n')

  @TypeConverter fun dateToEpochDay(value: LocalDate?): Long? = value?.toEpochDay()

  @TypeConverter fun epochDayToDate(value: Long?): LocalDate? = value?.let(LocalDate::ofEpochDay)

  @TypeConverter fun processToString(value: CoffeeProcess?): String? = value?.name

  @TypeConverter fun stringToProcess(value: String?): CoffeeProcess? = CoffeeProcess.entries.firstOrNull { it.name == value }

  @TypeConverter fun roastLevelToString(value: RoastLevel?): String? = value?.name

  @TypeConverter fun stringToRoastLevel(value: String?): RoastLevel? = RoastLevel.entries.firstOrNull { it.name == value }
}
