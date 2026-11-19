package dev.josueamaya.pourline.ui.tour

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalWindowInfo
import dev.josueamaya.pourline.settings.LocalExperienceMode
import dev.josueamaya.pourline.ui.components.LocalModalPresence
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Cuánto sin altas ni bajas de anclas se espera antes de evaluar un recorrido, y cuánto debe faltar un ancla para perder su paso (D9). */
const val TOUR_STABLE_MS = 300L

/** Lo que [TourScreen] da a [TourHost]: el registro de anclas de la entrada y si es la pantalla visible. */
@Stable
class TourScreenState(val anchors: TourAnchorRegistry, val visible: Boolean)

val LocalTourScreen = staticCompositionLocalOf<TourScreenState?> { null }

/**
 * Envuelve el contenido de una entrada de la navegación: le da su propio registro de anclas y dice si es la pantalla
 * visible (la cima de la pila de la pestaña activa) (D20). Sin este envoltorio, el recorrido no hace nada.
 */
@Composable
fun TourScreen(visible: Boolean, content: @Composable () -> Unit) {
  val anchors = remember { TourAnchorRegistry() }
  val state = remember(anchors, visible) { TourScreenState(anchors, visible) }
  CompositionLocalProvider(LocalTourAnchors provides anchors, LocalTourScreen provides state, content = content)
}

/**
 * Espera a que pasen [TOUR_STABLE_MS] sin altas ni bajas de anclas en ningún registro. Los cambios de límites no
 * cuentan: un elemento que se mueve no debe retrasar el recorrido sin fin.
 */
@OptIn(FlowPreview::class)
suspend fun awaitStableAnchors(vararg registries: TourAnchorRegistry) {
  snapshotFlow { registries.map { it.ids } }.debounce(TOUR_STABLE_MS).first()
}

/**
 * Presenta el recorrido [tour] de la pantalla en la que se compone, una vez por apertura (D21).
 *
 * Espera a que la pantalla sea la visible, a que estén [ready] y [enabled], a que no haya una hoja ni un modal abiertos,
 * a que la ventana tenga el foco, a que nadie más presente y a que las anclas se estabilicen. Entonces calcula los pasos
 * pendientes y los presenta uno a uno: revela el ancla, la valida y espera la acción del usuario o la pérdida del ancla.
 * Si [enabled] o la visibilidad se pierden, cierra el recorrido sin marcar nada (D24).
 *
 * No hace nada fuera de [TourScreen] y [TourRoot].
 */
@Composable
fun TourHost(tour: Tour?, ready: Boolean = true, enabled: Boolean = true) {
  val screen = LocalTourScreen.current ?: return
  val coordinator = LocalTourCoordinator.current ?: return
  val mode = LocalExperienceMode.current
  val modals = LocalModalPresence.current
  val windowInfo = LocalWindowInfo.current
  val tourId = tour?.id
  val canRun = tour != null && screen.visible && ready && enabled

  val owner = remember { Any() }
  // Lo que sobrevive a una rotación o a la muerte del proceso: los pasos de esta apertura y el que está en pantalla.
  // Los ids van en una cadena separada por comas (ningún id lleva comas): un `ArrayList` en un `MutableState` es un aviso de lint.
  var savedIds by rememberSaveable(tourId) { mutableStateOf<String?>(null) }
  var savedIndex by rememberSaveable(tourId) { mutableIntStateOf(0) }
  // Ya se evaluó en esta apertura: una apertura nueva es una composición nueva de la entrada.
  val evaluated = remember(tourId) { booleanArrayOf(false) }

  val latestMode by rememberUpdatedState(mode)

  LaunchedEffect(tourId, canRun) {
    coordinator.dismiss(owner)
    if (tour == null || !canRun) {
      // D24: cerrar sin marcar y olvidar lo guardado.
      savedIds = null
      return@LaunchedEffect
    }

    fun canStart(): Boolean =
      (modals?.open ?: 0) == 0 && windowInfo.isWindowFocused && coordinator.presentation == null

    // Condiciones de inicio y estabilización: se repite si cambian mientras se espera.
    while (true) {
      snapshotFlow { canStart() }.first { it }
      awaitStableAnchors(screen.anchors, coordinator.barAnchors)
      if (canStart()) break
    }

    fun registryOf(anchor: String): TourAnchorRegistry? =
      listOf(screen.anchors, coordinator.barAnchors).firstOrNull { anchor in it.ids }

    var run: TourRun? = savedIds?.let { restore(tour, it.split(','), savedIndex) }
    if (run == null && !evaluated[0]) {
      val anchors = screen.anchors.ids + coordinator.barAnchors.ids
      val pending = pendingSteps(tour, coordinator.store.seen.value, latestMode, anchors)
      if (pending.isNotEmpty()) run = TourRun(tour.id, pending)
    }
    evaluated[0] = true

    val actions = Channel<Pair<Int, TourAction>>(Channel.UNLIMITED)
    var generation = 0
    try {
      while (run != null) {
        val current: TourRun = run
        savedIds = current.steps.joinToString(",") { it.id }
        savedIndex = current.index
        val step = current.current

        // Revelar el ancla y comprobar que tiene límites válidos; si no, el paso se pierde sin marcarse.
        var lost = false
        if (step.anchor != null) {
          val registry = registryOf(step.anchor)
          val bounds = registry?.reveal(step.anchor)
          val root = windowInfo.containerSize
          lost = bounds == null || bounds.width <= 0f || bounds.height <= 0f ||
            !bounds.overlaps(Rect(0f, 0f, root.width.toFloat(), root.height.toFloat()))
        }
        if (lost) {
          run = apply(current.reduce(TourAction.AnchorLost), coordinator)
          continue
        }

        val id = ++generation
        val anchorId = step.anchor
        val presented = coordinator.present(
          TourPresentation(
            owner = owner,
            step = step,
            position = current.position,
            count = current.count,
            bounds = { anchorId?.let { screen.anchors.bounds(it) ?: coordinator.barAnchors.bounds(it) } },
            onAction = { action -> actions.trySend(id to action) },
          ),
        )
        if (!presented) break

        // Esperar una acción del usuario; si el ancla falta durante TOUR_STABLE_MS seguidos, es AnchorLost (D22).
        val action = coroutineScope {
          val watcher = if (anchorId != null) {
            launch {
              snapshotFlow { anchorId in screen.anchors.ids || anchorId in coordinator.barAnchors.ids }.collectLatest { present ->
                if (!present) {
                  delay(TOUR_STABLE_MS)
                  actions.trySend(id to TourAction.AnchorLost)
                }
              }
            }
          } else {
            null
          }
          // Las acciones de un paso anterior (un doble toque) se descartan.
          var received: TourAction? = null
          while (received == null) {
            val (generationOf, candidate) = actions.receive()
            if (generationOf == id) received = candidate
          }
          watcher?.cancel()
          received
        }
        run = apply(current.reduce(action), coordinator)
      }
    } finally {
      coordinator.dismiss(owner)
    }
    if (run == null) savedIds = null
  }

  // El estado guardado es de esta composición: si el recorrido se cierra solo, se olvida aquí también.
  DisposableEffect(owner) { onDispose { coordinator.dismiss(owner) } }
}

/** Aplica el efecto de una transición (marcar vistos, avisar) y devuelve el recorrido que sigue abierto. */
private fun apply(transition: TourTransition, coordinator: TourCoordinator): TourRun? {
  if (transition.markSeen.isNotEmpty()) coordinator.store.markSeen(transition.markSeen)
  if (transition.skippedHint) coordinator.skippedHint()
  return transition.run
}
