package dev.josueamaya.pourline.ui.session

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.josueamaya.pourline.R
import dev.josueamaya.pourline.brew.BrewPhase
import dev.josueamaya.pourline.brew.BrewState
import dev.josueamaya.pourline.brew.DeviationLevel
import dev.josueamaya.pourline.brew.fromGrams
import dev.josueamaya.pourline.brew.toGrams
import dev.josueamaya.pourline.grinder.ConverterArgs
import dev.josueamaya.pourline.grinder.GrindFix
import dev.josueamaya.pourline.history.BrewRepository
import dev.josueamaya.pourline.scale.ConnectionState
import dev.josueamaya.pourline.recipe.StageType
import dev.josueamaya.pourline.session.GrindSelection
import dev.josueamaya.pourline.session.PrepState
import dev.josueamaya.pourline.session.PrepStep
import dev.josueamaya.pourline.session.SavedBrew
import dev.josueamaya.pourline.session.ScaleError
import dev.josueamaya.pourline.session.ScaleSessionManager
import dev.josueamaya.pourline.session.ScaleUiState
import dev.josueamaya.pourline.settings.ExpertFeature
import dev.josueamaya.pourline.settings.ExperienceMode
import dev.josueamaya.pourline.settings.LocalExperienceMode
import dev.josueamaya.pourline.settings.ModeStore
import dev.josueamaya.pourline.share.ShareContent
import dev.josueamaya.pourline.share.ShareSender
import dev.josueamaya.pourline.share.ShareTarget
import dev.josueamaya.pourline.share.rememberShareLauncher
import dev.josueamaya.pourline.share.rememberShareSender
import dev.josueamaya.pourline.ui.components.AntAlert
import dev.josueamaya.pourline.ui.components.AntButton
import dev.josueamaya.pourline.ui.components.AntButtonType
import dev.josueamaya.pourline.ui.components.AntCard
import dev.josueamaya.pourline.ui.components.AntCardSize
import dev.josueamaya.pourline.ui.components.AntChoiceModal
import dev.josueamaya.pourline.ui.components.AntIconButton
import dev.josueamaya.pourline.ui.components.AntIcons
import dev.josueamaya.pourline.ui.components.AntMessageHost
import dev.josueamaya.pourline.ui.components.AntModalChoice
import dev.josueamaya.pourline.ui.components.AntPageIndicator
import dev.josueamaya.pourline.ui.components.AntProgress
import dev.josueamaya.pourline.ui.components.AntStatus
import dev.josueamaya.pourline.ui.components.AntTag
import dev.josueamaya.pourline.ui.components.AntTagColor
import dev.josueamaya.pourline.ui.components.AntText
import dev.josueamaya.pourline.ui.components.AntTextType
import dev.josueamaya.pourline.ui.components.AntTitle
import dev.josueamaya.pourline.ui.components.AntTopBar
import dev.josueamaya.pourline.ui.components.showAntMessage
import dev.josueamaya.pourline.ui.grinders.GrindCard
import dev.josueamaya.pourline.ui.scale.Chart
import dev.josueamaya.pourline.ui.scale.formatDeviation
import dev.josueamaya.pourline.ui.scale.formatGuideAmount
import dev.josueamaya.pourline.ui.scale.formatNumber
import dev.josueamaya.pourline.ui.scale.levelText
import dev.josueamaya.pourline.ui.scale.tagColor
import dev.josueamaya.pourline.ui.theme.AntSize
import dev.josueamaya.pourline.ui.theme.AntTheme
import kotlinx.coroutines.flow.flowOf

private const val PREP_STEPS = 4
internal val MaxDial = 260.dp
internal val MinDial = 180.dp

/**
 * La sesión inmersiva (§3.4): pantalla completa sin barra inferior. Según el estado muestra los pasos previos
 * ([PrepPanel]), el vertido ([ActivePanel]) o el resumen ([SessionSummary]). La X y "Atrás" salen igual: antes de
 * `Active` sin preguntar, en `Active` con un diálogo y en `Finished` a Preparar.
 *
 * @param modeStore dónde se cambia el modo desde el menú ⋯; el modo que se ve sale de [LocalExperienceMode].
 * @param brews de dónde salen la valoración y las notas del resumen, tras "Añadir detalles".
 * @param onAddDetails abre `BrewEdit` con el id de la preparación guardada.
 * @param onDone cierra la sesión y vuelve a Preparar.
 */
