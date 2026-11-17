package dev.josueamaya.pourline.ui.tour

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import dev.josueamaya.pourline.R
import dev.josueamaya.pourline.grinder.GrindSetting
import dev.josueamaya.pourline.grinder.TestGrinders
import dev.josueamaya.pourline.grinder.addGrinder
import dev.josueamaya.pourline.history.BrewMethod
import dev.josueamaya.pourline.recipe.Recipe
import dev.josueamaya.pourline.recipe.RecipeStage
import dev.josueamaya.pourline.settings.ReferenceKey
import dev.josueamaya.pourline.ui.TestApp
import dev.josueamaya.pourline.ui.TestAppDependencies
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Los recorridos de Herramientas, la calculadora de ratio, el generador 4:6 y el convertidor de molienda. */
class ToolsTourTest {
  @get:Rule
  val rule = createAndroidComposeRule<ComponentActivity>()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private val env = TestAppDependencies(context)

  @After
  fun tearDown() = env.close()

  private fun show() = rule.setContent { TestApp(env) }

  private fun exists(tag: String) = rule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

  private fun waitFor(tag: String) = rule.waitUntil(5_000) { exists(tag) }

  private fun openTools() {
    waitFor("tab_tools")
    rule.onNodeWithTag("tab_tools").performClick()
    waitFor("tools")
  }

  private fun goTo(counter: String) {
    var guard = 0
    while (rule.tourCounter() != counter && guard++ < 30) {
      val before = rule.tourCounter()
      rule.tapTourNext()
      rule.waitUntil(5_000) { rule.tourCounter() != before }
    }
    assertEquals(counter, rule.tourCounter())
  }

  private fun titleIs(resource: Int) = assertEquals(context.getString(resource), rule.textOf("tour_title"))

  @Test
  fun theToolsTourShowsItsThreeRows() {
    env.skipOnboarding(showTours = listOf(Tours.tools))
    show()
    openTools()

    rule.waitForTour("1/3", env.tourSettings.seen.value)
    titleIs(R.string.tour_tools_ratio_title)
    goTo("3/3")
    titleIs(R.string.tour_tools_converter_title)
    rule.completeTour()
  }

  @Test
  fun theRatioTourShowsItsTwoFields() {
    env.skipOnboarding(showTours = listOf(Tours.ratioCalculator))
    show()
    openTools()
    rule.onNodeWithTag("tools_ratio").performClick()
    waitFor("ratio_calculator")

    rule.waitForTour("1/2", env.tourSettings.seen.value)
    titleIs(R.string.tour_ratio_calculator_fields_title)
    goTo("2/2")
    titleIs(R.string.tour_ratio_calculator_validation_title)
  }

  @Test
  fun theFourSixTourShowsItsFourSteps() {
    env.skipOnboarding(showTours = listOf(Tours.fourSix))
    show()
    openTools()
    rule.onNodeWithTag("tools_four_six").performClick()
    waitFor("four_six")

    rule.waitForTour("1/4", env.tourSettings.seen.value)
    titleIs(R.string.tour_four_six_dose_title)
    goTo("2/4")
    titleIs(R.string.tour_four_six_sliders_title)
    // Los dos sliders quedan dentro del hueco: el ancla es la columna que los envuelve.
    val levels = rule.onNodeWithTag("four_six_levels").fetchSemanticsNode().boundsInRoot
    listOf("four_six_taste", "four_six_strength").forEach { slider ->
      val bounds: Rect = rule.onNodeWithTag(slider).fetchSemanticsNode().boundsInRoot
      // El slider se sale un poco por los lados (el pulgar); en vertical debe caber entero.
      assertTrue("$slider $bounds no cabe en el hueco $levels", bounds.top >= levels.top && bounds.bottom <= levels.bottom)
    }
    goTo("4/4")
    titleIs(R.string.tour_four_six_save_title)
  }

  @Test
  fun theConverterTourWithoutASourceShowsOnlyTheSource() {
    env.skipOnboarding(showTours = listOf(Tours.converter))
    show()
    openTools()
    rule.onNodeWithTag("tools_converter").performClick()
    waitFor("converter")

    rule.waitForTour("1/1", env.tourSettings.seen.value)
    titleIs(R.string.tour_converter_source_title)
    rule.completeTour()
    assertTrue("converter.rows" !in env.tourSettings.seen.value)
  }

  @Test
  fun theRowsStepShowsInALaterOpening() {
    val uno = runBlocking { env.db.addGrinder(TestGrinders.uno) }
    val dos = runBlocking { env.db.addGrinder(TestGrinders.dos, 2_000L) }
    val recipeId = runBlocking {
      env.recipes.save(
        Recipe(name = "Mi V60", method = BrewMethod.V60, grind = GrindSetting(uno, 20.0, "x"), doseG = 15.0, stages = listOf(RecipeStage("Bloom", 0, 40.0, 10_000))),
      )
    }
    env.skipOnboarding(showTours = listOf(Tours.converter))
    show()
    // Primera apertura, desde Herramientas y sin origen: solo el paso del origen.
    openTools()
    rule.onNodeWithTag("tools_converter").performClick()
    waitFor("converter")
    rule.waitForTour("1/1", env.tourSettings.seen.value)
    rule.completeTour()
    rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
    waitFor("tools")

    // Apertura posterior desde la tarjeta de molienda de Preparar: con origen y otro molino aparecen las equivalencias.
    rule.onNodeWithTag("tab_prepare").performClick()
    rule.runOnUiThread { env.scaleSession.selectTarget(ReferenceKey.Recipe(recipeId)) }
    waitFor("grind_card_converter")
    rule.onNodeWithTag("grind_card_converter").performClick()
    waitFor("converter_row_$dos")

    rule.waitForTour("1/1", env.tourSettings.seen.value)
    titleIs(R.string.tour_converter_rows_title)
  }
}
