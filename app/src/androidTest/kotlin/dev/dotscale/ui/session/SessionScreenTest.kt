package dev.dotscale.ui.session

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.platform.app.InstrumentationRegistry
import dev.dotscale.R
import dev.dotscale.Tab
import dev.dotscale.brew.BrewPhase
import dev.dotscale.history.BrewFilter
import dev.dotscale.recipe.StageType
import dev.dotscale.session.PrepStep
import dev.dotscale.settings.ExperienceMode
import dev.dotscale.sim.FollowMode
import dev.dotscale.ui.RecordingNotifier
import dev.dotscale.ui.TestApp
import dev.dotscale.ui.TestAppDependencies
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * La sesión guiada completa con Room en memoria y la báscula simulada: pasos previos, vertido, resumen, salida,
 * modos, desconexión y avisos de paso.
 */
class SessionScreenTest {
  @get:Rule
  val rule = createAndroidComposeRule<ComponentActivity>()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private val notifier = RecordingNotifier()
  private val env = TestAppDependencies(context, notifier = notifier)
  private val driver = SessionDriver(rule, env)
  private val manager get() = env.scaleSession

  @After
  fun tearDown() = env.close()

  private fun show(mode: ExperienceMode = ExperienceMode.BASIC) {
    env.skipOnboarding(mode)
    rule.setContent { TestApp(env) }
    driver.connect()
    driver.selectRecipe()
  }

