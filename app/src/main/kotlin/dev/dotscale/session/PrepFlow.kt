package dev.dotscale.session

import kotlin.math.abs

/** Los pasos previos de la sesión (§3.4): tarar, añadir la dosis, tarar para verter y verter. */
enum class PrepStep { TARE, DOSE, TARE_FOR_POUR, POUR }

/**
 * Lo que la sesión muestra de los pasos previos.
 *
 * @param doseG la dosis capturada, en gramos; `null` sin captura o saltada.
 * @param doseSkipped el usuario saltó el paso de la dosis ("Saltar (sin dosis)"): no hay recaptura.
 * @param stableProgress 0..1: cuánto de la ventana de estabilidad del paso de la dosis se ha cumplido.
 * @param waitingTare se envió "Tarar" desde la app y aún no se confirma.
 * @param interrupted la báscula se desconectó durante los pasos previos y aún no ha vuelto.
 */
data class PrepState(
  val step: PrepStep = PrepStep.TARE,
  val doseG: Double? = null,
  val doseSkipped: Boolean = false,
  val stableProgress: Float = 0f,
  val waitingTare: Boolean = false,
  val interrupted: Boolean = false,
)

/** Lo que el gestor ejecuta por cuenta de [PrepFlow] (D42). */
sealed interface PrepEffect {
  /** Envía la tara a la báscula. */
  data object SendTare : PrepEffect

  /** Fija (o borra, con `null`) la dosis de la sesión, en gramos, sin tarar. */
  data class SetDose(val grams: Double?) : PrepEffect

  /** Arma la `BrewSession`: desde aquí el agua empieza la preparación. */
  data object Arm : PrepEffect

  /** "Empezar timer": la `BrewSession` pasa a `Active` sin esperar al agua. */
  data object StartNow : PrepEffect
}

/**
 * Los umbrales de [PrepFlow] (D17, D19). La DOT no manda una bandera de peso estable ni se ha verificado que mande la
 * trama de tara, así que todo sale de los pesos y está aquí para poder calibrarlo con la báscula física.
 */
data class PrepConfig(
  val stableWindowMs: Long = 2_000,
  val stableToleranceG: Double = 0.3,
  val stableMinSamples: Int = 3,
  val minDoseG: Double = 1.0,
  val autoCaptureMinShare: Double = 0.5,
  val recaptureMaxShare: Double = 1.5,
  val recaptureDeltaG: Double = 0.3,
  val zeroToleranceG: Double = 0.3,
  val tareSettleMs: Long = 1_000,
  val tareTimeoutMs: Long = 5_000,
)

/**
 * Máquina pura de los pasos previos de la sesión: Tara → Dosis → Tara → Vertido. No sabe de Android ni de la báscula:
 * el gestor le pasa cada peso y ejecuta los [PrepEffect] que devuelve.
 *
 * - **Tara confirmada (D19):** vale la trama de tara, el peso en cero un segundo tras "Tarar" en la app (en los 5 s
 *   siguientes) o el peso que pasa de ≥ 1 g a cero estable sin tara enviada (botón físico).
 * - **Dosis (D17):** una ventana de 2 s con 3 lecturas como mínimo, una variación de 0,3 g como máximo y un peso de al
 *   menos el 50 % de la dosis objetivo. Antes de la segunda tara, otra ventana estable de hasta el 150 % que difiera
 *   más de 0,3 g sustituye a la dosis (recaptura), salvo que se saltara la dosis.
 *
 * @param objectiveDoseG la dosis objetivo: la nominal de la receta o la de Preparar.
 */
class PrepFlow(private val objectiveDoseG: Double, private val config: PrepConfig = PrepConfig()) {

  var state = PrepState()
    private set

  private class Reading(val nowMs: Long, val grams: Double)

  private val window = ArrayDeque<Reading>()
  private var tareSentMs = 0L
  private var zeroSinceMs: Long? = null
  private var sawWeight = false

  /** Empieza en el paso 1; con una dosis ya capturada ([knownDoseG]) el paso 2 se da por hecho (D24). */
  fun start(knownDoseG: Double?): List<PrepEffect> {
    reset()
    state = PrepState(doseG = knownDoseG)
    return emptyList()
  }

