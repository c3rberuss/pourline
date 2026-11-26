package dev.josueamaya.pourline.recipe

import dev.josueamaya.pourline.history.LegacyBuiltIns

/** Textos falsos y deterministas para los tests instrumentados; el idioma se puede cambiar en caliente. */
class FakeRecipeTexts(@Volatile var language: String = "es") : RecipeTexts {
  override fun builtInName(key: String): String = when (key) {
    LegacyBuiltIns.V60_CLASSIC -> if (language == "en") "Classic V60" else "V60 clásica"
    else -> if (language == "en") "4:6 method" else "Método 4:6"
  }

  override fun builtInStageNames(key: String): List<String?> = when (key) {
    LegacyBuiltIns.V60_CLASSIC ->
      if (language == "en") listOf("Bloom", null, "2nd pour", null, "3rd pour") else listOf("Bloom", null, "2.º vertido", null, "3.er vertido")
    else -> (1..9).map { if (it % 2 == 0) null else if (language == "en") "Pour ${(it + 1) / 2}" else "Vertido ${(it + 1) / 2}" }
  }

  override fun builtInStageInstructions(key: String): List<String?> = when (key) {
    LegacyBuiltIns.V60_CLASSIC -> (1..5).map { if (language == "en") "en-i$it" else "i$it" }
    else -> (1..9).map { if (language == "en") "en-i$it" else "i$it" }
  }

  override fun copyName(name: String) = if (language == "en") "$name (copy)" else "$name (copia)"

  override fun fromBrewName(base: String) = if (language == "en") "$base (recipe)" else "$base (receta)"
}
