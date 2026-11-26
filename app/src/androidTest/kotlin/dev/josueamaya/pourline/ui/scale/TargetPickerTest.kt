package dev.josueamaya.pourline.ui.scale

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import dev.josueamaya.pourline.R
import dev.josueamaya.pourline.history.BrewListItem
import dev.josueamaya.pourline.history.BrewMethod
import dev.josueamaya.pourline.recipe.RecipeListItem
import dev.josueamaya.pourline.settings.ReferenceKey
import dev.josueamaya.pourline.ui.theme.PourlineTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** El selector es sin estado: se prueba con listas fijas y sin base de datos. */
class TargetPickerTest {
  @get:Rule
  val rule = createComposeRule()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext

  private val recipes = listOf(
    RecipeListItem(1, "V60 clásica", BrewMethod.V60, null, 15.0, 250.0, 3, totalDurationMs = 115_000),
    RecipeListItem(2, "Mi receta", BrewMethod.CHEMEX, null, 30.0, 500.0, 4, totalDurationMs = 210_000),
  )

  private fun brew(id: Long, name: String, waterG: Double) = BrewListItem(
    id = id,
    startedAtEpochMs = 1_700_000_000_000,
    name = name,
    coffee = null,
    totalTimeMs = 120_000,
    totalWaterG = waterG,
    finalRatio = null,
    stabilityScore = null,
    stabilityLevel = null,
    unit = dev.josueamaya.pourline.protocol.WeightUnit.GRAMS,
    simulated = false,
    preview = List(48) { 0f },
    fitScore = null,
    rating = null,
  )

  private val brews = listOf(brew(10, "Mañana", 250.0), brew(11, "Vacía", 0.0))

  private val selected = mutableListOf<ReferenceKey>()
  private var cleared = 0
  private var managed = 0
  private var closed = 0

  private fun show(
    recipes: List<RecipeListItem> = this.recipes,
    brews: List<BrewListItem> = this.brews,
    active: ReferenceKey? = null,
    showBrews: Boolean = true,
  ) = rule.setContent {
    PourlineTheme(darkTheme = false) {
      TargetPicker(
        recipes = recipes,
        brews = brews,
        active = active,
        onSelect = { selected += it },
        onClear = { cleared++ },
        onManageRecipes = { managed++ },
        onClose = { closed++ },
        showBrews = showBrews,
      )
    }
  }

  private fun tab(label: Int) = rule.onNodeWithText(context.getString(label))

  @Test
  fun withoutBrewsThereAreOnlyRecipesAndNoTabs() {
    show(showBrews = false)
    rule.onAllNodesWithTag("target_picker_tabs").assertCountEquals(0)
    rule.onNodeWithText("V60 clásica").assertIsDisplayed()
    rule.onNodeWithText("Mi receta").assertIsDisplayed()
    rule.onAllNodesWithText("Mañana").assertCountEquals(0)
    rule.onNodeWithText("Mi receta").performClick()
    assertEquals(listOf<ReferenceKey>(ReferenceKey.Recipe(2)), selected)
  }

  @Test
  fun opensOnRecipesAndShowsBothTabs() {
    show()
    tab(R.string.target_picker_recipes).assertIsDisplayed()
    tab(R.string.target_picker_history).assertIsDisplayed()
    rule.onAllNodesWithTag("recipe_row").assertCountEquals(2)
    rule.onAllNodesWithTag("history_row").assertCountEquals(0)
  }

  @Test
  fun aRecipeRowShowsItsTotalTime() {
    show()
    rule.onNode(hasText("1:55", substring = true)).assertIsDisplayed()
    rule.onNode(hasText("3:30", substring = true)).assertIsDisplayed()
    rule.onNode(hasContentDescription(context.getString(R.string.recipe_total_time_description, "3:30"), substring = true)).assertIsDisplayed()
  }

  @Test
  fun tappingARecipeSelectsIt() {
    show()
    rule.onAllNodesWithTag("recipe_row")[1].performClick()
    assertEquals(listOf<ReferenceKey>(ReferenceKey.Recipe(2)), selected)
  }

  @Test
  fun aBrewTakesTheHistoryTabAndOneTap() {
    show()
    tab(R.string.target_picker_history).performClick()
    rule.onAllNodesWithTag("recipe_row").assertCountEquals(0)
    rule.onAllNodesWithTag("history_row").assertCountEquals(2)
    rule.onAllNodesWithTag("history_row")[0].performClick()
    assertEquals(listOf<ReferenceKey>(ReferenceKey.Brew(10)), selected)
  }

  @Test
  fun aBrewWithoutWaterIsDisabled() {
    show()
    tab(R.string.target_picker_history).performClick()
    rule.onAllNodesWithTag("history_row")[1].performClick()
    assertTrue(selected.isEmpty())
  }

  @Test
  fun theActiveRecipeIsMarked() {
    show(active = ReferenceKey.Recipe(2))
    rule.onAllNodesWithTag("recipe_active_tag", useUnmergedTree = true).assertCountEquals(1)
  }

  @Test
  fun removeTargetOnlyAppearsWithAnActiveTarget() {
    show()
    rule.onAllNodesWithTag("target_picker_clear").assertCountEquals(0)
  }

  @Test
  fun removeTargetClears() {
    show(active = ReferenceKey.Recipe(1))
    rule.onNodeWithTag("target_picker_clear").performClick()
    assertEquals(1, cleared)
  }

  @Test
  fun manageRecipesIsAlwaysThere() {
    show()
    rule.onNodeWithTag("target_picker_manage").performClick()
    assertEquals(1, managed)
  }

  @Test
  fun emptyListsShowTheirMessage() {
    show(recipes = emptyList(), brews = emptyList())
    rule.onNodeWithText(context.getString(R.string.target_picker_empty_recipes)).assertIsDisplayed()
    tab(R.string.target_picker_history).performClick()
    rule.onNodeWithText(context.getString(R.string.target_picker_empty_history)).assertIsDisplayed()
  }
}
