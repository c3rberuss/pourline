package dev.dotscale.brew

/** Una muestra de la sesión. `tMs` es relativo a t0 (primer instante sobre el umbral). */
data class BrewSample(
  val tMs: Long,
  /** Agua vertida = peso actual − línea base, en gramos. */
  val waterG: Double,
  /** Flujo enviado por la báscula, en g/s. */
  val flowGps: Double,
  /** Puntuación 0-100; `null` si no hay vertido o la ventana es insuficiente. */
  val stability: Int?,
  /** `false` si la muestra estaba fuera de rango o con peso negativo: se dibuja como hueco. */
  val valid: Boolean,
)

enum class StabilityLevel { STABLE, IRREGULAR, TURBULENT }

data class Pour(val index: Int, val startMs: Long, val endMs: Long, val waterG: Double)

enum class FinishReason { INACTIVITY, TIMER_PAUSED, TIMER_RESET, MANUAL, DISCONNECTED, MAX_DURATION }

data class BrewSummary(
  val totalTimeMs: Long,
  val totalWaterG: Double,
  /** Promedio ponderado por tiempo, solo durante vertidos. */
  val avgFlowGps: Double,
  val maxFlowGps: Double,
  /** Promedio ponderado por tiempo de las puntuaciones no nulas. */
  val stabilityScore: Int?,
  val stabilityLevel: StabilityLevel?,
  /** Fracción 0..1 del tiempo puntuado (`stability != null`) en cada nivel. */
  val levelShare: Map<StabilityLevel, Double>,
  val pours: List<Pour>,
  /** `totalWaterG / dosis` en gramos; `null` sin dosis. */
  val finalRatio: Double?,
  val reason: FinishReason,
  /** `true` si `reason == DISCONNECTED`. */
  val interrupted: Boolean,
)

sealed interface BrewPhase {
  data object Armed : BrewPhase

  data class Active(val pouring: Boolean) : BrewPhase

  /** `pourIgnored`: se detectó un vertido estando terminada y sin rearmar. */
  data class Finished(val summary: BrewSummary, val rearmed: Boolean, val pourIgnored: Boolean) : BrewPhase
}

data class BrewState(
  val phase: BrewPhase,
  /** Vacía en `Armed` sin sesión previa. */
  val samples: List<BrewSample>,
  val currentStability: Int?,
  val currentLevel: StabilityLevel?,
) {
  companion object {
    val Initial = BrewState(BrewPhase.Armed, emptyList(), currentStability = null, currentLevel = null)
  }
}

sealed interface BrewEffect {
  /** El ViewModel envía `ScaleCommands.startTimer()`. */
  data object StartTimer : BrewEffect

  /** El ViewModel envía `ScaleCommands.pauseTimer()`. */
  data object PauseTimer : BrewEffect

  /** El ViewModel envía `ScaleCommands.resetTimer()`. */
  data object ResetTimer : BrewEffect

  /** Se emite una sola vez por sesión, al pasar a Finished. */
  data class Finished(val brew: FinishedBrew) : BrewEffect
}

/** Lo que el ViewModel necesita para guardar una sesión terminada. */
data class FinishedBrew(
  /** Reloj monotónico de t0, para calcular la hora de pared. */
  val t0MonoMs: Long,
  /** Muestras ya recortadas (D4 de SPEC-001). */
  val samples: List<BrewSample>,
  val summary: BrewSummary,
  val doseG: Double?,
)
