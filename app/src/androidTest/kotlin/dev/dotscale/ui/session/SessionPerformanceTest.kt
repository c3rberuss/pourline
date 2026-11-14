package dev.dotscale.ui.session

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.FrameMetrics
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.platform.app.InstrumentationRegistry
import dev.dotscale.history.BrewMethod
import dev.dotscale.recipe.Recipe
import dev.dotscale.recipe.RecipeStage
import dev.dotscale.recipe.StageType
import dev.dotscale.settings.ExperienceMode
import dev.dotscale.sim.FollowMode
import dev.dotscale.ui.TestApp
import dev.dotscale.ui.TestAppDependencies
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.Collections

/**
 * Fluidez de la sesión mientras vierte (D40): se miden con `FrameMetrics` los fotogramas de 120 s de vertido, tras 5 s
 * de calentamiento, en Básico y en Experto con la gráfica (página 2) a la vista. El percentil 95 del tiempo total de
 * fotograma debe ser de 32 ms o menos. Los números van al logcat con la etiqueta `SessionPerf`.
 */
class SessionPerformanceTest {
  @get:Rule
  val rule = createAndroidComposeRule<ComponentActivity>()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private val env = TestAppDependencies(context)
  private val driver = SessionDriver(rule, env)

  @After
  fun tearDown() = env.close()

  /** Un solo vertido de 250 g en 200 s: dura más que el calentamiento más la medida. */
  private fun longRecipe() = Recipe(
    name = "Rendimiento",
    method = BrewMethod.V60,
    doseG = 15.0,
    stages = listOf(
      RecipeStage(name = "Vertido", startMs = 0L, targetWaterG = 250.0, durationMs = 200_000L, type = StageType.POUR, instruction = null),
    ),
  )

  /**
   * Deja correr la app [ms] de reloj real. En un test de Compose la recomposición solo avanza mientras el test espera
   * con `waitUntil`; con `Thread.sleep` la interfaz se queda quieta y no hay fotogramas que medir.
   */
  private fun runFor(ms: Long) {
    val end = System.nanoTime() + ms * 1_000_000
    rule.waitUntil(ms + 30_000) { System.nanoTime() >= end }
  }

  private fun measure(mode: ExperienceMode, chartPage: Boolean) {
    runBlocking { env.recipes.save(longRecipe()) }
    env.skipOnboarding(mode)
    rule.setContent { TestApp(env) }
    driver.connect()
    driver.selectRecipe("Rendimiento")
    driver.reachPourStep()
    driver.node("prep_start_timer").performClick()
    driver.waitFor("session_timer")
    rule.runOnUiThread { env.scaleSession.simulateTargetPour(FollowMode.FAITHFUL) }
    if (chartPage) {
      driver.node("session_dial").performTouchInput { swipeLeft() }
      driver.waitFor("session_chart")
    }

    runFor(WARMUP_MS)
    val frames = Collections.synchronizedList(mutableListOf<Long>())
    // Por fase (UI, medida, dibujo, sincronización, comandos y GPU) solo de los fotogramas lentos, para ver dónde va el tiempo.
    val phases = Collections.synchronizedList(mutableListOf<LongArray>())
    val listener = android.view.Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
      val total = metrics.getMetric(FrameMetrics.TOTAL_DURATION)
      frames += total
      if (total > 32_000_000L) {
        phases += longArrayOf(
          metrics.getMetric(FrameMetrics.INPUT_HANDLING_DURATION) + metrics.getMetric(FrameMetrics.ANIMATION_DURATION),
          metrics.getMetric(FrameMetrics.LAYOUT_MEASURE_DURATION),
          metrics.getMetric(FrameMetrics.DRAW_DURATION),
          metrics.getMetric(FrameMetrics.SYNC_DURATION),
          metrics.getMetric(FrameMetrics.COMMAND_ISSUE_DURATION),
          metrics.getMetric(FrameMetrics.SWAP_BUFFERS_DURATION),
          metrics.getMetric(FrameMetrics.GPU_DURATION),
        )
      }
    }
    rule.runOnUiThread { rule.activity.window.addOnFrameMetricsAvailableListener(listener, Handler(Looper.getMainLooper())) }
    runFor(MEASURE_MS)
    rule.runOnUiThread { rule.activity.window.removeOnFrameMetricsAvailableListener(listener) }

    val ms = frames.toList().map { it / 1_000_000.0 }.sorted()
    assertTrue("no se midió ningún fotograma", ms.isNotEmpty())
    fun percentile(p: Double) = ms[((ms.size - 1) * p).toInt()]
    val p50 = percentile(0.5)
    val p95 = percentile(0.95)
    val p90 = percentile(0.90)
    val p99 = percentile(0.99)
    val slow = ms.count { it > 32.0 }
    val slowPct = 100.0 * slow / ms.size
    Log.i(
      TAG,
      "mode=$mode chart=$chartPage frames=${ms.size} p50=%.1f p90=%.1f p95=%.1f p99=%.1f max=%.1f over32=$slow (%.2f%%)"
        .format(p50, p90, p95, p99, ms.last(), slowPct),
    )
    if (phases.isNotEmpty()) {
      val names = listOf("input+anim", "layout", "draw", "sync", "commands", "swap", "gpu")
      val means = names.indices.map { i -> phases.toList().map { it[i] }.average() / 1_000_000.0 }
      Log.i(TAG, "slow frames (${phases.size}) mean ms: " + names.zip(means).joinToString { (n, v) -> "$n=%.1f".format(v) })
    }
    assertTrue("p95 = %.1f ms (límite 32 ms) en $mode".format(p95), p95 <= 32.0)
    assertTrue("%.2f %% de fotogramas por encima de 32 ms (límite 5 %%) en $mode".format(slowPct), slowPct <= 5.0)
    assertTrue("fotograma de %.0f ms (límite 250 ms) en $mode".format(ms.last()), ms.last() <= 250.0)
  }

  @Test
  fun basicPourKeepsTheFrameTimeUnderTheLimit() = measure(ExperienceMode.BASIC, chartPage = false)

  @Test
  fun expertPourWithTheChartKeepsTheFrameTimeUnderTheLimit() = measure(ExperienceMode.EXPERT, chartPage = true)

  private companion object {
    const val TAG = "SessionPerf"
    const val WARMUP_MS = 5_000L
    const val MEASURE_MS = 120_000L
  }
}
