package dev.dotscale.ui.scale

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.dotscale.R
import dev.dotscale.brew.BrewPhase
import dev.dotscale.protocol.TimerStatus
import dev.dotscale.protocol.WeightUnit
import dev.dotscale.scale.ConnectionState
import dev.dotscale.scale.ConnectionStep
import dev.dotscale.sim.PourProfile
import dev.dotscale.settings.ThemeModeButton
import dev.dotscale.ui.components.AntAlert
import dev.dotscale.ui.components.AntBadge
import dev.dotscale.ui.components.AntBadgeStatus
import dev.dotscale.ui.components.AntButton
import dev.dotscale.ui.components.AntButtonShape
import dev.dotscale.ui.components.AntButtonType
import dev.dotscale.ui.components.AntCard
import dev.dotscale.ui.components.AntCardSize
import dev.dotscale.ui.components.AntDivider
import dev.dotscale.ui.components.AntIconButton
import dev.dotscale.ui.components.AntIcons
import dev.dotscale.ui.components.AntLoadingIcon
import dev.dotscale.ui.components.AntMessageHost
import dev.dotscale.ui.components.AntSegmented
import dev.dotscale.ui.components.AntSegmentedOption
import dev.dotscale.ui.components.AntStatistic
import dev.dotscale.ui.components.AntStatus
import dev.dotscale.ui.components.AntTag
import dev.dotscale.ui.components.AntTagColor
import dev.dotscale.ui.components.AntText
import dev.dotscale.ui.components.AntTextType
import dev.dotscale.ui.components.AntTopBar
import dev.dotscale.ui.components.AntVerticalDivider
import dev.dotscale.ui.components.showAntMessage
import dev.dotscale.ui.theme.AntSize
import dev.dotscale.ui.theme.AntTheme
import java.util.Locale

private const val LOW_BATTERY_PERCENT = 20

@Composable
fun ScaleScreen(viewModel: ScaleViewModel, title: String, onBack: () -> Unit, onOpenSettings: () -> Unit) {
  val ui by viewModel.ui.collectAsStateWithLifecycle()
  val snackbar = remember { SnackbarHostState() }

  LaunchedEffect(viewModel) { viewModel.errors.collect { snackbar.showAntMessage(it, AntStatus.Error) } }
  KeepScreenOn()

  val model = ui.model
  val firmware = ui.firmware
  val info = if (model != null && firmware != null) stringResource(R.string.scale_info, model.displayName, firmware) else null

  Scaffold(
    containerColor = AntTheme.colors.bgLayout,
    topBar = {
      AntTopBar(
        title = ui.deviceName ?: title,
        subtitle = info,
        navigationIcon = {
          AntIconButton(AntIcons.ArrowLeft, contentDescription = stringResource(R.string.scale_back), onClick = onBack)
        },
        actions = {
          ui.batteryPercent?.let { BatteryTag(it) }
          AntIconButton(AntIcons.Settings, contentDescription = stringResource(R.string.settings_open), onClick = onOpenSettings)
          ThemeModeButton()
        },
      )
    },
    snackbarHost = { AntMessageHost(snackbar) },
  ) { padding ->
    val enabled = ui.connection == ConnectionState.Ready
    Column(
      Modifier
        .fillMaxSize()
        .padding(padding)
        .verticalScroll(rememberScrollState())
        .padding(AntTheme.spacing.base),
      verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.sm),
    ) {
      ConnectionBanner(ui.connection, onRetry = viewModel::connect)
      WeightCard(ui)
      BrewControls(
        running = ui.timer == TimerStatus.RUNNING,
        enabled = enabled,
        onTare = viewModel::tare,
        onToggleTimer = viewModel::toggleTimer,
        onReset = viewModel::resetTimer,
      )
      BrewChartCard(ui.brew, ui.unit, onFinish = viewModel::finishBrew)
      (ui.brew.phase as? BrewPhase.Finished)?.let { BrewSummaryCard(it.summary, ui.unit) }
      DoseCard(ui, enabled, onCapture = viewModel::captureDose, onClear = viewModel::clearDose)
      UnitCard(ui.unit, enabled, onToggle = viewModel::toggleUnit)
      if (ui.isSimulated) SimulationCard(onAdd = viewModel::simulateAdd, onPour = viewModel::simulatePour)
    }
  }
}

