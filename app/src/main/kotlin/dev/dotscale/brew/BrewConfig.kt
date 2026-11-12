package dev.dotscale.brew

/** Constantes de la sesión. Son valores iniciales que se calibrarán con la báscula física. */
data class BrewConfig(
  /** Umbral de vertido (el mismo que usa la app oficial). */
  val pourThresholdGps: Double = 0.5,
  /** Tiempo continuo sobre el umbral para confirmar el inicio. */
  val pourConfirmMs: Long = 500,
  /** Tiempo bajo el umbral para cerrar un vertido. */
  val pourGapMs: Long = 2_000,
  /** Ventana móvil de la métrica. */
  val stabilityWindowMs: Long = 3_000,
  /** Rampa inicial de cada vertido, excluida de la métrica. */
  val stabilityRampMs: Long = 1_000,
  /** Datos mínimos en la ventana para dar puntuación. */
  val stabilityMinWindowMs: Long = 1_000,
  /** CV con el que la puntuación llega a 0. */
  val cvZeroScore: Double = 0.5,
  val levelStableMin: Int = 75,
  val levelIrregularMin: Int = 50,
  val maxDurationMs: Long = 900_000,
  /** Tope del Δt con el que pesa una muestra en las métricas ponderadas. */
  val maxSampleGapMs: Long = 500,
  /** Por debajo de este peso (g) una muestra es inválida; el ruido cercano a 0 tras una tara no cuenta. */
  val negativeWeightToleranceG: Double = 1.0,
) {
  fun levelOf(score: Int): StabilityLevel = when {
    score >= levelStableMin -> StabilityLevel.STABLE
    score >= levelIrregularMin -> StabilityLevel.IRREGULAR
    else -> StabilityLevel.TURBULENT
  }

  companion object {
    /**
     * Versión de la métrica con la que se calculan y se guardan los resúmenes. Hay que incrementarla
     * cuando cambie cualquier constante que afecte a la estabilidad o al resumen (umbrales, ventana,
     * `cvZeroScore`, niveles...), para que el historial pueda saber con qué versión se puntuó cada
     * preparación.
     */
    const val METRIC_VERSION = 1
  }
}
