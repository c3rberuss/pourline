package dev.josueamaya.pourline.history

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dev.josueamaya.pourline.recipe.BuiltInRecipeSeeder
import dev.josueamaya.pourline.recipe.RecipeStage
import dev.josueamaya.pourline.recipe.RecipeTexts
import dev.josueamaya.pourline.recipe.SeedSchema
import dev.josueamaya.pourline.recipe.StageGaps

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

      BuiltInRecipeSeeder.seed(db, texts, clock(), SeedSchema.V2)
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

/**
 * Migración 3 → 4 (SPEC-005, D35): `type` e `instruction` en `recipe_stages`. Los pasos existentes quedan como
 * `POUR` y sin instrucción, y cada hueco entre pasos (también el inicial) pasa a ser un paso `WAIT` explícito
 * ([StageGaps.fillWaits]), así que la curva objetivo de cada receta no cambia. Los solapes no generan nada.
 *
 * La clave primaria es `(recipeId, position)`, así que, por cada receta que cambia, se borran sus filas y se
 * vuelven a insertar con `position` renumerada; `recipes` no se toca.
 */
val MIGRATION_3_4: Migration = object : Migration(3, 4) {
  override fun migrate(db: SupportSQLiteDatabase) {
    db.execSQL("ALTER TABLE `recipe_stages` ADD COLUMN `type` TEXT NOT NULL DEFAULT 'POUR'")
    db.execSQL("ALTER TABLE `recipe_stages` ADD COLUMN `instruction` TEXT")

    val recipeIds = db.query("SELECT `id` FROM `recipes` ORDER BY `id`").use { cursor ->
      buildList { while (cursor.moveToNext()) add(cursor.getLong(0)) }
    }
    for (recipeId in recipeIds) {
      val stages = db.query(
        "SELECT `name`, `startMs`, `targetWaterG`, `durationMs` FROM `recipe_stages` WHERE `recipeId` = ? ORDER BY `position`",
        arrayOf<Any>(recipeId),
      ).use { cursor ->
        buildList {
          while (cursor.moveToNext()) {
            add(
              RecipeStage(
                name = if (cursor.isNull(0)) null else cursor.getString(0),
                startMs = cursor.getLong(1),
                targetWaterG = cursor.getDouble(2),
                durationMs = cursor.getLong(3),
              ),
            )
          }
        }
      }
      val filled = StageGaps.fillWaits(stages)
      if (filled.size == stages.size) continue
      db.execSQL("DELETE FROM `recipe_stages` WHERE `recipeId` = ?", arrayOf<Any>(recipeId))
      filled.forEachIndexed { position, stage ->
        db.execSQL(
          "INSERT INTO `recipe_stages` (`recipeId`, `position`, `name`, `startMs`, `targetWaterG`, `durationMs`, `type`, " +
            "`instruction`) VALUES (?, ?, ?, ?, ?, ?, ?, NULL)",
          arrayOf<Any?>(recipeId, position, stage.name, stage.startMs, stage.targetWaterG, stage.durationMs, stage.type.name),
        )
      }
    }
  }
}

/**
 * Migración 4 → 5 (SPEC-006, D19): las tablas de molinos y el ajuste estructurado en `recipes` y `brews`. SQLite 3.18
 * (minSdk 26) no tiene `DROP COLUMN`, así que `recipes` y `brews` se reconstruyen (crear, copiar, borrar, renombrar)
 * sin la columna `grind` y con `grinderId`, `grindValue` y `grindLabel` a `NULL`: el texto anterior se descarta.
 *
 * Room ejecuta las migraciones con las claves foráneas desactivadas (las activa `onOpen`, después). Con ellas activas,
 * el `DROP TABLE` borraría en cascada `recipe_stages`, `brew_curves` y `brew_targets`: por eso la migración aborta sin
 * tocar nada si `PRAGMA foreign_keys` vale 1, y al terminar comprueba `PRAGMA foreign_key_check`. Las dos tablas son
 * `AUTOINCREMENT`, así que se repone `sqlite_sequence` para no reutilizar los ids de filas borradas, que `ReferenceKey`
 * y `referenceId` guardan sin clave foránea. El catálogo no se siembra aquí, sino al arrancar (§3.6).
 *
 * Los `CREATE TABLE` y los índices se copian de `app/schemas/.../5.json`.
 */
