package dev.josueamaya.pourline.ui.recipes

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.josueamaya.pourline.ui.components.AntDivider
import dev.josueamaya.pourline.ui.session.stepTypeColors
import dev.josueamaya.pourline.ui.theme.AntColorRole
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.josueamaya.pourline.R
import dev.josueamaya.pourline.brew.TargetCurve
import dev.josueamaya.pourline.grinder.ConverterArgs
import dev.josueamaya.pourline.history.BrewMethod
import dev.josueamaya.pourline.protocol.WeightUnit
import dev.josueamaya.pourline.recipe.Adjustment
import dev.josueamaya.pourline.recipe.RecipeValidator
import dev.josueamaya.pourline.recipe.StageType
import dev.josueamaya.pourline.settings.ExpertFeature
import dev.josueamaya.pourline.settings.LocalExperienceMode
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
import dev.josueamaya.pourline.ui.components.AntIconButton
import dev.josueamaya.pourline.ui.components.AntIcons
import dev.josueamaya.pourline.ui.components.AntInput
import dev.josueamaya.pourline.ui.components.AntInputStatus
import dev.josueamaya.pourline.ui.components.AntMessageHost
import dev.josueamaya.pourline.ui.components.AntModal
import dev.josueamaya.pourline.ui.components.AntSegmented
import dev.josueamaya.pourline.ui.components.AntSegmentedOption
import dev.josueamaya.pourline.ui.components.AntSelect
import dev.josueamaya.pourline.ui.components.AntSelectOption
import dev.josueamaya.pourline.ui.components.AntSpin
import dev.josueamaya.pourline.ui.components.AntStatus
import dev.josueamaya.pourline.ui.components.AntText
import dev.josueamaya.pourline.ui.components.AntTextType
import dev.josueamaya.pourline.ui.components.AntTopBar
import dev.josueamaya.pourline.ui.grinders.GrindSettingField
import dev.josueamaya.pourline.ui.history.label
import dev.josueamaya.pourline.ui.scale.BrewChart
import dev.josueamaya.pourline.ui.session.formatClock
import dev.josueamaya.pourline.ui.session.stepTypeIcon
import dev.josueamaya.pourline.ui.session.stepTypeLabel
import dev.josueamaya.pourline.ui.theme.AntSize
import dev.josueamaya.pourline.ui.theme.AntTheme
import dev.josueamaya.pourline.ui.tour.TourHost
import dev.josueamaya.pourline.ui.tour.Tours
import dev.josueamaya.pourline.ui.tour.tourAnchor

