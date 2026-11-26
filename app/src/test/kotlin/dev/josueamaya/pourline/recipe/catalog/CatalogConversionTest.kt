package dev.josueamaya.pourline.recipe.catalog

import dev.josueamaya.pourline.history.BrewMethod
import dev.josueamaya.pourline.recipe.StageType
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogConversionTest {
  private fun entry(vararg changes: Pair<String, kotlinx.serialization.json.JsonElement>): CatalogRecipeEntry =
    parsed(catalogJson(recipeJson("v60_test", changes.toMap()))).entries.single()

  @Test
  fun `stage times become milliseconds and stages are sorted by start`() {
    val shuffled = JsonArray(
      listOf(
        stageJson(startS = 45, durationS = 20, waterG = 150.0),
        stageJson(startS = 0, durationS = 10, waterG = 60.0),
        stageJson(type = "WAIT", startS = 10, durationS = 35, waterG = null),
      ),
    )
    val recipe = entry("stages" to shuffled).inLanguage(CatalogLanguage.ES).toRecipe()
    assertEquals(listOf(0L, 10_000L, 45_000L), recipe.stages.map { it.startMs })
    assertEquals(listOf(10_000L, 35_000L, 20_000L), recipe.stages.map { it.durationMs })
    assertEquals(listOf(StageType.POUR, StageType.WAIT, StageType.POUR), recipe.stages.map { it.type })
  }

  @Test
  fun `non pour stages take the water of the previous pour`() {
    val stages = JsonArray(
      listOf(
        stageJson(startS = 0, durationS = 10, waterG = 100.0),
        stageJson(type = "SWIRL", startS = 10, durationS = 5, waterG = null),
        stageJson(type = "WAIT", startS = 15, durationS = 60, waterG = null),
        stageJson(type = "ACTION", startS = 75, durationS = 10, waterG = null, instruction = localized("Añade 60 g de agua a 96 °C")),
      ),
    )
    val recipe = entry("stages" to stages).inLanguage(CatalogLanguage.ES).toRecipe()
    assertEquals(listOf(100.0, 100.0, 100.0, 100.0), recipe.stages.map { it.targetWaterG })
    assertEquals(100.0, recipe.totalWaterG, 0.0)
  }

  @Test
  fun `texts come in the chosen language`() {
    val stages = JsonArray(
      listOf(stageJson(name = localized("Bloom", "Bloom!"), instruction = localized("Vierte", "Pour"))),
    )
    val item = entry(
      "name" to localized("Nombre", "Name"),
      "notes" to localized("Notas", "Notes"),
      "context" to localized("Contexto", "Context"),
      "stages" to stages,
    )
    val es = item.inLanguage(CatalogLanguage.ES)
    val en = item.inLanguage(CatalogLanguage.EN)
    assertEquals("Nombre", es.name)
    assertEquals("Name", en.name)
    assertEquals("Contexto", es.context)
    assertEquals("Context", en.context)
    assertEquals("Notas", es.toRecipe().notes)
    assertEquals("Notes", en.toRecipe().notes)
    assertEquals("Vierte", es.toRecipe().stages.single().instruction)
    assertEquals("Pour", en.toRecipe().stages.single().instruction)
    assertEquals("Bloom!", en.toRecipe().stages.single().name)
    assertEquals(CatalogLanguage.EN, CatalogLanguage.of("en"))
    assertEquals(CatalogLanguage.ES, CatalogLanguage.of("es"))
    assertEquals(CatalogLanguage.ES, CatalogLanguage.of("fr"))
  }

  @Test
  fun `author, source url and catalog key are copied`() {
    val item = entry(
      "author" to JsonPrimitive("Autora"),
      "sourceUrl" to JsonPrimitive("https://example.com/receta"),
    )
    val recipe = item.inLanguage(CatalogLanguage.ES).toRecipe()
    assertEquals("Autora", recipe.author)
    assertEquals("https://example.com/receta", recipe.sourceUrl)
    assertEquals("v60_test", recipe.catalogKey)
    assertEquals(BrewMethod.V60, recipe.method)
    assertEquals(93.0, recipe.waterTempC)
    assertEquals(15.0, recipe.doseG, 0.0)
  }

  @Test
  fun `the recipe is new, not built in and not localized`() {
    val grind = buildJsonObject {
      put("text", localized("Media"))
    }
    val recipe = entry("grind" to grind).inLanguage(CatalogLanguage.EN).toRecipe()
    assertEquals(0L, recipe.id)
    assertNull(recipe.builtInKey)
    assertFalse(recipe.localizedNames)
    assertNull(recipe.grind)
  }

  @Test
  fun `totals come from the recipe`() {
    val recipe = entry().inLanguage(CatalogLanguage.ES).toRecipe()
    assertEquals(150.0, recipe.totalWaterG, 0.0)
    assertEquals(10.0, recipe.ratio, 0.0)
    assertEquals(65_000L, recipe.totalDurationMs)
    assertTrue(recipe.stages.isNotEmpty())
  }
}
