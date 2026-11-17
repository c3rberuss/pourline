package dev.dotscale.grinder

import androidx.test.platform.app.InstrumentationRegistry
import dev.dotscale.history.BrewDetails
import dev.dotscale.history.BrewMethod
import dev.dotscale.history.BrewRepository
import dev.dotscale.history.HistoryDatabase
import dev.dotscale.history.TestBrews
import dev.dotscale.recipe.FakeRecipeTexts
import dev.dotscale.recipe.Recipe
import dev.dotscale.recipe.RecipeRepository
import dev.dotscale.recipe.RecipeStage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Room en memoria: el `history.db` real no se toca. */
class GrinderDaoTest {
  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private val texts = FakeRecipeTexts()
  private lateinit var db: HistoryDatabase
  private lateinit var recipes: RecipeRepository
  private lateinit var brews: BrewRepository
  private val dao get() = db.grinderDao()

  @Before
  fun setUp() {
    db = HistoryDatabase.inMemory(context, texts)
    recipes = RecipeRepository(db, texts)
    brews = BrewRepository(db)
  }

  @After
  fun tearDown() = db.close()

  private fun recipe(grind: GrindSetting?) = Recipe(
    name = "R",
    method = BrewMethod.V60,
    grind = grind,
    doseG = 15.0,
    stages = listOf(RecipeStage("Bloom", 0, 40.0, 10_000)),
  )

  @Test
  fun anAddedGrinderIsReadBackWithItsUnitsRangesAndAnchors() = runTest {
    val id = db.addGrinder(TestGrinders.tres)
    val read = dao.get(id)!!.toGrinder()
    assertEquals(TestGrinders.tres.copy(id = id), read)
    assertEquals(listOf(GrindUnit.ROTATIONS, GrindUnit.NUMBERS, GrindUnit.CLICKS), read.units.map { it.unit })
    assertEquals(listOf(null, 4, 10), read.units.map { it.perParent })
  }

  @Test
  fun editingReplacesTheUnitsRangesAndAnchorsAndKeepsTheId() = runTest {
    val id = db.addGrinder(TestGrinders.tres)
    val edited = TestGrinders.uno.copy(
      id = id,
      model = "Tres reducido",
      ranges = mapOf(BrewMethod.CHEMEX to GrindRange(10.0, 20.0)),
      anchors = mapOf(GrindLevel.MEDIUM to 20.0),
    )
    assertEquals(id, dao.save(edited.toRows(2_000L)))
    val read = dao.get(id)!!.toGrinder()
    assertEquals(edited, read)
    assertEquals(1, read.units.size)
    assertEquals(setOf(BrewMethod.CHEMEX), read.ranges.keys)
    assertEquals(1, dao.observeAll().first().size)
  }

  @Test
  fun myGrindersAndTheCatalogAreSeparated() = runTest {
    val own = db.addGrinder(TestGrinders.uno)
    val listed = db.addGrinder(TestGrinders.dos.copy(catalogKey = "acme-dos", owned = false))
    val retired = db.addGrinder(TestGrinders.tres.copy(catalogKey = "acme-tres", owned = false, retired = true))

    assertEquals(listOf(own), dao.observeOwned().first().map { it.grinder.id })
    assertEquals(listOf(listed), dao.observeCatalog().first().map { it.grinder.id })
    assertEquals(setOf(own, listed, retired), dao.observeAll().first().map { it.grinder.id }.toSet())
    assertEquals(setOf("acme-dos", "acme-tres"), dao.catalogKeys().toSet())
    assertEquals(listed, dao.byCatalogKey("acme-dos")!!.id)
    assertNull(dao.byCatalogKey("no-existe"))
  }

  @Test
  fun setOwnedMovesAPredefinedGrinderInAndOutOfMyGrinders() = runTest {
    val id = db.addGrinder(TestGrinders.dos.copy(catalogKey = "acme-dos", owned = false))
    assertTrue(dao.observeOwned().first().isEmpty())
    dao.setOwned(id, true, 5_000L)
    assertEquals(listOf(id), dao.observeOwned().first().map { it.grinder.id })
    assertEquals(5_000L, dao.get(id)!!.grinder.updatedAtEpochMs)
    dao.setOwned(id, false, 6_000L)
    assertTrue(dao.observeOwned().first().isEmpty())
  }

  @Test
  fun recipeCountAndIsUsedFollowRecipesAndBrews() = runTest {
    val id = db.addGrinder(TestGrinders.uno)
    assertEquals(0, dao.recipeCount(id).first())
    assertFalse(dao.isUsed(id))

    recipes.save(recipe(GrindSetting(id, 21.0, "Uno · 21")))
    recipes.save(recipe(GrindSetting(id, null, "Uno")))
    recipes.save(recipe(null))
    assertEquals(2, dao.recipeCount(id).first())
    assertEquals(listOf(21.0, null), dao.recipesUsing(id).map { it.value }.sortedBy { it == null })
    assertTrue(dao.isUsed(id))

    val other = db.addGrinder(TestGrinders.dos)
    assertFalse(dao.isUsed(other))
    brews.save(TestBrews.finished(), TestBrews.context(details = BrewDetails(grind = GrindSetting(other, 10.0, "Dos · 10"))))
    assertEquals(0, dao.recipeCount(other).first())
    assertTrue(dao.isUsed(other))
    assertEquals(listOf(10.0), dao.brewsUsing(other).map { it.value })
  }

  @Test
  fun deletingLeavesNullGrinderIdKeepsTheValueAndRegeneratesTheLabelWithTheCurrentName() = runTest {
    val id = db.addGrinder(TestGrinders.uno)
    val recipeId = recipes.save(recipe(GrindSetting(id, 21.0, "Uno · 21 clics")))
    val brewId = brews.save(TestBrews.finished(), TestBrews.context(details = BrewDetails(grind = GrindSetting(id, 12.0, "Uno · 12 clics"))))
    // Renombrado antes de borrar: la copia del texto se regenera con el nombre actual.
    dao.save(TestGrinders.uno.copy(id = id, model = "Uno Pro").toRows(2_000L))

    dao.deleteRelabeling(id, mapOf(recipeId to "Uno Pro · 21 clics"), mapOf(brewId to "Uno Pro · 12 clics"))

    assertNull(dao.get(id))
    assertEquals(GrindSetting(null, 21.0, "Uno Pro · 21 clics"), recipes.get(recipeId)!!.grind)
    val brew = brews.get(brewId)!!.entity
    assertNull(brew.grinderId)
    assertEquals(12.0, brew.grindValue!!, 0.0)
    assertEquals("Uno Pro · 12 clics", brew.grindLabel)
  }
}