@Composable
fun RecipeEditScreen(
  viewModel: RecipeEditViewModel,
  onBack: () -> Unit,
  onDuplicated: (Long) -> Unit,
  onOpenConverter: ((ConverterArgs) -> Unit)? = null,
  /** Abre el generador 4:6; `null` lo oculta (solo se ofrece al crear una receta desde cero). */
  onOpenFourSix: (() -> Unit)? = null,
  shareSender: ShareSender = rememberShareSender(),
) {
  val status by viewModel.status.collectAsStateWithLifecycle()
  val form by viewModel.form.collectAsStateWithLifecycle()
  val initial by viewModel.initial.collectAsStateWithLifecycle()
  val adjustments by viewModel.adjustments.collectAsStateWithLifecycle()
  val grinders by viewModel.grinders.collectAsStateWithLifecycle()
  val currentGrinder by viewModel.currentGrinder.collectAsStateWithLifecycle()
  val sharePrompt by viewModel.share.collectAsStateWithLifecycle()
  var confirmDiscard by rememberSaveable { mutableStateOf(false) }
  val snackbar = remember { SnackbarHostState() }
  val launcher = rememberShareLauncher(shareSender, snackbar)

  // `Saved` y `Gone` vuelven atrás; `Duplicated` abre la copia.
  LaunchedEffect(status) {
    when (val s = status) {
      RecipeEditStatus.Saved, RecipeEditStatus.Gone -> onBack()
      is RecipeEditStatus.Duplicated -> onDuplicated(s.id)
      else -> Unit
    }
  }

  val ready = status == RecipeEditStatus.Ready
  val dirty = form.isDirty(initial) && ready
  val requestBack = { if (dirty) confirmDiscard = true else onBack() }
  BackHandler(enabled = dirty) { confirmDiscard = true }

  // Sin recorte ni aviso se comparte directamente; con él, se espera a que el usuario acepte el diálogo.
  LaunchedEffect(sharePrompt) {
    val prompt = sharePrompt ?: return@LaunchedEffect
    if (!prompt.needsConfirmation) {
      launcher.share(ShareTarget.Recipe(prompt.id), ShareContent.Recipe(prompt.data))
      viewModel.clearShare()
    }
  }

  val errors = remember(form) { form.errors() }

  // El paso recién movido se resalta un momento en su nuevo puesto.
  var moved by remember { mutableStateOf<MovedStage?>(null) }
  LaunchedEffect(moved) {
    if (moved != null) {
      delay(MOVED_HIGHLIGHT_MS)
      moved = null
    }
  }

  Scaffold(
    containerColor = AntTheme.colors.bgLayout,
    topBar = {
      AntTopBar(
        title = stringResource(if (viewModel.isExisting) R.string.recipe_edit_title else R.string.recipe_edit_title_new),
        navigationIcon = {
          AntIconButton(AntIcons.ArrowLeft, contentDescription = stringResource(R.string.scale_back), onClick = requestBack)
        },
        actions = {
          AntIconButton(
            AntIcons.Share,
            contentDescription = stringResource(R.string.recipe_share_action),
            onClick = viewModel::prepareShare,
            modifier = Modifier.testTag("recipe_edit_share"),
            enabled = viewModel.isExisting && ready && !dirty,
          )
        },
      )
    },
    snackbarHost = { AntMessageHost(snackbar) },
  ) { padding ->
    if (status == RecipeEditStatus.Loading) {
      AntSpin(Modifier.padding(padding).padding(AntTheme.spacing.xl))
      return@Scaffold
    }
    Column(
      Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(AntTheme.spacing.base),
      verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.base),
    ) {
      if (adjustments.isNotEmpty()) AdjustmentsAlert(adjustments)
      if (onOpenFourSix != null) {
        AntButton(
          text = stringResource(R.string.recipe_edit_four_six),
          onClick = onOpenFourSix,
          modifier = Modifier.fillMaxWidth().testTag("recipe_edit_four_six"),
          icon = AntIcons.Dripper,
        )
      }

      LabeledInput(
        label = stringResource(R.string.recipe_edit_name),
        value = form.name,
        onValueChange = { v -> viewModel.update { it.copy(name = v) } },
        error = errors.name,
        tag = "recipe_edit_name",
      )
      val noMethod = stringResource(R.string.recipe_edit_method_none)
      val options = listOf(AntSelectOption<BrewMethod?>(null, noMethod)) + BrewMethod.entries.map { AntSelectOption<BrewMethod?>(it, it.label()) }
      AntSelect(
        options = options,
        selected = form.method,
        onSelect = { m -> viewModel.update { it.copy(method = m) } },
        label = stringResource(R.string.recipe_edit_method),
        modifier = Modifier.fillMaxWidth().tourAnchor("recipe_edit_method"),
        testTag = "recipe_edit_method",
      )
      if (form.method == BrewMethod.OTHER) {
        AntInput(
          value = form.methodOther,
          onValueChange = { v -> viewModel.update { it.copy(methodOther = v) } },
          modifier = Modifier.fillMaxWidth().testTag("recipe_edit_method_other"),
          placeholder = stringResource(R.string.recipe_edit_method_other),
        )
      }
      GrindSettingField(
        choice = form.grind,
        options = grinders,
        current = currentGrinder,
        method = form.method,
        onChange = { choice -> viewModel.update { it.copy(grind = choice) } },
        tagPrefix = "recipe_edit",
        modifier = Modifier.fillMaxWidth(),
        onOpenConverter = onOpenConverter,
      )
      LabeledInput(
        label = stringResource(R.string.recipe_edit_temp),
        value = form.waterTemp,
        onValueChange = { v -> viewModel.update { it.copy(waterTemp = v) } },
        error = errors.temp,
        keyboardType = KeyboardType.Decimal,
        tag = "recipe_edit_temp",
      )
      LabeledInput(
        label = stringResource(R.string.recipe_edit_notes),
        value = form.notes,
        onValueChange = { v -> viewModel.update { it.copy(notes = v) } },
        singleLine = false,
        tag = "recipe_edit_notes",
      )
      LabeledInput(
        label = stringResource(R.string.recipe_edit_dose),
        value = form.dose,
        onValueChange = { v -> viewModel.update { it.copy(dose = v) } },
        error = errors.dose,
        keyboardType = KeyboardType.Decimal,
        tag = "recipe_edit_dose",
      )

      AntText(stringResource(R.string.recipe_edit_stages), style = AntTheme.typography.heading5)
      errors.general?.let { ErrorText(it, "recipe_edit_error_general") }
      form.stages.forEachIndexed { i, stage ->
        StageCard(
          index = i,
          count = form.stages.size,
          stage = stage,
          errors = errors.stages.getOrNull(i) ?: StageFieldErrors(),
          canRemove = form.canRemoveStage,
          canMoveUp = form.canMoveStageUp(i),
          canMoveDown = form.canMoveStageDown(i),
          justMoved = moved?.takeIf { it.index == i },
          onChange = { changed -> viewModel.update { it.updateStage(i, changed) } },
          onTypeChange = { type -> viewModel.update { it.setStageType(i, type) } },
          onRemove = {
            moved = null
            viewModel.update { it.removeStage(i) }
          },
          onMoveUp = {
            if (form.canMoveStageUp(i)) {
              viewModel.update { it.moveStageUp(i) }
              moved = MovedStage(i - 1, (moved?.serial ?: 0) + 1)
            }
          },
          onMoveDown = {
            if (form.canMoveStageDown(i)) {
              viewModel.update { it.moveStageDown(i) }
              moved = MovedStage(i + 1, (moved?.serial ?: 0) + 1)
            }
          },
        )
      }
      AntButton(
        text = stringResource(R.string.recipe_edit_add_stage),
        onClick = {
          moved = null
          viewModel.update { it.addStage() }
        },
        modifier = Modifier.fillMaxWidth().tourAnchor("recipe_add_stage").testTag("recipe_add_stage"),
        enabled = form.canAddStage,
        icon = AntIcons.Plus,
      )

      Totals(form)
      if (LocalExperienceMode.current.shows(ExpertFeature.RECIPE_CURVE_PREVIEW)) Preview(form)

      AntButton(
        text = stringResource(R.string.recipe_edit_save),
        onClick = viewModel::save,
        modifier = Modifier.fillMaxWidth().testTag("recipe_edit_save"),
        type = AntButtonType.Primary,
        enabled = !errors.any && ready,
        loading = status == RecipeEditStatus.Saving,
      )
      if (viewModel.isExisting) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AntTheme.spacing.sm)) {
          AntButton(
            text = stringResource(R.string.recipe_edit_duplicate),
            onClick = viewModel::duplicate,
            modifier = Modifier.weight(1f).testTag("recipe_edit_duplicate"),
            enabled = ready && !dirty,
          )
          AntButton(
            text = stringResource(R.string.recipe_edit_delete),
            onClick = viewModel::delete,
            modifier = Modifier.weight(1f).testTag("recipe_edit_delete"),
            danger = true,
            enabled = ready,
          )
        }
      }
    }
  }

  TourHost(Tours.recipeEdit, ready = status != RecipeEditStatus.Loading)
  launcher.Host()

  sharePrompt?.takeIf { it.needsConfirmation }?.let { prompt ->
    AntModal(
      title = stringResource(R.string.recipe_share_dialog_title),
      text = if (prompt.withoutQr) {
        stringResource(prompt.dialogTextRes())
      } else {
        stringResource(prompt.dialogTextRes(), trimmedPartsText(prompt.trimmed, prompt.partialInstructions))
      },
      confirmText = stringResource(R.string.recipe_share_confirm),
      cancelText = stringResource(R.string.recipe_share_cancel),
      onConfirm = {
        launcher.share(ShareTarget.Recipe(prompt.id), ShareContent.Recipe(prompt.data))
        viewModel.clearShare()
      },
      onDismiss = viewModel::clearShare,
    )
  }

  if (confirmDiscard) {
    AntModal(
      title = stringResource(R.string.recipe_edit_discard_title),
      text = stringResource(R.string.recipe_edit_discard_text),
      confirmText = stringResource(R.string.recipe_edit_discard),
      cancelText = stringResource(R.string.recipe_edit_keep),
      onConfirm = {
        confirmDiscard = false
        onBack()
      },
      onDismiss = { confirmDiscard = false },
      danger = true,
    )
  }
}

