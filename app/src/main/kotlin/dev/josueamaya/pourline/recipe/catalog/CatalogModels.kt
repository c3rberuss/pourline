package dev.josueamaya.pourline.recipe.catalog

import dev.josueamaya.pourline.coffee.RoastLevel
import dev.josueamaya.pourline.history.BrewMethod
import dev.josueamaya.pourline.recipe.RecipeStage

/** El idioma de los textos del catálogo (SPEC-012 §3.2). */
enum class CatalogLanguage {
  ES,
  EN,
  ;

  companion object {
    /** `"en"` es inglés; cualquier otro código, español (la carpeta de recursos por defecto). */
    fun of(code: String): CatalogLanguage = if (code.trim().lowercase() == "en") EN else ES
  }
}

/** Un texto en los dos idiomas, los dos recortados y no vacíos. */
data class LocalizedText(val es: String, val en: String) {
  fun inLanguage(language: CatalogLanguage): String = if (language == CatalogLanguage.EN) en else es
}

/** El molino del catálogo de molinos con el que se desarrolló la receta, y el ajuste en sus unidades base. */
data class CatalogGrinderRef(val catalogKey: String, val value: Double)

/** Una receta del catálogo ya en un idioma (SPEC-012 §3.4). Los pasos van en milisegundos, ordenados y con el agua derivada. */
data class CatalogRecipe(
  val key: String,
  val name: String,
  val author: String?,
  val context: String?,
  val method: BrewMethod,
  val methodOther: String?,
  val doseG: Double,
  val waterTempC: Double?,
  val grindText: String?,
  val grinderRef: CatalogGrinderRef?,
  val notes: String?,
  val sourceUrl: String?,
  val stages: List<RecipeStage>,
  /** Los tuestes para los que sirve la receta, igual en los dos idiomas. Vacío: sirve para cualquier tueste. */
  val roasts: Set<RoastLevel> = emptySet(),
)

/** Una receta válida en los dos idiomas; el idioma se elige al usarla (D5, D16). */
data class CatalogRecipeEntry(val key: String, val es: CatalogRecipe, val en: CatalogRecipe) {
  fun inLanguage(language: CatalogLanguage): CatalogRecipe = if (language == CatalogLanguage.EN) en else es
}

/** Por qué se descartó la receta número [index] (desde 0) del archivo. [key] es `null` si ni siquiera se pudo leer. */
data class DiscardedRecipe(val index: Int, val key: String?, val reason: String)

data class ParsedCatalog(
  val schemaVersion: Int,
  val revision: Int,
  /** Las recetas válidas, en el orden del archivo. */
  val entries: List<CatalogRecipeEntry>,
  val discarded: List<DiscardedRecipe>,
)

sealed interface CatalogParseResult {
  data class Parsed(val catalog: ParsedCatalog) : CatalogParseResult

  /** El archivo es de un formato más nuevo que el que esta versión de la app entiende. */
  data class NewerSchema(val schemaVersion: Int) : CatalogParseResult

  /** No es JSON, le faltan campos obligatorios de la raíz o no queda ninguna receta válida. */
  data object Corrupt : CatalogParseResult
}
