package dev.dotscale.history

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

private const val FILTER = "(:coffee IS NULL OR coffee = :coffee) AND (:method IS NULL OR method = :method)"

@Dao
abstract class BrewDao {

  @Query(
    "SELECT id, startedAtEpochMs, name, coffee, totalTimeMs, totalWaterG, finalRatio, stabilityScore, " +
      "stabilityLevel, unit, simulated, preview FROM brews WHERE $FILTER " +
      "ORDER BY startedAtEpochMs DESC, id DESC",
  )
  abstract fun list(coffee: String?, method: BrewMethod?): Flow<List<BrewListRow>>

  @Query(
    "SELECT id AS brewId, startedAtEpochMs, stabilityScore AS score FROM brews " +
      "WHERE simulated = 0 AND stabilityScore IS NOT NULL AND $FILTER " +
      "ORDER BY startedAtEpochMs ASC, id ASC",
  )
  abstract fun progress(coffee: String?, method: BrewMethod?): Flow<List<ProgressPoint>>

  @Query("SELECT coffee FROM brews WHERE coffee IS NOT NULL GROUP BY coffee ORDER BY MAX(startedAtEpochMs) DESC")
  abstract fun coffees(): Flow<List<String>>

  @Query("SELECT DISTINCT method FROM brews WHERE method IS NOT NULL")
  abstract fun methods(): Flow<List<BrewMethod>>

  @Query("SELECT * FROM brews WHERE id = :id")
  abstract fun observeBrew(id: Long): Flow<BrewEntity?>

  @Query("SELECT * FROM brews WHERE id = :id")
  abstract suspend fun brew(id: Long): BrewEntity?

  @Query("SELECT data FROM brew_curves WHERE brewId = :id")
  abstract suspend fun curve(id: Long): ByteArray?

  @Query("SELECT DISTINCT coffee FROM brews WHERE coffee IS NOT NULL AND id != :id")
  abstract suspend fun coffeesExcept(id: Long): List<String>

  @Insert
  protected abstract suspend fun insertBrewRow(brew: BrewEntity): Long

  @Insert
  protected abstract suspend fun insertCurveRow(curve: BrewCurveEntity)

  @Query("DELETE FROM brews WHERE id = :id")
  protected abstract suspend fun deleteBrewRow(id: Long)

  @Query(
    "UPDATE brews SET name = :name, coffee = :coffee, method = :method, methodOther = :methodOther, " +
      "grind = :grind, waterTempC = :waterTempC, notes = :notes WHERE id = :id",
  )
  protected abstract suspend fun updateDetailsRow(
    id: Long,
    name: String?,
    coffee: String?,
    method: BrewMethod?,
    methodOther: String?,
    grind: String?,
    waterTempC: Double?,
    notes: String?,
  )

  /** Inserta la preparación y su curva en una transacción y devuelve el id. */
  @Transaction
  open suspend fun insertBrew(brew: BrewEntity, curveData: ByteArray): Long {
    val id = insertBrewRow(brew)
    insertCurveRow(BrewCurveEntity(id, curveData))
    return id
  }

  /** Guarda los datos manuales. El café reutiliza la grafía existente (D9), sin contar la propia fila. */
  @Transaction
  open suspend fun applyDetails(id: Long, details: BrewDetails) {
    val normalized = details.normalized()
    val coffee = canonicalCoffee(normalized.coffee, coffeesExcept(id))
    updateDetailsRow(
      id,
      normalized.name,
      coffee,
      normalized.method,
      normalized.methodOther,
      normalized.grind,
      normalized.waterTempC,
      normalized.notes,
    )
  }

  /** Borra la preparación (y su curva, por la cascada) y devuelve lo borrado, o `null` si no existía. */
  @Transaction
  open suspend fun deleteBrew(id: Long): DeletedBrew? {
    val entity = brew(id) ?: return null
    val curve = curve(id)?.let { BrewCurveEntity(id, it) } ?: BrewCurveEntity(id, ByteArray(0))
    deleteBrewRow(id)
    return DeletedBrew(entity, curve)
  }

  /** Reinserta la fila y la curva con el mismo id. */
  @Transaction
  open suspend fun restore(deleted: DeletedBrew) {
    insertBrewRow(deleted.entity)
    insertCurveRow(deleted.curve)
  }
}
