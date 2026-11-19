package dev.josueamaya.pourline.ui.prepare

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.josueamaya.pourline.R
import dev.josueamaya.pourline.scale.ConnectionStep
import dev.josueamaya.pourline.ui.components.AntAlert
import dev.josueamaya.pourline.ui.components.AntButton
import dev.josueamaya.pourline.ui.components.AntButtonType
import dev.josueamaya.pourline.ui.components.AntCard
import dev.josueamaya.pourline.ui.components.AntIconButton
import dev.josueamaya.pourline.ui.components.AntIcons
import dev.josueamaya.pourline.ui.components.AntLoadingIcon
import dev.josueamaya.pourline.ui.components.AntStatus
import dev.josueamaya.pourline.ui.components.AntText
import dev.josueamaya.pourline.ui.components.AntTextType
import dev.josueamaya.pourline.ui.scale.BatteryTag
import dev.josueamaya.pourline.ui.theme.AntSize
import dev.josueamaya.pourline.ui.theme.AntTheme

/** Las acciones de la tarjeta de báscula; cada estado usa las suyas. */
class ScaleCardActions(
  val onGrantPermissions: () -> Unit,
  val onEnableBluetooth: () -> Unit,
  /** Abre la hoja de conexión ("Conectar" sin báscula y "Cambiar báscula"). */
  val onOpenSheet: () -> Unit,
  /** "Conectar" con una báscula ya conocida. */
  val onConnect: () -> Unit,
  val onRetry: () -> Unit,
  val onDisconnect: () -> Unit,
)

/** La tarjeta de báscula de Preparar: un estado con su acción (§3.3 y D12). */
@Composable
fun ScaleCard(state: ScaleCardState, actions: ScaleCardActions, modifier: Modifier = Modifier) {
  AntCard(modifier.fillMaxWidth().testTag("scale_card")) {
    when (state) {
      // La simulada solo se ofrece en la hoja de conexión.
      ScaleCardState.NoPermission -> StatusRow(AntIcons.Bluetooth, stringResource(R.string.scale_card_no_permission)) {
        CardButton(R.string.scale_card_grant, "scale_card_grant", actions.onGrantPermissions)
      }
      ScaleCardState.BluetoothOff -> StatusRow(AntIcons.Bluetooth, stringResource(R.string.scale_card_bluetooth_off)) {
        CardButton(R.string.scale_card_enable, "scale_card_enable", actions.onEnableBluetooth)
      }
      ScaleCardState.NoScale -> StatusRow(AntIcons.Scale, stringResource(R.string.scale_card_connect_prompt)) {
        CardButton(R.string.scale_card_connect, "scale_card_connect", actions.onOpenSheet)
      }
      is ScaleCardState.Connecting -> {
        Row(horizontalArrangement = Arrangement.spacedBy(AntTheme.spacing.sm), verticalAlignment = Alignment.CenterVertically) {
          AntLoadingIcon()
          Column(Modifier.weight(1f)) {
            AntText(stringResource(R.string.scale_card_connecting, state.name))
            AntText(stringResource(state.step.label()), type = AntTextType.Secondary, modifier = Modifier.testTag("scale_card_step"))
          }
        }
      }
      is ScaleCardState.Connected -> Row(
        horizontalArrangement = Arrangement.spacedBy(AntTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        CardIcon(AntIcons.Scale)
        AntText(stringResource(R.string.scale_card_connected, state.name), Modifier.weight(1f))
        state.batteryPercent?.let { BatteryTag(it) }
        ConnectedMenu(onChange = actions.onOpenSheet, onDisconnect = actions.onDisconnect)
      }
      is ScaleCardState.Failed -> {
        AntAlert(message = stringResource(R.string.scale_card_failed, state.name), type = AntStatus.Warning)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AntTheme.spacing.xs)) {
          AntButton(
            text = stringResource(R.string.scale_card_retry),
            onClick = actions.onRetry,
            modifier = Modifier.testTag("scale_card_retry"),
            type = AntButtonType.Primary,
            size = AntSize.Small,
            icon = AntIcons.Reload,
          )
          CardButton(R.string.scale_card_change, "scale_card_change", actions.onOpenSheet, AntButtonType.Default)
        }
      }
      is ScaleCardState.Disconnected -> {
        StatusRow(AntIcons.Scale, stringResource(R.string.scale_card_disconnected, state.name)) {
          CardButton(R.string.scale_card_connect, "scale_card_connect", actions.onConnect)
        }
        AntButton(
          text = stringResource(R.string.scale_card_change),
          onClick = actions.onOpenSheet,
          modifier = Modifier.testTag("scale_card_change"),
          type = AntButtonType.Link,
          size = AntSize.Small,
        )
      }
    }
  }
}

@Composable
private fun CardIcon(icon: ImageVector) {
  Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp), tint = AntTheme.colors.textSecondary)
}

@Composable
private fun StatusRow(icon: ImageVector, text: String, action: @Composable () -> Unit) {
  Row(horizontalArrangement = Arrangement.spacedBy(AntTheme.spacing.xs), verticalAlignment = Alignment.CenterVertically) {
    CardIcon(icon)
    AntText(text, Modifier.weight(1f))
    action()
  }
}

@Composable
private fun CardButton(text: Int, tag: String, onClick: () -> Unit, type: AntButtonType = AntButtonType.Primary) {
  AntButton(stringResource(text), onClick, Modifier.testTag(tag), type = type, size = AntSize.Small)
}

@Composable
private fun ConnectedMenu(onChange: () -> Unit, onDisconnect: () -> Unit) {
  var open by remember { mutableStateOf(false) }
  Box {
    AntIconButton(
      AntIcons.More,
      contentDescription = stringResource(R.string.scale_card_menu),
      onClick = { open = true },
      modifier = Modifier.testTag("scale_card_menu"),
    )
    DropdownMenu(open, onDismissRequest = { open = false }, containerColor = AntTheme.colors.bgElevated) {
      DropdownMenuItem(
        text = { androidx.compose.material3.Text(stringResource(R.string.scale_card_disconnect), color = AntTheme.colors.text) },
        onClick = {
          open = false
          onDisconnect()
        },
        modifier = Modifier.testTag("scale_card_disconnect"),
      )
      DropdownMenuItem(
        text = { androidx.compose.material3.Text(stringResource(R.string.scale_card_change), color = AntTheme.colors.text) },
        onClick = {
          open = false
          onChange()
        },
        modifier = Modifier.testTag("scale_card_change"),
      )
    }
  }
}

private fun ConnectionStep.label(): Int = when (this) {
  ConnectionStep.CONNECTING -> R.string.conn_connecting
  ConnectionStep.DISCOVERING -> R.string.conn_discovering
  ConnectionStep.BONDING -> R.string.conn_bonding
  ConnectionStep.CONFIGURING -> R.string.conn_configuring
  ConnectionStep.HANDSHAKE -> R.string.conn_handshake
}
