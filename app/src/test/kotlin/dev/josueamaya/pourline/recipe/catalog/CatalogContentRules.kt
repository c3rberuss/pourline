package dev.josueamaya.pourline.recipe.catalog

import dev.josueamaya.pourline.coffee.RoastLevel
import dev.josueamaya.pourline.coffee.fold
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Las reglas de contenido del catálogo del repo (SPEC-012 §3.9 y §3.10, Enmienda 1): valores de `roasts`, proporción por
 * tueste y redacción. Kotlin puro: las usa [catalogProblems] y las prueban catálogos sintéticos.
 */

/** Como mucho, las recetas solo para tueste claro: las de campeonato más icónicas (§3.10). */
internal const val MAX_LIGHT_ONLY = 10

/**
 * `roasts` solo con nombres de [RoastLevel] y sin repetir. Se mira en el JSON crudo porque la app ignora al leer los
 * valores que no valen (D50): un error aquí no puede quedar escondido.
 */
fun roastValueProblems(text: String): List<String> {
  val root = try {
    CatalogJson.parseToJsonElement(text) as? JsonObject
  } catch (_: SerializationException) {
    null
  } catch (_: IllegalArgumentException) {
    null
  } ?: return emptyList()
  val recipes = root["recipes"] as? JsonArray ?: return emptyList()
  val names = RoastLevel.entries.map { it.name }
  return buildList {
    recipes.forEachIndexed { index, element ->
      val recipe = element as? JsonObject ?: return@forEachIndexed
      val roasts = recipe["roasts"] ?: return@forEachIndexed
      val id = (recipe["key"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: "no key"
      val where = "recipe #$index ($id)"
      val values = roasts as? JsonArray
      val texts = values?.map { value -> (value as? JsonPrimitive)?.takeIf { it.isString }?.content }
      if (texts == null || texts.any { it == null }) {
        add("$where: roasts must be a list of roast names")
        return@forEachIndexed
      }
      texts.filterNotNull().filter { it !in names }.distinct().forEach { add("$where: roasts has the unknown value \"$it\"") }
      texts.filterNotNull().filter { it in names }.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        .forEach { add("$where: roasts repeats \"$it\"") }
    }
  }
}

/**
 * La proporción de §3.10: al menos 2/3 de las recetas aptas para tueste medio (`MEDIUM` o sin `roasts`) y como máximo
 * [MAX_LIGHT_ONLY] solo para claro.
 */
fun roastShareProblems(entries: List<CatalogRecipeEntry>): List<String> {
  val total = entries.size
  if (total == 0) return emptyList()
  val suited = entries.count { it.es.roasts.isEmpty() || RoastLevel.MEDIUM in it.es.roasts }
  val lightOnly = entries.count { it.es.roasts == setOf(RoastLevel.LIGHT) }
  return buildList {
    val needed = (2 * total + 2) / 3
    if (suited * 3 < total * 2) add("only $suited of $total recipes suit a medium roast, at least $needed are needed")
    if (lightOnly > MAX_LIGHT_ONLY) add("$lightOnly recipes are only for a light roast, at most $MAX_LIGHT_ONLY")
  }
}

/** Un término prohibido (§3.10): su nombre en los mensajes, qué busca y en qué idiomas. */
private class ForbiddenTerm(val name: String, val pattern: Regex, val languages: Set<CatalogLanguage>)

private val BothLanguages = setOf(CatalogLanguage.ES, CatalogLanguage.EN)

private val ForbiddenTerms = listOf(
  ForbiddenTerm("drawdown", Regex("""\bdraw[\s-]*downs?\b"""), BothLanguages),
  ForbiddenTerm("rao spin", Regex("""\brao[\s-]*spin\b"""), BothLanguages),
  ForbiddenTerm("remolinea", Regex("""\bremoline\w*"""), BothLanguages),
  ForbiddenTerm("bloom", Regex("""\bbloom\w*"""), setOf(CatalogLanguage.ES)),
)

/** Un ratio entero va tras la palabra "ratio" ("ratio final con hielo 1:13") y no es un tiempo (D59). */
private val RatioMention = Regex("""(?i)\bratio\b[^.;\d]{0,40}?\b1:\d+(?:[.,]\d+)?""")

/** Un tiempo `m:ss` suelto: "0:45", "3:30". Los ratios con decimales ("1:16,7") nunca lo son. */
private val ClockTime = Regex("""(?<![\d:.,])\d{1,2}:\d{2}(?![\d:]|[.,]\d)""")

/**
 * Redacción de §3.10: ni los términos prohibidos ni tiempos `m:ss` en los nombres de paso, las instrucciones y las
 * notas, en cada idioma. Los nombres, contextos y textos de molienda de la receta no se miran.
 */
fun wordingProblems(entries: List<CatalogRecipeEntry>): List<String> = buildList {
  for (entry in entries) {
    for (language in CatalogLanguage.entries) {
      val recipe = entry.inLanguage(language)
      val tag = language.name.lowercase()
      val fields = buildList {
        recipe.stages.forEachIndexed { index, stage ->
          stage.name?.let { add("stages[$index].name" to it) }
          stage.instruction?.let { add("stages[$index].instruction" to it) }
        }
        recipe.notes?.let { add("notes" to it) }
      }
      for ((field, value) in fields) {
        val folded = fold(value)
        ForbiddenTerms.filter { language in it.languages && it.pattern.containsMatchIn(folded) }
          .forEach { add("recipe ${entry.key}: $field ($tag) uses the forbidden term \"${it.name}\"") }
        ClockTime.findAll(RatioMention.replace(value, "")).map { it.value }.distinct()
          .forEach { add("recipe ${entry.key}: $field ($tag) has the time \"$it\"") }
      }
    }
  }
}