@Composable
fun SessionScreen(
  session: ScaleSessionManager,
  modeStore: ModeStore,
  brews: BrewRepository,
  onAddDetails: (Long) -> Unit,
  onDone: () -> Unit,
  grind: GrindSelection,
  onAddGrinder: () -> Unit,
  onFixGrinder: (GrindFix, Long) -> Unit,
  onOpenConverter: ((ConverterArgs) -> Unit)? = null,
  shareSender: ShareSender? = null,
) {
  val ui by session.ui.collectAsStateWithLifecycle()
  val mode = LocalExperienceMode.current
  val snackbar = remember { SnackbarHostState() }
  val launcher = rememberShareLauncher(shareSender ?: rememberShareSender(), snackbar)
  var exitDialog by rememberSaveable { mutableStateOf(false) }
  var optionsOpen by rememberSaveable { mutableStateOf(false) }

  val commandError = stringResource(R.string.scale_error_command)
  val saveError = stringResource(R.string.scale_error_save)
  LaunchedEffect(session) {
    session.errors.collect { error ->
      val text = when (error) {
        is ScaleError.Command -> error.detail ?: commandError
        ScaleError.SaveFailed -> saveError
        else -> null
      }
      if (text != null) snackbar.showAntMessage(text, AntStatus.Error)
    }
  }
  KeepScreenOn()

  val phase = ui.brew.phase
  fun leave() {
    when (phase) {
      is BrewPhase.Active -> exitDialog = true
      is BrewPhase.Finished -> {
        session.endSession()
        onDone()
      }
      BrewPhase.Armed -> {
        session.endSession()
        onDone()
      }
    }
  }
  BackHandler(onBack = ::leave)

  Scaffold(
    containerColor = AntTheme.colors.bgLayout,
    topBar = {
      SessionTopBar(
        title = ui.target?.name ?: stringResource(R.string.session_title_free),
        onClose = ::leave,
        onOptions = { optionsOpen = true },
      )
    },
    snackbarHost = { AntMessageHost(snackbar) },
  ) { padding ->
    Column(Modifier.fillMaxSize().padding(padding)) {
      // Sin báscula no hay nada que reconectar: la sesión va solo por el reloj.
      if (!ui.scaleless) DisconnectedNotice(ui.connection, onRetry = session::retry)
      when {
        phase is BrewPhase.Finished -> {
          val saved = ui.savedBrew as? SavedBrew.Saved
          val record by remember(saved?.id) { saved?.let { brews.observe(it.id) } ?: flowOf(null) }
            .collectAsStateWithLifecycle(null)
          val shareData = summaryShareData(ui, phase.summary, record?.entity?.rating, record?.entity?.notes)
          SessionSummary(
            ui = ui,
            summary = phase.summary,
            record = record,
            mode = mode,
            onAddDetails = onAddDetails,
            onShare = { launcher.share(ShareTarget.Summary, ShareContent.Brew(shareData)) },
            onDone = {
              session.endSession()
              onDone()
            },
          )
        }
        phase is BrewPhase.Active && ui.scaleless -> ScalelessActivePanel(ui, onFinish = session::finishBrew)
        phase is BrewPhase.Active -> ActivePanel(ui, mode, onFinish = session::finishBrew)
        phase is BrewPhase.Armed && ui.scaleless -> ScalelessReadyPanel(ui, onStart = session::scalelessStartTimer)
        ui.prep != null -> PrepPanel(ui, ui.prep!!, session, grind, onAddGrinder, onFixGrinder, onOpenConverter)
        else -> Unit
      }
    }
  }

  launcher.Host()

  if (exitDialog) {
    AntChoiceModal(
      title = stringResource(R.string.exit_title),
      text = stringResource(R.string.exit_text),
      choices = listOf(
        AntModalChoice(
          stringResource(R.string.exit_save),
          { exitDialog = false; session.finishBrew() },
          "exit_save",
          AntButtonType.Primary,
        ),
        AntModalChoice(
          stringResource(R.string.exit_discard),
          {
            exitDialog = false
            session.discardSession()
            onDone()
          },
          "exit_discard",
          danger = true,
        ),
        AntModalChoice(stringResource(R.string.exit_continue), { exitDialog = false }, "exit_continue"),
      ),
      onDismiss = { exitDialog = false },
      modifier = Modifier.testTag("exit_dialog"),
    )
  }

  if (optionsOpen) {
    SessionOptionsSheet(
      expert = mode == ExperienceMode.EXPERT,
      onExpertChange = { modeStore.setMode(if (it) ExperienceMode.EXPERT else ExperienceMode.BASIC) },
      unit = ui.sessionUnit,
      unitEnabled = ui.connection == ConnectionState.Ready && !ui.scaleless,
      onUnitChange = { session.toggleUnit() },
      // Sin báscula, "Reiniciar" solo tiene sentido al terminar: en `Armed` no habría nada que reiniciar.
      restartEnabled = if (ui.scaleless) phase is BrewPhase.Finished else phase !is BrewPhase.Active,
      onRestart = session::restartPrep,
      simulation = if (ui.isSimulated && !ui.scaleless) {
        SimulationActions(
          hasTarget = ui.target != null,
          onAdd = session::simulateAdd,
          onPour = session::simulatePour,
          onFollow = session::simulateTargetPour,
        )
      } else {
        null
      },
      onClose = { optionsOpen = false },
    )
  }
}

