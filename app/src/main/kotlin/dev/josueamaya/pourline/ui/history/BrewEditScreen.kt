package dev.josueamaya.pourline.ui.history

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.josueamaya.pourline.R
import dev.josueamaya.pourline.coffee.brewDaysSinceRoast
import dev.josueamaya.pourline.history.BrewMethod
import dev.josueamaya.pourline.ui.coffees.CoffeePicker
import dev.josueamaya.pourline.ui.coffees.CoffeePickerViewModel
import dev.josueamaya.pourline.ui.components.AntRate
import dev.josueamaya.pourline.ui.components.AntSelectTrigger
import dev.josueamaya.pourline.ui.components.AntButton
import dev.josueamaya.pourline.ui.components.AntButtonType
import dev.josueamaya.pourline.ui.components.AntIconButton
import dev.josueamaya.pourline.ui.components.AntIcons
import dev.josueamaya.pourline.ui.components.AntInput
import dev.josueamaya.pourline.ui.components.AntInputStatus
import dev.josueamaya.pourline.ui.components.AntModal
import dev.josueamaya.pourline.ui.components.AntSelect
import dev.josueamaya.pourline.ui.components.AntSelectOption
import dev.josueamaya.pourline.ui.components.AntSpin
import dev.josueamaya.pourline.ui.components.AntText
import dev.josueamaya.pourline.ui.components.AntTextType
import dev.josueamaya.pourline.ui.components.AntTopBar
import dev.josueamaya.pourline.ui.grinders.GrindSettingField
import dev.josueamaya.pourline.ui.theme.AntSize
import dev.josueamaya.pourline.ui.theme.AntTheme
import java.time.ZoneId

private val RATING_STAR_SIZE = 32.dp
private val COFFEE_ICON_SIZE = 16.dp

@Composable
fun BrewEditScreen(
  viewModel: BrewEditViewModel,
  onBack: () -> Unit,
  coffeePickerViewModel: CoffeePickerViewModel? = null,
  onNewCoffee: () -> Unit = {},
) {
  val status by viewModel.status.collectAsStateWithLifecycle()
  val form by viewModel.form.collectAsStateWithLifecycle()
  val initial by viewModel.initial.collectAsStateWithLifecycle()
  val startedAt by viewModel.startedAtEpochMs.collectAsStateWithLifecycle()
  val linkedArchived by viewModel.linkedArchived.collectAsStateWithLifecycle()
  val grinders by viewModel.grinders.collectAsStateWithLifecycle()
  val currentGrinder by viewModel.currentGrinder.collectAsStateWithLifecycle()
  var confirmDiscard by rememberSaveable { mutableStateOf(false) }
  var coffeePickerOpen by rememberSaveable { mutableStateOf(false) }

  // `Saved` y `Gone` (la fila se borró mientras se editaba) vuelven atrás.
  LaunchedEffect(status) { if (status == BrewEditStatus.Saved || status == BrewEditStatus.Gone) onBack() }

  val dirty = form.isDirty(initial) && status == BrewEditStatus.Ready
  val requestBack = { if (dirty) confirmDiscard = true else onBack() }
  BackHandler(enabled = dirty) { confirmDiscard = true }

  Scaffold(
    containerColor = AntTheme.colors.bgLayout,
    topBar = {
      AntTopBar(
        title = stringResource(R.string.brew_edit_title),
        navigationIcon = {
          AntIconButton(AntIcons.ArrowLeft, contentDescription = stringResource(R.string.scale_back), onClick = requestBack)
        },
      )
    },
  ) { padding ->
    if (status == BrewEditStatus.Loading) {
      AntSpin(Modifier.padding(padding).padding(AntTheme.spacing.xl))
      return@Scaffold
    }
    Column(
      Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(AntTheme.spacing.base),
      verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.base),
    ) {
      Field(stringResource(R.string.brew_edit_name)) {
        AntInput(
          value = form.name,
          onValueChange = { v -> viewModel.update { it.copy(name = v) } },
          modifier = Modifier.fillMaxWidth().testTag("brew_edit_name"),
          placeholder = formatBrewDateTime(startedAt),
        )
      }
      CoffeeField(form.coffee, startedAt, onClick = { coffeePickerOpen = true }, enabled = coffeePickerViewModel != null)
      Field(stringResource(R.string.brew_edit_rating)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
          AntRate(
            value = form.rating,
            onValueChange = { v -> viewModel.update { it.copy(rating = v) } },
            modifier = Modifier.weight(1f),
            starSize = RATING_STAR_SIZE,
            testTag = "brew_edit_rating",
          )
          if (form.rating != null) {
            AntButton(
              text = stringResource(R.string.brew_edit_rating_clear),
              onClick = { viewModel.update { it.copy(rating = null) } },
              modifier = Modifier.testTag("brew_edit_rating_clear"),
              type = AntButtonType.Link,
              size = AntSize.Small,
            )
          }
        }
      }
      Field(stringResource(R.string.brew_edit_notes)) {
        AntInput(
          value = form.notes,
          onValueChange = { v -> viewModel.update { it.copy(notes = v) } },
          modifier = Modifier.fillMaxWidth().testTag("brew_edit_notes"),
          singleLine = false,
          minLines = 3,
        )
      }
      val noMethod = stringResource(R.string.brew_edit_method_none)
      val options = listOf(AntSelectOption<BrewMethod?>(null, noMethod)) + BrewMethod.entries.map { AntSelectOption<BrewMethod?>(it, it.label()) }
      AntSelect(
        options = options,
        selected = form.method,
        onSelect = { m -> viewModel.update { it.copy(method = m) } },
        label = stringResource(R.string.brew_edit_method),
        modifier = Modifier.fillMaxWidth(),
        testTag = "brew_edit_method",
      )
      if (form.method == BrewMethod.OTHER) {
        AntInput(
          value = form.methodOther,
          onValueChange = { v -> viewModel.update { it.copy(methodOther = v) } },
          modifier = Modifier.fillMaxWidth().testTag("brew_edit_method_other"),
          placeholder = stringResource(R.string.brew_edit_method_other),
        )
      }
      GrindSettingField(
        choice = form.grind,
        options = grinders,
        current = currentGrinder,
        method = form.method,
        onChange = { choice -> viewModel.update { it.copy(grind = choice) } },
        tagPrefix = "brew_edit",
        modifier = Modifier.fillMaxWidth(),
      )
      Field(stringResource(R.string.brew_edit_temp)) {
        val invalid = form.tempInvalid
        AntInput(
          value = form.waterTemp,
          onValueChange = { v -> viewModel.update { it.copy(waterTemp = v) } },
          modifier = Modifier.fillMaxWidth().testTag("brew_edit_temp"),
          status = if (invalid) AntInputStatus.Error else AntInputStatus.Default,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        if (invalid) {
          AntText(stringResource(R.string.brew_edit_temp_error), Modifier.testTag("brew_edit_temp_error"), type = AntTextType.Danger)
        }
      }
      if (form.scaleless) {
        Field(stringResource(R.string.brew_edit_dose)) {
          AntInput(
            value = form.dose,
            onValueChange = { v -> viewModel.update { it.copy(dose = v) } },
            modifier = Modifier.fillMaxWidth().testTag("brew_edit_dose"),
            status = if (form.doseInvalid) AntInputStatus.Error else AntInputStatus.Default,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          )
          if (form.doseInvalid) {
            AntText(stringResource(R.string.brew_edit_dose_error), Modifier.testTag("brew_edit_dose_error"), type = AntTextType.Danger)
          }
        }
        Field(stringResource(R.string.brew_edit_water)) {
          AntInput(
            value = form.water,
            onValueChange = { v -> viewModel.update { it.copy(water = v) } },
            modifier = Modifier.fillMaxWidth().testTag("brew_edit_water"),
            status = if (form.waterInvalid) AntInputStatus.Error else AntInputStatus.Default,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          )
          if (form.waterInvalid) {
            AntText(stringResource(R.string.brew_edit_water_error), Modifier.testTag("brew_edit_water_error"), type = AntTextType.Danger)
          }
        }
      }
      AntButton(
        text = stringResource(R.string.brew_edit_save),
        onClick = viewModel::save,
        modifier = Modifier.fillMaxWidth().testTag("brew_edit_save"),
        type = AntButtonType.Primary,
        enabled = form.isValid && status == BrewEditStatus.Ready,
        loading = status == BrewEditStatus.Saving,
      )
    }
  }

  if (coffeePickerOpen && coffeePickerViewModel != null) {
    val pickable by coffeePickerViewModel.pickable.collectAsStateWithLifecycle()
    CoffeePicker(
      coffees = pickable,
      selectedId = form.coffee.linkedId,
      onSelect = viewModel::selectCoffee,
      onClear = if (form.coffee !is CoffeeChoice.None) viewModel::clearCoffee else null,
      onNew = {
        coffeePickerOpen = false
        viewModel.requestNewCoffee()
        onNewCoffee()
      },
      onClose = { coffeePickerOpen = false },
      extra = linkedArchived,
    )
  }

  if (confirmDiscard) {
    AntModal(
      title = stringResource(R.string.brew_edit_discard_title),
      text = stringResource(R.string.brew_edit_discard_text),
      confirmText = stringResource(R.string.brew_edit_discard),
      cancelText = stringResource(R.string.brew_edit_keep),
      onConfirm = {
        confirmDiscard = false
        onBack()
      },
      onDismiss = { confirmDiscard = false },
      danger = true,
    )
  }
}

