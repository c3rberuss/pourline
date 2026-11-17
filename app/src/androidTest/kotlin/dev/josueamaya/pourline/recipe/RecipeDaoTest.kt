package dev.josueamaya.pourline.recipe

import androidx.test.platform.app.InstrumentationRegistry
import dev.josueamaya.pourline.grinder.GrindSetting
import dev.josueamaya.pourline.grinder.TestGrinders
import dev.josueamaya.pourline.grinder.addGrinder
import dev.josueamaya.pourline.history.BrewMethod
import dev.josueamaya.pourline.history.HistoryDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/** Room en memoria: el `history.db` real no se toca. */
class RecipeDaoTest {
  private lateinit var db: HistoryDatabase
  private lateinit var repository: RecipeRepository
  private val texts = FakeRecipeTexts()
  private var now = 1_000L

  @Before
  fun setUp() {
    db = HistoryDatabase.inMemory(InstrumentationRegistry.getInstrumentation().targetContext, texts)
    repository = RecipeRepository(db, texts) { now }
  }

  @After
  fun tearDown() = db.close()

  private fun mine(name: String = "Mi receta", dose: Double = 16.0) = Recipe(
    name = name,
    method = BrewMethod.V60,
    grind = GrindSetting(null, 21.0, "Acme · 21 clics"),
    waterTempC = 93.0,
    notes = "Vierte despacio",
    doseG = dose,
    stages = listOf(
      RecipeStage("Bloom", 0, 40.0, 10_000),
      RecipeStage(null, 45_000, 160.0, 20_000),
    ),
  )

  private suspend fun builtIn(key: String) = repository.list().first().single { it.name == texts.builtInName(key) }

  @Test
  fun aNewDatabaseHasTheTwoBuiltInRecipes() = runTest {
    val list = repository.list().first()
    assertEquals(2, list.size)
    assertTrue(list.all { it.builtIn })
    val v60 = repository.get(builtIn(BuiltInRecipes.V60_CLASSIC).id)!!
    assertEquals("V60 clásica", v60.name)
    assertEquals(BuiltInRecipes.V60_CLASSIC, v60.builtInKey)
    assertTrue(v60.localizedNames)
    assertEquals(15.0, v60.doseG, 0.0)
    assertEquals(listOf("Bloom", null, "2.º vertido", null, "3.er vertido"), v60.stages.map { it.name })
    assertEquals(
      listOf(StageType.POUR, StageType.WAIT, StageType.POUR, StageType.WAIT, StageType.POUR),
      v60.stages.map { it.type },
    )
    assertEquals(listOf("i1", "i2", "i3", "i4", "i5"), v60.stages.map { it.instruction })
    assertEquals(BuiltInRecipes.recipe(BuiltInRecipes.V60_CLASSIC, texts).stages, v60.stages)
    val fourSix = repository.get(builtIn(BuiltInRecipes.FOUR_SIX).id)!!
    assertEquals(20.0, fourSix.doseG, 0.0)
    assertEquals(9, fourSix.stages.size)
    assertEquals(300.0, fourSix.totalWaterG, 0.0)
  }

  @Test
  fun typeAndInstructionAreSavedAndRead() = runTest {
    val recipe = mine().copy(
      stages = listOf(
        RecipeStage("Bloom", 0, 40.0, 10_000, StageType.POUR, "Despacio"),
        RecipeStage(null, 10_000, 40.0, 5_000, StageType.SWIRL, "Gira el dripper"),
        RecipeStage(null, 15_000, 40.0, 30_000, StageType.WAIT),
        RecipeStage("Final", 45_000, 160.0, 20_000, StageType.POUR, null),
      ),
    )
    val id = repository.save(recipe)
    assertEquals(recipe.copy(id = id), repository.get(id))
    val item = repository.list().first().single { it.id == id }
    assertEquals(2, item.stageCount) // solo los vertidos
    assertEquals(160.0, item.totalWaterG, 0.0)
  }

