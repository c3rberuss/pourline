package dev.josueamaya.pourline.ui.session

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import dev.josueamaya.pourline.R
import dev.josueamaya.pourline.brew.GuideState
import dev.josueamaya.pourline.brew.TargetCurve
import dev.josueamaya.pourline.brew.TargetStage
import dev.josueamaya.pourline.recipe.StageType
import dev.josueamaya.pourline.ui.components.AntIcons
import java.util.Locale

/** `m:ss` a partir de milisegundos (se trunca al segundo). Es el formato del timer de la sesión (D25). */
fun formatClock(ms: Long): String {
  val seconds = (ms / 1000).coerceAtLeast(0)
  return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60)
}

/**
 * `m:ss` de un tiempo que falta, redondeado hacia arriba al segundo: así cambia a la vez que el cronómetro (que trunca)
 * y enlaza sin repetir ni saltarse un segundo con la cuenta atrás de `GuideState.countdownS` (que también redondea
 * hacia arriba). Con 5,4 s por delante dice "0:06", como el cronómetro que aún no ha llegado al segundo siguiente.
 */
fun formatRemaining(ms: Long): String = formatClock(ceilToSecond(ms))

private fun ceilToSecond(ms: Long): Long = if (ms <= 0) 0 else (ms + 999) / 1000 * 1000

@StringRes
fun stepTypeLabel(type: StageType): Int = when (type) {
  StageType.POUR -> R.string.step_type_pour
  StageType.SWIRL -> R.string.step_type_swirl
  StageType.WAIT -> R.string.step_type_wait
}

fun stepTypeIcon(type: StageType): ImageVector = when (type) {
  StageType.POUR -> AntIcons.Pour
  StageType.SWIRL -> AntIcons.Swirl
  StageType.WAIT -> AntIcons.Wait
}

/** Los últimos segundos antes del siguiente paso en que la cuenta atrás sale en grande (§3.4, `GuideState.countdownS`). */
const val COUNTDOWN_SECONDS = 5

/**
 * Lo que dice "Siguiente: {tipo} {nombre} en {tiempo}" (D6): el paso, el tiempo que falta y, en los últimos 5 s, la
 * cuenta atrás de `GuideState.countdownS`.
 */
data class NextStepInfo(
  val stage: TargetStage,
  /** `startMs − elapsedMs`, sin bajar de 0. */
  val remainingMs: Long,
  /** 5..1 en los últimos 5 s; `null` fuera. */
  val countdownS: Int?,
) {
  /** El tiempo se resalta (color primario y negrita) solo dentro de la cuenta atrás. */
  val emphasized: Boolean get() = countdownS != null

  /** El nombre del paso, si lo tiene. */
  val name: String? get() = stage.name?.takeIf { it.isNotBlank() }
}

/** `null` sin siguiente paso (el último paso en curso o ya completada la receta). */
fun nextStepInfo(next: TargetStage?, elapsedMs: Long, countdownS: Int?): NextStepInfo? =
  next?.let { NextStepInfo(it, (it.startMs - elapsedMs).coerceAtLeast(0), countdownS) }

/** El tiempo de "Siguiente … en": la cuenta atrás ("5 s" es un texto traducido, por eso lo da el composable) o `m:ss`. */
fun nextStepClock(info: NextStepInfo): String = formatRemaining(info.remainingMs)

/** El paso en curso de una sesión sin báscula (SPEC-007 §3.4): "paso n/N · quedan m:ss" y el avance del dial. */
data class TimedStepView(
  /** Desde 1. */
  val number: Int,
  val count: Int,
  /** `endMs − tMs`, sin bajar de 0. */
  val remainingMs: Long,
  /** Avance 0..1 del paso; 1 si dura 0. */
  val fraction: Float,
)

/** `null` sin guía, antes del primer paso o con todos los pasos hechos. */
fun timedStepView(guide: GuideState?, curve: TargetCurve?, tMs: Long?): TimedStepView? {
  val stage = guide?.stage ?: return null
  if (guide.allStagesDone || curve == null || tMs == null) return null
  val duration = stage.endMs - stage.startMs
  val fraction = if (duration <= 0) 1f else ((tMs - stage.startMs).toFloat() / duration).coerceIn(0f, 1f)
  return TimedStepView(stage.index + 1, curve.stages.size, (stage.endMs - tMs).coerceAtLeast(0), fraction)
}
