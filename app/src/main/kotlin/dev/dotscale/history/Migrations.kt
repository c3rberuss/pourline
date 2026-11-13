package dev.dotscale.history

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dev.dotscale.recipe.BuiltInRecipeSeeder
import dev.dotscale.recipe.RecipeTexts

/**
 * Migración 1 → 2 (SPEC-003). Corre en la transacción de la migración: el historial existente se
 * conserva con las columnas nuevas a `null`, y se insertan las recetas predefinidas.
 *
 * Los `CREATE TABLE` y los índices se copian de `app/schemas/.../2.json`, y las columnas nuevas no llevan
 * `DEFAULT`, para que `runMigrationsAndValidate` encuentre el esquema idéntico.
 */
fun migration1To2(texts: RecipeTexts, clock: () -> Long = System::currentTimeMillis): Migration =
  object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
      db.execSQL("ALTER TABLE `brews` ADD COLUMN `referenceType` TEXT")
      db.execSQL("ALTER TABLE `brews` ADD COLUMN `referenceId` INTEGER")
      db.execSQL("ALTER TABLE `brews` ADD COLUMN `referenceName` TEXT")
      db.execSQL("ALTER TABLE `brews` ADD COLUMN `targetWaterG` REAL")
      db.execSQL("ALTER TABLE `brews` ADD COLUMN `targetEndMs` INTEGER")
      db.execSQL("ALTER TABLE `brews` ADD COLUMN `fitScore` INTEGER")

      db.execSQL(
        "CREATE TABLE IF NOT EXISTS `brew_targets` (`brewId` INTEGER NOT NULL, `data` BLOB NOT NULL, " +
          "PRIMARY KEY(`brewId`), FOREIGN KEY(`brewId`) REFERENCES `brews`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
      )
      db.execSQL(
        "CREATE TABLE IF NOT EXISTS `recipes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
          "`method` TEXT, `methodOther` TEXT, `grind` TEXT, `waterTempC` REAL, `notes` TEXT, `doseG` REAL NOT NULL, " +
          "`builtInKey` TEXT, `localizedNames` INTEGER NOT NULL, `updatedAtEpochMs` INTEGER NOT NULL)",
      )
      db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_recipes_builtInKey` ON `recipes` (`builtInKey`)")
      db.execSQL(
        "CREATE TABLE IF NOT EXISTS `recipe_stages` (`recipeId` INTEGER NOT NULL, `position` INTEGER NOT NULL, " +
          "`name` TEXT, `startMs` INTEGER NOT NULL, `targetWaterG` REAL NOT NULL, `durationMs` INTEGER NOT NULL, " +
          "PRIMARY KEY(`recipeId`, `position`), FOREIGN KEY(`recipeId`) REFERENCES `recipes`(`id`) " +
          "ON UPDATE NO ACTION ON DELETE CASCADE )",
      )

      BuiltInRecipeSeeder.seed(db, texts, clock())
    }
  }
