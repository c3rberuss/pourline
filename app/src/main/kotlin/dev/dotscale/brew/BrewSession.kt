package dev.dotscale.brew

import dev.dotscale.protocol.ScaleMessage
import dev.dotscale.protocol.TimerStatus

/**
 * Máquina de estados pura y determinista de una preparación (Armada → Activa → Terminada).
 *
 * El tiempo se inyecta en cada llamada (`nowMs`, reloj monotónico). No es thread-safe: el
 * ViewModel la llama siempre desde el hilo principal.
 *
 * Internamente todo va en gramos y g/s, sea cual sea la unidad de la báscula.
 */
class BrewSession(private val config: BrewConfig = BrewConfig()) {

  /** Estado publicado. Es la misma instancia mientras nada cambie. */
  var state: BrewState = BrewState.Initial
    private set

  private enum class Kind { ARMED, ACTIVE, FINISHED }

  private class RunPoint(val nowMs: Long, val weightG: Double, val flowGps: Double)

  private class OpenPour(val startMs: Long, val waterBeforeG: Double)

  private class WindowPoint(val tMs: Long, val flowGps: Double)

  // Comunes a todas las fases.
  private var kind = Kind.ARMED
  private var lastValidWeightG: Double? = null
  private var lastRawWeightG = 0.0
  private val run = ArrayList<RunPoint>()
  private var runPreValidG: Double? = null
  private var doseG: Double? = null
  private var summary: BrewSummary? = null
  private var rearmed = false
  private var pourIgnored = false

  // Solo con sesión.
  private var t0 = 0L
  private var baselineG = 0.0
  private var samples = ArrayList<BrewSample>()
  private val pours = ArrayList<Pour>()
  private var openPour: OpenPour? = null
  private var lastAboveMs = 0L
  private var lastValidWaterG: Double? = null
  private var startTimerSent = false
  private val window = ArrayDeque<WindowPoint>()
  private var currentStability: Int? = null
  private var currentLevel: StabilityLevel? = null

  private var snapshot: List<BrewSample> = emptyList()
  private var dirty = false

  /** Devuelve los efectos que el ViewModel debe ejecutar. */
  fun onWeight(message: ScaleMessage.Weight, nowMs: Long, timer: TimerStatus): List<BrewEffect> {
    val weightG = toGrams(message.weight, message.unit)
    val flowGps = toGrams(message.flowRate, message.unit)
    val valid = !message.outOfRange && weightG >= -config.negativeWeightToleranceG
    var effects: List<BrewEffect> = emptyList()

    if (kind == Kind.ACTIVE) {
      val tMs = nowMs - t0
      if (tMs >= config.maxDurationMs) {
        finishWith(FinishReason.MAX_DURATION, config.maxDurationMs)
      } else {
        appendSample(tMs, weightG, flowGps, valid)
      }
    } else if (updateRun(nowMs, weightG, flowGps, valid)) {
      if (kind == Kind.ARMED || rearmed) {
        effects = startSession(timer)
      } else if (!pourIgnored) {
        pourIgnored = true
        run.clear()
        dirty = true
      }
    }

    if (valid) lastValidWeightG = weightG
    lastRawWeightG = weightG
    publish()
    return effects
  }

  fun onTimer(status: TimerStatus, nowMs: Long) {
    when (kind) {
      Kind.ACTIVE -> when (status) {
        TimerStatus.PAUSED -> finishWith(FinishReason.TIMER_PAUSED, nowMs - t0)
        TimerStatus.RESET -> finishWith(FinishReason.TIMER_RESET, nowMs - t0)
        else -> Unit
      }
      Kind.FINISHED -> if (status == TimerStatus.RESET) rearm()
      Kind.ARMED -> Unit
    }
    publish()
  }

  @Suppress("UNUSED_PARAMETER")
  fun onTared(nowMs: Long) {
    when (kind) {
      Kind.ARMED -> {
        run.clear()
        lastValidWeightG = null
      }
      // El peso bruto pasa a ser cero: se corre la línea base para que el agua sea continua.
      Kind.ACTIVE -> baselineG -= lastRawWeightG
      Kind.FINISHED -> {
        rearm()
        lastValidWeightG = null
      }
    }
    lastRawWeightG = 0.0
    publish()
  }

  fun onDisconnected(nowMs: Long) {
    when (kind) {
      Kind.ARMED -> run.clear()
      Kind.ACTIVE -> finishWith(FinishReason.DISCONNECTED, samples.lastOrNull()?.tMs ?: (nowMs - t0))
      Kind.FINISHED -> Unit
    }
    publish()
  }

  /** Evalúa el cierre de vertidos, la inactividad y la duración máxima. */
  fun onTick(nowMs: Long, autoFinishMs: Long) {
    if (kind != Kind.ACTIVE) return
    val tMs = nowMs - t0
    closePourIfGap(tMs)
    val inactivityAt = lastAboveMs + autoFinishMs
    val maxAt = config.maxDurationMs
    if (tMs >= inactivityAt || tMs >= maxAt) {
      if (inactivityAt <= maxAt) {
        finishWith(FinishReason.INACTIVITY, lastAboveMs)
      } else {
        finishWith(FinishReason.MAX_DURATION, maxAt)
      }
    }
    publish()
  }

