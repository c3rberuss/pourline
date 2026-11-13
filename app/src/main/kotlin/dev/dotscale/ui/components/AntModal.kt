package dev.dotscale.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.window.Dialog
import dev.dotscale.ui.theme.AntTheme

/**
 * `Modal.confirm` de Ant Design: diálogo con título, texto y los botones [cancelText] y [confirmText].
 * Con [danger] el botón de confirmar usa el color de error. Cerrar fuera del diálogo equivale a cancelar.
 */
@Composable
fun AntModal(
  title: String,
  text: String,
  confirmText: String,
  cancelText: String,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
  danger: Boolean = false,
) {
  Dialog(onDismissRequest = onDismiss) {
    AntCard(modifier.testTag("ant_modal"), title = title) {
      Text(text, color = AntTheme.colors.textSecondary, style = AntTheme.typography.text)
      Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AntTheme.spacing.xs, Alignment.End),
      ) {
        AntButton(cancelText, onDismiss, modifier = Modifier.testTag("ant_modal_cancel"))
        AntButton(confirmText, onConfirm, modifier = Modifier.testTag("ant_modal_confirm"), type = AntButtonType.Primary, danger = danger)
      }
    }
  }
}
