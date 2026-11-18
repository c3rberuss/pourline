package dev.josueamaya.pourline.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Cuántas hojas inferiores y diálogos hay abiertos ahora mismo. Los componentes que abren una ventana propia
 * ([AntDrawer], [AntModal], [AntChoiceModal] y el diálogo de [AntDatePicker]) lo avisan con [ReportModalPresence], y
 * el recorrido guiado no empieza mientras [open] sea mayor que 0.
 */
@Stable
class ModalPresence {
  var open by mutableIntStateOf(0)
}

/** Quien lo provee (la raíz del recorrido) recibe los avisos; sin proveedor (tests sueltos, previews) no pasa nada. */
val LocalModalPresence = staticCompositionLocalOf<ModalPresence?> { null }

/** Suma una hoja o un diálogo abierto mientras este componible esté en la composición. */
@Composable
fun ReportModalPresence() {
  val presence = LocalModalPresence.current ?: return
  DisposableEffect(presence) {
    presence.open += 1
    onDispose { presence.open -= 1 }
  }
}
