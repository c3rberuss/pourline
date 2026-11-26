package dev.josueamaya.pourline.ui.recipes

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.test.platform.app.InstrumentationRegistry
import dev.josueamaya.pourline.R
import dev.josueamaya.pourline.grinder.TestGrinderCatalog
import dev.josueamaya.pourline.recipe.catalog.CatalogLanguage
import dev.josueamaya.pourline.recipe.catalog.CatalogParseResult
import dev.josueamaya.pourline.recipe.catalog.FakeCatalogFetcher
import dev.josueamaya.pourline.recipe.catalog.FetchResult
import dev.josueamaya.pourline.recipe.catalog.RecipeCatalogParser
import dev.josueamaya.pourline.recipe.catalog.TestRecipeCatalog
import dev.josueamaya.pourline.recipe.catalog.grinderCatalogByKey
import dev.josueamaya.pourline.recipe.catalog.toListItem
import dev.josueamaya.pourline.recipe.ResourcesRecipeTexts
import dev.josueamaya.pourline.recipe.v60Classic
import dev.josueamaya.pourline.ui.TestApp
import dev.josueamaya.pourline.ui.TestAppDependencies
import dev.josueamaya.pourline.ui.session.formatClock
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** La pestaña Recetas con "Mis recetas" y "Recomendadas", con la aplicación entera y el catálogo de prueba (D48). */
class RecipesCatalogScreenTest {
  @get:Rule
  val rule = createAndroidComposeRule<ComponentActivity>()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private val fetcher = FakeCatalogFetcher()
  private var env: TestAppDependencies? = null

  private val language = CatalogLanguage.of(context.getString(R.string.catalog_language))
  private val keys = listOf(TestRecipeCatalog.KEY_V60, TestRecipeCatalog.KEY_AEROPRESS, TestRecipeCatalog.KEY_CLEVER, TestRecipeCatalog.KEY_GRINDER)

  @After
  fun tearDown() {
    env?.close()
  }

  private fun show(seedRecipes: Boolean = true, setup: (TestAppDependencies) -> Unit = {}): TestAppDependencies {
    val deps = TestAppDependencies(context, catalogFetcher = fetcher, seedRecipes = seedRecipes).also { env = it }
    deps.skipOnboarding()
    setup(deps)
    rule.setContent { TestApp(deps) }
    waitFor("tab_recipes")
    rule.onNodeWithTag("tab_recipes").performClick()
    waitFor("recipes_segment_catalog")
    return deps
  }

  private fun exists(tag: String) = rule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

  private fun waitFor(tag: String) = rule.waitUntil(5_000) { exists(tag) }

  private fun waitGone(tag: String) = rule.waitUntil(5_000) { !exists(tag) }

  private fun count(tag: String) = rule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().size

  private fun openCatalog() {
    rule.onNodeWithTag("recipes_segment_catalog").performClick()
    waitFor("catalog_list")
    waitFor("catalog_row_${keys[0]}")
  }

  private fun row(key: String) = hasTestTag("catalog_row_$key")