@Composable
private fun AdjustmentsAlert(adjustments: List<Adjustment>) {
  val lines = adjustments.map { adjustment ->
    when (adjustment) {
      is Adjustment.MergedPours -> pluralStringResource(R.plurals.recipe_edit_adjusted_merged, adjustment.count, adjustment.count)
      Adjustment.ClampedDurations -> stringResource(R.string.recipe_edit_adjusted_clamped)
      Adjustment.MissingDose -> stringResource(R.string.recipe_edit_adjusted_dose)
    }
  }
  AntAlert(
    message = stringResource(R.string.recipe_edit_adjusted_title),
    description = lines.joinToString("\n"),
    type = AntStatus.Info,
    modifier = Modifier.testTag("recipe_edit_adjusted"),
  )
}

/** Un paso recién movido a [index]; [serial] distingue dos movimientos seguidos al mismo puesto. */
private data class MovedStage(val index: Int, val serial: Int)

private const val MOVED_HIGHLIGHT_MS = 2_000L
private const val HIGHLIGHT_FADE_MS = 600

@Composable
private fun StageCard(
  index: Int,
  count: Int,
  stage: StageForm,
  errors: StageFieldErrors,
  canRemove: Boolean,
  canMoveUp: Boolean,
  canMoveDown: Boolean,
  justMoved: MovedStage?,
  onChange: (StageForm) -> Unit,
  onTypeChange: (StageType) -> Unit,
  onRemove: () -> Unit,
  onMoveUp: () -> Unit,
  onMoveDown: () -> Unit,
) {
  val number = index + 1
  val typeOptions = StageType.entries.map { AntSegmentedOption(it, stringResource(stepTypeLabel(it)), stepTypeIcon(it)) }
  val typeColors = stepTypeColors(stage.type, AntTheme.colors)
  val requester = remember { BringIntoViewRequester() }
  // Tras moverlo, la tarjeta se lleva a la vista y su borde se ilumina con el color del tipo hasta que se apaga.
  LaunchedEffect(justMoved) { if (justMoved != null) requester.bringIntoView() }
  val highlight by animateColorAsState(
    if (justMoved != null) typeColors.base else Color.Transparent,
    tween(if (justMoved != null) AntTheme.motion.durationFast else HIGHLIGHT_FADE_MS),
    label = "StageMovedHighlight",
  )
  val shape = RoundedCornerShape(AntTheme.radius.lg)
  AntCard(
    Modifier.fillMaxWidth()
      .bringIntoViewRequester(requester)
      .border(2.dp, highlight, shape)
      .tourAnchor("recipe_stage_0", enabled = index == 0)
      .testTag("recipe_stage_$index"),
    size = AntCardSize.Small,
  ) {
    // Con cuatro tipos no caben las etiquetas (D15 de SPEC-010): a la izquierda el número y el tipo elegido y a la
    // derecha las opciones.
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AntTheme.spacing.xs)) {
      StageNumber(number, typeColors)
      AntText(
        stringResource(stepTypeLabel(stage.type)),
        Modifier.weight(1f).testTag("recipe_stage_type_label_$index"),
        strong = true,
        maxLines = 2,
      )
      AntSegmented(
        options = typeOptions,
        selected = stage.type,
        onSelect = onTypeChange,
        modifier = Modifier.testTag("recipe_stage_type_$index"),
        size = AntSize.Small,
        iconOnly = true,
      )
    }
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(AntTheme.spacing.xs)) {
      Column(Modifier.weight(1f)) {
        AntInput(
          value = stage.name,
          onValueChange = { onChange(stage.copy(name = it)) },
          modifier = Modifier.fillMaxWidth().testTag("recipe_stage_name_$index"),
          placeholder = stringResource(R.string.recipe_edit_stage_name, number),
        )
      }
      AntIconButton(
        AntIcons.Close,
        contentDescription = stringResource(R.string.recipe_edit_stage_remove, number),
        onClick = onRemove,
        modifier = Modifier.testTag("recipe_stage_remove_$index"),
        enabled = canRemove,
      )
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AntTheme.spacing.xs)) {
      LabeledInput(
        label = stringResource(R.string.recipe_edit_stage_start),
        value = stage.start,
        onValueChange = { onChange(stage.copy(start = it)) },
        error = errors.start,
        modifier = Modifier.weight(1f),
        tag = "recipe_stage_start_$index",
        showError = false,
      )
      if (stage.type == StageType.POUR) {
        LabeledInput(
          label = stringResource(R.string.recipe_edit_stage_water),
          value = stage.water,
          onValueChange = { onChange(stage.copy(water = it)) },
          error = errors.water,
          keyboardType = KeyboardType.Decimal,
          modifier = Modifier.weight(1f),
          tag = "recipe_stage_water_$index",
          showError = false,
        )
      }
      LabeledInput(
        label = stringResource(R.string.recipe_edit_stage_duration),
        value = stage.duration,
        onValueChange = { onChange(stage.copy(duration = it)) },
        error = errors.duration,
        keyboardType = KeyboardType.Number,
        modifier = Modifier.weight(1f),
        tag = "recipe_stage_duration_$index",
        showError = false,
      )
    }
    Column(verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.xxs)) {
      AntText(stringResource(R.string.recipe_stage_instruction), type = AntTextType.Secondary, style = AntTheme.typography.textSM)
      AntInput(
        value = stage.instruction,
        onValueChange = { onChange(stage.copy(instruction = it)) },
        modifier = Modifier.fillMaxWidth().testTag("recipe_stage_instruction_$index"),
        status = if (errors.instruction != null) AntInputStatus.Error else AntInputStatus.Default,
        singleLine = false,
        minLines = 2,
      )
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AntTheme.spacing.xs)) {
        // Aviso no bloqueante: una Acción sin instrucción se guarda igual.
        if (stage.type == StageType.ACTION && stage.instruction.isBlank()) {
          AntText(
            stringResource(R.string.recipe_action_instruction_hint),
            Modifier.weight(1f).testTag("recipe_stage_action_hint_$index"),
            type = AntTextType.Warning,
            style = AntTheme.typography.textSM,
          )
        } else {
          Spacer(Modifier.weight(1f))
        }
        AntText(
          stringResource(R.string.recipe_stage_instruction_counter, stage.instruction.length, RecipeValidator.MAX_INSTRUCTION),
          Modifier.testTag("recipe_stage_instruction_counter_$index"),
          type = if (errors.instruction != null) AntTextType.Danger else AntTextType.Secondary,
          style = AntTheme.typography.textSM,
          textAlign = TextAlign.End,
        )
      }
    }
    // Con tres campos por fila no cabe el texto de cada error: se muestran debajo, uno por campo.
    errors.start?.let { ErrorText(it, "recipe_stage_start_error_$index") }
    errors.water?.let { ErrorText(it, "recipe_stage_water_error_$index") }
    errors.duration?.let { ErrorText(it, "recipe_stage_duration_error_$index") }
    errors.instruction?.let { ErrorText(it, "recipe_stage_instruction_error_$index") }
    if (count > 1) StageMoveBar(index, count, canMoveUp, canMoveDown, justMoved != null, typeColors, onMoveUp, onMoveDown)
  }
}