val MIGRATION_4_5: Migration = object : Migration(4, 5) {
  override fun migrate(db: SupportSQLiteDatabase) {
    val foreignKeys = db.query("PRAGMA foreign_keys").use { cursor -> cursor.moveToFirst() && cursor.getInt(0) == 1 }
    check(!foreignKeys) { "Migration 4 -> 5 needs foreign keys disabled: rebuilding recipes and brews would cascade" }

    db.execSQL(
      "CREATE TABLE IF NOT EXISTS `grinders` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `catalogKey` TEXT, " +
        "`brand` TEXT NOT NULL, `model` TEXT NOT NULL, `direction` TEXT NOT NULL, `scaleMin` REAL NOT NULL, " +
        "`scaleMax` REAL NOT NULL, `step` REAL NOT NULL, `owned` INTEGER NOT NULL, `retired` INTEGER NOT NULL, " +
        "`sourceUrl` TEXT, `sourceName` TEXT, `duplicatedFromKey` TEXT, `updatedAtEpochMs` INTEGER NOT NULL)",
    )
    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_grinders_catalogKey` ON `grinders` (`catalogKey`)")
    db.execSQL(
      "CREATE TABLE IF NOT EXISTS `grinder_units` (`grinderId` INTEGER NOT NULL, `position` INTEGER NOT NULL, " +
        "`unit` TEXT NOT NULL, `perParent` INTEGER, PRIMARY KEY(`grinderId`, `position`), " +
        "FOREIGN KEY(`grinderId`) REFERENCES `grinders`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
    )
    db.execSQL(
      "CREATE TABLE IF NOT EXISTS `grinder_method_ranges` (`grinderId` INTEGER NOT NULL, `method` TEXT NOT NULL, " +
        "`min` REAL NOT NULL, `max` REAL NOT NULL, PRIMARY KEY(`grinderId`, `method`), " +
        "FOREIGN KEY(`grinderId`) REFERENCES `grinders`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
    )
    db.execSQL(
      "CREATE TABLE IF NOT EXISTS `grinder_anchors` (`grinderId` INTEGER NOT NULL, `level` TEXT NOT NULL, " +
        "`value` REAL NOT NULL, PRIMARY KEY(`grinderId`, `level`), " +
        "FOREIGN KEY(`grinderId`) REFERENCES `grinders`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
    )

    rebuild(
      db,
      table = "recipes",
      createSql = RECIPES_V5_SQL,
      columns = "`id`, `name`, `method`, `methodOther`, `waterTempC`, `notes`, `doseG`, `builtInKey`, `localizedNames`, `updatedAtEpochMs`",
      indices = listOf(
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_recipes_builtInKey` ON `recipes` (`builtInKey`)",
        "CREATE INDEX IF NOT EXISTS `index_recipes_grinderId` ON `recipes` (`grinderId`)",
      ),
    )
    rebuild(
      db,
      table = "brews",
      createSql = BREWS_V5_SQL,
      columns = "`id`, `startedAtEpochMs`, `name`, `coffee`, `method`, `methodOther`, `waterTempC`, `notes`, `totalTimeMs`, " +
        "`totalWaterG`, `avgFlowGps`, `maxFlowGps`, `stabilityScore`, `stabilityLevel`, `shareStable`, `shareIrregular`, " +
        "`shareTurbulent`, `doseG`, `finalRatio`, `finishReason`, `interrupted`, `unit`, `scaleModel`, `simulated`, " +
        "`metricVersion`, `preview`, `referenceType`, `referenceId`, `referenceName`, `targetWaterG`, `targetEndMs`, " +
        "`fitScore`, `coffeeId`, `rating`",
      indices = listOf(
        "CREATE INDEX IF NOT EXISTS `index_brews_startedAtEpochMs` ON `brews` (`startedAtEpochMs`)",
        "CREATE INDEX IF NOT EXISTS `index_brews_coffeeId` ON `brews` (`coffeeId`)",
        "CREATE INDEX IF NOT EXISTS `index_brews_grinderId` ON `brews` (`grinderId`)",
      ),
    )

    for (table in listOf("recipes", "brews")) {
      val violations = db.query("PRAGMA foreign_key_check(`$table`)").use { it.count }
      check(violations == 0) { "Migration 4 -> 5 left $violations foreign key violations in $table" }
    }
  }

  /** Crea `<table>_new`, copia [columns], borra la antigua, renombra, recrea [indices] y repone `sqlite_sequence`. */
  private fun rebuild(db: SupportSQLiteDatabase, table: String, createSql: String, columns: String, indices: List<String>) {
    val sequence = db.query("SELECT `seq` FROM `sqlite_sequence` WHERE `name` = ?", arrayOf<Any>(table)).use { cursor ->
      if (cursor.moveToFirst()) cursor.getLong(0) else null
    }
    db.execSQL(createSql.replace("`$table`", "`${table}_new`"))
    db.execSQL("INSERT INTO `${table}_new` ($columns) SELECT $columns FROM `$table`")
    db.execSQL("DROP TABLE `$table`")
    db.execSQL("ALTER TABLE `${table}_new` RENAME TO `$table`")
    indices.forEach(db::execSQL)
    if (sequence != null) {
      db.execSQL("DELETE FROM `sqlite_sequence` WHERE `name` = ?", arrayOf<Any>(table))
      db.execSQL("INSERT INTO `sqlite_sequence` (`name`, `seq`) VALUES (?, ?)", arrayOf<Any>(table, sequence))
    }
  }
}