/** La barra de la sesión: X, el título (se recorta con elipsis) y ⋯. */
@Composable
internal fun SessionTopBar(title: String, onClose: () -> Unit, onOptions: () -> Unit) {
  AntTopBar(
    title = title,
    navigationIcon = {
      AntIconButton(
        AntIcons.Close,
        contentDescription = stringResource(R.string.session_close),
        onClick = onClose,
        modifier = Modifier.testTag("session_close"),
      )
    },
    actions = {
      AntIconButton(
        AntIcons.More,
        contentDescription = stringResource(R.string.session_options),
        onClick = onOptions,
        modifier = Modifier.testTag("session_options"),
      )
    },
  )
}

/** Aviso de desconexión con "Reintentar"; nada con la báscula lista. */
@Composable
private fun DisconnectedNotice(state: ConnectionState, onRetry: () -> Unit) {
  if (state == ConnectionState.Ready) return
  val retry: @Composable () -> Unit = {
    AntButton(
      text = stringResource(R.string.session_retry),
      onClick = onRetry,
      modifier = Modifier.testTag("session_retry"),
      size = AntSize.Small,
      icon = AntIcons.Reload,
    )
  }
  val message = when (state) {
    is ConnectionState.Failed -> stringResource(R.string.conn_failed, state.reason)
    is ConnectionState.Connecting -> stringResource(R.string.conn_connecting)
    else -> stringResource(R.string.conn_disconnected)
  }
  AntAlert(
    message = message,
    modifier = Modifier.padding(AntTheme.spacing.base).testTag("session_disconnected"),
    type = if (state is ConnectionState.Connecting) AntStatus.Info else AntStatus.Warning,
    action = if (state is ConnectionState.Connecting) null else retry,
  )
}

// --- Pasos previos ---