/** El número del paso en un círculo con el color de su tipo: así se ve en qué puesto queda al moverlo. */
@Composable
private fun StageNumber(number: Int, colors: AntColorRole) {
  Box(
    Modifier.size(28.dp).background(colors.bg, CircleShape).border(1.dp, colors.border, CircleShape).clearAndSetSemantics {},
    contentAlignment = Alignment.Center,
  ) {
    Text(number.toString(), color = colors.text, style = AntTheme.typography.textSM.copy(fontWeight = FontWeight.SemiBold))
  }
}

/**
 * Reordenar sin borrar: "Paso N de M" y los botones Subir y Bajar. Recién movido, el texto dice a qué puesto fue, con
 * el color del tipo, y se anuncia (región viva educada).
 */
@Composable
private fun StageMoveBar(
  index: Int,
  count: Int,
  canMoveUp: Boolean,
  canMoveDown: Boolean,
  justMoved: Boolean,
  colors: AntColorRole,
  onMoveUp: () -> Unit,
  onMoveDown: () -> Unit,
) {
  val number = index + 1
  AntDivider()
  Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AntTheme.spacing.xs)) {
    val position = stringResource(if (justMoved) R.string.recipe_edit_stage_moved else R.string.recipe_edit_stage_position, number, count)
    Text(
      position,
      Modifier.weight(1f).testTag("recipe_stage_position_$index").semantics { liveRegion = LiveRegionMode.Polite },
      color = if (justMoved) colors.text else AntTheme.colors.textSecondary,
      style = AntTheme.typography.textSM.copy(fontWeight = if (justMoved) FontWeight.SemiBold else FontWeight.Normal),
    )
    val up = stringResource(R.string.recipe_edit_stage_up, number)
    val down = stringResource(R.string.recipe_edit_stage_down, number)
    AntButton(
      text = stringResource(R.string.recipe_edit_stage_up_short),
      onClick = onMoveUp,
      modifier = Modifier.testTag("recipe_stage_up_$index").semantics { contentDescription = up },
      size = AntSize.Small,
      icon = AntIcons.Up,
      enabled = canMoveUp,
    )
    AntButton(
      text = stringResource(R.string.recipe_edit_stage_down_short),
      onClick = onMoveDown,
      modifier = Modifier.testTag("recipe_stage_down_$index").semantics { contentDescription = down },
      size = AntSize.Small,
      icon = AntIcons.Down,
      enabled = canMoveDown,
    )
  }
}

