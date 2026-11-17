package dev.josueamaya.pourline.history

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import dev.josueamaya.pourline.brew.PlannedAmounts
import dev.josueamaya.pourline.coffee.CoffeeOption
import kotlinx.coroutines.flow.Flow

private const val FILTER = "(:coffeeId IS NULL OR coffeeId = :coffeeId) AND (:method IS NULL OR method = :method)"

@Dao
abstract class BrewDao {

  @Query(
    "SELECT id, startedAtEpochMs, name, coffee, totalTimeMs, totalWaterG, finalRatio, stabilityScore, " +
      "stabilityLevel, unit, simulated, preview, fitScore, rating, scaleless FROM brews WHERE $FILTER " +
      "ORDER BY startedAtEpochMs DESC, id DESC",
  )
  abstract fun list(coffeeId: Long?, method: BrewMethod?): Flow<List<BrewListRow>>

  @Query(
    "SELECT id AS brewId, startedAtEpochMs, stabilityScore AS score FROM brews " +
      "WHERE simulated = 0 AND scaleless = 0 AND stabilityScore IS NOT NULL AND $FILTER " +
      "ORDER BY startedAtEpochMs ASC, id ASC",
  )
  abstract fun progress(coffeeId: Long?, method: BrewMethod?): Flow<List<ProgressPoint>>

  /** Cafés con al menos una preparación vinculada (también los archivados), por la preparación más reciente (D33). */
  @Query(
    "SELECT c.id AS id, c.name AS name, c.roastDate AS roastDate FROM coffees c " +
      "WHERE EXISTS (SELECT 1 FROM brews b WHERE b.coffeeId = c.id) " +
      "ORDER BY (SELECT MAX(b.startedAtEpochMs) FROM brews b WHERE b.coffeeId = c.id) DESC, c.id DESC",
  )
  abstract fun coffeeOptions(): Flow<List<CoffeeOption>>

  @Query("SELECT DISTINCT method FROM brews WHERE method IS NOT NULL")
  abstract fun methods(): Flow<List<BrewMethod>>

  @Query("SELECT * FROM brews WHERE id = :id")
  abstract fun observeBrew(id: Long): Flow<BrewEntity?>

  @Query("SELECT * FROM brews WHERE id = :id")
  abstract suspend fun brew(id: Long): BrewEntity?

  @Query("SELECT data FROM brew_curves WHERE brewId = :id")
  abstract suspend fun curve(id: Long): ByteArray?

  @Query("SELECT data FROM brew_targets WHERE brewId = :id")
  abstract suspend fun target(id: Long): ByteArray?

  @Query("SELECT COUNT(*) FROM brews")
  abstract fun count(): Flow<Int>

  @Query("SELECT COUNT(*) FROM brews WHERE scaleless = 0")
  abstract fun comparableCount(): Flow<Int>

  @Insert
  protected abstract suspend fun insertBrewRow(brew: BrewEntity): Long

  @Insert
  protected abstract suspend fun insertCurveRow(curve: BrewCurveEntity)

  @Insert
  protected abstract suspend fun insertTargetRow(target: BrewTargetEntity)

  @Query("DELETE FROM brews WHERE id = :id")
  protected abstract suspend fun deleteBrewRow(id: Long)

  @Query("SELECT EXISTS(SELECT 1 FROM grinders WHERE id = :id)")
  protected abstract suspend fun grinderExists(id: Long): Boolean

  /** El id del molino si todavía existe; si se borró mientras tanto, `null` (la copia de texto se conserva, D18). */
  private suspend fun liveGrinder(id: Long?): Long? = id?.takeIf { grinderExists(it) }

  @Query("SELECT name FROM coffees WHERE id = :id")
  protected abstract suspend fun coffeeName(id: Long): String?

  @Query("SELECT coffeeId FROM brews WHERE id = :id")
  protected abstract suspend fun coffeeIdOf(id: Long): Long?

  @Query("UPDATE brews SET coffeeId = :coffeeId, coffee = :coffee WHERE id = :id")
  protected abstract suspend fun updateCoffeeLink(id: Long, coffeeId: Long?, coffee: String?)

  /** `lastUsedAtEpochMs` es el inicio de la última preparación vinculada (D20); `NULL` si ya no queda ninguna. */
  @Query("UPDATE coffees SET lastUsedAtEpochMs = (SELECT MAX(startedAtEpochMs) FROM brews WHERE coffeeId = :coffeeId) WHERE id = :coffeeId")
  protected abstract suspend fun recomputeLastUsed(coffeeId: Long)

