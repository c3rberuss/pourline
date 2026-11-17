package dev.josueamaya.pourline.history

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import dev.josueamaya.pourline.coffee.CoffeeConverters
import dev.josueamaya.pourline.coffee.CoffeeDao
import dev.josueamaya.pourline.coffee.CoffeeEntity
import dev.josueamaya.pourline.grinder.GrindCalibrationDao
import dev.josueamaya.pourline.grinder.GrindCalibrationEntity
import dev.josueamaya.pourline.grinder.GrinderAnchorEntity
import dev.josueamaya.pourline.grinder.GrinderConverters
import dev.josueamaya.pourline.grinder.GrinderDao
import dev.josueamaya.pourline.grinder.GrinderEntity
import dev.josueamaya.pourline.grinder.GrinderMethodRangeEntity
import dev.josueamaya.pourline.grinder.GrinderUnitEntity
import dev.josueamaya.pourline.recipe.BuiltInRecipeSeeder
import dev.josueamaya.pourline.recipe.RecipeDao
import dev.josueamaya.pourline.recipe.RecipeEntity
import dev.josueamaya.pourline.recipe.RecipeStageEntity
import dev.josueamaya.pourline.recipe.RecipeTexts
import dev.josueamaya.pourline.recipe.ResourcesRecipeTexts

/**
 * Base de datos del historial y de las recetas. Todo cambio de esquema posterior a la versión 2 exige una
 * `Migration` con su test: no se usa `fallbackToDestructiveMigration`.
 *
 * Versión 2 (SPEC-003): seis columnas nuevas en `brews`, `brew_targets`, `recipes` y `recipe_stages`.
 * Versión 3 (SPEC-004): la tabla `coffees` y las columnas `coffeeId` y `rating` en `brews`.
 * Versión 4 (SPEC-005): `type` e `instruction` en `recipe_stages` y los huecos de las recetas como pasos `WAIT`.
 * Versión 5 (SPEC-006): las tablas de molinos y, en `recipes` y `brews`, el ajuste estructurado (`grinderId`,
 * `grindValue` y `grindLabel`) en lugar del texto `grind`.
 * Versión 6 (SPEC-007): la columna `scaleless` en `brews`, para las preparaciones hechas sin báscula.
 * Versión 7 (SPEC-006, enmienda 1): la tabla `grind_calibrations`, con los pares de calibración entre molinos.
 */
@Database(
  entities = [
    BrewEntity::class,
    BrewCurveEntity::class,
    BrewTargetEntity::class,
    RecipeEntity::class,
    RecipeStageEntity::class,
    CoffeeEntity::class,
    GrinderEntity::class,
    GrinderUnitEntity::class,
    GrinderMethodRangeEntity::class,
    GrinderAnchorEntity::class,
    GrindCalibrationEntity::class,
  ],
  version = 7,
  exportSchema = true,
)
@TypeConverters(HistoryConverters::class, CoffeeConverters::class, GrinderConverters::class)
abstract class HistoryDatabase : RoomDatabase() {
  abstract fun brewDao(): BrewDao

  abstract fun recipeDao(): RecipeDao

  abstract fun coffeeDao(): CoffeeDao

  abstract fun grinderDao(): GrinderDao

  abstract fun grindCalibrationDao(): GrindCalibrationDao

  companion object {
    const val FILE_NAME = "history.db"

    /** [fileName] solo cambia en los tests de rendimiento, que usan un archivo aparte del `history.db` real. */
    fun create(
      context: Context,
      fileName: String = FILE_NAME,
      texts: RecipeTexts = ResourcesRecipeTexts(context),
    ): HistoryDatabase =
      Room.databaseBuilder(context.applicationContext, HistoryDatabase::class.java, fileName)
        .addMigrations(migration1To2(texts), MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
        .addCallback(seedOnCreate(texts))
        .build()

    /** Para los tests: no toca el `history.db` real. También siembra las predefinidas. */
    fun inMemory(context: Context, texts: RecipeTexts = ResourcesRecipeTexts(context)): HistoryDatabase =
      Room.inMemoryDatabaseBuilder(context.applicationContext, HistoryDatabase::class.java)
        .addCallback(seedOnCreate(texts))
        .build()

    /** Una instalación nueva se crea en la última versión con las recetas predefinidas. */
    private fun seedOnCreate(texts: RecipeTexts, clock: () -> Long = System::currentTimeMillis) =
      object : Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
          BuiltInRecipeSeeder.seed(db, texts, clock())
        }
      }
  }
}
