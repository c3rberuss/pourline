package dev.dotscale.ui.prepare

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.dotscale.R
import dev.dotscale.ble.BLUETOOTH_PERMISSIONS
import dev.dotscale.ble.DeviceScanner
import dev.dotscale.grinder.ConverterArgs
import dev.dotscale.grinder.GrindFix
import dev.dotscale.session.ScaleError
import dev.dotscale.session.ScaleTarget
import dev.dotscale.settings.ExpertFeature
import dev.dotscale.settings.LocalExperienceMode
import dev.dotscale.ui.coffees.CoffeePicker
import dev.dotscale.ui.coffees.CoffeePickerViewModel
import dev.dotscale.ui.components.AntButton
import dev.dotscale.ui.components.AntButtonType
import dev.dotscale.ui.components.AntCard
import dev.dotscale.ui.components.AntIconButton
import dev.dotscale.ui.components.AntIcons
import dev.dotscale.ui.components.AntInput
import dev.dotscale.ui.components.AntInputStatus
import dev.dotscale.ui.components.AntMessageHost
import dev.dotscale.ui.components.AntStatistic
import dev.dotscale.ui.components.AntStatus
import dev.dotscale.ui.components.AntText
import dev.dotscale.ui.components.AntTextType
import dev.dotscale.ui.components.AntTopBar
import dev.dotscale.ui.components.showAntMessage
import dev.dotscale.ui.grinders.GrindCard
import dev.dotscale.ui.scale.CoffeeSelectRow
import dev.dotscale.ui.scale.TargetPicker
import dev.dotscale.ui.scale.TargetPickerViewModel
import dev.dotscale.ui.scale.TargetSelectRow
import dev.dotscale.ui.theme.AntSize
import dev.dotscale.ui.theme.AntTheme
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Preparar, la raíz de la primera pestaña: la tarjeta de báscula, la receta y el café, la dosis y el ratio, y
 * "Empezar" (habilitado con la báscula `Ready`).
 */