@Composable
private fun BatteryTag(percent: Int) {
  val description = stringResource(R.string.scale_battery, percent)
  AntTag(
    text = stringResource(R.string.scale_battery_short, percent),
    modifier = Modifier
      .padding(end = AntTheme.spacing.xxs)
      .clearAndSetSemantics { contentDescription = description },
    color = if (percent <= LOW_BATTERY_PERCENT) AntTagColor.Error else AntTagColor.Default,
    icon = AntIcons.Battery,
  )
}

@Composable
private fun WeightCard(ui: ScaleUiState) {
  val colors = AntTheme.colors
  AntCard(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
    AntStatistic(
      value = formatNumber(ui.weight, ui.unit),
      title = stringResource(R.string.scale_weight),
      suffix = ui.unit.symbol,
      valueStyle = AntTheme.typography.heading1.copy(fontSize = 64.sp, lineHeight = 72.sp, fontWeight = FontWeight.Light),
      valueColor = if (ui.outOfRange) colors.error.text else colors.text,
      affixStyle = AntTheme.typography.textXL,
      horizontalAlignment = Alignment.CenterHorizontally,
    )
    val stage = ui.brewStage
    if (ui.outOfRange || stage != null) {
      Row(horizontalArrangement = Arrangement.spacedBy(AntTheme.spacing.xs)) {
        if (ui.outOfRange) AntTag(stringResource(R.string.scale_out_of_range), color = AntTagColor.Error)
        stage?.let { AntTag(it, color = AntTagColor.Primary) }
      }
    }
    AntDivider()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      AntStatistic(
        value = formatNumber(ui.flowRate, ui.unit),
        title = stringResource(R.string.scale_flow),
        suffix = stringResource(R.string.scale_flow_value, ui.unit.symbol),
        modifier = Modifier.weight(1f),
        horizontalAlignment = Alignment.CenterHorizontally,
      )
      AntVerticalDivider(height = 40.dp)
      AntStatistic(
        value = formatTime(ui.seconds),
        title = stringResource(R.string.scale_time),
        modifier = Modifier.weight(1f),
        horizontalAlignment = Alignment.CenterHorizontally,
        titleExtra = if (ui.timer == TimerStatus.RUNNING) {
          { AntBadge(AntBadgeStatus.Processing) }
        } else {
          null
        },
      )
    }
  }
}

@Composable
private fun BrewControls(
  running: Boolean,
  enabled: Boolean,
  onTare: () -> Unit,
  onToggleTimer: () -> Unit,
  onReset: () -> Unit,
) {
  Row(
    Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(AntTheme.spacing.xs),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    AntButton(
      text = stringResource(R.string.scale_tare),
      onClick = onTare,
      modifier = Modifier.weight(1f),
      type = AntButtonType.Primary,
      size = AntSize.Large,
      enabled = enabled,
    )
    AntButton(
      text = stringResource(if (running) R.string.scale_pause else R.string.scale_start),
      onClick = onToggleTimer,
      modifier = Modifier.weight(1f),
      size = AntSize.Large,
      enabled = enabled,
      icon = if (running) AntIcons.Pause else AntIcons.Play,
    )
    AntIconButton(
      icon = AntIcons.RotateLeft,
      contentDescription = stringResource(R.string.scale_reset),
      onClick = onReset,
      type = AntButtonType.Default,
      size = AntSize.Large,
      shape = AntButtonShape.Default,
      enabled = enabled,
    )
  }
}

