package dev.dotscale.recipe

import dev.dotscale.history.BrewMethod

/** Todas las magnitudes van en gramos, milisegundos y °C. */
data class Recipe(
  val id: Long = 0,
  val name: String,
  val method: BrewMethod? = null,
  /** Solo con `method == OTHER`. */
  val methodOther: String? = null,
  val grind: String? = null,
  val waterTempC: Double? = null,
  /** Instrucciones, no notas de cata. */
  val notes: String? = null,
  /** Dosis nominal. */
  val doseG: Double,
  /** Ordenadas por `startMs`. */
  val stages: List<RecipeStage>,
  /** `"v60_classic"`, `"four_six"` o `null` si la creó el usuario. */
  val builtInKey: String? = null,
  /** `true`: los nombres se muestran traducidos (solo predefinidas sin renombrar). */
  val localizedNames: Boolean = false,
) {
  val totalWaterG: Double get() = stages.last().targetWaterG
  val ratio: Double get() = totalWaterG / doseG
}

data class RecipeStage(
  /** Opcional ("Bloom"). */
  val name: String?,
  /** Instante de inicio relativo a t0. */
  val startMs: Long,
  /** Agua ACUMULADA al terminar el vertido de la etapa. */
  val targetWaterG: Double,
  /** Duración del vertido. */
  val durationMs: Long,
) {
  val endMs: Long get() = startMs + durationMs
}
