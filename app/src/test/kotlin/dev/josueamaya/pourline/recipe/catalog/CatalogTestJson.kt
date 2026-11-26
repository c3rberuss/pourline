package dev.josueamaya.pourline.recipe.catalog

import dev.josueamaya.pourline.grinder.Grinder
import dev.josueamaya.pourline.grinder.GrindUnit
import dev.josueamaya.pourline.grinder.GrindUnitLevel
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Constructores de JSON de prueba para el parser del catálogo. */

fun localized(es: String, en: String = es): JsonObject = buildJsonObject {
  put("es", es)
  put("en", en)
}

fun stageJson(
  type: String = "POUR",
  startS: Int = 0,
  durationS: Int = 10,
  waterG: Double? = 60.0,
  name: JsonElement? = null,
  instruction: JsonElement? = null,
): JsonObject = buildJsonObject {
  put("type", type)
  put("startS", startS)
  put("durationS", durationS)
  if (waterG != null) put("waterG", waterG)
  if (name != null) put("name", name)
  if (instruction != null) put("instruction", instruction)
}

/** Una receta válida de dos vertidos (60 g y 150 g) y una espera, con [overrides] que sustituyen o añaden campos. */
fun recipeJson(id: String = "v60_test", overrides: Map<String, JsonElement> = emptyMap()): JsonObject {
  val base = mapOf<String, JsonElement>(
    "key" to JsonPrimitive(id),
    "name" to localized("V60 de prueba", "Test V60"),
    "method" to JsonPrimitive("V60"),
    "doseG" to JsonPrimitive(15.0),
    "waterTempC" to JsonPrimitive(93.0),
    "stages" to JsonArray(
      listOf(
        stageJson(startS = 0, durationS = 10, waterG = 60.0, name = localized("Bloom", "Bloom")),
        stageJson(type = "WAIT", startS = 10, durationS = 35, waterG = null),
        stageJson(startS = 45, durationS = 20, waterG = 150.0, instruction = localized("Vierte en espiral", "Pour in a spiral")),
      ),
    ),
  )
  return JsonObject(base + overrides)
}

fun JsonObject.without(vararg names: String): JsonObject = JsonObject(filterKeys { it !in names })

fun JsonObject.with(vararg changes: Pair<String, JsonElement>): JsonObject = JsonObject(this + changes.toMap())

fun catalogJson(vararg recipes: JsonObject, schemaVersion: Int = 1, revision: Int = 1): String =
  buildJsonObject {
    put("schemaVersion", schemaVersion)
    put("revision", revision)
    put("recipes", JsonArray(recipes.toList()))
  }.toString()

/** Un molino `comandante-c40` de 0 a 40 clics, como el del catálogo de molinos. */
fun testGrinders(): Map<String, Grinder> = mapOf(
  "comandante-c40" to Grinder(
    catalogKey = "comandante-c40",
    brand = "Comandante",
    model = "C40 MK4",
    units = listOf(GrindUnitLevel(GrindUnit.CLICKS)),
    scaleMin = 0.0,
    scaleMax = 40.0,
  ),
)

fun parsed(text: String, grinders: Map<String, Grinder> = testGrinders()): ParsedCatalog =
  (RecipeCatalogParser.parse(text, grinders) as CatalogParseResult.Parsed).catalog
