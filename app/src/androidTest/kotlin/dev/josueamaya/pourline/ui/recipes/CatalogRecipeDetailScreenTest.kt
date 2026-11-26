package dev.josueamaya.pourline.ui.recipes

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import dev.josueamaya.pourline.R
import dev.josueamaya.pourline.grinder.TestGrinderCatalog
import dev.josueamaya.pourline.recipe.catalog.CatalogLanguage
import dev.josueamaya.pourline.recipe.catalog.FakeCatalogFetcher
import dev.josueamaya.pourline.recipe.catalog.FetchResult
import dev.josueamaya.pourline.recipe.catalog.TestRecipeCatalog
import dev.josueamaya.pourline.settings.ExperienceMode
import dev.josueamaya.pourline.settings.LocalExperienceMode
import dev.josueamaya.pourline.ui.TestAppDependencies
import dev.josueamaya.pourline.ui.recipes.catalog.CatalogRecipeDetailScreen
import dev.josueamaya.pourline.ui.recipes.catalog.CatalogRecipeDetailViewModel
import dev.josueamaya.pourline.ui.theme.PourlineTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** El detalle de una receta recomendada (SPEC-012 §3.8) con Room en memoria y el catálogo de prueba. */
class CatalogRecipeDetailScreenTest {
  @get:Rule
  val rule = createAndroidComposeRule<ComponentActivity>()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private val env = TestAppDependencies(context, catalogFetcher = FakeCatalogFetcher(), seedRecipes = false)
  private val english = CatalogLanguage.of(context.getString(R.string.catalog_language)) == CatalogLanguage.EN
  private val opened = mutableListOf<String>()
  private val prepared = mutableListOf<String>()
  private var browser = true

  @After
  fun tearDown() = env.close()

  private fun name(es: String, en: String) = if (english) en else es

  private fun show(key: String = TestRecipeCatalog.KEY_V60, mode: ExperienceMode = ExperienceMode.BASIC) {
    val viewModel = CatalogRecipeDetailViewModel(key, env.recipeCatalog, env.recipes, env.grinders, env.grindTexts, env.catalogLanguage)
    rule.setContent {
      PourlineTheme(darkTheme = false) {
        CompositionLocalProvider(LocalExperienceMode provides mode) {
          CatalogRecipeDetailScreen(
            viewModel = viewModel,
            onBack = {},
            onPrepare = { prepared += it },
            openUrl = { url ->
              opened += url
              browser
            },
          )
        }
      }
    }
  }

  private fun exists(tag: String) = rule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

  private fun waitFor(tag: String) = rule.waitUntil(5_000) { exists(tag) }

  private fun click(tag: String) {
    rule.onNodeWithTag(tag).performScrollTo().performClick()
  }

