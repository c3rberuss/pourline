package dev.dotscale.recipe

/** Textos que las recetas necesitan fuera de Compose. Lo implementa `ResourcesRecipeTexts`; en los tests, un falso. */
interface RecipeTexts {
  /** Nombre traducido de una predefinida (`"v60_classic"`, `"four_six"`). */
  fun builtInName(key: String): String

  /** Nombres traducidos de sus etapas, en orden. */
  fun builtInStageNames(key: String): List<String>

  /** `"<nombre> (copia)"`. */
  fun copyName(name: String): String

  /** `"<base> (receta)"`. */
  fun fromBrewName(base: String): String
}
