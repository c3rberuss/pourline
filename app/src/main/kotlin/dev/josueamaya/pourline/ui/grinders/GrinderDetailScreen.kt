package dev.josueamaya.pourline.ui.grinders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.josueamaya.pourline.R
import dev.josueamaya.pourline.grinder.GrindFormat
import dev.josueamaya.pourline.grinder.GrindLevel
import dev.josueamaya.pourline.grinder.Grinder
import dev.josueamaya.pourline.history.BrewMethod
import dev.josueamaya.pourline.ui.components.AntButton
import dev.josueamaya.pourline.ui.components.AntButtonType
import dev.josueamaya.pourline.ui.components.AntIconButton
import dev.josueamaya.pourline.ui.components.AntIcons
import dev.josueamaya.pourline.ui.components.AntMessageHost
import dev.josueamaya.pourline.ui.components.AntModal
import dev.josueamaya.pourline.ui.components.AntSelect
import dev.josueamaya.pourline.ui.components.AntSelectOption
import dev.josueamaya.pourline.ui.components.AntSpin
import dev.josueamaya.pourline.ui.components.AntStatus
import dev.josueamaya.pourline.ui.components.AntTag
import dev.josueamaya.pourline.ui.components.AntTagColor
import dev.josueamaya.pourline.ui.components.AntText
import dev.josueamaya.pourline.ui.components.AntTextType
import dev.josueamaya.pourline.ui.components.AntTopBar
import dev.josueamaya.pourline.ui.components.showAntMessage
import dev.josueamaya.pourline.ui.history.label
import dev.josueamaya.pourline.ui.theme.AntTheme

/**
 * El detalle de un molino (D7): escala, dirección, unidades, la regla con el rango de un método, rangos, anclas, sus
 * equivalencias con otros molinos (D54), la fuente (si es predefinido) y las acciones según de quién sea.
 *
 * @param toCalibrations lleva la sección de equivalencias a la vista, una sola vez ("Añade otra equivalencia").
 */
