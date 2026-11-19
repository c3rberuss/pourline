package dev.josueamaya.pourline.ui.coffees

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasTestTag
import androidx.test.platform.app.InstrumentationRegistry
import dev.josueamaya.pourline.R
import dev.josueamaya.pourline.coffee.CoffeeDraft
import dev.josueamaya.pourline.coffee.CoffeeProcess
import dev.josueamaya.pourline.coffee.CoffeeRepository
import dev.josueamaya.pourline.coffee.RoastLevel
import dev.josueamaya.pourline.history.BrewDetails
import dev.josueamaya.pourline.history.BrewRepository
import dev.josueamaya.pourline.history.HistoryDatabase
import dev.josueamaya.pourline.history.TestBrews
import dev.josueamaya.pourline.history.UsedCoffee
import dev.josueamaya.pourline.ui.theme.PourlineTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class CoffeeDetailScreenTest {
  @get:Rule
  val rule = createComposeRule()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private val today = LocalDate.of(2026, 11, 13)
  private lateinit var db: HistoryDatabase
  private lateinit var coffees: CoffeeRepository
  private lateinit var brews: BrewRepository
  private var openedBrew: Long? = null
  private var newBagOf: Long? = null
  private var editedId: Long? = null
  private var backs = 0

  @Before
  fun setUp() {
    db = HistoryDatabase.inMemory(context)
    coffees = CoffeeRepository(db, { }, { today }, { ZoneId.of("UTC") })
    brews = BrewRepository(db)
  }

  @After
  fun tearDown() = db.close()

  private fun save(draft: CoffeeDraft) = runBlocking { coffees.save(draft) }

  private fun brew(coffeeId: Long, startedAt: Long, doseG: Double, rating: Int? = null, notes: String? = null) = runBlocking {
    val details = if (rating != null || notes != null) BrewDetails(rating = rating, notes = notes) else null
    brews.save(
      TestBrews.finished(totalMs = 60_000, doseG = doseG),
      TestBrews.context(startedAtEpochMs = startedAt, coffee = UsedCoffee(coffeeId, "x"), details = details),
    )
  }

  private fun show(id: Long) {
    val viewModel = CoffeeDetailViewModel(coffees, id)
    rule.setContent {
      PourlineTheme(darkTheme = false) {
        CoffeeDetailScreen(viewModel, onBack = { backs++ }, onEdit = { editedId = it }, onNewBag = { newBagOf = it }, onOpenBrew = { openedBrew = it })
      }
    }
  }

  private fun exists(tag: String) = rule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

  private fun rowCount() = rule.onAllNodesWithTag("coffee_log_row").fetchSemanticsNodes().size

  private fun fullCoffee() = TestBrews.coffeeDraft(name = "Etiopía Guji Hambela", roaster = "Café de Altura", roastDate = LocalDate.of(2026, 11, 4), bagWeightG = 250.0)
    .copy(process = CoffeeProcess.NATURAL, roastLevel = RoastLevel.LIGHT, altitudeMinM = 1800, altitudeMaxM = 2000)

  private fun withThreeBrews(): Long {
    val id = save(fullCoffee())
    brew(id, 1_794_000_000_000, 15.0, rating = 4, notes = "Jazmín y durazno")
    brew(id, 1_793_900_000_000, 15.0, rating = 3)
    brew(id, 1_793_800_000_000, 15.0)
    return id
  }

  @Test
  fun theLogShowsThreeBrewsAndTappingOneOpensIt() {
    val id = withThreeBrews()
    show(id)
    rule.waitUntil(5_000) { rowCount() == 3 }
    rule.onNodeWithText("Jazmín y durazno").assertIsDisplayed()
    val first = runBlocking { coffees.coffee(id).first()!!.brews.first().brewId }
    rule.onAllNodesWithTag("coffee_log_row")[0].performClick()
    assertEquals(first, openedBrew)
  }

  @Test
  fun theOrderIsLogThenFactsThenActions() {
    val id = withThreeBrews()
    show(id)
    rule.waitUntil(5_000) { rowCount() >= 1 }
    val list = rule.onNodeWithTag("coffee_detail_list")
    list.performScrollToNode(hasTestTag("coffee_detail_edit"))
    rule.waitForIdle()
    val lastRow = rule.onAllNodesWithTag("coffee_log_row").fetchSemanticsNodes().last().boundsInRoot.top
    val facts = rule.onNodeWithTag("coffee_detail_facts_header").fetchSemanticsNode().boundsInRoot.top
    val edit = rule.onNodeWithTag("coffee_detail_edit").fetchSemanticsNode().boundsInRoot.top
    assertTrue("la última preparación va sobre los datos", lastRow < facts)
    assertTrue("los datos van sobre las acciones", facts < edit)
  }

  @Test
  fun theFactsAreFoldedByDefaultAndOpenOnTap() {
    val id = save(fullCoffee())
    show(id)
    rule.waitUntil(5_000) { exists("coffee_detail_facts_header") }
    assertTrue(!exists("coffee_fact_process"))
    rule.onNodeWithText("Natural · Claro").assertIsDisplayed()
    rule.onNodeWithTag("coffee_detail_list").performScrollToNode(hasTestTag("coffee_detail_facts_header"))
    rule.onNodeWithTag("coffee_detail_facts_header").performClick()
    rule.waitUntil(5_000) { exists("coffee_fact_process") }
    rule.onNodeWithTag("coffee_fact_altitude", useUnmergedTree = true).assertIsDisplayed()
    rule.onNodeWithText("1800–2000 m").assertIsDisplayed()
    rule.onNodeWithTag("coffee_detail_facts_header").performClick()
    rule.waitUntil(5_000) { !exists("coffee_fact_process") }
  }

  @Test
  fun anExpandedFactsPanelSurvivesRecreation() {
    val id = save(fullCoffee())
    val tester = StateRestorationTester(rule)
    val viewModel = CoffeeDetailViewModel(coffees, id)
    tester.setContent {
      PourlineTheme(darkTheme = false) {
        CoffeeDetailScreen(viewModel, onBack = {}, onEdit = {}, onNewBag = {}, onOpenBrew = {})
      }
    }
    rule.waitUntil(5_000) { exists("coffee_detail_facts_header") }
    rule.onNodeWithTag("coffee_detail_list").performScrollToNode(hasTestTag("coffee_detail_facts_header"))
    rule.onNodeWithTag("coffee_detail_facts_header").performClick()
    rule.waitUntil(5_000) { exists("coffee_fact_process") }
    tester.emulateSavedInstanceStateRestore()
    rule.waitUntil(5_000) { exists("coffee_fact_process") }
  }

  @Test
  fun aCoffeeWithOnlyANameHasNoFactsPanel() {
    val id = save(CoffeeDraft(name = "Solo nombre"))
    show(id)
    rule.waitUntil(5_000) { exists("coffee_detail_edit") }
    assertTrue(!exists("coffee_detail_facts_header"))
    rule.onNodeWithText(context.getString(R.string.coffee_detail_log_empty)).assertIsDisplayed()
  }

  @Test
  fun aSoldOutCoffeeWarnsAndCanBeArchivedFromTheAlert() {
    val id = save(TestBrews.coffeeDraft(name = "Agotado", bagWeightG = 15.0))
    brew(id, 1_794_000_000_000, 15.0)
    show(id)
    rule.waitUntil(5_000) { exists("coffee_detail_sold_out") }
    rule.onNodeWithTag("coffee_detail_sold_out_archive").performClick()
    rule.waitUntil(5_000) { exists("coffee_detail_archived") }
    assertTrue(runBlocking { coffees.get(id) }!!.archived)
    rule.onNodeWithTag("coffee_detail_list").performScrollToNode(hasTestTag("coffee_detail_archive"))
    rule.onNodeWithText(context.getString(R.string.coffee_detail_unarchive)).assertIsDisplayed()
  }

  @Test
  fun theActionsCallTheirHandlers() {
    val id = save(fullCoffee())
    show(id)
    rule.waitUntil(5_000) { exists("coffee_detail_edit") }
    rule.onNodeWithTag("coffee_detail_list").performScrollToNode(hasTestTag("coffee_detail_edit"))
    rule.onNodeWithTag("coffee_detail_edit").performClick()
    assertEquals(id, editedId)
    rule.onNodeWithTag("coffee_detail_new_bag").performClick()
    assertEquals(id, newBagOf)
  }

  @Test
  fun deletingAsksWithTheBrewCountAndKeepsTheBrewName() {
    val id = withThreeBrews()
    show(id)
    rule.waitUntil(5_000) { exists("coffee_detail_delete") || rowCount() > 0 }
    rule.onNodeWithTag("coffee_detail_list").performScrollToNode(hasTestTag("coffee_detail_delete"))
    rule.onNodeWithTag("coffee_detail_delete").performClick()
    rule.onNodeWithText(context.resources.getQuantityString(R.plurals.coffee_detail_delete_with_brews, 3, 3)).assertIsDisplayed()
    rule.onNodeWithText(context.getString(R.string.coffee_detail_delete_cancel)).performClick()
    assertTrue(runBlocking { coffees.get(id) } != null)

    rule.onNodeWithTag("coffee_detail_delete").performClick()
    rule.onAllNodesWithText(context.getString(R.string.coffee_detail_delete)).onLast().performClick()
    rule.waitUntil(5_000) { backs > 0 }
    assertEquals(null, runBlocking { coffees.get(id) })
    val remaining = runBlocking { brews.list(dev.josueamaya.pourline.history.BrewFilter()).first() }
    assertEquals(3, remaining.size)
    assertTrue(remaining.all { it.coffee == "Etiopía Guji Hambela" })
  }
}
