package dev.josueamaya.pourline.ui.session

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import dev.josueamaya.pourline.brew.BrewPhase
import dev.josueamaya.pourline.session.BrewPlan
import dev.josueamaya.pourline.settings.ExperienceMode
import dev.josueamaya.pourline.ui.TestApp
import dev.josueamaya.pourline.ui.TestAppDependencies
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** La cuenta regresiva de "Empezar timer" en la sesión: con báscula y sin ella, en Básico y en Experto. */
class CountdownSessionTest {
  @get:Rule
  val rule = createAndroidComposeRule<ComponentActivity>()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private val env = TestAppDependencies(context, countdownSeconds = 3)
  private val driver = SessionDriver(rule, env)
  private val manager get() = env.scaleSession

  @After
  fun tearDown() = env.close()

  private fun showWithScale(mode: ExperienceMode) {
    env.skipOnboarding(mode)
    rule.setContent { TestApp(env) }
    driver.connect()
    driver.selectRecipe()
    driver.reachPourStep()
  }

  private fun showScaleless(mode: ExperienceMode) {
    env.skipOnboarding(mode)
    rule.setContent { TestApp(env) }
    driver.connect()
    driver.selectRecipe()
    driver.start()
    rule.runOnUiThread { manager.startScalelessSession(BrewPlan(doseG = 15.0, ratio = 16.0)) }
    driver.waitFor("scaleless_ready")
  }

  /** Pulsa [startTag], ve la cuenta desde 3 y espera a que la sesión pase a `Active` con [activeTag]. */
  private fun countDownAndStart(startTag: String, activeTag: String) {
    driver.node(startTag).performClick()
    driver.waitFor("session_countdown")
    driver.node("session_countdown_value").assertTextEquals("3")
    assertEquals(BrewPhase.Armed, manager.ui.value.brew.phase)
    driver.waitFor(activeTag, 10_000)
    driver.waitGone("session_countdown")
    assertTrue(manager.ui.value.brew.phase is BrewPhase.Active)
  }

  @Test
  fun withScaleInBasicTheTimerStartsAfterTheCountdown() {
    showWithScale(ExperienceMode.BASIC)
    countDownAndStart("prep_start_timer", "session_timer")
  }

  @Test
  fun withScaleInExpertTheTimerStartsAfterTheCountdown() {
    showWithScale(ExperienceMode.EXPERT)
    countDownAndStart("prep_start_timer", "session_timer")
  }

  @Test
  fun withoutScaleInBasicTheTimerStartsAfterTheCountdown() {
    showScaleless(ExperienceMode.BASIC)
    countDownAndStart("scaleless_start", "scaleless_active")
  }

  @Test
  fun withoutScaleInExpertTheTimerStartsAfterTheCountdown() {
    showScaleless(ExperienceMode.EXPERT)
    countDownAndStart("scaleless_start", "scaleless_active")
  }

  @Test
  fun cancelKeepsTheSessionReady() {
    showScaleless(ExperienceMode.BASIC)
    driver.node("scaleless_start").performClick()
    driver.waitFor("session_countdown")

    driver.node("session_countdown_cancel").performClick()
    driver.waitGone("session_countdown")
    driver.node("scaleless_ready").assertIsDisplayed()
    Thread.sleep(3_500)
    rule.waitForIdle()
    assertEquals(BrewPhase.Armed, manager.ui.value.brew.phase)
  }

  @Test
  fun backDuringTheCountdownOnlyCancelsIt() {
    showWithScale(ExperienceMode.BASIC)
    driver.node("prep_start_timer").performClick()
    driver.waitFor("session_countdown")

    rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
    driver.waitGone("session_countdown")
    driver.node("session_prep").assertIsDisplayed()
    assertEquals(BrewPhase.Armed, manager.ui.value.brew.phase)
  }
}
