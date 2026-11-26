package dev.josueamaya.pourline.ui.recipes

import android.Manifest
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.platform.app.InstrumentationRegistry
import dev.josueamaya.pourline.R
import dev.josueamaya.pourline.Tab
import dev.josueamaya.pourline.grinder.TestGrinderCatalog
import dev.josueamaya.pourline.history.BrewFilter
import dev.josueamaya.pourline.history.ReferenceType
import dev.josueamaya.pourline.settings.ReferenceKey
import dev.josueamaya.pourline.recipe.catalog.CatalogLanguage
import dev.josueamaya.pourline.recipe.catalog.FakeCatalogFetcher
import dev.josueamaya.pourline.recipe.catalog.TestRecipeCatalog
import dev.josueamaya.pourline.settings.ExperienceMode
import dev.josueamaya.pourline.ui.RecordingNotifier
import dev.josueamaya.pourline.ui.TestApp
import dev.josueamaya.pourline.ui.TestAppDependencies
import dev.josueamaya.pourline.ui.session.SessionDriver
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicLong

/**
 * Del detalle de una receta recomendada a Preparar y a una preparación guardada, sin copiarla a "Mis recetas"
 * (SPEC-012 §3.7, D14, D30), con la aplicación entera, un reloj de prueba y la preparación sin báscula.
 */
class CatalogPrepareFlowTest {
  @get:Rule
  val rule = createAndroidComposeRule<ComponentActivity>()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private val now = AtomicLong(10_000)
  private val env = TestAppDependencies(context, catalogFetcher = FakeCatalogFetcher(), seedRecipes = false, notifier = RecordingNotifier(), clock = { now.get() })
  private val driver = SessionDriver(rule, env)
  private val manager get() = env.scaleSession
  private val english = CatalogLanguage.of(context.getString(R.string.catalog_language)) == CatalogLanguage.EN