@Composable
private fun PrepPanel(
  ui: ScaleUiState,
  prep: PrepState,
  session: ScaleSessionManager,
  grind: GrindSelection,
  onAddGrinder: () -> Unit,
  onFixGrinder: (GrindFix, Long) -> Unit,
  onOpenConverter: ((ConverterArgs) -> Unit)?,
) {
  val unit = ui.unit
  val ready = ui.connection == ConnectionState.Ready
  val stepNumber = prep.step.ordinal + 1
  val stepName = stringResource(
    when (prep.step) {
      PrepStep.TARE -> R.string.prep_step_tare
      PrepStep.DOSE -> R.string.prep_step_dose
      PrepStep.TARE_FOR_POUR -> R.string.prep_step_tare_pour
      PrepStep.POUR -> R.string.prep_step_pour
    },
  )
  val instruction = when (prep.step) {
    PrepStep.TARE -> stringResource(R.string.prep_instruction_tare)
    PrepStep.DOSE -> {
      val objective = ui.target?.nominalDoseG ?: ui.plan?.doseG
      if (objective != null) {
        stringResource(R.string.prep_instruction_dose_target, "${formatNumber(fromGrams(objective, unit), unit)} ${unit.symbol}")
      } else {
        stringResource(R.string.prep_instruction_dose)
      }
    }
    PrepStep.TARE_FOR_POUR -> stringResource(R.string.prep_instruction_tare_pour)
    PrepStep.POUR -> stringResource(R.string.prep_instruction_pour)
  }

  Column(
    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(AntTheme.spacing.base).testTag("session_prep"),
    verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.sm),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    PrepStepper(prep.step)
    AntText(
      "${formatNumber(ui.weight, unit)} ${unit.symbol}",
      Modifier.fillMaxWidth().padding(vertical = AntTheme.spacing.lg).testTag("prep_weight"),
      style = AntTheme.typography.heading1.copy(fontSize = 56.sp, lineHeight = 64.sp, fontWeight = FontWeight.Light),
      textAlign = TextAlign.Center,
    )
    AntText(
      stringResource(R.string.prep_step_label, stepNumber, stepName),
      Modifier.fillMaxWidth().testTag("prep_step_label"),
      type = AntTextType.Secondary,
    )
    AntTitle(instruction, Modifier.fillMaxWidth().testTag("prep_instruction").semantics { liveRegion = LiveRegionMode.Polite }, level = 4)
    when (prep.step) {
      PrepStep.DOSE -> {
        AntProgress(
          percent = prep.stableProgress * 100f,
          modifier = Modifier.fillMaxWidth().testTag("prep_stability"),
          showInfo = false,
        )
        val card by grind.state.collectAsStateWithLifecycle()
        GrindCard(
          state = card,
          mode = LocalExperienceMode.current,
          onChoose = grind::choose,
          onAddGrinder = onAddGrinder,
          onFix = onFixGrinder,
          onOpenConverter = onOpenConverter,
        )
      }
      PrepStep.TARE_FOR_POUR -> AntText(
        prep.doseG?.let { stringResource(R.string.prep_dose_value, "${formatNumber(fromGrams(it, unit), unit)} ${unit.symbol}") }
          ?: stringResource(R.string.prep_no_dose),
        Modifier.fillMaxWidth().testTag("prep_dose"),
      )
      else -> Unit
    }
    if (prep.waitingTare) {
      AntText(stringResource(R.string.prep_waiting_tare), Modifier.fillMaxWidth().testTag("prep_waiting_tare"), type = AntTextType.Secondary)
    }
    PrepButtons(prep.step, ready, ui.weight > 0, session)
  }
}

@Composable
private fun PrepButtons(step: PrepStep, ready: Boolean, hasWeight: Boolean, session: ScaleSessionManager) {
  when (step) {
    PrepStep.TARE, PrepStep.TARE_FOR_POUR -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AntTheme.spacing.xs)) {
      AntButton(
        stringResource(R.string.prep_tare),
        onClick = session::prepTare,
        modifier = Modifier.weight(1f).testTag("prep_tare"),
        type = AntButtonType.Primary,
        size = AntSize.Large,
        enabled = ready,
      )
      AntButton(
        stringResource(R.string.prep_skip),
        onClick = session::prepSkip,
        modifier = Modifier.weight(1f).testTag("prep_skip"),
        size = AntSize.Large,
      )
    }
    PrepStep.DOSE -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AntTheme.spacing.xs)) {
      AntButton(
        stringResource(R.string.prep_capture),
        onClick = session::prepCaptureNow,
        modifier = Modifier.weight(1f).testTag("prep_capture"),
        type = AntButtonType.Primary,
        size = AntSize.Large,
        enabled = ready && hasWeight,
      )
      AntButton(
        stringResource(R.string.prep_skip_dose),
        onClick = session::prepSkipDose,
        modifier = Modifier.weight(1f).testTag("prep_skip_dose"),
        size = AntSize.Large,
      )
    }
    PrepStep.POUR -> AntButton(
      stringResource(R.string.prep_start_timer),
      onClick = session::prepStartTimer,
      modifier = Modifier.fillMaxWidth().testTag("prep_start_timer"),
      type = AntButtonType.Primary,
      size = AntSize.Large,
      enabled = ready,
    )
  }
}