  /** El aviso de encima de la lista tiene [text]; en una alerta el texto vive en un hijo de la etiqueta. */
  private fun assertNotice(text: String) {
    val inNotice = hasText(text, substring = true) and (hasTestTag("catalog_notice") or hasAnyAncestor(hasTestTag("catalog_notice")))
    rule.waitUntil(5_000) { rule.onAllNodes(inNotice, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
  }

  /**
   * Las filas que hay en la lista, estén o no a la vista: con el filtro de tueste la lista cabe menos en pantalla y una lista
   * perezosa no compone lo que queda fuera, así que se desplaza hasta cada fila para saber si existe.
   */
  private fun rowKeys(): List<String> {
    if (!exists("catalog_list")) return emptyList()
    val list = rule.onNodeWithTag("catalog_list")
    return keys.filter { key ->
      try {
        list.performScrollToNode(row(key))
        true
      } catch (_: AssertionError) {
        false
      }
    }
  }

  /** La receta de prueba tal como la lista la ve en el idioma de la prueba. */
  private fun listItem(key: String) = (
    RecipeCatalogParser.parse(TestRecipeCatalog.json(), grinderCatalogByKey { TestGrinderCatalog.JSON }) as CatalogParseResult.Parsed
    ).catalog.entries.first { it.key == key }.inLanguage(language).toListItem()

  @Test
  fun theSegmentSwitchesBetweenMyRecipesAndRecommended() {
    show()
    waitFor("recipe_row")
    rule.onNodeWithTag("recipes_segment_mine", useUnmergedTree = true).assertIsSelected()
    assertFalse(exists("catalog_list"))

    openCatalog()
    rule.onNodeWithTag("recipes_segment_catalog", useUnmergedTree = true).assertIsSelected()
    assertEquals(keys, rowKeys())
    assertFalse(exists("recipe_row"))

    rule.onNodeWithTag("recipes_segment_mine").performClick()
    waitFor("recipe_row")
    waitGone("catalog_list")
  }

  @Test
  fun theSegmentIsRememberedWhenComingBackToTheTab() {
    show()
    openCatalog()
    rule.onNodeWithTag("tab_prepare").performClick()
    waitFor("prepare_start")
    rule.onNodeWithTag("tab_recipes").performClick()
    waitFor("catalog_list")
    rule.onNodeWithTag("recipes_segment_catalog", useUnmergedTree = true).assertIsSelected()
  }

  @Test
  fun aFreshInstallShowsTheEmptyListWithSeeRecommended() {
    show(seedRecipes = false)
    waitFor("recipes_empty_see_catalog")
    assertFalse(exists("recipe_row"))
    rule.onNodeWithTag("recipes_empty_see_catalog").performClick()
    waitFor("catalog_list")
    waitFor("catalog_row_${keys[0]}")
    rule.onNodeWithTag("recipes_segment_catalog", useUnmergedTree = true).assertIsSelected()
  }

  @Test
  fun theRecommendedListShowsNameAuthorMethodSummaryAndTime() {
    show()
    openCatalog()
    val item = listItem(TestRecipeCatalog.KEY_V60)
    val time = formatClock(item.totalDurationMs)
    val summary = context.getString(
      R.string.recipe_summary,
      if (item.doseG % 1.0 == 0.0) item.doseG.toLong().toString() else "%.1f".format(item.doseG),
      if (item.totalWaterG % 1.0 == 0.0) item.totalWaterG.toLong().toString() else "%.1f".format(item.totalWaterG),
      context.getString(R.string.scale_ratio_value, item.ratio),
      context.resources.getQuantityString(R.plurals.recipe_stage_count, item.stageCount, item.stageCount),
      time,
    )
    fun inRow(tag: String) = rule.onNode(hasTestTag(tag) and hasAnyAncestor(row(TestRecipeCatalog.KEY_V60)), useUnmergedTree = true)
    inRow("catalog_row_name").assertTextContains(item.name)
    inRow("catalog_row_author").assertTextContains("Némo Pop")
    inRow("catalog_row_method").assertTextContains("V60")
    inRow("catalog_row_summary").assertTextContains(summary)
    assertTrue(summary.endsWith(time))
    // Una receta sin autor no tiene línea de autor.
    val clever = hasTestTag("catalog_row_author") and hasAnyAncestor(row(TestRecipeCatalog.KEY_CLEVER))
    assertEquals(0, rule.onAllNodes(clever, useUnmergedTree = true).fetchSemanticsNodes().size)
  }

  @Test
  fun theMethodFilterShowsOnlyThatMethod() {
    show()
    openCatalog()
    rule.onNodeWithTag("catalog_method_filter").performClick()
    rule.onNode(hasText("Aeropress") and hasAnyAncestor(isPopup())).performClick()
    rule.waitUntil(5_000) { rowKeys() == listOf(TestRecipeCatalog.KEY_AEROPRESS) }

    rule.onNodeWithTag("catalog_method_filter").performClick()
    rule.onNode(hasText(context.getString(R.string.catalog_filter_all)) and hasAnyAncestor(isPopup())).performClick()
    rule.waitUntil(5_000) { rowKeys() == keys }
  }

  @Test
  fun theSearchFindsByNameAndByAuthorWithoutAccents() {
    show()
    openCatalog()
    rule.onNodeWithTag("catalog_search").performTextInput("nemo")
    rule.waitUntil(5_000) { rowKeys() == listOf(TestRecipeCatalog.KEY_V60) }

    rule.onNodeWithTag("catalog_search").performTextReplacement("TESTER UNO")
    rule.waitUntil(5_000) { rowKeys() == listOf(TestRecipeCatalog.KEY_AEROPRESS) }

    rule.onNodeWithTag("catalog_search").performTextReplacement("CLEVER")
    rule.waitUntil(5_000) { rowKeys() == listOf(TestRecipeCatalog.KEY_CLEVER) }
  }

  @Test
  fun noResultsShowsTheMessageAndClearFiltersRestoresTheList() {
    show()
    openCatalog()
    rule.onNodeWithTag("catalog_search").performTextInput("zzzz")
    waitFor("catalog_empty")
    rule.onNodeWithTag("catalog_empty", useUnmergedTree = true).assertIsDisplayed()
    assertTrue(rowKeys().isEmpty())

    rule.onNodeWithTag("catalog_clear_filters").performClick()
    rule.waitUntil(5_000) { rowKeys() == keys }
    assertFalse(exists("catalog_empty"))
  }

  @Test
  fun aSavedCopyShowsTheSavedTag() {
    show { deps -> runBlocking { deps.recipes.save(v60Classic(ResourcesRecipeTexts(context)).copy(catalogKey = TestRecipeCatalog.KEY_V60)) } }
    openCatalog()
    val savedInV60 = hasTestTag("catalog_saved_tag") and hasAnyAncestor(row(TestRecipeCatalog.KEY_V60))
    rule.waitUntil(5_000) { rule.onAllNodes(savedInV60, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    assertEquals(1, count("catalog_saved_tag"))
  }

  @Test
  fun pullToRefreshForcesADownload() {
    show()
    openCatalog()
    // Mostrar la lista descarga una vez: nunca hubo una descarga correcta.
    rule.waitUntil(5_000) { fetcher.calls == 1 }
    rule.waitForIdle()
    val before = fetcher.calls
    rule.onNodeWithTag("catalog_list").performTouchInput { swipeDown() }
    rule.waitUntil(5_000) { fetcher.calls > before }
  }

  @Test
  fun aFailedDownloadWithoutPreviousOneShowsUsingBundledRecipes() {
    show()
    openCatalog()
    rule.waitUntil(5_000) { fetcher.calls >= 1 && exists("catalog_notice") }
    assertNotice(context.getString(R.string.catalog_using_bundled))
    // Las recetas del APK siguen a la vista.
    assertEquals(keys, rowKeys())
  }

  @Test
  fun aNewerSchemaAsksToUpdateTheApp() {
    fetcher.enqueue(FetchResult.Fetched(TestRecipeCatalog.json(revision = 5, schemaVersion = 2), null))
    show()
    openCatalog()
    rule.waitUntil(5_000) { exists("catalog_notice") }
    assertNotice(context.getString(R.string.catalog_newer_schema))
    assertEquals(keys, rowKeys())
  }

  @Test
  fun showingRecommendedDownloadsOnlyWhenStale() {
    fetcher.enqueue(FetchResult.Fetched(TestRecipeCatalog.json(revision = 2), "\"e2\""))
    show()
    openCatalog()
    rule.waitUntil(5_000) { fetcher.calls == 1 && exists("catalog_notice") }
    val last = context.getString(R.string.catalog_last_update, "")
    assertNotice(last.trim())

    // Salir y volver a mostrarla antes de 24 h no descarga otra vez.
    rule.onNodeWithTag("recipes_segment_mine").performClick()
    waitFor("recipe_row")
    openCatalog()
    rule.waitForIdle()
    assertEquals(1, fetcher.calls)
  }

  private fun selectRoast(name: String) {
    rule.onNodeWithTag("catalog_roast_$name").performClick()
  }

  private fun inRow(key: String, tag: String) = hasTestTag(tag) and hasAnyAncestor(row(key))

  @Test
  fun theRoastFilterStartsAtAllAndMediumShowsMediumAndAnyRoastRecipes() {
    show()
    openCatalog()
    rule.onNodeWithTag("catalog_roast_all", useUnmergedTree = true).assertIsSelected()
    assertEquals(keys, rowKeys())

    selectRoast("medium")
    rule.waitUntil(5_000) {
      rowKeys() == listOf(TestRecipeCatalog.KEY_V60, TestRecipeCatalog.KEY_CLEVER, TestRecipeCatalog.KEY_GRINDER)
    }
    rule.onNodeWithTag("catalog_roast_medium", useUnmergedTree = true).assertIsSelected()
  }

  @Test
  fun theRoastFilterCombinesWithMethodAndSearch() {
    show()
    openCatalog()
    selectRoast("light")
    rule.waitUntil(5_000) { rowKeys() == listOf(TestRecipeCatalog.KEY_AEROPRESS, TestRecipeCatalog.KEY_CLEVER) }

    rule.onNodeWithTag("catalog_method_filter").performClick()
    rule.onNode(hasText("Aeropress") and hasAnyAncestor(isPopup())).performClick()
    rule.waitUntil(5_000) { rowKeys() == listOf(TestRecipeCatalog.KEY_AEROPRESS) }

    rule.onNodeWithTag("catalog_method_filter").performClick()
    rule.onNode(hasText(context.getString(R.string.catalog_filter_all)) and hasAnyAncestor(isPopup())).performClick()
    rule.onNodeWithTag("catalog_search").performTextInput("némo")
    waitFor("catalog_empty")
    assertTrue(rowKeys().isEmpty())
  }

  @Test
  fun clearFiltersResetsTheRoastToAll() {
    show()
    openCatalog()
    selectRoast("light")
    rule.onNodeWithTag("catalog_search").performTextInput("némo")
    waitFor("catalog_empty")
    rule.onNodeWithTag("catalog_clear_filters").performClick()
    rule.waitUntil(5_000) { rowKeys() == keys }
    rule.onNodeWithTag("catalog_roast_all", useUnmergedTree = true).assertIsSelected()
  }

  @Test
  fun rowsShowOneTagPerRoastAndNoneForAnyRoast() {
    show()
    openCatalog()
    rule.onNode(inRow(TestRecipeCatalog.KEY_V60, "catalog_roast_tag_medium"), useUnmergedTree = true).assertExists()
    rule.onNode(inRow(TestRecipeCatalog.KEY_V60, "catalog_roast_tag_dark"), useUnmergedTree = true).assertExists()
    assertEquals(0, rule.onAllNodes(inRow(TestRecipeCatalog.KEY_V60, "catalog_roast_tag_light"), useUnmergedTree = true).fetchSemanticsNodes().size)
    rule.onNode(inRow(TestRecipeCatalog.KEY_AEROPRESS, "catalog_roast_tag_light"), useUnmergedTree = true).assertExists()
    listOf("light", "medium", "dark").forEach { roast ->
      assertEquals(0, rule.onAllNodes(inRow(TestRecipeCatalog.KEY_CLEVER, "catalog_roast_tag_$roast"), useUnmergedTree = true).fetchSemanticsNodes().size)
    }
  }

  @Test
  fun theRoastFilterIsKeptWhenSwitchingSegments() {
    show()
    openCatalog()
    selectRoast("medium")
    rule.waitUntil(5_000) { !exists("catalog_row_${TestRecipeCatalog.KEY_AEROPRESS}") }
    rule.onNodeWithTag("recipes_segment_mine").performClick()
    waitFor("recipe_row")
    rule.onNodeWithTag("recipes_segment_catalog").performClick()
    waitFor("catalog_list")
    rule.onNodeWithTag("catalog_roast_medium", useUnmergedTree = true).assertIsSelected()
    assertFalse(exists("catalog_row_${TestRecipeCatalog.KEY_AEROPRESS}"))
  }
}
