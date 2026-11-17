package dev.josueamaya.pourline.brew

import dev.josueamaya.pourline.protocol.ScaleMessage
import dev.josueamaya.pourline.protocol.TimerStatus
import dev.josueamaya.pourline.protocol.WeightUnit
import kotlin.math.roundToInt

/**
 * Báscula falsa que alimenta una [BrewSession]: genera un `ScaleMessage.Weight` cada 100 ms,
 * acumula el peso según el flujo y cuantiza como la báscula (×10 en g, ×100 en oz).
 *
 * También simula el temporizador: aplica los efectos `StartTimer`, `PauseTimer` y `ResetTimer` a
 * [timer] y devuelve a la sesión la notificación `Timer` correspondiente, como haría la báscula.
 */
class FakeScale(
  var unit: WeightUnit = WeightUnit.GRAMS,
  config: BrewConfig = BrewConfig(),
  startMs: Long = 10_000,
) {
  val session = BrewSession(config)
  var nowMs = startMs
    private set
  var timer = TimerStatus.RESET
  var grossG = 0.0
  private var tareG = 0.0
  /** Todos los efectos que ha producido la sesión, en orden. */
  val effects = mutableListOf<BrewEffect>()

  val state: BrewState get() = session.state
  val phase: BrewPhase get() = session.state.phase
  val finished: BrewPhase.Finished get() = phase as BrewPhase.Finished
  val startTimerCount: Int get() = effects.count { it == BrewEffect.StartTimer }
  val pauseTimerCount: Int get() = effects.count { it == BrewEffect.PauseTimer }
  val resetTimerCount: Int get() = effects.count { it == BrewEffect.ResetTimer }
  val finishedBrews: List<FinishedBrew> get() = effects.filterIsInstance<BrewEffect.Finished>().map { it.brew }

  fun clearEffects() = effects.clear()

  /** Registra los efectos y simula la respuesta del temporizador de la báscula. */
  private fun apply(produced: List<BrewEffect>): List<BrewEffect> {
    effects += produced
    for (effect in produced) {
      val status = when (effect) {
        BrewEffect.StartTimer -> TimerStatus.RUNNING
        BrewEffect.PauseTimer -> TimerStatus.PAUSED
        BrewEffect.ResetTimer -> TimerStatus.RESET
        is BrewEffect.Finished -> continue
      }
      timer = status
      apply(session.onTimer(status, nowMs))
    }
    return produced
  }

  /** Una notificación de peso, [STEP_MS] después de la anterior. */
  fun sample(flowGps: Double, outOfRange: Boolean = false, netOverrideG: Double? = null) {
    nowMs += STEP_MS
    grossG += flowGps * STEP_MS / 1000.0
    val netG = netOverrideG ?: (grossG - tareG)
    val factor = if (unit == WeightUnit.OUNCES) 1 / GRAMS_PER_OUNCE else 1.0
    val message = ScaleMessage.Weight(
      rawWeight = (netG * factor * unit.divisor).roundToInt(),
      rawFlow = (flowGps * factor * unit.divisor).roundToInt(),
      seconds = 0,
      unit = unit,
      outOfRange = outOfRange,
    )
    apply(session.onWeight(message, nowMs, timer))
  }

  fun pour(gps: Double, ms: Int) = repeat(ms / STEP_MS.toInt()) { sample(gps) }

  fun pourWith(ms: Int, flow: (index: Int) -> Double) = repeat(ms / STEP_MS.toInt()) { sample(flow(it)) }

  fun idle(ms: Int) = pour(0.0, ms)

  /** Pasa el tiempo sin que lleguen notificaciones. */
  fun advance(ms: Long) {
    nowMs += ms
  }

  /** La báscula avisa `Tared` antes de la primera muestra posterior a la tara. */
  fun tare() {
    tareG = grossG
    apply(session.onTared(nowMs))
  }

  fun timerEvent(status: TimerStatus) {
    timer = status
    apply(session.onTimer(status, nowMs))
  }

  fun tick(autoFinishMs: Long = 60_000) = apply(session.onTick(nowMs, autoFinishMs))

  fun finish() = apply(session.finish(nowMs))

  fun disconnect() = apply(session.onDisconnected(nowMs))

  companion object {
    const val STEP_MS = 100L
  }
}
