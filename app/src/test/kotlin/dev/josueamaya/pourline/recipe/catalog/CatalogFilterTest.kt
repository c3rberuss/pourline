package dev.josueamaya.pourline.recipe.catalog

import dev.josueamaya.pourline.coffee.RoastLevel
import dev.josueamaya.pourline.history.BrewMethod
import org.junit.Assert.assertEquals
import org.junit.Test

class CatalogFilterTest {
  private fun item(name: String, author: String?, method: BrewMethod = BrewMethod.V60, key: String = name.lowercase().replace(' ', '_')) =
    catalogRecipe(key = key, name = name, author = author).copy(method = method).toListItem()

  private val nemo = item("V60 de Némo", "Némo Pop")
  private val hoffmann = item("Ultimate V60", "James Hoffmann", key = "hoffmann")
  private val boen = item("Sweet Bloom", "Ole Kristian Bøen", BrewMethod.AEROPRESS, key = "boen")
  private val clever = item("Clever", null, BrewMethod.OTHER, key = "clever")
  private val items = listOf(nemo, hoffmann, boen, clever)

  private fun keys(result: List<CatalogListItem>) = result.map { it.key }

  @Test
  fun `the search matches the name or the author without case or accents`() {
    assertEquals(listOf(nemo.key), keys(filterCatalog(items, "nemo", null)))
    assertEquals(listOf(nemo.key), keys(filterCatalog(items, "NÉMO POP", null)))
    assertEquals(listOf("hoffmann"), keys(filterCatalog(items, "HOFFMANN", null)))
    assertEquals(listOf("hoffmann"), keys(filterCatalog(items, "ultimate", null)))
    assertEquals(items.map { it.key }, keys(filterCatalog(items, "", null)))
    assertEquals(items.map { it.key }, keys(filterCatalog(items, "   ", null)))
  }

  @Test
  fun `the name and the author are not searched as one text`() {
    // "némo" termina el nombre y "pop" empieza el autor, pero no se encuentran juntos.
    assertEquals(emptyList<String>(), keys(filterCatalog(items, "Némo Pop V60", null)))
  }

  @Test
  fun `special letters are folded`() {
    assertEquals(listOf("boen"), keys(filterCatalog(items, "boen", null)))
    assertEquals(listOf("boen"), keys(filterCatalog(items, "Bøen", null)))
    assertEquals("strasse", catalogSearchKey("Straße"))
    assertEquals("aeoe", catalogSearchKey("Æœ"))
    assertEquals("ld", catalogSearchKey("Łđ"))
  }

  @Test
  fun `the method filter keeps only that method`() {
    assertEquals(listOf("boen"), keys(filterCatalog(items, "", BrewMethod.AEROPRESS)))
    assertEquals(listOf("clever"), keys(filterCatalog(items, "", BrewMethod.OTHER)))
    assertEquals(emptyList<String>(), keys(filterCatalog(items, "", BrewMethod.MOKA)))
  }

  @Test
  fun `search and method filter combine`() {
    assertEquals(listOf(nemo.key, "hoffmann"), keys(filterCatalog(items, "", BrewMethod.V60)))
    assertEquals(listOf("hoffmann"), keys(filterCatalog(items, "james", BrewMethod.V60)))
    assertEquals(emptyList<String>(), keys(filterCatalog(items, "james", BrewMethod.AEROPRESS)))
  }

  @Test
  fun `the methods are those present in brew method order`() {
    val expected = BrewMethod.entries.filter { it == BrewMethod.V60 || it == BrewMethod.AEROPRESS || it == BrewMethod.OTHER }
    assertEquals(expected, catalogMethods(items))
    assertEquals(listOf(BrewMethod.V60), catalogMethods(listOf(nemo, hoffmann)))
    assertEquals(emptyList<BrewMethod>(), catalogMethods(emptyList()))
  }

  @Test
  fun `the file order is kept`() {
    assertEquals(listOf("clever", "boen", nemo.key), keys(filterCatalog(listOf(clever, boen, nemo), "", null)))
    assertEquals(listOf("hoffmann", nemo.key), keys(filterCatalog(listOf(hoffmann, nemo), "v60", null)))
  }

  @Test
  fun `a list item carries the summary of the recipe`() {
    val result = catalogRecipe().toListItem()
    assertEquals(15.0, result.doseG, 0.0)
    assertEquals(250.0, result.totalWaterG, 0.0)
    assertEquals(250.0 / 15.0, result.ratio, 1e-9)
    assertEquals(75_000L, result.totalDurationMs)
    assertEquals(2, result.stageCount)
  }

  private fun roastItem(key: String, vararg roasts: RoastLevel, method: BrewMethod = BrewMethod.V60, name: String = key) =
    catalogRecipe(key = key, name = name, author = null, roasts = roasts.toSet()).copy(method = method).toListItem()

  private val light = roastItem("light", RoastLevel.LIGHT)
  private val medium = roastItem("medium", RoastLevel.MEDIUM, method = BrewMethod.AEROPRESS)
  private val dark = roastItem("dark", RoastLevel.DARK)
  private val lightMedium = roastItem("light_medium", RoastLevel.LIGHT, RoastLevel.MEDIUM)
  private val any = roastItem("any")
  private val byRoast = listOf(light, medium, dark, lightMedium, any)

  @Test
  fun `a list item carries its roasts`() {
    assertEquals(setOf(RoastLevel.LIGHT, RoastLevel.MEDIUM), lightMedium.roasts)
    assertEquals(emptySet<RoastLevel>(), any.roasts)
  }

  @Test
  fun `medium shows medium recipes and those for any roast`() {
    assertEquals(listOf("medium", "light_medium", "any"), keys(filterCatalog(byRoast, "", null, RoastLevel.MEDIUM)))
  }

  @Test
  fun `light and dark also include the recipes for any roast`() {
    assertEquals(listOf("light", "light_medium", "any"), keys(filterCatalog(byRoast, "", null, RoastLevel.LIGHT)))
    assertEquals(listOf("dark", "any"), keys(filterCatalog(byRoast, "", null, RoastLevel.DARK)))
    assertEquals(byRoast.map { it.key }, keys(filterCatalog(byRoast, "", null, null)))
  }

  @Test
  fun `the roast filter combines with method and search`() {
    assertEquals(listOf("medium"), keys(filterCatalog(byRoast, "", BrewMethod.AEROPRESS, RoastLevel.MEDIUM)))
    assertEquals(emptyList<String>(), keys(filterCatalog(byRoast, "", BrewMethod.AEROPRESS, RoastLevel.DARK)))
    assertEquals(listOf("light_medium"), keys(filterCatalog(byRoast, "light_m", null, RoastLevel.MEDIUM)))
    assertEquals(emptyList<String>(), keys(filterCatalog(byRoast, "dark", null, RoastLevel.LIGHT)))
  }

  @Test
  fun `cleared filters are all at every filter`() {
    val cleared = CatalogFilters()
    assertEquals("", cleared.query)
    assertEquals(null, cleared.method)
    assertEquals(null, cleared.roast)
    assertEquals(false, cleared.active)
    assertEquals(true, cleared.copy(query = "v60").active)
    assertEquals(true, cleared.copy(method = BrewMethod.V60).active)
    assertEquals(true, cleared.copy(roast = RoastLevel.DARK).active)
    assertEquals(false, cleared.copy(query = "  ").active)
  }
}