// Copiados de `app/schemas/.../5.json` (con `${TABLE_NAME}` sustituido).
private const val RECIPES_V5_SQL =
  "CREATE TABLE IF NOT EXISTS `recipes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
  "`name` TEXT NOT NULL, `method` TEXT, `methodOther` TEXT, `waterTempC` REAL, `notes` TEXT, " +
  "`doseG` REAL NOT NULL, `builtInKey` TEXT, `localizedNames` INTEGER NOT NULL, " +
  "`updatedAtEpochMs` INTEGER NOT NULL, `grinderId` INTEGER, `grindValue` REAL, `grindLabel` TEXT, " +
  "FOREIGN KEY(`grinderId`) REFERENCES `grinders`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )"
private const val BREWS_V5_SQL =
  "CREATE TABLE IF NOT EXISTS `brews` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
  "`startedAtEpochMs` INTEGER NOT NULL, `name` TEXT, `coffee` TEXT, `method` TEXT, `methodOther` TEXT, " +
  "`waterTempC` REAL, `notes` TEXT, `totalTimeMs` INTEGER NOT NULL, `totalWaterG` REAL NOT NULL, " +
  "`avgFlowGps` REAL NOT NULL, `maxFlowGps` REAL NOT NULL, `stabilityScore` INTEGER, `stabilityLevel` TEXT, " +
  "`shareStable` REAL NOT NULL, `shareIrregular` REAL NOT NULL, `shareTurbulent` REAL NOT NULL, " +
  "`doseG` REAL, `finalRatio` REAL, `finishReason` TEXT NOT NULL, `interrupted` INTEGER NOT NULL, " +
  "`unit` TEXT NOT NULL, `scaleModel` TEXT, `simulated` INTEGER NOT NULL, `metricVersion` INTEGER NOT NULL, " +
  "`preview` BLOB NOT NULL, `referenceType` TEXT, `referenceId` INTEGER, `referenceName` TEXT, " +
  "`targetWaterG` REAL, `targetEndMs` INTEGER, `fitScore` INTEGER, `coffeeId` INTEGER, `rating` INTEGER, " +
  "`grinderId` INTEGER, `grindValue` REAL, `grindLabel` TEXT, " +
  "FOREIGN KEY(`coffeeId`) REFERENCES `coffees`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL , " +
  "FOREIGN KEY(`grinderId`) REFERENCES `grinders`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )"
