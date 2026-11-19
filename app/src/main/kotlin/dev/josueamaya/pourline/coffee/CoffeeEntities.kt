package dev.josueamaya.pourline.coffee

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/** Un café del catálogo (SPEC-004 §3.1). El stock no se guarda: se calcula en la consulta (D19). */
@Entity(tableName = "coffees", indices = [Index("name"), Index("archived")])
data class CoffeeEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val name: String,
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
  val archived: Boolean = false,
  val createdAtEpochMs: Long,
  /** Inicio de la última preparación vinculada; ordena el selector (D20). */
  val lastUsedAtEpochMs: Long? = null,
) {
  fun toDraft() = CoffeeDraft(
    id = id,
    name = name,
    roaster = roaster,
    originCountry = originCountry,
    originRegion = originRegion,
    farm = farm,
    varieties = varieties,
    process = process,
    processOther = processOther,
    altitudeMinM = altitudeMinM,
    altitudeMaxM = altitudeMaxM,
    roastLevel = roastLevel,
    roastDate = roastDate,
    roasterNotes = roasterNotes,
    bagWeightG = bagWeightG,
    price = price,
    openedDate = openedDate,
    photo1 = photo1,
    photo2 = photo2,
  )
}

/** Proyección de la lista: el café con el stock ya calculado por SQL (`null` sin peso de bolsa). */
data class CoffeeListRow(
  val id: Long,
  val name: String,
  val roaster: String?,
  val originCountry: String?,
  val originRegion: String?,
  val roastDate: LocalDate?,
  val photo1: String?,
  val archived: Boolean,
  val stockG: Double?,
)

/** Una preparación de la bitácora tal como sale de SQL. */
data class CoffeeLogRow(
  val brewId: Long,
  val startedAtEpochMs: Long,
  val rating: Int?,
  val notes: String?,
  val doseG: Double?,
)

/** Los valores de un café que alimentan las sugerencias. */
data class SuggestionRow(
  val roaster: String?,
  val originCountry: String?,
  val originRegion: String?,
  val varieties: List<String>,
)

/** El café, su estado y su bitácora, para la pantalla de detalle. */
data class CoffeeDetail(
  val coffee: CoffeeEntity,
  /** Lo que queda en la bolsa, nunca negativo; `null` sin peso de bolsa. */
  val stockG: Double?,
  val soldOut: Boolean,
  val daysSinceRoast: Int?,
  /** De la más reciente a la más antigua. */
  val brews: List<CoffeeLogEntry>,
)

/** Una opción del filtro "Café" del historial (D33). */
data class CoffeeOption(val id: Long, val name: String, val roastDate: LocalDate?)

/** Resultado de guardar: el id, si fue un alta y los nombres de foto que dejaron de usarse. */
data class SaveResult(val id: Long, val inserted: Boolean, val removedPhotos: List<String>)
