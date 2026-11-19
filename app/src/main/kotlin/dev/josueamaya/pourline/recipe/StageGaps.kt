package dev.josueamaya.pourline.recipe

/** Convierte los huecos entre pasos en pasos `WAIT` explícitos (D36). Función pura. */
object StageGaps {
  /**
   * Un `WAIT` por cada hueco mayor que 0 entre el fin de un paso y el inicio del siguiente, y también desde 0 si el
   * primer paso empieza después. El `WAIT` no lleva nombre ni instrucción y lleva el agua acumulada anterior. Los
   * solapes (hueco ≤ 0) no generan nada. [stages] se espera ordenada por `startMs`.
   */
  fun fillWaits(stages: List<RecipeStage>): List<RecipeStage> {
    val result = ArrayList<RecipeStage>(stages.size * 2)
    var previousEndMs = 0L
    var accumulatedWaterG = 0.0
    for (stage in stages) {
      if (stage.startMs > previousEndMs) {
        result += RecipeStage(
          name = null,
          startMs = previousEndMs,
          targetWaterG = accumulatedWaterG,
          durationMs = stage.startMs - previousEndMs,
          type = StageType.WAIT,
        )
      }
      result += stage
      previousEndMs = stage.endMs
      if (stage.type == StageType.POUR) accumulatedWaterG = stage.targetWaterG
    }
    return result
  }
}