  /** Botón "Terminar". */
  fun finish(nowMs: Long) {
    if (kind != Kind.ACTIVE) return
    finishWith(FinishReason.MANUAL, nowMs - t0)
    publish()
  }

  /** Dosis en gramos para el ratio final. Se aplica al terminar; no modifica un resumen ya calculado. */
  fun setDoseGrams(dose: Double?) {
    doseG = dose
  }

  // --- Inicio ---

  /** Actualiza la racha de confirmación y dice si ya se confirmó un vertido. */
  private fun updateRun(nowMs: Long, weightG: Double, flowGps: Double, valid: Boolean): Boolean {
    if (valid && flowGps >= config.pourThresholdGps) {
      if (run.isEmpty()) runPreValidG = lastValidWeightG
      run.add(RunPoint(nowMs, weightG, flowGps))
    } else {
      run.clear()
    }
    return run.isNotEmpty() && run.last().nowMs - run.first().nowMs >= config.pourConfirmMs
  }

  private fun startSession(timer: TimerStatus): List<BrewEffect> {
    t0 = run.first().nowMs
    baselineG = runPreValidG ?: run.first().weightG
    samples = ArrayList()
    pours.clear()
    openPour = null
    window.clear()
    lastValidWaterG = null
    currentStability = null
    currentLevel = null
    summary = null
    rearmed = false
    pourIgnored = false
    startTimerSent = false
    kind = Kind.ACTIVE

    val confirmation = run.toList()
    run.clear()
    confirmation.forEach { appendSample(it.nowMs - t0, it.weightG, it.flowGps, valid = true) }

    dirty = true
    if (timer == TimerStatus.RUNNING) return emptyList()
    startTimerSent = true
    return listOf(BrewEffect.StartTimer)
  }

  private fun rearm() {
    rearmed = true
    pourIgnored = false
    run.clear()
    dirty = true
  }

  // --- Sesión activa ---

  private fun appendSample(tMs: Long, weightG: Double, flowGps: Double, valid: Boolean) {
    val waterG = weightG - baselineG
    closePourIfGap(tMs)

    var stability: Int? = null
    if (valid && flowGps >= config.pourThresholdGps) {
      val pour = openPour ?: OpenPour(tMs, lastValidWaterG ?: 0.0).also {
        openPour = it
        window.clear()
      }
      lastAboveMs = tMs
      // La rampa y las muestras bajo el umbral no entran en la ventana (D1).
      if (tMs - pour.startMs >= config.stabilityRampMs) stability = windowScore(tMs, flowGps)
      currentStability = stability
      currentLevel = stability?.let(config::levelOf)
    }

    samples.add(BrewSample(tMs, waterG, flowGps, stability, valid))
    if (valid) lastValidWaterG = waterG
    dirty = true
  }

  private fun windowScore(tMs: Long, flowGps: Double): Int? {
    window.addLast(WindowPoint(tMs, flowGps))
    while (window.first().tMs <= tMs - config.stabilityWindowMs) window.removeFirst()
    if (window.last().tMs - window.first().tMs < config.stabilityMinWindowMs) return null
    return stabilityScore(window.map { it.flowGps }, config.cvZeroScore)
  }

  /** Cierra el vertido abierto si lleva `pourGapMs` por debajo del umbral. */
  private fun closePourIfGap(tMs: Long) {
    if (openPour != null && tMs - lastAboveMs >= config.pourGapMs) closePour()
  }

  private fun closePour() {
    val open = openPour ?: return
    pours.add(Pour(pours.size + 1, open.startMs, lastAboveMs, (lastValidWaterG ?: 0.0) - open.waterBeforeG))
    openPour = null
    window.clear()
    currentStability = null
    currentLevel = null
    dirty = true
  }

  private fun finishWith(reason: FinishReason, totalTimeMs: Long) {
    closePour()
    // Se descarta el rastro posterior al fin (p. ej. el minuto de inactividad).
    val cutoff = totalTimeMs + config.pourGapMs
    while (samples.isNotEmpty() && samples[samples.lastIndex].tMs > cutoff) samples.removeAt(samples.lastIndex)

    summary = summarize(samples, pours.toList(), totalTimeMs, reason, doseG, config)
    kind = Kind.FINISHED
    rearmed = false
    pourIgnored = false
    openPour = null
    window.clear()
    currentStability = null
    currentLevel = null
    run.clear()
    snapshot = samples.toList()
    dirty = true
  }

  // --- Publicación (D13) ---

  private fun publish() {
    if (!dirty) return
    dirty = false
    val phase = when (kind) {
      Kind.ARMED -> BrewPhase.Armed
      Kind.ACTIVE -> BrewPhase.Active(pouring = openPour != null)
      Kind.FINISHED -> BrewPhase.Finished(checkNotNull(summary), rearmed, pourIgnored)
    }
    if (kind == Kind.ACTIVE) snapshot = samples.toList()
    state = BrewState(phase, snapshot, currentStability, currentLevel)
  }
}
