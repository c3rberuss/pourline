package dev.dotscale.ui.grinders

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.dotscale.R
import dev.dotscale.grinder.GrindDirection
import dev.dotscale.grinder.GrindLevel
import dev.dotscale.grinder.GrindUnit
import dev.dotscale.grinder.GrinderError
import dev.dotscale.grinder.clampToScale
import dev.dotscale.grinder.multipliers
import dev.dotscale.grinder.snap
import dev.dotscale.history.BrewMethod
import dev.dotscale.ui.components.AntButton
import dev.dotscale.ui.components.AntButtonType
import dev.dotscale.ui.components.AntFormItem
import dev.dotscale.ui.components.AntIconButton
import dev.dotscale.ui.components.AntIcons
import dev.dotscale.ui.components.AntInput
import dev.dotscale.ui.components.AntInputStatus
import dev.dotscale.ui.components.AntModal
import dev.dotscale.ui.components.AntSegmented
import dev.dotscale.ui.components.AntSegmentedOption
import dev.dotscale.ui.components.AntSelect
import dev.dotscale.ui.components.AntSelectOption
import dev.dotscale.ui.components.AntSpin
import dev.dotscale.ui.components.AntTabItem
import dev.dotscale.ui.components.AntTabs
import dev.dotscale.ui.components.AntText
import dev.dotscale.ui.components.AntTextType
import dev.dotscale.ui.components.AntTopBar
import dev.dotscale.ui.history.label
import dev.dotscale.ui.theme.AntSize
import dev.dotscale.ui.theme.AntTheme

private enum class EditTab { DESCRIPTION, RANGES }

