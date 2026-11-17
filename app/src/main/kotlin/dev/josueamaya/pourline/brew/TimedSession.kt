package dev.josueamaya.pourline.brew

/**
 * Las cantidades del plan de una preparación sin báscula (SPEC-007 §3.5): lo que se guarda en lugar de lo medido.
 * Con receta salen de la receta escalada; sin ella, de la dosis y el ratio de Preparar. [doseG] es `null` si la
 * referencia no tiene dosis.
 */
data class PlannedAmounts(val doseG: Double?, val waterG: Double) {
  /** `waterG / doseG`; `null` sin dosis. */
  val ratio: Double? get() = doseG?.takeIf { it > 0 }?.let { waterG / it }
}

/**
 * Sesión cronometrada, sin báscula (SPEC-007): Armada → Activa → Terminada, guiada solo por el reloj. Publica los
 * mismos [BrewState] y emite los mismos [BrewEffect.Finished] que [BrewSession], pero sin muestras ni estabilidad, y
 * nunca emite los efectos del temporizador de la báscula.
 *
 * El tiempo se inyecta en cada llamada (`nowMs`, reloj monotónico). No es thread-safe: se llama siempre desde el
 * hilo principal.
 */
class TimedSession(private val config: BrewConfig = BrewConfig()) {

  /** Estado publicado. Es la misma instancia mientras nada cambie. */
  var state: BrewState = BrewState.Initial
    private set

  private var amounts: PlannedAmounts? = null
  private var t0 = 0L

  /** Deja la sesión en `Armed` con las cantidades del plan. Se ignora en `Active`. */
  fun arm(amounts: PlannedAmounts) {
    if (state.phase is BrewPhase.Active) return
    this.amounts = amounts
    state = BrewState.Initial
  }

  /** `Armed` → `Active` con t0 = [nowMs]. Se ignora sin [arm] o fuera de `Armed`. */
  fun start(nowMs: Long): List<BrewEffect> {
    if (amounts == null || state.phase != BrewPhase.Armed) return emptyList()
    t0 = nowMs
    state = state.copy(phase = BrewPhase.Active(pouring = false), t0MonoMs = nowMs)
    return emptyList()
  }

  /** Termina por [BrewConfig.maxDurationMs] si se ha cumplido. */
  fun tick(nowMs: Long): List<BrewEffect> {
    if (state.phase !is BrewPhase.Active) return emptyList()
    return if (nowMs - t0 >= config.maxDurationMs) finishWith(FinishReason.MAX_DURATION, config.maxDurationMs) else emptyList()
  }

  /** `Active` → `Finished`. Solo vale [FinishReason.MANUAL] o [FinishReason.MAX_DURATION]. */
  fun finish(nowMs: Long, reason: FinishReason = FinishReason.MANUAL): List<BrewEffect> {
    require(reason == FinishReason.MANUAL || reason == FinishReason.MAX_DURATION) { "Motivo no válido sin báscula: $reason" }
    if (state.phase !is BrewPhase.Active) return emptyList()
    val total = if (reason == FinishReason.MAX_DURATION) config.maxDurationMs else (nowMs - t0).coerceIn(0, config.maxDurationMs)
    return finishWith(reason, total)
  }

  /** Vuelve a `Armed` con las mismas cantidades, sin emitir nada. */
  fun discard(): List<BrewEffect> {
    if (amounts != null) state = BrewState.Initial
    return emptyList()
  }

  private fun finishWith(reason: FinishReason, totalTimeMs: Long): List<BrewEffect> {
    val planned = amounts ?: return emptyList()
    val summary = BrewSummary(
      totalTimeMs = totalTimeMs,
      totalWaterG = planned.waterG,
      avgFlowGps = 0.0,
      maxFlowGps = 0.0,
      stabilityScore = null,
      stabilityLevel = null,
      levelShare = emptyMap(),
      pours = emptyList(),
      finalRatio = planned.ratio,
      reason = reason,
      interrupted = false,
    )
    state = state.copy(phase = BrewPhase.Finished(summary, rearmed = false, pourIgnored = false), t0MonoMs = t0)
    return listOf(BrewEffect.Finished(FinishedBrew(t0, emptyList(), summary, planned.doseG)))
  }
}
