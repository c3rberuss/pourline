package dev.josueamaya.pourline.grinder

import android.content.Context
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

  // --- Pares de calibración (enmienda 1) ---

  private suspend fun twoGrinders(): Pair<Long, Long> = repository.save(TestGrinders.uno) to repository.save(TestGrinders.dos)

  @Test
  fun saveCalibrationReturnsSavedAndTheFlowEmitsIt() = runTest {
    val (uno, dos) = twoGrinders()
    assertTrue(repository.calibrations(uno).first().isEmpty())
    val saved = repository.saveCalibration(uno, 20.0, dos, 31.0) as CalibrationSaveResult.Saved
    assertEquals(listOf(GrindCalibration(saved.id, uno, 20.0, dos, 31.0)), repository.calibrations(uno).first())
    assertEquals(repository.calibrations(uno).first(), repository.calibrations(dos).first())
  }

  @Test
  fun saveCalibrationSnapsTheValues() = runTest {
    val (uno, dos) = twoGrinders()
    // Uno tiene paso 1 y Dos, 0,5: 20,4 → 20 y 30,3 → 30,5.
    repository.saveCalibration(uno, 20.4, dos, 30.3)
    val pair = repository.calibrations(uno).first().single()
    assertEquals(20.0, pair.valueA, 0.0)
    assertEquals(30.5, pair.valueB, 0.0)
  }

  @Test
  fun aRepeatedValueReturnsUpdated() = runTest {
    val (uno, dos) = twoGrinders()
    val id = (repository.saveCalibration(uno, 20.0, dos, 30.0) as CalibrationSaveResult.Saved).id
    assertEquals(CalibrationSaveResult.Updated(id), repository.saveCalibration(uno, 20.0, dos, 32.0))
    assertEquals(32.0, repository.calibrations(uno).first().single().valueB, 0.0)
  }

  @Test
  fun aContradictionReturnsConflictAndWritesNothing() = runTest {
    val (uno, dos) = twoGrinders()
    val first = (repository.saveCalibration(uno, 20.0, dos, 30.0) as CalibrationSaveResult.Saved).id
    val result = repository.saveCalibration(uno, 25.0, dos, 25.0)
    assertEquals(CalibrationSaveResult.Conflict(listOf(GrindCalibration(first, uno, 20.0, dos, 30.0))), result)
    assertEquals(listOf(first), repository.calibrations(uno).first().map { it.id })
  }

  @Test
  fun replaceCalibrationDeletesTheConflictsAndSaves() = runTest {
    val (uno, dos) = twoGrinders()
    repository.saveCalibration(uno, 20.0, dos, 30.0)
    val result = repository.replaceCalibration(uno, 25.0, dos, 25.0) as CalibrationSaveResult.Saved
    assertEquals(listOf(GrindCalibration(result.id, uno, 25.0, dos, 25.0)), repository.calibrations(uno).first())
  }

  @Test
  fun aGrinderThatIsNotOwnedReturnsInvalid() = runTest {
    val uno = repository.save(TestGrinders.uno)
    // Un predefinido sin "Lo tengo": existe, pero no es mío.
    val predefined = db.grinderDao().save(TestGrinders.dos.copy(catalogKey = "acme-dos", owned = false, sourceUrl = "u", sourceName = "n").toRows(1_000L))
    assertEquals(
      CalibrationSaveResult.Invalid(listOf(CalibrationError.NotOwned(predefined))),
      repository.saveCalibration(uno, 20.0, predefined, 30.0),
    )
    assertTrue(repository.calibrations(uno).first().isEmpty())
    // Y un molino que no existe.
    assertEquals(CalibrationSaveResult.Invalid(listOf(CalibrationError.NotOwned(999L))), repository.saveCalibration(uno, 20.0, 999L, 30.0))
  }

  @Test
  fun calibrationPointsAreOrientedFromTheFirstGrinder() = runTest {
    val (uno, dos) = twoGrinders()
    repository.saveCalibration(uno, 10.0, dos, 14.0)
    repository.saveCalibration(uno, 30.0, dos, 50.0)
    val fromUno = repository.calibrationPoints(uno, dos).first().sortedBy { it.fromValue }
    assertEquals(listOf(CalibrationPoint(10.0, 14.0), CalibrationPoint(30.0, 50.0)), fromUno)
    val fromDos = repository.calibrationPoints(dos, uno).first().sortedBy { it.fromValue }
    assertEquals(listOf(CalibrationPoint(14.0, 10.0), CalibrationPoint(50.0, 30.0)), fromDos)
    assertTrue(repository.calibrationPoints(uno, uno).first().isEmpty())
  }

  @Test
  fun deletingAGrinderDeletesItsPairs() = runTest {
    val (uno, dos) = twoGrinders()
    val tres = repository.save(TestGrinders.tres)
    repository.saveCalibration(uno, 20.0, dos, 30.0)
    repository.saveCalibration(dos, 10.0, tres, 40.0)
    assertTrue(repository.delete(dos))
    assertTrue(repository.calibrations(uno).first().isEmpty())
    assertTrue(repository.calibrations(tres).first().isEmpty())
  }

  @Test
  fun duplicatingAPredefinedDoesNotCopyItsPairs() = runTest {
    val a = db.grinderDao().save(TestGrinders.uno.copy(catalogKey = "acme-uno", sourceUrl = "u", sourceName = "n").toRows(1_000L))
    val b = db.grinderDao().save(TestGrinders.dos.copy(catalogKey = "acme-dos", sourceUrl = "u", sourceName = "n").toRows(1_000L))
    repository.saveCalibration(a, 20.0, b, 30.0)
    val copy = repository.save(repository.duplicateDraft(a)!!)
    assertTrue(repository.calibrations(copy).first().isEmpty())
    assertEquals(1, repository.calibrations(a).first().size)
  }

  @Test
  fun deleteCalibrationRemovesOnlyThatPair() = runTest {
    val (uno, dos) = twoGrinders()
    val first = (repository.saveCalibration(uno, 10.0, dos, 14.0) as CalibrationSaveResult.Saved).id
    val second = (repository.saveCalibration(uno, 30.0, dos, 50.0) as CalibrationSaveResult.Saved).id
    repository.deleteCalibration(first)
    assertEquals(listOf(second), repository.calibrations(uno).first().map { it.id })
  }

  private companion object {
    const val PREFS = "grinder_repository_test"
  }
}
