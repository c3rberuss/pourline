package dev.josueamaya.pourline.ui.tour

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Rect
import dev.josueamaya.pourline.settings.TourStore
import dev.josueamaya.pourline.ui.components.AntStatus
import dev.josueamaya.pourline.ui.components.showAntMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Lo que la capa del recorrido dibuja: el paso, su posición "[position]/[count]", los límites actuales del ancla
 * ([bounds], que se lee en dibujo o layout; `null` en un paso centrado) y qué hacer con cada botón ([onAction]).
 * [owner] es el [TourHost] que lo presenta.
 */
@Stable
class TourPresentation(
  val owner: Any,
  val step: TourStep,
  val position: Int,
  val count: Int,
  val bounds: () -> Rect?,
  val onAction: (TourAction) -> Unit,
)

/**
 * El único recorrido en pantalla (§3.5 del spec): quien lo presenta ([TourHost]), la capa que lo dibuja y el aviso de
 * Saltar. Lo crea [TourRoot]. [hint] da el texto del aviso en el idioma actual.
 */
@Stable
class TourCoordinator(
  val store: TourStore,
  private val scope: CoroutineScope,
  private val hint: () -> String,
) {
  /** El paso en pantalla; `null` si no hay recorrido. */
  var presentation by mutableStateOf<TourPresentation?>(null)
    private set

  /** Los avisos del recorrido ("Puedes volver a verlo en Ajustes > Ver recorrido"). */
  val messages = SnackbarHostState()

  /** Las anclas de la barra de pestañas, que no pertenecen a ninguna pantalla. */
  val barAnchors = TourAnchorRegistry()

  /** Presenta [p]; `false` si otro dueño ya está presentando. El mismo dueño puede cambiar de paso. */
  fun present(p: TourPresentation): Boolean {
    val current = presentation
    if (current != null && current.owner !== p.owner) return false
    presentation = p
    return true
  }

  /** Quita lo que presenta [owner]; si presenta otro, no hace nada. */
  fun dismiss(owner: Any) {
    if (presentation?.owner === owner) presentation = null
  }

  /** Muestra el aviso de que el recorrido se puede volver a ver. Va en el ámbito de la raíz: no se corta si la pantalla se va. */
  fun skippedHint() {
    scope.launch { messages.showAntMessage(hint(), AntStatus.Info) }
  }
}

/** El coordinador de la raíz; `null` fuera de [TourRoot], donde [TourHost] no hace nada. */
val LocalTourCoordinator = staticCompositionLocalOf<TourCoordinator?> { null }