@Composable
fun GrinderDetailScreen(
  viewModel: GrinderDetailViewModel,
  onBack: () -> Unit,
  onEdit: (Long) -> Unit,
  onDuplicate: (Long) -> Unit,
  toCalibrations: Boolean = false,
) {
  val ui by viewModel.ui.collectAsStateWithLifecycle()
  val draft by viewModel.editor.draft.collectAsStateWithLifecycle()
  val texts = rememberGrindTexts()
  var confirmDelete by rememberSaveable { mutableStateOf(false) }
  var deletingPair by rememberSaveable { mutableStateOf<Long?>(null) }
  var broughtIntoView by rememberSaveable { mutableStateOf(false) }
  val calibrationsRequester = remember { BringIntoViewRequester() }
  val snackbar = remember { SnackbarHostState() }
  val updatedText = stringResource(R.string.grind_calibration_updated)
  LaunchedEffect(ui) { if (ui == GrinderDetailUi.Gone) onBack() }
  LaunchedEffect(viewModel) {
    viewModel.editor.events.collect { event ->
      when (event) {
        CalibrationEvent.Updated -> snackbar.showAntMessage(updatedText, AntStatus.Success)
      }
    }
  }

  Scaffold(
    containerColor = AntTheme.colors.bgLayout,
    snackbarHost = { AntMessageHost(snackbar) },
    topBar = {
      AntTopBar(
        title = (ui as? GrinderDetailUi.Ready)?.grinder?.model.orEmpty(),
        navigationIcon = {
          AntIconButton(AntIcons.ArrowLeft, contentDescription = stringResource(R.string.scale_back), onClick = onBack)
        },
      )
    },
  ) { padding ->
    val ready = ui as? GrinderDetailUi.Ready
    if (ready == null) {
      AntSpin(Modifier.padding(padding).padding(AntTheme.spacing.xl))
      return@Scaffold
    }
    val grinder = ready.grinder
    val showCalibrations = grinder.owned || ready.groups.isNotEmpty()
    LaunchedEffect(toCalibrations, showCalibrations) {
      if (toCalibrations && showCalibrations && !broughtIntoView) {
        broughtIntoView = true
        calibrationsRequester.bringIntoView()
      }
    }
    Column(
      Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(AntTheme.spacing.base),
      verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.base),
    ) {
      Header(grinder, ready.isDefault)
      Facts(grinder)
      Ranges(grinder)
      if (showCalibrations) {
        GrinderCalibrations(
          grinder = grinder,
          groups = ready.groups,
          canAdd = ready.canAddCalibration,
          texts = texts,
          onAdd = viewModel::addCalibration,
          onEdit = viewModel::editCalibration,
          onDelete = { deletingPair = it.id },
          modifier = Modifier.bringIntoViewRequester(calibrationsRequester),
        )
      }
      GrindAttribution(listOf(grinder), testTag = "grinder_attribution")
      Actions(
        ready = ready,
        onDefault = viewModel::setDefault,
        onOwn = { viewModel.setOwned(true) },
        onUnown = { viewModel.setOwned(false) },
        onEdit = { onEdit(grinder.id) },
        onDuplicate = { onDuplicate(grinder.id) },
        onDelete = { confirmDelete = true },
      )
    }
    val sheet = draft
    if (sheet != null) {
      // Al editar, el otro molino puede ser uno retirado: no está entre los que se ofrecen al añadir, pero la hoja lo necesita.
      val editingOther = ready.groups.map { it.other }.filter { g -> g.id == sheet.otherId && ready.others.none { it.id == g.id } }
      CalibrationSheet(
        draft = sheet,
        grinder = grinder,
        others = ready.others + editingOther,
        texts = texts,
        onChange = viewModel.editor::update,
        onSave = viewModel.editor::save,
        onReplace = viewModel.editor::replace,
        onDismiss = viewModel.editor::dismiss,
      )
    }
    val pending = deletingPair?.let { id -> ready.groups.firstNotNullOfOrNull { g -> g.pairs.firstOrNull { it.id == id }?.let { pair -> g to pair } } }
    if (pending != null) {
      val (group, pair) = pending
      AntModal(
        title = stringResource(R.string.grind_calibration_delete_title, calibrationPairText(pair, grinder, group.other, texts)),
        text = "",
        confirmText = stringResource(R.string.grinder_delete_confirm),
        cancelText = stringResource(R.string.grinder_delete_cancel),
        onConfirm = {
          deletingPair = null
          viewModel.deleteCalibration(pair.id)
        },
        onDismiss = { deletingPair = null },
        modifier = Modifier.testTag("grinder_calibration_delete_dialog"),
        danger = true,
      )
    }
    if (confirmDelete) {
      AntModal(
        title = stringResource(R.string.grinder_delete_title, grinder.displayName),
        text = if (ready.recipeCount > 0) {
          pluralStringResource(R.plurals.grinder_delete_usage, ready.recipeCount, ready.recipeCount)
        } else {
          stringResource(R.string.grinder_delete_unused)
        },
        confirmText = stringResource(R.string.grinder_delete_confirm),
        cancelText = stringResource(R.string.grinder_delete_cancel),
        onConfirm = {
          confirmDelete = false
          viewModel.delete()
        },
        onDismiss = { confirmDelete = false },
        modifier = Modifier.testTag("grinder_delete_dialog"),
        danger = true,
      )
    }
  }
}

@Composable
private fun Header(grinder: Grinder, isDefault: Boolean) {
  Row(horizontalArrangement = Arrangement.spacedBy(AntTheme.spacing.xs), verticalAlignment = Alignment.CenterVertically) {
    AntText(grinder.brand, strong = true)
    AntTag(stringResource(if (grinder.isPredefined) R.string.grinder_tag_predefined else R.string.grinder_tag_own))
    if (grinder.retired) AntTag(stringResource(R.string.grinder_tag_retired), color = AntTagColor.Warning)
    if (isDefault) AntTag(stringResource(R.string.grinder_tag_default), Modifier.testTag("grinder_detail_is_default"), color = AntTagColor.Primary)
  }
}

@Composable
private fun Facts(grinder: Grinder) {
  val texts = rememberGrindTexts()
  Column(verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.xs)) {
    InfoRow(
      stringResource(R.string.grinder_detail_scale),
      stringResource(
        R.string.grinder_detail_scale_value,
        GrindFormat.format(grinder.scaleMin, grinder, texts),
        GrindFormat.format(grinder.scaleMax, grinder, texts),
        texts.number(grinder.step, dev.josueamaya.pourline.grinder.decimalsOf(grinder.step)),
      ),
      "grinder_detail_scale",
    )
    InfoRow(stringResource(R.string.grinder_detail_direction), grinder.direction.label(), "grinder_detail_direction")
    InfoRow(stringResource(R.string.grinder_detail_units), grinder.unitsSummary(texts), "grinder_detail_units")
  }
}