/** El editor de molinos propios (§3.7): pestañas "Descripción" y "Rangos". */
@Composable
fun GrinderEditScreen(viewModel: GrinderEditViewModel, openRanges: Boolean, onBack: () -> Unit) {
  val status by viewModel.status.collectAsStateWithLifecycle()
  val form by viewModel.form.collectAsStateWithLifecycle()
  val initial by viewModel.initial.collectAsStateWithLifecycle()
  var tab by rememberSaveable { mutableStateOf(if (openRanges) EditTab.RANGES else EditTab.DESCRIPTION) }
  var confirmDiscard by rememberSaveable { mutableStateOf(false) }

  LaunchedEffect(status) {
    if (status == GrinderEditStatus.Saved || status == GrinderEditStatus.Gone) onBack()
  }

  val ready = status == GrinderEditStatus.Ready
  val dirty = form.isDirty(initial) && ready
  val requestBack = { if (dirty) confirmDiscard = true else onBack() }
  BackHandler(enabled = dirty) { confirmDiscard = true }
  val errors = remember(form) { form.errors() }

  Scaffold(
    containerColor = AntTheme.colors.bgLayout,
    topBar = {
      AntTopBar(
        title = stringResource(if (viewModel.isExisting) R.string.grinder_edit_title else R.string.grinder_edit_title_new),
        navigationIcon = {
          AntIconButton(AntIcons.ArrowLeft, contentDescription = stringResource(R.string.scale_back), onClick = requestBack)
        },
        actions = {
          AntButton(
            text = stringResource(R.string.grinder_edit_save),
            onClick = viewModel::save,
            modifier = Modifier.padding(end = AntTheme.spacing.xs).testTag("grinder_edit_save"),
            type = AntButtonType.Primary,
            enabled = errors.isEmpty() && ready,
            loading = status == GrinderEditStatus.Saving,
          )
        },
      )
    },
  ) { padding ->
    if (status == GrinderEditStatus.Loading) {
      AntSpin(Modifier.padding(padding).padding(AntTheme.spacing.xl))
      return@Scaffold
    }
    Column(Modifier.fillMaxSize().padding(padding)) {
      AntTabs(
        items = listOf(
          AntTabItem(EditTab.DESCRIPTION, stringResource(R.string.grinder_edit_tab_description), "grinder_edit_tab_description"),
          AntTabItem(EditTab.RANGES, stringResource(R.string.grinder_edit_tab_ranges), "grinder_edit_tab_ranges"),
        ),
        selected = tab,
        onSelect = { tab = it },
        modifier = Modifier.background(AntTheme.colors.bgContainer),
      )
      when (tab) {
        EditTab.DESCRIPTION -> DescriptionTab(form, errors, viewModel::update)
        EditTab.RANGES -> RangesTab(form, errors, viewModel::update)
      }
    }
  }

  if (confirmDiscard) {
    AntModal(
      title = stringResource(R.string.grinder_edit_discard_title),
      text = stringResource(R.string.grinder_edit_discard_text),
      confirmText = stringResource(R.string.grinder_edit_discard),
      cancelText = stringResource(R.string.grinder_edit_keep),
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
private fun grinderErrorText(error: GrinderError): String = stringResource(
  when (error) {
    GrinderError.BlankBrand -> R.string.grinder_error_brand
    GrinderError.BlankModel -> R.string.grinder_error_model
    GrinderError.NoUnits, GrinderError.PrimaryWithPerParent, GrinderError.RangeForOther -> R.string.grinder_error_units
    GrinderError.ScaleOrder -> R.string.grinder_error_scale_order
    GrinderError.StepNotPositive -> R.string.grinder_error_step
    GrinderError.ScaleNotMultipleOfStep -> R.string.grinder_error_scale_step
    GrinderError.NegativeCompoundScale -> R.string.grinder_error_negative_scale
    is GrinderError.DuplicateUnit -> R.string.grinder_error_duplicate_unit
    is GrinderError.PerParentTooSmall -> R.string.grinder_error_per_parent
    GrinderError.MissingValue -> R.string.grinder_error_missing
    is GrinderError.RangeOrder -> R.string.grinder_error_range_order
    is GrinderError.RangeOutsideScale, is GrinderError.AnchorOutsideScale -> R.string.grinder_error_outside_scale
    is GrinderError.AnchorsNotMonotonic -> R.string.grinder_error_anchors_order
  },
)

private fun fieldTag(field: GrinderField): String = "grinder_edit_error_" + when (field) {
  GrinderField.Brand -> "brand"
  GrinderField.Model -> "model"
  GrinderField.Units -> "units"
  is GrinderField.Unit -> "unit_${field.index}"
  is GrinderField.PerParent -> "per_parent_${field.index}"
  GrinderField.Step -> "step"
  GrinderField.Scale -> "scale"
  is GrinderField.Range -> "range_${field.method.name}"
  is GrinderField.Anchor -> "anchor_${field.level.name}"
}

@Composable
private fun ErrorLine(errors: Map<GrinderField, GrinderError>, field: GrinderField) {
  errors[field]?.let { AntText(grinderErrorText(it), Modifier.testTag(fieldTag(field)), type = AntTextType.Danger, style = AntTheme.typography.textSM) }
}

@Composable
private fun DescriptionTab(form: GrinderEditForm, errors: Map<GrinderField, GrinderError>, update: ((GrinderEditForm) -> GrinderEditForm) -> Unit) {
  val texts = rememberGrindTexts()
  val unitOptions = GrindUnit.entries.map { AntSelectOption(it, texts.unitName(it)) }
  Column(
    Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(AntTheme.spacing.base),
    verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.base),
  ) {
    TextField(stringResource(R.string.grinder_edit_brand), form.brand, errors[GrinderField.Brand] != null, "grinder_edit_brand") { v -> update { it.copy(brand = v) } }
    ErrorLine(errors, GrinderField.Brand)
    TextField(stringResource(R.string.grinder_edit_model), form.model, errors[GrinderField.Model] != null, "grinder_edit_model") { v -> update { it.copy(model = v) } }
    ErrorLine(errors, GrinderField.Model)

    form.units.forEachIndexed { i, unit ->
      Column(verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.xxs)) {
        Row(horizontalArrangement = Arrangement.spacedBy(AntTheme.spacing.xs), verticalAlignment = Alignment.Bottom) {
          AntSelect(
            options = unitOptions,
            selected = unit.unit,
            onSelect = { selected -> update { it.setUnit(i, selected) } },
            label = if (i == 0) stringResource(R.string.grinder_edit_unit_primary) else stringResource(R.string.grinder_edit_unit_n, i + 1),
            modifier = Modifier.weight(1f),
            testTag = "grinder_edit_unit_$i",
          )
          if (i > 0) {
            val parent = form.units[i - 1].unit
            AntFormItem(label = stringResource(R.string.grinder_edit_per_parent, texts.quantity(parent, 1.0).substringAfter(' ')), modifier = Modifier.weight(0.6f)) {
              AntInput(
                value = unit.perParent,
                onValueChange = { v -> update { it.setPerParent(i, v.filter(Char::isDigit)) } },
                modifier = Modifier.fillMaxWidth().testTag("grinder_edit_per_parent_$i"),
                status = if (errors[GrinderField.PerParent(i)] != null) AntInputStatus.Error else AntInputStatus.Default,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
              )
            }
          }
          if (form.units.size > 1) {
            AntIconButton(
              AntIcons.Close,
              contentDescription = stringResource(R.string.grinder_edit_remove_unit),
              onClick = { update { it.removeUnit(i) } },
              modifier = Modifier.testTag("grinder_edit_unit_remove_$i"),
            )
          }
        }
        ErrorLine(errors, GrinderField.Unit(i))
        ErrorLine(errors, GrinderField.PerParent(i))
      }
    }
    ErrorLine(errors, GrinderField.Units)
    AntButton(
      text = stringResource(R.string.grinder_edit_add_unit),
      onClick = { update { it.addUnit() } },
      modifier = Modifier.fillMaxWidth().testTag("grinder_edit_add_unit"),
      type = AntButtonType.Dashed,
      icon = AntIcons.Plus,
      enabled = form.canAddUnit,
    )

    AntFormItem(label = stringResource(R.string.grinder_edit_step), help = texts.unitName(form.units.last().unit)) {
      AntInput(
        value = form.step,
        onValueChange = { v -> update { it.copy(step = v) } },
        modifier = Modifier.fillMaxWidth().testTag("grinder_edit_step"),
        status = if (errors[GrinderField.Step] != null) AntInputStatus.Error else AntInputStatus.Default,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
      )
    }
    ErrorLine(errors, GrinderField.Step)

    AntFormItem(label = stringResource(R.string.grinder_edit_direction)) {
      AntSegmented(
        options = GrindDirection.entries.map { AntSegmentedOption(it, it.label()) },
        selected = form.direction,
        onSelect = { d -> update { it.copy(direction = d) } },
        modifier = Modifier.fillMaxWidth().testTag("grinder_edit_direction"),
      )
    }
  }
}

@Composable
private fun TextField(label: String, value: String, error: Boolean, tag: String, onChange: (String) -> Unit) {
  AntFormItem(label = label) {
    AntInput(
      value = value,
      onValueChange = onChange,
      modifier = Modifier.fillMaxWidth().testTag(tag),
      status = if (error) AntInputStatus.Error else AntInputStatus.Default,
    )
  }
}

@Composable
private fun RangesTab(form: GrinderEditForm, errors: Map<GrinderField, GrinderError>, update: ((GrinderEditForm) -> GrinderEditForm) -> Unit) {
  var previewMethod by rememberSaveable { mutableStateOf<BrewMethod?>(null) }
  val limited = form.inputGrinder(limited = true)
  val unlimited = form.inputGrinder(limited = false)
  val preview = remember(form) { form.previewGrinder() }
  val span = limited.scaleMax - limited.scaleMin

  Column(Modifier.fillMaxSize().imePadding()) {
    if (preview != null) {
      GrindRuler(
        preview,
        previewMethod,
        value = null,
        showAnchors = true,
        modifier = Modifier.fillMaxWidth().background(AntTheme.colors.bgContainer).padding(AntTheme.spacing.base),
        testTag = "grinder_edit_preview",
      )
    }
    Column(
      Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(AntTheme.spacing.base),
      verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.base),
    ) {
      AntText(stringResource(R.string.grinder_edit_scale), strong = true)
      LabeledInput(stringResource(R.string.grinder_edit_from)) {
        GrindInput(form.scaleMin, unlimited, { v -> update { it.copy(scaleMin = v) } }, start = 0.0, testTag = "grinder_edit_scale_min", clampToScale = false)
      }
      LabeledInput(stringResource(R.string.grinder_edit_to)) {
        val start = if (unlimited.units.size > 1) multipliers(unlimited.units)[0] * 4.0 else 30.0
        GrindInput(form.scaleMax, unlimited, { v -> update { it.copy(scaleMax = v) } }, start = start, testTag = "grinder_edit_scale_max", clampToScale = false)
      }
      ErrorLine(errors, GrinderField.Scale)

      AntText(stringResource(R.string.grinder_edit_ranges_title), strong = true)
      for (method in BrewMethod.entries.filter { it != BrewMethod.OTHER }) {
        val range = form.ranges[method]
        Column(verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.xs)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            AntText(
              method.label(),
              Modifier.weight(1f).testTag("grinder_edit_range_${method.name}").clickable { previewMethod = method },
              strong = previewMethod == method,
            )
            if (range == null) {
              AntButton(
                text = stringResource(R.string.grinder_edit_add_range),
                onClick = {
                  previewMethod = method
                  update { it.setRange(method, RangeForm()) }
                },
                modifier = Modifier.testTag("grinder_edit_range_add_${method.name}"),
                size = AntSize.Small,
                icon = AntIcons.Plus,
              )
            } else {
              AntIconButton(
                AntIcons.Close,
                contentDescription = stringResource(R.string.grinder_edit_remove),
                onClick = { update { it.setRange(method, null) } },
                modifier = Modifier.testTag("grinder_edit_range_remove_${method.name}"),
              )
            }
          }
          if (range != null) {
            val quarter = snap(limited.scaleMin + span * 0.25, limited)
            val threeQuarters = snap(limited.scaleMin + span * 0.75, limited)
            LabeledInput(stringResource(R.string.grinder_edit_from)) {
              GrindInput(range.min, limited, { v -> update { it.setRange(method, range.copy(min = v)) } }, start = quarter, testTag = "grinder_edit_range_${method.name}_min")
            }
            LabeledInput(stringResource(R.string.grinder_edit_to)) {
              GrindInput(range.max, limited, { v -> update { it.setRange(method, range.copy(max = v)) } }, start = threeQuarters, testTag = "grinder_edit_range_${method.name}_max")
            }
            ErrorLine(errors, GrinderField.Range(method))
          }
        }
      }

      AntText(stringResource(R.string.grinder_edit_anchors_title), strong = true)
      for (level in GrindLevel.entries) {
        val present = level in form.anchors
        Column(verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.xs)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            AntText(level.label(), Modifier.weight(1f))
            if (!present) {
              AntButton(
                text = stringResource(R.string.grinder_edit_add_anchor),
                onClick = { update { it.setAnchor(level, null) } },
                modifier = Modifier.testTag("grinder_edit_anchor_add_${level.name}"),
                size = AntSize.Small,
                icon = AntIcons.Plus,
              )
            } else {
              AntIconButton(
                AntIcons.Close,
                contentDescription = stringResource(R.string.grinder_edit_remove),
                onClick = { update { it.setAnchor(level, null, present = false) } },
                modifier = Modifier.testTag("grinder_edit_anchor_remove_${level.name}"),
              )
            }
          }
          if (present) {
            // Del extremo fino al grueso: el centro de la banda del nivel.
            val fraction = (level.ordinal + 0.5) / GrindLevel.entries.size
            val raw = if (limited.direction == GrindDirection.HIGHER_IS_COARSER) limited.scaleMin + span * fraction else limited.scaleMax - span * fraction
            GrindInput(form.anchors[level], limited, { v -> update { it.setAnchor(level, v) } }, start = clampToScale(snap(raw, limited), limited), testTag = "grinder_edit_anchor_${level.name}")
            ErrorLine(errors, GrinderField.Anchor(level))
          }
        }
      }
    }
  }
}

@Composable
private fun LabeledInput(label: String, content: @Composable () -> Unit) {
  Row(horizontalArrangement = Arrangement.spacedBy(AntTheme.spacing.sm), verticalAlignment = Alignment.CenterVertically) {
    AntText(label, type = AntTextType.Secondary, modifier = Modifier.padding(end = AntTheme.spacing.xs))
    content()
  }
}
