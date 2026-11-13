package dev.dotscale.brew

import dev.dotscale.recipe.Recipe

data class TargetPoint(val tMs: Long, val waterG: Double)

data class TargetStage(
  /** Posición, desde 0. */
  val index: Int,
  val name: String?,
  val startMs: Long,
  /** Fin del vertido de la etapa. */
  val endMs: Long,
  /** Agua acumulada al terminar la etapa. */
  val targetWaterG: Double,
)

/**
 * Curva objetivo: agua acumulada en el tiempo, en gramos, relativa al t0 de la sesión.
 * Se construye desde una receta ([fromRecipe]) o desde una preparación guardada ([fromBrew]).
 */
data class TargetCurve(
  /** Ordenados por `tMs`, sin duplicados de `tMs`. */
  val points: List<TargetPoint>,
  /** Al menos una. */
  val stages: List<TargetStage>,
) {
  /** Fin del vertido de la última etapa. Es el "tiempo objetivo" de la métrica de ajuste. */
  val endMs: Long get() = stages.last().endMs

  /** Agua acumulada de la última etapa. */
  val finalWaterG: Double get() = stages.last().targetWaterG

  /** Interpolación lineal entre puntos. Antes del primero vale 0; después del último, el último valor. */
  fun waterAt(tMs: Long): Double {
    if (points.isEmpty() || tMs < points.first().tMs) return 0.0
    val last = points.last()
    if (tMs >= last.tMs) return last.waterG
    // Último índice con points[i].tMs <= tMs.
    var lo = 0
    var hi = points.size - 1
    while (lo < hi) {
      val mid = (lo + hi + 1) ushr 1
      if (points[mid].tMs <= tMs) lo = mid else hi = mid - 1
    }
    val a = points[lo]
    val b = points[lo + 1]
    return a.waterG + (b.waterG - a.waterG) * (tMs - a.tMs).toDouble() / (b.tMs - a.tMs)
  }

  /** Multiplica el agua de puntos y etapas por [factor]; los tiempos no cambian. */
  fun scaled(factor: Double): TargetCurve = TargetCurve(
    points = points.map { it.copy(waterG = it.waterG * factor) },
    stages = stages.map { it.copy(targetWaterG = it.targetWaterG * factor) },
  )

  companion object {
    /**
     * Una rampa durante cada vertido y una meseta durante cada espera. Si la primera etapa no empieza en
     * 0, se añade `(0, 0)`.
     */
    fun fromRecipe(recipe: Recipe): TargetCurve {
      val points = ArrayList<TargetPoint>()
      fun add(tMs: Long, waterG: Double) {
        if (points.isNotEmpty() && points.last().tMs == tMs) {
          points[points.lastIndex] = TargetPoint(tMs, waterG)
        } else {
          points += TargetPoint(tMs, waterG)
        }
      }
      if (recipe.stages.first().startMs > 0) add(0, 0.0)
      var previousWater = 0.0
      val stages = recipe.stages.mapIndexed { i, stage ->
        val endMs = stage.startMs + stage.durationMs
        add(stage.startMs, previousWater)
        add(endMs, stage.targetWaterG)
        previousWater = stage.targetWaterG
        TargetStage(i, stage.name, stage.startMs, endMs, stage.targetWaterG)
      }
      return TargetCurve(points, stages)
    }

    /**
     * La curva real de una preparación: las muestras válidas, una cada [minStepMs] como máximo (la última
     * de cada tramo). Las etapas salen de los vertidos, con el agua acumulada al final de cada uno
     * (`Pour.waterG` es el agua de ese vertido y no la acumulada). Sin vertidos, una única etapa de 0 a
     * [totalTimeMs]. Devuelve `null` si no hay muestras válidas.
     */
    fun fromBrew(
      samples: List<BrewSample>,
      pours: List<Pour>,
      totalTimeMs: Long,
      minStepMs: Long = TargetConfig().brewTargetMinStepMs,
    ): TargetCurve? {
      val points = ArrayList<TargetPoint>()
      var lastBucket = Long.MIN_VALUE
      for (sample in samples) {
        if (!sample.valid) continue
        val bucket = sample.tMs / minStepMs
        val point = TargetPoint(sample.tMs, sample.waterG)
        if (bucket == lastBucket) points[points.lastIndex] = point else points += point
        lastBucket = bucket
      }
      if (points.isEmpty()) return null
      val curve = TargetCurve(points, stages = listOf(TargetStage(0, null, 0, 0, 0.0)))
      val stages = if (pours.isEmpty()) {
        val endMs = maxOf(totalTimeMs, 0L)
        listOf(TargetStage(0, null, 0, endMs, curve.waterAt(endMs)))
      } else {
        pours.mapIndexed { i, pour -> TargetStage(i, null, pour.startMs, pour.endMs, curve.waterAt(pour.endMs)) }
      }
      return curve.copy(stages = stages)
    }
  }
}
