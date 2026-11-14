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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.dotscale.R
import dev.dotscale.brew.BrewPhase
import dev.dotscale.brew.BrewSummary
import dev.dotscale.coffee.brewDaysSinceRoast
import dev.dotscale.protocol.TimerStatus
import dev.dotscale.protocol.WeightUnit
import dev.dotscale.scale.ConnectionState
import dev.dotscale.scale.ConnectionStep
import dev.dotscale.session.ScaleError
import dev.dotscale.session.ScaleSessionManager
import dev.dotscale.session.ScaleUiState
import dev.dotscale.settings.ExpertFeature
import dev.dotscale.settings.LocalExperienceMode
import dev.dotscale.share.BrewShareData
import dev.dotscale.share.FooterCoffee
import dev.dotscale.share.ShareContent
import dev.dotscale.share.ShareSender
import dev.dotscale.share.ShareTarget
import dev.dotscale.share.footerLines
import dev.dotscale.share.rememberShareLauncher
import dev.dotscale.share.rememberShareSender
import dev.dotscale.sim.FollowMode
import dev.dotscale.sim.PourProfile
import dev.dotscale.ui.coffees.CoffeePicker
import dev.dotscale.ui.coffees.CoffeePickerViewModel
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
import dev.dotscale.ui.history.formatBrewDateTime
import dev.dotscale.ui.theme.AntSize
import dev.dotscale.ui.theme.AntTheme
import java.time.ZoneId
import java.util.Locale

private const val LOW_BATTERY_PERCENT = 20
private const val LINE_HEIGHT_RATIO = 1.125f

