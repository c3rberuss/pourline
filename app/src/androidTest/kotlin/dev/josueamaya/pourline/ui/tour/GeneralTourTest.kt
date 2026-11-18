package dev.josueamaya.pourline.ui.tour

import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import dev.josueamaya.pourline.R
import dev.josueamaya.pourline.grinder.GrindSetting
import dev.josueamaya.pourline.grinder.TestGrinders
import dev.josueamaya.pourline.grinder.addGrinder
import dev.josueamaya.pourline.history.BrewMethod
import dev.josueamaya.pourline.recipe.Recipe
import dev.josueamaya.pourline.recipe.RecipeStage
import dev.josueamaya.pourline.settings.ExperienceMode
import dev.josueamaya.pourline.settings.ReferenceKey
import dev.josueamaya.pourline.ui.TestApp
import dev.josueamaya.pourline.ui.TestAppDependencies
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** El recorrido general de Preparar y el de Ajustes, con la aplicación entera (`TestApp`) y Room en memoria. */
class GeneralTourTest {
  @get:Rule
  val rule = createAndroidComposeRule<ComponentActivity>()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private val env = TestAppDependencies(context)

  /** Los pasos del recorrido general en Básico: todos menos la regla de molienda. */
  private val basicIds = Tours.prepare.steps.filterNot { it.expertOnly }.map { it.id }.toSet()

  @After
  fun tearDown() = env.close()

  private fun show() = rule.setContent { TestApp(env) }

  private fun exists(tag: String) = rule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

  private fun waitFor(tag: String) = rule.waitUntil(5_000) { exists(tag) }

  /** Avanza hasta el contador [counter]. */
  private fun goTo(counter: String) {
    var guard = 0
    while (rule.tourCounter() != counter && guard++ < 30) {
      val before = rule.tourCounter()
      rule.tapTourNext()
      rule.waitUntil(5_000) { rule.tourCounter() != before }
    }
    assertEquals(counter, rule.tourCounter())
  }

  private fun hintShown() =
    rule.onAllNodesWithText(context.getString(R.string.tour_skipped_hint)).fetchSemanticsNodes().isNotEmpty()