/** Los cuatro pasos: hecho (marca), actual (resaltado) y pendiente. */
@Composable
private fun PrepStepper(current: PrepStep) {
  val colors = AntTheme.colors
  val description = stringResource(R.string.prep_stepper, current.ordinal + 1)
  Row(
    Modifier.fillMaxWidth().testTag("prep_stepper").semantics { contentDescription = description },
    verticalAlignment = Alignment.CenterVertically,
  ) {
    PrepStep.entries.forEachIndexed { index, step ->
      if (index > 0) {
        Box(
          Modifier
            .weight(1f)
            .height(2.dp)
            .background(if (step.ordinal <= current.ordinal) colors.primary.base else colors.fill),
        )
      }
      val done = step.ordinal < current.ordinal
      val active = step == current
      Box(
        Modifier
          .size(28.dp)
          .background(if (done) colors.primary.base else colors.bgContainer, CircleShape)
          .border(2.dp, if (done || active) colors.primary.base else colors.fill, CircleShape),
        contentAlignment = Alignment.Center,
      ) {
        if (done) {
          Icon(AntIcons.Check, contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.bgContainer)
        } else {
          AntText(
            "${index + 1}",
            style = AntTheme.typography.textSM,
            type = if (active) AntTextType.Default else AntTextType.Secondary,
            strong = active,
          )
        }
      }
    }
  }
}

// --- Vertido ---

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ActivePanel(ui: ScaleUiState, mode: ExperienceMode, onFinish: () -> Unit) {
  val unit = ui.unit
  val brew = ui.brew
  val target = ui.target
  val guide = ui.guide
  val waterG = brew.samples.lastOrNull { it.valid }?.waterG ?: 0.0
  val doseG = ui.dose?.let { toGrams(it, unit) }
  val ratio = doseG?.takeIf { it > 0 }?.let { waterG / it }
  val showChart = mode.shows(ExpertFeature.SESSION_CHART_PAGE)
  val insights = mode.shows(ExpertFeature.SESSION_DIAL_INSIGHTS)
  val elapsed = ui.elapsedMs ?: 0L
  val segments = remember(target?.curve) { target?.curve?.let(::dialSegments).orEmpty() }
  val stretches = remember(insights, brew.samples, ui.targetWaterG) {
    if (insights) dialStretches(brew.samples, ui.targetWaterG) else emptyList()
  }
  // El agua de la curva objetivo ahora: la del dial menos el desvío (los dos salen de la última muestra válida).
  val expectedWaterG = guide?.deviationG?.takeIf { insights && target != null }?.let { waterG - it }
  var showLegend by remember { mutableStateOf(false) }
  val pagerState = rememberPagerState { if (showChart) 2 else 1 }
  // Pasar a Básico en la página de la gráfica devuelve el dial (§4).
  LaunchedEffect(showChart) { if (!showChart) pagerState.scrollToPage(0) }

  BoxWithConstraints(Modifier.fillMaxSize()) {
    val dialSize: Dp = minOf(maxWidth * 0.62f, maxHeight * 0.34f, MaxDial).coerceAtLeast(MinDial)
    Column(
      Modifier.fillMaxSize().padding(horizontal = AntTheme.spacing.base, vertical = AntTheme.spacing.xs),
      verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.xs),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      AntText(
        formatClock(elapsed),
        Modifier.testTag("session_timer"),
        style = AntTheme.typography.heading1.copy(fontSize = 48.sp, lineHeight = 56.sp, fontWeight = FontWeight.Light),
      )
      HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth().height(dialSize)) { page ->
        if (page == 0) {
          Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            BrewDial(
              waterG = waterG,
              targetWaterG = ui.targetWaterG,
              segments = segments,
              ratio = ratio,
              flowGps = ui.flowRate,
              unit = unit,
              modifier = Modifier.size(dialSize),
              stretches = stretches,
              expectedWaterG = expectedWaterG,
              flowSamples = brew.samples.takeIf { insights },
            )
          }
        } else {
          Box(Modifier.fillMaxSize().testTag("session_chart")) {
            Chart(brew.samples, unit, stabilityLabel(brew), target = target?.curve, fillHeight = true)
          }
        }
      }
      if (showChart) AntPageIndicator(count = 2, selected = pagerState.currentPage)
      Metrics(ui, mode, onLegend = { showLegend = true }.takeIf { insights })
      CurrentStep(ui)
      NextStep(ui, elapsed)
      val stages = target?.curve?.stages
      if (stages != null) {
        StepList(
          stages = stages,
          states = stepStates(stages.size, guide?.stage?.index, guide?.allStagesDone == true),
          unit = unit,
          modifier = Modifier.weight(1f).fillMaxWidth(),
        )
      } else {
        Box(Modifier.weight(1f))
      }
      AntButton(
        text = stringResource(R.string.session_finish),
        onClick = onFinish,
        modifier = Modifier.fillMaxWidth().testTag("session_finish"),
        type = AntButtonType.Primary,
        size = AntSize.Large,
      )
    }
  }
  // Pasar a Básico con la leyenda abierta la cierra: el dial ya no tiene nada que explicar.
  if (showLegend && insights) DialLegendSheet(onClose = { showLegend = false })
}

