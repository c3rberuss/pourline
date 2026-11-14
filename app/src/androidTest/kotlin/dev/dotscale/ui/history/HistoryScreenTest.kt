package dev.dotscale.ui.history

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertContentDescriptionEquals
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
import dev.dotscale.coffee.CoffeeRepository
import dev.dotscale.coffee.PhotoFiles
import dev.dotscale.history.BrewDetails
import dev.dotscale.history.BrewMethod
import dev.dotscale.history.BrewRepository
import dev.dotscale.history.HistoryDatabase
import dev.dotscale.history.TestBrews
import dev.dotscale.history.UsedCoffee
import dev.dotscale.settings.ExperienceMode
import dev.dotscale.settings.LocalExperienceMode
import dev.dotscale.ui.coffees.formatCoffeeDate
import dev.dotscale.ui.theme.DotScaleTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

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

  private val coffees get() = CoffeeRepository(db, PhotoFiles { })

  private fun coffee(name: String, roastDate: LocalDate? = null): Long =
    runBlocking { coffees.save(TestBrews.coffeeDraft(name = name).copy(roastDate = roastDate)) }

  private fun seed(
    startedAt: Long,
    name: String? = null,
    coffeeId: Long? = null,
    method: BrewMethod? = null,
    simulated: Boolean = false,
    scored: Boolean = true,
    rating: Int? = null,
  ): Long = runBlocking {
    val id = repository.save(
      TestBrews.finished(totalMs = 60_000, scored = scored),
      TestBrews.context(startedAtEpochMs = startedAt, simulated = simulated, coffee = coffeeId?.let { UsedCoffee(it, "x") }),
    )
    if (name != null || method != null || rating != null) {
      repository.updateDetails(id, BrewDetails(name = name, method = method, rating = rating))
    }
    id
  }

  /** Una preparación anterior al catálogo: texto de café sin vínculo. */
  private fun seedLegacy(startedAt: Long, name: String, coffee: String): Long = seed(startedAt, name).also {
    db.openHelper.writableDatabase.execSQL("UPDATE brews SET coffee = ? WHERE id = ?", arrayOf<Any>(coffee, it))
  }

  private fun show(mode: ExperienceMode = ExperienceMode.EXPERT) {
    val viewModel = HistoryViewModel(repository)
    rule.setContent {
      CompositionLocalProvider(LocalExperienceMode provides mode) {
        DotScaleTheme(darkTheme = false) {
          HistoryScreen(viewModel, onBack = {}, onOpenBrew = { opened = it })
        }
      }
    }
  }

  private fun rows() = rule.onAllNodesWithTag("history_row")

  private fun waitForRows(count: Int) = rule.waitUntil(5_000) { rows().fetchSemanticsNodes().size == count }

  /** El texto aparece también en las filas; el del menú es el que está dentro del popup. */
  private fun clickMenuOption(text: String) =
    rule.onNode(hasText(text) and hasAnyAncestor(isPopup())).performClick()

  @Test
  fun inBasicModeThereIsNoProgressCardAndTheRowsHaveNoStabilityOrFitTags() {
    runBlocking {
      repository.save(TestBrews.finished(totalMs = 60_000), TestBrews.context(startedAtEpochMs = 1_000, target = TestBrews.usedTarget(score = 86)))
      repository.save(TestBrews.finished(totalMs = 60_000), TestBrews.context(startedAtEpochMs = 2_000, simulated = true))
    }
    show(ExperienceMode.BASIC)
    waitForRows(2)

    rule.onAllNodesWithText(context.getString(R.string.history_progress_title)).assertCountEquals(0)
    rule.onAllNodesWithTag("progress_empty").assertCountEquals(0)
    rule.onAllNodesWithTag("progress_chart").assertCountEquals(0)
    rule.onAllNodesWithTag("history_row_fit", useUnmergedTree = true).assertCountEquals(0)
    rule.onAllNodesWithText(context.getString(R.string.history_row_no_score), substring = true).assertCountEquals(0)
    // Lo que no es una métrica sigue: la etiqueta de simulada.
    rows()[0].assertTextContains(context.getString(R.string.history_row_simulated), substring = true)
  }

  @Test
  fun inExpertModeTheProgressCardAndTheRowTagsShow() {
    seed(startedAt = 1_000, scored = false)
    show(ExperienceMode.EXPERT)
    waitForRows(1)
    rule.onNodeWithText(context.getString(R.string.history_progress_title)).assertIsDisplayed()
    rows()[0].assertTextContains(context.getString(R.string.history_row_no_score), substring = true)
  }

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
    val etiopia = coffee("Etiopía")
    val kenia = coffee("Kenia")
    seed(startedAt = 1_000, name = "A", coffeeId = etiopia)
    seed(startedAt = 2_000, name = "B", coffeeId = kenia)
    seed(startedAt = 3_000, name = "C", coffeeId = etiopia)
    show()
    waitForRows(3)

    rule.onNodeWithTag("history_filter_coffee").performClick()
    clickMenuOption("Etiopía")
    waitForRows(2)
    rows()[0].assertTextContains("C", substring = true)
    rule.onNodeWithTag("history_filter_coffee").assertTextContains("Etiopía")
  }

  @Test
  fun theCoffeeOptionsAreTheCatalogCoffeesWithBrewsIncludingAnArchivedOne() {
    val etiopia = coffee("Etiopía")
    val archived = coffee("Colombia")
    coffee("Sin preparaciones")
    seed(startedAt = 1_000, name = "A", coffeeId = etiopia)
    seed(startedAt = 2_000, name = "B", coffeeId = archived)
    runBlocking { coffees.setArchived(archived, true) }
    show()
    waitForRows(2)

    rule.onNodeWithTag("history_filter_coffee").performClick()
    rule.onNode(hasText("Etiopía") and hasAnyAncestor(isPopup())).assertIsDisplayed()
    rule.onNode(hasText("Colombia") and hasAnyAncestor(isPopup())).assertIsDisplayed()
    rule.onAllNodes(hasText("Sin preparaciones") and hasAnyAncestor(isPopup())).assertCountEquals(0)
    clickMenuOption("Colombia")
    waitForRows(1)
    rows()[0].assertTextContains("B", substring = true)
  }

  @Test
  fun aLegacyBrewWithoutLinkOnlyAppearsWithAll() {
    val etiopia = coffee("Etiopía")
    seed(startedAt = 1_000, name = "Vinculada", coffeeId = etiopia)
    seedLegacy(startedAt = 2_000, name = "Antigua", coffee = "Etiopía")
    show()
    waitForRows(2)

    rule.onNodeWithTag("history_filter_coffee").performClick()
    clickMenuOption("Etiopía")
    waitForRows(1)
    rows()[0].assertTextContains("Vinculada", substring = true)

    rule.onNodeWithTag("history_filter_coffee").performClick()
    clickMenuOption(context.getString(R.string.history_filter_all))
    waitForRows(2)
  }

  @Test
  fun twoBagsWithTheSameNameAreToldApartByTheirRoastDate() {
    val october = LocalDate.of(2026, 10, 12)
    val first = coffee("Guji", october)
    val second = coffee("Guji", null)
    seed(startedAt = 1_000, name = "A", coffeeId = first)
    seed(startedAt = 2_000, name = "B", coffeeId = second)
    show()
    waitForRows(2)

    val dated = context.getString(R.string.history_filter_coffee_roasted, "Guji", formatCoffeeDate(october))
    val undated = context.getString(R.string.history_filter_coffee_no_date, "Guji")
    rule.onNodeWithTag("history_filter_coffee").performClick()
    rule.onNode(hasText(dated) and hasAnyAncestor(isPopup())).assertIsDisplayed()
    rule.onNode(hasText(undated) and hasAnyAncestor(isPopup())).assertIsDisplayed()
    clickMenuOption(dated)
    waitForRows(1)
    rows()[0].assertTextContains("A", substring = true)
  }

  @Test
  fun theRowShowsTheRatingOnlyWhenThereIsOne() {
    seed(startedAt = 1_000, name = "Sin valorar")
    seed(startedAt = 2_000, name = "Valorada", rating = 4)
    show()
    waitForRows(2)

    rule.onAllNodesWithTag("history_row_rating", useUnmergedTree = true).assertCountEquals(1)
    rule.onNodeWithTag("history_row_rating", useUnmergedTree = true)
      .assertContentDescriptionEquals(context.getString(R.string.rate_description, 4, 5))
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
    seed(startedAt = 1_000, coffeeId = coffee("Etiopía"), method = BrewMethod.V60)
    seed(startedAt = 2_000, coffeeId = coffee("Kenia"), method = BrewMethod.CHEMEX)
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
    seed(startedAt = 1_000, name = "A", coffeeId = coffee("Etiopía"))
    val kenia = seed(startedAt = 2_000, name = "B", coffeeId = coffee("Kenia"))
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
