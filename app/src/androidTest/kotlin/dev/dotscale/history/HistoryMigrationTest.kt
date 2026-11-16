package dev.dotscale.history

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.dotscale.recipe.BuiltInRecipes
import dev.dotscale.brew.TargetCurve
import dev.dotscale.recipe.FakeRecipeTexts
import dev.dotscale.recipe.Recipe
import dev.dotscale.recipe.RecipeRepository
import dev.dotscale.recipe.RecipeStage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.room.testing.MigrationTestHelper

/** Migraciones 1 → 2, 2 → 3 y 3 → 4 sobre bases creadas con los esquemas exportados. */
@RunWith(AndroidJUnit4::class)
class HistoryMigrationTest {
  private val instrumentation = InstrumentationRegistry.getInstrumentation()
  private val texts = FakeRecipeTexts()

  @get:Rule
  val helper = MigrationTestHelper(instrumentation, HistoryDatabase::class.java)

  @After
  fun deleteDatabase() {
    instrumentation.targetContext.deleteDatabase(NAME)
  }

  private val preview = ByteArray(48) { it.toByte() }

  private fun insertV1Brew(db: SupportSQLiteDatabase, id: Long, name: String?, coffee: String?, startedAt: Long) {
    db.execSQL(
      "INSERT INTO brews (id, startedAtEpochMs, name, coffee, method, methodOther, grind, waterTempC, notes, " +
        "totalTimeMs, totalWaterG, avgFlowGps, maxFlowGps, stabilityScore, stabilityLevel, shareStable, shareIrregular, " +
        "shareTurbulent, doseG, finalRatio, finishReason, interrupted, unit, scaleModel, simulated, metricVersion, preview) " +
        "VALUES (?, ?, ?, ?, 'V60', NULL, 'Media', 93.5, 'Notas de cata', 240000, 300.5, 3.2, 5.1, 82, 'STABLE', 0.7, " +
        "0.2, 0.1, 20.0, 15.025, 'MANUAL', 0, 'GRAMS', 'DOT', 0, 1, ?)",
      arrayOf<Any?>(id, startedAt, name, coffee, preview),
    )
    db.execSQL("INSERT INTO brew_curves (brewId, data) VALUES (?, ?)", arrayOf<Any>(id, byteArrayOf(1, 2, 3, id.toByte())))
  }