/** El texto de la estabilidad en vivo: sin vertido, midiendo o el nivel con su puntuación. */
@Composable
private fun stabilityLabel(brew: BrewState): String {
  val phase = brew.phase as? BrewPhase.Active
  val score = brew.currentStability
  val level = brew.currentLevel
  return when {
    phase == null || !phase.pouring -> stringResource(R.string.brew_level_idle)
    score == null || level == null -> stringResource(R.string.brew_level_measuring)
    else -> levelText(level, score)
  }
}

/**
 * Solo en Experto: la estabilidad en vivo, el desvío en gramos contra la curva objetivo y, con [onLegend], el botón ⓘ
 * que abre la leyenda del dial.
 */
@Composable
private fun Metrics(ui: ScaleUiState, mode: ExperienceMode, onLegend: (() -> Unit)?) {
  val showStability = mode.shows(ExpertFeature.SESSION_STABILITY)
  val deviationG = ui.guide?.deviationG?.takeIf { ui.target != null && mode.shows(ExpertFeature.SESSION_DEVIATION) }
  if (!showStability && deviationG == null) return
  val level = ui.brew.currentLevel
  Row(
    Modifier.fillMaxWidth().testTag("session_metrics"),
    horizontalArrangement = Arrangement.spacedBy(AntTheme.spacing.sm, Alignment.CenterHorizontally),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (showStability) {
      val phase = ui.brew.phase as? BrewPhase.Active
      val color = if (phase?.pouring == true && level != null) level.tagColor() else AntTagColor.Default
      AntTag(stabilityLabel(ui.brew), Modifier.testTag("session_stability"), color = color)
    }
    if (deviationG != null) {
      val deviation = stringResource(R.string.session_deviation, formatDeviation(deviationG, ui.unit))
      val type = when (ui.guide?.deviationLevel) {
        DeviationLevel.OK -> AntTextType.Success
        DeviationLevel.WARNING -> AntTextType.Warning
        DeviationLevel.ERROR -> AntTextType.Danger
        null -> AntTextType.Secondary
      }
      AntText(deviation, Modifier.testTag("session_deviation"), type = type, strong = true)
    }
    if (onLegend != null) {
      AntIconButton(
        AntIcons.Info,
        contentDescription = stringResource(R.string.session_dial_legend),
        onClick = onLegend,
        modifier = Modifier.testTag("session_dial_legend"),
        size = AntSize.Small,
      )
    }
  }
}

