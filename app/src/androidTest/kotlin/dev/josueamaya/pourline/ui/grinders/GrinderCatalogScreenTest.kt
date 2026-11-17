package dev.josueamaya.pourline.ui.grinders

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import dev.josueamaya.pourline.grinder.TestGrinderCatalog
import dev.josueamaya.pourline.grinder.TestGrinders
import dev.josueamaya.pourline.grinder.addGrinder
import dev.josueamaya.pourline.ui.TestApp
import dev.josueamaya.pourline.ui.TestAppDependencies
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class GrinderCatalogScreenTest {
  @get:Rule
  val rule = createAndroidComposeRule<ComponentActivity>()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private lateinit var env: TestAppDependencies
  private lateinit var ui: GrindersUi

  @Before
  fun setUp() {
    env = TestAppDependencies(context)
    env.skipOnboarding()
    env.seedGrinders()
    ui = GrindersUi(rule, env)
  }

  @After
  fun tearDown() = env.close()

  private fun openCatalog() {
    rule.setContent { TestApp(env) }
    ui.openGrinders()
    ui.click("grinders_add")
    ui.waitFor("grinder_catalog_search")
  }

  private fun row(key: String) = "grinder_catalog_$key"

  @Test
  fun theSearchIgnoresCaseAndAccentsAndMatchesBrandAndModel() {
    openCatalog()
    ui.waitFor(row(TestGrinderCatalog.KEY_TRES))
    assertTrue(listOf("uno", "dos", "tres", "inverso").all { ui.exists(row("acme-$it")) })

    rule.onNodeWithTag("grinder_catalog_search").performTextInput("acme tres")
    rule.waitUntil(5_000) { !ui.exists(row(TestGrinderCatalog.KEY_UNO)) }
    assertTrue(ui.exists(row(TestGrinderCatalog.KEY_TRES)))
    assertEquals(false, ui.exists(row(TestGrinderCatalog.KEY_DOS)))

    ui.click("grinder_catalog_search_clear")
    rule.onNodeWithTag("grinder_catalog_search").performTextInput("ACMÉ")
    ui.waitFor(row(TestGrinderCatalog.KEY_UNO))
    assertTrue(listOf("uno", "dos", "tres", "inverso").all { ui.exists(row("acme-$it")) })

    ui.click("grinder_catalog_search_clear")
    rule.onNodeWithTag("grinder_catalog_search").performTextInput("zzz")
    ui.waitFor("grinder_catalog_empty")
  }

  @Test
  fun iHaveItAddsTheGrinderToMyGrindersWithoutLeavingTheList() {
    openCatalog()
    ui.waitFor("grinder_catalog_own_acme-uno")
    assertEquals(emptyList<String>(), ui.owned().map { it.model })

    ui.click("grinder_catalog_own_acme-uno")
    ui.waitFor("grinder_catalog_owned_acme-uno")
    assertFalse(ui.exists("grinder_catalog_own_acme-uno"))
    assertTrue(ui.exists("grinder_catalog_search"))
    assertEquals(listOf("Uno"), ui.owned().map { it.model })
  }

  @Test
  fun aRetiredGrinderDoesNotShow() {
    runBlocking { env.db.addGrinder(TestGrinders.uno.copy(model = "Viejo", catalogKey = "acme-viejo", owned = false, retired = true)) }
    openCatalog()
    ui.waitFor(row(TestGrinderCatalog.KEY_UNO))
    assertFalse(ui.exists(row("acme-viejo")))
  }

  @Test
  fun createYourOwnGrinderOpensTheEditor() {
    openCatalog()
    ui.waitFor("grinder_catalog_create")
    ui.click("grinder_catalog_create")
    ui.waitFor("grinder_edit_brand")
  }
}
