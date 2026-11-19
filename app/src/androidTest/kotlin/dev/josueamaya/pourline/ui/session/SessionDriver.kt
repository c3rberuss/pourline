package dev.josueamaya.pourline.ui.session

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import dev.josueamaya.pourline.R
import dev.josueamaya.pourline.scale.ConnectionState
import dev.josueamaya.pourline.session.PrepStep
import dev.josueamaya.pourline.session.ScaleTarget
import dev.josueamaya.pourline.settings.ReferenceKey
import dev.josueamaya.pourline.sim.FollowMode
import dev.josueamaya.pourline.ui.TestAppDependencies
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Lleva la app de pruebas por la sesión guiada con la báscula simulada: conectar, elegir receta, "Empezar", los pasos
 * previos y el vertido. Los tests de sesión, de diseño, de rendimiento y del recorrido del usuario nuevo lo comparten.
 */
class SessionDriver(private val rule: ComposeTestRule, val env: TestAppDependencies) {
  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private val manager get() = env.scaleSession

  fun exists(tag: String) = rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

  fun waitFor(tag: String, timeoutMs: Long = 10_000) = rule.waitUntil(timeoutMs) { exists(tag) }

  fun waitGone(tag: String, timeoutMs: Long = 10_000) = rule.waitUntil(timeoutMs) { !exists(tag) }

  fun node(tag: String): SemanticsNodeInteraction = rule.onNodeWithTag(tag)

  /** Conecta la báscula simulada sin pasar por la hoja de conexión. */
  fun connect() {
    rule.runOnUiThread { manager.connect(ScaleTarget.Simulated("Simulada")) }
    rule.waitUntil(10_000) { manager.ui.value.connection == ConnectionState.Ready }
  }

  /** El id de la receta predefinida cuyo nombre contiene [part] (por ejemplo, "V60"). */
  fun recipeId(part: String = "V60"): Long = runBlocking { env.recipes.list().first().first { it.name.contains(part) }.id }

  fun selectRecipe(part: String = "V60") {
    val id = recipeId(part)
    rule.runOnUiThread { manager.selectTarget(ReferenceKey.Recipe(id)) }
    rule.waitUntil(5_000) { manager.ui.value.target != null }
  }

  /** "Empezar" en Preparar: abre la sesión en el paso 1. */
  fun start() {
    waitFor("prepare_start")
    // Con marcas en la tarjeta de molienda ("aproximado") el botón puede quedar bajo el pliegue.
    runCatching { node("prepare_start").performScrollTo() }
    node("prepare_start").performClick()
    waitFor("session_prep")
  }

  fun step(): PrepStep? = manager.ui.value.prep?.step

  fun waitStep(step: PrepStep, timeoutMs: Long = 15_000) = rule.waitUntil(timeoutMs) { this.step() == step }

  fun tare() {
    waitFor("prep_tare")
    node("prep_tare").performClick()
  }

  /** Pone [grams] en la báscula simulada, a través del menú ⋯ ("+15 g", "+100 g" o "+300 g") o directamente. */
  fun addWeight(grams: Double) {
    rule.runOnUiThread { manager.simulateAdd(grams) }
  }

  /** Con la báscula simulada: el menú ⋯, el botón de simulación [tag] y cierra la hoja. */
  fun simulateFromSheet(tag: String) {
    node("session_options").performClick()
    waitFor(tag)
    node(tag).performClick()
    closeSheet()
  }

  fun closeSheet() {
    rule.onNodeWithContentDescription(context.getString(R.string.action_close)).performClick()
    waitGone("session_options_sheet")
  }

  /** Recorre los pasos 1 a 3 con la báscula simulada y deja la sesión armada (paso de verter). */
  fun reachPourStep(dose: Double = 15.0) {
    start()
    tare()
    waitStep(PrepStep.DOSE)
    addWeight(dose)
    waitStep(PrepStep.TARE_FOR_POUR)
    tare()
    waitStep(PrepStep.POUR)
  }

  /** Arma la sesión y lanza el vertido que sigue el objetivo; espera a `Active`. */
  fun pourFollowingTarget(mode: FollowMode = FollowMode.FAITHFUL) {
    rule.runOnUiThread { manager.simulateTargetPour(mode) }
    waitFor("session_timer", 20_000)
  }

  fun finishFromButton() {
    node("session_finish").performClick()
    waitFor("session_summary")
  }
}