  @Test
  fun migratesKeepingTheBrewsAndAddingTheNewColumnsAndTables() {
    helper.createDatabase(NAME, 1).use { db ->
      insertV1Brew(db, 1, "Etiopía", "Etiopía Guji", 1_700_000_000_000)
      insertV1Brew(db, 2, null, null, 1_700_000_100_000)
    }

    val db = helper.runMigrationsAndValidate(NAME, 2, true, migration1To2(texts))

    // Las filas se conservan con todas sus columnas.
    db.query("SELECT * FROM brews ORDER BY id").use { c ->
      assertEquals(2, c.count)
      c.moveToFirst()
      fun text(column: String) = c.getString(c.getColumnIndexOrThrow(column))
      fun double(column: String) = c.getDouble(c.getColumnIndexOrThrow(column))
      assertEquals(1L, c.getLong(c.getColumnIndexOrThrow("id")))
      assertEquals(1_700_000_000_000, c.getLong(c.getColumnIndexOrThrow("startedAtEpochMs")))
      assertEquals("Etiopía", text("name"))
      assertEquals("Etiopía Guji", text("coffee"))
      assertEquals("V60", text("method"))
      assertEquals("Media", text("grind"))
      assertEquals(93.5, double("waterTempC"), 0.0)
      assertEquals("Notas de cata", text("notes"))
      assertEquals(240_000L, c.getLong(c.getColumnIndexOrThrow("totalTimeMs")))
      assertEquals(300.5, double("totalWaterG"), 0.0)
      assertEquals(82, c.getInt(c.getColumnIndexOrThrow("stabilityScore")))
      assertEquals("STABLE", text("stabilityLevel"))
      assertEquals(20.0, double("doseG"), 0.0)
      assertEquals(15.025, double("finalRatio"), 0.0)
      assertEquals("GRAMS", text("unit"))
      assertEquals("DOT", text("scaleModel"))
      assertEquals(1, c.getInt(c.getColumnIndexOrThrow("metricVersion")))
      assertArrayEquals(preview, c.getBlob(c.getColumnIndexOrThrow("preview")))
      // Las columnas nuevas, a null.
      for (column in listOf("referenceType", "referenceId", "referenceName", "targetWaterG", "targetEndMs", "fitScore")) {
        assertTrue("$column should be null", c.isNull(c.getColumnIndexOrThrow(column)))
      }
      c.moveToNext()
      assertEquals(2L, c.getLong(c.getColumnIndexOrThrow("id")))
      assertTrue(c.isNull(c.getColumnIndexOrThrow("name")))
      assertTrue(c.isNull(c.getColumnIndexOrThrow("fitScore")))
    }

    // brew_curves sigue igual y brew_targets existe vacía.
    db.query("SELECT brewId, data FROM brew_curves ORDER BY brewId").use { c ->
      assertEquals(2, c.count)
      c.moveToFirst()
      assertArrayEquals(byteArrayOf(1, 2, 3, 1), c.getBlob(1))
      c.moveToNext()
      assertArrayEquals(byteArrayOf(1, 2, 3, 2), c.getBlob(1))
    }
    db.query("SELECT COUNT(*) FROM brew_targets").use {
      it.moveToFirst()
      assertEquals(0, it.getInt(0))
    }

    // Las dos predefinidas, con sus etapas.
    db.query("SELECT id, name, builtInKey, localizedNames, doseG, method FROM recipes ORDER BY id").use { c ->
      assertEquals(2, c.count)
      c.moveToFirst()
      assertEquals("V60 clásica", c.getString(1))
      assertEquals(BuiltInRecipes.V60_CLASSIC, c.getString(2))
      assertEquals(1, c.getInt(3))
      assertEquals(15.0, c.getDouble(4), 0.0)
      assertEquals("V60", c.getString(5))
      val v60Id = c.getLong(0)
      c.moveToNext()
      assertEquals(BuiltInRecipes.FOUR_SIX, c.getString(2))
      assertEquals(20.0, c.getDouble(4), 0.0)
      val fourSixId = c.getLong(0)
      assertEquals(3, stageCount(db, v60Id))
      assertEquals(5, stageCount(db, fourSixId))
    }
    db.close()

    // Y la base se abre con Room y se lee por los DAO.
    val room = Room.databaseBuilder(instrumentation.targetContext, HistoryDatabase::class.java, NAME)
      .addMigrations(migration1To2(texts), MIGRATION_2_3, MIGRATION_3_4)
      .build()
    try {
      runBlocking {
        val repository = BrewRepository(room)
        val items = repository.list(BrewFilter()).first()
        assertEquals(listOf(2L, 1L), items.map { it.id })
        assertTrue(items.all { it.fitScore == null })
        val first = room.brewDao().brew(1)!!
        assertEquals("Etiopía", first.name)
        assertEquals(null, first.referenceType)
        assertEquals(null, first.fitScore)
        assertEquals(2, room.recipeDao().observeAll().first().size)
      }
    } finally {
      room.close()
    }
  }

  @Test
  fun anEmptyV1DatabaseMigratesToo() {
    helper.createDatabase(NAME, 1).close()
    helper.runMigrationsAndValidate(NAME, 2, true, migration1To2(texts)).use { db ->
      db.query("SELECT COUNT(*) FROM recipes").use {
        it.moveToFirst()
        assertEquals(2, it.getInt(0))
      }
      db.query("SELECT COUNT(*) FROM brews").use {
        it.moveToFirst()
        assertEquals(0, it.getInt(0))
      }
    }
  }

