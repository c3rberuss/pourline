package dev.dotscale.ui.history

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import dev.dotscale.R
import dev.dotscale.history.BrewDetails
import dev.dotscale.history.BrewMethod
import dev.dotscale.history.BrewRepository
import dev.dotscale.history.HistoryDatabase
import dev.dotscale.history.TestBrews
import dev.dotscale.ui.theme.DotScaleTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class HistoryScreenTest {
  @get:Rule
  val rule = createComposeRule()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private lateinit var db: HistoryDatabase
  private lateinit var repository: BrewRepository
  private var opened: Long? = null

  @Before
  fun setUp() {
    db = HistoryDatabase.inMemory(context)
    repository = BrewRepository(db)
  }

  @After
  fun tearDown() = db.close()

  private fun seed(
    startedAt: Long,
    name: String? = null,
    coffee: String? = null,
    method: BrewMethod? = null,
    simulated: Boolean = false,
    scored: Boolean = true,
  ): Long = runBlocking {
    val id = repository.save(
      TestBrews.finished(totalMs = 60_000, scored = scored),
      TestBrews.context(startedAtEpochMs = startedAt, simulated = simulated),
    )
    if (name != null || coffee != null || method != null) {
      repository.updateDetails(id, BrewDetails(name = name, coffee = coffee, method = method))
    }
    id
  }

  private fun show() {
    val viewModel = HistoryViewModel(repository)
    rule.setContent {
      DotScaleTheme(darkTheme = false) {
        HistoryScreen(viewModel, onBack = {}, onOpenBrew = { opened = it })
      }
    }
  }

  private fun rows() = rule.onAllNodesWithTag("history_row")

  private fun waitForRows(count: Int) = rule.waitUntil(5_000) { rows().fetchSemanticsNodes().size == count }

  /** El texto aparece también en las filas; el del menú es el que está dentro del popup. */
  private fun clickMenuOption(text: String) =
    rule.onNode(hasText(text) and hasAnyAncestor(isPopup())).performClick()

  @Test
  fun emptyStateWhenThereAreNoBrews() {
    show()
    rule.waitUntil(5_000) { rule.onAllNodesWithText(context.getString(R.string.history_empty)).fetchSemanticsNodes().isNotEmpty() }
    rows().assertCountEquals(0)
  }

  @Test
  fun rowsAreShownNewestFirst() {
    seed(startedAt = 1_000, name = "Primera")
    seed(startedAt = 3_000, name = "Tercera")
    seed(startedAt = 2_000, name = "Segunda")
    show()
    waitForRows(3)

    rows()[0].assertTextContains("Tercera", substring = true)
    rows()[1].assertTextContains("Segunda", substring = true)
    rows()[2].assertTextContains("Primera", substring = true)
  }

  @Test
  fun simulatedAndUnscoredBrewsAreLabeled() {
    seed(startedAt = 1_000, simulated = true, scored = false)
    show()
    waitForRows(1)
    rows()[0].assertTextContains(context.getString(R.string.history_row_simulated), substring = true)
    rows()[0].assertTextContains(context.getString(R.string.history_row_no_score), substring = true)
  }

  @Test
  fun theFitTagAppearsOnlyOnBrewsMadeWithAReference() {
    runBlocking {
      repository.save(TestBrews.finished(totalMs = 60_000), TestBrews.context(startedAtEpochMs = 1_000, target = TestBrews.usedTarget(score = 86)))
      repository.save(TestBrews.finished(totalMs = 60_000), TestBrews.context(startedAtEpochMs = 2_000))
    }
    show()
    waitForRows(2)

    // La más reciente (2_000) no lleva referencia; la antigua sí.
    rule.onAllNodesWithTag("history_row_fit", useUnmergedTree = true).assertCountEquals(1)
    rows()[1].assertTextContains(context.getString(R.string.history_row_fit, 86), substring = true)
  }

  @Test
  fun clickingARowOpensItsDetail() {
    val id = seed(startedAt = 1_000, name = "Una")
    show()
    waitForRows(1)
    rows()[0].performClick()
    rule.waitUntil(5_000) { opened == id }
  }

  @Test
  fun filterByCoffee() {
    seed(startedAt = 1_000, name = "A", coffee = "Etiopía")
    seed(startedAt = 2_000, name = "B", coffee = "Kenia")
    seed(startedAt = 3_000, name = "C", coffee = "Etiopía")
    show()
    waitForRows(3)

    rule.onNodeWithTag("history_filter_coffee").performClick()
    clickMenuOption("Etiopía")
    waitForRows(2)
    rows()[0].assertTextContains("C", substring = true)
    rule.onNodeWithTag("history_filter_coffee").assertTextContains("Etiopía")
  }

  @Test
  fun filterByMethod() {
    seed(startedAt = 1_000, name = "A", method = BrewMethod.V60)
    seed(startedAt = 2_000, name = "B", method = BrewMethod.CHEMEX)
    show()
    waitForRows(2)

    rule.onNodeWithTag("history_filter_method").performClick()
    clickMenuOption("Chemex")
    waitForRows(1)
    rows()[0].assertTextContains("B", substring = true)
  }

  @Test
  fun aFilterWithoutResultsShowsItsOwnEmptyState() {
    seed(startedAt = 1_000, coffee = "Etiopía", method = BrewMethod.V60)
    seed(startedAt = 2_000, coffee = "Kenia", method = BrewMethod.CHEMEX)
    show()
    waitForRows(2)

    rule.onNodeWithTag("history_filter_coffee").performClick()
    clickMenuOption("Etiopía")
    waitForRows(1)
    rule.onNodeWithTag("history_filter_method").performClick()
    clickMenuOption("Chemex")
    rule.waitUntil(5_000) {
      rule.onAllNodesWithText(context.getString(R.string.history_empty_filtered)).fetchSemanticsNodes().isNotEmpty()
    }
  }

  @Test
  fun theFilterGoesBackToAllWhenItsValueDisappears() {
    seed(startedAt = 1_000, name = "A", coffee = "Etiopía")
    val kenia = seed(startedAt = 2_000, name = "B", coffee = "Kenia")
    show()
    waitForRows(2)

    rule.onNodeWithTag("history_filter_coffee").performClick()
    clickMenuOption("Kenia")
    waitForRows(1)

    runBlocking { repository.delete(kenia) }
    waitForRows(1) // vuelve a "Todos" y queda la de Etiopía
    rows()[0].assertTextContains("A", substring = true)
    rule.onNodeWithTag("history_filter_coffee").assertTextContains(context.getString(R.string.history_filter_all))
  }

  @Test
  fun progressChartNeedsTwoScoredRealBrews() {
    seed(startedAt = 1_000)
    seed(startedAt = 2_000, simulated = true) // las simuladas no cuentan
    show()
    waitForRows(2)
    rule.onNodeWithTag("progress_empty").assertIsDisplayed()
    rule.onAllNodesWithTag("progress_chart").assertCountEquals(0)

    seed(startedAt = 3_000)
    rule.waitUntil(5_000) { rule.onAllNodesWithTag("progress_chart").fetchSemanticsNodes().isNotEmpty() }
    rule.onAllNodesWithTag("progress_empty").assertCountEquals(0)
  }

  @Test
  fun deleteShowsUndoAndRestoresTheSameBrew() {
    val id = seed(startedAt = 1_000, name = "Borrable")
    show()
    waitForRows(1)

    runBlocking { repository.delete(id) } // lo que hace el detalle
    waitForRows(0)
    val undo = context.getString(R.string.history_undo)
    rule.waitUntil(5_000) { rule.onAllNodesWithText(undo).fetchSemanticsNodes().isNotEmpty() }
    rule.onNodeWithText(undo).performClick()

    waitForRows(1)
    rows()[0].assertTextContains("Borrable", substring = true)
    assertEquals(id, runBlocking { repository.get(id) }?.entity?.id)
    assertNotNull(runBlocking { repository.get(id) })
  }
}
