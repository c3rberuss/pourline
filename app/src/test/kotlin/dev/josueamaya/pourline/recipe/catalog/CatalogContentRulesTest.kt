package dev.josueamaya.pourline.recipe.catalog

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Las reglas de contenido del catálogo (D57 a D59) sobre catálogos sintéticos. */
class CatalogContentRulesTest {
  private fun roastProblems(vararg overrides: Pair<String, JsonElement>) =
    roastValueProblems(catalogJson(recipeJson("v60_test", overrides.toMap())))

  /** Un catálogo con una receta por cada lista de [roasts] (vacía: sin `roasts`). */
  private fun entriesWith(vararg roasts: List<String>): List<CatalogRecipeEntry> {
    val recipes = roasts.mapIndexed { index, values ->
      recipeJson("receta_$index", if (values.isEmpty()) emptyMap() else mapOf("roasts" to roastsJson(*values.toTypedArray())))
    }
    return parsed(catalogJson(*recipes.toTypedArray())).entries
  }

  private val light = listOf("LIGHT")
  private val medium = listOf("MEDIUM")
  private val any = emptyList<String>()

  /** Las dos recetas de prueba con un solo paso con [name] e [instruction] y las [notes]. */
  private fun wording(
    name: JsonElement? = null,
    instruction: JsonElement? = null,
    notes: JsonElement? = null,
    recipeName: JsonElement? = null,
    context: JsonElement? = null,
    grindText: JsonElement? = null,
  ): List<String> {
    val overrides = buildMap {
      put("stages", JsonArray(listOf(stageJson(name = name, instruction = instruction))))
      if (notes != null) put("notes", notes)
      if (recipeName != null) put("name", recipeName)
      if (context != null) put("context", context)
      if (grindText != null) put("grind", JsonObject(mapOf("text" to grindText)))
    }
    return wordingProblems(parsed(catalogJson(recipeJson("v60_test", overrides))).entries)
  }

  /** [es] con un inglés limpio: un problema sale una sola vez, en español. */
  private fun onlyEs(es: String) = localized(es, "Fine")

  @Test
  fun `roast values must be known roast names without repeats`() {
    assertEquals(emptyList<String>(), roastProblems("roasts" to roastsJson("MEDIUM")))
    assertEquals(emptyList<String>(), roastProblems())
    assertEquals(emptyList<String>(), roastProblems("roasts" to roastsJson()))
    val unknown = roastProblems("roasts" to roastsJson("MEDIUM", "ULTRA"))
    assertEquals(unknown.toString(), 1, unknown.size)
    assertTrue(unknown.toString(), unknown.single().contains("\"ULTRA\"") && unknown.single().contains("v60_test"))
    val repeated = roastProblems("roasts" to roastsJson("DARK", "DARK"))
    assertEquals(repeated.toString(), 1, repeated.size)
    assertTrue(repeated.toString(), repeated.single().contains("repeats \"DARK\""))
    assertTrue(roastProblems("roasts" to JsonPrimitive("MEDIUM")).single().contains("must be a list of roast names"))
    assertTrue(roastProblems("roasts" to JsonArray(listOf(JsonPrimitive(1)))).single().contains("must be a list of roast names"))
  }

  @Test
  fun `at least two thirds of the recipes must suit a medium roast`() {
    assertEquals(emptyList<String>(), roastShareProblems(entriesWith(medium, medium, light)))
    val few = roastShareProblems(entriesWith(medium, light, light))
    assertEquals(few.toString(), 1, few.size)
    assertTrue(few.toString(), few.single().contains("only 1 of 3 recipes suit a medium roast, at least 2 are needed"))
    assertEquals(emptyList<String>(), roastShareProblems(entriesWith(listOf("LIGHT", "MEDIUM"), listOf("MEDIUM", "DARK"), light)))
    assertEquals(emptyList<String>(), roastShareProblems(entriesWith(any, any, listOf("DARK"))))
    assertEquals(emptyList<String>(), roastShareProblems(emptyList()))
  }

  @Test
  fun `at most ten recipes can be only for a light roast`() {
    val ten = entriesWith(*Array(10) { light }, *Array(20) { medium })
    assertEquals(emptyList<String>(), roastShareProblems(ten))
    val eleven = entriesWith(*Array(11) { light }, *Array(22) { medium })
    val problems = roastShareProblems(eleven)
    assertEquals(problems.toString(), 1, problems.size)
    assertTrue(problems.toString(), problems.single().contains("11 recipes are only for a light roast, at most 10"))
    val lightAndMedium = entriesWith(*Array(7) { listOf("LIGHT", "MEDIUM") }, *Array(3) { medium })
    assertEquals(emptyList<String>(), roastShareProblems(lightAndMedium))
  }

