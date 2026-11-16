package dev.dotscale.recipe

import androidx.sqlite.db.SupportSQLiteDatabase

/** Con qué columnas de `recipe_stages` se siembra: las de la versión 2 (sin tipo ni instrucción) o las actuales. */
enum class SeedSchema { V2, CURRENT }

/**
 * Inserta las recetas predefinidas con SQL directo, que es lo único que ven `RoomDatabase.Callback.onCreate`
 * y las migraciones. Las filas llevan `localizedNames = 1` y el texto del idioma de la inserción como respaldo.
 *
 * [SeedSchema.V2] lo usa la migración 1 → 2, cuya tabla aún no tiene `type` ni `instruction`: inserta solo los
 * pasos `POUR`, y la migración 3 → 4 los convierte en la definición actual (D38).
 */
object BuiltInRecipeSeeder {

  fun seed(db: SupportSQLiteDatabase, texts: RecipeTexts, nowMs: Long, schema: SeedSchema = SeedSchema.CURRENT) {
    for (recipe in BuiltInRecipes.all(texts)) {
      val key = requireNotNull(recipe.builtInKey)
      db.execSQL(
        "INSERT INTO `recipes` (`name`, `method`, `methodOther`, `waterTempC`, `notes`, `doseG`, " +
          "`builtInKey`, `localizedNames`, `updatedAtEpochMs`) VALUES (?, ?, NULL, NULL, NULL, ?, ?, 1, ?)",
        arrayOf<Any>(recipe.name, requireNotNull(recipe.method).name, recipe.doseG, key, nowMs),
      )
      val id = db.query("SELECT `id` FROM `recipes` WHERE `builtInKey` = ?", arrayOf<Any>(key)).use { cursor ->
        check(cursor.moveToFirst()) { "Built-in recipe $key was not inserted" }
        cursor.getLong(0)
      }
      val stages = if (schema == SeedSchema.V2) recipe.stages.filter { it.type == StageType.POUR } else recipe.stages
      stages.forEachIndexed { position, stage ->
        when (schema) {
          SeedSchema.V2 -> db.execSQL(
            "INSERT INTO `recipe_stages` (`recipeId`, `position`, `name`, `startMs`, `targetWaterG`, `durationMs`) " +
              "VALUES (?, ?, ?, ?, ?, ?)",
            arrayOf<Any?>(id, position, stage.name, stage.startMs, stage.targetWaterG, stage.durationMs),
          )
          SeedSchema.CURRENT -> db.execSQL(
            "INSERT INTO `recipe_stages` (`recipeId`, `position`, `name`, `startMs`, `targetWaterG`, `durationMs`, " +
              "`type`, `instruction`) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            arrayOf<Any?>(id, position, stage.name, stage.startMs, stage.targetWaterG, stage.durationMs, stage.type.name, stage.instruction),
          )
        }
      }
    }
  }
}
