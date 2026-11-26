package dev.josueamaya.pourline.recipe.catalog

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Valida el catálogo real del repo (`catalog/recipes.json`, en la raíz): es lo que ejecuta el workflow de GitHub y lo que
 * impide publicar una receta rota (SPEC-012 §3.9). El directorio de trabajo de los tests de `:app` es `app/`.
 */
class RecipeCatalogFileTest {
  private val catalogFile = File("../catalog/recipes.json")
  private val grinderFile = File("src/main/assets/grinders/catalog.json")

  private fun catalog(): String {
    assertTrue("falta ${catalogFile.path}", catalogFile.exists())
    return catalogFile.readText()
  }

  private fun grinders(): String {
    assertTrue("falta ${grinderFile.path}", grinderFile.exists())
    return grinderFile.readText()
  }

  @Test
  fun `the repository catalog is valid in both languages`() {
    val problems = catalogProblems(catalog(), grinders())
    assertEquals("problemas del catálogo:\n${problems.joinToString("\n")}", emptyList<String>(), problems)
  }

  @Test
  fun `an invalid recipe makes the catalog check fail`() {
    val original = catalog()
    val root = CatalogJson.parseToJsonElement(original) as JsonObject
    val recipes = (root["recipes"] as JsonArray).toList()
    val first = recipes.first() as JsonObject

    val decreasing = recipeJson(
      "agua_decreciente",
      mapOf("stages" to JsonArray(listOf(stageJson(waterG = 100.0), stageJson(startS = 20, waterG = 80.0)))),
    )
    val siphon = recipeJson("metodo_desconocido", mapOf("method" to JsonPrimitive("SIPHON")))
    val broken = JsonObject(
      root + ("recipes" to JsonArray(recipes + decreasing + siphon + first)),
    ).toString()

    val problems = catalogProblems(broken, grinders())
    assertTrue(problems.toString(), problems.any { it.contains("agua_decreciente") && it.contains("WaterNotIncreasing") })
    assertTrue(problems.toString(), problems.any { it.contains("metodo_desconocido") && it.contains("SIPHON") })
    assertTrue(problems.toString(), problems.any { it.contains((first["key"] as JsonPrimitive).content) && it.contains("duplicated") })
  }

  @Test
  fun `the check also fails without the recipes the migrated copies point to`() {
    val problems = catalogProblems(catalogJson(recipeJson("otra")), grinders())
    assertTrue(problems.toString(), problems.any { it.contains("four_six") })
    assertTrue(problems.toString(), problems.any { it.contains("v60_classic") })
  }

  /** El catálogo del repo con [extra] recetas añadidas al final. */
  private fun withRecipes(vararg extra: JsonObject): String {
    val root = CatalogJson.parseToJsonElement(catalog()) as JsonObject
    val recipes = (root["recipes"] as JsonArray).toList()
    return JsonObject(root + ("recipes" to JsonArray(recipes + extra))).toString()
  }

  @Test
  fun `an unknown or repeated roast makes the catalog check fail`() {
    val unknown = recipeJson("tueste_desconocido", mapOf("roasts" to roastsJson("MEDIUM", "EXTRA_LIGHT")))
    val repeated = recipeJson("tueste_repetido", mapOf("roasts" to roastsJson("DARK", "DARK")))
    val problems = catalogProblems(withRecipes(unknown, repeated), grinders())
    assertTrue(problems.toString(), problems.any { it.contains("tueste_desconocido") && it.contains("EXTRA_LIGHT") })
    assertTrue(problems.toString(), problems.any { it.contains("tueste_repetido") && it.contains("repeats") })
  }

  @Test
  fun `too few medium roast recipes or too many light only ones make the check fail`() {
    val lightOnly = (1..7).map { recipeJson("solo_claro_$it", mapOf("roasts" to roastsJson("LIGHT"))) }
    val problems = catalogProblems(withRecipes(*lightOnly.toTypedArray()), grinders())
    assertTrue(problems.toString(), problems.any { it.contains("are only for a light roast, at most 6") })

    val few = catalogJson(
      recipeJson("four_six"),
      recipeJson("v60_classic", mapOf("roasts" to roastsJson("LIGHT"))),
      recipeJson("otra_clara", mapOf("roasts" to roastsJson("LIGHT"))),
    )
    val fewProblems = catalogProblems(few, grinders())
    assertTrue(fewProblems.toString(), fewProblems.any { it.contains("recipes suit a medium roast") })
  }
}
