package dev.josueamaya.pourline.ui.grinders

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import dev.josueamaya.pourline.R
import dev.josueamaya.pourline.grinder.GrindSetting
import dev.josueamaya.pourline.grinder.TestGrinders
import dev.josueamaya.pourline.grinder.addGrinder
import dev.josueamaya.pourline.history.BrewMethod
import dev.josueamaya.pourline.recipe.Recipe
import dev.josueamaya.pourline.recipe.RecipeStage
import dev.josueamaya.pourline.ui.TestApp
import dev.josueamaya.pourline.ui.TestAppDependencies
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class GrinderDetailScreenTest {
  @get:Rule
  val rule = createAndroidComposeRule<ComponentActivity>()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private lateinit var env: TestAppDependencies
  private lateinit var ui: GrindersUi

  @Before
  fun setUp() {
    env = TestAppDependencies(context)
    env.skipOnboarding()
    ui = GrindersUi(rule, env)
  }

  @After
  fun tearDown() = env.close()

  private fun openDetail(id: Long) {
    rule.setContent { TestApp(env) }
    ui.openGrinders()
    ui.waitFor("grinder_row_$id")
    ui.click("grinder_row_$id")
    ui.waitFor("grinder_detail_ruler")
  }

  private fun recipeUsing(id: Long, name: String) = runBlocking {
    env.recipes.save(
      Recipe(name = name, method = BrewMethod.V60, grind = GrindSetting(id, 20.0, "x"), doseG = 15.0, stages = listOf(RecipeStage("Bloom", 0, 40.0, 10_000))),
    )
  }

  @Test
  fun aPredefinedGrinderHasNoEditOrDeleteAndShowsItsSource() {
    env.seedGrinders()
    val id = runBlocking { env.grinders.catalog().first().single { it.model == "Uno" }.id }
    runBlocking { env.grinders.setOwned(id, true) }
    openDetail(id)

    assertFalse(ui.exists("grinder_detail_edit"))
    assertFalse(ui.exists("grinder_detail_delete"))
    assertTrue(ui.exists("grinder_attribution"))
    ui.node("grinder_attribution").assertTextContains("Acme Guide", substring = true)
    assertTrue(ui.exists("grinder_attribution_link_acme-uno"))
    assertTrue(ui.exists("grinder_detail_duplicate"))
    assertTrue(ui.exists("grinder_detail_unown"))
    assertTrue(ui.exists("grinder_detail_default"))
    ui.node("grinder_detail_scale").assertTextContains("40", substring = true)
  }

  @Test
  fun aPredefinedGrinderIHaveNotOffersIHaveItAndNoDefault() {
    env.seedGrinders()
    val id = runBlocking { env.grinders.catalog().first().single { it.model == "Dos" }.id }
    rule.setContent { TestApp(env) }
    ui.openGrinders()
    ui.click("grinders_add")
    ui.waitFor("grinder_catalog_acme-dos")
    ui.click("grinder_catalog_acme-dos")
    ui.waitFor("grinder_detail_own")
    assertFalse(ui.exists("grinder_detail_default"))
    assertFalse(ui.exists("grinder_detail_unown"))
    ui.click("grinder_detail_own")
    ui.waitFor("grinder_detail_unown")
    assertTrue(ui.exists("grinder_detail_default"))
    assertEquals(listOf(id), ui.owned().map { it.id })
  }

  @Test
  fun duplicatingOpensTheEditorWithACopyAndSavingLeavesItEditableInMyGrinders() {
    env.seedGrinders()
    val original = runBlocking { env.grinders.catalog().first().single { it.model == "Uno" } }
    runBlocking { env.grinders.setOwned(original.id, true) }
    openDetail(original.id)

    ui.click("grinder_detail_duplicate")
    ui.waitFor("grinder_edit_model")
    ui.node("grinder_edit_model").assertTextContains(env.grindTexts.copyName("Uno"))
    ui.click("grinder_edit_save")
    ui.waitFor("grinder_detail_duplicate") // vuelve al detalle del original

    val copy = ui.owned().single { !it.isPredefined }
    assertEquals(env.grindTexts.copyName("Uno"), copy.model)
    assertEquals("acme-uno", copy.duplicatedFromKey)
    // El original sigue sin "Editar".
    assertFalse(ui.exists("grinder_detail_edit"))
    rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
    ui.waitFor("grinder_row_${copy.id}")
    ui.click("grinder_row_${copy.id}")
    ui.waitFor("grinder_detail_edit")
    assertTrue(ui.exists("grinder_detail_delete"))
  }

  @Test
  fun anOwnGrinderCanBeEditedAndDeletingShowsHowManyRecipesUseIt() {
    val id = runBlocking { env.db.addGrinder(TestGrinders.uno) }
    recipeUsing(id, "R1")
    recipeUsing(id, "R2")
    openDetail(id)

    assertTrue(ui.exists("grinder_detail_edit"))
    ui.click("grinder_detail_delete")
    ui.waitFor("grinder_delete_dialog")
    rule.onNodeWithText("It is used in 2 recipes", substring = true).assertIsDisplayed()
  }

  @Test
  fun deletingAnUnusedGrinderSaysSoAndLeavesTheList() {
    val id = runBlocking { env.db.addGrinder(TestGrinders.uno) }
    openDetail(id)
    ui.click("grinder_detail_delete")
    ui.waitFor("grinder_delete_dialog")
    rule.onNodeWithText(context.getString(R.string.grinder_delete_unused)).assertIsDisplayed()
    ui.click("ant_modal_confirm")
    ui.waitFor("grinders_empty")
    assertEquals(emptyList<Long>(), ui.owned().map { it.id })
  }

  @Test
  fun removingTheDefaultFromMyGrindersClearsIt() {
    env.seedGrinders()
    val id = runBlocking { env.grinders.catalog().first().single { it.model == "Uno" }.id }
    runBlocking { env.grinders.setOwned(id, true) }
    env.grinderSettings.setDefaultGrinderId(id)
    openDetail(id)

    assertTrue(ui.exists("grinder_detail_is_default"))
    assertFalse(ui.exists("grinder_detail_default"))
    ui.click("grinder_detail_unown")
    rule.waitUntil(5_000) { env.grinderSettings.defaultGrinderId.value == null }
    assertNull(env.grinderSettings.defaultGrinderId.value)
    ui.waitFor("grinder_detail_own")
    assertTrue(ui.owned().isEmpty())
    assertFalse(rule.onAllNodesWithTagExists("grinder_detail_is_default"))
  }

  private fun androidx.compose.ui.test.junit4.ComposeTestRule.onAllNodesWithTagExists(tag: String) =
    onAllNodes(androidx.compose.ui.test.hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
}
