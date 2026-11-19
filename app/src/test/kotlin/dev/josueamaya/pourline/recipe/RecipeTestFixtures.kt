package dev.josueamaya.pourline.recipe

import dev.josueamaya.pourline.history.BrewMethod

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

/**
 * "AeroPress invertida" (SPEC-010): 15 g, 85 °C y seis pasos con tres Acciones: un vertido de 0 a 10 s hasta 200 g, un
 * remover, "Pon la tapa" (20-25 s), una espera de 60 s, "Invierte" (85-90 s) y una Acción de 30 s sin nombre ni
 * instrucción. Dura 2:00, con 200 g y ratio 1:13,3.
 */
fun aeroPressWithActions(): Recipe = Recipe(
  name = "AeroPress invertida",
  method = BrewMethod.AEROPRESS,
  waterTempC = 85.0,
  doseG = 15.0,
  stages = listOf(
    stage(0, 200.0, 10),
    stage(10, 0.0, 10, type = StageType.SWIRL),
    stage(20, 0.0, 5, type = StageType.ACTION, instruction = "Pon la tapa"),
    stage(25, 0.0, 60, type = StageType.WAIT),
    stage(85, 0.0, 5, type = StageType.ACTION, instruction = "Invierte"),
    stage(90, 0.0, 30, type = StageType.ACTION),
  ).withDerivedWater(),
)