@Composable
private fun DoseCard(ui: ScaleUiState, enabled: Boolean, onCapture: () -> Unit, onClear: () -> Unit) {
  val dose = ui.dose
  AntCard(
    modifier = Modifier.fillMaxWidth(),
    title = stringResource(R.string.scale_dose_title),
    extra = if (dose != null) {
      {
        AntButton(
          text = stringResource(R.string.scale_clear_dose),
          onClick = onClear,
          type = AntButtonType.Link,
          size = AntSize.Small,
          danger = true,
        )
      }
    } else {
      null
    },
  ) {
    if (dose == null) {
      AntText(stringResource(R.string.scale_dose_hint), type = AntTextType.Secondary)
      AntButton(
        text = stringResource(R.string.scale_capture_dose),
        onClick = onCapture,
        modifier = Modifier.fillMaxWidth(),
        type = AntButtonType.Primary,
        enabled = enabled && ui.weight > 0,
        icon = AntIcons.Plus,
      )
    } else {
      Row(Modifier.fillMaxWidth()) {
        AntStatistic(
          value = formatNumber(dose, ui.unit),
          title = stringResource(R.string.scale_dose),
          suffix = ui.unit.symbol,
          modifier = Modifier.weight(1f),
        )
        AntStatistic(
          value = ui.ratio?.let { stringResource(R.string.scale_ratio_value, it) } ?: stringResource(R.string.scale_ratio_empty),
          title = stringResource(R.string.scale_ratio),
          modifier = Modifier.weight(1f),
          valueColor = AntTheme.colors.primary.base,
        )
      }
    }
  }
}

@Composable
private fun UnitCard(unit: WeightUnit, enabled: Boolean, onToggle: () -> Unit) {
  AntCard(
    Modifier.fillMaxWidth(),
    contentPadding = PaddingValues(horizontal = AntTheme.spacing.base, vertical = AntTheme.spacing.xxs),
  ) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      AntText(stringResource(R.string.scale_unit_label), Modifier.weight(1f))
      AntSegmented(
        options = WeightUnit.entries.map { AntSegmentedOption(it, it.symbol) },
        selected = unit,
        onSelect = { if (it != unit) onToggle() },
        modifier = Modifier.width(160.dp),
        enabled = enabled,
      )
    }
  }
}

@Composable
private fun ConnectionBanner(state: ConnectionState, onRetry: () -> Unit) {
  val retry: @Composable () -> Unit = {
    AntButton(text = stringResource(R.string.scale_retry), onClick = onRetry, size = AntSize.Small, icon = AntIcons.Reload)
  }
  when (state) {
    ConnectionState.Ready -> Unit
    ConnectionState.Disconnected ->
      AntAlert(message = stringResource(R.string.conn_disconnected), type = AntStatus.Warning, action = retry)
    is ConnectionState.Failed ->
      AntAlert(message = stringResource(R.string.conn_failed, state.reason), type = AntStatus.Error, action = retry)
    is ConnectionState.Connecting -> {
      val step = when (state.step) {
        ConnectionStep.CONNECTING -> R.string.conn_connecting
        ConnectionStep.DISCOVERING -> R.string.conn_discovering
        ConnectionStep.BONDING -> R.string.conn_bonding
        ConnectionStep.CONFIGURING -> R.string.conn_configuring
        ConnectionStep.HANDSHAKE -> R.string.conn_handshake
      }
      val infoColor = AntTheme.colors.info.base
      AntAlert(message = stringResource(step), type = AntStatus.Info, icon = { AntLoadingIcon(color = infoColor) })
    }
  }
}

@Composable
private fun SimulationCard(onAdd: (Double) -> Unit, onPour: (PourProfile) -> Unit) {
  AntCard(Modifier.fillMaxWidth(), title = stringResource(R.string.scale_simulation), size = AntCardSize.Small) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AntTheme.spacing.xs)) {
      listOf(15, 100, 300).forEach { grams ->
        AntButton(
          text = stringResource(R.string.scale_sim_add, grams),
          onClick = { onAdd(grams.toDouble()) },
          modifier = Modifier.weight(1f),
          type = AntButtonType.Dashed,
          size = AntSize.Small,
        )
      }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AntTheme.spacing.xs)) {
      AntButton(
        text = stringResource(R.string.sim_pour_stable),
        onClick = { onPour(PourProfile.STABLE) },
        modifier = Modifier.weight(1f),
        type = AntButtonType.Dashed,
        size = AntSize.Small,
      )
      AntButton(
        text = stringResource(R.string.sim_pour_irregular),
        onClick = { onPour(PourProfile.IRREGULAR) },
        modifier = Modifier.weight(1f),
        type = AntButtonType.Dashed,
        size = AntSize.Small,
      )
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

private fun formatTime(seconds: Int): String = String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60)
