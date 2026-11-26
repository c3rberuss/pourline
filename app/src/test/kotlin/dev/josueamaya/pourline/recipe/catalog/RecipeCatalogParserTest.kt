package dev.josueamaya.pourline.recipe.catalog

import dev.josueamaya.pourline.coffee.RoastLevel
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeCatalogParserTest {
  private fun parse(text: String) = RecipeCatalogParser.parse(text, testGrinders())

  /** Solo la receta [bad] pierde: la receta buena sigue y la mala se anota como descartada. */
  private fun assertOnlyDiscarded(bad: JsonObject, reasonPart: String? = null): DiscardedRecipe {
    val good = recipeJson("good")
    val result = parsed(catalogJson(good, bad))
    assertEquals(listOf("good"), result.entries.map { it.key })
    assertEquals(1, result.discarded.size)
    val discarded = result.discarded.single()
    assertEquals(1, discarded.index)
    if (reasonPart != null) assertTrue(discarded.reason, discarded.reason.contains(reasonPart))
    return discarded
  }

  @Test
  fun `a valid catalog is parsed with its revision and recipes in file order`() {
    val result = parsed(catalogJson(recipeJson("b_second"), recipeJson("a_first"), revision = 7))
    assertEquals(1, result.schemaVersion)
    assertEquals(7, result.revision)
    assertEquals(listOf("b_second", "a_first"), result.entries.map { it.key })
    assertTrue(result.discarded.isEmpty())
  }

  @Test
  fun `a newer schema version is reported before reading anything else`() {
    assertEquals(CatalogParseResult.NewerSchema(2), parse("""{"schemaVersion":2,"formatoNuevo":true}"""))
    assertEquals(CatalogParseResult.NewerSchema(9), parse(catalogJson(recipeJson(), schemaVersion = 9)))
  }

  @Test
  fun `a root without schema version, revision or recipes is corrupt`() {
    assertEquals(CatalogParseResult.Corrupt, parse("""{"revision":1,"recipes":[]}"""))
    assertEquals(CatalogParseResult.Corrupt, parse("""{"schemaVersion":1,"recipes":[]}"""))
    assertEquals(CatalogParseResult.Corrupt, parse("""{"schemaVersion":1,"revision":0,"recipes":[]}"""))
    assertEquals(CatalogParseResult.Corrupt, parse("""{"schemaVersion":"1","revision":1,"recipes":[]}"""))
    val recipe = recipeJson().toString()
    assertEquals(CatalogParseResult.Corrupt, parse("""{"schemaVersion":1,"revision":1,"recipes":$recipe}"""))
    assertEquals(CatalogParseResult.Corrupt, parse("""{"schemaVersion":1,"revision":1}"""))
    assertEquals(CatalogParseResult.Corrupt, parse("esto no es json"))
    assertEquals(CatalogParseResult.Corrupt, parse("[1,2,3]"))
    assertEquals(CatalogParseResult.Corrupt, parse(""))
  }

  @Test
  fun `an unknown method discards only that recipe`() {
    assertOnlyDiscarded(recipeJson("bad", mapOf("method" to JsonPrimitive("SIPHON"))), "SIPHON")
  }

  @Test
  fun `an unknown stage type discards only that recipe`() {
    val stages = JsonArray(listOf(stageJson(), stageJson(type = "SPIN", startS = 20, waterG = null)))
    assertOnlyDiscarded(recipeJson("bad", mapOf("stages" to stages)), "SPIN")
  }

  @Test
  fun `a missing language discards the recipe`() {
    val onlyEs = buildJsonObject { put("es", "solo español") }
    val onlyEn = buildJsonObject { put("en", "only english") }
    val blank = localized("  ", "x")
    assertOnlyDiscarded(recipeJson("bad", mapOf("name" to onlyEs)), "name")
    assertOnlyDiscarded(recipeJson("bad", mapOf("name" to blank)), "name")
    assertOnlyDiscarded(recipeJson("bad", mapOf("notes" to onlyEn)), "notes")
    assertOnlyDiscarded(
      recipeJson("bad", mapOf("method" to JsonPrimitive("OTHER"), "methodOther" to onlyEs)),
      "methodOther",
    )
    val stages = JsonArray(listOf(stageJson(instruction = onlyEn)))
    assertOnlyDiscarded(recipeJson("bad", mapOf("stages" to stages)), "instruction")
  }

  @Test
  fun `a recipe that fails the validator is discarded`() {
    val decreasing = JsonArray(listOf(stageJson(waterG = 100.0), stageJson(startS = 20, waterG = 80.0)))
    assertOnlyDiscarded(recipeJson("bad", mapOf("stages" to decreasing)), "WaterNotIncreasing")
    assertOnlyDiscarded(recipeJson("bad", mapOf("doseG" to JsonPrimitive(0.0))), "DoseInvalid")
    val longText = "x".repeat(201)
    val long = JsonArray(listOf(stageJson(instruction = localized(longText))))
    assertOnlyDiscarded(recipeJson("bad", mapOf("stages" to long)), "InstructionTooLong")
    val overlap = JsonArray(listOf(stageJson(durationS = 30), stageJson(startS = 10, waterG = 120.0)))
    assertOnlyDiscarded(recipeJson("bad", mapOf("stages" to overlap)), "StartOverlaps")
  }

  @Test
  fun `method other requires its text and other methods reject it`() {
    assertOnlyDiscarded(recipeJson("bad", mapOf("method" to JsonPrimitive("OTHER"))), "methodOther")
    assertOnlyDiscarded(recipeJson("bad", mapOf("methodOther" to localized("Clever"))), "methodOther")
    val good = recipeJson("clever", mapOf("method" to JsonPrimitive("OTHER"), "methodOther" to localized("Clever")))
    val entry = parsed(catalogJson(good)).entries.single()
    assertEquals("Clever", entry.es.methodOther)
  }

  @Test
  fun `water is required in pours and rejected elsewhere`() {
    val pourWithoutWater = JsonArray(listOf(stageJson(waterG = null)))
    assertOnlyDiscarded(recipeJson("bad", mapOf("stages" to pourWithoutWater)), "without waterG")
    val waitWithWater = JsonArray(listOf(stageJson(), stageJson(type = "WAIT", startS = 10, waterG = 60.0)))
    assertOnlyDiscarded(recipeJson("bad", mapOf("stages" to waitWithWater)), "not a POUR")
  }

  @Test
  fun `negative times discard the recipe`() {
    val negativeStart = JsonArray(listOf(stageJson(startS = -1)))
    assertOnlyDiscarded(recipeJson("bad", mapOf("stages" to negativeStart)), "negative startS")
    val negativeDuration = JsonArray(listOf(stageJson(durationS = -5)))
    assertOnlyDiscarded(recipeJson("bad", mapOf("stages" to negativeDuration)), "negative durationS")
  }

  @Test
  fun `an unknown grinder or a value out of its scale discards the recipe`() {
    fun withGrinder(catalogKey: String, value: Double) = mapOf(
      "grind" to buildJsonObject {
        put("text", localized("Media"))
        put(
          "grinder",
          buildJsonObject {
            put("catalogKey", catalogKey)
            put("value", value)
          },
        )
      },
    )
    assertOnlyDiscarded(recipeJson("bad", withGrinder("no-existe", 10.0)), "unknown grinder")
    assertOnlyDiscarded(recipeJson("bad", withGrinder("comandante-c40", 41.0)), "out of the scale")
    val entry = parsed(catalogJson(recipeJson("ok", withGrinder("comandante-c40", 27.0)))).entries.single()
    assertEquals(CatalogGrinderRef("comandante-c40", 27.0), entry.es.grinderRef)
    assertEquals("Media", entry.en.grindText)
  }

  @Test
  fun `a source url must be https and an author requires a source`() {
    assertOnlyDiscarded(recipeJson("bad", mapOf("sourceUrl" to JsonPrimitive("http://example.com/a"))), "sourceUrl")
    assertOnlyDiscarded(recipeJson("bad", mapOf("sourceUrl" to JsonPrimitive("https://"))), "sourceUrl")
    assertOnlyDiscarded(recipeJson("bad", mapOf("author" to JsonPrimitive("Alguien"))), "author")
    val ok = recipeJson("ok", mapOf("author" to JsonPrimitive("Alguien"), "sourceUrl" to JsonPrimitive("https://example.com/a")))
    val entry = parsed(catalogJson(ok)).entries.single()
    assertEquals("Alguien", entry.es.author)
    assertEquals("https://example.com/a", entry.es.sourceUrl)
  }

  @Test
  fun `an invalid key discards the recipe`() {
    assertOnlyDiscarded(recipeJson("Mala-Clave"), "key")
    assertOnlyDiscarded(recipeJson(""), "key")
  }

  @Test
  fun `duplicated keys discard every recipe with that key`() {
    val result = parsed(catalogJson(recipeJson("dup"), recipeJson("unique"), recipeJson("dup")))
    assertEquals(listOf("unique"), result.entries.map { it.key })
    assertEquals(listOf(0, 2), result.discarded.map { it.index })
    assertTrue(result.discarded.all { it.key == "dup" && it.reason.contains("duplicated") })
  }

  @Test
  fun `unknown fields are ignored at every level`() {
    val text = """
      {
        "schemaVersion": 1, "revision": 3, "futuro": {"a": 1},
        "recipes": [{
          "key": "ok", "campoNuevo": [1, 2],
          "name": {"es": "Nombre", "en": "Name", "fr": "Nom"},
          "method": "V60", "doseG": 15.0,
          "grind": {"text": {"es": "Media", "en": "Medium", "fr": "Moyenne"}, "extra": true},
          "stages": [{"type": "POUR", "startS": 0, "durationS": 10, "waterG": 60.0, "color": "rojo"}]
        }]
      }
    """.trimIndent()
    val entry = parsed(text).entries.single()
    assertEquals("Nombre", entry.es.name)
    assertEquals("Name", entry.en.name)
    assertEquals("Medium", entry.en.grindText)
  }

  @Test
  fun `a catalog without any valid recipe is corrupt`() {
    assertEquals(CatalogParseResult.Corrupt, parse(catalogJson()))
    assertEquals(CatalogParseResult.Corrupt, parse(catalogJson(recipeJson("bad", mapOf("method" to JsonPrimitive("SIPHON"))))))
  }

  @Test
  fun `each discarded recipe reports its index, key and reason`() {
    val undecodable = JsonObject(mapOf("key" to JsonPrimitive("sin_pasos"), "name" to localized("X")))
    val bad = recipeJson("bad", mapOf("method" to JsonPrimitive("SIPHON")))
    val result = parsed(catalogJson(recipeJson("ok"), undecodable, bad))
    assertEquals(listOf(1, 2), result.discarded.map { it.index })
    assertEquals(listOf("sin_pasos", "bad"), result.discarded.map { it.key })
    assertTrue(result.discarded.all { it.reason.isNotBlank() })
    assertNotNull(result.discarded.first { it.key == "sin_pasos" }.reason)
  }

  private fun roastsOf(vararg overrides: Pair<String, kotlinx.serialization.json.JsonElement>): CatalogRecipeEntry =
    parsed(catalogJson(recipeJson("v60_test", overrides.toMap()))).entries.single()

  @Test
  fun `roasts are read as roast levels`() {
    val entry = roastsOf("roasts" to roastsJson("MEDIUM", "DARK"))
    assertEquals(setOf(RoastLevel.MEDIUM, RoastLevel.DARK), entry.es.roasts)
    assertEquals(setOf(RoastLevel.MEDIUM, RoastLevel.DARK), entry.en.roasts)
  }

  @Test
  fun `missing or empty roasts mean any roast`() {
    assertTrue(roastsOf().es.roasts.isEmpty())
    assertTrue(roastsOf("roasts" to roastsJson()).es.roasts.isEmpty())
    assertTrue(roastsOf("roasts" to roastsJson()).en.roasts.isEmpty())
  }

  @Test
  fun `unknown roast values are ignored and none left means any roast`() {
    val some = parsed(catalogJson(recipeJson("v60_test", mapOf("roasts" to roastsJson("MEDIUM", "ULTRA")))))
    assertEquals(setOf(RoastLevel.MEDIUM), some.entries.single().es.roasts)
    val none = parsed(catalogJson(recipeJson("v60_test", mapOf("roasts" to roastsJson("ULTRA")))))
    assertEquals(emptySet<RoastLevel>(), none.entries.single().es.roasts)
    assertTrue(none.discarded.isEmpty())
  }

  @Test
  fun `a malformed roasts field is ignored without discarding the recipe`() {
    val malformed = listOf(
      JsonPrimitive("MEDIUM"),
      JsonArray(listOf(JsonPrimitive(1), JsonNull)),
      buildJsonObject { put("a", 1) },
      JsonNull,
    )
    malformed.forEach { value ->
      val result = parsed(catalogJson(recipeJson("v60_test", mapOf("roasts" to value))))
      assertTrue(value.toString(), result.discarded.isEmpty())
      assertEquals(value.toString(), emptySet<RoastLevel>(), result.entries.single().es.roasts)
    }
  }

  @Test
  fun `repeated roasts are read once`() {
    val entry = roastsOf("roasts" to roastsJson("DARK", "LIGHT", "DARK"))
    assertEquals(listOf(RoastLevel.LIGHT, RoastLevel.DARK), entry.es.roasts.toList())
  }
}
