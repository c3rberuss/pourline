package dev.dotscale.history

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.dotscale.recipe.BuiltInRecipes
import dev.dotscale.recipe.FakeRecipeTexts
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

/** Migraciones 1 → 2 y 2 → 3 sobre bases creadas con los esquemas exportados. */
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
      .addMigrations(migration1To2(texts), MIGRATION_2_3)
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
  fun aFreshInstallIsCreatedAtVersion3WithTheBuiltIns() = runBlocking {
    val context = instrumentation.targetContext
    context.deleteDatabase(NAME)
    val fresh = HistoryDatabase.create(context, NAME, texts)
    try {
      assertEquals(2, fresh.recipeDao().observeAll().first().size)
      assertEquals(3, fresh.openHelper.readableDatabase.version)
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
      .addMigrations(migration1To2(texts), MIGRATION_2_3)
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

  private fun stageCount(db: SupportSQLiteDatabase, recipeId: Long): Int =
    db.query("SELECT COUNT(*) FROM recipe_stages WHERE recipeId = $recipeId").use {
      it.moveToFirst()
      it.getInt(0)
    }

  private companion object {
    const val NAME = "migration-test.db"
  }
}