  /** Una lectura de peso en gramos; solo importa mientras el paso no es el de verter. */
  fun onWeight(grams: Double, valid: Boolean, nowMs: Long): List<PrepEffect> {
    val step = state.step
    if (step == PrepStep.POUR) return emptyList()
    if (!valid) {
      window.clear()
      zeroSinceMs = null
      publishProgress()
      return emptyList()
    }
    return when (step) {
      PrepStep.TARE, PrepStep.TARE_FOR_POUR -> {
        val effects = tareWeight(grams, nowMs)
        if (effects.isEmpty() && step == PrepStep.TARE_FOR_POUR) recapture(grams, nowMs) else effects
      }
      PrepStep.DOSE -> stableDose(grams, nowMs)
      PrepStep.POUR -> emptyList()
    }
  }

  /** La báscula confirmó la tara (con la trama de tara). */
  fun onTared(@Suppress("UNUSED_PARAMETER") nowMs: Long): List<PrepEffect> =
    if (state.step == PrepStep.TARE || state.step == PrepStep.TARE_FOR_POUR) confirmTare() else emptyList()

  /** "Tarar" en la app: envía la tara y espera su confirmación. */
  fun tare(nowMs: Long): List<PrepEffect> {
    if (state.step != PrepStep.TARE && state.step != PrepStep.TARE_FOR_POUR) return emptyList()
    tareSentMs = nowMs
    zeroSinceMs = null
    window.clear()
    state = state.copy(waitingTare = true, stableProgress = 0f)
    return listOf(PrepEffect.SendTare)
  }

  /** "Saltar" en los pasos 1 y 3. */
  fun skip(): List<PrepEffect> = if (state.step == PrepStep.TARE || state.step == PrepStep.TARE_FOR_POUR) confirmTare() else emptyList()

  /** "Capturar ahora": toma el peso actual como dosis, sin esperar a que sea estable. */
  fun captureNow(grams: Double): List<PrepEffect> {
    if (state.step != PrepStep.DOSE || grams <= 0.0) return emptyList()
    return captureDose(round1(grams))
  }

  /** "Saltar (sin dosis)": sigue sin dosis y sin recaptura. */
  fun skipDose(): List<PrepEffect> {
    if (state.step != PrepStep.DOSE) return emptyList()
    enter(PrepStep.TARE_FOR_POUR, state.copy(doseG = null, doseSkipped = true))
    return emptyList()
  }

  /** "Empezar timer" en el paso 4. */
  fun startTimer(): List<PrepEffect> = if (state.step == PrepStep.POUR) listOf(PrepEffect.StartNow) else emptyList()

  /** La dosis de la sesión se borró (por ejemplo, al cambiar de unidad). */
  fun forgetDose() {
    state = state.copy(doseG = null)
  }

  /** La báscula se desconectó: vuelve al paso 1 con la dosis, y avisa de la interrupción. */
  fun onDisconnected() {
    reset()
    state = state.copy(step = PrepStep.TARE, waitingTare = false, stableProgress = 0f, interrupted = true)
  }

  /** La báscula volvió: puede haber perdido la tara, así que se empieza por el paso 1 (D24). */
  fun onReconnected() {
    reset()
    state = state.copy(step = PrepStep.TARE, waitingTare = false, stableProgress = 0f, interrupted = false)
  }

  // --- Tara ---

  private fun tareWeight(grams: Double, nowMs: Long): List<PrepEffect> {
    if (grams >= config.minDoseG) sawWeight = true
    if (abs(grams) > config.zeroToleranceG) {
      zeroSinceMs = null
      expireTare(nowMs)
      return emptyList()
    }
    val since = zeroSinceMs ?: nowMs.also { zeroSinceMs = it }
    if (state.waitingTare) {
      // (b) tras "Tarar": cuenta desde el envío y solo dentro del plazo.
      if (nowMs - tareSentMs > config.tareTimeoutMs) {
        state = state.copy(waitingTare = false)
        return emptyList()
      }
      if (nowMs - maxOf(since, tareSentMs) >= config.tareSettleMs) return confirmTare()
    } else if (sawWeight && nowMs - since >= config.tareSettleMs) {
      // (c) el peso pasó de ≥ 1 g a cero sin tara enviada: el botón de la báscula.
      return confirmTare()
    }
    return emptyList()
  }

