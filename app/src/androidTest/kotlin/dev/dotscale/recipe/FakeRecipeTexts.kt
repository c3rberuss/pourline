package dev.dotscale.recipe

/** Textos falsos y deterministas para los tests instrumentados; el idioma se puede cambiar en caliente. */
class FakeRecipeTexts(@Volatile var language: String = "es") : RecipeTexts {
  override fun builtInName(key: String): String = when (key) {
    BuiltInRecipes.V60_CLASSIC -> if (language == "en") "Classic V60" else "V60 clásica"
    else -> if (language == "en") "4:6 method" else "Método 4:6"
  }

  override fun builtInStageNames(key: String): List<String> = when (key) {
    BuiltInRecipes.V60_CLASSIC -> if (language == "en") listOf("Bloom", "2nd pour", "3rd pour") else listOf("Bloom", "2.º vertido", "3.er vertido")
    else -> (1..5).map { if (language == "en") "Pour $it" else "Vertido $it" }
  }

  override fun copyName(name: String) = if (language == "en") "$name (copy)" else "$name (copia)"

  override fun fromBrewName(base: String) = if (language == "en") "$base (recipe)" else "$base (receta)"
}
