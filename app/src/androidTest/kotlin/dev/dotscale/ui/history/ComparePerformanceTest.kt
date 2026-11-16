package dev.dotscale.ui.history

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.test.platform.app.InstrumentationRegistry
import dev.dotscale.coffee.CoffeeRepository
import dev.dotscale.coffee.PhotoFiles
import dev.dotscale.history.BrewRepository
import dev.dotscale.history.HistoryDatabase
import dev.dotscale.history.TestBrews
import dev.dotscale.ui.theme.DotScaleTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Objetivo de rendimiento de SPEC-003 §5: la vista de comparación de dos preparaciones de 10 min muestra sus
 * dos gráficas en menos de 500 ms. Base en un archivo aparte (`compare-perf.db`); el tiempo va desde que la
 * pantalla entra en la composición hasta que existen `compare_chart_water` y `compare_chart_flow`, con una
 * apertura de calentamiento que no cuenta y la mediana de 5. Pensado para un emulador con API 36 o superior.
 */
class ComparePerformanceTest {
  @get:Rule
  val rule = createComposeRule()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private lateinit var db: HistoryDatabase
  private lateinit var repository: BrewRepository
  private var aId = 0L
  private var bId = 0L

  @Before
  fun setUp() {
    context.deleteDatabase(DB_NAME)
    db = HistoryDatabase.create(context, DB_NAME)
    repository = BrewRepository(db)
    runBlocking {
      aId = repository.save(TestBrews.finished(totalMs = TEN_MINUTES_MS, seed = 1), TestBrews.context(startedAtEpochMs = 1_000))
      bId = repository.save(TestBrews.finished(totalMs = TEN_MINUTES_MS, seed = 2), TestBrews.context(startedAtEpochMs = 2_000))
    }
  }

  @After
  fun tearDown() {
    db.close()
    context.deleteDatabase(DB_NAME)
  }

  private fun chartsExist() =
    rule.onAllNodesWithTag("compare_chart_water").fetchSemanticsNodes().isNotEmpty() &&
      rule.onAllNodesWithTag("compare_chart_flow").fetchSemanticsNodes().isNotEmpty()

  @Test
  fun twoTenMinuteBrewsShowTheirChartsFast() {
    var open by mutableStateOf(false)
    rule.setContent {
      DotScaleTheme(darkTheme = false) {
        if (open) {
          // Un ViewModel nuevo por apertura: así se mide también la carga y la decodificación de las dos curvas.
          val viewModel = remember { BrewCompareViewModel(repository, CoffeeRepository(db, PhotoFiles { }), aId, bId) }
          BrewCompareScreen(viewModel, onBack = {})
        }
      }
    }

    val times = mutableListOf<Long>()
    repeat(1 + SAMPLES) { run ->
      open = false
      rule.waitForIdle()

      val start = System.nanoTime()
      open = true
      rule.waitUntil(10_000) { chartsExist() }
      val ms = (System.nanoTime() - start) / 1_000_000

      if (run > 0) times += ms // la primera apertura es de calentamiento
    }

    val median = times.sorted()[times.size / 2]
    val report = "aperturas=$times mediana=$median ms"
    Log.i(TAG, report)
    assertTrue("la comparación tarda demasiado: $report", median < LIMIT_MS)
  }

  private companion object {
    const val TAG = "ComparePerf"
    const val DB_NAME = "compare-perf.db"
    const val TEN_MINUTES_MS = 600_000L
    const val SAMPLES = 5
    const val LIMIT_MS = 500L
  }
}
