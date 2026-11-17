package dev.josueamaya.pourline.ui.prepare

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.josueamaya.pourline.R
import dev.josueamaya.pourline.ble.BLUETOOTH_PERMISSIONS
import dev.josueamaya.pourline.ble.DeviceScanner
import dev.josueamaya.pourline.grinder.ConverterArgs
import dev.josueamaya.pourline.grinder.GrindFix
import dev.josueamaya.pourline.session.ScaleError
import dev.josueamaya.pourline.session.ScaleTarget
import dev.josueamaya.pourline.settings.ExpertFeature
import dev.josueamaya.pourline.settings.LocalExperienceMode
import dev.josueamaya.pourline.ui.coffees.CoffeePicker
import dev.josueamaya.pourline.ui.coffees.CoffeePickerViewModel
import dev.josueamaya.pourline.ui.components.AntButton
import dev.josueamaya.pourline.ui.components.AntButtonType
import dev.josueamaya.pourline.ui.components.AntCard
import dev.josueamaya.pourline.ui.components.AntIconButton
import dev.josueamaya.pourline.ui.components.AntIcons
import dev.josueamaya.pourline.ui.components.AntInput
import dev.josueamaya.pourline.ui.components.AntInputStatus
import dev.josueamaya.pourline.ui.components.AntMessageHost
import dev.josueamaya.pourline.ui.components.AntStatistic
import dev.josueamaya.pourline.ui.components.AntStatus
import dev.josueamaya.pourline.ui.components.AntText
import dev.josueamaya.pourline.ui.components.AntTextType
import dev.josueamaya.pourline.ui.components.AntTopBar
import dev.josueamaya.pourline.ui.components.showAntMessage
import dev.josueamaya.pourline.ui.grinders.GrindCard
import dev.josueamaya.pourline.ui.scale.CoffeeSelectRow
import dev.josueamaya.pourline.ui.scale.TargetPicker
import dev.josueamaya.pourline.ui.scale.TargetPickerViewModel
import dev.josueamaya.pourline.ui.scale.TargetSelectRow
import dev.josueamaya.pourline.ui.theme.AntSize
import dev.josueamaya.pourline.ui.theme.AntTheme
import dev.josueamaya.pourline.ui.tour.TourHost
import dev.josueamaya.pourline.ui.tour.Tours
import dev.josueamaya.pourline.ui.tour.tourAnchor
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
  // Los textos de la dosis y el ratio libres viven aquí: "Empezar sin báscula" necesita saber si valen. La clave los
  // reinicia al poner o quitar la receta, como cuando los campos salían de la composición.
  var doseText by rememberSaveable(ui.target == null) { mutableStateOf(formatInput(ui.plan.doseG)) }
  var ratioText by rememberSaveable(ui.target == null) { mutableStateOf(formatInput(ui.plan.ratio)) }

  val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
    viewModel.refreshPermissions()
  }
  val enableLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {}
  // La notificación de la preparación en curso; si se niega, la sesión sigue igual en segundo plano.
  val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
  val context = LocalContext.current
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
            modifier = Modifier.tourAnchor("prepare_settings").testTag("prepare_settings"),
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
      ScaleCard(ui.card, actions, Modifier.tourAnchor("scale_card"))
      TargetSelectRow(
        ui.target,
        enabled = true,
        onClick = { pickerOpen = true },
        modifier = Modifier.tourAnchor("prepare_recipe").testTag("prepare_recipe"),
        label = stringResource(R.string.prepare_recipe),
      )
      CoffeeSelectRow(
        ui.coffee,
        enabled = true,
        onClick = { coffeePickerOpen = true },
        modifier = Modifier.tourAnchor("prepare_coffee").testTag("prepare_coffee"),
      )
      DoseCard(
        ui,
        viewModel,
        doseText = doseText,
        onDoseText = { doseText = it },
        ratioText = ratioText,
        onRatioText = { ratioText = it },
      )
      GrindCard(
        state = grind,
        mode = LocalExperienceMode.current,
        onChoose = viewModel::chooseGrinder,
        onAddGrinder = onAddGrinder,
        onFix = onFixGrinder,
        onOpenConverter = onOpenConverter,
        modifier = Modifier.tourAnchor("grind_card"),
      )
      Column(verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.xs)) {
        AntButton(
          text = stringResource(R.string.prepare_start),
          onClick = {
            if (context.needsNotificationPermission()) notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            viewModel.startSession()
            onStart()
          },
          modifier = Modifier.fillMaxWidth().tourAnchor("prepare_start").testTag("prepare_start"),
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
        // Sin báscula (SPEC-007): siempre visible, la báscula no interviene.
        AntButton(
          text = stringResource(R.string.prepare_start_scaleless),
          onClick = {
            if (context.needsNotificationPermission()) notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            viewModel.startScalelessSession()
            onStart()
          },
          modifier = Modifier.fillMaxWidth().tourAnchor("prepare_start_scaleless").testTag("prepare_start_scaleless"),
          size = AntSize.Large,
          enabled = canStartScaleless(ui.target != null, doseText, ratioText),
        )
      }
    }
  }

  // El recorrido general (SPEC-008): señala la pantalla y, al final, las pestañas.
  TourHost(Tours.prepare)

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
private fun DoseCard(
  ui: PrepareUiState,
  viewModel: PrepareViewModel,
  doseText: String,
  onDoseText: (String) -> Unit,
  ratioText: String,
  onRatioText: (String) -> Unit,
) {
  AntCard(Modifier.fillMaxWidth().tourAnchor("prepare_dose_card").testTag("prepare_dose_card")) {
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
      FreeFields(ui, viewModel, doseText, onDoseText, ratioText, onRatioText)
    }
  }
}

@Composable
private fun FreeFields(
  ui: PrepareUiState,
  viewModel: PrepareViewModel,
  doseText: String,
  onDoseText: (String) -> Unit,
  ratioText: String,
  onRatioText: (String) -> Unit,
) {
  val doseValid = parseFreeDose(doseText) != null
  val ratioValid = parseFreeRatio(ratioText) != null
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AntTheme.spacing.sm)) {
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.xxs)) {
      AntText(stringResource(R.string.prepare_dose), type = AntTextType.Secondary, style = AntTheme.typography.textSM)
      AntInput(
        value = doseText,
        onValueChange = {
          onDoseText(it)
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
          onRatioText(it)
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

/** Android 13+ pide permiso para mostrar la notificación de la preparación en curso. */
private fun Context.needsNotificationPermission(): Boolean =
  Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
    ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