  private fun waitForText(text: String) = rule.waitUntil(5_000) {
    rule.onAllNodes(hasText(text, substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
  }

  private fun ownRecipes() = runBlocking { env.recipes.all() }

  @Test
  fun theDetailShowsEveryFieldAndTheSteps() {
    show()
    waitFor("catalog_detail_name")
    rule.onNodeWithTag("catalog_detail_name").assertTextContains(name(TestRecipeCatalog.NAME_V60_ES, TestRecipeCatalog.NAME_V60_EN))
    rule.onNodeWithTag("catalog_detail_method").assertTextContains("V60")
    rule.onNodeWithTag("catalog_detail_author").assertTextContains("Némo Pop", substring = true)
    rule.onNodeWithTag("catalog_detail_context").assertTextContains(name("Receta de prueba", "Test recipe"))
    rule.onNodeWithTag("catalog_detail_grind_text").assertTextContains(name("Media-fina", "Medium-fine"))
    rule.onNodeWithTag("catalog_detail_notes").assertTextContains(name("Notas de la V60", "V60 notes"))
    rule.onNodeWithTag("catalog_detail_facts").assertExists()
    rule.onNodeWithTag("catalog_detail_duration").assertTextContains("1:05")
    // Tres pasos, con la instrucción del primero y del segundo.
    for (index in 0..2) rule.onNodeWithTag("catalog_detail_step_$index").assertExists()
    rule.onNodeWithTag("catalog_detail_step_instruction_0").assertTextContains(name("Vierte en espiral", "Pour in a spiral"))
    rule.onNodeWithTag("catalog_detail_step_instruction_1").assertTextContains(name("Espera", "Wait"))
    assertFalse(exists("catalog_detail_step_instruction_2"))
    assertFalse(exists("catalog_detail_step_3"))
    assertFalse(exists("catalog_detail_already_saved"))
  }

  @Test
  fun viewSourceOpensTheUrl() {
    show()
    waitFor("catalog_detail_source")
    click("catalog_detail_source")
    assertEquals(listOf("https://example.com/nemo"), opened)
  }

  @Test
  fun aRecipeWithoutSourceHasNoViewSourceButton() {
    show(TestRecipeCatalog.KEY_CLEVER)
    waitFor("catalog_detail_name")
    assertFalse(exists("catalog_detail_source"))
    assertFalse(exists("catalog_detail_author"))
    assertFalse(exists("catalog_detail_notes"))
  }

  @Test
  fun withoutABrowserTheMessageIsShown() {
    browser = false
    show()
    waitFor("catalog_detail_source")
    click("catalog_detail_source")
    waitForText(context.getString(R.string.catalog_no_browser))
    assertEquals(1, opened.size)
  }

  @Test
  fun theCurveIsShownInExpertAndHiddenInBasic() {
    show(mode = ExperienceMode.EXPERT)
    waitFor("catalog_detail_preview")
  }

  @Test
  fun theCurveIsHiddenInBasic() {
    show(mode = ExperienceMode.BASIC)
    waitFor("catalog_detail_name")
    assertFalse(exists("catalog_detail_preview"))
  }

  @Test
  fun prepareReportsTheKeyWithoutSavingAnything() {
    show()
    waitFor("catalog_detail_prepare")
    click("catalog_detail_prepare")
    assertEquals(listOf(TestRecipeCatalog.KEY_V60), prepared)
    assertTrue(ownRecipes().isEmpty())
  }

  @Test
  fun saveCreatesACopyShowsTheMessageAndTheSavedTag() {
    show()
    waitFor("catalog_detail_save")
    click("catalog_detail_save")
    waitForText(context.getString(R.string.catalog_detail_saved))

    val copy = ownRecipes().single()
    assertEquals(name(TestRecipeCatalog.NAME_V60_ES, TestRecipeCatalog.NAME_V60_EN), copy.name)
    assertEquals(TestRecipeCatalog.KEY_V60, copy.catalogKey)
    assertEquals("Némo Pop", copy.author)
    assertEquals("https://example.com/nemo", copy.sourceUrl)
    assertEquals(3, copy.stages.size)
    assertFalse(copy.localizedNames)
    assertNull(copy.builtInKey)
    // Ahora hay una copia: el detalle lo avisa.
    waitFor("catalog_detail_already_saved")
  }

  @Test
  fun anExistingCopyShowsTheWarningAndStillSaves() {
    show()
    waitFor("catalog_detail_save")
    click("catalog_detail_save")
    waitFor("catalog_detail_already_saved")

    click("catalog_detail_save")
    rule.waitUntil(5_000) { ownRecipes().size == 2 }
    val base = name(TestRecipeCatalog.NAME_V60_ES, TestRecipeCatalog.NAME_V60_EN)
    assertEquals(setOf(base, "$base (2)"), ownRecipes().map { it.name }.toSet())
    assertTrue(ownRecipes().all { it.catalogKey == TestRecipeCatalog.KEY_V60 })
  }

  @Test
  fun theGrindPickerAppearsOnlyWithMoreThanOneOption() {
    env.seedGrinders()
    // Sin molinos propios: solo el texto de la receta, sin selector.
    show(TestRecipeCatalog.KEY_GRINDER)
    waitFor("catalog_detail_grind_text")
    assertFalse(exists("catalog_detail_grind"))
  }

  @Test
  fun aConvertibleGrindShowsThePickerAndSavingAsTextKeepsTheText() {
    env.seedGrinders()
    val dos = runBlocking { env.grinders.byCatalogKey(TestGrinderCatalog.KEY_DOS)!! }
    runBlocking { env.grinders.setOwned(dos.id, true) }
    show(TestRecipeCatalog.KEY_GRINDER)
    waitFor("catalog_detail_grind")
    assertFalse(exists("catalog_detail_grind_text"))

    // La opción por defecto usa el molino propio: la copia queda enlazada a Dos.
    click("catalog_detail_save")
    rule.waitUntil(5_000) { ownRecipes().size == 1 }
    val copy = ownRecipes().single()
    assertEquals(dos.id, copy.grind!!.grinderId)
  }

  @Test
  fun aSavedCopyWithTheUserGrinderIsLinkedToIt() {
    env.seedGrinders()
    val uno = runBlocking { env.grinders.byCatalogKey(TestGrinderCatalog.KEY_UNO)!! }
    runBlocking { env.grinders.setOwned(uno.id, true) }
    show(TestRecipeCatalog.KEY_GRINDER)
    waitFor("catalog_detail_grind_text")
    rule.onNodeWithTag("catalog_detail_grind_text").assertTextContains("Acme Uno", substring = true)

    click("catalog_detail_save")
    rule.waitUntil(5_000) { ownRecipes().size == 1 }
    val grind = ownRecipes().single().grind!!
    assertEquals(uno.id, grind.grinderId)
    assertEquals(20.0, grind.value!!, 0.0)
  }

  @Test
  fun aRecipeRemovedFromTheCatalogShowsTheMissingState() {
    show()
    waitFor("catalog_detail_name")
    // Una actualización con la pantalla abierta que ya no trae la receta.
    val without = TestRecipeCatalog.json(revision = 2).replace("\"${TestRecipeCatalog.KEY_V60}\"", "\"removed_${TestRecipeCatalog.KEY_V60}\"")
    (env.catalogFetcher as FakeCatalogFetcher).enqueue(FetchResult.Fetched(without, "etag-2"))
    runBlocking { env.recipeCatalog.refresh(force = true).await() }
    waitFor("catalog_detail_missing")
    assertFalse(exists("catalog_detail_save"))
    assertTrue(ownRecipes().isEmpty())
  }
}