  private fun backPressed() = rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }

  @Test
  fun theGeneralTourFollowsTheFirstLaunchAndDoesNotReturnAfterARestart() {
    env.skipTours(except = listOf(Tours.prepare))
    show()
    waitFor("onboarding_skip")
    rule.onNodeWithTag("onboarding_skip").performClick()

    rule.waitForTour("1/13", env.tourSettings.seen.value)
    rule.completeTour()
    assertEquals(basicIds, Tours.prepare.steps.map { it.id }.filter { it in env.tourSettings.seen.value }.toSet())

    rule.recreateWith(env)
    waitFor("prepare_start")
    rule.assertNoTourFor()
  }

  @Test
  fun skipClosesTheGeneralTourAndShowsTheHint() {
    env.skipOnboarding(showTours = listOf(Tours.prepare))
    show()
    rule.waitForTour("1/13")

    rule.tapTourSkip()

    rule.waitUntil(5_000) { !rule.tourVisible() }
    rule.waitUntil(5_000) { hintShown() }
    assertEquals(basicIds, Tours.prepare.steps.map { it.id }.filter { it in env.tourSettings.seen.value }.toSet())
  }

  @Test
  fun previousGoesBackAndBackOnTheFirstStepClosesWithTheHint() {
    env.skipOnboarding(showTours = listOf(Tours.prepare))
    show()
    rule.waitForTour("1/13")

    rule.tapTourNext()
    rule.waitForTour("2/13")
    rule.tapTourPrev()
    rule.waitForTour("1/13")
    backPressed()

    rule.waitUntil(5_000) { !rule.tourVisible() }
    rule.waitUntil(5_000) { hintShown() }

    // Pasar a Herramientas y volver: no vuelve a salir (Atrás lo marcó como visto).
    rule.onNodeWithTag("tab_tools").performClick()
    waitFor("tools")
    rule.onNodeWithTag("tab_prepare").performClick()
    waitFor("prepare_start")
    rule.assertNoTourFor()
  }

  @Test
  fun afterTheBasicTourExpertShowsOnlyTheRuler() {
    val uno = runBlocking { env.db.addGrinder(TestGrinders.uno) }
    val recipeId = runBlocking {
      env.recipes.save(
        Recipe(
          name = "Mi V60",
          method = BrewMethod.V60,
          grind = GrindSetting(uno, 20.0, "x"),
          doseG = 15.0,
          stages = listOf(RecipeStage("Bloom", 0, 40.0, 10_000)),
        ),
      )
    }
    env.skipOnboarding(ExperienceMode.BASIC, showTours = listOf(Tours.prepare))
    show()
    rule.runOnUiThread { env.scaleSession.selectTarget(ReferenceKey.Recipe(recipeId)) }
    waitFor("grind_card")
    rule.waitForTour()

    rule.completeTour()
    assertTrue("prepare.grind_ruler" !in env.tourSettings.seen.value)

    // Pasar a Experto en Ajustes y volver.
    rule.onNodeWithTag("prepare_settings").performClick()
    waitFor("settings_mode")
    rule.onNodeWithText(context.getString(R.string.mode_expert)).performClick()
    backPressed()
    waitFor("prepare_start")

    rule.waitForTour("1/1")
    assertEquals(context.getString(R.string.tour_prepare_grind_ruler_title), rule.textOf("tour_title"))
  }

  @Test
  fun replayInSettingsGoesBackToPrepareFromStepOne() {
    env.skipOnboarding()
    show()
    waitFor("prepare_settings")
    rule.assertNoTourFor(800)
    rule.onNodeWithTag("prepare_settings").performClick()
    waitFor("settings_tour_replay")

    rule.onNodeWithTag("settings_tour_replay").performScrollTo().performClick()

    rule.waitForTour("1/13")
    waitFor("prepare_start")
  }

  @Test
  fun tappingTheHighlightedRecipeDoesNotOpenThePicker() {
    env.skipOnboarding(showTours = listOf(Tours.prepare))
    show()
    rule.waitForTour("1/13")
    goTo("3/13")
    assertEquals(context.getString(R.string.tour_prepare_recipe_title), rule.textOf("tour_title"))

    rule.onNodeWithTag("prepare_recipe").performClick()

    rule.waitForIdle()
    assertTrue(!exists("target_picker"))
    assertEquals("3/13", rule.tourCounter())
  }

  @Test
  fun theTabBarStepsHighlightTheTabs() {
    env.skipOnboarding(showTours = listOf(Tours.prepare))
    show()
    rule.waitForTour("1/13")
    goTo("10/13")
    assertEquals(context.getString(R.string.tour_prepare_tab_coffees_title), rule.textOf("tour_title"))

    // El hueco deja ver la pestaña resaltada y el velo oscurece las demás.
    val inside = luminance("tab_coffees")
    val outside = luminance("tab_history")
    assertTrue("El hueco no aclara la pestaña: dentro=$inside fuera=$outside", inside > outside + 0.05f)
    // El globo queda por encima de la barra.
    val balloon = rule.onNodeWithTag("tour_balloon").fetchSemanticsNode().boundsInRoot
    val tab = rule.onNodeWithTag("tab_coffees").fetchSemanticsNode().boundsInRoot
    assertTrue("globo $balloon no queda sobre la pestaña $tab", balloon.bottom <= tab.top)
  }

  @Test
  fun theSettingsTourHasSixStepsAndTheReplayRow() {
    env.skipOnboarding(showTours = listOf(Tours.settings))
    show()
    waitFor("prepare_settings")
    rule.onNodeWithTag("prepare_settings").performClick()

    rule.waitForTour("1/6")
    assertEquals(context.getString(R.string.tour_settings_mode_title), rule.textOf("tour_title"))
    goTo("6/6")
    assertEquals(context.getString(R.string.tour_settings_replay_title), rule.textOf("tour_title"))
    rule.completeTour()
    assertTrue(env.tourSettings.seen.value.containsAll(Tours.settings.steps.map { it.id }))
  }

  @Test
  fun rotationKeepsTheCurrentStep() {
    env.skipOnboarding(showTours = listOf(Tours.prepare))
    // `recreate()` más un `setContent` tardío no restaura `rememberSaveable`; el probador de restauración sí.
    val tester = StateRestorationTester(rule)
    tester.setContent { TestApp(env) }
    rule.waitForTour("1/13")
    goTo("3/13")

    tester.emulateSavedInstanceStateRestore()

    rule.waitForTour("3/13")
  }

  /** La luminancia del píxel de la esquina izquierda del nodo [tag], medio de alto: fondo de la pestaña, con o sin velo. */
  private fun luminance(tag: String): Float {
    val image = rule.onNodeWithTag(tag).captureToImage().toPixelMap()
    val color: Color = image[3, image.height / 2].convert(ColorSpaces.Srgb)
    return 0.2126f * color.red + 0.7152f * color.green + 0.0722f * color.blue
  }
}