@Composable
private fun Totals(form: RecipeEditForm) {
  val water = form.totalWaterG() ?: return
  val ratio = form.ratio()
  val time = form.totalDurationMs()?.let(::formatClock)
  val timeDescription = time?.let { stringResource(R.string.recipe_total_time_description, it) }
  // Cuatro cadenas completas (con y sin ratio, con y sin tiempo) para poder traducir el orden y el separador.
  @Composable
  fun totals(timeText: String?): String = when {
    ratio != null && timeText != null -> stringResource(R.string.recipe_edit_totals_time, formatGrams(water), formatRatioNumber(ratio), timeText)
    ratio != null -> stringResource(R.string.recipe_edit_totals, formatGrams(water), formatRatioNumber(ratio))
    timeText != null -> stringResource(R.string.recipe_edit_totals_water_time, formatGrams(water), timeText)
    else -> stringResource(R.string.recipe_edit_totals_water, formatGrams(water))
  }
  val text = totals(time)
  val description = totals(timeDescription)
  AntText(
    text,
    Modifier.tourAnchor("recipe_edit_totals").testTag("recipe_edit_totals").semantics { contentDescription = description },
    type = AntTextType.Secondary,
  )
}

@Composable
private fun Preview(form: RecipeEditForm) {
  val recipe = remember(form) { form.toRecipe() } ?: return
  val curve = remember(recipe) { TargetCurve.fromRecipe(recipe) }
  val unit = WeightUnit.GRAMS
  BrewChart(
    samples = emptyList(),
    unit = unit,
    waterUnitLabel = unit.symbol,
    flowUnitLabel = stringResource(R.string.scale_flow_value, unit.symbol),
    description = stringResource(R.string.recipe_edit_preview),
    modifier = Modifier.tourAnchor("recipe_edit_preview").testTag("recipe_edit_preview"),
    target = curve,
  )
}