  @Test
  fun `forbidden terms are found without case, accents or hyphens`() {
    val terms = listOf(
      "Drawdown", "draw-down", "Draw down", "Rao spin", "rao-spin", "Remolinea", "remolinear", "Remolíneas",
    )
    terms.forEach { term ->
      val es = wording(instruction = onlyEs("Haz el $term ahora"))
      assertEquals("es: $term -> $es", 1, es.size)
      val en = wording(instruction = localized("Haz esto", "Do the $term now"))
      assertEquals("en: $term -> $en", 1, en.size)
    }
  }

  @Test
  fun `bloom is forbidden only in spanish`() {
    val es = wording(name = localized("Bloom", "Preinfusion"))
    assertEquals(es.toString(), listOf("recipe v60_test: stages[0].name (es) uses the forbidden term \"bloom\""), es)
    assertEquals(emptyList<String>(), wording(name = localized("Preinfusión", "Bloom")))
    assertEquals(emptyList<String>(), wording(instruction = localized("Espera a la preinfusión", "Wait for the bloom")))
    assertEquals(1, wording(notes = localized("El bloom dura un rato", "Bloom lasts a while")).size)
  }

  @Test
  fun `clock times are found in instructions, stage names and notes`() {
    assertEquals(1, wording(instruction = onlyEs("Espera hasta 0:45")).size)
    assertEquals(1, wording(name = onlyEs("Hasta 10:00")).size)
    assertEquals(1, wording(notes = onlyEs("Termina hacia 3:30.")).size)
    val problems = wording(instruction = localized("Espera hasta 0:45", "Wait until 1:05"))
    assertEquals(problems.toString(), 2, problems.size)
    assertTrue(problems.toString(), problems.any { it.contains("(es)") && it.contains("\"0:45\"") })
    assertTrue(problems.toString(), problems.any { it.contains("(en)") && it.contains("\"1:05\"") })
  }

  @Test
  fun `ratios are not clock times`() {
    assertEquals(emptyList<String>(), wording(notes = localized("El ratio final con hielo es 1:13", "The final ratio is 1:7.3")))
    assertEquals(emptyList<String>(), wording(notes = onlyEs("Ratio 1:16,7")))
    assertEquals(emptyList<String>(), wording(notes = localized("Queda en un ratio de 1:13.", "Ends at a ratio of 1:13.")))
    assertEquals(1, wording(notes = onlyEs("Termina hacia 1:13")).size)
    assertEquals(1, wording(notes = onlyEs("Ratio 1:13 y retira a las 2:30")).size)
  }

  @Test
  fun `durations with units are allowed`() {
    assertEquals(emptyList<String>(), wording(notes = localized("Infusiona 4 min o 2 min 30 s", "Steep 4 min or 2 min 30 s")))
    assertEquals(emptyList<String>(), wording(instruction = localized("Añade 60 g (total 120 g) en círculos.", "Add 60 g (total 120 g) in circles.")))
  }

  @Test
  fun `recipe names, contexts and grind texts are not checked`() {
    val problems = wording(
      recipeName = localized("Bloom Drawdown 1:13", "Rao spin 0:45"),
      context = localized("Drawdown, Bloom y 3:30", "Rao spin 3:30"),
      grindText = localized("Remolinea 2:00", "Drawdown 2:00"),
    )
    assertEquals(emptyList<String>(), problems)
  }

  @Test
  fun `each problem names the recipe, the field and the language`() {
    val problems = wording(
      name = localized("Bloom", "Bloom"),
      instruction = localized("Espera hasta 0:45", "Let the drawdown finish"),
      notes = localized("Nada", "Termina a 3:30"),
    )
    assertEquals(
      listOf(
        "recipe v60_test: stages[0].name (es) uses the forbidden term \"bloom\"",
        "recipe v60_test: stages[0].instruction (es) has the time \"0:45\"",
        "recipe v60_test: stages[0].instruction (en) uses the forbidden term \"drawdown\"",
        "recipe v60_test: notes (en) has the time \"3:30\"",
      ),
      problems,
    )
  }
}
