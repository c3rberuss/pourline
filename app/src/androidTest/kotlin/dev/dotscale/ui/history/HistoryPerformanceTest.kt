package dev.dotscale.ui.history

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
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
import java.io.File

/**
 * Objetivos de rendimiento de SPEC-002 §5 con 500 preparaciones de 4 min en un `history-perf.db` aparte.
 * Cada tiempo se mide con una apertura de calentamiento que no cuenta y la mediana de 5 aperturas.
 * Pensado para un emulador con API 36 o superior.
 */
class HistoryPerformanceTest {
  @get:Rule
  val rule = createComposeRule()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private lateinit var db: HistoryDatabase
  private lateinit var repository: BrewRepository

  @Before
  fun setUp() {
    context.deleteDatabase(DB_NAME)
    db = HistoryDatabase.create(context, DB_NAME)
    repository = BrewRepository(db)
    runBlocking {
      repeat(BREWS) { i ->
        repository.save(
          TestBrews.finished(totalMs = 240_000, seed = i.toLong()),
          TestBrews.context(startedAtEpochMs = 1_700_000_000_000 + i * 3_600_000L),
        )
      }
    }
  }

  @After
  fun tearDown() {
    db.close()
    context.deleteDatabase(DB_NAME)
  }

  private fun median(values: List<Long>) = values.sorted()[values.size / 2]

  private fun rowsExist() = rule.onAllNodesWithTag("history_row").fetchSemanticsNodes().isNotEmpty()

  private fun chartExists() = rule.onAllNodesWithTag("brew_chart").fetchSemanticsNodes().isNotEmpty()

  @Test
  fun listAndDetailOpenFastAndTheDatabaseIsSmall() {
    var screen by mutableStateOf(Screen.Closed)
    var openId by mutableStateOf(0L)
    rule.setContent {
      DotScaleTheme(darkTheme = false) {
        when (screen) {
          Screen.Closed -> Unit
          Screen.List -> {
            val viewModel = remember { HistoryViewModel(repository) }
            HistoryScreen(viewModel, onBack = {}, onOpenBrew = { id ->
              openId = id
              screen = Screen.Detail
            })
          }
          Screen.Detail -> {
            val viewModel = remember(openId) { BrewDetailViewModel(repository, openId) }
            BrewDetailScreen(viewModel, onBack = { screen = Screen.List }, onEdit = {}, onCompare = {}, onSaveAsRecipe = {})
          }
        }
      }
    }

    val listTimes = mutableListOf<Long>()
    val detailTimes = mutableListOf<Long>()
    repeat(1 + SAMPLES) { run ->
      screen = Screen.Closed
      rule.waitForIdle()

      val listStart = System.nanoTime()
      screen = Screen.List
      rule.waitUntil(10_000) { rowsExist() }
      val listMs = (System.nanoTime() - listStart) / 1_000_000

      val detailStart = System.nanoTime()
      rule.onAllNodesWithTag("history_row")[0].performClick()
      rule.waitUntil(10_000) { chartExists() }
      val detailMs = (System.nanoTime() - detailStart) / 1_000_000

      if (run > 0) { // la primera apertura es de calentamiento
        listTimes += listMs
        detailTimes += detailMs
      }
    }

    val sizeBytes = listOf("", "-wal", "-shm").sumOf { File(context.getDatabasePath(DB_NAME).path + it).takeIf(File::exists)?.length() ?: 0L }
    val report = "lista=${listTimes} mediana=${median(listTimes)} ms; detalle=${detailTimes} mediana=${median(detailTimes)} ms; " +
      "db=${sizeBytes / 1024} KiB"
    Log.i(TAG, report)

    assertTrue("la lista tarda demasiado: $report", median(listTimes) < LIST_LIMIT_MS)
    assertTrue("el detalle tarda demasiado: $report", median(detailTimes) < DETAIL_LIMIT_MS)
    assertTrue("la base de datos ocupa demasiado: $report", sizeBytes < SIZE_LIMIT_BYTES)
  }

  private enum class Screen { Closed, List, Detail }

  private companion object {
    const val TAG = "HistoryPerf"
    const val DB_NAME = "history-perf.db"
    const val BREWS = 500
    const val SAMPLES = 5
    const val LIST_LIMIT_MS = 1_000L
    const val DETAIL_LIMIT_MS = 500L
    const val SIZE_LIMIT_BYTES = 10L * 1024 * 1024
  }
}
