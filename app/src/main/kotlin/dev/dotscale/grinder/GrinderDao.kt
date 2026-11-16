package dev.dotscale.grinder

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
abstract class GrinderDao {
  @Transaction
  @Query("SELECT * FROM grinders WHERE id = :id")
  abstract fun observe(id: Long): Flow<GrinderWithDetails?>

  @Transaction
  @Query("SELECT * FROM grinders WHERE id = :id")
  abstract suspend fun get(id: Long): GrinderWithDetails?

  @Query("SELECT EXISTS(SELECT 1 FROM grinders WHERE id = :id)")
  abstract suspend fun exists(id: Long): Boolean

  @Insert
  protected abstract suspend fun insertRow(grinder: GrinderEntity): Long

  @Update
  protected abstract suspend fun updateRow(grinder: GrinderEntity)

  @Insert
  protected abstract suspend fun insertUnits(units: List<GrinderUnitEntity>)

  @Insert
  protected abstract suspend fun insertRanges(ranges: List<GrinderMethodRangeEntity>)

  @Insert
  protected abstract suspend fun insertAnchors(anchors: List<GrinderAnchorEntity>)

  @Query("DELETE FROM grinder_units WHERE grinderId = :id")
  protected abstract suspend fun deleteUnits(id: Long)

  @Query("DELETE FROM grinder_method_ranges WHERE grinderId = :id")
  protected abstract suspend fun deleteRanges(id: Long)

  @Query("DELETE FROM grinder_anchors WHERE grinderId = :id")
  protected abstract suspend fun deleteAnchors(id: Long)

  /**
   * Alta (con `id = 0`) o edición: guarda el molino y reemplaza sus unidades, rangos y anclas en una transacción.
   * Devuelve el id. No se usa `REPLACE`: borraría la fila y pondría a `NULL` el molino de las recetas.
   */
  @Transaction
  open suspend fun save(rows: GrinderRows): Long {
    val id = if (rows.grinder.id == 0L) {
      insertRow(rows.grinder)
    } else {
      updateRow(rows.grinder)
      deleteUnits(rows.grinder.id)
      deleteRanges(rows.grinder.id)
      deleteAnchors(rows.grinder.id)
      rows.grinder.id
    }
    insertUnits(rows.units.map { it.copy(grinderId = id) })
    insertRanges(rows.ranges.map { it.copy(grinderId = id) })
    insertAnchors(rows.anchors.map { it.copy(grinderId = id) })
    return id
  }
}
