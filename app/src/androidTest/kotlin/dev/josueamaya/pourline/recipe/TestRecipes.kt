package dev.josueamaya.pourline.recipe

import dev.josueamaya.pourline.history.BrewMethod
import dev.josueamaya.pourline.history.LegacyBuiltIns

/**
 * Recetas con pasos Acción para los tests instrumentados (SPEC-010). Los tests instrumentados no ven las fixtures de la
 * JVM (`app/src/test`), así que esta es la misma "AeroPress invertida" de `RecipeTestFixtures.aeroPressWithActions()`.
 */
fun aeroPressWithActions(): Recipe = Recipe(
  name = "AeroPress invertida",
  method = BrewMethod.AEROPRESS,
  waterTempC = 85.0,
  doseG = 15.0,
  stages = listOf(
    RecipeStage(null, 0, 200.0, 10_000),
    RecipeStage(null, 10_000, 0.0, 10_000, StageType.SWIRL),
    RecipeStage(null, 20_000, 0.0, 5_000, StageType.ACTION, "Pon la tapa"),
    RecipeStage(null, 25_000, 0.0, 60_000, StageType.WAIT),
    RecipeStage(null, 85_000, 0.0, 5_000, StageType.ACTION, "Invierte"),
    RecipeStage(null, 90_000, 0.0, 30_000, StageType.ACTION),
  ).withDerivedWater(),
)

/**
 * "Acción primero": una Acción de 30 s con [instruction] y después un vertido de 20 s hasta 200 g. Empieza con la Acción
 * para probar la sesión con báscula en tiempo real sin esperar al paso.
 */
fun actionFirstRecipe(instruction: String?): Recipe = Recipe(
  name = "Acción primero",
  method = BrewMethod.AEROPRESS,
  doseG = 15.0,
  stages = listOf(
    RecipeStage(null, 0, 0.0, 30_000, StageType.ACTION, instruction),
    RecipeStage(null, 30_000, 200.0, 20_000),
  ),
)

private fun pour(startS: Int, waterG: Double, durationS: Int, name: String?, instruction: String?) =
  RecipeStage(name, startS * 1_000L, waterG, durationS * 1_000L, StageType.POUR, instruction)

private fun wait(startS: Int, waterG: Double, durationS: Int, instruction: String?) =
  RecipeStage(null, startS * 1_000L, waterG, durationS * 1_000L, StageType.WAIT, instruction)

/**
 * La antigua V60 clásica de fábrica (15 g, cinco pasos) como receta propia: sin `builtInKey`, con los textos de [texts]
 * y sin clave de catálogo. Los tests instrumentales la siembran (`TestAppDependencies.seedRecipes`) para no cambiar los
 * que buscan "V60" en pantalla.
 */
fun v60Classic(texts: RecipeTexts): Recipe {
  val names = texts.builtInStageNames(LegacyBuiltIns.V60_CLASSIC)
  val instructions = texts.builtInStageInstructions(LegacyBuiltIns.V60_CLASSIC)
  return Recipe(
    name = texts.builtInName(LegacyBuiltIns.V60_CLASSIC),
    method = BrewMethod.V60,
    doseG = 15.0,
    stages = listOf(
      pour(0, 40.0, 10, names[0], instructions[0]),
      wait(10, 40.0, 35, instructions[1]),
      pour(45, 150.0, 20, names[2], instructions[2]),
      wait(65, 150.0, 25, instructions[3]),
      pour(90, 250.0, 25, names[4], instructions[4]),
    ),
  )
}

/** El antiguo método 4:6 de fábrica (20 g, nueve pasos) como receta propia, como [v60Classic]. */
fun fourSix(texts: RecipeTexts): Recipe {
  val names = texts.builtInStageNames(LegacyBuiltIns.FOUR_SIX)
  val instructions = texts.builtInStageInstructions(LegacyBuiltIns.FOUR_SIX)
  return Recipe(
    name = texts.builtInName(LegacyBuiltIns.FOUR_SIX),
    method = BrewMethod.V60,
    doseG = 20.0,
    stages = listOf(
      pour(0, 60.0, 10, names[0], instructions[0]),
      wait(10, 60.0, 35, instructions[1]),
      pour(45, 120.0, 10, names[2], instructions[2]),
      wait(55, 120.0, 35, instructions[3]),
      pour(90, 180.0, 10, names[4], instructions[4]),
      wait(100, 180.0, 35, instructions[5]),
      pour(135, 240.0, 10, names[6], instructions[6]),
      wait(145, 240.0, 35, instructions[7]),
      pour(180, 300.0, 10, names[8], instructions[8]),
    ),
  )
}
