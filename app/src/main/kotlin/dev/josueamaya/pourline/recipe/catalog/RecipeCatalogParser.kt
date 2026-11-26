package dev.josueamaya.pourline.recipe.catalog

import dev.josueamaya.pourline.grinder.Grinder
import dev.josueamaya.pourline.history.BrewMethod
import dev.josueamaya.pourline.recipe.RecipeValidator
import dev.josueamaya.pourline.recipe.StageType
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

internal fun methodOf(name: String): BrewMethod? = BrewMethod.entries.firstOrNull { it.name == name }

internal fun stageTypeOf(name: String): StageType? = StageType.entries.firstOrNull { it.name == name }

private val IdPattern = Regex("^[a-z0-9_]{1,64}$")

/**
 * Lee y valida el archivo del catálogo (SPEC-012 §3.1). Kotlin puro: lo usan la app y el test del repo.
 *
 * El archivo entero solo falla si no se puede leer la raíz o no queda ninguna receta; una receta inválida se descarta
 * sola (§4) y se anota en [ParsedCatalog.discarded].
 */
object RecipeCatalogParser {
  /** La versión del formato que entiende esta versión de la app. */
  const val SCHEMA_VERSION = 1

  /** [grinders] es el catálogo de molinos por `catalogKey`, para validar `grind.grinder`. */
  fun parse(text: String, grinders: Map<String, Grinder>): CatalogParseResult {
    val root = try {
      CatalogJson.parseToJsonElement(text) as? JsonObject
    } catch (_: SerializationException) {
      null
    } catch (_: IllegalArgumentException) {
      null
    } ?: return CatalogParseResult.Corrupt
    val schemaVersion = root.intField("schemaVersion") ?: return CatalogParseResult.Corrupt
    if (schemaVersion > SCHEMA_VERSION) return CatalogParseResult.NewerSchema(schemaVersion)
    if (schemaVersion < 1) return CatalogParseResult.Corrupt
    val revision = root.intField("revision")?.takeIf { it >= 1 } ?: return CatalogParseResult.Corrupt
    val recipes = root["recipes"] as? JsonArray ?: return CatalogParseResult.Corrupt

    val decoded: List<Any> = recipes.map { element ->
      try {
        CatalogJson.decodeFromJsonElement(RecipeCatalogRecipeDto.serializer(), element)
      } catch (e: SerializationException) {
        e
      } catch (e: IllegalArgumentException) {
        e
      }
    }
    val idCounts = decoded.filterIsInstance<RecipeCatalogRecipeDto>().groupingBy { it.key }.eachCount()

    val entries = mutableListOf<CatalogRecipeEntry>()
    val discarded = mutableListOf<DiscardedRecipe>()
    decoded.forEachIndexed { index, item ->
      if (item !is RecipeCatalogRecipeDto) {
        val id = ((recipes[index] as? JsonObject)?.get("key") as? JsonPrimitive)?.takeIf { it.isString }?.content
        val detail = (item as Exception).message?.lineSequence()?.firstOrNull().orEmpty()
        discarded += DiscardedRecipe(index, id, "cannot be decoded: $detail")
        return@forEachIndexed
      }
      val problem = if ((idCounts[item.key] ?: 0) > 1) "duplicated key" else problemWith(item, grinders)
      if (problem != null) {
        discarded += DiscardedRecipe(index, item.key, problem)
      } else {
        entries += CatalogRecipeEntry(item.key, item.toCatalogRecipe(CatalogLanguage.ES), item.toCatalogRecipe(CatalogLanguage.EN))
      }
    }
    if (entries.isEmpty()) return CatalogParseResult.Corrupt
    return CatalogParseResult.Parsed(ParsedCatalog(schemaVersion, revision, entries, discarded))
  }

  private fun JsonObject.intField(name: String): Int? {
    val primitive = this[name] as? JsonPrimitive ?: return null
    return if (primitive.isString) null else primitive.intOrNull
  }

  /** El motivo por el que [dto] no vale, o `null` si es válida en los dos idiomas. */
  private fun problemWith(dto: RecipeCatalogRecipeDto, grinders: Map<String, Grinder>): String? {
    if (!IdPattern.matches(dto.key)) return "invalid key"
    val method = methodOf(dto.method) ?: return "unknown method ${dto.method}"
    dto.stages.firstOrNull { stageTypeOf(it.type) == null }?.let { return "unknown stage type ${it.type}" }

    missingLanguage("name", dto.name)?.let { return it }
    listOf("context" to dto.context, "notes" to dto.notes, "grind.text" to dto.grind?.text).forEach { (field, text) ->
      if (text != null) missingLanguage(field, text)?.let { return it }
    }
    if (method == BrewMethod.OTHER) {
      val other = dto.methodOther ?: return "methodOther is required with OTHER"
      missingLanguage("methodOther", other)?.let { return it }
    } else if (dto.methodOther != null) {
      return "methodOther only applies to OTHER"
    }
    dto.stages.forEachIndexed { i, stage ->
      listOf("name" to stage.name, "instruction" to stage.instruction).forEach { (field, text) ->
        if (text != null) missingLanguage("stages[$i].$field", text)?.let { return it }
      }
      val type = stageTypeOf(stage.type)
      if (type == StageType.POUR && stage.waterG == null) return "stages[$i] is a POUR without waterG"
      if (type != StageType.POUR && stage.waterG != null) return "stages[$i] has waterG but is not a POUR"
      if (stage.startS < 0) return "stages[$i] has a negative startS"
      if (stage.durationS < 0) return "stages[$i] has a negative durationS"
    }
    dto.grind?.grinder?.let { ref ->
      val grinder = grinders[ref.catalogKey] ?: return "unknown grinder ${ref.catalogKey}"
      if (!ref.value.isFinite() || ref.value < grinder.scaleMin || ref.value > grinder.scaleMax) {
        return "grinder value ${ref.value} is out of the scale of ${ref.catalogKey}"
      }
    }
    val author = dto.author?.trim().orEmpty()
    val source = dto.sourceUrl?.trim()
    if (source != null && !(source.startsWith("https://") && RecipeValidator.isValidSourceUrl(source))) return "sourceUrl must be a valid https URL"
    if (author.isNotEmpty() && source == null) return "an author requires a sourceUrl"

    for (language in CatalogLanguage.entries) {
      val errors = RecipeValidator.validate(dto.toCatalogRecipe(language).toRecipe())
      if (errors.isNotEmpty()) return "invalid in $language: $errors"
    }
    return null
  }

  private fun missingLanguage(field: String, text: RecipeCatalogLocalizedDto): String? = when {
    text.es.isNullOrBlank() -> "$field is missing the es text"
    text.en.isNullOrBlank() -> "$field is missing the en text"
    else -> null
  }
}