@Composable
private fun Ranges(grinder: Grinder) {
  val texts = rememberGrindTexts()
  val methods = BrewMethod.entries.filter { it in grinder.ranges }
  var selected by rememberSaveable { mutableStateOf<BrewMethod?>(null) }
  val method = selected?.takeIf { it in methods } ?: methods.firstOrNull()
  Column(verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.sm)) {
    if (methods.isNotEmpty()) {
      AntSelect(
        options = methods.map { AntSelectOption(it, it.label()) },
        selected = method,
        onSelect = { selected = it },
        label = stringResource(R.string.grinder_detail_method),
        modifier = Modifier.fillMaxWidth(),
        testTag = "grinder_detail_method",
      )
    }
    GrindRuler(grinder, method, value = null, showAnchors = true, modifier = Modifier.fillMaxWidth(), testTag = "grinder_detail_ruler")
    if (methods.isEmpty()) {
      InfoRow(stringResource(R.string.grinder_detail_ranges), stringResource(R.string.grinder_detail_no_ranges), "grinder_detail_ranges")
    } else {
      InfoRow(
        stringResource(R.string.grinder_detail_ranges),
        methods.map { m ->
          val r = grinder.ranges.getValue(m)
          stringResource(R.string.grinder_detail_range_item, m.label(), texts.number(r.min, decimalsFor(grinder)), texts.number(r.max, decimalsFor(grinder)))
        }.joinToString(" · "),
        "grinder_detail_ranges",
      )
    }
    val anchors = GrindLevel.entries.filter { it in grinder.anchors }
    if (anchors.isNotEmpty()) {
      InfoRow(
        stringResource(R.string.grinder_detail_anchors),
        anchors.map { level ->
          stringResource(R.string.grinder_detail_anchor_item, level.label(), texts.number(grinder.anchors.getValue(level), decimalsFor(grinder)))
        }.joinToString(" · "),
        "grinder_detail_anchors",
      )
    }
  }
}

private fun decimalsFor(grinder: Grinder) = dev.josueamaya.pourline.grinder.decimalsOf(grinder.step)

@Composable
private fun InfoRow(label: String, value: String, tag: String) {
  Column(Modifier.fillMaxWidth().testTag(tag).semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.xxs)) {
    AntText(label, type = AntTextType.Secondary, style = AntTheme.typography.textSM)
    AntText(value)
  }
}

@Composable
private fun Actions(
  ready: GrinderDetailUi.Ready,
  onDefault: () -> Unit,
  onOwn: () -> Unit,
  onUnown: () -> Unit,
  onEdit: () -> Unit,
  onDuplicate: () -> Unit,
  onDelete: () -> Unit,
) {
  val grinder = ready.grinder
  Column(verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.sm)) {
    if (grinder.isPredefined && !grinder.owned) {
      DetailButton(R.string.grinder_detail_own, "grinder_detail_own", onOwn, AntButtonType.Primary)
    }
    // El predeterminado tiene que ser de mis molinos (D7).
    if (grinder.owned && !ready.isDefault) DetailButton(R.string.grinder_detail_default, "grinder_detail_default", onDefault, AntButtonType.Primary)
    if (!grinder.isPredefined) DetailButton(R.string.grinder_detail_edit, "grinder_detail_edit", onEdit)
    DetailButton(R.string.grinder_detail_duplicate, "grinder_detail_duplicate", onDuplicate)
    if (grinder.isPredefined && grinder.owned) DetailButton(R.string.grinder_detail_unown, "grinder_detail_unown", onUnown)
    if (!grinder.isPredefined) DetailButton(R.string.grinder_detail_delete, "grinder_detail_delete", onDelete, danger = true)
  }
}

@Composable
private fun DetailButton(text: Int, tag: String, onClick: () -> Unit, type: AntButtonType = AntButtonType.Default, danger: Boolean = false) {
  AntButton(stringResource(text), onClick, Modifier.fillMaxWidth().testTag(tag), type = type, danger = danger)
}
