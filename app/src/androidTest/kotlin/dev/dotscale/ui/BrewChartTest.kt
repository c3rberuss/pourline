package dev.dotscale.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.test.platform.app.InstrumentationRegistry
import dev.dotscale.R
import dev.dotscale.history.BrewRepository
import dev.dotscale.history.HistoryDatabase
import dev.dotscale.recipe.BuiltInRecipes
import dev.dotscale.recipe.RecipeRepository
import dev.dotscale.recipe.ResourcesRecipeTexts
import dev.dotscale.sim.SimulatedScaleConnection
import dev.dotscale.ui.scale.MemoryReferenceStore
import dev.dotscale.ui.scale.RepositoryTargetReferences
import dev.dotscale.ui.scale.ScaleScreen
import dev.dotscale.ui.scale.ScaleViewModel
import dev.dotscale.ui.scale.SilentNotifier
import dev.dotscale.ui.scale.TargetPickerViewModel
import dev.dotscale.ui.theme.DotScaleTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Báscula simulada y sin permisos BLE: no hace falta hardware. Localizadores por `testTag` y textos. */
class BrewChartTest {
  @get:Rule
  val rule = createComposeRule()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private val texts = ResourcesRecipeTexts(context)
  private val connection = SimulatedScaleConnection()
  private lateinit var db: HistoryDatabase
  private var managed = 0

  @Before
  fun setUp() {
    db = HistoryDatabase.inMemory(context, texts)
    val recipes = RecipeRepository(db, texts)
    val brews = BrewRepository(db)
    val viewModel = ScaleViewModel(
      connection,
      MutableStateFlow(60),
      saver = { _, _ -> 1L },
      applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
      references = MemoryReferenceStore(),
      targets = RepositoryTargetReferences(recipes, brews),
      notifier = SilentNotifier,
    )
    val picker = TargetPickerViewModel(recipes, brews)
    rule.setContent {
      DotScaleTheme(darkTheme = false) {
        ScaleScreen(
          viewModel,
          picker,
          title = "Test",
          onBack = {},
          onOpenSettings = {},
          onOpenHistory = {},
          onAddDetails = {},
          onManageRecipes = { managed++ },
        )
      }
    }
  }

  @After
  fun tearDown() {
    connection.disconnect()
    db.close()
  }

  private fun exists(tag: String) = rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

  private fun pickV60() {
    rule.onNodeWithTag("target_chip").performScrollTo().performClick()
    rule.waitUntil(5_000) { rule.onAllNodesWithTag("recipe_row").fetchSemanticsNodes().size == 2 }
    // La lista va ordenada por nombre: se elige por texto, no por posición.
    rule.onNodeWithText(texts.builtInName(BuiltInRecipes.V60_CLASSIC)).performClick()
    rule.waitUntil(5_000) { exists("target_armed_stage") }
  }

  private fun startStablePour() {
    rule.onNodeWithText(context.getString(R.string.sim_pour_stable)).performScrollTo().performClick()
    rule.waitUntil(5_000) { rule.onAllNodesWithTag("brew_chart").fetchSemanticsNodes().isNotEmpty() }
  }

  @Test
  fun chartAppearsWhenAPourIsSimulated() {
    rule.onNodeWithText(context.getString(R.string.brew_chart_empty)).assertIsDisplayed()
    startStablePour()
    rule.onNodeWithTag("brew_chart").assertIsDisplayed()
    rule.onNodeWithTag("brew_level").assertIsDisplayed()
  }

  @Test
  fun finishShowsTheSummary() {
    startStablePour()
    rule.onNodeWithTag("brew_finish").performClick()
    rule.onNodeWithTag("brew_summary").performScrollTo().assertIsDisplayed()
    rule.onNodeWithText(context.getString(R.string.brew_reason_manual)).assertIsDisplayed()
  }

  @Test
  fun aPourAfterFinishingShowsTheIgnoredNoticeAndKeepsTheSummary() {
    startStablePour()
    // El guion del simulador sigue vertiendo (bloom) tras "Terminar": sin tara ni reinicio se ignora.
    rule.onNodeWithTag("brew_finish").performClick()
    val notice = context.getString(R.string.brew_chart_ignored)
    rule.waitUntil(5_000) { rule.onAllNodesWithText(notice).fetchSemanticsNodes().isNotEmpty() }
    rule.onNodeWithText(notice).performScrollTo().assertIsDisplayed()
    rule.onNodeWithTag("brew_summary").assertExists()
  }

  // --- Objetivo (SPEC-003) ---

  @Test
  fun withoutATargetTheChipSaysSoAndTheSimulatorButtonsAreDisabled() {
    rule.onNodeWithText(context.getString(R.string.target_chip_none)).assertIsDisplayed()
    rule.onNodeWithTag("sim_follow_target").performScrollTo().assertIsNotEnabled()
    rule.onNodeWithTag("sim_follow_deviated").assertIsNotEnabled()
    assert(!exists("target_armed_stage"))
  }

  @Test
  fun pickingARecipeInTwoTapsShowsItsFirstStage() {
    pickV60()

    val name = texts.builtInName(BuiltInRecipes.V60_CLASSIC)
    rule.onNodeWithTag("target_chip").performScrollTo()
    rule.onNodeWithText(context.getString(R.string.target_chip, name)).assertIsDisplayed()
    rule.onNodeWithTag("target_armed_stage").performScrollTo().assertIsDisplayed()
    // El selector se cierra al elegir.
    rule.waitUntil(5_000) { !exists("target_picker") }
    rule.onNodeWithText(context.getString(R.string.brew_chart_empty)).assertIsDisplayed()
  }

  @Test
  fun manageRecipesClosesThePickerAndOpensRecipes() {
    rule.onNodeWithTag("target_chip").performScrollTo().performClick()
    rule.onNodeWithTag("target_picker_manage").performClick()
    rule.waitUntil(5_000) { managed == 1 }
    rule.waitUntil(5_000) { !exists("target_picker") }
  }

  @Test
  fun followingTheTargetShowsTheGuideAndFinishingShowsTheFit() {
    pickV60()
    rule.onNodeWithTag("sim_follow_target").performScrollTo().performClick()
    rule.waitUntil(10_000) { exists("target_guide") }
    rule.onNodeWithTag("target_guide_goal").assertIsDisplayed()
    rule.onNodeWithTag("target_guide_deviation").assertIsDisplayed()
    rule.onNodeWithTag("target_chip").performScrollTo().assertIsNotEnabled() // en `Active` no se cambia

    rule.onNodeWithTag("brew_finish").performScrollTo().performClick()
    rule.onNodeWithTag("fit_block").performScrollTo().assertIsDisplayed()
    rule.onNodeWithTag("fit_score").assertIsDisplayed()
    rule.onNodeWithTag("fit_reference").assertIsDisplayed()
  }
}