  @Test
  fun theWaterOfASwirlAndAWaitIsDerivedFromThePreviousPourWhenSaving() = runTest {
    val id = repository.save(
      mine().copy(
        stages = listOf(
          RecipeStage(null, 0, 40.0, 10_000),
          RecipeStage(null, 10_000, 999.0, 5_000, StageType.SWIRL),
          RecipeStage(null, 15_000, 0.0, 20_000, StageType.WAIT),
          RecipeStage(null, 35_000, 100.0, 10_000),
        ),
      ),
    )
    assertEquals(listOf(40.0, 40.0, 40.0, 100.0), repository.get(id)!!.stages.map { it.targetWaterG })
  }

  @Test
  fun saveWithStagesAndRead() = runTest {
    now = 5_000
    val id = repository.save(mine())
    assertTrue(id > 0)
    val read = repository.get(id)!!
    assertEquals(mine().copy(id = id), read)
    val item = repository.list().first().single { it.id == id }
    assertEquals("Mi receta", item.name)
    assertEquals(160.0, item.totalWaterG, 0.0)
    assertEquals(2, item.stageCount)
    assertFalse(item.builtIn)
    assertEquals(16.0, item.doseG, 0.0)
  }

  @Test
  fun editingReplacesTheStagesAndUpdatesTheTimestamp() = runTest {
    now = 1_000
    val id = repository.save(mine())
    now = 9_000
    val edited = mine(name = "Editada").copy(
      id = id,
      stages = listOf(RecipeStage("Único", 0, 200.0, 30_000)),
    )
    assertEquals(id, repository.save(edited))
    assertEquals(edited, repository.get(id))
    val updatedAt = db.openHelper.readableDatabase.query("SELECT updatedAtEpochMs FROM recipes WHERE id = $id").use {
      it.moveToFirst()
      it.getLong(0)
    }
    assertEquals(9_000L, updatedAt)
    val stageRows = db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM recipe_stages WHERE recipeId = $id").use {
      it.moveToFirst()
      it.getInt(0)
    }
    assertEquals(1, stageRows)
  }

  @Test
  fun savingDoesNotMakeObserveEmitNull() = runTest {
    val id = repository.save(mine())
    val emissions = CopyOnWriteArrayList<Recipe?>()
    val job = launch(Dispatchers.Default) { repository.observe(id).collect { emissions += it } }
    repeat(5) { i -> repository.save(mine(name = "Versión $i").copy(id = id)) }
    // Room emite desde sus propios hilos: se espera en tiempo real (en `runTest` el `delay` es virtual).
    withContext(Dispatchers.Default) {
      withTimeout(5_000) { while (emissions.lastOrNull()?.name != "Versión 4") delay(20) }
    }
    job.cancel()
    assertTrue("emissions: ${emissions.map { it?.name }}", emissions.isNotEmpty())
    assertTrue("observe emitted null while saving: ${emissions.map { it?.name }}", emissions.none { it == null })
    assertEquals("Versión 4", emissions.last()!!.name)
  }

  @Test
  fun duplicateCopiesWithANewNameAndNoBuiltInKey() = runTest {
    val original = builtIn(BuiltInRecipes.V60_CLASSIC)
    val copyId = repository.duplicate(original.id)!!
    assertNotEquals(original.id, copyId)
    val copy = repository.get(copyId)!!
    assertEquals("V60 clásica (copia)", copy.name)
    assertNull(copy.builtInKey)
    assertFalse(copy.localizedNames)
    assertEquals(repository.get(original.id)!!.stages, copy.stages)
    assertEquals(repository.get(original.id)!!.doseG, copy.doseG, 0.0)
    // La original sigue igual.
    assertEquals(BuiltInRecipes.V60_CLASSIC, repository.get(original.id)!!.builtInKey)
    assertNull(repository.duplicate(9_999))
  }

  @Test
  fun theGrindSettingIsSavedAndReadBackWithALiveGrinder() = runTest {
    val grinderId = db.addGrinder(TestGrinders.uno)
    val setting = GrindSetting(grinderId, 21.0, "Acme Uno · 21 clics")
    val id = repository.save(mine().copy(grind = setting))
    assertEquals(setting, repository.get(id)!!.grind)
  }

  @Test
  fun savingAndUndoingWithAGrinderThatNoLongerExistsLeavesGrinderIdNullAndKeepsValueAndLabel() = runTest {
    val setting = GrindSetting(999L, 21.0, "Acme Uno · 21 clics")
    val id = repository.save(mine().copy(grind = setting))
    assertEquals(GrindSetting(null, 21.0, "Acme Uno · 21 clics"), repository.get(id)!!.grind)

    // "Deshacer" tras borrar el molino mientras la receta estaba borrada: no rompe la clave foránea.
    val grinderId = db.addGrinder(TestGrinders.uno)
    repository.save(repository.get(id)!!.copy(grind = GrindSetting(grinderId, 10.0, "Acme Uno · 10 clics")))
    repository.delete(id)
    val deleted = repository.pendingUndo.value!!
    db.openHelper.writableDatabase.execSQL("DELETE FROM grinders WHERE id = $grinderId")
    repository.restore(deleted)
    assertEquals(GrindSetting(null, 10.0, "Acme Uno · 10 clics"), repository.get(id)!!.grind)
  }

  @Test
  fun deletingTheGrinderLeavesTheRecipeWithItsValueAndLabel() = runTest {
    val grinderId = db.addGrinder(TestGrinders.uno)
    val id = repository.save(mine().copy(grind = GrindSetting(grinderId, 21.0, "Acme Uno · 21 clics")))
    db.openHelper.writableDatabase.execSQL("DELETE FROM grinders WHERE id = $grinderId")
    assertEquals(GrindSetting(null, 21.0, "Acme Uno · 21 clics"), repository.get(id)!!.grind)
  }

  @Test
  fun deleteCascadesToTheStagesAndRestoreBringsBackTheSameId() = runTest {
    val id = repository.save(mine())
    val before = repository.get(id)!!
    repository.delete(id)
    assertNull(repository.get(id))
    val stageRows = db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM recipe_stages WHERE recipeId = $id").use {
      it.moveToFirst()
      it.getInt(0)
    }
    assertEquals(0, stageRows)
    val deleted = repository.pendingUndo.value!!
    assertEquals(id, deleted.recipe.id)
    assertEquals(2, deleted.stages.size)

    repository.restore(deleted)
    assertNull(repository.pendingUndo.value)
    assertEquals(before, repository.get(id))
  }

  @Test
  fun aSecondDeleteReplacesPendingUndoAndClearIsCompareAndSet() = runTest {
    val a = repository.save(mine("A"))
    val b = repository.save(mine("B"))
    repository.delete(a)
    val first = repository.pendingUndo.value!!
    repository.delete(b)
    val second = repository.pendingUndo.value!!
    assertNotEquals(first, second)
    repository.clearPendingUndo(first) // un aviso que expira no debe borrar el posterior
    assertEquals(second, repository.pendingUndo.value)
    repository.clearPendingUndo(second)
    assertNull(repository.pendingUndo.value)
  }

  @Test
  fun deletingABuiltInAllowsTheSameKeyAgainOnRestore() = runTest {
    val v60 = builtIn(BuiltInRecipes.V60_CLASSIC)
    repository.delete(v60.id)
    assertEquals(1, repository.list().first().size)
    repository.restore(repository.pendingUndo.value!!)
    assertEquals(2, repository.list().first().size)
  }

  @Test
  fun restoreBuiltInsResetsEditedOnesRecreatesDeletedOnesAndKeepsTheUsers() = runTest {
    val v60 = builtIn(BuiltInRecipes.V60_CLASSIC)
    val fourSix = builtIn(BuiltInRecipes.FOUR_SIX)
    val userId = repository.save(mine("Mi receta"))
    // Edita la V60 (la renombra) y borra la 4:6.
    val edited = repository.get(v60.id)!!.copy(
      name = "Mi V60",
      doseG = 18.0,
      notes = "Notas",
      localizedNames = false,
      stages = listOf(RecipeStage("Solo", 0, 300.0, 60_000)),
    )
    repository.save(edited)
    repository.delete(fourSix.id)
    repository.clearPendingUndo()

    repository.restoreBuiltIns()

    val list = repository.list().first()
    assertEquals(3, list.size)
    val restored = repository.get(v60.id)!! // conserva el id
    assertEquals("V60 clásica", restored.name)
    assertEquals(15.0, restored.doseG, 0.0)
    assertNull(restored.notes)
    assertTrue(restored.localizedNames)
    assertEquals(BuiltInRecipes.recipe(BuiltInRecipes.V60_CLASSIC, texts).stages, restored.stages)
    assertEquals(2, restored.stages.count { it.type == StageType.WAIT })
    val recreated = repository.get(builtIn(BuiltInRecipes.FOUR_SIX).id)!!
    assertEquals(9, recreated.stages.size)
    assertEquals(4, recreated.stages.count { it.type == StageType.WAIT })
    assertTrue(recreated.localizedNames)
    assertEquals(mine("Mi receta").copy(id = userId), repository.get(userId))
  }

  @Test
  fun aDuplicateOfABuiltInSurvivesRestoreBuiltIns() = runTest {
    val copyId = repository.duplicate(builtIn(BuiltInRecipes.V60_CLASSIC).id)!!
    repository.restoreBuiltIns()
    assertEquals(3, repository.list().first().size)
    assertEquals("V60 clásica (copia)", repository.get(copyId)!!.name)
  }

  @Test
  fun translatedNamesFollowTheLanguageWhileLocalizedNamesIsTrue() = runTest {
    val v60 = builtIn(BuiltInRecipes.V60_CLASSIC)
    texts.language = "en"
    val english = repository.get(v60.id)!!
    assertEquals("Classic V60", english.name)
    assertEquals(listOf("Bloom", null, "2nd pour", null, "3rd pour"), english.stages.map { it.name })
    assertEquals(listOf("en-i1", "en-i2", "en-i3", "en-i4", "en-i5"), english.stages.map { it.instruction })
    assertEquals("Classic V60", repository.observe(v60.id).first()!!.name)
    // Una vez renombrada, ya no se traduce.
    texts.language = "es"
    repository.save(repository.get(v60.id)!!.copy(name = "Mi V60", localizedNames = false))
    texts.language = "en"
    assertEquals("Mi V60", repository.get(v60.id)!!.name)
  }

  @Test
  fun theListIsOrderedByTheDisplayedNameAfterTranslating() = runTest {
    repository.save(mine("Brasil"))
    texts.language = "es"
    assertEquals(listOf("Brasil", "Método 4:6", "V60 clásica"), repository.list().first().map { it.name })
    texts.language = "en"
    // Sin distinguir mayúsculas, y con "4:6 method" antes que "Brasil" por los dígitos.
    assertEquals(listOf("4:6 method", "Brasil", "Classic V60"), repository.list().first().map { it.name })
  }

  @Test
  fun equalNamesAreAllowedAndOrderedById() = runTest {
    val first = repository.save(mine("Igual"))
    val second = repository.save(mine("igual"))
    val ids = repository.list().first().filter { it.name.equals("igual", ignoreCase = true) }.map { it.id }
    assertEquals(listOf(first, second), ids)
  }

  @Test
  fun methodOtherIsKeptOnlyWithTheOtherMethod() = runTest {
    val id = repository.save(mine().copy(method = BrewMethod.V60, methodOther = "Clever"))
    assertNull(repository.get(id)!!.methodOther)
    val other = repository.save(mine().copy(method = BrewMethod.OTHER, methodOther = " Clever "))
    assertEquals("Clever", repository.get(other)!!.methodOther)
    assertNotNull(repository.get(other))
  }
}
