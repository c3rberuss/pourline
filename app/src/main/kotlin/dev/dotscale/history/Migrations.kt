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

/**
 * Migración 2 → 3 (SPEC-004, D16): la tabla `coffees` y dos columnas en `brews`. Los `CREATE TABLE` y los
 * índices se copian de `app/schemas/.../3.json`. No se reconstruye `brews`: borrarla dispararía el `CASCADE`
 * hacia `brew_curves` y `brew_targets`. SQLite admite `REFERENCES` en `ADD COLUMN` con valor por defecto `NULL`.
 * Los textos de café que ya hay en las preparaciones no se tocan y quedan sin vínculo.
 */
val MIGRATION_2_3: Migration = object : Migration(2, 3) {
  override fun migrate(db: SupportSQLiteDatabase) {
    db.execSQL(
      "CREATE TABLE IF NOT EXISTS `coffees` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
        "`roaster` TEXT, `originCountry` TEXT, `originRegion` TEXT, `farm` TEXT, `varieties` TEXT NOT NULL, " +
        "`process` TEXT, `processOther` TEXT, `altitudeMinM` INTEGER, `altitudeMaxM` INTEGER, `roastLevel` TEXT, " +
        "`roastDate` INTEGER, `roasterNotes` TEXT, `bagWeightG` REAL, `price` REAL, `openedDate` INTEGER, " +
        "`photo1` TEXT, `photo2` TEXT, `archived` INTEGER NOT NULL, `createdAtEpochMs` INTEGER NOT NULL, " +
        "`lastUsedAtEpochMs` INTEGER)",
    )
    db.execSQL("CREATE INDEX IF NOT EXISTS `index_coffees_name` ON `coffees` (`name`)")
    db.execSQL("CREATE INDEX IF NOT EXISTS `index_coffees_archived` ON `coffees` (`archived`)")
    db.execSQL(
      "ALTER TABLE `brews` ADD COLUMN `coffeeId` INTEGER REFERENCES `coffees`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL",
    )
    db.execSQL("ALTER TABLE `brews` ADD COLUMN `rating` INTEGER")
    db.execSQL("CREATE INDEX IF NOT EXISTS `index_brews_coffeeId` ON `brews` (`coffeeId`)")
  }
}
