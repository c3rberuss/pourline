package dev.dotscale.recipe

import dev.dotscale.brew.BrewSample
import dev.dotscale.brew.Pour
import dev.dotscale.brew.TargetCurve
import dev.dotscale.history.BrewDetails
import kotlin.math.roundToLong

/** Lo que se ajustó al convertir una preparación en receta. El editor lo muestra en un aviso. */
sealed interface Adjustment {
  /** Se fusionaron [count] pares de vertidos para quedar en 10 etapas. */
  data class MergedPours(val count: Int) : Adjustment

  /** Alguna duración de más de 120 s se recortó a 120 s. */
  data object ClampedDurations : Adjustment

  /** La dosis no está entre 5 y 60 g: el editor la deja vacía. */
  data object MissingDose : Adjustment
}

/**
 * Receta propuesta desde una preparación. Con [Adjustment.MissingDose], `recipe.doseG` vale 0 y el
 * editor deja el campo vacío.
 */
data class RecipeDraft(val recipe: Recipe, val adjustments: List<Adjustment>)

/** Convierte una preparación en una receta editable (D16 del plan). Función pura. */
object RecipeFromBrew {
  private class Stage(val startMs: Long, val endMs: Long, val waterG: Double)

  /**
   * Una etapa por vertido, con el agua acumulada de [TargetCurve.fromBrew]. Orden: fusionar (más de
   * 10), redondear (inicio y duración a segundos, mínimo 1 s; agua a 1 g) y recortar a 120 s.
   *
   * @param name nombre ya compuesto (el de la preparación o su fecha, más " (receta)").
   */
  fun convert(
    name: String,
    details: BrewDetails,
    doseG: Double?,
    samples: List<BrewSample>,
    pours: List<Pour>,
    totalTimeMs: Long,
    totalWaterG: Double,
  ): RecipeDraft {
    val adjustments = ArrayList<Adjustment>()
    var stages = initialStages(samples, pours, totalTimeMs, totalWaterG)

    var merged = 0
    while (stages.size > RecipeValidator.MAX_STAGES) {
      val i = (0 until stages.size - 1).minBy { stages[it + 1].startMs - stages[it].endMs }
      val a = stages[i]
      val b = stages[i + 1]
      stages = stages.take(i) + Stage(a.startMs, b.endMs, b.waterG) + stages.drop(i + 2)
      merged++
    }
    if (merged > 0) adjustments += Adjustment.MergedPours(merged)

    var clamped = false
    val recipeStages = stages.map { stage ->
      val start = roundToSeconds(stage.startMs)
      var duration = maxOf(roundToSeconds(stage.endMs - stage.startMs), RecipeValidator.MIN_DURATION_MS)
      if (duration > RecipeValidator.MAX_DURATION_MS) {
        duration = RecipeValidator.MAX_DURATION_MS
        clamped = true
      }
      RecipeStage(name = null, startMs = start, targetWaterG = Math.round(stage.waterG).toDouble(), durationMs = duration)
    }
    if (clamped) adjustments += Adjustment.ClampedDurations

    val dose = doseG?.let { Math.round(it * 10) / 10.0 }?.takeIf { it in RecipeValidator.MIN_DOSE_G..RecipeValidator.MAX_DOSE_G }
    if (dose == null) adjustments += Adjustment.MissingDose

    val recipe = Recipe(
      name = name,
      method = details.method,
      methodOther = details.methodOther,
      grind = details.grind,
      waterTempC = details.waterTempC,
      doseG = dose ?: 0.0,
      stages = recipeStages,
    )
    return RecipeDraft(recipe, adjustments)
  }

  private fun initialStages(samples: List<BrewSample>, pours: List<Pour>, totalTimeMs: Long, totalWaterG: Double): List<Stage> {
    val curve = TargetCurve.fromBrew(samples, pours, totalTimeMs)
    if (curve != null) return curve.stages.map { Stage(it.startMs, it.endMs, it.targetWaterG) }
    // Sin muestras válidas: el agua acumulada sale de la suma de los vertidos.
    if (pours.isEmpty()) return listOf(Stage(0, maxOf(totalTimeMs, 0L), totalWaterG))
    var accumulated = 0.0
    return pours.map { pour ->
      accumulated += pour.waterG
      Stage(pour.startMs, pour.endMs, accumulated)
    }
  }

  private fun roundToSeconds(ms: Long): Long = (ms / 1000.0).roundToLong() * 1000
}
