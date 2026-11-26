package dev.josueamaya.pourline.recipe

/**
 * Textos que las recetas necesitan fuera de Compose. Lo implementa `ResourcesRecipeTexts`; en los tests, un falso.
 *
 * Los `builtIn*` son los textos de las dos recetas de fábrica antiguas y solo los usan las migraciones 1 → 2 y 7 → 8.
 */
interface RecipeTexts {
  /** Nombre traducido de una predefinida (`"v60_classic"`, `"four_six"`). */
  fun builtInName(key: String): String

  /** Nombres traducidos de sus pasos, en orden; `null` en los que no tienen nombre (las esperas). */
  fun builtInStageNames(key: String): List<String?>

  /** Instrucciones traducidas de sus pasos, en orden. */
  fun builtInStageInstructions(key: String): List<String?>

  /** `"<nombre> (copia)"`. */
  fun copyName(name: String): String

  /** `"<base> (receta)"`. */
  fun fromBrewName(base: String): String
}
