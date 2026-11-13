package dev.dotscale.ui.scan

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.dotscale.R
import dev.dotscale.ble.ScaleScanner
import dev.dotscale.ble.ScannedDevice
import dev.dotscale.settings.ThemeModeButton
import dev.dotscale.ui.components.AntAlert
import dev.dotscale.ui.components.AntBadge
import dev.dotscale.ui.components.AntBadgeStatus
import dev.dotscale.ui.components.AntButton
import dev.dotscale.ui.components.AntButtonType
import dev.dotscale.ui.components.AntCard
import dev.dotscale.ui.components.AntDivider
import dev.dotscale.ui.components.AntEmpty
import dev.dotscale.ui.components.AntIconButton
import dev.dotscale.ui.components.AntIcons
import dev.dotscale.ui.components.AntListItem
import dev.dotscale.ui.components.AntStatus
import dev.dotscale.ui.components.AntSwitch
import dev.dotscale.ui.components.AntSwitchSize
import dev.dotscale.ui.components.AntText
import dev.dotscale.ui.components.AntTextType
import dev.dotscale.ui.components.AntTopBar
import dev.dotscale.ui.theme.AntSize
import dev.dotscale.ui.theme.AntTheme

private val BLUETOOTH_PERMISSIONS: Array<String> =
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
  } else {
    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
  }

private fun Context.hasBluetoothPermissions(): Boolean =
  BLUETOOTH_PERMISSIONS.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }

@Composable
fun ScanScreen(
  onDeviceSelected: (address: String, name: String) -> Unit,
  onSimulatedSelected: () -> Unit,
  onOpenSettings: () -> Unit,
  onOpenHistory: () -> Unit,
  onOpenRecipes: () -> Unit,
) {
  val context = LocalContext.current
  val appContext = context.applicationContext
  val viewModel: ScanViewModel = viewModel { ScanViewModel(ScaleScanner(appContext)) }
  val ui by viewModel.ui.collectAsStateWithLifecycle()
  var hasPermissions by remember { mutableStateOf(context.hasBluetoothPermissions()) }

  val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
    hasPermissions = context.hasBluetoothPermissions()
    if (hasPermissions) viewModel.start()
  }

  LaunchedEffect(Unit) {
    if (hasPermissions) viewModel.start() else permissionLauncher.launch(BLUETOOTH_PERMISSIONS)
  }

  Scaffold(
    containerColor = AntTheme.colors.bgLayout,
    topBar = {
      AntTopBar(
        title = stringResource(R.string.scan_title),
        actions = {
          AntIconButton(AntIcons.Recipe, contentDescription = stringResource(R.string.recipe_open), onClick = onOpenRecipes)
          AntIconButton(AntIcons.History, contentDescription = stringResource(R.string.history_open), onClick = onOpenHistory)
          AntIconButton(AntIcons.Settings, contentDescription = stringResource(R.string.settings_open), onClick = onOpenSettings)
          ThemeModeButton()
        },
      )
    },
  ) { padding ->
    Column(
      Modifier.fillMaxSize().padding(padding).padding(AntTheme.spacing.base),
      verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.sm),
    ) {
      when {
        !hasPermissions -> {
          AntAlert(
            message = stringResource(R.string.scan_permissions_title),
            description = stringResource(R.string.scan_permissions_needed),
            type = AntStatus.Warning,
            action = {
              AntButton(
                text = stringResource(R.string.scan_grant_permissions),
                onClick = { permissionLauncher.launch(BLUETOOTH_PERMISSIONS) },
                type = AntButtonType.Primary,
              )
            },
          )
          Spacer(Modifier.weight(1f))
        }
        !viewModel.isBluetoothEnabled -> {
          AntAlert(
            message = stringResource(R.string.scan_bluetooth_off_title),
            description = stringResource(R.string.scan_bluetooth_off),
            type = AntStatus.Warning,
            action = { AntButton(text = stringResource(R.string.scan_start), onClick = viewModel::start) },
          )
          Spacer(Modifier.weight(1f))
        }
        else -> DevicesCard(
          ui = ui,
          onToggleScan = { if (ui.scanning) viewModel.stop() else viewModel.start() },
          onShowAllChange = viewModel::setShowAll,
          onDeviceSelected = onDeviceSelected,
          modifier = Modifier.weight(1f),
        )
      }

      AntButton(
        text = stringResource(R.string.scan_simulated),
        onClick = onSimulatedSelected,
        modifier = Modifier.fillMaxWidth(),
        type = AntButtonType.Dashed,
        size = AntSize.Large,
        icon = AntIcons.Play,
      )
    }
  }
}

@Composable
private fun DevicesCard(
  ui: ScanUiState,
  onToggleScan: () -> Unit,
  onShowAllChange: (Boolean) -> Unit,
  onDeviceSelected: (address: String, name: String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val spacing = AntTheme.spacing
  AntCard(
    modifier = modifier.fillMaxWidth(),
    title = stringResource(R.string.scan_devices),
    extra = {
      AntButton(
        text = stringResource(if (ui.scanning) R.string.scan_stop else R.string.scan_start),
        onClick = onToggleScan,
        type = AntButtonType.Link,
        size = AntSize.Small,
        icon = if (ui.scanning) null else AntIcons.Reload,
      )
    },
    contentPadding = PaddingValues(0.dp),
    verticalArrangement = Arrangement.Top,
  ) {
    Row(
      Modifier.fillMaxWidth().padding(start = spacing.base, end = spacing.xs),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      AntBadge(
        status = if (ui.scanning) AntBadgeStatus.Processing else AntBadgeStatus.Default,
        text = stringResource(if (ui.scanning) R.string.scan_scanning else R.string.scan_idle),
        modifier = Modifier.weight(1f),
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
    ui.error?.let { AntAlert(message = it, type = AntStatus.Error, banner = true) }
    AntDivider()
    LazyColumn(Modifier.weight(1f)) {
      items(ui.devices, key = { it.address }) { device ->
        val unknown = stringResource(R.string.scan_unknown_device)
        DeviceRow(device, unknown) { onDeviceSelected(device.address, device.name ?: unknown) }
      }
      if (ui.devices.isEmpty()) {
        item { AntEmpty(description = stringResource(R.string.scan_empty)) }
      }
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
