package dev.dotscale.ui.grinders

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import dev.dotscale.Tab
import dev.dotscale.grinder.GrindSetting
import dev.dotscale.grinder.TestGrinders
import dev.dotscale.grinder.addGrinder
import dev.dotscale.history.BrewMethod
import dev.dotscale.recipe.Recipe
import dev.dotscale.recipe.RecipeStage
import dev.dotscale.settings.ReferenceKey
import dev.dotscale.ui.TestApp
import dev.dotscale.ui.TestAppDependencies
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

/** "Ver en mis otros molinos": desde la tarjeta de Preparar y desde el editor de la receta abre el convertidor relleno. */
class GrindShortcutTest {
  @get:Rule
  val rule = createAndroidComposeRule<ComponentActivity>()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private val env = TestAppDependencies(context)
  private val ui = GrindersUi(rule, env)

  @After
  fun tearDown() = env.close()

  private fun recipe(grind: GrindSetting?) = runBlocking {
    env.recipes.save(Recipe(name = "Mi V60", method = BrewMethod.V60, grind = grind, doseG = 15.0, stages = listOf(RecipeStage("Bloom", 0, 40.0, 10_000))))
  }

  private fun assertConverterFilled(uno: Long, dos: Long) {
    ui.waitFor("converter")
    rule.waitUntil(5_000) { runCatching { ui.node("converter_source").assertTextContains("Acme Uno", substring = true) }.isSuccess }
    // Con el ajuste ya dado, hay una fila para Dos con el equivalente de 20 en Uno (V60): 28.
    ui.waitFor("converter_row_$dos")
    assertFalse(ui.exists("converter_row_$uno"))
    ui.node("converter_value_$dos", unmerged = true).assertTextContains("28", substring = true)
    assertFalse(ui.exists("converter_hint_value"))
  }

  @Test
  fun theCardShortcutOpensTheConverterWithTheSourceMethodAndSetting() {
    val uno = runBlocking { env.db.addGrinder(TestGrinders.uno) }
    val dos = runBlocking { env.db.addGrinder(TestGrinders.dos, 2_000L) }
    val id = recipe(GrindSetting(uno, 20.0, "x"))
    env.skipOnboarding()
    rule.setContent { TestApp(env) }
    rule.runOnUiThread { env.scaleSession.selectTarget(ReferenceKey.Recipe(id)) }
    ui.waitFor("grind_card_converter")
    ui.click("grind_card_converter")

    assertConverterFilled(uno, dos)
  }

  @Test
  fun theCardHasNoShortcutWithoutGrinderAndSetting() {
    runBlocking { env.db.addGrinder(TestGrinders.uno) }
    val id = recipe(null)
    env.skipOnboarding()
    rule.setContent { TestApp(env) }
    rule.runOnUiThread { env.scaleSession.selectTarget(ReferenceKey.Recipe(id)) }
    ui.waitFor("grind_card_value")
    assertFalse(ui.exists("grind_card_converter"))
  }

  @Test
  fun theRecipeEditorShortcutOpensTheConverterAndBackReturnsToTheEditor() {
    val uno = runBlocking { env.db.addGrinder(TestGrinders.uno) }
    val dos = runBlocking { env.db.addGrinder(TestGrinders.dos, 2_000L) }
    recipe(GrindSetting(uno, 20.0, "x"))
    env.skipOnboarding()
    rule.setContent { TestApp(env) }
    rule.onNodeWithTag(Tab.RECIPES.testTag).performClick()
    rule.waitUntil(5_000) { rule.onAllNodesWithText("Mi V60").fetchSemanticsNodes().isNotEmpty() }
    rule.onNodeWithText("Mi V60").performClick()
    ui.waitFor("recipe_edit_grind_converter")
    ui.click("recipe_edit_grind_converter")

    assertConverterFilled(uno, dos)
    rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
    ui.waitFor("recipe_edit_save")
  }

  @Test
  fun theRecipeEditorHasNoShortcutWithoutASettingOrWithADeletedGrinder() {
    val uno = runBlocking { env.db.addGrinder(TestGrinders.uno) }
    recipe(GrindSetting(uno, null, "Uno"))
    env.skipOnboarding()
    rule.setContent { TestApp(env) }
    rule.onNodeWithTag(Tab.RECIPES.testTag).performClick()
    rule.waitUntil(5_000) { rule.onAllNodesWithText("Mi V60").fetchSemanticsNodes().isNotEmpty() }
    rule.onNodeWithText("Mi V60").performClick()
    ui.waitFor("recipe_edit_grinder")
    assertFalse(ui.exists("recipe_edit_grind_converter"))
  }
}
