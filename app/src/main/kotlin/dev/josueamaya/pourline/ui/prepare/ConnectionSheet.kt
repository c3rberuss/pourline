package dev.josueamaya.pourline.ui.prepare

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.josueamaya.pourline.R
import dev.josueamaya.pourline.ble.DeviceScanner
import dev.josueamaya.pourline.ble.ScannedDevice
import dev.josueamaya.pourline.ui.components.AntAlert
import dev.josueamaya.pourline.ui.components.AntBadge
import dev.josueamaya.pourline.ui.components.AntBadgeStatus
import dev.josueamaya.pourline.ui.components.AntButton
import dev.josueamaya.pourline.ui.components.AntButtonType
import dev.josueamaya.pourline.ui.components.AntDivider
import dev.josueamaya.pourline.ui.components.AntDrawer
import dev.josueamaya.pourline.ui.components.AntEmpty
import dev.josueamaya.pourline.ui.components.AntIcons
import dev.josueamaya.pourline.ui.components.AntListItem
import dev.josueamaya.pourline.ui.components.AntStatus
import dev.josueamaya.pourline.ui.components.AntSwitch
import dev.josueamaya.pourline.ui.components.AntSwitchSize
import dev.josueamaya.pourline.ui.components.AntText
import dev.josueamaya.pourline.ui.components.AntTextType
import dev.josueamaya.pourline.ui.theme.AntSize
import dev.josueamaya.pourline.ui.theme.AntTheme

private val DevicesMaxHeight = 320.dp

/**
 * La hoja "Conectar báscula": busca al abrirse y deja de buscar al cerrarse. Elegir un dispositivo llama a
 * [onDevice] y "Usar báscula simulada" a [onSimulated]; quien la use cierra la hoja en los dos casos.
 */
@Composable
fun ConnectionSheet(
  scanner: DeviceScanner,
  onDevice: (address: String, name: String) -> Unit,
  onSimulated: () -> Unit,
  onClose: () -> Unit,
) {
  val viewModel: ScanViewModel = viewModel { ScanViewModel(scanner) }
  val ui by viewModel.ui.collectAsStateWithLifecycle()
  LaunchedEffect(viewModel) { viewModel.start() }
  DisposableEffect(viewModel) { onDispose { viewModel.stop() } }

  AntDrawer(title = stringResource(R.string.connect_title), onClose = onClose, modifier = Modifier.testTag("connection_sheet")) {
    Devices(
      ui = ui,
      onToggleScan = { if (ui.scanning) viewModel.stop() else viewModel.start() },
      onShowAllChange = viewModel::setShowAll,
      onDevice = onDevice,
    )
    AntDivider()
    AntButton(
      text = stringResource(R.string.connect_simulated),
      onClick = onSimulated,
      modifier = Modifier.fillMaxWidth().testTag("connect_simulated"),
      type = AntButtonType.Dashed,
      size = AntSize.Large,
      icon = AntIcons.Play,
    )
  }
}

@Composable
private fun Devices(
  ui: ScanUiState,
  onToggleScan: () -> Unit,
  onShowAllChange: (Boolean) -> Unit,
  onDevice: (address: String, name: String) -> Unit,
) {
  val spacing = AntTheme.spacing
  Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    AntButton(
      text = stringResource(if (ui.scanning) R.string.scan_stop else R.string.scan_start),
      onClick = onToggleScan,
      modifier = Modifier.testTag("connect_scan_toggle"),
      type = AntButtonType.Link,
      size = AntSize.Small,
      icon = if (ui.scanning) null else AntIcons.Reload,
    )
    AntBadge(
      status = if (ui.scanning) AntBadgeStatus.Processing else AntBadgeStatus.Default,
      text = stringResource(if (ui.scanning) R.string.scan_scanning else R.string.scan_idle),
      modifier = Modifier.weight(1f).padding(start = spacing.xs),
    )
    Row(
      Modifier
        .heightIn(min = 48.dp)
        .toggleable(value = ui.showAll, role = Role.Switch, onValueChange = onShowAllChange)
        .padding(horizontal = spacing.xs),
      horizontalArrangement = Arrangement.spacedBy(spacing.xs),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      AntText(stringResource(R.string.scan_show_all), type = AntTextType.Secondary)
      AntSwitch(checked = ui.showAll, onCheckedChange = null, size = AntSwitchSize.Small)
    }
  }
  ui.error?.let { AntAlert(message = it, type = AntStatus.Error) }
  LazyColumn(Modifier.fillMaxWidth().heightIn(max = DevicesMaxHeight)) {
    items(ui.devices, key = { it.address }) { device ->
      val unknown = stringResource(R.string.scan_unknown_device)
      DeviceRow(device, unknown) { onDevice(device.address, device.name ?: unknown) }
    }
    if (ui.devices.isEmpty()) {
      item { AntEmpty(description = stringResource(R.string.scan_empty)) }
    }
  }
}

@Composable
private fun DeviceRow(device: ScannedDevice, unknownName: String, onClick: () -> Unit) {
  val colors = AntTheme.colors
  AntListItem(
    title = device.name ?: unknownName,
    description = listOfNotNull(device.model?.displayName, device.address).joinToString(" · "),
    avatar = {
      Box(
        Modifier.size(40.dp).background(colors.primary.bg, CircleShape),
        contentAlignment = Alignment.Center,
      ) {
        Icon(AntIcons.Bluetooth, contentDescription = null, modifier = Modifier.size(20.dp), tint = colors.primary.base)
      }
    },
    extra = {
      AntText(
        stringResource(R.string.scan_rssi, device.rssi),
        type = AntTextType.Secondary,
        style = AntTheme.typography.textSM,
      )
    },
    onClick = onClick,
  )
}