/** El paso en curso: tipo con icono, nombre, agua acumulada (Verter) e instrucción. Sin receta, un único paso "Verter hasta X". */
@Composable
internal fun CurrentStep(ui: ScaleUiState) {
  val unit = ui.sessionUnit
  val target = ui.target
  val guide = ui.guide
  val stage = guide?.stage?.takeIf { guide.allStagesDone.not() }
  val total = target?.curve?.stages?.size ?: 1
  val title: String
  val instruction: String?
  val upTo: String?
  val icon = stage?.let { stepTypeIcon(it.type) } ?: AntIcons.Pour
  val description: String
  when {
    target == null -> {
      val water = ui.targetWaterG?.let { formatGuideAmount(it, unit) }.orEmpty()
      title = stringResource(R.string.session_free_step, water)
      instruction = null
      upTo = null
      description = title
    }
    guide?.allStagesDone == true -> {
      title = stringResource(R.string.session_steps_done)
      instruction = null
      upTo = null
      description = title
    }
    stage == null -> {
      title = stringResource(R.string.session_step_waiting)
      instruction = null
      upTo = null
      description = title
    }
    else -> {
      val type = stringResource(stepTypeLabel(stage.type))
      title = stage.name?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.session_step_title, type, it) } ?: type
      instruction = stage.instruction?.takeIf { it.isNotBlank() }
      upTo = if (stage.type == StageType.POUR) {
        stringResource(R.string.session_step_up_to, formatGuideAmount(stage.targetWaterG, unit))
      } else {
        null
      }
      description = stringResource(R.string.session_step_description, stage.index + 1, total, title)
    }
  }
  AntCard(
    Modifier
      .fillMaxWidth()
      .heightIn(min = 72.dp)
      .testTag("session_current_step")
      .semantics { contentDescription = description; liveRegion = LiveRegionMode.Polite },
    size = AntCardSize.Small,
    verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.xxs),
  ) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AntTheme.spacing.xs)) {
      Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = AntTheme.colors.primary.base)
      AntText(title, Modifier.weight(1f), strong = true, maxLines = 1, overflow = TextOverflow.Ellipsis)
      upTo?.let { AntText(it, Modifier.testTag("session_step_up_to"), type = AntTextType.Secondary, maxLines = 1) }
    }
    instruction?.let {
      AntText(it, Modifier.testTag("session_instruction"), type = AntTextType.Secondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
  }
}

/** "Siguiente: {tipo} {nombre} en {m:ss}"; en los últimos 5 s, el tiempo es la cuenta atrás en color primario y negrita (D6). */
@Composable
internal fun NextStep(ui: ScaleUiState, elapsedMs: Long) {
  val guide = ui.guide ?: return
  if (ui.target == null || guide.allStagesDone) return
  val info = nextStepInfo(guide.nextStage, elapsedMs, guide.countdownS) ?: return
  val type = stringResource(stepTypeLabel(info.stage.type))
  val label = info.name?.let { stringResource(R.string.session_step_title, type, it) } ?: type
  val time = info.countdownS?.let { stringResource(R.string.session_next_seconds, it) } ?: nextStepClock(info)
  // El tiempo va dentro de la frase traducida: se parte por un marcador para darle estilo propio.
  val marker = "\u0000"
  val (before, after) = stringResource(R.string.session_next, label, marker).split(marker).let { it[0] to it.getOrElse(1) { "" } }
  Row(Modifier.fillMaxWidth().testTag("session_next_step"), verticalAlignment = Alignment.CenterVertically) {
    AntText(before, type = AntTextType.Secondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
    AntText(
      time,
      Modifier.testTag("session_next_time"),
      type = if (info.emphasized) AntTextType.Default else AntTextType.Secondary,
      style = if (info.emphasized) AntTheme.typography.text.copy(color = AntTheme.colors.primary.base) else AntTheme.typography.text,
      strong = info.emphasized,
      maxLines = 1,
    )
    if (after.isNotEmpty()) AntText(after, type = AntTextType.Secondary, maxLines = 1)
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
