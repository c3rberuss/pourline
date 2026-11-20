package dev.josueamaya.pourline.backup

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import dev.josueamaya.pourline.coffee.CoffeeEntity
import dev.josueamaya.pourline.grinder.GrindCalibrationEntity
import dev.josueamaya.pourline.grinder.GrinderAnchorEntity
import dev.josueamaya.pourline.grinder.GrinderEntity
import dev.josueamaya.pourline.grinder.GrinderMethodRangeEntity
import dev.josueamaya.pourline.grinder.GrinderUnitEntity
import dev.josueamaya.pourline.history.BrewCurveEntity
import dev.josueamaya.pourline.history.BrewEntity
import dev.josueamaya.pourline.history.BrewTargetEntity
import dev.josueamaya.pourline.recipe.RecipeEntity
import dev.josueamaya.pourline.recipe.RecipeStageEntity

/** Los recuentos de las 11 tablas y los mayores ids de las cinco autoincrementales, para la huella de una importación. */
data class FingerprintRow(
  val brews: Int,
  val brewCurves: Int,
  val brewTargets: Int,
  val recipes: Int,
  val recipeStages: Int,
  val coffees: Int,
  val grinders: Int,
  val grinderUnits: Int,
  val grinderMethodRanges: Int,
  val grinderAnchors: Int,
  val grindCalibrations: Int,
  val maxBrewId: Long,
  val maxRecipeId: Long,
  val maxCoffeeId: Long,
  val maxGrinderId: Long,
  val maxCalibrationId: Long,
)

/**
 * Lee y reemplaza las 11 tablas de la base para la copia de seguridad (SPEC-011 §3.5). Es un DAO, no una entidad: no
 * forma parte del esquema y no cambia su hash.
 */
@Dao
abstract class BackupDao {

  @Query("SELECT * FROM brews ORDER BY id")
  protected abstract suspend fun brews(): List<BrewEntity>

  @Query("SELECT * FROM brew_curves ORDER BY brewId")
  protected abstract suspend fun brewCurves(): List<BrewCurveEntity>

  @Query("SELECT * FROM brew_targets ORDER BY brewId")
  protected abstract suspend fun brewTargets(): List<BrewTargetEntity>

  @Query("SELECT * FROM recipes ORDER BY id")
  protected abstract suspend fun recipes(): List<RecipeEntity>

  @Query("SELECT * FROM recipe_stages ORDER BY recipeId, position")
  protected abstract suspend fun recipeStages(): List<RecipeStageEntity>

  @Query("SELECT * FROM coffees ORDER BY id")
  protected abstract suspend fun coffees(): List<CoffeeEntity>

  @Query("SELECT * FROM grinders ORDER BY id")
  protected abstract suspend fun grinders(): List<GrinderEntity>

  @Query("SELECT * FROM grinder_units ORDER BY grinderId, position")
  protected abstract suspend fun grinderUnits(): List<GrinderUnitEntity>

  @Query("SELECT * FROM grinder_method_ranges ORDER BY grinderId, method")
  protected abstract suspend fun grinderMethodRanges(): List<GrinderMethodRangeEntity>

  @Query("SELECT * FROM grinder_anchors ORDER BY grinderId, level")
  protected abstract suspend fun grinderAnchors(): List<GrinderAnchorEntity>

  @Query("SELECT * FROM grind_calibrations ORDER BY id")
  protected abstract suspend fun grindCalibrations(): List<GrindCalibrationEntity>

  /** Todas las filas de todas las tablas en una transacción: una foto coherente aunque se guarde una preparación a la vez. */
  @Transaction
  open suspend fun readAll(): BackupRows = BackupRows(
    brews = brews(),
    brewCurves = brewCurves(),
    brewTargets = brewTargets(),
    recipes = recipes(),
    recipeStages = recipeStages(),
    coffees = coffees(),
    grinders = grinders(),
    grinderUnits = grinderUnits(),
    grinderMethodRanges = grinderMethodRanges(),
    grinderAnchors = grinderAnchors(),
    grindCalibrations = grindCalibrations(),
  )

  @Query("DELETE FROM brew_targets")
  protected abstract suspend fun deleteBrewTargets()

  @Query("DELETE FROM brew_curves")
  protected abstract suspend fun deleteBrewCurves()

  @Query("DELETE FROM brews")
  protected abstract suspend fun deleteBrews()

  @Query("DELETE FROM recipe_stages")
  protected abstract suspend fun deleteRecipeStages()

  @Query("DELETE FROM recipes")
  protected abstract suspend fun deleteRecipes()

  @Query("DELETE FROM grind_calibrations")
  protected abstract suspend fun deleteGrindCalibrations()

  @Query("DELETE FROM grinder_anchors")
  protected abstract suspend fun deleteGrinderAnchors()

  @Query("DELETE FROM grinder_method_ranges")
  protected abstract suspend fun deleteGrinderMethodRanges()

  @Query("DELETE FROM grinder_units")
  protected abstract suspend fun deleteGrinderUnits()

  @Query("DELETE FROM grinders")
  protected abstract suspend fun deleteGrinders()

  @Query("DELETE FROM coffees")
  protected abstract suspend fun deleteCoffees()

  @Insert
  protected abstract suspend fun insertCoffees(rows: List<CoffeeEntity>)

