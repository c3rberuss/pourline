package dev.dotscale.ui.coffees

import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.dotscale.R
import dev.dotscale.coffee.CoffeeError
import dev.dotscale.coffee.CoffeeProcess
import dev.dotscale.coffee.RoastLevel
import dev.dotscale.ui.components.AntAlert
import dev.dotscale.ui.components.AntAutoComplete
import dev.dotscale.ui.components.AntButton
import dev.dotscale.ui.components.AntButtonType
import dev.dotscale.ui.components.AntDatePicker
import dev.dotscale.ui.components.AntDivider
import dev.dotscale.ui.components.AntDividerOrientation
import dev.dotscale.ui.components.AntFormItem
import dev.dotscale.ui.components.AntIconButton
import dev.dotscale.ui.components.AntIcons
import dev.dotscale.ui.components.AntInput
import dev.dotscale.ui.components.AntInputStatus
import dev.dotscale.ui.components.AntMessageHost
import dev.dotscale.ui.components.AntModal
import dev.dotscale.ui.components.AntSelect
import dev.dotscale.ui.components.AntSelectOption
import dev.dotscale.ui.components.AntSpin
import dev.dotscale.ui.components.AntStatus
import dev.dotscale.ui.components.AntTag
import dev.dotscale.ui.components.AntTagColor
import dev.dotscale.ui.components.AntText
import dev.dotscale.ui.components.AntTopBar
import dev.dotscale.ui.components.showAntMessage
import dev.dotscale.ui.theme.AntSize
import dev.dotscale.ui.theme.AntTheme
import dev.dotscale.ui.theme.DotScaleTheme
import java.time.LocalDate

private const val NOTES_MIN_LINES = 3

@Composable
fun CoffeeEditScreen(
  viewModel: CoffeeEditViewModel,
  newCameraTarget: () -> java.io.File,
  onBack: () -> Unit,
  onSaved: (id: Long) -> Unit,
  today: () -> LocalDate = LocalDate::now,
  isEdit: Boolean = false,
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val form by viewModel.form.collectAsStateWithLifecycle()
  val initial by viewModel.initial.collectAsStateWithLifecycle()
  val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
  val photoBusy by viewModel.photoBusy.collectAsStateWithLifecycle()
  val ocrRunning by viewModel.ocrRunning.collectAsStateWithLifecycle()
  val ocrAlert by viewModel.ocrAlert.collectAsStateWithLifecycle()
  val snackbar = remember { SnackbarHostState() }
  var confirmDiscard by rememberSaveable { mutableStateOf(false) }

  val ocrFailed = stringResource(R.string.ocr_failed)
  val photoFailed = stringResource(R.string.coffee_photo_failed)
  LaunchedEffect(viewModel) {
    viewModel.events.collect { event ->
      when (event) {
        CoffeeEditEvent.OcrEmpty -> snackbar.showAntMessage(ocrFailed, AntStatus.Warning)
        CoffeeEditEvent.PhotoFailed -> snackbar.showAntMessage(photoFailed, AntStatus.Error)
        is CoffeeEditEvent.OcrFilled -> Unit
      }
    }
  }
  LaunchedEffect(state) {
    when (val s = state) {
      is CoffeeEditState.Saved -> onSaved(s.id)
      CoffeeEditState.Gone -> onBack()
      else -> Unit
    }
  }

  val ready = state == CoffeeEditState.Ready
  val dirty = form.isDirty(initial) && ready
  val errors = remember(form) { form.errors(today()) }
  var touched by rememberSaveable { mutableStateOf(false) }
  LaunchedEffect(dirty) { if (dirty) touched = true }
  val leave = {
    viewModel.discard()
    onBack()
  }
  val requestBack = { if (dirty) confirmDiscard = true else leave() }
  BackHandler(enabled = dirty) { confirmDiscard = true }

  Scaffold(
    containerColor = AntTheme.colors.bgLayout,
    topBar = {
      AntTopBar(
        title = stringResource(
          when {
            viewModel.isNewBag -> R.string.coffee_edit_title_bag
            isEdit -> R.string.coffee_edit_title_edit
            else -> R.string.coffee_edit_title_new
          },
        ),
        navigationIcon = {
          AntIconButton(AntIcons.ArrowLeft, contentDescription = stringResource(R.string.scale_back), onClick = requestBack)
        },
        actions = {
          AntButton(
            text = stringResource(R.string.coffee_edit_save),
            onClick = viewModel::save,
            modifier = Modifier.padding(end = AntTheme.spacing.xs).testTag("coffee_edit_save"),
            type = AntButtonType.Primary,
            size = AntSize.Small,
            enabled = ready && errors.isEmpty(),
            loading = state == CoffeeEditState.Saving,
          )
        },
      )
    },
    snackbarHost = { AntMessageHost(snackbar) },
  ) { padding ->
    if (state == CoffeeEditState.Loading) {
      AntSpin(Modifier.padding(padding).padding(AntTheme.spacing.xl))
      return@Scaffold
    }
    // Los errores aparecen cuando el usuario ya tocó algo (aunque lo deshaga): un formulario nuevo no empieza en rojo.
    val shown = if (touched) errors else emptyMap()
    Column(
      Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(AntTheme.spacing.base),
      verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.base),
    ) {
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AntTheme.spacing.sm)) {
        listOf(1 to form.photo1, 2 to form.photo2).forEach { (slot, photo) ->
          CoffeePhotoSlot(
            slot = slot,
            photo = photo,
            busy = slot in photoBusy,
            newCameraTarget = newCameraTarget,
            onPhoto = { open -> viewModel.addPhoto(slot, open) },
            onRemove = { viewModel.removePhoto(slot) },
            modifier = Modifier.weight(1f),
          )
        }
      }
      AntButton(
        text = stringResource(R.string.coffee_edit_read_label),
        onClick = viewModel::readLabel,
        modifier = Modifier.fillMaxWidth().testTag("coffee_edit_read_label"),
        icon = AntIcons.ScanText,
        enabled = (form.photo1 != null || form.photo2 != null) && photoBusy.isEmpty() && !ocrRunning,
        loading = ocrRunning,
      )
      ocrAlert?.let { count ->
        AntAlert(
          message = pluralStringResource(R.plurals.ocr_alert_title, count, count),
          modifier = Modifier.testTag("coffee_edit_ocr_alert"),
          type = AntStatus.Info,
          description = stringResource(R.string.ocr_alert_text),
          onClose = viewModel::dismissOcrAlert,
        )
      }

      CoffeeFields(form, shown, suggestions, viewModel::update, viewModel::addVariety, viewModel::removeVariety, today)
    }
  }

  if (confirmDiscard) {
    AntModal(
      title = stringResource(R.string.brew_edit_discard_title),
      text = stringResource(R.string.brew_edit_discard_text),
      confirmText = stringResource(R.string.brew_edit_discard),
      cancelText = stringResource(R.string.brew_edit_keep),
      onConfirm = {
        confirmDiscard = false
        leave()
      },
      onDismiss = { confirmDiscard = false },
      danger = true,
    )
  }
}

