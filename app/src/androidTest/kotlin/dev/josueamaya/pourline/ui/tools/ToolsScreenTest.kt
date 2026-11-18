package dev.josueamaya.pourline.ui.tools

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.AnnotatedString
import androidx.test.platform.app.InstrumentationRegistry
import dev.josueamaya.pourline.R
import dev.josueamaya.pourline.Tab
import dev.josueamaya.pourline.recipe.StageType
import dev.josueamaya.pourline.tools.RatioCalculator
import dev.josueamaya.pourline.ui.TestApp
import dev.josueamaya.pourline.ui.TestAppDependencies
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** La pestaña Herramientas: calculadora de ratio, generador 4:6 (con "Guardar como receta") y el convertidor. */
class ToolsScreenTest {
  @get:Rule
  val rule = createAndroidComposeRule<ComponentActivity>()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private lateinit var env: TestAppDependencies

  @Before
  fun setUp() {
    env = TestAppDependencies(context)
    env.skipOnboarding()
    rule.setContent { TestApp(env) }
    rule.onNodeWithTag(Tab.TOOLS.testTag).performClick()
    waitFor("tools")
  }

  @After
  fun tearDown() = env.close()

  private fun exists(tag: String) = rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

  /** Mueve el slider [tag] a la posición [level] (de 0 a 4). */
  private fun setLevel(tag: String, level: Int) {
    rule.onNodeWithTag(tag).performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(level.toFloat()) }
  }

  private fun waitFor(tag: String) = rule.waitUntil(5_000) { exists(tag) }

  private fun input(tag: String) = rule.onNodeWithTag(tag)

  private fun SemanticsNodeInteraction.assertInput(value: String) =
    assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(value)))

  private fun back() = rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }

  @Test
  fun theRatioCalculatorRecomputesFromAnyField() {
    rule.onNodeWithTag("tools_ratio").performClick()
    waitFor("ratio_calculator")
    // Los predeterminados de Preparar: 15 g a 1:16.
    input("ratio_dose").assertInput("15")
    input("ratio_ratio").assertInput("16")
    input("ratio_water").assertInput("240")

    rule.onNodeWithTag("ratio_dose").performTextReplacement("20")
    input("ratio_water").assertInput("320")

    rule.onNodeWithTag("ratio_ratio").performTextReplacement("15")
    input("ratio_water").assertInput("300")

    rule.onNodeWithTag("ratio_water").performTextReplacement("450")
    input("ratio_dose").assertInput("30")
    input("ratio_ratio").assertInput("15")

    rule.onNodeWithTag("ratio_ratio").performTextReplacement("")
    rule.onNodeWithTag("ratio_error").assertIsDisplayed()
    rule.onNodeWithTag("ratio_ratio").performTextReplacement("18")
    assertFalse(exists("ratio_error"))
    input("ratio_water").assertInput("540")
  }

  @Test
  fun theFourSixGeneratorShowsThePoursAndUpdatesWithTheChoices() {
    rule.onNodeWithTag("tools_four_six").performClick()
    waitFor("four_six")
    rule.onNodeWithTag("four_six_dose").performTextReplacement("20")
    rule.onNodeWithTag("four_six_taste_value").assertTextContains(context.getString(R.string.four_six_taste_balanced), substring = true)
    rule.onNodeWithTag("four_six_taste_value").assertTextContains("60/60 g", substring = true)
    rule.onNodeWithTag("four_six_strength_value").assertTextContains(context.getString(R.string.four_six_strength_standard), substring = true)
    rule.onNodeWithTag("four_six_water").assertTextContains("300", substring = true)
    rule.onNodeWithTag("four_six_pour_count").performScrollTo().assertTextContains("5", substring = true)
    rule.onNodeWithTag("four_six_time").assertTextContains("3:30", substring = true)
    rule.onNodeWithTag("four_six_pour_0").performScrollTo().assertTextContains("+60 g")
    rule.onNodeWithTag("four_six_pour_1").performScrollTo().assertTextContains("0:45–0:55", substring = true)

    // Más lento: el vertido dura más, pero empieza a la misma hora.
    rule.onNodeWithText(context.getString(R.string.four_six_pace_slowest)).performScrollTo().performClick()
    rule.onNodeWithTag("four_six_pour_1").performScrollTo().assertTextContains("0:45–1:05", substring = true)
    rule.onNodeWithTag("four_six_time").performScrollTo().assertTextContains("3:35", substring = true)

    // Ritmo propio: el campo empieza con el ritmo que había (3 g/s) y acepta un decimal.
    rule.onNodeWithText(context.getString(R.string.four_six_pace_custom)).performScrollTo().performClick()
    rule.onNodeWithTag("four_six_pace_custom").performScrollTo().assertTextContains("3", substring = true)
    rule.onNodeWithTag("four_six_pace_custom").performTextReplacement("2.5")
    rule.onNodeWithTag("four_six_pace_value").assertTextContains("${RatioCalculator.format(2.5)} g/s", substring = true)
    rule.onNodeWithTag("four_six_pour_1").performScrollTo().assertTextContains("0:45–1:09", substring = true)
    rule.onNodeWithTag("four_six_pace_custom").performScrollTo().performTextReplacement("1")
    rule.onNodeWithTag("four_six_pace_error").assertIsDisplayed()
    assertFalse(exists("four_six_error"))
    assertFalse(exists("four_six_pour_0"))
    rule.onNodeWithTag("four_six_save").performScrollTo().assertIsNotEnabled()
    rule.onNodeWithText(context.getString(R.string.four_six_pace_normal)).performScrollTo().performClick()

    setLevel("four_six_taste", 0)
    setLevel("four_six_strength", 4)
    rule.onNodeWithTag("four_six_taste_value").assertTextContains(context.getString(R.string.four_six_taste_sweet), substring = true)
    rule.onNodeWithTag("four_six_taste_value").assertTextContains("40/80 g", substring = true)
    rule.onNodeWithTag("four_six_strength_value").assertTextContains(context.getString(R.string.four_six_strength_strongest), substring = true)
    rule.onNodeWithTag("four_six_pour_count").performScrollTo().assertTextContains("7", substring = true)
    rule.onNodeWithTag("four_six_time").assertTextContains("5:00", substring = true)
    rule.onNodeWithTag("four_six_pour_0").performScrollTo().assertTextContains("+40 g")
    rule.onNodeWithTag("four_six_pour_1").performScrollTo().assertTextContains("+80 g")
    rule.onNodeWithTag("four_six_pour_6").performScrollTo().assertTextContains("+36 g")

    // 60 g a 1:20 son 1200 g: más de lo que admite una receta.
    rule.onNodeWithTag("four_six_dose").performScrollTo().performTextReplacement("60")
    rule.onNodeWithTag("four_six_ratio").performTextReplacement("20")
    rule.onNodeWithTag("four_six_error").assertIsDisplayed()
    assertFalse(exists("four_six_pour_0"))
    rule.onNodeWithTag("four_six_save").performScrollTo().assertIsNotEnabled()
  }

  @Test
  fun saveAsRecipeOpensTheEditorFilledAndSavesIt() {
    rule.onNodeWithTag("tools_four_six").performClick()
    waitFor("four_six")
    rule.onNodeWithTag("four_six_dose").performTextReplacement("20")
    setLevel("four_six_strength", 3)
    rule.onNodeWithText(context.getString(R.string.four_six_pace_slow)).performScrollTo().performClick()
    rule.onNodeWithTag("four_six_save").performScrollTo().assertIsEnabled().performClick()

    waitFor("recipe_edit_save")
    val name = context.getString(
      R.string.four_six_recipe_name,
      context.getString(R.string.four_six_taste_balanced),
      context.getString(R.string.four_six_strength_strong),
    )
    rule.onNodeWithTag("recipe_edit_name").assertTextContains(name, substring = true)
    rule.onNodeWithTag("recipe_edit_save").performScrollTo().performClick()
    // Guardada, vuelve al generador.
    waitFor("four_six")

    val saved = runBlocking { env.recipes.list().first().single { it.name == name } }
    assertEquals(20.0, saved.doseG, 0.0)
    assertEquals(300.0, saved.totalWaterG, 0.0)
    val recipe = runBlocking { env.recipes.get(saved.id) }
    assertNotNull(recipe)
    assertEquals(listOf(60.0, 120.0, 165.0, 210.0, 255.0, 300.0), recipe!!.stages.filter { it.type == StageType.POUR }.map { it.targetWaterG })
    // A ritmo lento, cada vertido dura 15 s y cada espera llega justo al siguiente.
    assertEquals(List(6) { 15_000L }, recipe.stages.filter { it.type == StageType.POUR }.map { it.durationMs })
    recipe.stages.zipWithNext().forEach { (a, b) -> assertEquals(a.endMs, b.startMs) }
    assertEquals(StageType.WAIT, recipe.stages.last().type)
    assertEquals(255_000L, recipe.stages.last().endMs)
  }

  @Test
  fun theGrindConverterOpensFromTheToolsAndBackReturns() {
    rule.onNodeWithTag("tools_converter").performClick()
    waitFor("converter")
    back()
    waitFor("tools")
  }
}
