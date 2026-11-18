package dev.josueamaya.pourline.ui.tour

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.withFrameNanos

/** Máximo de desplazamientos del 80 % del alto de una `LazyColumn` al buscar un ancla que no está compuesta (D10). */
const val MAX_REVEAL_STEPS = 30

/**
 * Las anclas de una pantalla: los elementos que un recorrido puede resaltar, con sus límites en la raíz. Hay un
 * registro por entrada de la navegación (dos pantallas de la pila no comparten anclas) y otro para la barra de
 * pestañas.
 *
 * [ids] solo cambia con las altas y bajas; los límites van en un estado aparte que solo leen el dibujo y el layout de la
 * capa del recorrido, así que las pantallas no se recomponen cuando algo se mueve.
 */
@Stable
class TourAnchorRegistry {
  private val attached: SnapshotStateMap<String, TourAnchorNode> = mutableStateMapOf()
  private val declared: SnapshotStateMap<String, LazyDeclaration> = mutableStateMapOf()

  /** Los ids presentes: los elementos compuestos y los que una lista perezosa declaró con [TourLazyAnchor]. */
  val ids: Set<String> get() = attached.keys + declared.keys

  /** Los límites del ancla [id] en la raíz, o `null` si no está compuesta. Se lee en dibujo o layout, no en composición. */
  fun bounds(id: String): Rect? = attached[id]?.bounds

  internal fun isAttached(id: String): Boolean = id in attached

  internal fun attach(id: String, node: TourAnchorNode) {
    attached[id] = node
  }

  /** Solo borra si el nodo registrado sigue siendo [node]: con dos nodos del mismo id gana el último. */
  internal fun detach(id: String, node: TourAnchorNode) {
    if (attached[id] === node) attached.remove(id)
  }

  internal fun declare(id: String, declaration: LazyDeclaration) {
    declared[id] = declaration
  }

  internal fun undeclare(id: String, declaration: LazyDeclaration) {
    if (declared[id] === declaration) declared.remove(id)
  }

  /**
   * Deja el ancla [id] a la vista y devuelve sus límites, o `null` si no se encuentra. Un elemento compuesto se revela
   * con `bringIntoView`. Uno de una lista perezosa que no está compuesto se busca desplazando la lista desde arriba,
   * un 80 % del alto cada vez, hasta [MAX_REVEAL_STEPS] veces.
   */
  internal suspend fun reveal(id: String): Rect? {
    if (id !in attached) {
      val state = declared[id]?.state ?: return null
      state.scrollToItem(0)
      withFrame()
      var steps = 0
      while (id !in attached && state.canScrollForward && steps < MAX_REVEAL_STEPS) {
        state.scrollBy(state.layoutInfo.viewportSize.height * 0.8f)
        withFrame()
        steps++
      }
    }
    attached[id]?.reveal() ?: return null
    // Un fotograma para que el desplazamiento coloque el elemento antes de leer sus límites.
    withFrame()
    return bounds(id)
  }

  private suspend fun withFrame() {
    withFrameNanos { }
  }
}

/** Un ancla que una lista perezosa dice tener aunque su elemento no esté compuesto (D10). */
internal class LazyDeclaration(val state: LazyListState)

/** El registro de la entrada de navegación (o de la barra) en la que se compone el elemento; `null` fuera del recorrido. */
val LocalTourAnchors = staticCompositionLocalOf<TourAnchorRegistry?> { null }

/**
 * Marca este elemento como el ancla [id] de un recorrido: registra sus límites en el [TourAnchorRegistry] de la
 * pantalla y se da de baja al salir de la composición. El id es el `testTag` del elemento. Con [enabled] en `false`, o
 * sin registro (una pantalla suelta, una vista previa), no hace nada.
 */
fun Modifier.tourAnchor(id: String, enabled: Boolean = true): Modifier {
  if (!enabled) return this
  // `bringIntoView` no está disponible sobre un nodo de modificador en esta versión de Compose: cada ancla lleva su requester.
  val requester = BringIntoViewRequester()
  return this.bringIntoViewRequester(requester).then(TourAnchorElement(id, requester))
}

private data class TourAnchorElement(val id: String, val requester: BringIntoViewRequester) : ModifierNodeElement<TourAnchorNode>() {
  override fun create() = TourAnchorNode(id, requester)

  override fun update(node: TourAnchorNode) = node.update(id, requester)

  override fun InspectorInfo.inspectableProperties() {
    name = "tourAnchor"
    properties["id"] = id
  }
}

internal class TourAnchorNode(private var id: String, private var requester: BringIntoViewRequester) :
  Modifier.Node(),
  GlobalPositionAwareModifierNode,
  CompositionLocalConsumerModifierNode {
  var bounds by mutableStateOf<Rect?>(null)
    private set

  private var registry: TourAnchorRegistry? = null

  // El registro es el de la entrada y no cambia mientras el nodo está colocado: leerlo al adjuntar es lo que se quiere.
  @Suppress("SuspiciousCompositionLocalModifierRead")
  override fun onAttach() {
    registry = currentValueOf(LocalTourAnchors)
    registry?.attach(id, this)
  }

  override fun onDetach() {
    registry?.detach(id, this)
    registry = null
    bounds = null
  }

  override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
    if (!coordinates.isAttached) return
    val next = coordinates.boundsInRoot()
    if (next != bounds) bounds = next
  }

  fun update(newId: String, newRequester: BringIntoViewRequester) {
    requester = newRequester
    if (newId == id) return
    registry?.detach(id, this)
    id = newId
    registry?.attach(id, this)
  }

  suspend fun reveal() {
    if (!isAttached) return
    // El requester puede no tener aún su nodo si el ancla se acaba de componer.
    try {
      requester.bringIntoView()
    } catch (_: IllegalStateException) {
    }
  }
}

/**
 * Declara el ancla [id] de un elemento de una `LazyColumn` mientras [present] sea cierto, aunque el elemento no esté
 * compuesto (una lista perezosa solo compone lo que se ve). El recorrido la busca desplazando [state]. El elemento
 * sigue poniendo su `Modifier.tourAnchor(id)`.
 */
@Composable
fun TourLazyAnchor(id: String, state: LazyListState, present: Boolean) {
  val registry = LocalTourAnchors.current ?: return
  if (!present) return
  DisposableEffect(registry, id, state) {
    val declaration = LazyDeclaration(state)
    registry.declare(id, declaration)
    onDispose { registry.undeclare(id, declaration) }
  }
}
