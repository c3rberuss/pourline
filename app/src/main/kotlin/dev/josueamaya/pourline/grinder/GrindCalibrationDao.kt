package dev.josueamaya.pourline.grinder

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** Los pares de calibración entre molinos (SPEC-006, enmienda 1). */
@Dao
abstract class GrindCalibrationDao {
  /** Los pares en los que interviene el molino [id], en cualquiera de los dos lados. */
  @Query("SELECT * FROM grind_calibrations WHERE grinderAId = :id OR grinderBId = :id")
  abstract fun observeOf(id: Long): Flow<List<GrindCalibrationEntity>>

  /** Los pares entre [a] y [b] (con `a < b`, el orden canónico). */
  @Query("SELECT * FROM grind_calibrations WHERE grinderAId = :a AND grinderBId = :b")
  abstract fun observeBetween(a: Long, b: Long): Flow<List<GrindCalibrationEntity>>

  @Query("SELECT * FROM grind_calibrations WHERE grinderAId = :a AND grinderBId = :b")
  abstract suspend fun between(a: Long, b: Long): List<GrindCalibrationEntity>

  @Query("SELECT * FROM grind_calibrations WHERE id = :id")
  abstract suspend fun get(id: Long): GrindCalibrationEntity?

  @Insert
  abstract suspend fun insert(calibration: GrindCalibrationEntity): Long

  @Update
  abstract suspend fun update(calibration: GrindCalibrationEntity)

  @Query("DELETE FROM grind_calibrations WHERE id = :id")
  abstract suspend fun deleteById(id: Long)

  @Query("DELETE FROM grind_calibrations WHERE id IN (:ids)")
  abstract suspend fun deleteByIds(ids: List<Long>)

  /**
   * Guarda un par (o edita [editingId]) si [CalibrationValidator] lo admite. Un valor repetido actualiza el par que lo
   * tiene (`Updated`); una contradicción o un par inválido no escribe nada. Escribe en el orden canónico.
   */
  @Transaction
  open suspend fun save(grinder: Grinder, value: Double, other: Grinder, otherValue: Double, editingId: Long?, nowMs: Long): CalibrationSaveResult {
    val existing = existingBetween(grinder, other)
    return when (val check = CalibrationValidator.check(grinder, value, other, otherValue, existing, editingId)) {
      is CalibrationCheck.Invalid -> CalibrationSaveResult.Invalid(check.errors)
      is CalibrationCheck.Conflict -> CalibrationSaveResult.Conflict(check.conflicts)
      is CalibrationCheck.Update -> write(grinder, value, other, otherValue, editingId = check.existing.id, nowMs = nowMs, updated = true)
      CalibrationCheck.Ok -> write(grinder, value, other, otherValue, editingId = editingId, nowMs = nowMs, updated = false)
    }
  }

  /**
   * Como [save], pero un conflicto se resuelve: borra los pares que contradicen al nuevo y lo guarda, todo en una
   * transacción. El conflicto se recalcula dentro de ella, así que no se borra una lista que haya cambiado.
   */
  @Transaction
  open suspend fun replace(grinder: Grinder, value: Double, other: Grinder, otherValue: Double, editingId: Long?, nowMs: Long): CalibrationSaveResult {
    val first = CalibrationValidator.check(grinder, value, other, otherValue, existingBetween(grinder, other), editingId)
    if (first is CalibrationCheck.Conflict) deleteByIds(first.conflicts.map { it.id })
    return save(grinder, value, other, otherValue, editingId, nowMs)
  }

  private suspend fun existingBetween(grinder: Grinder, other: Grinder): List<GrindCalibration> {
    val (a, b) = canonical(grinder.id, other.id)
    return between(a, b).map { it.toCalibration() }
  }

  private fun canonical(first: Long, second: Long): Pair<Long, Long> = if (first < second) first to second else second to first

  private suspend fun write(grinder: Grinder, value: Double, other: Grinder, otherValue: Double, editingId: Long?, nowMs: Long, updated: Boolean): CalibrationSaveResult {
    // Orden canónico: el molino de menor id es el lado A.
    val swap = grinder.id > other.id
    val entity = GrindCalibrationEntity(
      id = editingId ?: 0,
      grinderAId = if (swap) other.id else grinder.id,
      valueA = if (swap) otherValue else value,
      grinderBId = if (swap) grinder.id else other.id,
      valueB = if (swap) value else otherValue,
      updatedAtEpochMs = nowMs,
    )
    return if (editingId != null) {
      update(entity)
      if (updated) CalibrationSaveResult.Updated(editingId) else CalibrationSaveResult.Saved(editingId)
    } else {
      CalibrationSaveResult.Saved(insert(entity))
    }
  }
}
