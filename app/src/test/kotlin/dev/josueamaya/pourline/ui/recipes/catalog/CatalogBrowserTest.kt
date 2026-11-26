package dev.josueamaya.pourline.ui.recipes.catalog

import dev.josueamaya.pourline.coffee.RoastLevel
import dev.josueamaya.pourline.grinder.CatalogSource
import dev.josueamaya.pourline.history.BrewMethod
import dev.josueamaya.pourline.recipe.catalog.CatalogCache
import dev.josueamaya.pourline.recipe.catalog.CatalogFetcher
import dev.josueamaya.pourline.recipe.catalog.CatalogLanguage
import dev.josueamaya.pourline.recipe.catalog.CatalogLanguageSource
import dev.josueamaya.pourline.recipe.catalog.FetchResult
import dev.josueamaya.pourline.recipe.catalog.RecipeCatalogRepository
import dev.josueamaya.pourline.recipe.catalog.catalogJson
import dev.josueamaya.pourline.recipe.catalog.localized
import dev.josueamaya.pourline.recipe.catalog.recipeJson
import dev.josueamaya.pourline.recipe.catalog.roastsJson
import dev.josueamaya.pourline.recipe.catalog.testGrinders
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** El estado de los filtros de `CatalogBrowser`, con un catálogo real en memoria (como `RecipeCatalogRepositoryTest`). */
@OptIn(ExperimentalCoroutinesApi::class)
class CatalogBrowserTest {
  @get:Rule val folder = TemporaryFolder()

  private fun recipe(id: String, name: String, method: String, roasts: JsonElement? = null) = recipeJson(
    id,
    buildMap {
      put("name", localized(name))
      put("method", JsonPrimitive(method))
      if (roasts != null) put("roasts", roasts)
    },
  )

  private val catalog = catalogJson(
    recipe("v60_any", "V60 libre", "V60"),
    recipe("v60_light", "V60 claro", "V60", roastsJson("LIGHT")),
    recipe("aero_medium", "Aero medio", "AEROPRESS", roastsJson("MEDIUM")),
    recipe("press_dark", "Prensa oscura", "FRENCH_PRESS", roastsJson("MEDIUM", "DARK")),
  )

  private val allIds = listOf("v60_any", "v60_light", "aero_medium", "press_dark")

  private fun TestScope.browser(): CatalogBrowser {
    val repository = RecipeCatalogRepository(
      bundled = CatalogSource { catalog },
      cache = CatalogCache(File(folder.root, "catalog")),
      fetcher = object : CatalogFetcher {
        override suspend fun fetch(etag: String?): FetchResult = FetchResult.Failed
      },
      grinders = { testGrinders() },
      scope = backgroundScope,
      io = StandardTestDispatcher(testScheduler),
      now = { 0L },
    )
    return CatalogBrowser(repository, flowOf(emptySet()), CatalogLanguageSource { CatalogLanguage.ES }, backgroundScope)
  }

  /** Mantiene suscrito `ui` (es `WhileSubscribed`) y deja que cargue el catálogo. */
  private fun TestScope.shown(browser: CatalogBrowser): CatalogBrowser {
    backgroundScope.launch { browser.ui.collect { } }
    runCurrent()
    return browser
  }

  private fun CatalogBrowser.ids() = ui.value.items.map { item -> item.key }

  @Test
  fun `the roast filter starts at all and shows every recipe`() = runTest {
    val browser = shown(browser())
    assertNull(browser.ui.value.roast)
    assertFalse(browser.ui.value.filtered)
    assertEquals(allIds, browser.ids())
  }

  @Test
  fun `medium shows the medium recipes and those for any roast`() = runTest {
    val browser = shown(browser())
    browser.setRoast(RoastLevel.MEDIUM)
    runCurrent()
    assertEquals(RoastLevel.MEDIUM, browser.ui.value.roast)
    assertTrue(browser.ui.value.filtered)
    assertEquals(listOf("v60_any", "aero_medium", "press_dark"), browser.ids())
    assertEquals(4, browser.ui.value.total)
  }

  @Test
  fun `roast, method and search combine`() = runTest {
    val browser = shown(browser())
    browser.setRoast(RoastLevel.MEDIUM)
    browser.setMethod(BrewMethod.V60)
    runCurrent()
    assertEquals(listOf("v60_any"), browser.ids())
    browser.setRoast(RoastLevel.LIGHT)
    runCurrent()
    assertEquals(listOf("v60_any", "v60_light"), browser.ids())
    browser.setQuery("claro")
    runCurrent()
    assertEquals(listOf("v60_light"), browser.ids())
    browser.setMethod(null)
    browser.setRoast(RoastLevel.DARK)
    runCurrent()
    assertEquals(emptyList<String>(), browser.ids())
  }

  @Test
  fun `clear filters resets the roast to all with the method and the search`() = runTest {
    val browser = shown(browser())
    browser.setRoast(RoastLevel.DARK)
    browser.setMethod(BrewMethod.AEROPRESS)
    browser.setQuery("zzz")
    runCurrent()
    assertEquals(emptyList<String>(), browser.ids())
    browser.clearFilters()
    runCurrent()
    val ui = browser.ui.value
    assertNull(ui.roast)
    assertNull(ui.method)
    assertEquals("", ui.query)
    assertFalse(ui.filtered)
    assertEquals(allIds, browser.ids())
  }
}
