package dev.josueamaya.pourline.ui.scale

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.test.platform.app.InstrumentationRegistry
import dev.josueamaya.pourline.R
import dev.josueamaya.pourline.history.BrewListItem
import dev.josueamaya.pourline.history.BrewMethod
import dev.josueamaya.pourline.recipe.RecipeListItem
import dev.josueamaya.pourline.recipe.catalog.CatalogListItem
import dev.josueamaya.pourline.recipe.catalog.catalogMethods
import dev.josueamaya.pourline.recipe.catalog.catalogSearchKey
import dev.josueamaya.pourline.recipe.catalog.filterCatalog
import dev.josueamaya.pourline.settings.ReferenceKey
import dev.josueamaya.pourline.ui.recipes.catalog.CatalogListUi
import dev.josueamaya.pourline.ui.theme.PourlineTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** El selector es sin estado: se prueba con listas fijas y sin base de datos. */
class TargetPickerTest {
  @get:Rule
  val rule = createComposeRule()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext

  private val recipes = listOf(
    RecipeListItem(1, "V60 clásica", BrewMethod.V60, null, 15.0, 250.0, 3, totalDurationMs = 115_000),
    RecipeListItem(2, "Mi receta", BrewMethod.CHEMEX, null, 30.0, 500.0, 4, totalDurationMs = 210_000),
  )

  private fun brew(id: Long, name: String, waterG: Double) = BrewListItem(
    id = id,
    startedAtEpochMs = 1_700_000_000_000,
    name = name,
    coffee = null,
    totalTimeMs = 120_000,
    totalWaterG = waterG,
    finalRatio = null,
    stabilityScore = null,
    stabilityLevel = null,
    unit = dev.josueamaya.pourline.protocol.WeightUnit.GRAMS,
    simulated = false,
    preview = List(48) { 0f },
    fitScore = null,
    rating = null,
  )

  private val brews = listOf(brew(10, "Mañana", 250.0), brew(11, "Vacía", 0.0))

  private fun catalogItem(key: String, name: String, author: String?, method: BrewMethod) = CatalogListItem(
    key = key,
    name = name,
    author = author,
    method = method,
    methodOther = null,
    doseG = 15.0,
    totalWaterG = 250.0,
    ratio = 250.0 / 15.0,
    totalDurationMs = 115_000,
    stageCount = 3,
    searchKey = catalogSearchKey(name) + "\u0000" + catalogSearchKey(author.orEmpty()),
  )

  private val catalogItems = listOf(
    catalogItem("nemo_v60", "V60 de Némo", "Némo Pop", BrewMethod.V60),
    catalogItem("test_aeropress", "AeroPress de prueba", "Tester Uno", BrewMethod.AEROPRESS),
    catalogItem("test_clever", "Clever de prueba", null, BrewMethod.V60),
  )

  private val selected = mutableListOf<ReferenceKey>()
  private var cleared = 0
  private var managed = 0
  private var closed = 0
  private var catalogShown = 0

  private fun show(
    recipes: List<RecipeListItem> = this.recipes,
    brews: List<BrewListItem> = this.brews,
    active: ReferenceKey? = null,
    showBrews: Boolean = true,
    savedKeys: Set<String> = emptySet(),
    loaded: Boolean = true,
  ) = rule.setContent {
    // El selector es sin estado: la búsqueda y el filtro se guardan aquí, como hace el `CatalogBrowser`.
    var query by remember { mutableStateOf("") }
    var method by remember { mutableStateOf<BrewMethod?>(null) }
    val catalog = CatalogListUi(
      items = filterCatalog(catalogItems, query, method),
      total = catalogItems.size,
      methods = catalogMethods(catalogItems),
      query = query,
      method = method,
      savedKeys = savedKeys,
      loaded = loaded,
    )
    PourlineTheme(darkTheme = false) {
      TargetPicker(
        recipes = recipes,
        brews = brews,
        active = active,
        onSelect = { selected += it },
        onClear = { cleared++ },
        onManageRecipes = { managed++ },
        onClose = { closed++ },
        catalog = catalog,
        onCatalogQuery = { query = it },
        onCatalogMethod = { method = it },
        onCatalogClearFilters = {
          query = ""
          method = null
        },
        onCatalogShown = { catalogShown++ },
        showBrews = showBrews,
      )
    }
  }

  private fun openCatalogTab() {
    rule.onNodeWithTag("target_picker_tab_catalog").performClick()
    rule.onNodeWithTag("target_picker_catalog_list").assertIsDisplayed()
  }

  private fun tab(label: Int) = rule.onNodeWithText(context.getString(label))

  @Test
  fun theRecommendedTabIsShownInBasicWithRecipes() {
    show(showBrews = false)
    // Sin preparaciones como objetivo quedan dos opciones: Recetas y Recomendadas.
    rule.onNodeWithTag("target_picker_tabs").assertIsDisplayed()
    rule.onNodeWithTag("target_picker_tab_recipes").assertIsDisplayed()
    rule.onNodeWithTag("target_picker_tab_catalog").assertIsDisplayed()
    rule.onAllNodesWithTag("target_picker_tab_history").assertCountEquals(0)
    rule.onNodeWithText("V60 clásica").assertIsDisplayed()
    rule.onNodeWithText("Mi receta").assertIsDisplayed()
    rule.onAllNodesWithText("Mañana").assertCountEquals(0)
    rule.onNodeWithText("Mi receta").performClick()
    assertEquals(listOf<ReferenceKey>(ReferenceKey.Recipe(2)), selected)
  }

