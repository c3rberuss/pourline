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
  @Query("SELECT * FROM grinders WHERE owned = 1")
  abstract fun observeOwned(): Flow<List<GrinderWithDetails>>

  /** Los predefinidos que siguen en el catálogo (sin los retirados). */
  @Transaction
  @Query("SELECT * FROM grinders WHERE catalogKey IS NOT NULL AND retired = 0")
  abstract fun observeCatalog(): Flow<List<GrinderWithDetails>>

  @Transaction
  @Query("SELECT * FROM grinders")
  abstract fun observeAll(): Flow<List<GrinderWithDetails>>

  @Transaction
  @Query("SELECT * FROM grinders WHERE id = :id")
  abstract fun observe(id: Long): Flow<GrinderWithDetails?>

  @Transaction
  @Query("SELECT * FROM grinders WHERE id = :id")
  abstract suspend fun get(id: Long): GrinderWithDetails?

  @Query("SELECT EXISTS(SELECT 1 FROM grinders WHERE id = :id)")
  abstract suspend fun exists(id: Long): Boolean

  @Query("SELECT * FROM grinders WHERE catalogKey = :key")
  abstract suspend fun byCatalogKey(key: String): GrinderEntity?

  @Query("SELECT catalogKey FROM grinders WHERE catalogKey IS NOT NULL")
  abstract suspend fun catalogKeys(): List<String>

  @Query("UPDATE grinders SET owned = :owned, updatedAtEpochMs = :nowMs WHERE id = :id")
  abstract suspend fun setOwned(id: Long, owned: Boolean, nowMs: Long)

  @Query("UPDATE grinders SET retired = :retired WHERE id = :id")
  protected abstract suspend fun setRetired(id: Long, retired: Boolean)

  /** Cuántas recetas usan el molino. */
  @Query("SELECT COUNT(*) FROM recipes WHERE grinderId = :id")
  abstract fun recipeCount(id: Long): Flow<Int>

  /** Si alguna receta o preparación usa el molino. */
  @Query("SELECT EXISTS(SELECT 1 FROM recipes WHERE grinderId = :id) OR EXISTS(SELECT 1 FROM brews WHERE grinderId = :id)")
  abstract suspend fun isUsed(id: Long): Boolean

  @Query("SELECT id, grindValue AS value FROM recipes WHERE grinderId = :id")
  abstract suspend fun recipesUsing(id: Long): List<GrindUse>

  @Query("SELECT id, grindValue AS value FROM brews WHERE grinderId = :id")
  abstract suspend fun brewsUsing(id: Long): List<GrindUse>

  @Query("UPDATE recipes SET grindLabel = :label WHERE id = :id")
  protected abstract suspend fun relabelRecipe(id: Long, label: String)

  @Query("UPDATE brews SET grindLabel = :label WHERE id = :id")
  protected abstract suspend fun relabelBrew(id: Long, label: String)

  @Query("DELETE FROM grinders WHERE id = :id")
  protected abstract suspend fun deleteRow(id: Long)

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

  /**
   * Borra el molino tras regenerar la copia de texto de cada receta y preparación que lo usan (D17): la clave
   * foránea pone después `grinderId = NULL` y conserva valor y copia.
   */
  @Transaction
  open suspend fun deleteRelabeling(id: Long, recipeLabels: Map<Long, String>, brewLabels: Map<Long, String>) {
    recipeLabels.forEach { (recipe, label) -> relabelRecipe(recipe, label) }
    brewLabels.forEach { (brew, label) -> relabelBrew(brew, label) }
    deleteRow(id)
  }

  /**
   * Sincroniza los predefinidos con [catalog] en una transacción (D34): inserta los nuevos, actualiza los que ya
   * están (conservando id y `owned`, y reemplazando los hijos) y trata los que ya no vienen: los que están en uso
   * (recetas, preparaciones, `owned` o el predeterminado [defaultId]) pasan a `retired`; el resto se borra.
   * Los molinos propios no se tocan. Un predefinido que vuelve deja de estar retirado.
   */
  @Transaction
  open suspend fun syncCatalog(catalog: List<GrinderRows>, defaultId: Long?): SyncCounts {
    var added = 0
    var updated = 0
    var retired = 0
    var deleted = 0
    val incoming = catalog.mapNotNull { it.grinder.catalogKey }.toSet()
    for (rows in catalog) {
      val existing = byCatalogKey(requireNotNull(rows.grinder.catalogKey))
      if (existing == null) {
        save(rows.copy(grinder = rows.grinder.copy(id = 0, owned = false, retired = false)))
        added++
      } else {
        save(rows.copy(grinder = rows.grinder.copy(id = existing.id, owned = existing.owned, retired = false)))
        updated++
      }
    }
    for (key in catalogKeys().filterNot { it in incoming }) {
      val gone = byCatalogKey(key) ?: continue
      if (gone.owned || gone.id == defaultId || isUsed(gone.id)) {
        if (!gone.retired) {
          setRetired(gone.id, true)
          retired++
        }
      } else {
        deleteRow(gone.id)
        deleted++
      }
    }
    return SyncCounts(added, updated, retired, deleted)
  }
}

/** Un uso de un molino en una receta o una preparación: su id y el ajuste guardado. */
data class GrindUse(val id: Long, val value: Double?)

data class SyncCounts(val added: Int, val updated: Int, val retired: Int, val deleted: Int)
