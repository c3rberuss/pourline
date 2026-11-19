package dev.josueamaya.pourline.ui.tour

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import dev.josueamaya.pourline.R
import dev.josueamaya.pourline.brew.BrewPhase
import dev.josueamaya.pourline.session.BrewPlan
import dev.josueamaya.pourline.ui.TestApp
import dev.josueamaya.pourline.ui.TestAppDependencies
import dev.josueamaya.pourline.ui.session.SessionDriver
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Los recorridos de la sesión (pasos previos, sin báscula y resumen) con la aplicación entera y la báscula simulada. */
class SessionTourTest {
  @get:Rule
  val rule = createAndroidComposeRule<ComponentActivity>()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private var env = TestAppDependencies(context)
  private var driver = SessionDriver(rule, env)
  private val manager get() = env.scaleSession

  @After
  fun tearDown() = env.close()

  /** Sustituye las dependencias por unas con [countdownSeconds] de cuenta regresiva. */
  private fun withCountdown(countdownSeconds: Int) {
    env.close()
    env = TestAppDependencies(context, countdownSeconds = countdownSeconds)
    driver = SessionDriver(rule, env)
  }

  private fun titleIs(resource: Int) = assertEquals(context.getString(resource), rule.textOf("tour_title"))

  private fun back() = rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }

  /** Abre la sesión sin báscula (con la V60 o sin receta) y espera al estado listo. */
  private fun openScalelessSession(recipe: Boolean, content: () -> Unit = { rule.setContent { TestApp(env) } }) {
    content()
    driver.connect()
    if (recipe) driver.selectRecipe() else rule.runOnUiThread { manager.selectTarget(null) }
    driver.start()
    rule.runOnUiThread { manager.startScalelessSession(BrewPlan(doseG = 15.0, ratio = 16.0)) }
    driver.waitFor("scaleless_ready")
  }

  @Test
  fun thePrepTourShowsBeforePouring() {
    env.skipOnboarding(showTours = listOf(Tours.sessionPrep))
    rule.setContent { TestApp(env) }
    driver.connect()
    driver.selectRecipe()
    driver.start()

    rule.waitForTour("1/6", env.tourSettings.seen.value)
    titleIs(R.string.tour_session_prep_stepper_title)
  }

  @Test
  fun goingActiveClosesThePrepTourWithoutMarking() {
    env.skipOnboarding(showTours = listOf(Tours.sessionPrep))
    rule.setContent { TestApp(env) }
    driver.connect()
    driver.selectRecipe()
    driver.start()
    rule.waitForTour("1/6", env.tourSettings.seen.value)
    rule.tapTourNext()
    rule.waitForTour("2/6")

    // Llevar la preparación hasta el vertido desde el gestor, con el recorrido abierto.
    rule.runOnUiThread {
      manager.prepSkip()
      manager.prepSkipDose()
      manager.prepSkip()
      manager.prepStartTimer()
    }
    rule.waitUntil(10_000) { manager.ui.value.brew.phase is BrewPhase.Active }

    rule.waitUntil(5_000) { !rule.tourVisible() }
    val seen = env.tourSettings.seen.value.filter { it.startsWith("session_prep.") }
    assertEquals(listOf("session_prep.stepper"), seen)
  }

  @Test
  fun systemBackDuringThePrepTourDoesNotLeaveTheSession() {
    env.skipOnboarding(showTours = listOf(Tours.sessionPrep))
    rule.setContent { TestApp(env) }
    driver.connect()
    driver.selectRecipe()
    driver.start()
    rule.waitForTour("1/6")

    back()

    rule.waitUntil(5_000) { !rule.tourVisible() }
    rule.waitUntil(5_000) {
      rule.onAllNodesWithText(context.getString(R.string.tour_skipped_hint)).fetchSemanticsNodes().isNotEmpty()
    }
    assertTrue("La sesión debería seguir abierta", driver.exists("session_prep"))
  }

  @Test
  fun theScalelessTourShowsWhenReady() {
    env.skipOnboarding(showTours = listOf(Tours.sessionScaleless))
    openScalelessSession(recipe = false)

    // Sin receta, con el plan: plan, empezar, dial y salir.
    rule.waitForTour("1/4", env.tourSettings.seen.value)
    titleIs(R.string.tour_session_scaleless_plan_title)
  }

  @Test
  fun theScalelessTourWithARecipeHasNoPlanStep() {
    env.skipOnboarding(showTours = listOf(Tours.sessionScaleless))
    openScalelessSession(recipe = true)

    rule.waitForTour("1/3", env.tourSettings.seen.value)
    titleIs(R.string.tour_session_scaleless_start_title)
  }

  @Test
  fun noTourDuringTheCountdown() {
    withCountdown(5)
    env.skipOnboarding(showTours = listOf(Tours.sessionScaleless, Tours.summary))
    val tester = StateRestorationTester(rule)
    openScalelessSession(recipe = false) { tester.setContent { TestApp(env) } }
    rule.waitForTour("1/4", env.tourSettings.seen.value)
    rule.completeTour()

    driver.node("scaleless_start").performClick()
    driver.waitFor("session_countdown")
    // Una apertura nueva con la cuenta en marcha: todo vuelve a estar pendiente y no debe salir nada.
    env.tourSettings.reset()
    tester.emulateSavedInstanceStateRestore()
    driver.waitFor("session_countdown")
    rule.assertNoTourFor()

    driver.waitFor("scaleless_active", 15_000)
    driver.waitGone("session_countdown")
    assertTrue(manager.ui.value.brew.phase is BrewPhase.Active)
    rule.assertNoTourFor()
  }

  @Test
  fun theSummaryTourWaitsForTheSave() {
    env.skipOnboarding(showTours = listOf(Tours.summary))
    rule.setContent { TestApp(env) }
    driver.connect()
    driver.selectRecipe()
    driver.reachPourStep()
    driver.node("prep_start_timer").performClick()
    driver.waitFor("session_timer")
    driver.node("session_finish").performClick()
    driver.waitFor("session_summary")

    rule.waitForTour("1/3", env.tourSettings.seen.value)
    // El recorrido solo sale cuando "Añadir detalles" ya existe (la preparación está guardada).
    assertTrue(driver.exists("summary_details"))
    titleIs(R.string.tour_summary_rating_title)
  }
}
