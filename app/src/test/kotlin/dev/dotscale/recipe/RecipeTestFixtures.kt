package dev.dotscale.recipe

import dev.dotscale.history.BrewMethod

/** Textos falsos y deterministas: no necesitan `Resources`. */
class FakeRecipeTexts(private val language: String = "es") : RecipeTexts {
  override fun builtInName(key: String): String = when (key) {
    BuiltInRecipes.V60_CLASSIC -> if (language == "en") "Classic V60" else "V60 clásica"
    else -> if (language == "en") "4:6 method" else "Método 4:6"
  }

  override fun builtInStageNames(key: String): List<String?> = when (key) {
    BuiltInRecipes.V60_CLASSIC ->
      if (language == "en") listOf("Bloom", null, "2nd pour", null, "3rd pour") else listOf("Bloom", null, "2.º vertido", null, "3.er vertido")
    else -> listOf("1", null, "2", null, "3", null, "4", null, "5")
  }

  override fun builtInStageInstructions(key: String): List<String?> = when (key) {
    BuiltInRecipes.V60_CLASSIC -> listOf("i1", "i2", "i3", "i4", "i5")
    else -> (1..9).map { "i$it" }
  }

  override fun copyName(name: String) = "$name (copia)"

  override fun fromBrewName(base: String) = "$base (receta)"
}

/** La V60 clásica de fábrica, con los nombres españoles falsos. */
fun v60Classic(): Recipe = BuiltInRecipes.recipe(BuiltInRecipes.V60_CLASSIC, FakeRecipeTexts())

fun stage(
  startS: Int,
  waterG: Double,
  durationS: Int,
  name: String? = null,
  type: StageType = StageType.POUR,
  instruction: String? = null,
) = RecipeStage(name, startS * 1_000L, waterG, durationS * 1_000L, type, instruction)

fun recipeOf(vararg stages: RecipeStage, dose: Double = 15.0, method: BrewMethod? = BrewMethod.V60) =
  Recipe(name = "Prueba", method = method, doseG = dose, stages = stages.toList())
