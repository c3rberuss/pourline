package dev.dotscale.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import dev.dotscale.R
import dev.dotscale.Tab
import dev.dotscale.coffee.CoffeeDraft
import dev.dotscale.history.TestBrews
import dev.dotscale.history.UsedCoffee
import dev.dotscale.scale.ConnectionState
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Las cuatro pestañas con su pila, la barra inferior y "Atrás" (D30 y D31), con Room en memoria y la báscula simulada. */
class TabsNavigationTest {
  @get:Rule
  val rule = createAndroidComposeRule<ComponentActivity>()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private lateinit var env: TestAppDependencies

  @Before
  fun setUp() {
    env = TestAppDependencies(context)
    env.skipOnboarding()
  }

  @After
  fun tearDown() = env.close()

  private fun show() = rule.setContent { TestApp(env) }

  private fun tab(tab: Tab) = rule.onNodeWithTag(tab.testTag)

  private fun exists(tag: String) = rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

  private fun waitFor(tag: String) = rule.waitUntil(5_000) { exists(tag) }

  private fun back() = rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }

  private fun saveCoffee(name: String = "Etiopía Guji") = runBlocking { env.coffees.save(CoffeeDraft(name = name)) }

  private fun saveBrew(coffeeId: Long) = runBlocking {
    env.brews.save(TestBrews.finished(totalMs = 60_000), TestBrews.context(coffee = UsedCoffee(coffeeId, "Etiopía Guji")))
  }

  @Test
  fun fourTabsShowAnIconAndAText() {
    show()

    Tab.entries.forEach { t ->
      tab(t).assertIsDisplayed().assert(hasText(context.getString(t.label)))
    }
  }

  @Test
  fun openingACoffeeGoingToHistoryAndComingBackKeepsTheDetail() {
    saveCoffee()
    show()

    tab(Tab.COFFEES).performClick()
    waitFor("coffee_row")
    rule.onNodeWithTag("coffee_row").performClick()
    waitFor("coffee_detail_edit")

    tab(Tab.HISTORY).performClick()
    rule.waitUntil(5_000) { !exists("coffee_detail_edit") }
    tab(Tab.COFFEES).performClick()

    waitFor("coffee_detail_edit")
    rule.onNodeWithTag("coffee_detail_edit").assertIsDisplayed()
  }

  @Test
  fun tappingTheActiveTabReturnsToItsRoot() {
    saveCoffee()
    show()
    tab(Tab.COFFEES).performClick()
    waitFor("coffee_row")
    rule.onNodeWithTag("coffee_row").performClick()
    waitFor("coffee_detail_edit")

    tab(Tab.COFFEES).performClick()

    waitFor("coffees_add")
    rule.onNodeWithTag("coffees_add").assertIsDisplayed()
    assertTrue(!exists("coffee_detail_edit"))
  }

  @Test
  fun backAtTheRootOfCoffeesSelectsPrepare() {
    show()
    tab(Tab.COFFEES).performClick()
    waitFor("coffees_add")

    back()

    waitFor("prepare_start")
    rule.onNodeWithTag("prepare_start").assertIsDisplayed()
    assertTrue(!rule.activity.isFinishing)
  }

  @Test
  fun backInsideATabPopsOnlyThatTab() {
    saveCoffee()
    show()
    tab(Tab.COFFEES).performClick()
    waitFor("coffee_row")
    rule.onNodeWithTag("coffee_row").performClick()
    waitFor("coffee_detail_edit")

    back()

    waitFor("coffees_add")
    assertTrue(!exists("prepare_start"))
  }

  @Test
  fun backAtTheRootOfPrepareFinishesTheActivity() {
    show()
    rule.onNodeWithTag("prepare_start").assertIsDisplayed()

    back()

    rule.waitUntil(5_000) { rule.activity.isFinishing }
  }

  @Test
  fun theCoffeeOfABrewOpensInTheHistoryStack() {
    val coffeeId = saveCoffee()
    saveBrew(coffeeId)
    show()
    tab(Tab.HISTORY).performClick()
    waitFor("history_row")
    rule.onNodeWithTag("history_row").performClick()
    waitFor("brew_detail_coffee")
    rule.onNodeWithTag("brew_detail_coffee").performClick()
    waitFor("coffee_detail_edit")

    // Cafés conserva su raíz: el detalle vive en la pila de Historial.
    tab(Tab.COFFEES).performClick()
    waitFor("coffees_add")
    assertTrue(!exists("coffee_detail_edit"))

    tab(Tab.HISTORY).performClick()
    waitFor("coffee_detail_edit")
    back()
    waitFor("brew_detail_coffee")
  }

  @Test
  fun theBarHidesInTheFullScreenEditors() {
    saveBrew(saveCoffee())
    show()

    tab(Tab.COFFEES).performClick()
    waitFor("coffees_add")
    rule.onNodeWithTag("coffees_add").performClick()
    rule.waitUntil(5_000) { !exists("tab_prepare") }
    back()
    waitFor("tab_prepare")

    tab(Tab.RECIPES).performClick()
    waitFor("recipes_add")
    rule.onNodeWithTag("recipes_add").performClick()
    rule.waitUntil(5_000) { !exists("tab_prepare") }
    back()
    waitFor("tab_prepare")

    tab(Tab.HISTORY).performClick()
    waitFor("history_row")
    rule.onNodeWithTag("history_row").performClick()
    waitFor("brew_detail_edit")
    rule.onNodeWithTag("tab_prepare").assertIsDisplayed()
    rule.onNodeWithTag("brew_detail_edit").performClick()
    rule.waitUntil(5_000) { !exists("tab_prepare") }
  }

  @Test
  fun aConnectedSimulatedScaleStaysConnectedAcrossTheFourTabs() {
    show()
    rule.onNodeWithTag("scale_card_connect").performClick()
    waitFor("connect_simulated")
    rule.onNodeWithTag("connect_simulated").performClick()
    rule.waitUntil(10_000) { env.scaleSession.ui.value.connection == ConnectionState.Ready }

    Tab.entries.forEach { t ->
      tab(t).performClick()
      rule.waitForIdle()
    }
    tab(Tab.PREPARE).performClick()

    assertEquals(ConnectionState.Ready, env.scaleSession.ui.value.connection)
    assertEquals(1, env.connectionsMade.size)
    rule.onNodeWithTag("scale_card")
      .assert(hasAnyDescendant(hasText(context.getString(R.string.scale_card_connected, env.scaleSession.target.value!!.name))))
  }
}
