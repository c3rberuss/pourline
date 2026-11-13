package dev.dotscale.brew

import kotlin.math.abs
import kotlin.math.ceil

enum class DeviationLevel { OK, WARNING, ERROR }

data class GuideState(
  /** Etapa en curso: desde su `startMs` hasta el `startMs` de la siguiente. `null` antes de una primera etapa que no empieza en 0. */
  val stage: TargetStage?,
  val nextStage: TargetStage?,
  /** 5..1 en los últimos `TargetConfig.countdownMs` antes de `nextStage.startMs`; `null` fuera. */
  val countdownS: Int?,
  /** Agua actual − `waterAt(tMs)`; `null` fuera de `Active` o sin muestra válida reciente. */
  val deviationG: Double?,
  val deviationLevel: DeviationLevel?,
  /** `tMs ≥ target.endMs`: ya no queda ninguna etapa por hacer. */
  val allStagesDone: Boolean = false,
)

object TargetGuide {
  /** Tolerancia con la que los límites del 5 % y el 10 % son inclusivos. */
  private const val EPSILON = 1e-9

  private val Empty = GuideState(null, null, null, null, null)

  /**
   * - `Armed`: la primera etapa, para anunciar qué hay que hacer.
   * - `Active`: la etapa que corresponde a [tMs] (el reloj es fijo: avanza aunque el usuario vaya atrasado).
   * - `Finished`: todo `null`.
   */
  fun at(target: TargetCurve, phase: BrewPhase, tMs: Long?, waterG: Double?, config: TargetConfig): GuideState = when (phase) {
    is BrewPhase.Finished -> Empty
    is BrewPhase.Armed -> armed(target)
    is BrewPhase.Active -> if (tMs == null) armed(target) else active(target, tMs, waterG, config)
  }

  private fun armed(target: TargetCurve) = Empty.copy(stage = target.stages.first(), nextStage = target.stages.getOrNull(1))

  private fun active(target: TargetCurve, tMs: Long, waterG: Double?, config: TargetConfig): GuideState {
    val current = target.stages.indexOfLast { it.startMs <= tMs }
    val next = target.stages.getOrNull(current + 1)
    val remaining = next?.let { it.startMs - tMs }
    val countdown = remaining?.takeIf { it in 1..config.countdownMs }?.let { ceil(it / 1000.0).toInt() }
    val deviation = waterG?.let { it - target.waterAt(tMs) }
    return GuideState(
      stage = target.stages.getOrNull(current),
      nextStage = next,
      countdownS = countdown,
      deviationG = deviation,
      deviationLevel = deviation?.let { level(it, target.finalWaterG, config) },
      allStagesDone = tMs >= target.endMs,
    )
  }

  /** Niveles en valor absoluto y relativos a `finalWaterG`, con los límites inclusivos. */
  internal fun level(deviationG: Double, finalWaterG: Double, config: TargetConfig): DeviationLevel {
    if (finalWaterG <= 0.0) return DeviationLevel.OK
    val share = abs(deviationG) / finalWaterG
    return when {
      share <= config.deviationWarnShare + EPSILON -> DeviationLevel.OK
      share <= config.deviationErrorShare + EPSILON -> DeviationLevel.WARNING
      else -> DeviationLevel.ERROR
    }
  }
}