  @Test
  fun theRecommendedTabIsShownInExpertWithRecipesAndHistory() {
    show()
    tab(R.string.target_picker_recipes).assertIsDisplayed()
    rule.onNodeWithTag("target_picker_tab_catalog").assertIsDisplayed()
    tab(R.string.target_picker_history).assertIsDisplayed()
    rule.onAllNodesWithTag("recipe_row").assertCountEquals(2)
    rule.onAllNodesWithTag("history_row").assertCountEquals(0)
  }

  @Test
  fun pickingARecommendedRecipeSelectsACatalogReference() {
    show()
    assertEquals(0, catalogShown)
    openCatalogTab()
    rule.waitUntil(5_000) { catalogShown == 1 }
    rule.onAllNodesWithTag("recipe_row").assertCountEquals(0)
    rule.onNodeWithTag("catalog_row_test_aeropress").performClick()
    assertEquals(listOf<ReferenceKey>(ReferenceKey.Catalog("test_aeropress")), selected)
  }

  @Test
  fun theRecommendedTabSearchesAndFilters() {
    show(savedKeys = setOf("nemo_v60"))
    openCatalogTab()
    rule.onAllNodesWithTag("catalog_row_nemo_v60").assertCountEquals(1)
    rule.onAllNodesWithTag("catalog_row_test_aeropress").assertCountEquals(1)
    rule.onAllNodesWithTag("catalog_row_test_clever").assertCountEquals(1)
    // "Guardada" en la copia que ya existe.
    rule.onAllNodesWithTag("catalog_saved_tag", useUnmergedTree = true).assertCountEquals(1)

    // Búsqueda por autor, sin acentos.
    rule.onNodeWithTag("catalog_search").performTextInput("nemo")
    rule.onAllNodesWithTag("catalog_row_nemo_v60").assertCountEquals(1)
    rule.onAllNodesWithTag("catalog_row_test_aeropress").assertCountEquals(0)

    // Sin resultados: el mensaje y "Quitar filtros".
    rule.onNodeWithTag("catalog_search").performTextReplacement("zzz")
    rule.onNodeWithTag("target_picker_catalog_empty").assertIsDisplayed()
    rule.onNodeWithTag("target_picker_catalog_clear_filters").performClick()
    rule.onAllNodesWithTag("catalog_row_test_aeropress").assertCountEquals(1)

    // Filtro por método.
    rule.onNodeWithTag("catalog_method_filter").performClick()
    rule.onNode(hasText("Aeropress") and hasAnyAncestor(isPopup())).performClick()
    rule.onAllNodesWithTag("catalog_row_nemo_v60").assertCountEquals(0)
    rule.onAllNodesWithTag("catalog_row_test_aeropress").assertCountEquals(1)
  }

  @Test
  fun theActiveCatalogRecipeIsHighlighted() {
    show(active = ReferenceKey.Catalog("test_clever"))
    openCatalogTab()
    rule.onAllNodesWithTag("catalog_active_tag", useUnmergedTree = true).assertCountEquals(1)
    rule.onNode(hasTestTag("catalog_active_tag") and hasAnyAncestor(hasTestTag("catalog_row_test_clever")), useUnmergedTree = true).assertExists()
  }

  @Test
  fun aRecipeRowShowsItsTotalTime() {
    show()
    rule.onNode(hasText("1:55", substring = true)).assertIsDisplayed()
    rule.onNode(hasText("3:30", substring = true)).assertIsDisplayed()
    rule.onNode(hasContentDescription(context.getString(R.string.recipe_total_time_description, "3:30"), substring = true)).assertIsDisplayed()
  }

  @Test
  fun tappingARecipeSelectsIt() {
    show()
    rule.onAllNodesWithTag("recipe_row")[1].performClick()
    assertEquals(listOf<ReferenceKey>(ReferenceKey.Recipe(2)), selected)
  }

  @Test
  fun aBrewTakesTheHistoryTabAndOneTap() {
    show()
    tab(R.string.target_picker_history).performClick()
    rule.onAllNodesWithTag("recipe_row").assertCountEquals(0)
    rule.onAllNodesWithTag("history_row").assertCountEquals(2)
    rule.onAllNodesWithTag("history_row")[0].performClick()
    assertEquals(listOf<ReferenceKey>(ReferenceKey.Brew(10)), selected)
  }

  @Test
  fun aBrewWithoutWaterIsDisabled() {
    show()
    tab(R.string.target_picker_history).performClick()
    rule.onAllNodesWithTag("history_row")[1].performClick()
    assertTrue(selected.isEmpty())
  }

  @Test
  fun theActiveRecipeIsMarked() {
    show(active = ReferenceKey.Recipe(2))
    rule.onAllNodesWithTag("recipe_active_tag", useUnmergedTree = true).assertCountEquals(1)
  }

  @Test
  fun removeTargetOnlyAppearsWithAnActiveTarget() {
    show()
    rule.onAllNodesWithTag("target_picker_clear").assertCountEquals(0)
  }

  @Test
  fun removeTargetClears() {
    show(active = ReferenceKey.Recipe(1))
    rule.onNodeWithTag("target_picker_clear").performClick()
    assertEquals(1, cleared)
  }

  @Test
  fun manageRecipesIsAlwaysThere() {
    show()
    rule.onNodeWithTag("target_picker_manage").performClick()
    assertEquals(1, managed)
  }

  @Test
  fun emptyListsShowTheirMessage() {
    show(recipes = emptyList(), brews = emptyList())
    rule.onNodeWithText(context.getString(R.string.target_picker_empty_recipes)).assertIsDisplayed()
    tab(R.string.target_picker_history).performClick()
    rule.onNodeWithText(context.getString(R.string.target_picker_empty_history)).assertIsDisplayed()
  }
}
