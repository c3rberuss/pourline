package dev.dotscale.recipe

import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Inserta las recetas predefinidas con SQL directo, que es lo único que ven `RoomDatabase.Callback.onCreate`
 * y las migraciones. Las filas llevan `localizedNames = 1` y el texto del idioma de la inserción como respaldo.
 */
object BuiltInRecipeSeeder {

  fun seed(db: SupportSQLiteDatabase, texts: RecipeTexts, nowMs: Long) {
    for (recipe in BuiltInRecipes.all(texts)) {
      val key = requireNotNull(recipe.builtInKey)
      db.execSQL(
        "INSERT INTO `recipes` (`name`, `method`, `methodOther`, `grind`, `waterTempC`, `notes`, `doseG`, " +
          "`builtInKey`, `localizedNames`, `updatedAtEpochMs`) VALUES (?, ?, NULL, NULL, NULL, NULL, ?, ?, 1, ?)",
        arrayOf<Any>(recipe.name, requireNotNull(recipe.method).name, recipe.doseG, key, nowMs),
      )
      val id = db.query("SELECT `id` FROM `recipes` WHERE `builtInKey` = ?", arrayOf<Any>(key)).use { cursor ->
        check(cursor.moveToFirst()) { "Built-in recipe $key was not inserted" }
        cursor.getLong(0)
      }
      recipe.stages.forEachIndexed { position, stage ->
        db.execSQL(
          "INSERT INTO `recipe_stages` (`recipeId`, `position`, `name`, `startMs`, `targetWaterG`, `durationMs`) " +
            "VALUES (?, ?, ?, ?, ?, ?)",
          arrayOf<Any?>(id, position, stage.name, stage.startMs, stage.targetWaterG, stage.durationMs),
        )
      }
    }
  }
}
