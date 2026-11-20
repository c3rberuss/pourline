package dev.josueamaya.pourline.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import dev.josueamaya.pourline.Tab
import dev.josueamaya.pourline.coffee.CoffeeDraft
import dev.josueamaya.pourline.history.TestBrews
import dev.josueamaya.pourline.history.UsedCoffee
import dev.josueamaya.pourline.recipe.Recipe
import dev.josueamaya.pourline.recipe.RecipeStage
import dev.josueamaya.pourline.recipe.StageType
import dev.josueamaya.pourline.recipe.share.ImportSource
import dev.josueamaya.pourline.recipe.share.RecipeShareCodec
import dev.josueamaya.pourline.ui.settings.FakeBackupOperations
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Lo que hace la navegación al terminar una importación y mientras dura (SPEC-011 D22 y D28). */
class BackupNavigationTest {
  @get:Rule
  val rule = createAndroidComposeRule<ComponentActivity>()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private val fake = FakeBackupOperations()
  private lateinit var env: TestAppDependencies

  @Before
  fun setUp() {
    env = TestAppDependencies(context, backup = fake)
    env.skipOnboarding()
  }

  @After
  fun tearDown() = env.close()

  private fun show() = rule.setContent { TestApp(env) }

  private fun tab(tab: Tab) = rule.onNodeWithTag(tab.testTag)

  private fun exists(tag: String) = rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

  private fun waitFor(tag: String) = rule.waitUntil(5_000) { exists(tag) }

  private fun uri(): String {
    val stages = listOf(RecipeStage("Bloom", 0, 40.0, 30_000, StageType.POUR), RecipeStage("Segundo", 45_000, 250.0, 60_000, StageType.POUR))
    return RecipeShareCodec.encode(Recipe(name = "Receta importada", doseG = 15.0, stages = stages), grinder = null)!!.uri
  }

  @Test
  fun afterImportingTheOtherTabsGoBackToTheirRoots() {
    val coffeeId = runBlocking { env.coffees.save(CoffeeDraft(name = "Etiopía Guji")) }
    runBlocking { env.brews.save(TestBrews.finished(totalMs = 60_000), TestBrews.context(coffee = UsedCoffee(coffeeId, "Etiopía Guji"))) }
    show()
    // Un café abierto en Cafés y una preparación abierta en Historial.
    tab(Tab.COFFEES).performClick()
    waitFor("coffee_row")
    rule.onNodeWithTag("coffee_row").performClick()
    waitFor("coffee_detail_edit")
    tab(Tab.HISTORY).performClick()
    waitFor("history_row")
    rule.onNodeWithTag("history_row").performClick()
    waitFor("brew_detail_coffee")
    // Preparar -> Ajustes.
    tab(Tab.PREPARE).performClick()
    waitFor("prepare_settings")
    rule.onNodeWithTag("prepare_settings").performClick()
    waitFor("settings_backup_export")

    fake.emitImported()

    // Ajustes sigue arriba en Preparar.
    rule.waitForIdle()
    rule.onNodeWithTag("settings_backup_export").assertExists()
    tab(Tab.COFFEES).performClick()
    waitFor("coffees_add")
    assertTrue(!exists("coffee_detail_edit"))
    tab(Tab.HISTORY).performClick()
    waitFor("history_row")
    assertTrue(!exists("brew_detail_coffee"))
    tab(Tab.PREPARE).performClick()
    waitFor("settings_backup_export")
  }

  @Test
  fun aRecipeLinkWaitsWhileImporting() {
    show()
    waitFor("prepare_start")
    fake.setApplying(true)

    env.recipeImports.submit(uri(), ImportSource.LINK, hasSession = false)

    // Mientras se reemplazan los datos la vista previa no se abre y el pendiente sigue en la cola.
    rule.waitForIdle()
    Thread.sleep(500)
    rule.waitForIdle()
    assertTrue(!exists("recipe_import_save"))
    assertNotNull(env.recipeImports.pending.value)

    fake.setApplying(false)

    waitFor("recipe_import_save")
    assertNull(env.recipeImports.pending.value)
  }
}
