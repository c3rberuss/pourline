package dev.dotscale.history

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import dev.dotscale.coffee.CoffeeConverters
import dev.dotscale.coffee.CoffeeDao
import dev.dotscale.coffee.CoffeeEntity
import dev.dotscale.recipe.BuiltInRecipeSeeder
import dev.dotscale.recipe.RecipeDao
import dev.dotscale.recipe.RecipeEntity
import dev.dotscale.recipe.RecipeStageEntity
import dev.dotscale.recipe.RecipeTexts
import dev.dotscale.recipe.ResourcesRecipeTexts

/**
 * Base de datos del historial y de las recetas. Todo cambio de esquema posterior a la versión 2 exige una
 * `Migration` con su test: no se usa `fallbackToDestructiveMigration`.
 *
 * Versión 2 (SPEC-003): seis columnas nuevas en `brews`, `brew_targets`, `recipes` y `recipe_stages`.
 * Versión 3 (SPEC-004): la tabla `coffees` y las columnas `coffeeId` y `rating` en `brews`.
 * Versión 4 (SPEC-005): `type` e `instruction` en `recipe_stages` y los huecos de las recetas como pasos `WAIT`.
 */
@Database(
  entities = [
    BrewEntity::class,
    BrewCurveEntity::class,
    BrewTargetEntity::class,
    RecipeEntity::class,
    RecipeStageEntity::class,
    CoffeeEntity::class,
  ],
  version = 4,
  exportSchema = true,
)
@TypeConverters(HistoryConverters::class, CoffeeConverters::class)
abstract class HistoryDatabase : RoomDatabase() {
  abstract fun brewDao(): BrewDao

  abstract fun recipeDao(): RecipeDao

  abstract fun coffeeDao(): CoffeeDao

  companion object {
    const val FILE_NAME = "history.db"

    /** [fileName] solo cambia en los tests de rendimiento, que usan un archivo aparte del `history.db` real. */
    fun create(
      context: Context,
      fileName: String = FILE_NAME,
      texts: RecipeTexts = ResourcesRecipeTexts(context),
    ): HistoryDatabase =
      Room.databaseBuilder(context.applicationContext, HistoryDatabase::class.java, fileName)
        .addMigrations(migration1To2(texts), MIGRATION_2_3, MIGRATION_3_4)
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