  private fun back() = rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }

  private fun savedBrews() = runBlocking { env.brews.list(BrewFilter()).first() }

  /** Hasta `Active` con el vertido que sigue la receta y algo de agua ya en el dial. */
  private fun pouring() {
    driver.reachPourStep()
    driver.pourFollowingTarget()
    rule.waitUntil(15_000) { manager.ui.value.brew.samples.size > 20 }
  }

  // --- La pantalla ---

  @Test
  fun theSessionHasNoBottomBar() {
    show()
    driver.start()

    Tab.entries.forEach { assertTrue(!driver.exists(it.testTag)) }
    driver.node("session_close").assertIsDisplayed()
    driver.node("session_options").assertIsDisplayed()
  }

  @Test
  fun theTitleIsTheRecipeNameOrFreeBrew() {
    show()
    driver.start()
    rule.onNode(hasText(manager.ui.value.target!!.name)).assertIsDisplayed()

    back()
    rule.runOnUiThread { manager.selectTarget(null) }
    rule.waitUntil(5_000) { manager.ui.value.target == null }
    driver.start()
    rule.onNode(hasText(context.getString(R.string.session_title_free))).assertIsDisplayed()
  }

  // --- Pasos previos ---

  @Test
  fun thePrepStepsAdvanceByThemselves() {
    show()
    driver.start()
    driver.node("prep_instruction").assert(hasText(context.getString(R.string.prep_instruction_tare)))

    driver.tare()
    driver.waitStep(PrepStep.DOSE)
    driver.node("prep_stability").assertIsDisplayed()
    driver.node("prep_capture").assertIsDisplayed()

    driver.addWeight(15.0)
    driver.waitStep(PrepStep.TARE_FOR_POUR)
    driver.node("prep_dose").assert(hasText("15,0", substring = true).or(hasText("15.0", substring = true)))
    assertEquals(15.0, manager.ui.value.dose!!, 1e-6)

    driver.tare()
    driver.waitStep(PrepStep.POUR)
    driver.node("prep_start_timer").assertIsDisplayed()
  }

  @Test
  fun everyPrepStepCanBeSkipped() {
    show()
    driver.start()

    driver.node("prep_skip").performClick()
    driver.waitStep(PrepStep.DOSE)
    driver.node("prep_skip_dose").performClick()
    driver.waitStep(PrepStep.TARE_FOR_POUR)
    driver.node("prep_skip").performClick()
    driver.waitStep(PrepStep.POUR)
    driver.node("prep_start_timer").assertIsDisplayed()
  }

  @Test
  fun captureNowTakesTheCurrentWeightAsTheDose() {
    show()
    driver.start()
    driver.node("prep_skip").performClick()
    driver.waitStep(PrepStep.DOSE)
    driver.addWeight(18.0)
    rule.waitUntil(5_000) { manager.ui.value.weight > 17.0 }

    driver.node("prep_capture").performClick()

    driver.waitStep(PrepStep.TARE_FOR_POUR)
    assertEquals(18.0, manager.ui.value.dose!!, 0.2)
  }

  @Test
  fun startTimerMovesToTheDialWithoutWaitingForWater() {
    show()
    driver.reachPourStep()

    driver.node("prep_start_timer").performClick()

    driver.waitFor("session_timer")
    driver.node("session_dial").assertIsDisplayed()
    assertTrue(manager.ui.value.brew.phase is BrewPhase.Active)
  }

  @Test
  fun theSimulationSheetAddsWeightFromTheMenu() {
    show()
    driver.start()
    driver.simulateFromSheet("sim_add_15")

    rule.waitUntil(5_000) { manager.ui.value.weight > 14.0 }
    driver.node("prep_weight").assertIsDisplayed()
  }

  // --- Vertido ---

  @Test
  fun theActivePanelShowsTheStepInstructionAndTheTypeIconsInTheList() {
    show()
    pouring()

    driver.node("session_current_step").assertIsDisplayed()
    driver.node("session_instruction").assertIsDisplayed()
    driver.node("session_steps").assertIsDisplayed()
    val wait = context.getString(R.string.step_type_wait)
    val pour = context.getString(R.string.step_type_pour)
    // La V60 clásica tiene 2 pasos Esperar y 3 Verter, y la fila lleva su tipo.
    val waits = rule.onAllNodes(hasContentDescription(wait, substring = true)).fetchSemanticsNodes().size
    val pours = rule.onAllNodes(hasContentDescription(pour, substring = true)).fetchSemanticsNodes().size
    assertTrue("pasos Esperar en la lista: $waits", waits >= 2)
    assertTrue("pasos Verter en la lista: $pours", pours >= 3)
  }

  @Test
  fun basicHidesTheChartTheMetricsAndTheStabilityInTheSummary() {
    show(ExperienceMode.BASIC)
    pouring()

    assertTrue(!driver.exists("session_metrics"))
    driver.node("session_dial").performTouchInput { swipeLeft() }
    rule.waitForIdle()
    assertTrue(!driver.exists("session_chart"))
    driver.node("session_dial").assertIsDisplayed()

    driver.finishFromButton()
    driver.node("summary_done").assertIsDisplayed()
    driver.node("summary_time").assertIsDisplayed()
    assertTrue(!driver.exists("summary_stability"))
    assertTrue(!driver.exists("summary_expert"))
    assertTrue(!driver.exists("fit_block"))
  }

  @Test
  fun expertShowsTheChartOnPageTwoTheMetricsAndTheFullSummary() {
    show(ExperienceMode.EXPERT)
    pouring()

    driver.node("session_metrics").assertIsDisplayed()
    driver.node("session_dial").assertIsDisplayed()
    driver.node("session_dial").performTouchInput { swipeLeft() }
    driver.waitFor("session_chart")
    driver.node("session_chart").assertIsDisplayed()
    // La gráfica trae la curva objetivo: su descripción la menciona.
    rule.onNodeWithTag("brew_chart").assertIsDisplayed()

    driver.finishFromButton()
    driver.node("summary_expert").assertIsDisplayed()
    driver.node("summary_stability").assertExists()
    driver.node("fit_block").assertExists()
    driver.node("summary_avg_flow").assertExists()
    driver.node("summary_reason").assertExists()
  }

  @Test
  fun theModeSwitchInTheMenuChangesTheModeAtOnceKeepingTheTimerAndReturningToPageOne() {
    show(ExperienceMode.EXPERT)
    pouring()
    driver.node("session_dial").performTouchInput { swipeLeft() }
    driver.waitFor("session_chart")
    val t0 = manager.ui.value.brew.t0MonoMs
    val before = manager.ui.value.elapsedMs!!

    driver.node("session_options").performClick()
    driver.waitFor("session_expert_switch")
    driver.node("session_expert_switch").performClick()
    rule.waitUntil(5_000) { env.experienceSettings.mode.value == ExperienceMode.BASIC }
    driver.closeSheet()

    driver.waitFor("session_dial")
    assertTrue(!driver.exists("session_chart"))
    assertTrue(!driver.exists("session_metrics"))
    assertEquals(t0, manager.ui.value.brew.t0MonoMs)
    assertTrue(manager.ui.value.elapsedMs!! >= before)
    assertTrue(manager.ui.value.brew.phase is BrewPhase.Active)
  }

  // --- Salir ---

  @Test
  fun theCrossBeforeActiveLeavesWithoutAskingAndKeepsNothingSaved() {
    show()
    driver.start()

    driver.node("session_close").performClick()

    driver.waitFor("prepare_start")
    assertTrue(!driver.exists("exit_dialog"))
    assertTrue(!manager.ui.value.hasSession)
    assertTrue(savedBrews().isEmpty())
  }

  @Test
  fun theCrossInActiveOpensTheDialogAndKeepBrewingClosesIt() {
    show()
    pouring()

    driver.node("session_close").performClick()
    driver.waitFor("exit_dialog")
    driver.node("exit_save").assertIsDisplayed()
    driver.node("exit_discard").assertIsDisplayed()
    driver.node("exit_continue").performClick()

    driver.waitGone("exit_dialog")
    assertTrue(manager.ui.value.brew.phase is BrewPhase.Active)
  }

  @Test
  fun discardLeavesNothingInTheHistoryAndGoesBackToPrepare() {
    show()
    pouring()

    driver.node("session_close").performClick()
    driver.waitFor("exit_dialog")
    driver.node("exit_discard").performClick()

    driver.waitFor("prepare_start")
    assertTrue(savedBrews().isEmpty())
    assertTrue(!manager.ui.value.hasSession)
    assertTrue(manager.ui.value.brew.phase is BrewPhase.Armed)
  }

  @Test
  fun finishAndSaveShowsTheSummaryAndSavesTheBrew() {
    show()
    pouring()

    driver.node("session_close").performClick()
    driver.waitFor("exit_dialog")
    driver.node("exit_save").performClick()

    driver.waitFor("session_summary")
    rule.waitUntil(10_000) { savedBrews().size == 1 }
    driver.waitFor("summary_details")
    driver.node("summary_details").assertIsEnabled()
  }

  @Test
  fun theSystemBackBehavesLikeTheCross() {
    show()
    pouring()

    back()
    driver.waitFor("exit_dialog")
    driver.node("exit_continue").performClick()
    driver.waitGone("exit_dialog")

    driver.finishFromButton()
    back()
    driver.waitFor("prepare_start")
    assertTrue(!manager.ui.value.hasSession)
  }

  @Test
  fun backBeforeActiveLeavesWithoutAsking() {
    show()
    driver.start()

    back()

    driver.waitFor("prepare_start")
    assertTrue(!driver.exists("exit_dialog"))
  }

  @Test
  fun doneClosesTheSessionAndReturnsToPrepareWithTheScaleStillConnected() {
    show()
    pouring()
    driver.finishFromButton()

    driver.node("summary_done").performClick()

    driver.waitFor("prepare_start")
    assertTrue(!manager.ui.value.hasSession)
    assertEquals(dev.dotscale.scale.ConnectionState.Ready, manager.ui.value.connection)
    driver.node("prepare_start").assertIsEnabled()
  }

  // --- Desconexión ---

  @Test
  fun aDisconnectionShowsTheNoticeWithRetryAndTheStepsStartOver() {
    show()
    driver.start()
    driver.node("prep_skip").performClick()
    driver.waitStep(PrepStep.DOSE)

    rule.runOnUiThread { manager.disconnect() }
    driver.waitFor("session_disconnected")
    driver.node("session_retry").assertIsDisplayed()
    // Al desconectar, los pasos vuelven al 1 (la tara no vale) y la tara espera a la báscula.
    driver.waitStep(PrepStep.TARE)
    driver.node("prep_tare").assertIsNotEnabled()

    driver.node("session_retry").performClick()
    driver.waitGone("session_disconnected")
    driver.node("prep_tare").assertIsEnabled()
    driver.waitStep(PrepStep.TARE)
  }

  // --- Avisos de paso ---

  @Test
  fun theNotifierGetsAnAlertForEveryStepIncludingTheWaits() {
    show()
    pouring()

    // La V60 clásica empieza un Esperar a los 0:10 y un Verter a los 0:45.
    rule.waitUntil(70_000) { notifier.started.size >= 2 }
    val started = notifier.started.toList()
    assertEquals(StageType.WAIT, started[0].type)
    assertEquals(1, started[0].index)
    assertEquals(StageType.POUR, started[1].type)
    assertEquals(2, started[1].index)
  }

  @Test
  fun followingTheTargetWithDeviationShowsTheDeviationInExpert() {
    show(ExperienceMode.EXPERT)
    driver.reachPourStep()
    driver.pourFollowingTarget(FollowMode.DEVIATED)

    driver.waitFor("session_deviation", 30_000)
    driver.node("session_deviation").assertIsDisplayed()
  }
}
