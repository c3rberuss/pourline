package dev.dotscale.ui.grinders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.dotscale.R
import dev.dotscale.grinder.GrindFormat
import dev.dotscale.grinder.GrindLevel
import dev.dotscale.grinder.Grinder
import dev.dotscale.history.BrewMethod
import dev.dotscale.ui.components.AntButton
import dev.dotscale.ui.components.AntButtonType
import dev.dotscale.ui.components.AntIconButton
import dev.dotscale.ui.components.AntIcons
import dev.dotscale.ui.components.AntModal
import dev.dotscale.ui.components.AntSelect
import dev.dotscale.ui.components.AntSelectOption
import dev.dotscale.ui.components.AntSpin
import dev.dotscale.ui.components.AntTag
import dev.dotscale.ui.components.AntTagColor
import dev.dotscale.ui.components.AntText
import dev.dotscale.ui.components.AntTextType
import dev.dotscale.ui.components.AntTopBar
import dev.dotscale.ui.history.label
import dev.dotscale.ui.theme.AntTheme

/**
 * El detalle de un molino (D7): escala, dirección, unidades, la regla con el rango de un método, rangos, anclas, la
 * fuente (si es predefinido) y las acciones según de quién sea.
 */
@Composable
fun GrinderDetailScreen(
  viewModel: GrinderDetailViewModel,
  onBack: () -> Unit,
  onEdit: (Long) -> Unit,
  onDuplicate: (Long) -> Unit,
) {
  val ui by viewModel.ui.collectAsStateWithLifecycle()
  var confirmDelete by rememberSaveable { mutableStateOf(false) }
  LaunchedEffect(ui) { if (ui == GrinderDetailUi.Gone) onBack() }

  Scaffold(
    containerColor = AntTheme.colors.bgLayout,
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
    Column(
      Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(AntTheme.spacing.base),
      verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.base),
    ) {
      Header(grinder, ready.isDefault)
      Facts(grinder)
      Ranges(grinder)
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
        texts.number(grinder.step, dev.dotscale.grinder.decimalsOf(grinder.step)),
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

private fun decimalsFor(grinder: Grinder) = dev.dotscale.grinder.decimalsOf(grinder.step)

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
