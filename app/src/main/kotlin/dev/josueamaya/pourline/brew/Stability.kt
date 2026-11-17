package dev.josueamaya.pourline.brew

import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Puntuación 0-100 de lo regular que es el flujo: `CV = σ / μ` (σ poblacional) y
 * `round(100 × (1 − min(CV / cvZeroScore, 1)))`. Con media ≤ 0 o sin datos devuelve 0.
 */
fun stabilityScore(flows: List<Double>, cvZeroScore: Double): Int {
  if (flows.isEmpty()) return 0
  val mean = flows.average()
  if (mean <= 0.0) return 0
  val variance = flows.sumOf { (it - mean) * (it - mean) } / flows.size
  val cv = sqrt(variance) / mean
  return (100 * (1 - min(cv / cvZeroScore, 1.0))).roundToInt()
}
