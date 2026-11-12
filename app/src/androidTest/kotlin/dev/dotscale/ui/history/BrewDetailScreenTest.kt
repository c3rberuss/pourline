package dev.dotscale.ui.history

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import dev.dotscale.R
import dev.dotscale.history.BrewDetails
import dev.dotscale.history.BrewMethod
import dev.dotscale.history.BrewRepository
import dev.dotscale.history.HistoryDatabase
import dev.dotscale.history.TestBrews
import dev.dotscale.protocol.WeightUnit
import dev.dotscale.ui.theme.DotScaleTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class BrewDetailScreenTest {
  @get:Rule
  val rule = createComposeRule()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private lateinit var db: HistoryDatabase
  private lateinit var repository: BrewRepository
  private var backCount = 0
  private var edited: Long? = null

  @Before
  fun setUp() {
    db = HistoryDatabase.inMemory(context)
    repository = BrewRepository(db)
  }

  @After
  fun tearDown() = db.close()

  private fun show(id: Long) {
    val viewModel = BrewDetailViewModel(repository, id)
    rule.setContent {
      DotScaleTheme(darkTheme = false) {
        BrewDetailScreen(viewModel, onBack = { backCount++ }, onEdit = { edited = it })
      }
    }
  }

  private fun seed(unit: WeightUnit = WeightUnit.GRAMS, details: BrewDetails? = null): Long = runBlocking {
    val id = repository.save(TestBrews.finished(totalMs = 60_000), TestBrews.context(unit = unit))
    if (details != null) repository.updateDetails(id, details)
    id
  }

  @Test
  fun showsTheChartTheSummaryAndTheDetails() {
    val id = seed(
      details = BrewDetails(name = "Mañana", coffee = "Etiopía", method = BrewMethod.V60, grind = "21 clics", waterTempC = 92.5, notes = "Dulce"),
    )
    show(id)

    rule.waitUntil(5_000) { rule.onAllNodesWithTag("brew_chart").fetchSemanticsNodes().isNotEmpty() }
    rule.onNodeWithTag("brew_chart").assertIsDisplayed()
    rule.onNodeWithTag("brew_summary").performScrollTo().assertIsDisplayed()
    rule.onNodeWithTag("brew_detail_details").performScrollTo().assertIsDisplayed()
    for (text in listOf("Etiopía", "V60", "21 clics", "Dulce")) {
      rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty().let { assertEquals(text, true, it) }
    }
  }

  @Test
  fun aBrewMadeInOuncesIsShownInOunces() {
    show(seed(unit = WeightUnit.OUNCES))
    rule.waitUntil(5_000) { rule.onAllNodesWithTag("brew_summary").fetchSemanticsNodes().isNotEmpty() }
    rule.onNodeWithTag("brew_summary").performScrollTo()
    assertEquals(true, rule.onAllNodesWithText("oz", substring = true).fetchSemanticsNodes().isNotEmpty())
  }

  @Test
  fun theDetailsCardIsOmittedWithoutManualData() {
    show(seed())
    rule.waitUntil(5_000) { rule.onAllNodesWithTag("brew_summary").fetchSemanticsNodes().isNotEmpty() }
    assertEquals(0, rule.onAllNodesWithTag("brew_detail_details").fetchSemanticsNodes().size)
  }

  @Test
  fun editOpensTheEditScreenOfThatBrew() {
    val id = seed()
    show(id)
    rule.waitUntil(5_000) { rule.onAllNodesWithTag("brew_detail_edit").fetchSemanticsNodes().isNotEmpty() }
    rule.onNodeWithTag("brew_detail_edit").performScrollTo().performClick()
    assertEquals(id, edited)
  }

  @Test
  fun deleteGoesBackOnceAndLeavesTheBrewForUndo() {
    val id = seed()
    show(id)
    rule.waitUntil(5_000) { rule.onAllNodesWithTag("brew_detail_delete").fetchSemanticsNodes().isNotEmpty() }
    rule.onNodeWithTag("brew_detail_delete").performScrollTo().performClick()

    rule.waitUntil(5_000) { backCount > 0 }
    rule.waitForIdle()
    assertEquals(1, backCount)
    assertNull(runBlocking { repository.get(id) })
    assertEquals(id, repository.pendingUndo.value?.entity?.id)
  }

  @Test
  fun aMissingBrewGoesBack() {
    show(404)
    rule.waitUntil(5_000) { backCount > 0 }
  }
}