  @Query(
    "UPDATE brews SET name = :name, method = :method, methodOther = :methodOther, " +
      "grinderId = :grinderId, grindValue = :grindValue, grindLabel = :grindLabel, waterTempC = :waterTempC, " +
      "notes = :notes, rating = :rating WHERE id = :id",
  )
  protected abstract suspend fun updateDetailsRow(
    id: Long,
    name: String?,
    method: BrewMethod?,
    methodOther: String?,
    grinderId: Long?,
    grindValue: Double?,
    grindLabel: String?,
    waterTempC: Double?,
    notes: String?,
    rating: Int?,
  )

  /** Reescribe la dosis, el agua y el ratio de una preparación sin báscula; en una con báscula no toca nada (SPEC-007 §3.5). */
  @Query("UPDATE brews SET doseG = :doseG, totalWaterG = :totalWaterG, finalRatio = :finalRatio WHERE id = :id AND scaleless = 1")
  protected abstract suspend fun updateScalelessAmounts(id: Long, doseG: Double?, totalWaterG: Double, finalRatio: Double?): Int

  /**
   * Inserta la preparación, su curva y, si la hay, la copia de la curva objetivo en una transacción y devuelve
   * el id. Con [coffee]: si el café existe, se vincula con su nombre actual; si se borró mientras tanto (se
   * puede abrir Cafés desde Scale), queda sin vínculo pero con el nombre (D21). Recalcula `lastUsedAtEpochMs`.
   */
  @Transaction
  open suspend fun insertBrew(
    brew: BrewEntity,
    curveData: ByteArray,
    targetData: ByteArray? = null,
    coffee: UsedCoffee? = null,
  ): Long {
    val currentName = coffee?.let { coffeeName(it.id) }
    val withCoffee = when {
      coffee == null -> brew
      currentName != null -> brew.copy(coffeeId = coffee.id, coffee = currentName)
      else -> brew.copy(coffeeId = null, coffee = coffee.name)
    }
    val row = withCoffee.copy(grinderId = liveGrinder(withCoffee.grinderId))
    val id = insertBrewRow(row)
    insertCurveRow(BrewCurveEntity(id, curveData))
    if (targetData != null) insertTargetRow(BrewTargetEntity(id, targetData))
    row.coffeeId?.let { recomputeLastUsed(it) }
    return id
  }

  /** Vincula la preparación a un café (con la copia de su nombre) o la desvincula con `null` (quita también el nombre). */
  @Transaction
  open suspend fun linkCoffee(brewId: Long, coffeeId: Long?) {
    val previous = coffeeIdOf(brewId)
    val name = coffeeId?.let { coffeeName(it) }
    val newId = if (name != null) coffeeId else null
    updateCoffeeLink(brewId, newId, name)
    previous?.let { recomputeLastUsed(it) }
    newId?.let { recomputeLastUsed(it) }
  }

  /**
   * Guarda los datos manuales. No toca `coffee` ni `coffeeId`: el café se cambia con [linkCoffee] (D22). Con [amounts],
   * en la misma transacción, corrige la dosis y el agua, solo si la preparación es sin báscula.
   */
  @Transaction
  open suspend fun applyDetails(id: Long, details: BrewDetails, amounts: PlannedAmounts? = null) {
    val normalized = details.normalized()
    updateDetailsRow(
      id,
      normalized.name,
      normalized.method,
      normalized.methodOther,
      liveGrinder(normalized.grind?.grinderId),
      normalized.grind?.value,
      normalized.grind?.label,
      normalized.waterTempC,
      normalized.notes,
      normalized.rating,
    )
    if (amounts != null) updateScalelessAmounts(id, amounts.doseG, amounts.waterG, amounts.ratio)
  }

  /** Borra la preparación (y su curva, por la cascada) y devuelve lo borrado, o `null` si no existía. */
  @Transaction
  open suspend fun deleteBrew(id: Long): DeletedBrew? {
    val entity = brew(id) ?: return null
    val curve = curve(id)?.let { BrewCurveEntity(id, it) } ?: BrewCurveEntity(id, ByteArray(0))
    val target = target(id)?.let { BrewTargetEntity(id, it) }
    deleteBrewRow(id)
    entity.coffeeId?.let { recomputeLastUsed(it) }
    return DeletedBrew(entity, curve, target)
  }

  /** Reinserta la fila, la curva y la copia del objetivo con el mismo id. */
  @Transaction
  open suspend fun restore(deleted: DeletedBrew) {
    // Si el café se borró mientras tanto, la clave foránea fallaría: queda sin vínculo y con el nombre.
    val coffeeId = deleted.entity.coffeeId?.takeIf { coffeeName(it) != null }
    insertBrewRow(deleted.entity.copy(coffeeId = coffeeId, grinderId = liveGrinder(deleted.entity.grinderId)))
    insertCurveRow(deleted.curve)
    deleted.target?.let { insertTargetRow(it) }
    coffeeId?.let { recomputeLastUsed(it) }
  }
}
