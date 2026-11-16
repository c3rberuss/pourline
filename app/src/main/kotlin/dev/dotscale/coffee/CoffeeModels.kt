package dev.dotscale.coffee

import java.time.LocalDate

/** Proceso de beneficio del café. */
enum class CoffeeProcess { WASHED, NATURAL, HONEY, ANAEROBIC, WET_HULLED, OTHER }

/** Nivel de tueste; los niveles combinados ("medio claro") no existen: no se rellenan. */
enum class RoastLevel { LIGHT, MEDIUM, DARK }

/**
 * Lo que el usuario edita de un café (sin las marcas de tiempo ni `archived`). [normalized] deja el borrador
 * como se guarda: textos recortados, vacíos como `null` y altitud con un solo valor duplicada.
 */
data class CoffeeDraft(
  val id: Long? = null,
  val name: String = "",
  val roaster: String? = null,
  val originCountry: String? = null,
  val originRegion: String? = null,
  val farm: String? = null,
  val varieties: List<String> = emptyList(),
  val process: CoffeeProcess? = null,
  val processOther: String? = null,
  val altitudeMinM: Int? = null,
  val altitudeMaxM: Int? = null,
  val roastLevel: RoastLevel? = null,
  val roastDate: LocalDate? = null,
  val roasterNotes: String? = null,
  val bagWeightG: Double? = null,
  val price: Double? = null,
  val openedDate: LocalDate? = null,
  val photo1: String? = null,
  val photo2: String? = null,
) {
  fun normalized(): CoffeeDraft {
    val min = altitudeMinM ?: altitudeMaxM
    val max = altitudeMaxM ?: altitudeMinM
    return copy(
      name = name.trim(),
      roaster = roaster.cleaned(),
      originCountry = originCountry.cleaned(),
      originRegion = originRegion.cleaned(),
      farm = farm.cleaned(),
      varieties = varieties.mapNotNull { it.cleaned() }.distinctBy { it.lowercase() },
      processOther = if (process == CoffeeProcess.OTHER) processOther.cleaned() else null,
      altitudeMinM = min,
      altitudeMaxM = max,
      roasterNotes = roasterNotes.cleaned(),
    )
  }
}

internal fun String?.cleaned(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

/** Fila de la lista de cafés y del selector, con el stock y los días ya calculados. */
data class CoffeeListItem(
  val id: Long,
  val name: String,
  val roaster: String?,
  val originCountry: String?,
  val originRegion: String?,
  val roastDate: LocalDate?,
  val daysSinceRoast: Int?,
  val stockG: Double?,
  val soldOut: Boolean,
  val photo1: String?,
  val archived: Boolean,
)

/** Una preparación vinculada a un café, en su bitácora. */
data class CoffeeLogEntry(
  val brewId: Long,
  val startedAtEpochMs: Long,
  val rating: Int?,
  val notes: String?,
  val daysSinceRoast: Int?,
  val doseG: Double?,
)

/** El café elegido en Scale: lo justo para mostrarlo y vincularlo. */
data class PickedCoffee(
  val id: Long,
  val name: String,
  val roaster: String?,
  val roastDate: LocalDate?,
)

/** Valores ya registrados, para sugerir y para unificar la grafía. */
data class CoffeeSuggestions(
  val roasters: List<String> = emptyList(),
  val countries: List<String> = emptyList(),
  val regions: List<String> = emptyList(),
  val varieties: List<String> = emptyList(),
)
