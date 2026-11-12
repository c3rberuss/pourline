package dev.dotscale.ui.scale

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.dotscale.R
import dev.dotscale.protocol.TimerStatus
import dev.dotscale.protocol.WeightUnit
import dev.dotscale.scale.ConnectionState
import dev.dotscale.scale.ConnectionStep
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScaleScreen(viewModel: ScaleViewModel, title: String, onBack: () -> Unit) {
  val ui by viewModel.ui.collectAsStateWithLifecycle()
  val snackbar = remember { SnackbarHostState() }

  LaunchedEffect(viewModel) { viewModel.errors.collect { snackbar.showSnackbar(it) } }
  KeepScreenOn()

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(ui.deviceName ?: title) },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.scale_back))
          }
        },
        actions = {
          ui.batteryPercent?.let {
            Text(stringResource(R.string.scale_battery, it), Modifier.padding(end = 16.dp))
          }
        },
      )
    },
    snackbarHost = { SnackbarHost(snackbar) },
  ) { padding ->
    Column(
      Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      ConnectionBanner(ui.connection, onRetry = viewModel::connect)

      WeightDisplay(ui)

      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        Metric(stringResource(R.string.scale_flow), stringResource(R.string.scale_flow_value, formatAmount(ui.flowRate, ui.unit)))
        Metric(stringResource(R.string.scale_time), formatTime(ui.seconds))
      }

      val enabled = ui.connection == ConnectionState.Ready
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = viewModel::tare, enabled = enabled, modifier = Modifier.weight(1f)) {
          Text(stringResource(R.string.scale_tare))
        }
        FilledTonalButton(onClick = viewModel::toggleTimer, enabled = enabled, modifier = Modifier.weight(1f)) {
          Text(stringResource(if (ui.timer == TimerStatus.RUNNING) R.string.scale_pause else R.string.scale_start))
        }
        OutlinedButton(onClick = viewModel::resetTimer, enabled = enabled, modifier = Modifier.weight(1f)) {
          Text(stringResource(R.string.scale_reset))
        }
      }

      DoseCard(ui, enabled, onCapture = viewModel::captureDose, onClear = viewModel::clearDose)

      val otherUnit = if (ui.unit == WeightUnit.GRAMS) WeightUnit.OUNCES else WeightUnit.GRAMS
      OutlinedButton(onClick = viewModel::toggleUnit, enabled = enabled) {
        Text(stringResource(R.string.scale_unit, otherUnit.symbol))
      }

      ui.brewStage?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
      if (ui.model != null && ui.firmware != null) {
        Text(
          stringResource(R.string.scale_info, ui.model!!.displayName, ui.firmware!!),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      if (ui.isSimulated) SimulationControls(onAdd = viewModel::simulateAdd)
    }
  }
}

@Composable
private fun WeightDisplay(ui: ScaleUiState) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text(
      text = formatAmount(ui.weight, ui.unit),
      fontSize = 64.sp,
      fontWeight = FontWeight.Light,
    )
    if (ui.outOfRange) {
      Text(stringResource(R.string.scale_out_of_range), color = MaterialTheme.colorScheme.error)
    }
  }
}

@Composable
private fun Metric(label: String, value: String) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(value, style = MaterialTheme.typography.headlineSmall)
  }
}

@Composable
private fun DoseCard(ui: ScaleUiState, enabled: Boolean, onCapture: () -> Unit, onClear: () -> Unit) {
  Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors()) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      val dose = ui.dose
      if (dose == null) {
        Text(stringResource(R.string.scale_dose_hint), style = MaterialTheme.typography.bodyMedium)
        Button(onClick = onCapture, enabled = enabled && ui.weight > 0) {
          Text(stringResource(R.string.scale_capture_dose))
        }
      } else {
        val doseText = formatAmount(dose, ui.unit)
        Text(stringResource(R.string.scale_dose, doseText), style = MaterialTheme.typography.titleMedium)
        ui.ratio?.let { Text(stringResource(R.string.scale_ratio, it), style = MaterialTheme.typography.headlineMedium) }
        OutlinedButton(onClick = onClear) { Text(stringResource(R.string.scale_clear_dose)) }
      }
    }
  }
}

@Composable
private fun ConnectionBanner(state: ConnectionState, onRetry: () -> Unit) {
  val text = when (state) {
    ConnectionState.Ready -> return
    ConnectionState.Disconnected -> stringResource(R.string.conn_disconnected)
    is ConnectionState.Failed -> stringResource(R.string.conn_failed, state.reason)
    is ConnectionState.Connecting -> stringResource(
      when (state.step) {
        ConnectionStep.CONNECTING -> R.string.conn_connecting
        ConnectionStep.DISCOVERING -> R.string.conn_discovering
        ConnectionStep.BONDING -> R.string.conn_bonding
        ConnectionStep.CONFIGURING -> R.string.conn_configuring
        ConnectionStep.HANDSHAKE -> R.string.conn_handshake
      },
    )
  }
  Card(
    Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
  ) {
    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
      Text(text, Modifier.weight(1f))
      if (state is ConnectionState.Failed || state == ConnectionState.Disconnected) {
        OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.scale_retry)) }
      }
    }
  }
}

@Composable
private fun SimulationControls(onAdd: (Double) -> Unit) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Spacer(Modifier.height(8.dp))
    Text(stringResource(R.string.scale_simulation), style = MaterialTheme.typography.labelLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      listOf(15, 100, 300).forEach { grams ->
        AssistChip(onClick = { onAdd(grams.toDouble()) }, label = { Text(stringResource(R.string.scale_sim_add, grams)) })
      }
    }
  }
}

@Composable
private fun KeepScreenOn() {
  val view = LocalView.current
  DisposableEffect(view) {
    view.keepScreenOn = true
    onDispose { view.keepScreenOn = false }
  }
}

/** Gramos con 1 decimal y onzas con 2 (resolución de la báscula). Evita mostrar "-0,0". */
private fun formatAmount(value: Double, unit: WeightUnit): String {
  val decimals = if (unit == WeightUnit.OUNCES) 2 else 1
  val shown = if (abs(value) < 0.5 / unit.divisor) 0.0 else value
  return String.format(Locale.getDefault(), "%.${decimals}f %s", shown, unit.symbol)
}

private fun formatTime(seconds: Int): String = String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60)