  @Insert
  protected abstract suspend fun insertGrinders(rows: List<GrinderEntity>)

  @Insert
  protected abstract suspend fun insertGrinderUnits(rows: List<GrinderUnitEntity>)

  @Insert
  protected abstract suspend fun insertGrinderMethodRanges(rows: List<GrinderMethodRangeEntity>)

  @Insert
  protected abstract suspend fun insertGrinderAnchors(rows: List<GrinderAnchorEntity>)

  @Insert
  protected abstract suspend fun insertGrindCalibrations(rows: List<GrindCalibrationEntity>)

  @Insert
  protected abstract suspend fun insertRecipes(rows: List<RecipeEntity>)

  @Insert
  protected abstract suspend fun insertRecipeStages(rows: List<RecipeStageEntity>)

  @Insert
  protected abstract suspend fun insertBrews(rows: List<BrewEntity>)

  @Insert
  protected abstract suspend fun insertBrewCurves(rows: List<BrewCurveEntity>)

  @Insert
  protected abstract suspend fun insertBrewTargets(rows: List<BrewTargetEntity>)

  /**
   * Deja exactamente [rows] en las 11 tablas, con sus ids, en una sola transacción: si algo falla se revierte todo. Borra
   * de hijas a padres e inserta de padres a hijas. No usa `clearAllTables()`, que abre su propia transacción.
   */
  @Transaction
  open suspend fun replaceAll(rows: BackupRows) {
    deleteBrewTargets()
    deleteBrewCurves()
    deleteBrews()
    deleteRecipeStages()
    deleteRecipes()
    deleteGrindCalibrations()
    deleteGrinderAnchors()
    deleteGrinderMethodRanges()
    deleteGrinderUnits()
    deleteGrinders()
    deleteCoffees()

    insertCoffees(rows.coffees)
    insertGrinders(rows.grinders)
    insertGrinderUnits(rows.grinderUnits)
    insertGrinderMethodRanges(rows.grinderMethodRanges)
    insertGrinderAnchors(rows.grinderAnchors)
    insertGrindCalibrations(rows.grindCalibrations)
    insertRecipes(rows.recipes)
    insertRecipeStages(rows.recipeStages)
    insertBrews(rows.brews)
    insertBrewCurves(rows.brewCurves)
    insertBrewTargets(rows.brewTargets)
  }

  @Query(
    "SELECT (SELECT COUNT(*) FROM brews) AS brews, (SELECT COUNT(*) FROM brew_curves) AS brewCurves, " +
      "(SELECT COUNT(*) FROM brew_targets) AS brewTargets, (SELECT COUNT(*) FROM recipes) AS recipes, " +
      "(SELECT COUNT(*) FROM recipe_stages) AS recipeStages, (SELECT COUNT(*) FROM coffees) AS coffees, " +
      "(SELECT COUNT(*) FROM grinders) AS grinders, (SELECT COUNT(*) FROM grinder_units) AS grinderUnits, " +
      "(SELECT COUNT(*) FROM grinder_method_ranges) AS grinderMethodRanges, " +
      "(SELECT COUNT(*) FROM grinder_anchors) AS grinderAnchors, " +
      "(SELECT COUNT(*) FROM grind_calibrations) AS grindCalibrations, " +
      "IFNULL((SELECT MAX(id) FROM brews), 0) AS maxBrewId, IFNULL((SELECT MAX(id) FROM recipes), 0) AS maxRecipeId, " +
      "IFNULL((SELECT MAX(id) FROM coffees), 0) AS maxCoffeeId, IFNULL((SELECT MAX(id) FROM grinders), 0) AS maxGrinderId, " +
      "IFNULL((SELECT MAX(id) FROM grind_calibrations), 0) AS maxCalibrationId",
  )
  abstract suspend fun fingerprintRow(): FingerprintRow
}

/** La huella que dejaría importar estas filas: es lo que se espera encontrar en la base si la transacción se confirmó. */
internal fun BackupRows.fingerprint(): BackupFingerprint = BackupFingerprint(
  counts = listOf(
    brews.size, brewCurves.size, brewTargets.size, recipes.size, recipeStages.size, coffees.size, grinders.size,
    grinderUnits.size, grinderMethodRanges.size, grinderAnchors.size, grindCalibrations.size,
  ),
  maxIds = listOf(
    brews.maxOfOrNull { it.id } ?: 0L,
    recipes.maxOfOrNull { it.id } ?: 0L,
    coffees.maxOfOrNull { it.id } ?: 0L,
    grinders.maxOfOrNull { it.id } ?: 0L,
    grindCalibrations.maxOfOrNull { it.id } ?: 0L,
  ),
  photoNames = referencedPhotos(this).sorted(),
)

/** La huella de la base actual; [photoNames] sale de `CoffeeDao.photoNames()`. */
internal fun FingerprintRow.fingerprint(photoNames: List<String>): BackupFingerprint = BackupFingerprint(
  counts = listOf(
    brews, brewCurves, brewTargets, recipes, recipeStages, coffees, grinders, grinderUnits, grinderMethodRanges, grinderAnchors,
    grindCalibrations,
  ),
  maxIds = listOf(maxBrewId, maxRecipeId, maxCoffeeId, maxGrinderId, maxCalibrationId),
  photoNames = photoNames.distinct().sorted(),
)
