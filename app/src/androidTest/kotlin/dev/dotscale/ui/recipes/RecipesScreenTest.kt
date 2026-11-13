package dev.dotscale.ui.recipes

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import dev.dotscale.R
import dev.dotscale.history.HistoryDatabase
import dev.dotscale.recipe.BuiltInRecipes
import dev.dotscale.recipe.RecipeRepository
import dev.dotscale.recipe.ResourcesRecipeTexts
import dev.dotscale.ui.theme.DotScaleTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class RecipesScreenTest {
  @get:Rule
  val rule = createComposeRule()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private val texts = ResourcesRecipeTexts(context)
  private lateinit var db: HistoryDatabase
  private lateinit var repository: RecipeRepository
  private var opened: Long? = null
  private var created = 0

  @Before
  fun setUp() {
    db = HistoryDatabase.inMemory(context, texts)
    repository = RecipeRepository(db, texts)
    val viewModel = RecipesViewModel(repository)
    rule.setContent {
      DotScaleTheme(darkTheme = false) {
        RecipesScreen(viewModel, onBack = {}, onOpenRecipe = { opened = it }, onCreateRecipe = { created++ })
      }
    }
  }

  @After
  fun tearDown() = db.close()

  private fun rows() = rule.onAllNodesWithTag("recipe_row")

  private fun waitForRows(count: Int) = rule.waitUntil(5_000) { rows().fetchSemanticsNodes().size == count }

  private fun ids() = runBlocking { repository.list().first().map { it.id } }

  @Test
  fun theTwoBuiltInsShowTheirNamesSummaryAndTag() {
    waitForRows(2)
    rows().assertCountEquals(2)
    rule.onAllNodesWithTag("recipe_builtin_tag", useUnmergedTree = true).assertCountEquals(2)
    rule.onNodeWithText(texts.builtInName(BuiltInRecipes.V60_CLASSIC)).assertIsDisplayed()
    rule.onNodeWithText(texts.builtInName(BuiltInRecipes.FOUR_SIX)).assertIsDisplayed()
    // "15 g · 250 g · 1:16,7 · 3 etapas" con los números del idioma actual.
    val summary = context.getString(
      R.string.recipe_summary,
      "15",
      "250",
      context.getString(R.string.scale_ratio_value, 250.0 / 15),
      context.resources.getQuantityString(R.plurals.recipe_stage_count, 3, 3),
    )
    rule.onNodeWithText(summary).assertIsDisplayed()
  }

  @Test
  fun tappingARowOpensTheRecipe() {
    waitForRows(2)
    val first = ids().first()
    rows()[0].performClick()
    assertEquals(first, opened)
  }

  @Test
  fun thePlusButtonCreatesARecipe() {
    waitForRows(2)
    rule.onNodeWithTag("recipes_add").performClick()
    assertEquals(1, created)
  }

  @Test
  fun withoutRecipesTheEmptyStateOffersToCreateOne() {
    waitForRows(2)
    val all = ids()
    runBlocking { all.forEach { repository.delete(it) } }
    waitForRows(0)
    rule.onNodeWithText(context.getString(R.string.recipe_empty)).assertIsDisplayed()
    rule.onNodeWithTag("recipes_empty_add").performClick()
    assertEquals(1, created)
  }

  @Test
  fun deletingShowsAMessageAndUndoBringsTheSameRecipeBack() {
    waitForRows(2)
    val id = ids().first()
    runBlocking { repository.delete(id) }
    waitForRows(1)

    val undo = context.getString(R.string.recipe_undo)
    rule.waitUntil(5_000) { rule.onAllNodesWithText(undo).fetchSemanticsNodes().isNotEmpty() }
    rule.onNodeWithText(context.getString(R.string.recipe_deleted)).assertIsDisplayed()
    rule.onNodeWithText(undo).performClick()

    waitForRows(2)
    assertNotNull(runBlocking { repository.get(id) })
    assertNull(repository.pendingUndo.value)
  }
}
