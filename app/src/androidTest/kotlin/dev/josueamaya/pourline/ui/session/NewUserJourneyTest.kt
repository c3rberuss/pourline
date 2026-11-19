package dev.josueamaya.pourline.ui.session

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import dev.josueamaya.pourline.settings.ExperienceMode
import dev.josueamaya.pourline.ui.TestApp
import dev.josueamaya.pourline.ui.TestAppDependencies
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * El recorrido de un usuario nuevo (§1 del spec): elige el modo en el primer arranque, ve Preparar, conecta la
 * simulada, elige receta, hace los pasos previos, vierte siguiendo el objetivo, termina y ve el resumen guardado.
 */
class NewUserJourneyTest {
  @get:Rule
  val rule = createAndroidComposeRule<ComponentActivity>()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private val env = TestAppDependencies(context)
  private val driver = SessionDriver(rule, env)

  @After
  fun tearDown() = env.close()

  @Test
  fun aNewUserGoesFromTheFirstLaunchToASavedBrew() {
    env.skipTours()
    rule.setContent { TestApp(env) }

    // Primer arranque: elige "Básico".
    driver.waitFor("onboarding_basic")
    driver.node("onboarding_basic").performClick()
    // Paso 2: "¿Usas báscula?"; con báscula.
    driver.waitFor("onboarding_scale_with")
    driver.node("onboarding_scale_with").performClick()
    // Paso 3: "¿Qué molino usas?"; se omite.
    driver.waitFor("onboarding_grinder_skip")
    driver.node("onboarding_grinder_skip").performClick()
    driver.waitFor("prepare_start")
    assertEquals(ExperienceMode.BASIC, runBlocking { env.experienceSettings.mode.first() })

    driver.connect()
    driver.selectRecipe()
    driver.reachPourStep()
    driver.node("prep_start_timer").performClick()
    driver.waitFor("session_timer")
    driver.pourFollowingTarget()
    driver.finishFromButton()

    driver.node("session_summary").assertIsDisplayed()
    driver.node("summary_time").assertIsDisplayed()
    driver.node("summary_water").assertIsDisplayed()
    rule.waitUntil(10_000) { driver.exists("summary_details") }
    assertTrue(!driver.exists("summary_expert"))
    driver.node("summary_done").performClick()
    driver.waitFor("prepare_start")
  }
}
