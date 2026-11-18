package dev.josueamaya.pourline.ui.session

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import dev.josueamaya.pourline.R
import dev.josueamaya.pourline.brew.BrewPhase
import dev.josueamaya.pourline.brew.FinishReason
import dev.josueamaya.pourline.history.BrewFilter
import dev.josueamaya.pourline.protocol.WeightUnit
import dev.josueamaya.pourline.recipe.aeroPressWithActions
import dev.josueamaya.pourline.session.BrewPlan
import dev.josueamaya.pourline.settings.ExperienceMode
import dev.josueamaya.pourline.ui.TestApp
import dev.josueamaya.pourline.ui.TestAppDependencies
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicLong

/**
 * La sesión sin báscula (SPEC-007 §3.4) con Room en memoria y un reloj de prueba: el estado listo, el en curso, los pasos
 * completados, el resumen, la salida y el menú ⋯. Se abre con "Empezar" y se pasa a sin báscula desde el gestor.
 */
class ScalelessSessionScreenTest {
  @get:Rule
  val rule = createAndroidComposeRule<ComponentActivity>()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private val now = AtomicLong(10_000)
  private val env = TestAppDependencies(context, clock = { now.get() })
  private val driver = SessionDriver(rule, env)
  private val manager get() = env.scaleSession
  private val plan = BrewPlan(doseG = 15.0, ratio = 16.0)

  @After
  fun tearDown() = env.close()

  private fun show(mode: ExperienceMode = ExperienceMode.BASIC, recipe: Boolean = true, recipePart: String = "V60") {
    env.skipOnboarding(mode)
    rule.setContent { TestApp(env) }
    driver.connect()
    if (recipe) driver.selectRecipe(recipePart) else rule.runOnUiThread { manager.selectTarget(null) }
    // La sesión guiada se abre con la báscula simulada y pasa a sin báscula desde el gestor.
    driver.start()
    rule.runOnUiThread { manager.startScalelessSession(plan) }
    driver.waitFor("scaleless_ready")
  }

  private fun startTimer() {
    driver.node("scaleless_start").performClick()
    driver.waitFor("scaleless_active")
  }

  /** Adelanta el reloj de prueba hasta [ms] desde el inicio de la sesión y espera a que el gestor lo publique. */
  private fun at(ms: Long) {
    val t0 = manager.ui.value.brew.t0MonoMs!!
    now.set(t0 + ms)
    rule.waitUntil(10_000) { manager.ui.value.elapsedMs == ms }
    rule.waitForIdle()
  }

  private fun savedBrews() = runBlocking { env.brews.list(BrewFilter()).first() }

  private fun text(id: Int, vararg args: Any) = context.getString(id, *args)