  @Test
  fun aFreshInstallIsCreatedAtVersion4WithTheBuiltIns() = runBlocking {
    val context = instrumentation.targetContext
    context.deleteDatabase(NAME)
    val fresh = HistoryDatabase.create(context, NAME, texts)
    try {
      assertEquals(2, fresh.recipeDao().observeAll().first().size)
      assertEquals(4, fresh.openHelper.readableDatabase.version)
      val steps = fresh.openHelper.readableDatabase.query(
        "SELECT builtInKey, COUNT(*) FROM recipe_stages JOIN recipes ON recipes.id = recipeId GROUP BY builtInKey",
      ).use { c -> buildMap { while (c.moveToNext()) put(c.getString(0), c.getInt(1)) } }
      assertEquals(mapOf(BuiltInRecipes.V60_CLASSIC to 5, BuiltInRecipes.FOUR_SIX to 9), steps)
      assertFalse(fresh.brewDao().list(null, null).first().isNotEmpty())
    } finally {
      fresh.close()
    }
  }

  @Test
  fun migrates2To3KeepingTheBrewsAndLeavingTheCoffeeTextUnlinked() {
    helper.createDatabase(NAME, 2).use { db ->
      insertV1Brew(db, 1, "Etiopía", "Etiopía Guji", 1_700_000_000_000)
      insertV1Brew(db, 2, null, null, 1_700_000_100_000)
    }

    val db = helper.runMigrationsAndValidate(NAME, 3, true, MIGRATION_2_3)

    db.query("SELECT id, name, coffee, notes, totalWaterG, coffeeId, rating FROM brews ORDER BY id").use { c ->
      assertEquals(2, c.count)
      c.moveToFirst()
      assertEquals(1L, c.getLong(0))
      assertEquals("Etiopía", c.getString(1))
      assertEquals("Etiopía Guji", c.getString(2))
      assertEquals("Notas de cata", c.getString(3))
      assertEquals(300.5, c.getDouble(4), 0.0)
      assertTrue(c.isNull(5))
      assertTrue(c.isNull(6))
      c.moveToNext()
      assertTrue(c.isNull(2))
      assertTrue(c.isNull(5))
    }
    db.query("SELECT COUNT(*) FROM coffees").use {
      it.moveToFirst()
      assertEquals(0, it.getInt(0))
    }
    db.query("SELECT brewId, data FROM brew_curves ORDER BY brewId").use { c ->
      assertEquals(2, c.count)
      c.moveToFirst()
      assertArrayEquals(byteArrayOf(1, 2, 3, 1), c.getBlob(1))
    }
    db.query("SELECT COUNT(*) FROM brew_targets").use {
      it.moveToFirst()
      assertEquals(0, it.getInt(0))
    }
    db.close()

    // En la base migrada la clave foránea funciona: vincular un café y borrarlo deja `coffeeId = null` y el nombre.
    val room = Room.databaseBuilder(instrumentation.targetContext, HistoryDatabase::class.java, NAME)
      .addMigrations(migration1To2(texts), MIGRATION_2_3, MIGRATION_3_4)
      .build()
    try {
      runBlocking {
        val coffees = dev.dotscale.coffee.CoffeeRepository(room, dev.dotscale.coffee.PhotoFiles { })
        val brews = BrewRepository(room)
        val coffeeId = coffees.save(TestBrews.coffeeDraft(name = "Kenia AA"))
        brews.setCoffee(1, coffeeId)
        assertEquals(coffeeId, room.brewDao().brew(1)!!.coffeeId)
        coffees.delete(coffeeId)
        val brew = room.brewDao().brew(1)!!
        assertEquals(null, brew.coffeeId)
        assertEquals("Kenia AA", brew.coffee)
      }
    } finally {
      room.close()
    }
  }

  @Test
  fun migrates1To2To3InAChain() {
    helper.createDatabase(NAME, 1).use { db -> insertV1Brew(db, 1, "Etiopía", "Etiopía Guji", 1_700_000_000_000) }
    helper.runMigrationsAndValidate(NAME, 3, true, migration1To2(texts), MIGRATION_2_3).use { db ->
      db.query("SELECT coffee, coffeeId, rating, fitScore FROM brews").use { c ->
        c.moveToFirst()
        assertEquals("Etiopía Guji", c.getString(0))
        assertTrue(c.isNull(1))
        assertTrue(c.isNull(2))
        assertTrue(c.isNull(3))
      }
      db.query("SELECT COUNT(*) FROM recipes").use {
        it.moveToFirst()
        assertEquals(2, it.getInt(0))
      }
    }
  }

