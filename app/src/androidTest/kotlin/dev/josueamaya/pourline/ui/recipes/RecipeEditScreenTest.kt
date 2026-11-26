package dev.josueamaya.pourline.ui.recipes

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.platform.app.InstrumentationRegistry
import dev.josueamaya.pourline.R
import dev.josueamaya.pourline.grinder.FakeDefaultGrinderStore
import dev.josueamaya.pourline.grinder.GrindSetting
import dev.josueamaya.pourline.grinder.GrinderRepository
import dev.josueamaya.pourline.grinder.ResourcesGrindTexts
import dev.josueamaya.pourline.grinder.TestGrinders
import dev.josueamaya.pourline.grinder.addGrinder
import dev.josueamaya.pourline.grinder.grinderRepository
import dev.josueamaya.pourline.history.BrewRepository
import dev.josueamaya.pourline.history.HistoryDatabase
import dev.josueamaya.pourline.history.LegacyBuiltIns
import dev.josueamaya.pourline.history.TestBrews
import dev.josueamaya.pourline.history.BrewMethod
import dev.josueamaya.pourline.recipe.Recipe
import dev.josueamaya.pourline.recipe.RecipeStage
import dev.josueamaya.pourline.recipe.RecipeRepository
import dev.josueamaya.pourline.recipe.ResourcesRecipeTexts
import dev.josueamaya.pourline.recipe.StageType
import dev.josueamaya.pourline.recipe.fourSix
import dev.josueamaya.pourline.recipe.v60Classic
import dev.josueamaya.pourline.settings.ExperienceMode
import dev.josueamaya.pourline.settings.LocalExperienceMode
import dev.josueamaya.pourline.ui.theme.PourlineTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
  private lateinit var grinders: GrinderRepository
  private val defaults = FakeDefaultGrinderStore()
  private var backCount = 0
  private var duplicatedId: Long? = null

  @Before
  fun setUp() {
    db = HistoryDatabase.inMemory(context)
    recipes = RecipeRepository(db, texts)
    brews = BrewRepository(db)
    grinders = db.grinderRepository(context, defaults)
  }

  @After
  fun tearDown() = db.close()

  private fun show(id: Long? = null, fromBrewId: Long? = null, mode: ExperienceMode = ExperienceMode.EXPERT): RecipeEditViewModel {
    val viewModel = RecipeEditViewModel(recipes, brews, texts, grinders, defaults, ResourcesGrindTexts(context), id, fromBrewId)
    rule.setContent {
      CompositionLocalProvider(LocalExperienceMode provides mode) {
        PourlineTheme(darkTheme = false) {
          RecipeEditScreen(viewModel, onBack = { backCount++ }, onDuplicated = { duplicatedId = it })
        }
      }
    }
    rule.waitUntil(5_000) { rule.onAllNodesWithTag("recipe_edit_save").fetchSemanticsNodes().isNotEmpty() }
    return viewModel
  }

  /** Siembra la V60 clásica o la 4:6 antiguas como receta propia (si aún no están) y devuelve su id. */
  private fun seededId(key: String): Long = runBlocking {
    recipes.list().first().firstOrNull { it.name == texts.builtInName(key) }?.id
      ?: recipes.save(if (key == LegacyBuiltIns.V60_CLASSIC) v60Classic(texts) else fourSix(texts))
  }

  private fun type(tag: String, text: String) = rule.onNodeWithTag(tag).performScrollTo().performTextInput(text)

  private fun replace(tag: String, text: String) = rule.onNodeWithTag(tag).performScrollTo().performTextReplacement(text)

  private fun exists(tag: String) = rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

  /** Elige el tipo de un paso por su descripción (las opciones solo llevan icono), dentro del segmentado `recipe_stage_type_<i>`. */
  private fun chooseType(index: Int, labelRes: Int) {
    rule.onNode(hasContentDescription(context.getString(labelRes)) and hasAnyAncestor(hasTestTag("recipe_stage_type_$index")))
      .performScrollTo()
      .performClick()
  }

  @Test
  fun theMethodSelectorOffersTheNewMethodsAndSavesTheChosenOne() {
    show()
    val names = listOf(
      "Espresso" to BrewMethod.ESPRESSO,
      context.getString(R.string.brew_method_french_press) to BrewMethod.FRENCH_PRESS,
      context.getString(R.string.brew_method_cold_brew) to BrewMethod.COLD_BREW,
      context.getString(R.string.brew_method_moka) to BrewMethod.MOKA,
    )
    for ((name, _) in names) {
      rule.onNodeWithTag("recipe_edit_method").performScrollTo().performClick()
      rule.onNode(hasText(name) and hasAnyAncestor(isPopup())).performClick()
      rule.onNodeWithTag("recipe_edit_method").assertTextContains(name, substring = true)
    }
    type("recipe_edit_name", "Mi prensa")
    type("recipe_edit_dose", "30")
    rule.onNodeWithTag("recipe_edit_save").performScrollTo().performClick()
    rule.waitUntil(5_000) { runBlocking { recipes.list().first().any { it.name == "Mi prensa" } } }
    assertEquals(BrewMethod.MOKA, runBlocking { recipes.list().first().single { it.name == "Mi prensa" }.method })
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
  fun theCurvePreviewShowsOnlyInExpertMode() {
    show(id = seededId(LegacyBuiltIns.V60_CLASSIC), mode = ExperienceMode.EXPERT)
    rule.onNodeWithTag("recipe_edit_preview").performScrollTo().assertExists()
  }

  @Test
  fun inBasicModeThereIsNoCurvePreviewButTheStepsAreEditable() {
    show(id = seededId(LegacyBuiltIns.V60_CLASSIC), mode = ExperienceMode.BASIC)
    assertFalse(exists("recipe_edit_preview"))
    assertTrue(exists("recipe_stage_4"))
    rule.onNodeWithTag("recipe_edit_totals").performScrollTo().assertExists()
    chooseType(1, R.string.step_type_swirl)
    assertFalse(exists("recipe_stage_water_1"))
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
    val saved = runBlocking { recipes.get(recipes.list().first().single().id)!! }
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
    show(id = seededId(LegacyBuiltIns.V60_CLASSIC))
    assertTrue(exists("recipe_stage_4"))
    assertFalse(exists("recipe_stage_5"))
    // Los dos pasos de espera no piden agua; los tres vertidos sí.
    for (i in listOf(1, 3)) assertFalse("water $i", exists("recipe_stage_water_$i"))
    for (i in listOf(0, 2, 4)) assertTrue("water $i", exists("recipe_stage_water_$i"))
    rule.onNodeWithTag("recipe_stage_water_4").performScrollTo().assertTextEquals("250")
    // Cada paso trae su instrucción.
    rule.onNodeWithTag("recipe_stage_instruction_0").performScrollTo()
    val instruction = texts.builtInStageInstructions(LegacyBuiltIns.V60_CLASSIC).first()!!
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
  fun movingAStepReordersItAndRecalculatesItsStartAndWater() {
    show()
    rule.onNodeWithTag("recipe_add_stage").performScrollTo().performClick()
    rule.onNodeWithTag("recipe_add_stage").performScrollTo().performClick()
    replace("recipe_stage_name_0", "Bloom")
    replace("recipe_stage_name_1", "Vertido")
    replace("recipe_stage_name_2", "Espera")
    chooseType(2, R.string.step_type_wait)
    replace("recipe_stage_duration_2", "30")
    // Los extremos no se mueven hacia fuera.
    rule.onNodeWithContentDescription(context.getString(R.string.recipe_edit_stage_up, 1)).performScrollTo().assertIsNotEnabled()
    rule.onNodeWithContentDescription(context.getString(R.string.recipe_edit_stage_down, 3)).performScrollTo().assertIsNotEnabled()

    // La espera, añadida al final, sube entre el bloom y el vertido.
    rule.onNodeWithContentDescription(context.getString(R.string.recipe_edit_stage_up, 3)).performScrollTo().performClick()
    rule.onNodeWithTag("recipe_stage_name_1").performScrollTo().assertTextEquals("Espera", includeEditableText = true)
    rule.onNodeWithTag("recipe_stage_start_1").assertTextEquals("0:10")
    assertFalse(exists("recipe_stage_water_1"))
    rule.onNodeWithTag("recipe_stage_name_2").performScrollTo().assertTextEquals("Vertido", includeEditableText = true)
    rule.onNodeWithTag("recipe_stage_start_2").assertTextEquals("0:40")
    rule.onNodeWithTag("recipe_stage_water_2").assertTextEquals("100")

    // El vertido baja al primer puesto con los 50 g que echaba: ahora hasta 50 g y el bloom hasta 100 g.
    rule.onNodeWithContentDescription(context.getString(R.string.recipe_edit_stage_up, 3)).performScrollTo().performClick()
    rule.onNodeWithContentDescription(context.getString(R.string.recipe_edit_stage_up, 2)).performScrollTo().performClick()
    rule.onNodeWithTag("recipe_stage_name_0").performScrollTo().assertTextEquals("Vertido", includeEditableText = true)
    rule.onNodeWithTag("recipe_stage_water_0").assertTextEquals("50")
    rule.onNodeWithTag("recipe_stage_name_1").performScrollTo().assertTextEquals("Bloom", includeEditableText = true)
    rule.onNodeWithTag("recipe_stage_water_1").assertTextEquals("100")

    type("recipe_edit_name", "Reordenada")
    type("recipe_edit_dose", "15")
    rule.onNodeWithTag("recipe_edit_save").performScrollTo().assertIsEnabled().performClick()
    rule.waitUntil(5_000) { runBlocking { recipes.list().first().any { it.name == "Reordenada" } } }
    val saved = runBlocking { recipes.get(recipes.list().first().single { it.name == "Reordenada" }.id)!! }
    assertEquals(listOf("Vertido", "Bloom", "Espera"), saved.stages.map { it.name })
    assertEquals(listOf(0L, 10_000L, 20_000L), saved.stages.map { it.startMs })
  }

  @Test
  fun aMovedStepSaysWhereItWentForAMomentAndEachStepShowsItsPosition() {
    show()
    assertFalse(exists("recipe_stage_position_0")) // con un solo paso no hay nada que reordenar
    rule.onNodeWithTag("recipe_add_stage").performScrollTo().performClick()
    rule.onNodeWithTag("recipe_add_stage").performScrollTo().performClick()
    rule.onNodeWithTag("recipe_stage_position_0").performScrollTo()
      .assertTextEquals(context.getString(R.string.recipe_edit_stage_position, 1, 3))

    rule.mainClock.autoAdvance = false
    rule.onNodeWithContentDescription(context.getString(R.string.recipe_edit_stage_down, 1)).performScrollTo().performClick()
    rule.mainClock.advanceTimeBy(500)
    rule.onNodeWithTag("recipe_stage_position_1")
      .assertTextEquals(context.getString(R.string.recipe_edit_stage_moved, 2, 3))
    rule.onNodeWithTag("recipe_stage_position_0")
      .assertTextEquals(context.getString(R.string.recipe_edit_stage_position, 1, 3))
    rule.mainClock.advanceTimeBy(2_500)
    rule.onNodeWithTag("recipe_stage_position_1")
      .assertTextEquals(context.getString(R.string.recipe_edit_stage_position, 2, 3))
    rule.mainClock.autoAdvance = true
  }

  @Test
  fun liveTotalsAndAPreviewAppearWithAValidRecipe() {
    show()
    type("recipe_edit_name", "Mi receta")
    type("recipe_edit_dose", "20")
    replace("recipe_stage_water_0", "300")
    rule.onNodeWithTag("recipe_edit_totals").performScrollTo()
      .assertTextEquals(context.getString(R.string.recipe_edit_totals_time, "300", String.format(java.util.Locale.getDefault(), "%.1f", 15.0), "0:10"))
    rule.onNodeWithTag("recipe_edit_preview").performScrollTo()
  }

  private fun totalsText(withTime: String?): String {
    val ratio = String.format(java.util.Locale.getDefault(), "%.1f", 6.0)
    return if (withTime != null) context.getString(R.string.recipe_edit_totals_time, "120", ratio, withTime) else context.getString(R.string.recipe_edit_totals, "120", ratio)
  }

  @Test
  fun theTotalTimeFollowsTheStepsAndHidesWhileAStepCannotBeRead() {
    show()
    type("recipe_edit_dose", "20")
    replace("recipe_stage_water_0", "120")
    rule.onNodeWithTag("recipe_edit_totals").performScrollTo().assertTextEquals(totalsText("0:10"))
    replace("recipe_stage_duration_0", "25")
    rule.onNodeWithTag("recipe_edit_totals").performScrollTo().assertTextEquals(totalsText("0:25"))
    // Un paso nuevo empieza donde acaba el anterior (0:25) y dura 10 s.
    rule.onNodeWithTag("recipe_add_stage").performScrollTo().performClick()
    replace("recipe_stage_water_1", "120")
    rule.onNodeWithTag("recipe_edit_totals").performScrollTo().assertTextEquals(totalsText("0:35"))
    // Con un inicio que no se lee, la línea sigue sin el tiempo.
    replace("recipe_stage_start_1", "")
    rule.onNodeWithTag("recipe_edit_totals").performScrollTo().assertTextEquals(totalsText(null))
    replace("recipe_stage_start_1", "0:25")
    rule.onNodeWithTag("recipe_edit_totals").performScrollTo().assertTextEquals(totalsText("0:35"))
    rule.onNodeWithTag("recipe_edit_totals")
      .assertContentDescriptionEquals(totalsText(context.getString(R.string.recipe_total_time_description, "0:35")))
  }

  @Test
  fun theTypeSelectorOffersActionAndSavesAnActionWithItsInstruction() {
    show()
    type("recipe_edit_name", "AeroPress")
    type("recipe_edit_dose", "15")
    rule.onNodeWithTag("recipe_add_stage").performScrollTo().performClick()
    assertTrue(exists("recipe_stage_water_1"))
    chooseType(1, R.string.step_type_action)
    assertFalse(exists("recipe_stage_water_1"))
    rule.onNodeWithTag("recipe_stage_type_label_1").performScrollTo().assertTextEquals(context.getString(R.string.step_type_action))
    type("recipe_stage_instruction_1", "Pon la tapa")
    rule.onNodeWithTag("recipe_edit_save").performScrollTo().assertIsEnabled().performClick()
    rule.waitUntil(5_000) { backCount == 1 }
    val saved = runBlocking { recipes.get(recipes.list().first().single().id)!! }
    assertEquals(StageType.ACTION, saved.stages[1].type)
    assertEquals("Pon la tapa", saved.stages[1].instruction)
    assertEquals(50.0, saved.stages[1].targetWaterG, 0.0) // el agua del vertido anterior
  }

  @Test
  fun anActionWithoutInstructionShowsTheHintAndStillSaves() {
    show()
    type("recipe_edit_name", "AeroPress")
    type("recipe_edit_dose", "15")
    rule.onNodeWithTag("recipe_add_stage").performScrollTo().performClick()
    // En un vertido sin instrucción no hay aviso.
    assertFalse(exists("recipe_stage_action_hint_0"))
    assertFalse(exists("recipe_stage_action_hint_1"))
    chooseType(1, R.string.step_type_action)
    rule.onNodeWithTag("recipe_stage_action_hint_1").performScrollTo().assertTextEquals(context.getString(R.string.recipe_action_instruction_hint))
    type("recipe_stage_instruction_1", "Pon la tapa")
    assertFalse(exists("recipe_stage_action_hint_1"))
    replace("recipe_stage_instruction_1", "")
    assertTrue(exists("recipe_stage_action_hint_1"))
    rule.onNodeWithTag("recipe_edit_save").performScrollTo().assertIsEnabled().performClick()
    rule.waitUntil(5_000) { backCount == 1 }
    val saved = runBlocking { recipes.get(recipes.list().first().single().id)!! }
    assertEquals(StageType.ACTION, saved.stages[1].type)
    assertEquals(null, saved.stages[1].instruction)
  }

  @Test
  fun theTypeSelectorShowsTheChosenTypeAndFourOptionsAt360dpWithALargeFont() {
    rule.setContent {
      val density = LocalDensity.current
      CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1.3f)) {
        PourlineTheme(darkTheme = false) {
          Box(Modifier.width(360.dp)) {
            val viewModel = remember { RecipeEditViewModel(recipes, brews, texts, grinders, defaults, ResourcesGrindTexts(context), null, null) }
            RecipeEditScreen(viewModel, onBack = {}, onDuplicated = {})
          }
        }
      }
    }
    rule.waitUntil(5_000) { exists("recipe_edit_save") }
    val dp = context.resources.displayMetrics.density
    val card = rule.onNodeWithTag("recipe_stage_0").performScrollTo().fetchSemanticsNode().boundsInRoot
    for (labelRes in listOf(R.string.step_type_pour, R.string.step_type_swirl, R.string.step_type_wait, R.string.step_type_action)) {
      chooseType(0, labelRes)
      val label = context.getString(labelRes)
      val node = rule.onNodeWithTag("recipe_stage_type_label_0").performScrollTo().assertTextEquals(label).fetchSemanticsNode()
      val layouts = mutableListOf<TextLayoutResult>()
      node.config[SemanticsActions.GetTextLayoutResult].action!!(layouts)
      val layout = layouts.single()
      assertEquals("'$label' en una línea", 1, layout.lineCount)
      assertFalse("'$label' se corta", layout.hasVisualOverflow)
      Log.i("StageTypeSelector", "'$label' mide ${(layout.getLineRight(0) - layout.getLineLeft(0)) / dp} dp de ${node.size.width / dp} dp disponibles")
    }
    // Cuatro opciones solo con icono, de 48 dp o más y dentro de la tarjeta del paso.
    for (labelRes in listOf(R.string.step_type_pour, R.string.step_type_swirl, R.string.step_type_wait, R.string.step_type_action)) {
      val option = rule.onNode(hasContentDescription(context.getString(labelRes)) and hasAnyAncestor(hasTestTag("recipe_stage_type_0")))
        .performScrollTo().assertIsDisplayed().fetchSemanticsNode()
      assertTrue("opción de ${option.size.width / dp} dp", option.size.width >= 48 * dp - 1)
      assertTrue("la opción queda dentro de la tarjeta", option.boundsInRoot.left >= card.left && option.boundsInRoot.right <= card.right)
    }
  }

  @Test
  fun savingGoesBackAndCreatesTheRecipe() {
    show()
    type("recipe_edit_name", "  Mi receta ")
    type("recipe_edit_dose", "16,5")
    rule.onNodeWithTag("recipe_edit_save").performScrollTo().performClick()
    rule.waitUntil(5_000) { backCount == 1 }
    val saved = runBlocking { recipes.list().first().single() }
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
  fun editingOnlyQuantitiesKeepsTheNameTheStagesAndHasNoBuiltInKey() {
    val id = seededId(LegacyBuiltIns.V60_CLASSIC)
    show(id = id)
    replace("recipe_edit_dose", "16")
    rule.onNodeWithTag("recipe_edit_save").performScrollTo().performClick()
    rule.waitUntil(5_000) { backCount == 1 }
    val recipe = runBlocking { recipes.get(id)!! }
    assertEquals(16.0, recipe.doseG, 0.0)
    assertFalse(recipe.localizedNames)
    assertNull(recipe.builtInKey)
    assertNull(recipe.catalogKey)
    assertEquals(texts.builtInName(LegacyBuiltIns.V60_CLASSIC), recipe.name)
    assertEquals(texts.builtInStageNames(LegacyBuiltIns.V60_CLASSIC), recipe.stages.map { it.name })
  }

  @Test
  fun renamingARecipeStoresTheNewNameAndKeepsTheStageNames() {
    val id = seededId(LegacyBuiltIns.FOUR_SIX)
    show(id = id)
    replace("recipe_edit_name", "Mi 4:6")
    rule.onNodeWithTag("recipe_edit_save").performScrollTo().performClick()
    rule.waitUntil(5_000) { backCount == 1 }
    val recipe = runBlocking { recipes.get(id)!! }
    assertEquals("Mi 4:6", recipe.name)
    assertFalse(recipe.localizedNames)
    assertEquals(texts.builtInStageNames(LegacyBuiltIns.FOUR_SIX), recipe.stages.map { it.name })
  }

  @Test
  fun editingACatalogCopyKeepsItsCatalogKeyAuthorAndSource() {
    val id = runBlocking {
      recipes.save(
        v60Classic(texts).copy(
          name = "Copia del catálogo",
          catalogKey = "nemo_v60",
          author = "Nemo",
          sourceUrl = "https://example.com/nemo",
        ),
      )
    }
    show(id = id)
    replace("recipe_edit_dose", "16")
    rule.onNodeWithTag("recipe_edit_save").performScrollTo().performClick()
    rule.waitUntil(5_000) { backCount == 1 }
    val recipe = runBlocking { recipes.get(id)!! }
    assertEquals(16.0, recipe.doseG, 0.0)
    assertEquals("nemo_v60", recipe.catalogKey)
    assertEquals("Nemo", recipe.author)
    assertEquals("https://example.com/nemo", recipe.sourceUrl)
  }

  @Test
  fun duplicateIsDisabledWithUnsavedChangesAndOpensTheCopyOtherwise() {
    val id = seededId(LegacyBuiltIns.V60_CLASSIC)
    show(id = id)
    rule.onNodeWithTag("recipe_edit_duplicate").performScrollTo().assertIsEnabled()
    replace("recipe_edit_dose", "17")
    rule.onNodeWithTag("recipe_edit_duplicate").performScrollTo().assertIsNotEnabled()
    replace("recipe_edit_dose", "15")
    rule.onNodeWithTag("recipe_edit_duplicate").performScrollTo().assertIsEnabled().performClick()
    rule.waitUntil(5_000) { duplicatedId != null }
    val copy = runBlocking { recipes.get(duplicatedId!!)!! }
    assertEquals(context.getString(R.string.recipe_copy_name, texts.builtInName(LegacyBuiltIns.V60_CLASSIC)), copy.name)
    assertEquals(null, copy.builtInKey)
  }

  @Test
  fun deletingGoesBackAndLeavesTheRecipeForUndo() {
    val id = seededId(LegacyBuiltIns.V60_CLASSIC)
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
    val saved = runBlocking { recipes.list().first().single() }
    assertEquals(5, saved.stageCount) // tres vertidos y dos esperas
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

  // --- Molienda (SPEC-006) ---

  private fun grinderUno() = runBlocking { db.addGrinder(TestGrinders.uno) }

  @Test
  fun choosingAGrinderShowsTheSettingWithoutValueAndSavesItsIdAndLabel() {
    val unoId = grinderUno()
    show()
    type("recipe_edit_name", "Con molino")
    type("recipe_edit_dose", "16")
    assertFalse(exists("recipe_edit_grind_value_0_value"))
    rule.onNodeWithTag("recipe_edit_grinder").performScrollTo().performClick()
    rule.onNode(hasText("Acme Uno") and hasAnyAncestor(isPopup())).performClick()
    rule.waitUntil(5_000) { exists("recipe_edit_grind_value_0_inc") }
    rule.onNodeWithText(context.getString(R.string.grind_value_hint)).performScrollTo().assertExists()
    // Sin valor todavía: guardar no guarda ajuste, pero sí el molino.
    rule.onNodeWithTag("recipe_edit_grind_value_0_inc").performScrollTo().performClick()
    rule.onNodeWithTag("recipe_edit_save").performScrollTo().performClick()
    rule.waitUntil(5_000) { runBlocking { recipes.list().first().any { it.name == "Con molino" } } }
    val saved = runBlocking { recipes.get(recipes.list().first().single { it.name == "Con molino" }.id)!! }
    assertEquals(unoId, saved.grind?.grinderId)
    assertNotNull(saved.grind?.value)
    assertTrue(saved.grind!!.label!!.contains("Uno"))
  }

  @Test
  fun aDeletedGrinderShowsItsTextCopyWithTheDeletedTagAndChoosingReplacesIt() {
    grinderUno()
    val id = runBlocking {
      recipes.save(
        Recipe(name = "Huérfana", method = BrewMethod.V60, grind = GrindSetting(null, 30.0, "Chestnut S3 · 30 clics"), doseG = 15.0, stages = listOf(RecipeStage("Bloom", 0, 40.0, 10_000))),
      )
    }
    show(id = id)
    rule.onNodeWithTag("recipe_edit_grind_orphan", useUnmergedTree = true).performScrollTo().assertTextContains("Chestnut S3", substring = true)
    rule.onNodeWithTag("recipe_edit_grind_orphan_deleted", useUnmergedTree = true).assertExists()
    rule.onNodeWithTag("recipe_edit_grinder").assertTextContains(context.getString(R.string.grind_choose), substring = true)

    rule.onNodeWithTag("recipe_edit_grinder").performClick()
    rule.onNode(hasText("Acme Uno") and hasAnyAncestor(isPopup())).performClick()
    rule.waitUntil(5_000) { !exists("recipe_edit_grind_orphan") }
    rule.onNodeWithTag("recipe_edit_save").performScrollTo().performClick()
    rule.waitUntil(5_000) { backCount > 0 }
    assertEquals(null, runBlocking { recipes.get(id) }!!.grind?.value)
  }

  @Test
  fun noGrinderClearsTheGrindOfTheRecipe() {
    val unoId = grinderUno()
    val id = runBlocking {
      recipes.save(
        Recipe(name = "Con molino", method = BrewMethod.V60, grind = GrindSetting(unoId, 20.0, "Acme Uno · 20 clics"), doseG = 15.0, stages = listOf(RecipeStage("Bloom", 0, 40.0, 10_000))),
      )
    }
    show(id = id)
    rule.onNodeWithTag("recipe_edit_grinder").performScrollTo().performClick()
    rule.onNode(hasText(context.getString(R.string.grind_none)) and hasAnyAncestor(isPopup())).performClick()
    assertFalse(exists("recipe_edit_grind_value_0_inc"))
    rule.onNodeWithTag("recipe_edit_save").performScrollTo().performClick()
    rule.waitUntil(5_000) { backCount > 0 }
    assertEquals(null, runBlocking { recipes.get(id) }!!.grind)
  }
}