@Composable
private fun LabeledInput(
  label: String,
  value: String,
  onValueChange: (String) -> Unit,
  modifier: Modifier = Modifier,
  error: FieldError? = null,
  placeholder: String? = null,
  keyboardType: KeyboardType = KeyboardType.Text,
  singleLine: Boolean = true,
  showError: Boolean = true,
  tag: String,
) {
  Column(modifier, verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.xxs)) {
    AntText(label, type = AntTextType.Secondary, style = AntTheme.typography.textSM)
    AntInput(
      value = value,
      onValueChange = onValueChange,
      modifier = Modifier.fillMaxWidth().testTag(tag),
      placeholder = placeholder,
      status = if (error != null) AntInputStatus.Error else AntInputStatus.Default,
      singleLine = singleLine,
      minLines = if (singleLine) 1 else 3,
      keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
    )
    if (error != null && showError) ErrorText(error, "${tag}_error")
  }
}

@Composable
private fun ErrorText(error: FieldError, tag: String) {
  AntText(fieldErrorText(error), Modifier.testTag(tag), type = AntTextType.Danger)
}

@Composable
private fun fieldErrorText(error: FieldError): String = stringResource(
  when (error) {
    FieldError.NAME_REQUIRED -> R.string.recipe_error_name
    FieldError.DOSE_INVALID -> R.string.recipe_error_dose
    FieldError.TEMP_INVALID -> R.string.recipe_error_temp
    FieldError.START_INVALID -> R.string.recipe_error_start
    FieldError.START_OVERLAPS -> R.string.recipe_error_start_overlaps
    FieldError.START_TOO_LATE -> R.string.recipe_error_start_late
    FieldError.WATER_INVALID -> R.string.recipe_error_water
    FieldError.WATER_NOT_INCREASING -> R.string.recipe_error_water_order
    FieldError.DURATION_INVALID -> R.string.recipe_error_duration
    FieldError.STAGE_COUNT -> R.string.recipe_error_stage_count
    FieldError.NO_POUR -> R.string.recipe_error_no_pour
    FieldError.INSTRUCTION_TOO_LONG -> R.string.recipe_error_instruction_long
  },
)
