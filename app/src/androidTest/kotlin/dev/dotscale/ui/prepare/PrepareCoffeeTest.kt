package dev.dotscale.ui.prepare

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import dev.dotscale.R
import dev.dotscale.history.BrewFilter
import dev.dotscale.ui.TestApp
import dev.dotscale.ui.TestAppDependencies
import dev.dotscale.history.TestBrews
import dev.dotscale.ui.session.SessionDriver
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/** El café elegido en Preparar viaja con la sesión guiada: se congela en `Active` y se guarda vinculado, con su stock. */
class PrepareCoffeeTest {
  @get:Rule
  val rule = createAndroidComposeRule<ComponentActivity>()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private val env = TestAppDependencies(context)
  private val driver = SessionDriver(rule, env)
  private var coffeeId = 0L

  @Before
  fun setUp() {
    env.skipOnboarding()
    coffeeId = runBlocking {
      val today = LocalDate.now()
      env.coffees.save(TestBrews.coffeeDraft(name = "Etiopía Guji", roastDate = today.minusDays(9), bagWeightG = 250.0))
    }
    rule.setContent { TestApp(env) }
    driver.connect()
  }

  @After
  fun tearDown() = env.close()

  @Test
  fun pickingACoffeeInPrepareAndBrewingLinksTheBrewAndDiscountsTheStock() {
    rule.onNodeWithTag("prepare_coffee").performClick()
    rule.waitUntil(5_000) { rule.onAllNodesWithTag("coffee_picker_row").fetchSemanticsNodes().size == 1 }
    rule.onNodeWithTag("coffee_picker_row").performClick()
    driver.waitGone("coffee_picker")
    rule.onNodeWithText(context.getString(R.string.brew_setup_coffee_value, "Etiopía Guji", 9)).assertIsDisplayed()

    driver.reachPourStep()
    driver.node("prep_start_timer").performClick()
    driver.waitFor("session_timer")
    driver.node("session_finish").performClick()
    driver.waitFor("session_summary")

    rule.waitUntil(10_000) { runBlocking { env.brews.list(BrewFilter()).first() }.isNotEmpty() }
    val saved = runBlocking { env.brews.list(BrewFilter()).first() }.single()
    assertEquals("Etiopía Guji", saved.coffee)
    val detail = runBlocking { env.coffees.coffee(coffeeId).first() }!!
    assertEquals(1, detail.brews.size)
    assertEquals(saved.id, detail.brews.single().brewId)
    assertNotNull(detail.stockG)
    assertTrue("el stock bajó la dosis: ${detail.stockG}", detail.stockG!! < 250.0)
    assertEquals(235.0, detail.stockG!!, 1.0)
  }

  @Test
  fun newCoffeeFromThePickerOpensTheEditorInThePrepareStack() {
    rule.onNodeWithTag("prepare_coffee").performClick()
    driver.waitFor("coffee_picker_new")
    rule.onNodeWithTag("coffee_picker_new").performClick()

    driver.waitFor("coffee_edit_name")
    driver.waitGone("coffee_picker")
    assertTrue(!driver.exists("tab_coffees_selected"))
  }
}
