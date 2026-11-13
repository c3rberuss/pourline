package dev.dotscale.brew

import kotlin.math.abs
import kotlin.math.roundToInt

data class BrewFit(
  /** 0-100; `null` si no se puede calcular. */
  val score: Int?,
  /** `finalWaterG` de la curva escalada. */
  val targetWaterG: Double,
  /** `endMs` de la curva. */
  val targetEndMs: Long,
) {
  fun waterDiffG(totalWaterG: Double) = totalWaterG - targetWaterG

  fun waterDiffShare(totalWaterG: Double) = waterDiffG(totalWaterG) / targetWaterG

  fun timeDiffMs(totalTimeMs: Long) = totalTimeMs - targetEndMs
}

/**
 * Cuánto se ajustó una preparación a la curva objetivo.
 *
 * Ventana `W = max(totalTimeMs, target.endMs)` y rejilla de [TargetConfig.fitGridMs]. El agua real en
 * `t` es la de la última muestra válida con `tMs ≤ min(t, totalTimeMs)` (0 si no hay ninguna). La
 * puntuación es `round(100 × clamp(1 − MAE / (fitZeroErrorShare × finalWaterG), 0, 1))`. Sin muestras
 * válidas, `score = null`.
 */
fun brewFit(samples: List<BrewSample>, totalTimeMs: Long, target: TargetCurve, config: TargetConfig): BrewFit {
  val fit = BrewFit(score = null, targetWaterG = target.finalWaterG, targetEndMs = target.endMs)
  val valid = samples.filter { it.valid }
  val zeroError = config.fitZeroErrorShare * target.finalWaterG
  if (valid.isEmpty() || zeroError <= 0.0 || config.fitGridMs <= 0) return fit

  val window = maxOf(totalTimeMs, target.endMs)
  var next = 0
  var actual = 0.0
  var errorSum = 0.0
  var count = 0
  var t = 0L
  while (t <= window) {
    val limit = minOf(t, totalTimeMs)
    while (next < valid.size && valid[next].tMs <= limit) actual = valid[next++].waterG
    errorSum += abs(actual - target.waterAt(t))
    count++
    t += config.fitGridMs
  }
  val mae = errorSum / count
  val score = (100.0 * (1.0 - mae / zeroError).coerceIn(0.0, 1.0)).roundToInt()
  return fit.copy(score = score)
}
