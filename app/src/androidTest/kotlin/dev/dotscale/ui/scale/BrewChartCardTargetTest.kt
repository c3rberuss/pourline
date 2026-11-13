package dev.dotscale.ui.scale

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import dev.dotscale.R
import dev.dotscale.brew.BrewPhase
import dev.dotscale.brew.BrewSample
import dev.dotscale.brew.BrewState
import dev.dotscale.brew.DeviationLevel
import dev.dotscale.brew.GuideState
import dev.dotscale.brew.TargetCurve
import dev.dotscale.history.BrewDetails
import dev.dotscale.history.ReferenceType
import dev.dotscale.protocol.WeightUnit
import dev.dotscale.recipe.Recipe
import dev.dotscale.recipe.RecipeStage
import dev.dotscale.settings.ReferenceKey
import dev.dotscale.ui.components.AntTextType
import dev.dotscale.ui.components.antTextColor
import dev.dotscale.ui.theme.DotScaleTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.Locale

/** La tarjeta de la gráfica con objetivo, a 360 dp (el ancho en el que deben caber las cifras de la guía). */
class BrewChartCardTargetTest {
  @get:Rule
  val rule = createComposeRule()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext

  private val recipe = Recipe(
    name = "V60 clásica",
    doseG = 15.0,
    stages = listOf(
      RecipeStage("Bloom", 0, 50.0, 10_000),
      RecipeStage("Segundo", 30_000, 150.0, 15_000),
      RecipeStage("Tercero", 60_000, 250.0, 15_000),
    ),
  )
  private val curve = TargetCurve.fromRecipe(recipe)

  private fun target(factor: Double = 1.0, curve: TargetCurve = this.curve) = ActiveTarget(
    key = ReferenceKey.Recipe(1),
    type = ReferenceType.RECIPE,
    name = "V60 clásica",
    curve = curve,
    factor = factor,
    scaledDoseG = if (factor == 1.0) null else 16.4,
    prefill = BrewDetails(),
  )

  private val samples = listOf(BrewSample(0, 0.0, 0.0, null, true), BrewSample(5_000, 25.0, 5.0, null, true))
  private val active = BrewState(BrewPhase.Active(pouring = true), samples, currentStability = 80, currentLevel = null, t0MonoMs = 0)

  private fun guide(
    stageIndex: Int? = 1,
    next: Int? = 2,
    countdown: Int? = null,
    deviation: Double? = 6.0,
    level: DeviationLevel? = DeviationLevel.WARNING,
    done: Boolean = false,
  ) = GuideState(
    stage = stageIndex?.let { curve.stages[it] },
    nextStage = next?.let { curve.stages[it] },
    countdownS = countdown,
    deviationG = deviation,
    deviationLevel = level,
    allStagesDone = done,
  )

  private fun show(
    brew: BrewState = BrewState.Initial,
    target: ActiveTarget? = null,
    guide: GuideState? = null,
    onPick: (() -> Unit)? = null,
  ) = rule.setContent {
    DotScaleTheme(darkTheme = false) {
      Box(Modifier.width(360.dp)) {
        BrewChartCard(brew, WeightUnit.GRAMS, onFinish = {}, target = target, guide = guide, onPickTarget = onPick)
      }
    }
  }

  private fun exists(tag: String) = rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

  @Test
  fun armedShowsTheFirstStageAndTheEmptyText() {
    show(target = target())
    rule.onNodeWithTag("target_armed_stage")
      .assertTextEquals(context.getString(R.string.target_stage_armed, "1/3 · Bloom", "50 g", 10))
    rule.onNodeWithText(context.getString(R.string.brew_chart_empty)).assertIsDisplayed()
    rule.onNodeWithTag("brew_chart").assertIsDisplayed()
    assertTrue(!exists("target_scaled"))
  }

  @Test
  fun armedWithAScaledTargetSaysSo() {
    show(target = target(factor = 16.4 / 15.0, curve = curve.scaled(16.4 / 15.0)))
    val dose = String.format(Locale.getDefault(), "%.1f g", 16.4)
    rule.onNodeWithTag("target_scaled").assertTextEquals(context.getString(R.string.target_scaled, dose))
  }

  @Test
  fun withoutATargetTheCardIsAsBefore() {
    show()
    rule.onNodeWithText(context.getString(R.string.brew_chart_empty)).assertIsDisplayed()
    assertTrue(!exists("target_chip"))
    assertTrue(!exists("target_armed_stage"))
    assertTrue(!exists("target_guide"))
    assertTrue(!exists("brew_chart"))
  }

  @Test
  fun activeShowsTheStageTheGoalAndTheDeviation() {
    show(brew = active, target = target(), guide = guide())
    rule.onNodeWithTag("target_guide").assertIsDisplayed()
    rule.onNodeWithTag("target_guide_stage").assertTextEquals("2/3 · Segundo")
    rule.onNodeWithTag("target_guide_goal").assertTextEquals(context.getString(R.string.target_up_to, "150 g"))
    rule.onNodeWithTag("target_guide_deviation").assertTextEquals("+6 g")
    assertTrue(!exists("target_guide_countdown"))
    rule.onNodeWithTag("brew_level").assertIsDisplayed() // el AntTag de estabilidad se mantiene
  }

