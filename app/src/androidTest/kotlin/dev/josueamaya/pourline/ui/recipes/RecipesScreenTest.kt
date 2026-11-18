package dev.josueamaya.pourline.ui.recipes

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import dev.josueamaya.pourline.R
import dev.josueamaya.pourline.history.HistoryDatabase
import dev.josueamaya.pourline.recipe.BuiltInRecipes
import dev.josueamaya.pourline.recipe.RecipeRepository
import dev.josueamaya.pourline.recipe.ResourcesRecipeTexts
import dev.josueamaya.pourline.ui.theme.PourlineTheme
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
  private var activeId by mutableStateOf<Long?>(null)

  @Before
  fun setUp() {
    db = HistoryDatabase.inMemory(context, texts)
    repository = RecipeRepository(db, texts)
    val viewModel = RecipesViewModel(repository)
    rule.setContent {
      PourlineTheme(darkTheme = false) {
        RecipesScreen(viewModel, onBack = {}, onOpenRecipe = { opened = it }, onCreateRecipe = { created++ }, activeRecipeId = activeId)
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
    // "15 g · 250 g · 1:16,7 · 5 pasos" con los números del idioma actual.
    val summary = context.getString(
      R.string.recipe_summary,
      "15",
      "250",
      context.getString(R.string.scale_ratio_value, 250.0 / 15),
      context.resources.getQuantityString(R.plurals.recipe_stage_count, 5, 5),
    )
    rule.onNodeWithText(summary).assertIsDisplayed()
  }

  @Test
  fun theActiveRecipeIsTagged() {
    waitForRows(2)
    rule.onAllNodesWithTag("recipe_active_tag", useUnmergedTree = true).assertCountEquals(0)

    val second = ids()[1]
    rule.runOnUiThread { activeId = second }

    rule.waitUntil(5_000) { rule.onAllNodesWithTag("recipe_active_tag", useUnmergedTree = true).fetchSemanticsNodes().size == 1 }
    rule.onNodeWithText(context.getString(R.string.recipe_active_tag)).assertIsDisplayed()
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
