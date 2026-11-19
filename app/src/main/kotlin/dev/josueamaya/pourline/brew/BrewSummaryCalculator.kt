package dev.josueamaya.pourline.brew

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Resumen de una sesión terminada. Las métricas ponderadas dan a cada muestra el Δt respecto a la
 * anterior, con tope [BrewConfig.maxSampleGapMs]. El flujo medio solo cuenta las muestras válidas
 * dentro de un vertido (de su inicio al último instante sobre el umbral).
 */
fun summarize(
  samples: List<BrewSample>,
  pours: List<Pour>,
  totalTimeMs: Long,
  reason: FinishReason,
  doseG: Double?,
  config: BrewConfig = BrewConfig(),
): BrewSummary {
  var flowWeight = 0.0
  var flowSum = 0.0
  var scoreWeight = 0.0
  var scoreSum = 0.0
  val levelWeight = StabilityLevel.entries.associateWith { 0.0 }.toMutableMap()
  var maxFlow = 0.0
  var previousMs: Long? = null
  var pourIndex = 0

  for (sample in samples) {
    val dt = previousMs?.let { (sample.tMs - it).coerceIn(0, config.maxSampleGapMs).toDouble() } ?: 0.0
    previousMs = sample.tMs
    if (!sample.valid) continue
    maxFlow = max(maxFlow, sample.flowGps)

    while (pourIndex < pours.size && sample.tMs > pours[pourIndex].endMs) pourIndex++
    if (pourIndex < pours.size && sample.tMs >= pours[pourIndex].startMs) {
      flowWeight += dt
      flowSum += sample.flowGps * dt
    }

    sample.stability?.let { score ->
      scoreWeight += dt
      scoreSum += score * dt
      val level = config.levelOf(score)
      levelWeight[level] = levelWeight.getValue(level) + dt
    }
  }

  val score = if (scoreWeight > 0) (scoreSum / scoreWeight).roundToInt() else null
  val totalWater = samples.lastOrNull { it.valid }?.waterG?.coerceAtLeast(0.0) ?: 0.0
  return BrewSummary(
    totalTimeMs = totalTimeMs,
    totalWaterG = totalWater,
    avgFlowGps = if (flowWeight > 0) flowSum / flowWeight else 0.0,
    maxFlowGps = maxFlow,
    stabilityScore = score,
    stabilityLevel = score?.let(config::levelOf),
    levelShare = levelWeight.mapValues { (_, w) -> if (scoreWeight > 0) w / scoreWeight else 0.0 },
    pours = pours,
    finalRatio = doseG?.takeIf { it > 0 }?.let { totalWater / it },
    reason = reason,
    interrupted = reason == FinishReason.DISCONNECTED,
  )
}
