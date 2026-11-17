package dev.josueamaya.pourline.ui.session

import android.Manifest
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import dev.josueamaya.pourline.R
import dev.josueamaya.pourline.Tab
import dev.josueamaya.pourline.brew.BrewPhase
import dev.josueamaya.pourline.brew.FinishReason
import dev.josueamaya.pourline.history.BrewFilter
import dev.josueamaya.pourline.settings.ExperienceMode
import dev.josueamaya.pourline.ui.TestApp
import dev.josueamaya.pourline.ui.TestAppDependencies
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicLong

/**
 * El recorrido de la preparación sin báscula (SPEC-007 §5) con la interfaz real: desde Preparar, sin báscula, hasta el
 * Historial, con Room en memoria y un reloj de prueba. Los avances se esperan con `waitUntil`, nunca con `sleep`.
 */
class ScalelessJourneyTest {
  @get:Rule
  val rule = createAndroidComposeRule<ComponentActivity>()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private val now = AtomicLong(10_000)
  private val env = TestAppDependencies(context, clock = { now.get() })
  private val driver = SessionDriver(rule, env)
  private val manager get() = env.scaleSession

  @Before
  fun grantNotifications() {
    // Sin el permiso, "Empezar sin báscula" abriría el diálogo del sistema delante de la app.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
    }
  }

  @After
  fun tearDown() = env.close()

  private fun at(ms: Long) {
    val t0 = manager.ui.value.brew.t0MonoMs!!
    now.set(t0 + ms)
    rule.waitUntil(10_000) { manager.ui.value.elapsedMs == ms }
    rule.waitForIdle()
  }

  private fun unmerged(tag: String) = rule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

  private fun savedBrews() = runBlocking { env.brews.list(BrewFilter()).first() }

  /** Elige la V60 clásica en el selector de Preparar, sin báscula conectada. */
  private fun pickRecipeFromPrepare() {
    val name = runBlocking { env.recipes.list().first().first { it.name.contains("V60") }.name }
    driver.waitFor("prepare_recipe")
    driver.node("prepare_recipe").performClick()
    driver.waitFor("target_picker")
    rule.onAllNodesWithText(name)[0].performClick()
    rule.waitUntil(5_000) { manager.ui.value.target != null }
  }

  @Test
  fun aScalelessV60FromPrepareToTheHistory() {
    env.skipOnboarding(ExperienceMode.EXPERT)
    rule.setContent { TestApp(env) }
    pickRecipeFromPrepare()

    driver.waitFor("prepare_start_scaleless")
    driver.node("prepare_start_scaleless").performClick()
    driver.waitFor("scaleless_ready")
    assertTrue(manager.ui.value.scaleless)

    driver.node("scaleless_start").performClick()
    driver.waitFor("scaleless_active")

    // 116 s: la V60 clásica ya no tiene pasos y la sesión sigue en curso con el aviso.
    at(116_000)
    driver.waitFor("scaleless_done_hint")
    assertTrue(manager.ui.value.brew.phase is BrewPhase.Active)

    driver.finishFromButton()
    driver.node("summary_scaleless").assertIsDisplayed()
    rule.waitUntil(10_000) { savedBrews().size == 1 }
    val saved = savedBrews().single()
    assertTrue(saved.scaleless)
    assertEquals(116_000L, saved.totalTimeMs)

    // Valoración de 4 estrellas desde "Añadir detalles".
    driver.waitFor("summary_details")
    driver.node("summary_details").performClick()
    driver.waitFor("brew_edit_save")
    driver.node("brew_edit_rating_4").performScrollTo().performClick()
    driver.node("brew_edit_save").performScrollTo().performClick()
    driver.waitFor("summary_done")
    driver.node("summary_done").performClick()
    driver.waitFor("prepare_start")

    driver.node(Tab.HISTORY.testTag).performClick()
    driver.waitFor("history_row")
    rule.onAllNodesWithTag("history_row").assertCountEquals(1)
    assertTrue(unmerged("history_row_scaleless"))
    assertTrue(unmerged("history_row_rating"))
    assertEquals(4, runBlocking { env.brews.get(saved.id) }!!.entity.rating)

    // Las preparaciones sin báscula no se ofrecen como referencia: la pestaña de preparaciones del selector sale vacía.
    driver.node(Tab.PREPARE.testTag).performClick()
    driver.waitFor("prepare_recipe")
    driver.node("prepare_recipe").performClick()
    driver.waitFor("target_picker")
    // "Historial" también es una pestaña de la barra inferior: se busca la del selector.
    rule.onNode(hasText(context.getString(R.string.target_picker_history)) and hasAnyAncestor(hasTestTag("target_picker_tabs"))).performClick()
    driver.waitFor("target_picker_empty")
    assertFalse(driver.exists("history_row"))
  }

  @Test
  fun aScalelessSessionEndsAtFifteenMinutes() {
    env.skipOnboarding(ExperienceMode.EXPERT)
    rule.setContent { TestApp(env) }
    pickRecipeFromPrepare()

    driver.waitFor("prepare_start_scaleless")
    driver.node("prepare_start_scaleless").performClick()
    driver.waitFor("scaleless_ready")
    driver.node("scaleless_start").performClick()
    driver.waitFor("scaleless_active")

    val t0 = manager.ui.value.brew.t0MonoMs!!
    now.set(t0 + 900_000)
    driver.waitFor("session_summary", 20_000)

    val finished = manager.ui.value.brew.phase as BrewPhase.Finished
    assertEquals(FinishReason.MAX_DURATION, finished.summary.reason)
    driver.node("summary_reason").assertIsDisplayed()
    rule.waitUntil(10_000) { savedBrews().size == 1 }
    val saved = savedBrews().single()
    assertTrue(saved.scaleless)
    assertEquals(900_000L, saved.totalTimeMs)
    assertNotNull(saved.id)
    assertNull(manager.ui.value.lastFit)
  }
}
