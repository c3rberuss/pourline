package dev.dotscale.ui.recipes

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.platform.app.InstrumentationRegistry
import dev.dotscale.R
import dev.dotscale.history.BrewRepository
import dev.dotscale.history.HistoryDatabase
import dev.dotscale.history.TestBrews
import dev.dotscale.history.BrewMethod
import dev.dotscale.recipe.BuiltInRecipes
import dev.dotscale.recipe.Recipe
import dev.dotscale.recipe.RecipeStage
import dev.dotscale.recipe.RecipeRepository
import dev.dotscale.recipe.ResourcesRecipeTexts
import dev.dotscale.ui.theme.DotScaleTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class RecipeEditScreenTest {
  @get:Rule
  val rule = createComposeRule()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private val texts = ResourcesRecipeTexts(context)
  private lateinit var db: HistoryDatabase
  private lateinit var recipes: RecipeRepository
  private lateinit var brews: BrewRepository
  private var backCount = 0
  private var duplicatedId: Long? = null

  @Before
  fun setUp() {
    db = HistoryDatabase.inMemory(context, texts)
    recipes = RecipeRepository(db, texts)
    brews = BrewRepository(db)
  }

  @After
  fun tearDown() = db.close()

  private fun show(id: Long? = null, fromBrewId: Long? = null): RecipeEditViewModel {
    val viewModel = RecipeEditViewModel(recipes, brews, texts, id, fromBrewId)
    rule.setContent {
      DotScaleTheme(darkTheme = false) {
        RecipeEditScreen(viewModel, onBack = { backCount++ }, onDuplicated = { duplicatedId = it })
      }
    }
    rule.waitUntil(5_000) { rule.onAllNodesWithTag("recipe_edit_save").fetchSemanticsNodes().isNotEmpty() }
    return viewModel
  }

  private fun builtInId(key: String) = runBlocking { recipes.list().first().single { it.name == texts.builtInName(key) }.id }

  private fun type(tag: String, text: String) = rule.onNodeWithTag(tag).performScrollTo().performTextInput(text)

  private fun replace(tag: String, text: String) = rule.onNodeWithTag(tag).performScrollTo().performTextReplacement(text)

  private fun exists(tag: String) = rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

  /** Elige el tipo de un paso con su etiqueta, dentro del segmentado `recipe_stage_type_<i>`. */
  private fun chooseType(index: Int, labelRes: Int) {
    rule.onNode(hasText(context.getString(labelRes)) and hasAnyAncestor(hasTestTag("recipe_stage_type_$index")))
      .performScrollTo()
      .performClick()
  }

  @Test
  fun aNewRecipeStartsWithOneStageAndSaveIsDisabledUntilItIsValid() {
    show()
    rule.onNodeWithTag("recipe_stage_start_0").performScrollTo().assertTextEquals("0:00")
    rule.onNodeWithTag("recipe_stage_water_0").assertTextEquals("50")
    rule.onNodeWithTag("recipe_stage_duration_0").assertTextEquals("10")
    assertFalse(exists("recipe_stage_1"))
    rule.onNodeWithTag("recipe_edit_save").performScrollTo().assertIsNotEnabled() // sin nombre ni dosis

    type("recipe_edit_name", "Mi receta")
    rule.onNodeWithTag("recipe_edit_save").performScrollTo().assertIsNotEnabled() // falta la dosis
    type("recipe_edit_dose", "16")
    rule.onNodeWithTag("recipe_edit_save").performScrollTo().assertIsEnabled()
  }

  @Test
  fun validationErrorsShowOnTheirFieldAndBlockSave() {
    show()
    type("recipe_edit_name", "Mi receta")
    type("recipe_edit_dose", "4")
    rule.onNodeWithTag("recipe_edit_dose_error").performScrollTo()
    rule.onNodeWithTag("recipe_edit_save").performScrollTo().assertIsNotEnabled()

    replace("recipe_edit_dose", "16")
    assertFalse(exists("recipe_edit_dose_error"))
    replace("recipe_stage_start_0", "1:75")
    rule.onNodeWithTag("recipe_stage_start_error_0").performScrollTo()
    rule.onNodeWithTag("recipe_edit_save").performScrollTo().assertIsNotEnabled()
    replace("recipe_stage_start_0", "90")
    replace("recipe_stage_water_0", "abc")
    rule.onNodeWithTag("recipe_stage_water_error_0").performScrollTo()
    replace("recipe_stage_water_0", "50")
    replace("recipe_stage_duration_0", "121")
    rule.onNodeWithTag("recipe_stage_duration_error_0").performScrollTo()
    replace("recipe_stage_duration_0", "10")
    rule.onNodeWithTag("recipe_edit_save").performScrollTo().assertIsEnabled()

    replace("recipe_edit_temp", "120")
    rule.onNodeWithTag("recipe_edit_temp_error").performScrollTo()
    rule.onNodeWithTag("recipe_edit_save").performScrollTo().assertIsNotEnabled()
  }

  @Test
  fun addStageProposesTheNextValuesAndIsDisabledWithTwentyStages() {
    show()
    rule.onNodeWithTag("recipe_add_stage").performScrollTo().performClick()
    rule.onNodeWithTag("recipe_stage_start_1").performScrollTo().assertTextEquals("0:10") // el fin del paso anterior
    rule.onNodeWithTag("recipe_stage_water_1").assertTextEquals("100") // 50 + 50 g
    rule.onNodeWithTag("recipe_stage_duration_1").assertTextEquals("10")

    repeat(18) { rule.onNodeWithTag("recipe_add_stage").performScrollTo().performClick() }
    assertTrue(exists("recipe_stage_19"))
    rule.onNodeWithTag("recipe_add_stage").performScrollTo().assertIsNotEnabled()
  }

  @Test
  fun choosingWaitHidesTheWaterAndChoosingPourBringsItBackWithAProposal() {
    show()
    rule.onNodeWithTag("recipe_add_stage").performScrollTo().performClick()
    assertTrue(exists("recipe_stage_water_1"))
    chooseType(1, R.string.step_type_wait)
    assertFalse(exists("recipe_stage_water_1"))
    assertTrue(exists("recipe_stage_water_0"))
    chooseType(1, R.string.step_type_swirl)
    assertFalse(exists("recipe_stage_water_1"))
    chooseType(1, R.string.step_type_pour)
    rule.onNodeWithTag("recipe_stage_water_1").performScrollTo().assertTextEquals("100") // 50 + 50 g
  }

  @Test
  fun anInstructionIsKeptWhenSavingAndTheCounterFollowsIt() {
    show()
    type("recipe_edit_name", "Con instrucción")
    type("recipe_edit_dose", "16")
    rule.onNodeWithTag("recipe_stage_instruction_counter_0").performScrollTo().assertTextEquals("0/200")
    type("recipe_stage_instruction_0", "Vierte en espiral")
    rule.onNodeWithTag("recipe_stage_instruction_counter_0").assertTextEquals("17/200")
    rule.onNodeWithTag("recipe_edit_save").performScrollTo().performClick()
    rule.waitUntil(5_000) { backCount == 1 }
    val saved = runBlocking { recipes.get(recipes.list().first().single { !it.builtIn }.id)!! }
    assertEquals("Vierte en espiral", saved.stages.single().instruction)
  }

  @Test
  fun anInstructionOver200CharactersShowsItsErrorAndBlocksSave() {
    show()
    type("recipe_edit_name", "Larga")
    type("recipe_edit_dose", "16")
    type("recipe_stage_instruction_0", "a".repeat(201))
    rule.onNodeWithTag("recipe_stage_instruction_error_0").performScrollTo()
    rule.onNodeWithTag("recipe_edit_save").performScrollTo().assertIsNotEnabled()
    replace("recipe_stage_instruction_0", "a".repeat(200))
    assertFalse(exists("recipe_stage_instruction_error_0"))
    rule.onNodeWithTag("recipe_edit_save").performScrollTo().assertIsEnabled()
  }

  @Test
  fun theClassicV60ShowsItsFiveStepsWithTwoWaits() {
    show(id = builtInId(BuiltInRecipes.V60_CLASSIC))
    assertTrue(exists("recipe_stage_4"))
    assertFalse(exists("recipe_stage_5"))
    // Los dos pasos de espera no piden agua; los tres vertidos sí.
    for (i in listOf(1, 3)) assertFalse("water $i", exists("recipe_stage_water_$i"))
    for (i in listOf(0, 2, 4)) assertTrue("water $i", exists("recipe_stage_water_$i"))
    rule.onNodeWithTag("recipe_stage_water_4").performScrollTo().assertTextEquals("250")
    // Cada paso trae su instrucción.
    rule.onNodeWithTag("recipe_stage_instruction_0").performScrollTo()
    val instruction = texts.builtInStageInstructions(BuiltInRecipes.V60_CLASSIC).first()!!
    rule.onNodeWithTag("recipe_stage_instruction_0").assertTextEquals(instruction, includeEditableText = true)
  }

  @Test
  fun aRecipeOf21StepsInsertedBySqlShowsTheCountErrorAndCannotBeSavedUntilReduced() {
    val id = runBlocking {
      val saved = recipes.save(
        Recipe(
          name = "Larga",
          method = BrewMethod.V60,
          doseG = 15.0,
          stages = listOf(RecipeStage(null, 0, 40.0, 10_000)),
        ),
      )
      saved
    }
    val sql = db.openHelper.writableDatabase
    for (i in 1..20) {
      sql.execSQL(
        "INSERT INTO recipe_stages (recipeId, position, name, startMs, targetWaterG, durationMs, type, instruction) " +
          "VALUES ($id, $i, NULL, ${i * 20_000}, ${40.0 + i}, 10000, 'POUR', NULL)",
      )
    }
    show(id = id)
    assertTrue(exists("recipe_stage_20"))
    rule.onNodeWithTag("recipe_edit_error_general").performScrollTo()
    rule.onNodeWithTag("recipe_edit_save").performScrollTo().assertIsNotEnabled()
    rule.onNodeWithTag("recipe_add_stage").performScrollTo().assertIsNotEnabled()
    rule.onNodeWithContentDescription(context.getString(R.string.recipe_edit_stage_remove, 21)).performScrollTo().performClick()
    assertFalse(exists("recipe_edit_error_general"))
    rule.onNodeWithTag("recipe_edit_save").performScrollTo().assertIsEnabled()
  }

  @Test
  fun removingAStageUsesItsLabelAndTheLastOneCannotBeRemoved() {
    show()
    rule.onNodeWithTag("recipe_add_stage").performScrollTo().performClick()
    assertTrue(exists("recipe_stage_1"))
    rule.onNodeWithContentDescription(context.getString(R.string.recipe_edit_stage_remove, 2)).performScrollTo().performClick()
    assertFalse(exists("recipe_stage_1"))
    rule.onNodeWithContentDescription(context.getString(R.string.recipe_edit_stage_remove, 1)).performScrollTo().assertIsNotEnabled()
  }

  @Test
  fun liveTotalsAndAPreviewAppearWithAValidRecipe() {
    show()
    type("recipe_edit_name", "Mi receta")
    type("recipe_edit_dose", "20")
    replace("recipe_stage_water_0", "300")
    rule.onNodeWithTag("recipe_edit_totals").performScrollTo()
      .assertTextEquals(context.getString(R.string.recipe_edit_totals, "300", String.format(java.util.Locale.getDefault(), "%.1f", 15.0)))
    rule.onNodeWithTag("recipe_edit_preview").performScrollTo()
  }

  @Test
  fun savingGoesBackAndCreatesTheRecipe() {
    show()
    type("recipe_edit_name", "  Mi receta ")
    type("recipe_edit_dose", "16,5")
    rule.onNodeWithTag("recipe_edit_save").performScrollTo().performClick()
    rule.waitUntil(5_000) { backCount == 1 }
    val saved = runBlocking { recipes.list().first().single { !it.builtIn } }
    assertEquals("Mi receta", saved.name)
    assertEquals(16.5, saved.doseG, 0.0)
    assertEquals(50.0, saved.totalWaterG, 0.0)
  }

  @Test
  fun backWithChangesAsksBeforeDiscarding() {
    show()
    type("recipe_edit_name", "Algo")
    val back = context.getString(R.string.scale_back)
    rule.onNodeWithContentDescription(back).performClick()
    rule.onNodeWithTag("ant_modal").assertExists()
    assertEquals(0, backCount)
    rule.onNodeWithTag("ant_modal_cancel").performClick() // "Seguir editando"
    assertFalse(exists("ant_modal"))
    rule.onNodeWithContentDescription(back).performClick()
    rule.onNodeWithTag("ant_modal_confirm").performClick() // "Descartar"
    assertEquals(1, backCount)
  }

  @Test
  fun backWithoutChangesLeavesAtOnce() {
    show()
    rule.onNodeWithContentDescription(context.getString(R.string.scale_back)).performClick()
    assertEquals(1, backCount)
    assertFalse(exists("ant_modal"))
  }

  @Test
  fun editingOnlyQuantitiesKeepsTheTranslatedNamesAndRenamingDropsThem() {
    val id = builtInId(BuiltInRecipes.V60_CLASSIC)
    show(id = id)
    replace("recipe_edit_dose", "16")
    rule.onNodeWithTag("recipe_edit_save").performScrollTo().performClick()
    rule.waitUntil(5_000) { backCount == 1 }
    var recipe = runBlocking { recipes.get(id)!! }
    assertEquals(16.0, recipe.doseG, 0.0)
    assertTrue(recipe.localizedNames)
    assertEquals(BuiltInRecipes.V60_CLASSIC, recipe.builtInKey)
    assertEquals(texts.builtInName(BuiltInRecipes.V60_CLASSIC), recipe.name)

    // Renombrar la receta pone `localizedNames` a false y guarda los textos mostrados.
    rule.runOnIdle { }
  }

  @Test
  fun renamingABuiltInStoresTheDisplayedTextsAndStopsTranslating() {
    val id = builtInId(BuiltInRecipes.FOUR_SIX)
    show(id = id)
    replace("recipe_edit_name", "Mi 4:6")
    rule.onNodeWithTag("recipe_edit_save").performScrollTo().performClick()
    rule.waitUntil(5_000) { backCount == 1 }
    val recipe = runBlocking { recipes.get(id)!! }
    assertEquals("Mi 4:6", recipe.name)
    assertFalse(recipe.localizedNames)
    assertEquals(BuiltInRecipes.FOUR_SIX, recipe.builtInKey)
    // Las etapas conservan los nombres que se mostraban.
    assertEquals(texts.builtInStageNames(BuiltInRecipes.FOUR_SIX), recipe.stages.map { it.name })
  }

  @Test
  fun duplicateIsDisabledWithUnsavedChangesAndOpensTheCopyOtherwise() {
    val id = builtInId(BuiltInRecipes.V60_CLASSIC)
    show(id = id)
    rule.onNodeWithTag("recipe_edit_duplicate").performScrollTo().assertIsEnabled()
    replace("recipe_edit_dose", "17")
    rule.onNodeWithTag("recipe_edit_duplicate").performScrollTo().assertIsNotEnabled()
    replace("recipe_edit_dose", "15")
    rule.onNodeWithTag("recipe_edit_duplicate").performScrollTo().assertIsEnabled().performClick()
    rule.waitUntil(5_000) { duplicatedId != null }
    val copy = runBlocking { recipes.get(duplicatedId!!)!! }
    assertEquals(context.getString(R.string.recipe_copy_name, texts.builtInName(BuiltInRecipes.V60_CLASSIC)), copy.name)
    assertEquals(null, copy.builtInKey)
  }

  @Test
  fun deletingGoesBackAndLeavesTheRecipeForUndo() {
    val id = builtInId(BuiltInRecipes.V60_CLASSIC)
    show(id = id)
    rule.onNodeWithTag("recipe_edit_delete").performScrollTo().performClick()
    rule.waitUntil(5_000) { backCount == 1 }
    assertEquals(null, runBlocking { recipes.get(id) })
    assertEquals(id, recipes.pendingUndo.value?.recipe?.id)
  }

  @Test
  fun aNewRecipeHasNoDuplicateOrDeleteButtons() {
    show()
    assertFalse(exists("recipe_edit_duplicate"))
    assertFalse(exists("recipe_edit_delete"))
  }

  @Test
  fun saveAsRecipeFromABrewFillsOneStagePerPourAndWarnsAboutAMissingDose() {
    val brewId = runBlocking { brews.save(TestBrews.finished(doseG = null), TestBrews.context()) }
    show(fromBrewId = brewId)
    assertTrue(exists("recipe_edit_adjusted"))
    // Tres vertidos con dos huecos → tres vertidos y dos esperas (D40).
    assertTrue(exists("recipe_stage_4"))
    assertFalse(exists("recipe_stage_5"))
    rule.onNodeWithTag("recipe_edit_dose").performScrollTo().assertTextEquals("")
    rule.onNodeWithTag("recipe_edit_save").performScrollTo().assertIsNotEnabled()

    type("recipe_edit_dose", "20")
    rule.onNodeWithTag("recipe_edit_save").performScrollTo().assertIsEnabled().performClick()
    rule.waitUntil(5_000) { backCount == 1 }
    val saved = runBlocking { recipes.list().first().single { !it.builtIn } }
    assertEquals(3, saved.stageCount)
    assertTrue(saved.name.endsWith(context.getString(R.string.recipe_from_brew_name, "").trim().ifEmpty { "(" }.take(1)).not())
  }

  @Test
  fun saveAsRecipeWithAValidDoseShowsNoAdjustmentNotice() {
    val brewId = runBlocking { brews.save(TestBrews.finished(doseG = 20.0), TestBrews.context()) }
    show(fromBrewId = brewId)
    assertFalse(exists("recipe_edit_adjusted"))
    rule.onNodeWithTag("recipe_edit_dose").performScrollTo().assertTextEquals("20")
    rule.onNodeWithTag("recipe_edit_save").performScrollTo().assertIsEnabled()
    // El nombre es el de la fecha de la preparación más " (receta)".
    assertNotNull(runBlocking { brews.get(brewId) })
  }

  @Test
  fun aBrewWithoutDoseClearedTextStaysClearedAfterEditing() {
    val brewId = runBlocking { brews.save(TestBrews.finished(doseG = 20.0), TestBrews.context()) }
    show(fromBrewId = brewId)
    rule.onNodeWithTag("recipe_edit_dose").performScrollTo().performTextClearance()
    rule.onNodeWithTag("recipe_edit_dose_error").performScrollTo()
    rule.onNodeWithTag("recipe_edit_save").performScrollTo().assertIsNotEnabled()
  }
}
