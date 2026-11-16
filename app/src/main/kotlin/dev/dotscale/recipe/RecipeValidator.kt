package dev.dotscale.recipe

sealed interface RecipeError {
  data object NameRequired : RecipeError

  /** Fuera de 5-60 g o con más de un decimal. */
  data object DoseInvalid : RecipeError

  /** Menos de 1 o más de 20 pasos. */
  data object StageCountInvalid : RecipeError

  /** Ningún paso es de tipo `POUR`. */
  data object NoPour : RecipeError

  /** Fuera de 0-100 °C o con más de un decimal. */
  data object TempInvalid : RecipeError

  /** Inicio de la última etapa a las 10:00 o después. */
  data object LastStartTooLate : RecipeError

  sealed interface StageError : RecipeError {
    val index: Int
  }

  data class StartNegative(override val index: Int) : StageError

  /** Empieza antes de que termine el vertido anterior. */
  data class StartOverlaps(override val index: Int) : StageError

  /** Agua acumulada ≤ 0 o > 1000 g. */
  data class WaterOutOfRange(override val index: Int) : StageError

  /** Agua acumulada no mayor que la de la etapa anterior. */
  data class WaterNotIncreasing(override val index: Int) : StageError

  /** Duración fuera de 1-120 s (`POUR` y `SWIRL`) o de 1-600 s (`WAIT`). */
  data class DurationOutOfRange(override val index: Int) : StageError

  /** Instrucción de más de 200 caracteres. */
  data class InstructionTooLong(override val index: Int) : StageError
}

/** Reglas de validación de una receta (§3.2 del spec). Función pura, la usa también el editor. */
object RecipeValidator {
  const val MIN_DOSE_G = 5.0
  const val MAX_DOSE_G = 60.0
  const val MAX_STAGES = 20
  const val MAX_WATER_G = 1000.0
  const val MIN_DURATION_MS = 1_000L
  const val MAX_DURATION_MS = 120_000L
  const val MAX_WAIT_MS = 600_000L
  const val MAX_INSTRUCTION = 200
  const val MAX_LAST_START_MS = 600_000L
  const val MAX_TEMP_C = 100.0

  fun validate(recipe: Recipe): List<RecipeError> {
    val errors = ArrayList<RecipeError>()
    if (recipe.name.isBlank()) errors += RecipeError.NameRequired
    if (!recipe.doseG.inRange(MIN_DOSE_G, MAX_DOSE_G) || !recipe.doseG.hasOneDecimalAtMost()) errors += RecipeError.DoseInvalid
    recipe.waterTempC?.let { temp ->
      if (!temp.inRange(0.0, MAX_TEMP_C) || !temp.hasOneDecimalAtMost()) errors += RecipeError.TempInvalid
    }
    if (recipe.stages.size !in 1..MAX_STAGES) {
      errors += RecipeError.StageCountInvalid
    } else {
      if (recipe.stages.none { it.type == StageType.POUR }) errors += RecipeError.NoPour
      var previousPourWaterG: Double? = null
      recipe.stages.forEachIndexed { i, stage ->
        val previous = recipe.stages.getOrNull(i - 1)
        if (stage.startMs < 0) errors += RecipeError.StartNegative(i)
        if (previous != null && stage.startMs < previous.startMs + previous.durationMs) errors += RecipeError.StartOverlaps(i)
        if (stage.type == StageType.POUR) {
          if (!stage.targetWaterG.isFinite() || stage.targetWaterG <= 0.0 || stage.targetWaterG > MAX_WATER_G) {
            errors += RecipeError.WaterOutOfRange(i)
          } else if (previousPourWaterG != null && stage.targetWaterG <= previousPourWaterG!!) {
            errors += RecipeError.WaterNotIncreasing(i)
          }
          previousPourWaterG = stage.targetWaterG
        }
        val maxDuration = if (stage.type == StageType.WAIT) MAX_WAIT_MS else MAX_DURATION_MS
        if (stage.durationMs !in MIN_DURATION_MS..maxDuration) errors += RecipeError.DurationOutOfRange(i)
        if ((stage.instruction?.length ?: 0) > MAX_INSTRUCTION) errors += RecipeError.InstructionTooLong(i)
      }
      if (recipe.stages.last().startMs >= MAX_LAST_START_MS) errors += RecipeError.LastStartTooLate
    }
    return errors
  }

  private fun Double.inRange(min: Double, max: Double) = isFinite() && this in min..max

  private fun Double.hasOneDecimalAtMost(): Boolean = kotlin.math.abs(this * 10 - Math.round(this * 10)) < 1e-6
}
