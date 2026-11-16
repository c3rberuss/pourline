package dev.dotscale.ui.grinders

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import dev.dotscale.R
import dev.dotscale.grinder.TestGrinders
import dev.dotscale.grinder.addGrinder
import dev.dotscale.ui.TestApp
import dev.dotscale.ui.TestAppDependencies
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class GrindersScreenTest {
  @get:Rule
  val rule = createAndroidComposeRule<ComponentActivity>()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private lateinit var env: TestAppDependencies
  private lateinit var ui: GrindersUi

  @Before
  fun setUp() {
    env = TestAppDependencies(context)
    env.skipOnboarding()
    ui = GrindersUi(rule, env)
  }

  @After
  fun tearDown() = env.close()

  @Test
  fun aRowShowsThePrimaryUnitAndTheDefaultTag() {
    val tres = runBlocking { env.db.addGrinder(TestGrinders.tres) }
    val uno = runBlocking { env.db.addGrinder(TestGrinders.uno) }
    env.grinderSettings.setDefaultGrinderId(tres)
    rule.setContent { TestApp(env) }
    ui.openGrinders()

    ui.waitFor("grinder_row_$tres")
    ui.node("grinder_row_$tres").assertIsDisplayed()
      .assertTextContains("Acme Tres", substring = true)
      // La jerarquía completa: Rotaciones · Números · Clics.
      .assertTextContains("Rotations", substring = true)
      .assertTextContains("Clicks", substring = true)
    assertTrue(ui.exists("grinder_default_$tres"))
    assertFalse(ui.exists("grinder_default_$uno"))
    ui.node("grinder_row_$uno").assertTextContains("Clicks", substring = true)
  }

  @Test
  fun withoutGrindersItShowsTheEmptyStateAndAddOpensTheCatalog() {
    rule.setContent { TestApp(env) }
    ui.openGrinders()

    ui.waitFor("grinders_empty")
    rule.onNodeWithText(context.getString(R.string.grinders_empty)).assertIsDisplayed()
    ui.click("grinders_add")
    ui.waitFor("grinder_catalog_search")
  }

  @Test
  fun aRetiredGrinderCarriesItsTag() {
    runBlocking { env.db.addGrinder(TestGrinders.uno.copy(catalogKey = "acme-viejo", owned = true, retired = true)) }
    rule.setContent { TestApp(env) }
    ui.openGrinders()
    rule.onNodeWithText(context.getString(R.string.grinder_tag_retired)).assertIsDisplayed()
  }
}
