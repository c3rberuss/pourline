package dev.dotscale.ui.session

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import dev.dotscale.Tab
import dev.dotscale.grinder.GrindSetting
import dev.dotscale.grinder.TestGrinders
import dev.dotscale.grinder.addGrinder
import dev.dotscale.history.BrewFilter
import dev.dotscale.history.BrewMethod
import dev.dotscale.recipe.Recipe
import dev.dotscale.recipe.RecipeStage
import dev.dotscale.settings.ExperienceMode
import dev.dotscale.settings.ReferenceKey
import dev.dotscale.ui.TestApp
import dev.dotscale.ui.TestAppDependencies
import dev.dotscale.ui.grinders.GrindersUi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Una sesión completa con "Moler con" en otro molino: la preparación guarda el molino elegido y el ajuste convertido (§3.8 7). */
class GrindBrewFlowTest {
  @get:Rule
  val rule = createAndroidComposeRule<ComponentActivity>()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private val env = TestAppDependencies(context)
  private val driver = SessionDriver(rule, env)

  @After
  fun tearDown() = env.close()

  @Test
  fun theBrewSavesTheChosenGrinderTheConvertedSettingAndItsCopy() {
    val unoId = runBlocking { env.db.addGrinder(TestGrinders.uno) }
    val tresId = runBlocking { env.db.addGrinder(TestGrinders.tres, 2_000L) }
    val recipeId = runBlocking {
      env.recipes.save(
        Recipe(
          name = "Mi V60",
          method = BrewMethod.V60,
          grind = GrindSetting(unoId, 30.0, "Uno · 30 clics"),
          doseG = 15.0,
          stages = listOf(RecipeStage("Bloom", 0, 45.0, 10_000), RecipeStage("Main", 30_000, 250.0, 20_000)),
        ),
      )
    }
    env.skipOnboarding(ExperienceMode.EXPERT)
    rule.setContent { TestApp(env) }
    driver.connect()
    rule.runOnUiThread { env.scaleSession.selectTarget(ReferenceKey.Recipe(recipeId)) }
    driver.waitFor("grind_card_value")

    GrindersUi(rule, env).choose("grind_card_grinder", "Acme Tres")
    // Uno V60 16–24 en 30 está a +175 %; Tres V60 60–100 → 60 + 1,75 × 40 = 130.
    rule.waitUntil(5_000) { env.grindSelection.current()?.grinderId == tresId }
    assertEquals(130.0, env.grindSelection.current()!!.value!!, 0.0)

    driver.reachPourStep()
    driver.pourFollowingTarget()
    driver.finishFromButton()

    rule.waitUntil(10_000) { runBlocking { env.brews.list(BrewFilter()).first().isNotEmpty() } }
    val brewId = runBlocking { env.brews.list(BrewFilter()).first().single().id }
    val saved = runBlocking { env.brews.get(brewId) }!!.entity
    assertEquals(tresId, saved.grinderId)
    assertEquals(130.0, saved.grindValue!!, 0.0)
    assertTrue(saved.grindLabel!!.startsWith("Tres"))
    assertNotNull(saved.method)

    // El detalle de la preparación muestra el molino elegido con su ajuste.
    driver.node("summary_done").performClick()
    driver.waitFor(Tab.HISTORY.testTag)
    driver.node(Tab.HISTORY.testTag).performClick()
    driver.waitFor("history_row")
    driver.node("history_row").performClick()
    rule.waitUntil(10_000) { rule.onAllNodesWithTag("brew_detail_grind", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    rule.onNodeWithTag("brew_detail_grind", useUnmergedTree = true).assertTextContains("Tres", substring = true)
  }
}