/** El disparador del selector de café: "Nombre · día N", el texto antiguo con su ayuda, o "Sin café". */
@Composable
private fun CoffeeField(choice: CoffeeChoice, startedAtEpochMs: Long, onClick: () -> Unit, enabled: Boolean) {
  val none = stringResource(R.string.brew_setup_coffee_none)
  val value = when (choice) {
    CoffeeChoice.None -> none
    is CoffeeChoice.Legacy -> choice.name
    is CoffeeChoice.Linked -> {
      val days = brewDaysSinceRoast(choice.coffee.roastDate, startedAtEpochMs, ZoneId.systemDefault())
      if (days != null) stringResource(R.string.brew_setup_coffee_value, choice.coffee.name, days) else choice.coffee.name
    }
  }
  Column(verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.xxs)) {
    AntSelectTrigger(
      value = value,
      onClick = onClick,
      modifier = Modifier.fillMaxWidth(),
      label = stringResource(R.string.brew_edit_coffee),
      leading = {
        Icon(AntIcons.Coffee, contentDescription = null, modifier = Modifier.size(COFFEE_ICON_SIZE), tint = AntTheme.colors.textTertiary)
      },
      enabled = enabled,
      testTag = "brew_edit_coffee",
    )
    if (choice is CoffeeChoice.Legacy) {
      AntText(
        stringResource(R.string.brew_edit_coffee_legacy),
        Modifier.testTag("brew_edit_coffee_legacy"),
        type = AntTextType.Secondary,
        style = AntTheme.typography.textSM,
      )
    }
  }
}

@Composable
private fun Field(label: String, content: @Composable () -> Unit) {
  Column(verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.xxs)) {
    AntText(label, type = AntTextType.Secondary, style = AntTheme.typography.textSM)
    content()
  }
}
