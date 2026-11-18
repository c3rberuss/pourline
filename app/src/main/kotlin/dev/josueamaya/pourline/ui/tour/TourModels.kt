package dev.josueamaya.pourline.ui.tour

import androidx.annotation.StringRes
import dev.josueamaya.pourline.settings.ExperienceMode

/**
 * Un paso de un recorrido guiado (SPEC-008, §3.2).
 *
 * @property id único en toda la app: `"<recorrido>.<paso>"`, p. ej. `"prepare.scale"`. Es lo que se guarda como visto.
 * @property anchor id del ancla (`Modifier.tourAnchor`) que se resalta; `null` es un paso centrado, sin hueco.
 * @property expertOnly el paso solo se muestra en modo Experto.
 */
data class TourStep(
  val id: String,
  val anchor: String?,
  @param:StringRes val title: Int,
  @param:StringRes val body: Int,
  val expertOnly: Boolean = false,
)

/** Los pasos de una pantalla, en el orden en que se presentan. */
data class Tour(val id: String, val steps: List<TourStep>)

/**
 * Los pasos de [tour] que toca mostrar ahora, en orden: los que no están en [seen], los que no son de Experto salvo
 * que [mode] lo sea, y los que tienen su ancla en [anchors] o no tienen ancla.
 *
 * Un paso centrado no se muestra solo: si no queda ningún paso con ancla, la lista es vacía. Los ids de [seen] que
 * no son de [tour] no intervienen.
 */
fun pendingSteps(tour: Tour, seen: Set<String>, mode: ExperienceMode, anchors: Set<String>): List<TourStep> {
  val visible = tour.steps.filter { step ->
    step.id !in seen &&
      (!step.expertOnly || mode == ExperienceMode.EXPERT) &&
      (step.anchor == null || step.anchor in anchors)
  }
  return if (visible.any { it.anchor != null }) visible else emptyList()
}

/**
 * Un recorrido abierto: la lista de pasos de esa apertura ([steps]) y el paso en pantalla ([index]). El contador
 * "n/N" es [position]/[count] sobre esa lista, no sobre el recorrido completo.
 */
data class TourRun(val tourId: String, val steps: List<TourStep>, val index: Int = 0) {
  val current: TourStep get() = steps[index]

  /** Número del paso en pantalla, desde 1. */
  val position: Int get() = index + 1

  val count: Int get() = steps.size

  val isFirst: Boolean get() = index == 0

  val isLast: Boolean get() = index == steps.lastIndex
}

/** Lo que hace el usuario (o la pantalla) con un recorrido abierto. */
sealed interface TourAction {
  /** Siguiente, o Listo en el último paso. */
  data object Next : TourAction

  data object Prev : TourAction

  /** "Atrás" del sistema: retrocede un paso y, en el primero, salta el recorrido. */
  data object Back : TourAction

  /** Saltar: marca todos los pasos de esta apertura y cierra. */
  data object Skip : TourAction

  /** El ancla del paso actual desapareció: se descarta sin marcarlo. */
  data object AnchorLost : TourAction

  /** La pantalla cierra el recorrido (cuenta regresiva, vertido, otra pantalla): sin marcar nada. */
  data object Cancel : TourAction
}

/**
 * El resultado de una acción: el recorrido que sigue abierto ([run], `null` si se cierra), los ids que hay que marcar
 * como vistos ([markSeen]) y si hay que mostrar el aviso de "Ver recorrido" ([skippedHint]).
 */
data class TourTransition(
  val run: TourRun?,
  val markSeen: List<String> = emptyList(),
  val skippedHint: Boolean = false,
)

/** Aplica [action] a este recorrido abierto. */
fun TourRun.reduce(action: TourAction): TourTransition = when (action) {
  TourAction.Next ->
    if (isLast) TourTransition(null, listOf(current.id)) else TourTransition(copy(index = index + 1), listOf(current.id))

  TourAction.Prev -> TourTransition(if (isFirst) this else copy(index = index - 1))

  TourAction.Back -> reduce(if (isFirst) TourAction.Skip else TourAction.Prev)

  // Solo los pasos de esta apertura: los que no se veían (por modo o por ancla) ni otros recorridos no se tocan.
  TourAction.Skip -> TourTransition(null, steps.map { it.id }, skippedHint = true)

  TourAction.AnchorLost -> {
    val remaining = steps.filterIndexed { i, _ -> i != index }
    // El índice se mantiene: pasa a apuntar al paso siguiente. Si el perdido era el último, no queda ninguno.
    TourTransition(if (index < remaining.size) copy(steps = remaining) else null)
  }

  TourAction.Cancel -> TourTransition(null)
}

/**
 * Recupera un recorrido guardado (rotación o muerte del proceso): descarta los [ids] que ya no están en [tour] y
 * ajusta [index]. `null` si no queda ningún paso.
 */
fun restore(tour: Tour, ids: List<String>, index: Int): TourRun? {
  val steps = ids.mapNotNull { id -> tour.steps.firstOrNull { it.id == id } }
  if (steps.isEmpty()) return null
  return TourRun(tour.id, steps, index.coerceIn(0, steps.lastIndex))
}
