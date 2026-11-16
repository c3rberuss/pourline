package dev.dotscale.grinder

import android.content.Context
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
import dev.dotscale.settings.GrinderSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GrinderRepositoryTest {
  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private val texts = ResourcesGrindTexts(context)
  private lateinit var db: HistoryDatabase
  private lateinit var settings: GrinderSettings
  private lateinit var repository: GrinderRepository

  @Before
  fun setUp() {
    context.deleteSharedPreferences(PREFS)
    db = HistoryDatabase.inMemory(context, FakeRecipeTexts())
    settings = GrinderSettings(context, PREFS)
    repository = GrinderRepository(db, settings, texts)
  }

  @After
  fun tearDown() {
    db.close()
    context.deleteSharedPreferences(PREFS)
  }

  @Test
  fun saveInsertsAnOwnGrinderAndEditsIt() = runTest {
    val id = repository.save(TestGrinders.uno.copy(owned = false))
    val saved = repository.get(id)!!
    assertTrue(saved.owned)
    assertNull(saved.catalogKey)
    repository.save(saved.copy(model = "Uno Pro"))
    assertEquals(listOf("Uno Pro"), repository.owned().first().map { it.model })
  }

  @Test
  fun saveRejectsAnInvalidGrinderAndAPredefinedOne() = runTest {
    val error = assertThrows(InvalidGrinderException::class.java) {
      kotlinx.coroutines.runBlocking { repository.save(TestGrinders.uno.copy(brand = "")) }
    }
    assertTrue(GrinderError.BlankBrand in error.errors)
    assertThrows(IllegalArgumentException::class.java) {
      kotlinx.coroutines.runBlocking { repository.save(TestGrinders.uno.copy(catalogKey = "acme-uno")) }
    }
    assertTrue(repository.all().first().isEmpty())
  }

  @Test
  fun duplicateDraftIsAnUnsavedCopyAccordingToD22() = runTest {
    val catalogId = db.addGrinder(TestGrinders.dos.copy(catalogKey = "acme-dos", owned = false, sourceUrl = "https://example.test/dos"))
    val draft = repository.duplicateDraft(catalogId)!!
    assertEquals(0L, draft.id)
    assertNull(draft.catalogKey)
    assertTrue(draft.owned)
    assertFalse(draft.retired)
    assertNull(draft.sourceUrl)
    assertEquals(texts.copyName("Dos"), draft.model)
    assertEquals("acme-dos", draft.duplicatedFromKey)
    assertEquals(TestGrinders.dos.units, draft.units)
    assertEquals(TestGrinders.dos.ranges, draft.ranges)
    // No se ha guardado nada.
    assertEquals(listOf(catalogId), repository.all().first().map { it.id })

    // Un duplicado de un duplicado apunta al predefinido original.
    val copyId = repository.save(draft)
    assertEquals("acme-dos", repository.duplicateDraft(copyId)!!.duplicatedFromKey)
    assertNull(repository.duplicateDraft(999L))
  }

  @Test
  fun removingFromMyGrindersOrDeletingClearsTheDefaultOnlyIfItWasThatOne() = runTest {
    val predefined = db.addGrinder(TestGrinders.dos.copy(catalogKey = "acme-dos", owned = true))
    val own = repository.save(TestGrinders.uno)
    val other = repository.save(TestGrinders.inverso)

    settings.setDefaultGrinderId(predefined)
    repository.setOwned(predefined, false)
    assertNull(settings.defaultGrinderId.value)
    assertTrue(repository.owned().first().none { it.id == predefined })

    settings.setDefaultGrinderId(other)
    repository.setOwned(predefined, true)
    assertEquals(other, settings.defaultGrinderId.value)

    settings.setDefaultGrinderId(own)
    assertTrue(repository.delete(own))
    assertNull(settings.defaultGrinderId.value)

    settings.setDefaultGrinderId(other)
    val third = repository.save(TestGrinders.dos.copy(model = "Dos propio"))
    assertTrue(repository.delete(third))
    assertEquals(other, settings.defaultGrinderId.value)
  }

  @Test
  fun anOwnGrinderCannotBeRemovedFromMyGrindersAndAPredefinedOneCannotBeDeleted() = runTest {
    val own = repository.save(TestGrinders.uno)
    repository.setOwned(own, false)
    assertNotNull(repository.owned().first().singleOrNull { it.id == own })

    val predefined = db.addGrinder(TestGrinders.dos.copy(catalogKey = "acme-dos", owned = false))
    assertFalse(repository.delete(predefined))
    assertNotNull(repository.get(predefined))
    assertFalse(repository.delete(999L))
  }

  @Test
  fun deletingRegeneratesTheLabelOfTheRecipesAndBrewsThatUsedTheGrinder() = runTest {
    val id = repository.save(TestGrinders.uno)
    val recipes = RecipeRepository(db, FakeRecipeTexts())
    val brews = BrewRepository(db)
    val recipeId = recipes.save(
      Recipe(
        name = "R",
        method = BrewMethod.V60,
        grind = GrindSetting(id, 21.0, "texto antiguo"),
        doseG = 15.0,
        stages = listOf(RecipeStage("Bloom", 0, 40.0, 10_000)),
      ),
    )
    val brewId = brews.save(TestBrews.finished(), TestBrews.context(details = BrewDetails(grind = GrindSetting(id, null, "otro texto"))))

    assertTrue(repository.delete(id))

    val expected = GrindFormat.label(TestGrinders.uno, 21.0, texts)
    assertEquals(GrindSetting(null, 21.0, expected), recipes.get(recipeId)!!.grind)
    // Sin ajuste no hay nada que regenerar: se conserva la copia que tenía.
    assertEquals("otro texto", brews.get(brewId)!!.entity.grindLabel)
    assertNull(brews.get(brewId)!!.entity.grinderId)
  }

  @Test
  fun theListsAreOrderedByBrandAndModelAndRecipeCountFollowsTheRecipes() = runTest {
    val z = repository.save(TestGrinders.uno.copy(brand = "Zeta", model = "A"))
    repository.save(TestGrinders.uno.copy(brand = "Acme", model = "Beta"))
    repository.save(TestGrinders.uno.copy(brand = "Acme", model = "Alfa"))
    assertEquals(listOf("Acme Alfa", "Acme Beta", "Zeta A"), repository.owned().first().map { "${it.brand} ${it.model}" })
    assertEquals(0, repository.recipeCount(z).first())
    assertNull(repository.grinder(null).first())
    assertEquals("A", repository.grinder(z).first()!!.model)
  }

  private companion object {
    const val PREFS = "grinder_repository_test"
  }
}
