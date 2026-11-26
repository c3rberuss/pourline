package dev.josueamaya.pourline.recipe.catalog

import dev.josueamaya.pourline.grinder.GrinderCatalogFile

/** Las recetas a las que apuntan las copias migradas de las recetas de fábrica (SPEC-012 §3.5): no pueden faltar. */
private val RequiredKeys = listOf("four_six", "v60_classic")

/**
 * Comprueba el catálogo del repo como lo haría la app y lista cada problema; vacía si todo está bien. No fija la lista
 * de claves: se puede añadir una receta sin tocar código (US-8).
 *
 * Reglas: lo que valida la app (esquema, claves únicas, pasos, molino), las recetas de las copias migradas, y las de
 * contenido de la Enmienda 1 (`CatalogContentRules.kt`): valores de `roasts` ([roastValueProblems]), proporción por
 * tueste ([roastShareProblems]) y redacción ([wordingProblems]).
 */
fun catalogProblems(text: String, grinderCatalog: String): List<String> {
  val grinders = GrinderCatalogFile.parse(grinderCatalog).getOrElse { return listOf("grinder catalog is invalid: ${it.message}") }
    .grinders.mapNotNull { grinder -> grinder.catalogKey?.let { it to grinder } }.toMap()
  return when (val result = RecipeCatalogParser.parse(text, grinders)) {
    CatalogParseResult.Corrupt -> listOf("the catalog is corrupt: it is not valid JSON, lacks schemaVersion, revision or recipes, or has no valid recipe")
    is CatalogParseResult.NewerSchema -> listOf("schemaVersion ${result.schemaVersion} is newer than the supported ${RecipeCatalogParser.SCHEMA_VERSION}")
    is CatalogParseResult.Parsed -> buildList {
      val catalog = result.catalog
      if (catalog.schemaVersion != RecipeCatalogParser.SCHEMA_VERSION) add("schemaVersion must be ${RecipeCatalogParser.SCHEMA_VERSION}")
      catalog.discarded.forEach { add("recipe #${it.index} (${it.key ?: "no key"}) is invalid: ${it.reason}") }
      RequiredKeys.filter { required -> catalog.entries.none { it.key == required } }.forEach { add("the recipe $it is missing") }
      addAll(roastValueProblems(text))
      addAll(roastShareProblems(catalog.entries))
      addAll(wordingProblems(catalog.entries))
    }
  }
}