  /** Inserta una receta v3 con SQL (sin `type` ni `instruction`) y devuelve su id. */
  private fun insertV3Recipe(db: SupportSQLiteDatabase, id: Long, stages: List<Triple<Long, Double, Long>>, name: String? = null) {
    db.execSQL(
      "INSERT INTO recipes (id, name, method, methodOther, grind, waterTempC, notes, doseG, builtInKey, localizedNames, " +
        "updatedAtEpochMs) VALUES (?, ?, 'V60', NULL, NULL, NULL, NULL, 15.0, NULL, 0, 1000)",
      arrayOf<Any?>(id, "Receta $id"),
    )
    stages.forEachIndexed { position, (startMs, waterG, durationMs) ->
      db.execSQL(
        "INSERT INTO recipe_stages (recipeId, position, name, startMs, targetWaterG, durationMs) VALUES (?, ?, ?, ?, ?, ?)",
        arrayOf<Any?>(id, position, if (position == 0) name else null, startMs, waterG, durationMs),
      )
    }
  }

  private val withGaps = listOf(Triple(0L, 40.0, 10_000L), Triple(45_000L, 150.0, 20_000L), Triple(90_000L, 250.0, 25_000L))
  private val leadingGap = listOf(Triple(5_000L, 40.0, 10_000L), Triple(30_000L, 100.0, 10_000L))
  private val overlapping = listOf(Triple(0L, 40.0, 10_000L), Triple(5_000L, 100.0, 10_000L), Triple(30_000L, 160.0, 10_000L))
  private val tenPours = (0 until 10).map { Triple(5_000L + it * 30_000L, 20.0 * (it + 1), 10_000L) }

  private fun curveOf(stages: List<Triple<Long, Double, Long>>) = TargetCurve.fromRecipe(
    Recipe(
      name = "x",
      method = BrewMethod.V60,
      doseG = 15.0,
      stages = stages.map { (s, w, d) -> RecipeStage(null, s, w, d) },
    ),
  )

  @Test
  fun migrates3To4FillingTheGapsWithWaitsAndKeepingTheCurves() {
    helper.createDatabase(NAME, 3).use { db ->
      insertV3Recipe(db, 1, withGaps, name = "Bloom")
      insertV3Recipe(db, 2, leadingGap)
      insertV3Recipe(db, 3, overlapping)
      insertV3Recipe(db, 4, tenPours)
    }

    val db = helper.runMigrationsAndValidate(NAME, 4, true, MIGRATION_3_4)

    fun rows(id: Long) = db.query(
      "SELECT position, name, startMs, targetWaterG, durationMs, type, instruction FROM recipe_stages WHERE recipeId = $id ORDER BY position",
    ).use { c -> buildList { while (c.moveToNext()) add(listOf(c.getInt(0), c.getString(1), c.getLong(2), c.getDouble(3), c.getLong(4), c.getString(5), c.getString(6))) } }

    // Con huecos: 3 vertidos y 2 esperas, con el agua anterior y sin nombre; el nombre del primero se conserva.
    val first = rows(1)
    assertEquals(listOf("POUR", "WAIT", "POUR", "WAIT", "POUR"), first.map { it[5] })
    assertEquals(listOf(0, 1, 2, 3, 4), first.map { it[0] })
    assertEquals("Bloom", first[0][1])
    assertEquals(listOf(40.0, 40.0, 150.0, 150.0, 250.0), first.map { it[3] })
    assertEquals(listOf(0L, 10_000L, 45_000L, 65_000L, 90_000L), first.map { it[2] })
    assertEquals(listOf(10_000L, 35_000L, 20_000L, 25_000L, 25_000L), first.map { it[4] })
    assertTrue(first.all { it[6] == null })
    assertTrue(first.filter { it[5] == "WAIT" }.all { it[1] == null })

    // Con hueco inicial: una espera desde 0 con 0 g.
    val second = rows(2)
    assertEquals(listOf("WAIT", "POUR", "WAIT", "POUR"), second.map { it[5] })
    assertEquals(0L, second[0][2])
    assertEquals(5_000L, second[0][4])
    assertEquals(0.0, second[0][3])

    // Con solape: no falla y los tres pasos se conservan (más la espera del hueco final).
    val third = rows(3)
    assertEquals(listOf("POUR", "POUR", "WAIT", "POUR"), third.map { it[5] })
    assertEquals(listOf(0L, 5_000L, 15_000L, 30_000L), third.map { it[2] })

    // Diez vertidos con hueco inicial y 9 huecos: 20 pasos.
    val fourth = rows(4)
    assertEquals(20, fourth.size)
    assertEquals((0 until 20).toList(), fourth.map { it[0] })
    assertEquals(10, fourth.count { it[5] == "POUR" })
    db.close()

    // Leídas con Room, la curva de cada receta es idéntica punto a punto a la calculada con las filas v3.
    val room = Room.databaseBuilder(instrumentation.targetContext, HistoryDatabase::class.java, NAME)
      .addMigrations(migration1To2(texts), MIGRATION_2_3, MIGRATION_3_4)
      .build()
    try {
      runBlocking {
        val repository = RecipeRepository(room, texts)
        for ((id, v3) in listOf(1L to withGaps, 2L to leadingGap, 3L to overlapping, 4L to tenPours)) {
          val migrated = TargetCurve.fromRecipe(repository.get(id)!!)
          val before = curveOf(v3)
          assertEquals("receta $id", before.points, migrated.points)
          assertEquals("receta $id", before.endMs, migrated.endMs)
          assertEquals("receta $id", before.finalWaterG, migrated.finalWaterG, 0.0)
        }
      }
    } finally {
      room.close()
    }
  }