/** La marca "Leído de la etiqueta" (D8) de un campo rellenado por el OCR. */
@Composable
private fun OcrMark(field: CoffeeField) {
  AntTag(
    text = stringResource(R.string.ocr_mark),
    modifier = Modifier.testTag("ocr_mark_${field.name.lowercase()}"),
    color = AntTagColor.Primary,
    icon = AntIcons.ScanText,
  )
}

@Composable
private fun CoffeeFields(
  form: CoffeeForm,
  errors: Map<CoffeeField, CoffeeError>,
  suggestions: dev.dotscale.coffee.CoffeeSuggestions,
  onUpdate: (CoffeeField, CoffeeForm.() -> CoffeeForm) -> Unit,
  onAddVariety: (String) -> Unit,
  onRemoveVariety: (String) -> Unit,
  today: () -> LocalDate,
) {
  fun status(field: CoffeeField) = if (field in errors) AntInputStatus.Error else AntInputStatus.Default

  @Composable
  fun Item(field: CoffeeField, label: Int, required: Boolean = false, content: @Composable () -> Unit) {
    AntFormItem(
      label = stringResource(label),
      required = required,
      error = errors[field]?.message(),
      errorTag = "coffee_edit_${field.name.lowercase()}_error",
      extra = if (field.mark in form.ocrFilled) {
        { OcrMark(field.mark) }
      } else {
        null
      },
      content = content,
    )
  }

  AntDivider(text = stringResource(R.string.coffee_edit_section_coffee), orientation = AntDividerOrientation.Left)
  Item(CoffeeField.NAME, R.string.coffee_field_name, required = true) {
    AntInput(
      value = form.name,
      onValueChange = { v -> onUpdate(CoffeeField.NAME) { copy(name = v) } },
      modifier = Modifier.fillMaxWidth().testTag("coffee_edit_name"),
      status = status(CoffeeField.NAME),
    )
  }
  Item(CoffeeField.ROASTER, R.string.coffee_field_roaster) {
    AntAutoComplete(
      value = form.roaster,
      onValueChange = { v -> onUpdate(CoffeeField.ROASTER) { copy(roaster = v) } },
      suggestions = suggestions.roasters,
      modifier = Modifier.fillMaxWidth().testTag("coffee_edit_roaster"),
    )
  }

  AntDivider(text = stringResource(R.string.coffee_edit_section_origin), orientation = AntDividerOrientation.Left)
  Row(horizontalArrangement = Arrangement.spacedBy(AntTheme.spacing.sm)) {
    Column(Modifier.weight(1f)) {
      Item(CoffeeField.COUNTRY, R.string.coffee_field_country) {
        AntAutoComplete(
          value = form.country,
          onValueChange = { v -> onUpdate(CoffeeField.COUNTRY) { copy(country = v) } },
          suggestions = suggestions.countries,
          modifier = Modifier.fillMaxWidth().testTag("coffee_edit_country"),
        )
      }
    }
    Column(Modifier.weight(1f)) {
      Item(CoffeeField.REGION, R.string.coffee_field_region) {
        AntAutoComplete(
          value = form.region,
          onValueChange = { v -> onUpdate(CoffeeField.REGION) { copy(region = v) } },
          suggestions = suggestions.regions,
          modifier = Modifier.fillMaxWidth().testTag("coffee_edit_region"),
        )
      }
    }
  }
  Item(CoffeeField.FARM, R.string.coffee_field_farm) {
    AntInput(
      value = form.farm,
      onValueChange = { v -> onUpdate(CoffeeField.FARM) { copy(farm = v) } },
      modifier = Modifier.fillMaxWidth().testTag("coffee_edit_farm"),
    )
  }
  Item(CoffeeField.VARIETIES, R.string.coffee_field_varieties) {
    VarietiesField(
      varieties = form.varieties,
      text = form.varietyText,
      suggestions = suggestions.varieties,
      onTextChange = { v -> onUpdate(CoffeeField.VARIETIES) { copy(varietyText = v) } },
      onAdd = onAddVariety,
      onRemove = onRemoveVariety,
    )
  }
  Item(CoffeeField.PROCESS, R.string.coffee_field_process) {
    Column(verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.xs)) {
      val none = stringResource(R.string.coffee_none)
      val options = listOf(AntSelectOption<CoffeeProcess?>(null, none)) +
        CoffeeProcess.entries.map { AntSelectOption<CoffeeProcess?>(it, it.label()) }
      AntSelect(
        options = options,
        selected = form.process,
        onSelect = { p -> onUpdate(CoffeeField.PROCESS) { copy(process = p, processOther = if (p == CoffeeProcess.OTHER) processOther else "") } },
        label = null,
        modifier = Modifier.fillMaxWidth(),
        placeholder = none,
        testTag = "coffee_edit_process",
      )
      if (form.process == CoffeeProcess.OTHER) {
        AntInput(
          value = form.processOther,
          onValueChange = { v -> onUpdate(CoffeeField.PROCESS_OTHER) { copy(processOther = v) } },
          modifier = Modifier.fillMaxWidth().testTag("coffee_edit_process_other"),
          placeholder = stringResource(R.string.coffee_field_process_other),
        )
      }
    }
  }
  Item(CoffeeField.ALTITUDE_MIN, R.string.coffee_field_altitude) {
    Row(horizontalArrangement = Arrangement.spacedBy(AntTheme.spacing.xs), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
      AntInput(
        value = form.altitudeMin,
        onValueChange = { v -> onUpdate(CoffeeField.ALTITUDE_MIN) { copy(altitudeMin = v) } },
        modifier = Modifier.weight(1f).testTag("coffee_edit_altitude_min"),
        placeholder = stringResource(R.string.coffee_field_altitude_from),
        status = status(CoffeeField.ALTITUDE_MIN),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
      )
      AntText("–")
      AntInput(
        value = form.altitudeMax,
        onValueChange = { v -> onUpdate(CoffeeField.ALTITUDE_MAX) { copy(altitudeMax = v) } },
        modifier = Modifier.weight(1f).testTag("coffee_edit_altitude_max"),
        placeholder = stringResource(R.string.coffee_field_altitude_to),
        status = status(CoffeeField.ALTITUDE_MIN),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
      )
    }
  }

  AntDivider(text = stringResource(R.string.coffee_edit_section_roast), orientation = AntDividerOrientation.Left)
  Row(horizontalArrangement = Arrangement.spacedBy(AntTheme.spacing.sm)) {
    Column(Modifier.weight(1f)) {
      Item(CoffeeField.ROAST_LEVEL, R.string.coffee_field_roast_level) {
        val none = stringResource(R.string.coffee_none)
        val options = listOf(AntSelectOption<RoastLevel?>(null, none)) +
          RoastLevel.entries.map { AntSelectOption<RoastLevel?>(it, it.label()) }
        AntSelect(
          options = options,
          selected = form.roastLevel,
          onSelect = { l -> onUpdate(CoffeeField.ROAST_LEVEL) { copy(roastLevel = l) } },
          label = null,
          modifier = Modifier.fillMaxWidth(),
          placeholder = none,
          testTag = "coffee_edit_roast_level",
        )
      }
    }
    Column(Modifier.weight(1f)) {
      Item(CoffeeField.ROAST_DATE, R.string.coffee_field_roast_date) {
        AntDatePicker(
          value = form.roastDate,
          onValueChange = { d -> onUpdate(CoffeeField.ROAST_DATE) { copy(roastDate = d) } },
          modifier = Modifier.fillMaxWidth(),
          placeholder = stringResource(R.string.coffee_field_date_hint),
          maxDate = today(),
          status = status(CoffeeField.ROAST_DATE),
          testTag = "coffee_edit_roast_date",
        )
      }
    }
  }
  Item(CoffeeField.ROASTER_NOTES, R.string.coffee_field_roaster_notes) {
    AntInput(
      value = form.roasterNotes,
      onValueChange = { v -> onUpdate(CoffeeField.ROASTER_NOTES) { copy(roasterNotes = v) } },
      modifier = Modifier.fillMaxWidth().testTag("coffee_edit_roaster_notes"),
      singleLine = false,
      minLines = NOTES_MIN_LINES,
    )
  }

  AntDivider(text = stringResource(R.string.coffee_edit_section_bag), orientation = AntDividerOrientation.Left)
  Row(horizontalArrangement = Arrangement.spacedBy(AntTheme.spacing.sm)) {
    Column(Modifier.weight(1f)) {
      Item(CoffeeField.BAG_WEIGHT, R.string.coffee_field_bag_weight) {
        AntInput(
          value = form.bagWeight,
          onValueChange = { v -> onUpdate(CoffeeField.BAG_WEIGHT) { copy(bagWeight = v) } },
          modifier = Modifier.fillMaxWidth().testTag("coffee_edit_bag_weight"),
          status = status(CoffeeField.BAG_WEIGHT),
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
      }
    }
    Column(Modifier.weight(1f)) {
      Item(CoffeeField.PRICE, R.string.coffee_field_price) {
        AntInput(
          value = form.price,
          onValueChange = { v -> onUpdate(CoffeeField.PRICE) { copy(price = v) } },
          modifier = Modifier.fillMaxWidth().testTag("coffee_edit_price"),
          prefix = { AntText("$", type = dev.dotscale.ui.components.AntTextType.Secondary) },
          status = status(CoffeeField.PRICE),
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
      }
    }
  }
  Item(CoffeeField.OPENED_DATE, R.string.coffee_field_opened_date) {
    AntDatePicker(
      value = form.openedDate,
      onValueChange = { d -> onUpdate(CoffeeField.OPENED_DATE) { copy(openedDate = d) } },
      modifier = Modifier.fillMaxWidth(),
      placeholder = stringResource(R.string.coffee_field_date_hint),
      maxDate = today(),
      status = status(CoffeeField.OPENED_DATE),
      testTag = "coffee_edit_opened_date",
    )
  }
}

private val previewForm = CoffeeForm(
  name = "Etiopía Guji Hambela",
  roaster = "Café de Altura",
  country = "Etiopía",
  region = "Guji",
  varieties = listOf("Heirloom", "74110"),
  process = CoffeeProcess.NATURAL,
  altitudeMin = "1800",
  altitudeMax = "2200",
  roastLevel = RoastLevel.LIGHT,
  roastDate = LocalDate.of(2026, 10, 12),
  bagWeight = "250",
  ocrFilled = setOf(CoffeeField.NAME, CoffeeField.ROASTER),
)

@Composable
private fun CoffeeEditPreviewBody() {
  Column(Modifier.padding(AntTheme.spacing.base), verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.sm)) {
    CoffeeFields(
      previewForm,
      emptyMap(),
      dev.dotscale.coffee.CoffeeSuggestions(),
      { _, _ -> },
      {},
      {},
      { LocalDate.of(2026, 11, 13) },
    )
  }
}

@Preview(name = "Editar café claro", showBackground = true, heightDp = 2400)
@Composable
private fun CoffeeEditPreview() {
  DotScaleTheme(darkTheme = false) { CoffeeEditPreviewBody() }
}

@Preview(name = "Editar café oscuro", showBackground = true, heightDp = 2400)
@Composable
private fun CoffeeEditPreviewDark() {
  DotScaleTheme(darkTheme = true) { CoffeeEditPreviewBody() }
}
