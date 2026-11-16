package dev.dotscale.recipe

import dev.dotscale.history.BrewMethod

/**
 * Recetas predefinidas de fábrica (§3.2 del spec de recetas, con los pasos con tipo de SPEC-005). Los nombres y
 * las instrucciones se piden a [RecipeTexts] por posición. Cada definición es la antigua con los huecos
 * convertidos en pasos `WAIT` ([StageGaps.fillWaits]), así que la curva objetivo es la misma de siempre.
 */
object BuiltInRecipes {
  const val V60_CLASSIC = "v60_classic"
  const val FOUR_SIX = "four_six"

  /** En el orden en que se siembran. */
  val keys = listOf(V60_CLASSIC, FOUR_SIX)

  private class Definition(val doseG: Double, val stages: List<RecipeStage>)

  private fun pour(startSeconds: Int, waterG: Double, durationSeconds: Int) =
    RecipeStage(null, startSeconds * 1_000L, waterG, durationSeconds * 1_000L, StageType.POUR)

  private fun wait(startSeconds: Int, waterG: Double, durationSeconds: Int) =
    RecipeStage(null, startSeconds * 1_000L, waterG, durationSeconds * 1_000L, StageType.WAIT)

  private val definitions = mapOf(
    V60_CLASSIC to Definition(
      15.0,
      listOf(pour(0, 40.0, 10), wait(10, 40.0, 35), pour(45, 150.0, 20), wait(65, 150.0, 25), pour(90, 250.0, 25)),
    ),
    FOUR_SIX to Definition(
      20.0,
      listOf(
        pour(0, 60.0, 10), wait(10, 60.0, 35),
        pour(45, 120.0, 10), wait(55, 120.0, 35),
        pour(90, 180.0, 10), wait(100, 180.0, 35),
        pour(135, 240.0, 10), wait(145, 240.0, 35),
        pour(180, 300.0, 10),
      ),
    ),
  )

  /** La receta de fábrica de [key], con los nombres de [texts], `id = 0` y `localizedNames = true`. */
  fun recipe(key: String, texts: RecipeTexts): Recipe {
    val definition = definitions.getValue(key)
    val names = texts.builtInStageNames(key)
    val instructions = texts.builtInStageInstructions(key)
    return Recipe(
      name = texts.builtInName(key),
      method = BrewMethod.V60,
      doseG = definition.doseG,
      stages = definition.stages.mapIndexed { i, stage ->
        stage.copy(name = names.getOrNull(i), instruction = instructions.getOrNull(i))
      },
      builtInKey = key,
      localizedNames = true,
    )
  }

  fun all(texts: RecipeTexts): List<Recipe> = keys.map { recipe(it, texts) }
}
