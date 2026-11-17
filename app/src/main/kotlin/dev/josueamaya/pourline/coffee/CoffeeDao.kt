package dev.josueamaya.pourline.coffee

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** Stock en SQL (D19): peso de la bolsa menos las dosis de las preparaciones vinculadas; `NULL` sin peso. */
private const val ROW_COLUMNS =
  "c.id AS id, c.name AS name, c.roaster AS roaster, c.originCountry AS originCountry, " +
    "c.originRegion AS originRegion, c.roastDate AS roastDate, c.photo1 AS photo1, c.archived AS archived, " +
    "CASE WHEN c.bagWeightG IS NULL THEN NULL ELSE c.bagWeightG - COALESCE(" +
    "(SELECT SUM(b.doseG) FROM brews b WHERE b.coffeeId = c.id AND b.doseG IS NOT NULL), 0) END AS stockG"

/** Arriba, los cafés en uso (D13). */
private const val ORDER = "ORDER BY COALESCE(c.lastUsedAtEpochMs, c.createdAtEpochMs) DESC, c.id DESC"

@Dao
abstract class CoffeeDao {
  @Query("SELECT $ROW_COLUMNS FROM coffees c WHERE c.archived = :archived $ORDER")
  abstract fun list(archived: Boolean): Flow<List<CoffeeListRow>>

  @Query("SELECT $ROW_COLUMNS FROM coffees c WHERE c.archived = 0 $ORDER")
  abstract fun pickable(): Flow<List<CoffeeListRow>>

  @Query("SELECT $ROW_COLUMNS FROM coffees c WHERE c.id = :id")
  abstract fun observeRow(id: Long): Flow<CoffeeListRow?>

  @Query("SELECT * FROM coffees WHERE id = :id")
  abstract fun observe(id: Long): Flow<CoffeeEntity?>

  @Query("SELECT * FROM coffees WHERE id = :id")
  abstract suspend fun get(id: Long): CoffeeEntity?

  @Query("SELECT id AS brewId, startedAtEpochMs, rating, notes, doseG FROM brews WHERE coffeeId = :id ORDER BY startedAtEpochMs DESC, id DESC")
  abstract fun logEntries(id: Long): Flow<List<CoffeeLogRow>>

  @Query("SELECT roaster, originCountry, originRegion, varieties FROM coffees")
  abstract fun observeSuggestionRows(): Flow<List<SuggestionRow>>

  @Query("SELECT roaster, originCountry, originRegion, varieties FROM coffees WHERE id != :excludedId")
  abstract suspend fun suggestionRows(excludedId: Long): List<SuggestionRow>

  @Query("SELECT photo1 FROM coffees WHERE photo1 IS NOT NULL UNION SELECT photo2 FROM coffees WHERE photo2 IS NOT NULL")
  abstract suspend fun photoNames(): List<String>

  @Query("SELECT EXISTS(SELECT 1 FROM coffees WHERE id = :id)")
  abstract suspend fun exists(id: Long): Boolean

  @Insert
  protected abstract suspend fun insertRow(coffee: CoffeeEntity): Long

  @Update
  protected abstract suspend fun updateRow(coffee: CoffeeEntity)

  @Query("DELETE FROM coffees WHERE id = :id")
  protected abstract suspend fun deleteRow(id: Long)

  @Query("UPDATE coffees SET archived = :archived WHERE id = :id")
  abstract suspend fun setArchived(id: Long, archived: Boolean)

  @Query("UPDATE brews SET coffee = :name WHERE coffeeId = :id")
  protected abstract suspend fun renameInBrews(id: Long, name: String)

  /**
   * Alta o edición. Unifica la grafía de tostador, país, región y variedades con la de los otros cafés
   * (sin contar la propia fila) y, si cambió el nombre, actualiza la copia en las preparaciones vinculadas.
   */
  @Transaction
  open suspend fun save(draft: CoffeeDraft, now: Long): SaveResult {
    val normalized = draft.normalized()
    val canonical = canonicalize(normalized, suggestionsExcluding(normalized.id ?: -1))
    val old = canonical.id?.let { get(it) }
    if (old == null) {
      val id = insertRow(canonical.toEntity(id = 0, createdAt = now))
      return SaveResult(id, inserted = true, removedPhotos = emptyList())
    }
    updateRow(canonical.toEntity(id = old.id, createdAt = old.createdAtEpochMs, old = old))
    if (old.name != canonical.name) renameInBrews(old.id, canonical.name)
    val kept = listOfNotNull(canonical.photo1, canonical.photo2)
    return SaveResult(old.id, inserted = false, removedPhotos = listOfNotNull(old.photo1, old.photo2).filter { it !in kept })
  }

  /** Borra el café y devuelve lo borrado (para quitar sus fotos), o `null` si no existía. Las preparaciones pasan a `coffeeId = NULL`. */
  @Transaction
  open suspend fun delete(id: Long): CoffeeEntity? {
    val entity = get(id) ?: return null
    deleteRow(id)
    return entity
  }

  private suspend fun suggestionsExcluding(id: Long): CoffeeSuggestions = suggestionRows(id).toSuggestions()
}

/** Valores distintos (sin distinguir mayúsculas; gana la primera grafía), ordenados sin tildes. */
internal fun List<SuggestionRow>.toSuggestions(): CoffeeSuggestions {
  fun distinct(values: List<String?>) =
    values.mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }.distinctBy { it.lowercase() }.sortedBy(::fold)
  return CoffeeSuggestions(
    roasters = distinct(map { it.roaster }),
    countries = distinct(map { it.originCountry }),
    regions = distinct(map { it.originRegion }),
    varieties = distinct(flatMap { it.varieties }),
  )
}

private fun CoffeeDraft.toEntity(id: Long, createdAt: Long, old: CoffeeEntity? = null) = CoffeeEntity(
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
  archived = old?.archived ?: false,
  createdAtEpochMs = createdAt,
  lastUsedAtEpochMs = old?.lastUsedAtEpochMs,
)