  @Test
  fun theCountdownAppearsOnlyDuringTheLastSeconds() {
    show(brew = active, target = target(), guide = guide(countdown = 4))
    rule.onNodeWithTag("target_guide_countdown").assertTextEquals(context.getString(R.string.target_next_in, 4))
  }

  @Test
  fun aNegativeDeviationUsesARealMinusSign() {
    show(brew = active, target = target(), guide = guide(deviation = -12.0, level = DeviationLevel.ERROR))
    rule.onNodeWithTag("target_guide_deviation").assertTextEquals("−12 g")
  }

  /** El color del desvío sale de su nivel: `AntText` pisaba el `color` del estilo con el del tipo. */
  @Test
  fun theDeviationIsColoredByItsLevel() {
    val level = mutableStateOf(DeviationLevel.OK)
    val expected = HashMap<DeviationLevel?, Color>()
    rule.setContent {
      DotScaleTheme(darkTheme = false) {
        expected[DeviationLevel.OK] = antTextColor(AntTextType.Success)
        expected[DeviationLevel.WARNING] = antTextColor(AntTextType.Warning)
        expected[DeviationLevel.ERROR] = antTextColor(AntTextType.Danger)
        expected[null] = antTextColor(AntTextType.Secondary)
        Box(Modifier.width(360.dp)) {
          BrewChartCard(
            active, WeightUnit.GRAMS, onFinish = {}, target = target(),
            guide = guide(deviation = 4.0, level = level.value), onPickTarget = null,
          )
        }
      }
    }
    for (shown in listOf(DeviationLevel.OK, DeviationLevel.WARNING, DeviationLevel.ERROR)) {
      level.value = shown
      rule.waitForIdle()
      val results = mutableListOf<TextLayoutResult>()
      rule.onNodeWithTag("target_guide_deviation").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
      assertEquals(shown.name, expected.getValue(shown), results.single().layoutInput.style.color)
    }
  }

  @Test
  fun withoutAReadingTheDeviationIsADash() {
    show(brew = active, target = target(), guide = guide(deviation = null, level = null))
    rule.onNodeWithTag("target_guide_deviation").assertTextEquals(context.getString(R.string.target_deviation_none))
  }

  @Test
  fun afterTheLastStageItSaysSoAndKeepsTheDeviation() {
    show(brew = active, target = target(), guide = guide(stageIndex = 2, next = null, done = true, deviation = -2.0, level = DeviationLevel.OK))
    rule.onNodeWithTag("target_guide_stage").assertTextEquals(context.getString(R.string.target_all_done))
    rule.onNodeWithTag("target_guide_deviation").assertTextEquals("−2 g")
    assertTrue(!exists("target_guide_goal"))
    assertTrue(!exists("target_guide_countdown"))
  }

  @Test
  fun beforeAFirstStageThatStartsLaterItWaitsForIt() {
    val late = curve.copy(stages = curve.stages.mapIndexed { i, s -> if (i == 0) s.copy(startMs = 5_000) else s })
    show(
      brew = active,
      target = target(curve = late),
      guide = GuideState(null, late.stages[0], 3, null, null),
    )
    rule.onNodeWithTag("target_guide_stage").assertTextEquals(context.getString(R.string.target_before_first, 3))
    rule.onNodeWithTag("target_guide_goal").assertTextEquals(context.getString(R.string.target_up_to, "50 g"))
  }

  @Test
  fun theChartDescriptionMentionsTheTarget() {
    show(brew = active, target = target(), guide = guide())
    // 250 g al terminar el último vertido, a los 60 s + 15 s.
    val goal = context.getString(R.string.target_description, String.format(Locale.getDefault(), "%.1f g", 250.0), "01:15")
    rule.onNode(hasContentDescription(goal, substring = true)).assertExists()
  }

  @Test
  fun theChipShowsTheNameOrNoneAndOpensThePicker() {
    var opened = 0
    show(target = target(), onPick = { opened++ })
    rule.onNodeWithText(context.getString(R.string.target_chip, "V60 clásica")).assertIsDisplayed()
    rule.onNodeWithTag("target_chip").assertIsEnabled().performClick()
    assertEquals(1, opened)
  }

  @Test
  fun theChipSaysNoneWithoutATarget() {
    show(onPick = {})
    rule.onNodeWithText(context.getString(R.string.target_chip_none)).assertIsDisplayed()
  }

  @Test
  fun theChipIsDisabledWhileActive() {
    show(brew = active, target = target(), guide = guide(), onPick = {})
    rule.onNodeWithTag("target_chip").assertIsNotEnabled()
  }
}
