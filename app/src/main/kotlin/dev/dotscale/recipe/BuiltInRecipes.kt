package dev.dotscale.recipe

import dev.dotscale.history.BrewMethod

/** Recetas predefinidas de fábrica (§3.2 del spec). Los nombres se piden a [RecipeTexts]. */
object BuiltInRecipes {
  const val V60_CLASSIC = "v60_classic"
  const val FOUR_SIX = "four_six"

  /** En el orden en que se siembran. */
  val keys = listOf(V60_CLASSIC, FOUR_SIX)

  private class Definition(val doseG: Double, val stages: List<Triple<Long, Double, Long>>)

  private fun s(startSeconds: Int, waterG: Double, durationSeconds: Int) =
    Triple(startSeconds * 1_000L, waterG, durationSeconds * 1_000L)

  private val definitions = mapOf(
    V60_CLASSIC to Definition(15.0, listOf(s(0, 40.0, 10), s(45, 150.0, 20), s(90, 250.0, 25))),
    FOUR_SIX to Definition(
      20.0,
      listOf(s(0, 60.0, 10), s(45, 120.0, 10), s(90, 180.0, 10), s(135, 240.0, 10), s(180, 300.0, 10)),
    ),
  )

  /** La receta de fábrica de [key], con los nombres de [texts], `id = 0` y `localizedNames = true`. */
  fun recipe(key: String, texts: RecipeTexts): Recipe {
    val definition = definitions.getValue(key)
    val stageNames = texts.builtInStageNames(key)
    return Recipe(
      name = texts.builtInName(key),
      method = BrewMethod.V60,
      doseG = definition.doseG,
      stages = definition.stages.mapIndexed { i, (start, water, duration) ->
        RecipeStage(stageNames.getOrNull(i), start, water, duration)
      },
      builtInKey = key,
      localizedNames = true,
    )
  }

  fun all(texts: RecipeTexts): List<Recipe> = keys.map { recipe(it, texts) }
}