  @Test
  fun migrates3To4LeavesTheBuiltInsSeededByEarlierVersionsWithTheirNewSteps() {
    helper.createDatabase(NAME, 1).close()
    helper.runMigrationsAndValidate(NAME, 4, true, migration1To2(texts), MIGRATION_2_3, MIGRATION_3_4).use { db ->
      db.query("SELECT builtInKey, COUNT(*) FROM recipe_stages JOIN recipes ON recipes.id = recipeId GROUP BY builtInKey").use { c ->
        val steps = buildMap { while (c.moveToNext()) put(c.getString(0), c.getInt(1)) }
        assertEquals(mapOf(BuiltInRecipes.V60_CLASSIC to 5, BuiltInRecipes.FOUR_SIX to 9), steps)
      }
    }
  }

  @Test
  fun migrates3To4OfAnEmptyRecipeTableKeepsTheBrews() {
    helper.createDatabase(NAME, 3).use { db -> insertV1Brew(db, 1, "Etiopía", null, 1_700_000_000_000) }
    helper.runMigrationsAndValidate(NAME, 4, true, MIGRATION_3_4).use { db ->
      db.query("SELECT COUNT(*) FROM brews").use {
        it.moveToFirst()
        assertEquals(1, it.getInt(0))
      }
    }
  }

  @Test
  fun migrates1To2To3To4InAChain() {
    helper.createDatabase(NAME, 1).use { db -> insertV1Brew(db, 1, "Etiopía", "Etiopía Guji", 1_700_000_000_000) }
    helper.runMigrationsAndValidate(NAME, 4, true, migration1To2(texts), MIGRATION_2_3, MIGRATION_3_4).use { db ->
      db.query("SELECT coffee, coffeeId, rating, fitScore FROM brews").use { c ->
        c.moveToFirst()
        assertEquals("Etiopía Guji", c.getString(0))
        assertTrue(c.isNull(1))
        assertTrue(c.isNull(2))
        assertTrue(c.isNull(3))
      }
      db.query("SELECT COUNT(*) FROM recipes").use {
        it.moveToFirst()
        assertEquals(2, it.getInt(0))
      }
      db.query("SELECT COUNT(*) FROM recipe_stages WHERE type = 'WAIT'").use {
        it.moveToFirst()
        assertEquals(6, it.getInt(0)) // 2 de la V60 y 4 de la 4:6
      }
    }
  }

  private fun stageCount(db: SupportSQLiteDatabase, recipeId: Long): Int =
    db.query("SELECT COUNT(*) FROM recipe_stages WHERE recipeId = $recipeId").use {
      it.moveToFirst()
      it.getInt(0)
    }

  private companion object {
    const val NAME = "migration-test.db"
  }
}
