package dev.josueamaya.pourline.grinder

import androidx.test.platform.app.InstrumentationRegistry
import dev.josueamaya.pourline.history.BrewDetails
import dev.josueamaya.pourline.history.BrewMethod
import dev.josueamaya.pourline.history.BrewRepository
import dev.josueamaya.pourline.history.HistoryDatabase
import dev.josueamaya.pourline.history.TestBrews
import dev.josueamaya.pourline.recipe.FakeRecipeTexts
import dev.josueamaya.pourline.recipe.Recipe
import dev.josueamaya.pourline.recipe.RecipeRepository
import dev.josueamaya.pourline.recipe.RecipeStage
import dev.josueamaya.pourline.settings.GrinderSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GrinderCatalogSeederTest {
  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private lateinit var db: HistoryDatabase
  private lateinit var settings: GrinderSettings
  private var json: String? = TestGrinderCatalog.JSON
  private lateinit var seeder: GrinderCatalogSeeder
  private val dao get() = db.grinderDao()

  @Before
  fun setUp() {
    context.deleteSharedPreferences(PREFS)
    db = HistoryDatabase.inMemory(context, FakeRecipeTexts())
    settings = GrinderSettings(context, PREFS)
    seeder = GrinderCatalogSeeder(dao, { json }, settings, settings, clock = { 9_000L })
  }

  @After
  fun tearDown() {
    db.close()
    context.deleteSharedPreferences(PREFS)
  }

  private suspend fun byKey(key: String) = dao.byCatalogKey(key)!!

  private suspend fun recipeUsing(id: Long) = RecipeRepository(db, FakeRecipeTexts()).save(
    Recipe(
      name = "R",
      method = BrewMethod.V60,
      grind = GrindSetting(id, 20.0, "x"),
      doseG = 15.0,
      stages = listOf(RecipeStage("Bloom", 0, 40.0, 10_000)),
    ),
  )

  @Test
  fun theFirstRunSeedsTheCatalogAndStoresTheVersion() = runTest {
    assertEquals(SyncResult.Synced(1, added = 4, updated = 0, retired = 0, deleted = 0), seeder.sync())
    assertEquals(1, settings.catalogVersion)
    val all = dao.observeAll().first().map { it.toGrinder() }
    assertEquals(4, all.size)
    assertTrue(all.all { it.catalogKey != null && !it.owned && !it.retired })
    assertEquals(listOf(GrindUnit.ROTATIONS, GrindUnit.NUMBERS, GrindUnit.CLICKS), all.single { it.model == "Tres" }.units.map { it.unit })
    assertEquals(GrindDirection.HIGHER_IS_FINER, all.single { it.model == "Inverso" }.direction)
  }

  @Test
  fun theSameVersionDoesNotWrite() = runTest {
    seeder.sync()
    val before = byKey("acme-uno")
    // Un archivo con la misma versión pero otro contenido no se aplica.
    json = TestGrinderCatalog.catalog(1, listOf(TestGrinderCatalog.uno(model = "Cambiado")))
    assertEquals(SyncResult.UpToDate, seeder.sync())
    assertEquals(before, byKey("acme-uno"))
    assertEquals(4, dao.catalogKeys().size)
  }

  @Test
  fun aNewerVersionUpdatesByKeyKeepingTheIdAndOwnedAndReplacingTheChildren() = runTest {
    seeder.sync()
    val id = byKey("acme-uno").id
    dao.setOwned(id, true, 1L)
    val own = dao.save(TestGrinders.uno.copy(model = "Mi propio").toRows(1L))
    val copy = dao.save(TestGrinders.uno.copy(model = "Uno (copia)", duplicatedFromKey = "acme-uno").toRows(1L))

    json = TestGrinderCatalog.catalog(2, listOf(TestGrinderCatalog.uno(model = "Uno II", max = 50.0), TestGrinderCatalog.dos(), TestGrinderCatalog.tres(), TestGrinderCatalog.inverso()))
    assertEquals(SyncResult.Synced(2, added = 0, updated = 4, retired = 0, deleted = 0), seeder.sync())

    val updated = dao.get(id)!!.toGrinder()
    assertEquals("Uno II", updated.model)
    assertEquals(50.0, updated.scaleMax, 0.0)
    assertTrue(updated.owned)
    assertEquals(9_000L, dao.get(id)!!.grinder.updatedAtEpochMs)
    assertEquals(2, settings.catalogVersion)
    // Los propios y los duplicados no se tocan.
    assertEquals("Mi propio", dao.get(own)!!.grinder.model)
    assertEquals("Uno (copia)", dao.get(copy)!!.grinder.model)
    assertEquals(4, dao.catalogKeys().size)
  }

  @Test
  fun aKeyThatDisappearsIsRetiredWhenInUseAndDeletedOtherwise() = runTest {
    seeder.sync()
    val withRecipe = byKey("acme-uno").id
    val owned = byKey("acme-dos").id
    val isDefault = byKey("acme-tres").id
    val unused = byKey("acme-inverso").id
    recipeUsing(withRecipe)
    dao.setOwned(owned, true, 1L)
    settings.setDefaultGrinderId(isDefault)

    json = TestGrinderCatalog.catalog(2, emptyList())
    assertEquals(SyncResult.Synced(2, added = 0, updated = 0, retired = 3, deleted = 1), seeder.sync())

    assertTrue(dao.get(withRecipe)!!.grinder.retired)
    assertTrue(dao.get(owned)!!.grinder.retired)
    assertTrue(dao.get(isDefault)!!.grinder.retired)
    assertNull(dao.get(unused))
  }

  @Test
  fun aBrewAlsoKeepsAGrinderAndAReturningKeyIsNoLongerRetired() = runTest {
    seeder.sync()
    val id = byKey("acme-inverso").id
    BrewRepository(db).save(TestBrews.finished(), TestBrews.context(details = BrewDetails(grind = GrindSetting(id, 20.0, "x"))))

    json = TestGrinderCatalog.catalog(2, listOf(TestGrinderCatalog.uno()))
    seeder.sync()
    assertTrue(dao.get(id)!!.grinder.retired)
    assertEquals(setOf("acme-uno", "acme-inverso"), dao.catalogKeys().toSet())

    json = TestGrinderCatalog.catalog(3, listOf(TestGrinderCatalog.uno(), TestGrinderCatalog.inverso()))
    assertEquals(SyncResult.Synced(3, added = 0, updated = 2, retired = 0, deleted = 0), seeder.sync())
    assertFalse(dao.get(id)!!.grinder.retired)
  }

  @Test
  fun aBrokenFileAnInvalidGrinderOrAMissingFileFailWithoutChangingAnything() = runTest {
    seeder.sync()
    val before = dao.observeAll().first().map { it.toGrinder() }.sortedBy { it.id }

    val invalid = TestGrinderCatalog.entry("acme-malo", "Acme", "Malo", """[{"unit":"CLICKS"}]""", min = 10.0, max = 5.0, step = 1.0)
    for (bad in listOf("{ esto no es json", TestGrinderCatalog.catalog(2, listOf(TestGrinderCatalog.uno(model = "Nuevo"), invalid)), null)) {
      json = bad
      assertTrue(seeder.sync() is SyncResult.Failed)
      assertEquals(1, settings.catalogVersion)
      assertEquals(before, dao.observeAll().first().map { it.toGrinder() }.sortedBy { it.id })
    }
  }

  private companion object {
    const val PREFS = "grinder_seeder_test"
  }
}
