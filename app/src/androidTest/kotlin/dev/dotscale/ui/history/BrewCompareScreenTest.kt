package dev.dotscale.ui.history

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import dev.dotscale.coffee.CoffeeRepository
import dev.dotscale.coffee.PhotoFiles
import dev.dotscale.history.BrewDetails
import dev.dotscale.history.BrewRepository
import dev.dotscale.history.HistoryDatabase
import dev.dotscale.history.TestBrews
import dev.dotscale.history.UsedCoffee
import dev.dotscale.protocol.WeightUnit
import dev.dotscale.ui.theme.DotScaleTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class BrewCompareScreenTest {
  @get:Rule
  val rule = createComposeRule()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private lateinit var db: HistoryDatabase
  private lateinit var repository: BrewRepository
  private var backCount = 0

  @Before
  fun setUp() {
    db = HistoryDatabase.inMemory(context)
    repository = BrewRepository(db)
  }

  @After
  fun tearDown() = db.close()

  private fun seed(
    name: String,
    totalMs: Long,
    coffee: String? = null,
    doseG: Double? = 20.0,
    unit: WeightUnit = WeightUnit.GRAMS,
  ): Long = runBlocking {
    // El café sale del catálogo: la preparación lo vincula y copia su nombre.
    val used = coffee?.let { UsedCoffee(CoffeeRepository(db, PhotoFiles { }).save(TestBrews.coffeeDraft(name = it)), it) }
    repository.save(
      TestBrews.finished(totalMs = totalMs, doseG = doseG),
      TestBrews.context(startedAtEpochMs = totalMs, unit = unit, details = BrewDetails(name = name), coffee = used),
    )
  }

  private fun corruptCurve(id: Long) {
    db.openHelper.writableDatabase.execSQL("UPDATE brew_curves SET data = x'0102030405' WHERE brewId = $id")
  }

  private fun show(aId: Long, bId: Long) {
    val viewModel = BrewCompareViewModel(repository, aId, bId)
    rule.setContent {
      DotScaleTheme(darkTheme = false) { BrewCompareScreen(viewModel, onBack = { backCount++ }) }
    }
    rule.waitUntil(5_000) { exists("compare_table") }
  }

  private fun exists(tag: String) = rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

  private fun cell(column: String, metric: String) = rule.onNodeWithTag("compare_cell_${column}_$metric").performScrollTo()

  @Test
  fun showsTheLegendTwoChartsAndTheTable() {
    val a = seed("Mañana", totalMs = 60_000, coffee = "Etiopía")
    val b = seed("Tarde", totalMs = 90_000, coffee = "Kenia")
    show(a, b)

    rule.onNodeWithTag("compare_legend_a").assertIsDisplayed()
    rule.onNodeWithTag("compare_legend_b").assertIsDisplayed()
    rule.onNodeWithTag("compare_chart_water").performScrollTo().assertIsDisplayed()
    rule.onNodeWithTag("compare_chart_flow").performScrollTo().assertIsDisplayed()
    rule.onNodeWithTag("compare_table").performScrollTo().assertIsDisplayed()
  }

  @Test
  fun theLegendNamesBothBrewsAndTheirCoffee() {
    val a = seed("Mañana", totalMs = 60_000, coffee = "Etiopía")
    val b = seed("Tarde", totalMs = 90_000, coffee = "Kenia")
    show(a, b)

    val expected = mapOf(
      "compare_legend_a" to listOf("A: Mañana", "Etiopía"),
      "compare_legend_b" to listOf("B: Tarde", "Kenia"),
    )
    for ((tag, texts) in expected) {
      for (text in texts) {
        rule.onNode(hasText(text) and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true).assertIsDisplayed()
      }
    }
  }

  @Test
  fun theTableShowsAAndBAndTheirDifference() {
    val a = seed("A", totalMs = 60_000)
    val b = seed("B", totalMs = 90_000)
    show(a, b)

    cell("a", "total_time").assertTextEquals("01:00")
    cell("b", "total_time").assertTextEquals("01:30")
    cell("diff", "total_time").assertTextEquals("+00:30")
    // Los dos tienen una dosis de 20 g: sin diferencia, sin signo.
    cell("diff", "dose").assertTextEquals(String.format(java.util.Locale.getDefault(), "%.1f g", 0.0))
  }

  @Test
  fun aNegativeDifferenceUsesARealMinus() {
    val a = seed("A", totalMs = 90_000)
    val b = seed("B", totalMs = 60_000)
    show(a, b)

    cell("diff", "total_time").assertTextEquals("−00:30")
  }

  @Test
  fun aMissingValueIsADashInTheValueAndInTheDifference() {
    val a = seed("A", totalMs = 60_000, doseG = 20.0)
    val b = seed("B", totalMs = 60_000, doseG = null)
    show(a, b)

    val dash = context.getString(dev.dotscale.R.string.compare_none)
    cell("b", "dose").assertTextEquals(dash)
    cell("diff", "dose").assertTextEquals(dash)
    cell("diff", "ratio").assertTextEquals(dash)
  }

  @Test
  fun everythingIsInTheUnitOfA() {
    val a = seed("A", totalMs = 60_000, unit = WeightUnit.OUNCES)
    val b = seed("B", totalMs = 60_000, unit = WeightUnit.GRAMS)
    show(a, b)

    val node = cell("b", "total_water").fetchSemanticsNode()
    assertTrue(node.config.toString().contains("oz"))
  }

  @Test
  fun anUnreadableCurveOmitsItsSeriesAndShowsAnAlertButKeepsTheTable() {
    val a = seed("A", totalMs = 60_000)
    val b = seed("B", totalMs = 60_000)
    corruptCurve(b)
    show(a, b)

    assertTrue(exists("compare_curve_error_b"))
    assertEquals(false, exists("compare_curve_error_a"))
    assertTrue(exists("compare_chart_water")) // queda la serie de A
    cell("b", "pours").assertTextEquals(context.getString(dev.dotscale.R.string.compare_none))
  }

  @Test
  fun ifBothCurvesAreUnreadableOnlyTheTableRemains() {
    val a = seed("A", totalMs = 60_000)
    val b = seed("B", totalMs = 60_000)
    corruptCurve(a)
    corruptCurve(b)
    show(a, b)

    assertTrue(exists("compare_curve_error_a"))
    assertTrue(exists("compare_curve_error_b"))
    assertEquals(false, exists("compare_chart_water"))
    assertEquals(false, exists("compare_chart_flow"))
    assertTrue(exists("compare_table"))
  }

  @Test
  fun deletingEitherBrewBringsTheScreenBack() {
    val a = seed("A", totalMs = 60_000)
    val b = seed("B", totalMs = 60_000)
    show(a, b)

    runBlocking { repository.delete(b) }
    rule.waitUntil(5_000) { backCount > 0 }
  }
}