  @Before
  fun grantNotifications() {
    // Sin el permiso, "Empezar sin báscula" abriría el diálogo del sistema delante de la app.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
    }
  }

  @After
  fun tearDown() = env.close()

  private fun name(es: String, en: String) = if (english) en else es

  private fun show(mode: ExperienceMode = ExperienceMode.BASIC) {
    env.skipOnboarding(mode)
    rule.setContent { TestApp(env) }
    driver.waitFor("tab_recipes")
  }

  /** Recetas → Recomendadas → la fila de [key] → "Preparar" (sin guardar): termina en la pestaña Preparar. */
  private fun prepareFromCatalog(key: String) {
    driver.node(Tab.RECIPES.testTag).performClick()
    driver.waitFor("recipes_segment_catalog")
    driver.node("recipes_segment_catalog").performClick()
    driver.waitFor("catalog_list")
    // La lista no compone lo que no cabe en pantalla: se desplaza hasta la fila.
    rule.onNodeWithTag("catalog_list").performScrollToNode(hasTestTag("catalog_row_$key"))
    driver.node("catalog_row_$key").performClick()
    driver.waitFor("catalog_detail_prepare")
    rule.onNodeWithTag("catalog_detail_prepare").performScrollTo().performClick()
    driver.waitFor("prepare_recipe")
    rule.waitUntil(5_000) { manager.ui.value.target != null }
  }

  private fun ownRecipes() = runBlocking { env.recipes.all() }

  private fun savedBrews() = runBlocking { env.brews.list(BrewFilter()).first() }

  private fun at(ms: Long) {
    val t0 = manager.ui.value.brew.t0MonoMs!!
    now.set(t0 + ms)
    rule.waitUntil(10_000) { manager.ui.value.elapsedMs == ms }
    rule.waitForIdle()
  }

  private fun text(tag: String) = rule.onNodeWithTag(tag, useUnmergedTree = true)

  private fun ownGrinder(catalogKey: String) {
    env.seedGrinders()
    val grinder = runBlocking { env.grinders.byCatalogKey(catalogKey)!! }
    runBlocking { env.grinders.setOwned(grinder.id, true) }
  }

  @Test
  fun savingFromTheDetailMarksTheRowAsSavedInTheList() {
    show()
    driver.node(Tab.RECIPES.testTag).performClick()
    driver.waitFor("recipes_segment_catalog")
    driver.node("recipes_segment_catalog").performClick()
    driver.waitFor("catalog_row_${TestRecipeCatalog.KEY_V60}")
    driver.node("catalog_row_${TestRecipeCatalog.KEY_V60}").performClick()
    driver.waitFor("catalog_detail_save")
    rule.onNodeWithTag("catalog_detail_save").performScrollTo().performClick()
    rule.waitUntil(5_000) { ownRecipes().size == 1 }
    rule.onNodeWithContentDescription(context.getString(R.string.scale_back)).performClick()
    rule.waitUntil(5_000) { rule.onAllNodesWithTag("catalog_saved_tag", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    assertEquals(TestRecipeCatalog.KEY_V60, ownRecipes().single().catalogKey)
  }

  @Test
  fun prepareSelectsTheCatalogRecipeAndOpensPrepare() {
    show()
    prepareFromCatalog(TestRecipeCatalog.KEY_V60)

    val target = manager.ui.value.target!!
    assertEquals(ReferenceKey.Catalog(TestRecipeCatalog.KEY_V60), target.key)
    assertEquals(ReferenceType.CATALOG, target.type)
    assertEquals(name(TestRecipeCatalog.NAME_V60_ES, TestRecipeCatalog.NAME_V60_EN), target.name)
    rule.onNode(hasText(target.name) and hasAnyAncestor(hasTestTag("prepare_recipe")), useUnmergedTree = true).assertExists()
  }

  @Test
  fun nothingIsAddedToMyRecipes() {
    show()
    prepareFromCatalog(TestRecipeCatalog.KEY_V60)
    assertTrue(ownRecipes().isEmpty())
  }

  @Test
  fun aBrewMadeFromTheCatalogIsSavedWithTheCatalogType() {
    show()
    prepareFromCatalog(TestRecipeCatalog.KEY_V60)
    val recipeName = name(TestRecipeCatalog.NAME_V60_ES, TestRecipeCatalog.NAME_V60_EN)

    driver.waitFor("prepare_start_scaleless")
    driver.node("prepare_start_scaleless").performClick()
    driver.waitFor("scaleless_ready")
    driver.node("scaleless_start").performClick()
    driver.waitFor("scaleless_active")
    at(10_000)
    driver.finishFromButton()

    rule.waitUntil(10_000) { savedBrews().size == 1 }
    val saved = runBlocking { env.brews.get(savedBrews().single().id) }!!.entity
    assertEquals(ReferenceType.CATALOG, saved.referenceType)
    assertEquals(TestRecipeCatalog.KEY_V60, saved.referenceKey)
    assertEquals(recipeName, saved.referenceName)
    assertTrue(ownRecipes().isEmpty())

    // El detalle de la preparación muestra el nombre de la receta recomendada.
    driver.waitFor("summary_done")
    driver.node("summary_done").performClick()
    driver.waitFor("prepare_start")
    driver.node(Tab.HISTORY.testTag).performClick()
    driver.waitFor("history_row")
    driver.node("history_row").performClick()
    driver.waitFor("brew_detail_facts")
    text("brew_detail_facts_recipe").assertTextContains(recipeName)
  }

  @Test
  fun prepareUsesTheGrindConvertedToTheFirstCompatibleGrinder() {
    // La receta pide Uno 20 (MEDIUM); el usuario solo tiene Dos, donde MEDIUM es 30.
    ownGrinder(TestGrinderCatalog.KEY_DOS)
    show()
    prepareFromCatalog(TestRecipeCatalog.KEY_GRINDER)

    driver.waitFor("grind_card_value")
    rule.onNodeWithTag("grind_card_grinder").assertContentDescriptionContains("Acme Dos", substring = true)
    text("grind_card_value").assertTextContains("30", substring = true)
    rule.onNodeWithTag("grind_card_source").assertTextContains("Uno", substring = true)
    assertNotNull(manager.ui.value.target!!.preferredGrinderId)
  }

  @Test
  fun aTextOnlyGrindShowsItsTextAndTheMethodSuggestion() {
    // "Media-fina" no tiene molino: Preparar enseña el texto y, con un molino propio, la sugerencia del método.
    ownGrinder(TestGrinderCatalog.KEY_UNO)
    show()
    prepareFromCatalog(TestRecipeCatalog.KEY_V60)

    driver.waitFor("grind_card_source")
    rule.onNodeWithTag("grind_card_source").assertTextContains(name("Media-fina", "Medium-fine"), substring = true)
    driver.waitFor("grind_card_value")
    assertTrue(rule.onAllNodesWithTag("grind_card_marks", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
    assertFalse(manager.ui.value.target!!.sourceGrind?.grinderId != null)
  }
}
