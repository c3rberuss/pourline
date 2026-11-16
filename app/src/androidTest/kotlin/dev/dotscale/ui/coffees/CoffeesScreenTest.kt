package dev.dotscale.ui.coffees

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import dev.dotscale.R
import dev.dotscale.coffee.CoffeeDraft
import dev.dotscale.coffee.CoffeeRepository
import dev.dotscale.history.BrewRepository
import dev.dotscale.history.HistoryDatabase
import dev.dotscale.history.TestBrews
import dev.dotscale.history.UsedCoffee
import dev.dotscale.ui.theme.DotScaleTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class CoffeesScreenTest {
  @get:Rule
  val rule = createComposeRule()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private val today = LocalDate.of(2026, 11, 13)
  private lateinit var db: HistoryDatabase
  private lateinit var coffees: CoffeeRepository
  private lateinit var brews: BrewRepository
  private var opened: Long? = null
  private var added = 0

  @Before
  fun setUp() {
    db = HistoryDatabase.inMemory(context)
    coffees = CoffeeRepository(db, { }, { today }, { ZoneId.of("UTC") })
    brews = BrewRepository(db)
  }

  @After
  fun tearDown() = db.close()

  private fun show() {
    val viewModel = CoffeesViewModel(coffees)
    rule.setContent {
      DotScaleTheme(darkTheme = false) {
        CoffeesScreen(viewModel, onBack = {}, onOpenCoffee = { opened = it }, onAddCoffee = { added++ })
      }
    }
  }

  private fun rows() = rule.onAllNodesWithTag("coffee_row")

  private fun waitForRows(count: Int) = rule.waitUntil(5_000) { rows().fetchSemanticsNodes().size == count }

  private fun save(draft: CoffeeDraft) = runBlocking { coffees.save(draft) }

  private fun dose(coffeeId: Long, grams: Double) = runBlocking {
    brews.save(TestBrews.finished(totalMs = 60_000, doseG = grams), TestBrews.context(coffee = UsedCoffee(coffeeId, "x")))
  }

  @Test
  fun anEmptyCatalogOffersToRegisterTheFirstCoffee() {
    show()
    rule.waitUntil(5_000) { rule.onAllNodesWithTag("coffees_empty").fetchSemanticsNodes().isNotEmpty() }
    rule.onNodeWithText(context.getString(R.string.coffees_empty)).assertIsDisplayed()
    rule.onNodeWithTag("coffees_empty_add").performClick()
    assertEquals(1, added)
    rule.onNodeWithTag("coffees_add").performClick()
    assertEquals(2, added)
  }

  @Test
  fun rowsShowNameRoasterRoastDayAndStock() {
    val id = save(TestBrews.coffeeDraft(name = "Etiopía Guji Hambela", roaster = "Café de Altura", roastDate = LocalDate.of(2026, 11, 4), bagWeightG = 250.0))
    dose(id, 70.0)
    show()
    waitForRows(1)
    rule.onNodeWithText("Etiopía Guji Hambela").assertIsDisplayed()
    rule.onNodeWithText("Café de Altura").assertIsDisplayed()
    rule.onNodeWithText(context.getString(R.string.coffee_row_roasted, formatCoffeeDate(LocalDate.of(2026, 11, 4)), 9)).assertIsDisplayed()
    rule.onNodeWithText(context.getString(R.string.coffee_row_stock, "180 g")).assertIsDisplayed()
    rows()[0].performClick()
    assertEquals(id, opened)
  }

  @Test
  fun aCoffeeWithoutRoastDateSaysSo() {
    save(TestBrews.coffeeDraft(name = "Sin fecha", roastDate = null, bagWeightG = null))
    show()
    waitForRows(1)
    rule.onNodeWithText(context.getString(R.string.coffee_row_no_roast_date)).assertIsDisplayed()
  }

  @Test
  fun aSoldOutCoffeeIsTaggedAndHasNoStockLine() {
    val id = save(TestBrews.coffeeDraft(name = "Kenia", bagWeightG = 15.0))
    dose(id, 15.0)
    show()
    waitForRows(1)
    rule.onNodeWithTag("coffee_sold_out_tag", useUnmergedTree = true).assertIsDisplayed()
    rule.onAllNodesWithTag("coffee_row").assertCountEquals(1)
  }

  @Test
  fun searchFiltersWithoutCaseOrAccentsAndShowsAnEmptyMessage() {
    save(TestBrews.coffeeDraft(name = "Etiopía Guji", roaster = "Café de Altura"))
    save(TestBrews.coffeeDraft(name = "Kenia AA", roaster = "Origen").copy(originCountry = "Kenia", originRegion = "Kiambu"))
    show()
    waitForRows(2)
    rule.onNodeWithTag("coffees_search").performTextInput("etiopia")
    waitForRows(1)
    rule.onNodeWithText("Etiopía Guji").assertIsDisplayed()
    rule.onNodeWithTag("coffees_search_clear").performClick()
    waitForRows(2)
    rule.onNodeWithTag("coffees_search").performTextInput("zzz")
    rule.waitUntil(5_000) { rule.onAllNodesWithTag("coffees_empty_search").fetchSemanticsNodes().isNotEmpty() }
    rule.onNodeWithText(context.getString(R.string.coffees_empty_search, "zzz")).assertIsDisplayed()
  }

  @Test
  fun theTabsSwitchBetweenActiveAndArchived() {
    save(TestBrews.coffeeDraft(name = "Activo"))
    val archived = save(TestBrews.coffeeDraft(name = "Archivado"))
    runBlocking { coffees.setArchived(archived, true) }
    show()
    waitForRows(1)
    rule.onNodeWithText("Activo").assertIsDisplayed()
    rule.onNodeWithText(context.getString(R.string.coffees_tab_archived)).performClick()
    rule.waitUntil(5_000) { rule.onAllNodesWithTag("coffee_row").fetchSemanticsNodes().size == 1 }
    rule.onNodeWithText("Archivado").assertIsDisplayed()
    rule.onNodeWithText(context.getString(R.string.coffees_tab_active)).performClick()
    rule.waitUntil(5_000) { rule.onAllNodesWithText("Activo").fetchSemanticsNodes().isNotEmpty() }
  }

  @Test
  fun anEmptyArchivedTabSaysThereAreNone() {
    save(TestBrews.coffeeDraft(name = "Activo"))
    show()
    waitForRows(1)
    rule.onNodeWithText(context.getString(R.string.coffees_tab_archived)).performClick()
    rule.waitUntil(5_000) { rule.onAllNodesWithTag("coffees_empty_archived").fetchSemanticsNodes().isNotEmpty() }
  }
}