  private fun back() = rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }

  // --- Listo ---

  @Test
  fun armedShowsTheFirstStepTheListAndStartTimerWithoutPrepSteps() {
    show()

    driver.node("session_current_step").assertIsDisplayed()
    driver.node("session_step_up_to").assert(hasText(text(R.string.session_step_up_to, "40 g")))
    driver.node("session_steps").assertIsDisplayed()
    driver.node("scaleless_start").assertIsDisplayed()
    driver.node("scaleless_start").assert(hasText(text(R.string.prep_start_timer)))
    listOf("session_prep", "prep_stepper", "prep_tare", "prep_skip", "prep_start_timer").forEach {
      assertFalse(it, driver.exists(it))
    }
    assertEquals(BrewPhase.Armed, manager.ui.value.brew.phase)
  }

  @Test
  fun armedWithoutARecipeShowsDoseRatioAndWater() {
    show(recipe = false)

    driver.node("scaleless_plan_dose").assert(hasText("15.0"))
    driver.node("scaleless_plan_ratio").assert(hasText(text(R.string.scale_ratio_value, 16.0)))
    driver.node("scaleless_plan_water").assert(hasText("240.0"))
    driver.node("scaleless_start").assertIsDisplayed()
    assertFalse(driver.exists("session_steps"))
  }

  // --- En curso ---

  @Test
  fun activeFollowsTheClock() {
    show()
    startTimer()

    at(5_000)
    driver.node("scaleless_clock").assert(hasText("0:05"))
    driver.node("scaleless_action").assert(hasText(text(R.string.scaleless_pour_to, "40")))
    driver.node("scaleless_step_progress").assert(hasText(text(R.string.scaleless_step_progress, 1, 5, "0:05")))
    driver.node("session_dial").assertIsDisplayed()

    at(46_000)
    driver.node("scaleless_clock").assert(hasText("0:46"))
    driver.node("scaleless_action").assert(hasText(text(R.string.scaleless_pour_to, "150")))
    driver.node("scaleless_step_progress").assert(hasText(text(R.string.scaleless_step_progress, 3, 5, "0:19")))
    driver.node("session_step_up_to").assert(hasText(text(R.string.session_step_up_to, "150 g")))
  }

  @Test
  fun aWaitStepShowsItsInstructionOrItsType() {
    show()
    startTimer()

    at(20_000)

    driver.node("scaleless_step_progress").assert(hasText(text(R.string.scaleless_step_progress, 2, 5, "0:25")))
    driver.node("scaleless_action").assertIsDisplayed()
    driver.node("scaleless_action").assert(!hasText(text(R.string.scaleless_pour_to, "40")))
    driver.node("scaleless_action").assert(!hasText(text(R.string.scaleless_pour_to, "150")))
  }

  @Test
  fun anActionStepShowsItsInstructionOrItsType() {
    runBlocking { env.recipes.save(aeroPressWithActions()) }
    show(recipePart = "AeroPress")
    startTimer()
    val action = text(R.string.step_type_action)

    // En el remover, el siguiente paso es la Acción (a 4 s ya es la cuenta atrás, con la descripción "Siguiente: Acción en 4 s").
    at(16_000)
    driver.node("session_next_step").assert(hasContentDescription(action, substring = true))

    // La Acción con instrucción dice la instrucción, sin objetivo de agua, en el paso 3 de 6 con 4 s por delante.
    at(21_000)
    driver.node("scaleless_action").assert(hasText("Pon la tapa"))
    driver.node("scaleless_step_progress").assert(hasText(text(R.string.scaleless_step_progress, 3, 6, "0:04")))
    assertFalse(driver.exists("session_step_up_to"))

    // La Acción sin instrucción dice su tipo.
    at(95_000)
    driver.node("scaleless_action").assert(hasText(action))
  }

  private fun assertNoFlowChartOrDeviation() {
    listOf("session_water", "session_ratio_flow", "session_chart", "session_stability", "session_deviation", "session_metrics")
      .forEach { assertFalse(it, driver.exists(it)) }
  }

  @Test
  fun activeHasNoFlowChartOrDeviationInBasic() {
    show(ExperienceMode.BASIC)
    startTimer()
    at(5_000)

    assertNoFlowChartOrDeviation()
    driver.node("session_finish").assertIsDisplayed()
  }

  @Test
  fun activeHasNoFlowChartOrDeviationInExpert() {
    show(ExperienceMode.EXPERT)
    startTimer()
    at(5_000)

    assertNoFlowChartOrDeviation()
    driver.node("session_finish").assertIsDisplayed()
  }

  @Test
  fun activeWithoutARecipeShowsTheWaterGoalAndFinish() {
    show(recipe = false)
    startTimer()
    at(3_000)

    driver.node("scaleless_clock").assert(hasText("0:03"))
    driver.node("scaleless_water_goal").assert(hasText(text(R.string.scaleless_water_goal, "240")))
    driver.node("session_finish").assertIsDisplayed()
    assertFalse(driver.exists("session_steps"))
    assertFalse(driver.exists("scaleless_step_progress"))
    assertNoFlowChartOrDeviation()
  }

  @Test
  fun whenAllStepsAreDoneTheHintShowsAndTheSessionStaysActive() {
    show()
    startTimer()

    at(114_000)
    assertFalse(driver.exists("scaleless_done_hint"))
    at(116_000)

    driver.node("scaleless_done_hint").assertIsDisplayed()
    driver.node("scaleless_done_hint").assert(hasAnyDescendant(hasText(text(R.string.scaleless_done_hint))))
    driver.node("session_finish").assertIsDisplayed()
    assertEquals(BrewPhase.Active(pouring = false), manager.ui.value.brew.phase)
  }

  // --- Resumen ---

  @Test
  fun finishShowsTheScalelessSummary() {
    show()
    startTimer()
    at(116_000)

    driver.finishFromButton()

    driver.node("summary_scaleless").assert(hasAnyDescendant(hasText(text(R.string.history_row_scaleless))))
    driver.node("summary_dose").assert(hasText("15.0"))
    driver.node("summary_water").assert(hasText("250.0"))
    driver.node("summary_ratio").assertTextContains(text(R.string.scale_ratio_value, 250.0 / 15.0), substring = true)
    driver.node("summary_time").assert(hasText("01:56"))
    assertFalse(driver.exists("summary_expert"))
    rule.waitUntil(10_000) { savedBrews().size == 1 }
    assertTrue(savedBrews().single().scaleless)
  }

  @Test
  fun inExpertTheSummaryOnlyShowsTheFinishReason() {
    show(ExperienceMode.EXPERT)
    startTimer()
    at(60_000)

    driver.finishFromButton()

    driver.node("summary_expert").assertIsDisplayed()
    driver.node("summary_reason").assertIsDisplayed()
    listOf("summary_avg_flow", "summary_max_flow", "summary_stability").forEach { assertFalse(it, driver.exists(it)) }
  }

  @Test
  fun atFifteenMinutesTheSessionEndsWithMaxDuration() {
    show(ExperienceMode.EXPERT)
    startTimer()

    val t0 = manager.ui.value.brew.t0MonoMs!!
    now.set(t0 + 900_000)
    driver.waitFor("session_summary", 20_000)

    val finished = manager.ui.value.brew.phase as BrewPhase.Finished
    assertEquals(FinishReason.MAX_DURATION, finished.summary.reason)
    driver.node("summary_reason").assertIsDisplayed()
  }

  // --- Salir y menú ---

  @Test
  fun closeInActiveOpensTheExitDialogAndDiscardSavesNothing() {
    show()
    startTimer()
    at(10_000)

    driver.node("session_close").performClick()
    driver.waitFor("exit_dialog")
    driver.node("exit_discard").performClick()

    driver.waitFor("prepare_start")
    assertFalse(manager.ui.value.scaleless)
    assertTrue(savedBrews().isEmpty())
  }

  @Test
  fun closeInArmedLeavesWithoutSaving() {
    show()

    driver.node("session_close").performClick()

    driver.waitFor("prepare_start")
    assertFalse(driver.exists("exit_dialog"))
    assertFalse(manager.ui.value.scaleless)
    assertFalse(manager.ui.value.hasSession)
    assertTrue(savedBrews().isEmpty())
  }

  @Test
  fun backInActiveAlsoOpensTheExitDialog() {
    show()
    startTimer()

    back()

    driver.waitFor("exit_dialog")
    driver.node("exit_continue").performClick()
    driver.waitGone("exit_dialog")
    assertTrue(manager.ui.value.brew.phase is BrewPhase.Active)
  }

  @Test
  fun optionsDisableUnitAndRestartInArmedAndHideTheSimulation() {
    show()

    driver.node("session_options").performClick()
    driver.waitFor("session_options_sheet")

    driver.node("session_restart").assertIsNotEnabled()
    rule.onNode(hasText(WeightUnit.OUNCES.symbol)).assertIsNotEnabled()
    assertFalse(driver.exists("sim_add_15"))
    driver.closeSheet()
  }

  @Test
  fun restartIsEnabledOnceFinished() {
    show()
    startTimer()
    at(10_000)
    driver.finishFromButton()

    driver.node("session_options").performClick()
    driver.waitFor("session_options_sheet")
    driver.node("session_restart").performClick()

    driver.waitFor("scaleless_ready")
    assertTrue(manager.ui.value.scaleless)
    assertNull(manager.ui.value.savedBrew)
  }

  // --- La báscula no interviene ---

  @Test
  fun noDisconnectedNoticeWithoutAScale() {
    show()
    rule.runOnUiThread { manager.disconnect() }
    rule.waitUntil(5_000) { manager.ui.value.connection != dev.josueamaya.pourline.scale.ConnectionState.Ready }
    rule.waitForIdle()

    assertFalse(driver.exists("session_disconnected"))
    driver.node("scaleless_start").assertIsDisplayed()
    startTimer()
    assertFalse(driver.exists("session_disconnected"))
  }

  @Test
  fun aConnectedSimulatedScaleDoesNotChangeTheSession() {
    show()
    startTimer()
    at(5_000)

    driver.addWeight(100.0)
    driver.addWeight(50.0)
    rule.waitUntil(5_000) { manager.ui.value.weight > 0.0 }
    rule.waitForIdle()

    assertEquals(BrewPhase.Active(pouring = false), manager.ui.value.brew.phase)
    assertTrue(manager.ui.value.brew.samples.isEmpty())
    driver.node("scaleless_clock").assert(hasText("0:05"))
    assertNoFlowChartOrDeviation()
  }
}
