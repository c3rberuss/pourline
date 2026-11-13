package dev.dotscale.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import dev.dotscale.ui.theme.AntSize
import dev.dotscale.ui.theme.AntTheme

/** Datos de un `message` de antd que viajan por la cola de un [SnackbarHostState]. */
class AntMessageVisuals(
  override val message: String,
  val type: AntStatus = AntStatus.Info,
  override val duration: SnackbarDuration = SnackbarDuration.Short,
  override val actionLabel: String? = null,
) : SnackbarVisuals {
  override val withDismissAction: Boolean = false
}

/**
 * Equivalente a `message.success/info/warning/error(...)`. Con [actionLabel] muestra un botón de acción
 * (p. ej. "Deshacer") y el resultado dice si se pulsó (`ActionPerformed`) o expiró (`Dismissed`).
 */
suspend fun SnackbarHostState.showAntMessage(
  message: String,
  type: AntStatus = AntStatus.Info,
  actionLabel: String? = null,
  duration: SnackbarDuration = SnackbarDuration.Short,
): SnackbarResult = showSnackbar(AntMessageVisuals(message, type, duration, actionLabel))

/** Host para `Scaffold(snackbarHost = …)` que pinta los mensajes con el estilo de antd. */
@Composable
fun AntMessageHost(hostState: SnackbarHostState, modifier: Modifier = Modifier) {
  SnackbarHost(hostState, modifier) { data ->
    val type = (data.visuals as? AntMessageVisuals)?.type ?: AntStatus.Info
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
      AntMessage(
        data.visuals.message,
        type,
        action = data.visuals.actionLabel?.let { label ->
          { AntButton(label, onClick = { data.performAction() }, type = AntButtonType.Link, size = AntSize.Small) }
        },
      )
    }
  }
}

/** Píldora flotante de `message`: icono de estado y texto sobre `colorBgElevated`, con una [action] opcional. */
@Composable
fun AntMessage(text: String, type: AntStatus, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
  val colors = AntTheme.colors
  val shape = RoundedCornerShape(AntTheme.radius.lg)
  Row(
    modifier
      .padding(AntTheme.spacing.base)
      .shadow(6.dp, shape)
      .background(colors.bgElevated, shape)
      .padding(horizontal = AntTheme.spacing.sm, vertical = 10.dp),
    horizontalArrangement = Arrangement.spacedBy(AntTheme.spacing.xs),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    AntStatusIcon(type)
    Text(text, color = colors.text, style = AntTheme.typography.text)
    action?.invoke()
  }
}
