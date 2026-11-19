package dev.josueamaya.pourline.recipe

import dev.josueamaya.pourline.history.BrewMethod

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
