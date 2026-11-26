package dev.josueamaya.pourline.history

import androidx.sqlite.db.SupportSQLiteDatabase
import dev.josueamaya.pourline.recipe.RecipeTexts

/**
 * Las dos recetas de fábrica que las versiones 2 a 7 de la base sembraban. Ya no se siembran (SPEC-012): esto solo lo
 * usan la migración 1 → 2, que las crea para que la cadena de migraciones siga funcionando, y la 7 → 8, que las
 * convierte en recetas propias.
 */
internal object LegacyBuiltIns {
  const val V60_CLASSIC = "v60_classic"
  const val FOUR_SIX = "four_six"

  /** Claves de las recetas de fábrica que pudo haber en una base antigua. */
  val KEYS: Set<String> = setOf(V60_CLASSIC, FOUR_SIX)

  /** Un vertido de la definición antigua. [nameIndex] es su posición en la lista completa (con las esperas). */
  private class Pour(val nameIndex: Int, val startS: Int, val waterG: Double, val durationS: Int)

  private class Definition(val doseG: Double, val pours: List<Pour>)

  private val definitions = linkedMapOf(
    V60_CLASSIC to Definition(
      15.0,
      listOf(Pour(0, 0, 40.0, 10), Pour(2, 45, 150.0, 20), Pour(4, 90, 250.0, 25)),
    ),
    FOUR_SIX to Definition(
      20.0,
      listOf(
        Pour(0, 0, 60.0, 10),
        Pour(2, 45, 120.0, 10),
        Pour(4, 90, 180.0, 10),
        Pour(6, 135, 240.0, 10),
        Pour(8, 180, 300.0, 10),
      ),
    ),
  )

  /**
   * Inserta las recetas con las columnas de `recipes` y `recipe_stages` de la versión 2 (sin `type` ni `instruction`):
   * solo los vertidos, y la migración 3 → 4 los convierte en la definición actual con los huecos como esperas.
   * Las filas llevan `localizedNames = 1` y el texto del idioma de la inserción como respaldo.
   */
  fun seedV2(db: SupportSQLiteDatabase, texts: RecipeTexts, nowMs: Long) {
    for ((key, definition) in definitions) {
      db.execSQL(
        "INSERT INTO `recipes` (`name`, `method`, `methodOther`, `waterTempC`, `notes`, `doseG`, " +
          "`builtInKey`, `localizedNames`, `updatedAtEpochMs`) VALUES (?, ?, NULL, NULL, NULL, ?, ?, 1, ?)",
        arrayOf<Any>(texts.builtInName(key), "V60", definition.doseG, key, nowMs),
      )
      val id = db.query("SELECT `id` FROM `recipes` WHERE `builtInKey` = ?", arrayOf<Any>(key)).use { cursor ->
        check(cursor.moveToFirst()) { "Built-in recipe $key was not inserted" }
        cursor.getLong(0)
      }
      val names = texts.builtInStageNames(key)
      definition.pours.forEachIndexed { position, pour ->
        db.execSQL(
          "INSERT INTO `recipe_stages` (`recipeId`, `position`, `name`, `startMs`, `targetWaterG`, `durationMs`) " +
            "VALUES (?, ?, ?, ?, ?, ?)",
          arrayOf<Any?>(id, position, names.getOrNull(pour.nameIndex), pour.startS * 1_000L, pour.waterG, pour.durationS * 1_000L),
        )
      }
    }
  }
}
