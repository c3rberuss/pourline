package dev.josueamaya.pourline.ui.session

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.platform.app.InstrumentationRegistry
import dev.josueamaya.pourline.R
import dev.josueamaya.pourline.session.PrepStep
import dev.josueamaya.pourline.settings.ExperienceMode
import dev.josueamaya.pourline.settings.ThemeMode
import dev.josueamaya.pourline.sim.FollowMode
import dev.josueamaya.pourline.ui.TestApp
import dev.josueamaya.pourline.ui.TestAppDependencies
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * No comprueba nada: guarda capturas de Preparar, de la sesión (Básico y Experto, en claro y en oscuro) y del resumen
 * para revisarlas a ojo. Solo corre con `-e screenshots true`; las deja en `files/session-screens` del almacenamiento
 * externo de la app (se lanza con `am instrument` para que desinstalar no las borre).
 */
class SessionScreenshotCapture {
  @get:Rule
  val rule = createAndroidComposeRule<ComponentActivity>()

  private val instrumentation = InstrumentationRegistry.getInstrumentation()
  private val context = instrumentation.targetContext
  private val env = TestAppDependencies(context)
  private val driver = SessionDriver(rule, env)
  private val dir = File(context.getExternalFilesDir(null), "session-screens").also { it.mkdirs() }

  @After
  fun tearDown() = env.close()

  private fun shot(name: String) {
    rule.waitForIdle()
    val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return
    File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
  }

  private fun run(mode: ExperienceMode, theme: ThemeMode, prefix: String) {
    assumeTrue("solo con -e screenshots true", InstrumentationRegistry.getArguments().getString("screenshots") == "true")
    env.themeSettings.setMode(theme)
    env.skipOnboarding(mode)
    rule.setContent { TestApp(env) }
    driver.connect()
    driver.selectRecipe()
    shot("$prefix-1-prepare")
    driver.start()
    shot("$prefix-2-tare")
    driver.tare()
    driver.waitStep(PrepStep.DOSE)
    shot("$prefix-3-dose")
    driver.addWeight(15.0)
    driver.waitStep(PrepStep.TARE_FOR_POUR)
    driver.tare()
    driver.waitStep(PrepStep.POUR)
    shot("$prefix-4-pour-ready")
    driver.node("prep_start_timer").performClick()
    driver.waitFor("session_timer")
    rule.runOnUiThread { env.scaleSession.simulateTargetPour(FollowMode.FAITHFUL) }
    runFor(55_000)
    shot("$prefix-5-pouring")
    if (mode == ExperienceMode.EXPERT) {
      driver.node("session_dial_legend").performClick()
      runFor(800)
      shot("$prefix-5b-legend")
      rule.onNodeWithContentDescription(context.getString(R.string.action_close)).performClick()
      runFor(800)
      driver.node("session_dial").performTouchInput { swipeLeft() }
      runFor(500)
      shot("$prefix-6-chart")
    }
    driver.finishFromButton()
    runFor(1_500)
    shot("$prefix-7-summary")
  }

  /** En un test de Compose la interfaz solo avanza mientras el test espera con `waitUntil`, no con `Thread.sleep`. */
  private fun runFor(ms: Long) {
    val end = System.nanoTime() + ms * 1_000_000
    rule.waitUntil(ms + 30_000) { System.nanoTime() >= end }
  }

  @Test
  fun basicLight() = run(ExperienceMode.BASIC, ThemeMode.LIGHT, "basic-light")

  @Test
  fun basicDark() = run(ExperienceMode.BASIC, ThemeMode.DARK, "basic-dark")

  @Test
  fun expertLight() = run(ExperienceMode.EXPERT, ThemeMode.LIGHT, "expert-light")

  @Test
  fun expertDark() = run(ExperienceMode.EXPERT, ThemeMode.DARK, "expert-dark")
}
