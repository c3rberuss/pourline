package dev.dotscale.brew

import dev.dotscale.recipe.Recipe
import dev.dotscale.recipe.StageType

data class TargetPoint(val tMs: Long, val waterG: Double)

data class TargetStage(
  /** Posición, desde 0. */
  val index: Int,
  val name: String?,
  val startMs: Long,
  /** Fin del vertido de la etapa. */
  val endMs: Long,
  /** Agua acumulada al terminar la etapa; en `SWIRL` y `WAIT`, la del vertido anterior. */
  val targetWaterG: Double,
  val type: StageType = StageType.POUR,
  val instruction: String? = null,
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
  /** Fin del último paso `POUR`. Es el "tiempo objetivo" de la métrica de ajuste. */
  val endMs: Long get() = stages.lastOrNull { it.type == StageType.POUR }?.endMs ?: stages.last().endMs

  /** Fin del último paso de cualquier tipo: cuando la guía ya no tiene nada que mostrar. */
  val lastStepEndMs: Long get() = stages.last().endMs

  /** Agua acumulada de la última etapa (la del último vertido, aunque la receta acabe en una espera). */
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
     * Una rampa durante cada vertido y una meseta en los huecos y durante los `SWIRL` y `WAIT`. Los puntos salen
     * solo de los pasos `POUR`; las etapas, de todos. Si el primer vertido no empieza en 0, se añade `(0, 0)`.
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
      val pours = recipe.stages.filter { it.type == StageType.POUR }
      if ((pours.firstOrNull()?.startMs ?: 0L) > 0L) add(0, 0.0)
      var previousWater = 0.0
      for (stage in pours) {
        add(stage.startMs, previousWater)
        add(stage.startMs + stage.durationMs, stage.targetWaterG)
        previousWater = stage.targetWaterG
      }
      var accumulated = 0.0
      val stages = recipe.stages.mapIndexed { i, stage ->
        if (stage.type == StageType.POUR) accumulated = stage.targetWaterG
        TargetStage(i, stage.name, stage.startMs, stage.startMs + stage.durationMs, accumulated, stage.type, stage.instruction)
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
