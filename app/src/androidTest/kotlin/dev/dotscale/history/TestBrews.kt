package dev.dotscale.history

import dev.dotscale.brew.BrewSample
import dev.dotscale.brew.FinishReason
import dev.dotscale.brew.FinishedBrew
import dev.dotscale.brew.Pour
import dev.dotscale.brew.summarize
import dev.dotscale.protocol.ScaleModel
import dev.dotscale.protocol.WeightUnit
import java.util.Random

/** Preparaciones sintéticas y deterministas para los tests instrumentados. */
object TestBrews {

  /**
   * Una preparación de [totalMs] con tres vertidos (de 3 g/s y ruido de ±0,05 g, para no sobrestimar la
   * compresión). Las puntuaciones oscilan entre 60 y 95 salvo en la rampa de cada vertido.
   */
  fun finished(
    totalMs: Long = 240_000,
    seed: Long = 1,
    doseG: Double? = 20.0,
    reason: FinishReason = FinishReason.MANUAL,
    scored: Boolean = true,
    t0MonoMs: Long = 0,
  ): FinishedBrew {
    val random = Random(seed)
    val stepMs = 100L
    val pourLengthMs = totalMs / 5
    val starts = listOf(0L, totalMs * 2 / 5, totalMs * 3 / 5 + 5_000)
    val samples = ArrayList<BrewSample>()
    val pours = ArrayList<Pour>()
    var water = 0.0
    var pourStartWater = 0.0
    var t = 0L
    while (t <= totalMs) {
      val pourIndex = starts.indexOfFirst { t >= it && t < it + pourLengthMs }
      val pouring = pourIndex >= 0
      if (pouring && t == starts[pourIndex]) pourStartWater = water
      val flow = if (pouring) 3.0 + random.nextGaussian() * 0.2 else 0.0
      water += flow * stepMs / 1000.0
      val stability = if (scored && pouring && t - starts[pourIndex] >= 1_000) 60 + random.nextInt(36) else null
      samples += BrewSample(t, water + random.nextGaussian() * 0.05, flow, stability, valid = true)
      if (pouring && t + stepMs >= starts[pourIndex] + pourLengthMs) {
        pours += Pour(pours.size + 1, starts[pourIndex], t, water - pourStartWater)
      }
      t += stepMs
    }
    val summary = summarize(samples, pours, totalMs, reason, doseG)
    return FinishedBrew(t0MonoMs, samples, summary, doseG)
  }

  fun context(
    startedAtEpochMs: Long = 1_700_000_000_000,
    unit: WeightUnit = WeightUnit.GRAMS,
    simulated: Boolean = false,
    model: ScaleModel? = ScaleModel.DOT,
  ) = BrewContext(startedAtEpochMs, unit, model, simulated)
}