  private fun expireTare(nowMs: Long) {
    if (state.waitingTare && nowMs - tareSentMs > config.tareTimeoutMs) state = state.copy(waitingTare = false)
  }

  private fun confirmTare(): List<PrepEffect> {
    val next = if (state.step == PrepStep.TARE) {
      if (state.doseG != null || state.doseSkipped) PrepStep.TARE_FOR_POUR else PrepStep.DOSE
    } else {
      PrepStep.POUR
    }
    enter(next, state.copy(waitingTare = false))
    return if (next == PrepStep.POUR) listOf(PrepEffect.Arm) else emptyList()
  }

  // --- Dosis ---

  private fun stableDose(grams: Double, nowMs: Long): List<PrepEffect> {
    val stable = pushReading(grams, nowMs, minG = minDoseWeight(), maxG = Double.MAX_VALUE) ?: return emptyList()
    return captureDose(round1(stable))
  }

  /** En el paso 3, antes de tarar: una nueva ventana estable entre el 50 % y el 150 % sustituye a la dosis. */
  private fun recapture(grams: Double, nowMs: Long): List<PrepEffect> {
    if (state.doseSkipped || state.waitingTare) return emptyList()
    val stable = pushReading(grams, nowMs, minG = minDoseWeight(), maxG = objectiveDoseG * config.recaptureMaxShare) ?: return emptyList()
    val dose = round1(stable)
    val current = state.doseG
    if (current != null && abs(dose - current) <= config.recaptureDeltaG) return emptyList()
    state = state.copy(doseG = dose)
    return listOf(PrepEffect.SetDose(dose))
  }

  private fun captureDose(dose: Double): List<PrepEffect> {
    enter(PrepStep.TARE_FOR_POUR, state.copy(doseG = dose, doseSkipped = false))
    return listOf(PrepEffect.SetDose(dose))
  }

  private fun minDoseWeight(): Double = maxOf(config.minDoseG, objectiveDoseG * config.autoCaptureMinShare)

  /**
   * Añade la lectura a la ventana y devuelve la media si ya hay una ventana estable (con el peso entre [minG] y
   * [maxG]); si no, `null` y actualiza el progreso.
   */
  private fun pushReading(grams: Double, nowMs: Long, minG: Double, maxG: Double): Double? {
    window.addLast(Reading(nowMs, grams))
    // Sale de la ventana lo que rompe la tolerancia (lo más antiguo primero).
    while (window.size > 1 && spread() > config.stableToleranceG) window.removeFirst()
    // Se conserva una lectura anterior al inicio de la ventana para poder medir su duración completa.
    while (window.size > 1 && window.elementAt(1).nowMs <= nowMs - config.stableWindowMs) window.removeFirst()
    val span = nowMs - window.first().nowMs
    val mean = window.map { it.grams }.average()
    val inRange = mean in minG..maxG
    val progress = if (inRange) (span.toFloat() / config.stableWindowMs).coerceIn(0f, 1f) else 0f
    if (progress != state.stableProgress) state = state.copy(stableProgress = progress)
    val stable = inRange && span >= config.stableWindowMs && window.size >= config.stableMinSamples
    return if (stable) mean else null
  }

  private fun spread(): Double {
    var min = Double.MAX_VALUE
    var max = -Double.MAX_VALUE
    for (r in window) {
      if (r.grams < min) min = r.grams
      if (r.grams > max) max = r.grams
    }
    return max - min
  }

  private fun publishProgress() {
    if (state.stableProgress != 0f) state = state.copy(stableProgress = 0f)
  }

  private fun enter(step: PrepStep, next: PrepState) {
    window.clear()
    zeroSinceMs = null
    sawWeight = false
    state = next.copy(step = step, stableProgress = 0f)
  }

  private fun reset() {
    window.clear()
    zeroSinceMs = null
    sawWeight = false
    tareSentMs = 0L
  }

  private fun round1(value: Double): Double = Math.round(value * 10.0) / 10.0
}
