package dev.josueamaya.pourline.ui.tour

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.platform.app.InstrumentationRegistry
import dev.josueamaya.pourline.R
import dev.josueamaya.pourline.history.TestBrews
import dev.josueamaya.pourline.history.UsedCoffee
import dev.josueamaya.pourline.ui.TestApp
import dev.josueamaya.pourline.ui.TestAppDependencies
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Los recorridos de Cafés (lista, alta y edición, y detalle), con la aplicación entera (`TestApp`) y Room en memoria. */
class CoffeesTourTest {
  @get:Rule
  val rule = createAndroidComposeRule<ComponentActivity>()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private val env = TestAppDependencies(context)

  @After
  fun tearDown() = env.close()

  private fun show() = rule.setContent { TestApp(env) }

  private fun exists(tag: String) = rule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

  private fun waitFor(tag: String) = rule.waitUntil(5_000) { exists(tag) }

  private fun openCoffeesTab() {
    waitFor("tab_coffees")
    rule.onNodeWithTag("tab_coffees").performClick()
    waitFor("coffees_list")
  }

  private fun openPrepareTab() {
    rule.onNodeWithTag("tab_prepare").performClick()
    waitFor("prepare_start")
  }

  private fun goTo(counter: String) {
    var guard = 0
    while (rule.tourCounter() != counter && guard++ < 30) {
      val before = rule.tourCounter()
      rule.tapTourNext()
      rule.waitUntil(5_000) { rule.tourCounter() != before }
    }
    assertEquals(counter, rule.tourCounter())
  }

  private fun titleIs(resource: Int) = assertEquals(context.getString(resource), rule.textOf("tour_title"))

  private fun saveCoffee(name: String, roastDate: java.time.LocalDate? = java.time.LocalDate.now().minusDays(5), bagWeightG: Double? = 250.0) =
    runBlocking { env.coffees.save(TestBrews.coffeeDraft(name = name, roastDate = roastDate, bagWeightG = bagWeightG)) }

  @Test
  fun skippingTheGeneralTourStillShowsTheCoffeesTour() {
    env.skipOnboarding(showTours = listOf(Tours.prepare, Tours.coffees))
    show()
    rule.waitForTour("1/13")

    rule.tapTourSkip()
    rule.waitUntil(5_000) { !rule.tourVisible() }
    rule.waitUntil(5_000) {
      rule.onAllNodesWithText(context.getString(R.string.tour_skipped_hint)).fetchSemanticsNodes().isNotEmpty()
    }

    openCoffeesTab()
    // Sin cafés: añadir, buscar y activos/archivados.
    rule.waitForTour("1/3", env.tourSettings.seen.value)
    titleIs(R.string.tour_coffees_add_title)
  }

  @Test
  fun withoutCoffeesTheRowStepWaitsForALaterOpening() {
    env.skipOnboarding(showTours = listOf(Tours.coffees))
    show()
    openCoffeesTab()
    rule.waitForTour("1/3", env.tourSettings.seen.value)
    rule.completeTour()
    assertTrue("coffees.row" !in env.tourSettings.seen.value)

    saveCoffee("Etiopía Guji")
    openPrepareTab()
    openCoffeesTab()

    rule.waitForTour("1/1", env.tourSettings.seen.value)
    titleIs(R.string.tour_coffees_row_title)
    rule.completeTour()
    assertTrue("coffees.row" in env.tourSettings.seen.value)
  }

  @Test
  fun aScrolledListStillShowsTheSearchStep() {
    repeat(30) { saveCoffee("Café ${"%02d".format(it)}") }
    env.skipOnboarding()
    show()
    openCoffeesTab()
    waitFor("coffee_row")
    // Desplazar la lista y abrir un café: al volver, la lista sigue a mitad y la búsqueda ya no está compuesta.
    rule.onNodeWithTag("coffees_list").performScrollToIndex(20)
    rule.waitUntil(5_000) { !exists("coffees_search") }
    rule.onAllNodesWithTag("coffee_row").onFirst().performClick()
    waitFor("coffee_detail_list")

    env.tourSettings.reset()
    env.skipTours(except = listOf(Tours.coffees))
    rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
    waitFor("coffees_list")

    rule.waitForTour("1/4", env.tourSettings.seen.value)
    assertTrue("La búsqueda debería seguir sin componer al empezar", !exists("coffees_search"))
    goTo("2/4")
    titleIs(R.string.tour_coffees_search_title)
    rule.onNodeWithTag("coffees_search").assertIsDisplayed()
  }

  @Test
  fun theEditTourShowsItsThreeSteps() {
    env.skipOnboarding(showTours = listOf(Tours.coffeeEdit))
    show()
    openCoffeesTab()
    rule.onNodeWithTag("coffees_add").performClick()
    waitFor("coffee_edit_name")

    rule.waitForTour("1/3", env.tourSettings.seen.value)
    titleIs(R.string.tour_coffee_edit_photos_title)
    goTo("2/3")
    titleIs(R.string.tour_coffee_edit_read_label_title)
    goTo("3/3")
    titleIs(R.string.tour_coffee_edit_save_title)
    rule.completeTour()
    assertTrue(env.tourSettings.seen.value.containsAll(Tours.coffeeEdit.steps.map { it.id }))
  }

  @Test
  fun theDetailTourRevealsNewBagBelowALongLog() {
    val id = saveCoffee("Etiopía Guji")
    repeat(20) { i ->
      runBlocking {
        env.brews.save(
          TestBrews.finished(totalMs = 60_000, doseG = 15.0),
          TestBrews.context(startedAtEpochMs = 1_700_000_000_000 + i * 86_400_000L, coffee = UsedCoffee(id, "Etiopía Guji")),
        )
      }
    }
    env.skipOnboarding(showTours = listOf(Tours.coffeeDetail))
    show()
    openCoffeesTab()
    waitFor("coffee_row")
    rule.onNodeWithTag("coffee_row").performClick()

    rule.waitForTour("1/4", env.tourSettings.seen.value)
    titleIs(R.string.tour_coffee_detail_status_title)
    goTo("3/4")
    titleIs(R.string.tour_coffee_detail_new_bag_title)
    rule.onNodeWithTag("coffee_detail_new_bag").assertIsDisplayed()
  }

  @Test
  fun aCoffeeWithoutRoastOrStockSkipsTheStatusStep() {
    saveCoffee("Sin datos", roastDate = null, bagWeightG = null)
    env.skipOnboarding(showTours = listOf(Tours.coffeeDetail))
    show()
    openCoffeesTab()
    waitFor("coffee_row")
    rule.onNodeWithTag("coffee_row").performClick()

    rule.waitForTour("1/3", env.tourSettings.seen.value)
    titleIs(R.string.tour_coffee_detail_log_title)
  }
}
