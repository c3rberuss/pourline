package dev.josueamaya.pourline.ui.coffees

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import dev.josueamaya.pourline.R
import dev.josueamaya.pourline.coffee.CoffeeListItem
import dev.josueamaya.pourline.ui.theme.PourlineTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class CoffeePickerTest {
  @get:Rule
  val rule = createComposeRule()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private var selected: Long? = null
  private var cleared = 0
  private var created = 0
  private var closed = 0

  private fun item(
    id: Long,
    name: String,
    roastDate: LocalDate? = LocalDate.of(2026, 11, 4),
    days: Int? = 9,
    stock: Double? = 180.0,
    soldOut: Boolean = false,
    archived: Boolean = false,
    country: String = "Etiopía",
  ) = CoffeeListItem(id, name, "Café de Altura", country, "Guji", roastDate, days, stock, soldOut, null, archived)

  private fun show(
    coffees: List<CoffeeListItem>,
    selectedId: Long? = null,
    withClear: Boolean = selectedId != null,
    extra: CoffeeListItem? = null,
  ) {
    rule.setContent {
      PourlineTheme(darkTheme = false) {
        CoffeePicker(
          coffees = coffees,
          selectedId = selectedId,
          onSelect = { selected = it },
          onClear = if (withClear) ({ cleared++ }) else null,
          onNew = { created++ },
          onClose = { closed++ },
          extra = extra,
        )
      }
    }
  }

  private fun rows() = rule.onAllNodesWithTag("coffee_picker_row")

  private fun exists(tag: String) = rule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

  @Test
  fun rowsShowTheChosenOneAndPickingClosesTheSheet() {
    show(listOf(item(1, "Etiopía Guji"), item(2, "Kenia AA")), selectedId = 1)
    rows().assertCountEquals(2)
    rule.onNodeWithTag("coffee_selected_tag", useUnmergedTree = true).assertIsDisplayed()
    rule.onNodeWithText(context.getString(R.string.coffee_selected_tag)).assertIsDisplayed()
    rows()[1].performClick()
    assertEquals(2L, selected)
    assertEquals(1, closed)
  }

  @Test
  fun clearAppearsOnlyWithAChoiceAndClears() {
    show(listOf(item(1, "Etiopía Guji")), selectedId = null)
    assertTrue(!exists("coffee_picker_clear"))
  }

  @Test
  fun clearClearsAndCloses() {
    show(listOf(item(1, "Etiopía Guji")), selectedId = 1)
    rule.onNodeWithTag("coffee_picker_clear").performClick()
    assertEquals(1, cleared)
    assertEquals(1, closed)
  }

  @Test
  fun theSearchFieldAppearsOnlyWithMoreThanFiveCoffees() {
    show((1L..5L).map { item(it, "Café $it") })
    assertTrue(!exists("coffee_picker_search"))
  }

  @Test
  fun withSixCoffeesTheSearchFiltersWithoutAccents() {
    show(listOf(item(1, "Etiopía Guji")) + (2L..6L).map { item(it, "Café $it", country = "Kenia") })
    rule.onNodeWithTag("coffee_picker_search").performTextInput("etiopia")
    rows().assertCountEquals(1)
  }

  @Test
  fun twoBagsOfTheSameCoffeeAreToldApartByTheirRoastDate() {
    show(
      listOf(
        item(1, "Etiopía Guji", LocalDate.of(2026, 11, 4), 9),
        item(2, "Etiopía Guji", LocalDate.of(2026, 10, 2), 42),
      ),
    )
    rule.onNodeWithText("${context.getString(R.string.coffee_row_roasted, formatCoffeeDate(LocalDate.of(2026, 11, 4)), 9)} · 180 g").assertIsDisplayed()
    rule.onNodeWithText("${context.getString(R.string.coffee_row_roasted, formatCoffeeDate(LocalDate.of(2026, 10, 2)), 42)} · 180 g").assertIsDisplayed()
  }

  @Test
  fun aLinkedArchivedCoffeeShowsWithItsTag() {
    show(listOf(item(1, "Etiopía Guji")), extra = item(9, "Kenia Kiambu AA", archived = true))
    rows().assertCountEquals(2)
    rule.onNodeWithTag("coffee_archived_tag", useUnmergedTree = true).assertIsDisplayed()
  }

  @Test
  fun aSoldOutCoffeeStaysSelectable() {
    show(listOf(item(1, "Agotado", stock = 0.0, soldOut = true)))
    rule.onNodeWithTag("coffee_sold_out_tag", useUnmergedTree = true).assertIsDisplayed()
    rows()[0].performClick()
    assertEquals(1L, selected)
  }

  @Test
  fun withoutCoffeesOnlyNewIsOffered() {
    show(emptyList())
    rule.onNodeWithTag("coffee_picker_empty").assertIsDisplayed()
    assertTrue(!exists("coffee_picker_clear"))
    rule.onNodeWithTag("coffee_picker_new").performClick()
    assertEquals(1, created)
  }
}