@Composable
fun PrepareScreen(
  viewModel: PrepareViewModel,
  scanner: DeviceScanner,
  pickerViewModel: TargetPickerViewModel,
  coffeePickerViewModel: CoffeePickerViewModel,
  onOpenSettings: () -> Unit,
  onStart: () -> Unit,
  onManageRecipes: () -> Unit,
  onNewCoffee: () -> Unit,
  onAddGrinder: () -> Unit,
  onFixGrinder: (GrindFix, Long) -> Unit,
  onOpenConverter: ((ConverterArgs) -> Unit)? = null,
) {
  val ui by viewModel.ui.collectAsStateWithLifecycle()
  val grind by viewModel.grind.collectAsStateWithLifecycle()
  val snackbar = remember { SnackbarHostState() }
  var sheetOpen by rememberSaveable { mutableStateOf(false) }
  var pickerOpen by rememberSaveable { mutableStateOf(false) }
  var coffeePickerOpen by rememberSaveable { mutableStateOf(false) }
  val simulatedName = stringResource(R.string.scale_simulation)

  val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
    viewModel.refreshPermissions()
  }
  val enableLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {}
  // El usuario puede cambiar los permisos desde Ajustes del sistema.
  LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshPermissions() }

  val commandError = stringResource(R.string.scale_error_command)
  val saveError = stringResource(R.string.scale_error_save)
  val removedError = stringResource(R.string.target_error_removed)
  val unreadableError = stringResource(R.string.target_error_unreadable)
  LaunchedEffect(viewModel) {
    viewModel.errors.collect { error ->
      val text = when (error) {
        is ScaleError.Command -> error.detail ?: commandError
        ScaleError.SaveFailed -> saveError
        ScaleError.TargetRemoved -> removedError
        ScaleError.TargetUnreadable -> unreadableError
      }
      snackbar.showAntMessage(text, AntStatus.Error)
    }
  }

  val actions = ScaleCardActions(
    onGrantPermissions = { permissionLauncher.launch(BLUETOOTH_PERMISSIONS) },
    onEnableBluetooth = { enableLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) },
    onOpenSheet = { sheetOpen = true },
    onConnect = viewModel::connectLast,
    onRetry = viewModel::retry,
    onDisconnect = viewModel::disconnect,
    onUseSimulated = { viewModel.connect(ScaleTarget.Simulated(simulatedName)) },
  )

  Scaffold(
    containerColor = AntTheme.colors.bgLayout,
    topBar = {
      AntTopBar(
        title = stringResource(R.string.tab_prepare),
        actions = {
          AntIconButton(
            AntIcons.Settings,
            contentDescription = stringResource(R.string.settings_open),
            onClick = onOpenSettings,
            modifier = Modifier.testTag("prepare_settings"),
          )
        },
      )
    },
    snackbarHost = { AntMessageHost(snackbar) },
  ) { padding ->
    Column(
      Modifier
        .fillMaxSize()
        .padding(padding)
        .verticalScroll(rememberScrollState())
        .padding(AntTheme.spacing.base),
      verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.sm),
    ) {
      ScaleCard(ui.card, actions)
      TargetSelectRow(
        ui.target,
        enabled = true,
        onClick = { pickerOpen = true },
        modifier = Modifier.testTag("prepare_recipe"),
        label = stringResource(R.string.prepare_recipe),
      )
      CoffeeSelectRow(ui.coffee, enabled = true, onClick = { coffeePickerOpen = true }, modifier = Modifier.testTag("prepare_coffee"))
      DoseCard(ui, viewModel)
      GrindCard(
        state = grind,
        mode = LocalExperienceMode.current,
        onChoose = viewModel::chooseGrinder,
        onAddGrinder = onAddGrinder,
        onFix = onFixGrinder,
        onOpenConverter = onOpenConverter,
      )
      Column(verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.xs)) {
        AntButton(
          text = stringResource(R.string.prepare_start),
          onClick = {
            viewModel.startSession()
            onStart()
          },
          modifier = Modifier.fillMaxWidth().testTag("prepare_start"),
          type = AntButtonType.Primary,
          size = AntSize.Large,
          enabled = ui.canStart,
        )
        if (!ui.canStart) {
          AntText(
            stringResource(R.string.prepare_start_disabled),
            Modifier.fillMaxWidth().testTag("prepare_start_hint"),
            type = AntTextType.Secondary,
          )
        }
      }
    }
  }

  if (sheetOpen) {
    ConnectionSheet(
      scanner = scanner,
      onDevice = { address, name ->
        sheetOpen = false
        viewModel.connect(ScaleTarget.Real(address, name))
      },
      onSimulated = {
        sheetOpen = false
        viewModel.connect(ScaleTarget.Simulated(simulatedName))
      },
      onClose = { sheetOpen = false },
    )
  }

  if (coffeePickerOpen) {
    val coffees by coffeePickerViewModel.pickable.collectAsStateWithLifecycle()
    CoffeePicker(
      coffees = coffees,
      selectedId = ui.coffee?.id,
      onSelect = viewModel::selectCoffee,
      onClear = if (ui.coffee != null) ({ viewModel.selectCoffee(null) }) else null,
      onNew = {
        coffeePickerOpen = false
        viewModel.requestNewCoffee()
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
        viewModel.selectTarget(it)
        pickerOpen = false
      },
      onClear = {
        viewModel.selectTarget(null)
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

/** Con receta, la dosis, el ratio y el agua son suyos y de solo lectura; sin receta se editan la dosis y el ratio. */
@Composable
private fun DoseCard(ui: PrepareUiState, viewModel: PrepareViewModel) {
  AntCard(Modifier.fillMaxWidth()) {
    if (ui.target != null) {
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AntTheme.spacing.sm)) {
        AntStatistic(
          value = ui.recipeDoseG?.let { formatGrams(it) } ?: stringResource(R.string.prepare_empty_value),
          modifier = Modifier.weight(1f).testTag("prepare_dose"),
          title = stringResource(R.string.prepare_dose),
          suffix = ui.recipeDoseG?.let { stringResource(R.string.prepare_grams) },
        )
        AntStatistic(
          value = ui.recipeRatio?.let { stringResource(R.string.prepare_ratio_value, formatRatio(it)) } ?: stringResource(R.string.prepare_empty_value),
          modifier = Modifier.weight(1f).testTag("prepare_ratio"),
          title = stringResource(R.string.prepare_ratio),
        )
        WaterStatistic(ui, Modifier.weight(1f))
      }
    } else {
      FreeFields(ui, viewModel)
    }
  }
}

@Composable
private fun FreeFields(ui: PrepareUiState, viewModel: PrepareViewModel) {
  var doseText by rememberSaveable { mutableStateOf(formatInput(ui.plan.doseG)) }
  var ratioText by rememberSaveable { mutableStateOf(formatInput(ui.plan.ratio)) }
  val doseValid = parseFreeDose(doseText) != null
  val ratioValid = parseFreeRatio(ratioText) != null
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AntTheme.spacing.sm)) {
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.xxs)) {
      AntText(stringResource(R.string.prepare_dose), type = AntTextType.Secondary, style = AntTheme.typography.textSM)
      AntInput(
        value = doseText,
        onValueChange = {
          doseText = it
          viewModel.setDose(it)
        },
        modifier = Modifier.fillMaxWidth().testTag("prepare_dose"),
        suffix = { AntText(stringResource(R.string.prepare_grams), type = AntTextType.Secondary) },
        status = if (doseValid) AntInputStatus.Default else AntInputStatus.Error,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
      )
    }
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.xxs)) {
      AntText(stringResource(R.string.prepare_ratio), type = AntTextType.Secondary, style = AntTheme.typography.textSM)
      AntInput(
        value = ratioText,
        onValueChange = {
          ratioText = it
          viewModel.setRatio(it)
        },
        modifier = Modifier.fillMaxWidth().testTag("prepare_ratio"),
        prefix = { AntText(stringResource(R.string.prepare_ratio_prefix), type = AntTextType.Secondary) },
        status = if (ratioValid) AntInputStatus.Default else AntInputStatus.Error,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
      )
    }
    WaterStatistic(ui, Modifier.weight(1f))
  }
  if (!doseValid || !ratioValid) {
    AntText(
      stringResource(R.string.prepare_range_error),
      Modifier.testTag("prepare_range_error"),
      type = AntTextType.Danger,
      style = AntTheme.typography.textSM,
    )
  }
}

@Composable
private fun WaterStatistic(ui: PrepareUiState, modifier: Modifier) {
  AntStatistic(
    value = formatGrams(ui.waterG),
    modifier = modifier.testTag("prepare_water"),
    title = stringResource(R.string.prepare_water),
    suffix = stringResource(R.string.prepare_grams),
  )
}

/** Gramos sin decimales si son enteros y con uno si no: "240" y "275,4". */
internal fun formatGrams(grams: Double): String =
  if (abs(grams - grams.roundToInt()) < 0.05) grams.roundToInt().toString() else String.format(Locale.getDefault(), "%.1f", grams)

private fun formatRatio(ratio: Double): String = String.format(Locale.getDefault(), "%.1f", ratio)

/** El valor inicial de un campo, con un decimal ("15,0"). */
private fun formatInput(value: Double): String = String.format(Locale.getDefault(), "%.1f", value)
