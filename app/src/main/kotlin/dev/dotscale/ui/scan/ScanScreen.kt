package dev.dotscale.ui.scan

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.dotscale.R
import dev.dotscale.ble.ScaleScanner
import dev.dotscale.ble.ScannedDevice

private val BLUETOOTH_PERMISSIONS: Array<String> =
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
  } else {
    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
  }

private fun Context.hasBluetoothPermissions(): Boolean =
  BLUETOOTH_PERMISSIONS.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
  onDeviceSelected: (address: String, name: String) -> Unit,
  onSimulatedSelected: () -> Unit,
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
    topBar = {
      TopAppBar(
        title = { Text(stringResource(R.string.scan_title)) },
        actions = {
          if (hasPermissions) {
            TextButton(onClick = { if (ui.scanning) viewModel.stop() else viewModel.start() }) {
              Text(stringResource(if (ui.scanning) R.string.scan_stop else R.string.scan_start))
            }
          }
        },
      )
    },
  ) { padding ->
    Column(Modifier.fillMaxSize().padding(padding)) {
      if (ui.scanning) LinearProgressIndicator(Modifier.fillMaxWidth())

      when {
        !hasPermissions -> Message(stringResource(R.string.scan_permissions_needed)) {
          Button(onClick = { permissionLauncher.launch(BLUETOOTH_PERMISSIONS) }) {
            Text(stringResource(R.string.scan_grant_permissions))
          }
        }
        !viewModel.isBluetoothEnabled -> Message(stringResource(R.string.scan_bluetooth_off)) {
          OutlinedButton(onClick = viewModel::start) { Text(stringResource(R.string.scan_start)) }
        }
        else -> {
          Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(stringResource(R.string.scan_show_all), Modifier.weight(1f))
            Switch(checked = ui.showAll, onCheckedChange = viewModel::setShowAll)
          }
          ui.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))
          }
          LazyColumn(Modifier.weight(1f)) {
            items(ui.devices, key = { it.address }) { device ->
              val unknown = stringResource(R.string.scan_unknown_device)
              DeviceRow(device, unknown) { onDeviceSelected(device.address, device.name ?: unknown) }
              HorizontalDivider()
            }
            if (ui.devices.isEmpty()) {
              item {
                Text(
                  stringResource(R.string.scan_empty),
                  modifier = Modifier.fillMaxWidth().padding(32.dp),
                  textAlign = TextAlign.Center,
                  style = MaterialTheme.typography.bodyMedium,
                )
              }
            }
          }
        }
      }

      OutlinedButton(
        onClick = onSimulatedSelected,
        modifier = Modifier.fillMaxWidth().padding(16.dp),
      ) {
        Text(stringResource(R.string.scan_simulated))
      }
    }
  }
}

@Composable
private fun DeviceRow(device: ScannedDevice, unknownName: String, onClick: () -> Unit) {
  ListItem(
    modifier = Modifier.clickable(onClick = onClick),
    headlineContent = { Text(device.name ?: unknownName) },
    supportingContent = {
      Text(listOfNotNull(device.model?.displayName, device.address).joinToString(" · "))
    },
    trailingContent = { Text(stringResource(R.string.scan_rssi, device.rssi)) },
  )
}

@Composable
private fun Message(text: String, action: @Composable () -> Unit) {
  Column(
    Modifier.fillMaxWidth().padding(32.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Text(text, textAlign = TextAlign.Center)
    action()
  }
}
