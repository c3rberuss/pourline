package dev.josueamaya.pourline.ui.tools

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.AnnotatedString
import androidx.test.platform.app.InstrumentationRegistry
import dev.josueamaya.pourline.R
import dev.josueamaya.pourline.Tab
import dev.josueamaya.pourline.recipe.StageType
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
    rule.onNodeWithTag("four_six_taste_balanced").assertIsOn()
    rule.onNodeWithTag("four_six_strength_standard").assertIsOn()
    rule.onNodeWithTag("four_six_water").assertTextContains("300", substring = true)
    rule.onNodeWithTag("four_six_pour_count").performScrollTo().assertTextContains("5", substring = true)
    rule.onNodeWithTag("four_six_time").assertTextContains("3:30", substring = true)
    rule.onNodeWithTag("four_six_pour_0").performScrollTo().assertTextContains("+60 g")

    rule.onNodeWithTag("four_six_taste_sweet").performScrollTo().performClick()
    rule.onNodeWithTag("four_six_strength_strongest").performScrollTo().performClick()
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
    rule.onNodeWithTag("four_six_strength_strong").performScrollTo().performClick()
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
