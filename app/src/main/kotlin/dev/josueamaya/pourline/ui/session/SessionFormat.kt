package dev.josueamaya.pourline.ui.session

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import dev.josueamaya.pourline.R
import dev.josueamaya.pourline.brew.TargetStage
import dev.josueamaya.pourline.recipe.StageType
import dev.josueamaya.pourline.ui.components.AntIcons
import java.util.Locale

/** `m:ss` a partir de milisegundos (se trunca al segundo). Es el formato del timer de la sesión (D25). */
fun formatClock(ms: Long): String {
  val seconds = (ms / 1000).coerceAtLeast(0)
  return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60)
}

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
fun nextStepClock(info: NextStepInfo): String = formatClock(info.remainingMs)
