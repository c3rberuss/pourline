package dev.dotscale.ui.history

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import dev.dotscale.R
import dev.dotscale.coffee.CoffeeRepository
import dev.dotscale.coffee.PhotoFiles
import dev.dotscale.history.BrewDetails
import dev.dotscale.history.BrewRepository
import dev.dotscale.history.HistoryDatabase
import dev.dotscale.history.TestBrews
import dev.dotscale.history.UsedCoffee
import dev.dotscale.ui.coffees.formatCoffeeDate
import dev.dotscale.ui.theme.DotScaleTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class ComparePickScreenTest {
  @get:Rule
  val rule = createComposeRule()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private lateinit var db: HistoryDatabase
  private lateinit var repository: BrewRepository
  private var picked: Long? = null
  private var backCount = 0

  @Before
  fun setUp() {
    db = HistoryDatabase.inMemory(context)
    repository = BrewRepository(db)
  }

  @After
  fun tearDown() = db.close()

  private fun coffee(name: String, roastDate: LocalDate? = null): Long = runBlocking {
    CoffeeRepository(db, PhotoFiles { }).save(TestBrews.coffeeDraft(name = name).copy(roastDate = roastDate))
  }

  private fun seed(startedAt: Long, name: String? = null, coffeeId: Long? = null): Long = runBlocking {
    repository.save(
      TestBrews.finished(totalMs = 60_000),
      TestBrews.context(
        startedAtEpochMs = startedAt,
        details = BrewDetails(name = name),
        coffee = coffeeId?.let { UsedCoffee(it, "x") },
      ),
    )
  }

  private fun show(aId: Long) {
    val viewModel = ComparePickViewModel(repository, aId)
    rule.setContent {
      DotScaleTheme(darkTheme = false) {
        ComparePickScreen(viewModel, onBack = { backCount++ }, onPick = { picked = it })
      }
    }
  }

  private fun rows() = rule.onAllNodesWithTag("history_row")

  private fun waitForRows(count: Int) = rule.waitUntil(5_000) { rows().fetchSemanticsNodes().size == count }

  private fun exists(tag: String) = rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

  private fun clickMenuOption(text: String) = rule.onNode(hasText(text) and hasAnyAncestor(isPopup())).performClick()

  @Test
  fun startsFilteredByTheCoffeeOfAAndLeavesAOut() {
    val etiopia = coffee("Etiopía")
    val a = seed(1_000, name = "A", coffeeId = etiopia)
    seed(2_000, name = "B", coffeeId = etiopia)
    seed(3_000, name = "C", coffeeId = coffee("Kenia"))
    show(a)

    waitForRows(1)
    rows()[0].assertTextContains("B", substring = true)
    rule.onNodeWithTag("history_filter_coffee").assertTextContains("Etiopía")
  }

  @Test
  fun twoBagsOfTheSameCoffeeStartInTheBagOfA() {
    val first = coffee("Guji", LocalDate.of(2026, 10, 12))
    val second = coffee("Guji", LocalDate.of(2026, 11, 2))
    val a = seed(1_000, name = "A", coffeeId = first)
    seed(2_000, name = "B", coffeeId = first)
    seed(3_000, name = "C", coffeeId = second)
    show(a)

    waitForRows(1)
    rows()[0].assertTextContains("B", substring = true)
    val label = context.getString(R.string.history_filter_coffee_roasted, "Guji", formatCoffeeDate(LocalDate.of(2026, 10, 12)))
    rule.onNodeWithTag("history_filter_coffee").assertTextContains(label)
  }

  @Test
  fun withoutACoffeeItStartsInAll() {
    val a = seed(1_000, name = "A")
    seed(2_000, name = "B", coffeeId = coffee("Etiopía"))
    seed(3_000, name = "C", coffeeId = coffee("Kenia"))
    show(a)

    waitForRows(2)
    rule.onNodeWithTag("history_filter_coffee").assertTextContains(context.getString(R.string.history_filter_all))
  }

  @Test
  fun tappingARowPicksThatBrew() {
    val a = seed(1_000, name = "A")
    val b = seed(2_000, name = "B")
    show(a)
    waitForRows(1)

    rows()[0].performClick()
    assertEquals(b, picked)
  }

  @Test
  fun withOnlyOneBrewItSaysThereIsNothingToCompare() {
    val a = seed(1_000, name = "A")
    show(a)

    rule.waitUntil(5_000) { exists("compare_pick_empty") }
    rule.onNodeWithTag("compare_pick_empty").assertIsDisplayed()
    rows().assertCountEquals(0)
    rule.onAllNodesWithTag("history_filter_coffee").assertCountEquals(0)
  }

  @Test
  fun ifTheCoffeeOfAHasNoOtherBrewsTheFilterEmptyStateAppearsAndAllFixesIt() {
    val a = seed(1_000, name = "A", coffeeId = coffee("Etiopía"))
    seed(2_000, name = "B", coffeeId = coffee("Kenia"))
    show(a)

    rule.waitUntil(5_000) { exists("compare_pick_empty_filtered") }
    rows().assertCountEquals(0)

    rule.onNodeWithTag("history_filter_coffee").performClick()
    clickMenuOption(context.getString(R.string.history_filter_all))
    waitForRows(1)
    rows()[0].assertTextContains("B", substring = true)
  }

  @Test
  fun aDeletedABringsTheScreenBack() {
    val a = seed(1_000, name = "A")
    seed(2_000, name = "B")
    show(a)
    waitForRows(1)

    runBlocking { repository.delete(a) }
    rule.waitUntil(5_000) { backCount > 0 }
  }
}