@Composable
fun ScaleScreen(
  session: ScaleSessionManager,
  pickerViewModel: TargetPickerViewModel,
  title: String,
  onBack: () -> Unit,
  onAddDetails: (Long) -> Unit,
  onManageRecipes: () -> Unit,
  coffeePickerViewModel: CoffeePickerViewModel? = null,
  onNewCoffee: () -> Unit = {},
  shareSender: ShareSender? = null,
) {
  LaunchedEffect(session) { session.arm() }
  val ui by session.ui.collectAsStateWithLifecycle()
  val snackbar = remember { SnackbarHostState() }
  val launcher = rememberShareLauncher(shareSender ?: rememberShareSender(), snackbar)
  var pickerOpen by rememberSaveable { mutableStateOf(false) }
  var coffeePickerOpen by rememberSaveable { mutableStateOf(false) }

  val commandError = stringResource(R.string.scale_error_command)
  val saveError = stringResource(R.string.scale_error_save)
  val removedError = stringResource(R.string.target_error_removed)
  val unreadableError = stringResource(R.string.target_error_unreadable)
  LaunchedEffect(session) {
    session.errors.collect { error ->
      val text = when (error) {
        is ScaleError.Command -> error.detail ?: commandError
        ScaleError.SaveFailed -> saveError
        ScaleError.TargetRemoved -> removedError
        ScaleError.TargetUnreadable -> unreadableError
      }
      snackbar.showAntMessage(text, AntStatus.Error)
    }
  }
  KeepScreenOn()

  val model = ui.model
  val firmware = ui.firmware
  val info = if (model != null && firmware != null) stringResource(R.string.scale_info, model.displayName, firmware) else null

  Scaffold(
    containerColor = AntTheme.colors.bgLayout,
    topBar = {
      ScaleTopBar(
        title = ui.deviceName ?: title,
        subtitle = info,
        batteryPercent = ui.batteryPercent,
        onBack = onBack,
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
      ConnectionBanner(ui.connection, onRetry = session::retry)
      WeightCard(ui)
      BrewControls(
        running = ui.timer == TimerStatus.RUNNING,
        enabled = enabled,
        onTare = session::tare,
        onToggleTimer = session::toggleTimer,
        onReset = session::resetTimer,
      )
      BrewChartCard(
        ui.brew,
        ui.unit,
        onFinish = session::finishBrew,
        target = ui.target,
        guide = ui.guide,
        lastFit = ui.lastFit,
        onPickTarget = { pickerOpen = true },
        coffee = ui.coffee,
        onPickCoffee = coffeePickerViewModel?.let { { coffeePickerOpen = true } },
      )
      (ui.brew.phase as? BrewPhase.Finished)?.let { finished ->
        val summary = finished.summary
        val fit = ui.lastFit?.let {
          fitView(it.fit.score, it.name, it.fit.targetWaterG, it.fit.targetEndMs, summary.totalWaterG, summary.totalTimeMs)
        }
        val shareData = summaryShareData(ui, summary)
        BrewSummaryCard(
          summary,
          ui.unit,
          savedBrew = ui.savedBrew,
          fit = fit,
          onAddDetails = onAddDetails,
          onShare = { launcher.share(ShareTarget.Summary, ShareContent.Brew(shareData)) },
        )
      }
      DoseCard(ui, enabled, onCapture = session::captureDose, onClear = session::clearDose)
      UnitCard(ui.unit, enabled, onToggle = session::toggleUnit)
      if (ui.isSimulated) {
        SimulationCard(
          hasTarget = ui.target != null,
          onAdd = session::simulateAdd,
          onPour = session::simulatePour,
          onFollow = session::simulateTargetPour,
        )
      }
    }
  }

  launcher.Host()

  if (coffeePickerOpen && coffeePickerViewModel != null) {
    val coffees by coffeePickerViewModel.pickable.collectAsStateWithLifecycle()
    CoffeePicker(
      coffees = coffees,
      selectedId = ui.coffee?.id,
      onSelect = session::selectCoffee,
      onClear = if (ui.coffee != null) ({ session.selectCoffee(null) }) else null,
      onNew = {
        coffeePickerOpen = false
        session.requestNewCoffee()
        onNewCoffee()
      },
      onClose = { coffeePickerOpen = false },
    )
  }

  // Las listas solo se recogen con el selector abierto.
  if (pickerOpen) {
    val recipes by pickerViewModel.recipes.collectAsStateWithLifecycle()
    val brews by pickerViewModel.brews.collectAsStateWithLifecycle()
    TargetPicker(
      recipes = recipes,
      brews = brews,
      active = ui.target?.key,
      onSelect = {
        session.selectTarget(it)
        pickerOpen = false
      },
      onClear = {
        session.selectTarget(null)
        pickerOpen = false
      },
      onManageRecipes = {
        pickerOpen = false
        onManageRecipes()
      },
      onClose = { pickerOpen = false },
      showBrews = LocalExperienceMode.current.shows(ExpertFeature.TARGET_FROM_BREW),
    )
  }
}

/** El estilo del peso de Scale (`heading1`, `Light`) con tamaño variable: 64 sp en el peso y 40 sp en la guía. */
@Composable
internal fun scaleFigureStyle(size: TextUnit): TextStyle =
  AntTheme.typography.heading1.copy(fontSize = size, lineHeight = size * LINE_HEIGHT_RATIO, fontWeight = FontWeight.Light)

/** Barra superior de Scale: el título se recorta con elipsis y la batería sigue visible. */
@Composable
internal fun ScaleTopBar(
  title: String,
  subtitle: String?,
  batteryPercent: Int?,
  onBack: () -> Unit,
) {
  AntTopBar(
    title = title,
    subtitle = subtitle,
    navigationIcon = {
      AntIconButton(AntIcons.ArrowLeft, contentDescription = stringResource(R.string.scale_back), onClick = onBack)
    },
    actions = {
      batteryPercent?.let { BatteryTag(it) }
    },
  )
}

@Composable
internal fun BatteryTag(percent: Int) {
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
      valueStyle = scaleFigureStyle(64.sp),
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
private fun SimulationCard(
  hasTarget: Boolean,
  onAdd: (Double) -> Unit,
  onPour: (PourProfile) -> Unit,
  onFollow: (FollowMode) -> Unit,
) {
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
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AntTheme.spacing.xs)) {
      AntButton(
        text = stringResource(R.string.target_sim_follow),
        onClick = { onFollow(FollowMode.FAITHFUL) },
        modifier = Modifier.weight(1f).testTag("sim_follow_target"),
        type = AntButtonType.Dashed,
        size = AntSize.Small,
        enabled = hasTarget,
      )
      AntButton(
        text = stringResource(R.string.target_sim_deviated),
        onClick = { onFollow(FollowMode.DEVIATED) },
        modifier = Modifier.weight(1f).testTag("sim_follow_deviated"),
        type = AntButtonType.Dashed,
        size = AntSize.Small,
        enabled = hasTarget,
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

/** La imagen del resumen: la hora de inicio (la de la sesión guardada, o ahora si aún no se guardó), el objetivo, el ajuste y el café. */
@Composable
private fun summaryShareData(ui: ScaleUiState, summary: BrewSummary): BrewShareData {
  val startedAt = ui.lastBrew?.startedAtEpochMs
  val fit = ui.lastFit
  val coffee = ui.lastBrew?.coffee
  return BrewShareData(
    samples = ui.brew.samples,
    summary = summary,
    unit = ui.unit,
    target = fit?.curve,
    title = formatBrewDateTime(startedAt ?: System.currentTimeMillis()),
    subtitle = fit?.let { stringResource(R.string.target_chip, it.name) },
    fitScore = fit?.fit?.score,
    rating = null,
    notes = null,
    footer = footerLines(
      coffee?.let { FooterCoffee(it.id, it.name, it.roaster, startedAt?.let { start -> brewDaysSinceRoast(it.roastDate, start, ZoneId.systemDefault()) }) },
    ),
  )
}
