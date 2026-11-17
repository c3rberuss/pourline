package dev.dotscale.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.dotscale.AppDependencies
import dev.dotscale.brew.StageNotifier
import dev.dotscale.brew.TargetStage
import dev.dotscale.MainNavigation
import dev.dotscale.ble.BluetoothEnvironment
import dev.dotscale.ble.DeviceScanner
import dev.dotscale.ble.ScannedDevice
import dev.dotscale.coffee.CoffeeRepository
import dev.dotscale.coffee.photo.CoffeeImages
import dev.dotscale.coffee.photo.CoffeePhotoStore
import dev.dotscale.coffee.photo.LocalCoffeeImages
import dev.dotscale.grinder.GrinderCatalogSeeder
import dev.dotscale.grinder.GrinderRepository
import dev.dotscale.grinder.ResourcesGrindTexts
import dev.dotscale.grinder.SyncResult
import dev.dotscale.grinder.TestGrinderCatalog
import dev.dotscale.history.BrewRepository
import dev.dotscale.history.HistoryDatabase
import dev.dotscale.protocol.Frame
import dev.dotscale.protocol.ScaleMessage
import dev.dotscale.recipe.RecipeRepository
import dev.dotscale.recipe.ResourcesRecipeTexts
import dev.dotscale.scale.ConnectionState
import dev.dotscale.scale.ConnectionStep
import dev.dotscale.scale.ScaleConnection
import dev.dotscale.session.AutoReconnect
import dev.dotscale.session.GrindSelection
import dev.dotscale.session.RepositorySessionGrinders
import dev.dotscale.session.SessionGrind
import dev.dotscale.session.RepositoryScaleCoffees
import dev.dotscale.session.RepositoryTargetReferences
import dev.dotscale.session.ScaleConnectionFactory
import dev.dotscale.session.ScaleSessionManager
import dev.dotscale.session.ScaleTarget
import dev.dotscale.settings.BrewSettings
import dev.dotscale.settings.ConnectionSettings
import dev.dotscale.settings.ExperienceMode
import dev.dotscale.settings.ExperienceSettings
import dev.dotscale.settings.GrinderSettings
import dev.dotscale.settings.LocalExperienceMode
import dev.dotscale.settings.TargetSettings
import dev.dotscale.settings.ThemeMode
import dev.dotscale.settings.ThemeSettings
import dev.dotscale.sim.SimulatedScaleConnection
import dev.dotscale.tools.ResourcesFourSixTexts
import dev.dotscale.ui.coffees.FakeLabelOcr
import dev.dotscale.ui.scale.SilentNotifier
import dev.dotscale.ui.theme.DotScaleTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.io.File

/** Permisos y Bluetooth a voluntad del test. */
class FakeBluetoothEnvironment(permissions: Boolean = true, bluetoothOn: Boolean = true) : BluetoothEnvironment {
  @Volatile var permissions = permissions
  val on = MutableStateFlow(bluetoothOn)

  override fun permissionsGranted() = permissions

  override val enabled: StateFlow<Boolean> get() = on
}

/** Una conexión que se queda en `Connecting` ([hang]) o falla ([failure]), para ver esos estados de la tarjeta. */
class ScriptedConnection(private val hang: Boolean) : ScaleConnection {
  private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
  override val state: StateFlow<ConnectionState> = _state
  override val messages: Flow<ScaleMessage> = emptyFlow()

  override suspend fun connect() {
    _state.value = ConnectionState.Connecting(ConnectionStep.BONDING)
    if (hang) awaitCancellation()
    _state.value = ConnectionState.Failed("fallo simulado")
    throw IllegalStateException("fallo simulado")
  }

  override suspend fun send(frame: Frame) = Unit

  override fun disconnect() {
    _state.value = ConnectionState.Disconnected
  }
}

/** Apunta los avisos de paso que recibe (en orden de llegada). */
class RecordingNotifier : StageNotifier {
  val started = java.util.Collections.synchronizedList(mutableListOf<TargetStage>())

  override fun stageStarted(stage: TargetStage) {
    started += stage
  }

  override fun release() = Unit
}

/** Un escáner que encuentra una báscula y sigue buscando. */
class FakeScanner(private val devices: List<ScannedDevice> = listOf(DOT)) : DeviceScanner {
  var scans = 0
    private set

  override fun scan() = flow {
    scans++
    devices.forEach { emit(it) }
    awaitCancellation()
  }

  companion object {
    val DOT = ScannedDevice(
      address = "AA:BB:CC:DD:EE:01",
      name = "DOT-A1B2",
      rssi = -58,
      advertisement = null,
      advertisesScaleService = true,
    )
  }
}

/**
 * Las dependencias de la app para los tests de navegación (D32): Room en memoria, preferencias propias
 * (`"settings_test"`, borradas al crear y al cerrar), la báscula simulada y un Bluetooth falso. Nada toca el
 * `history.db` ni las preferencias reales.
 */
class TestAppDependencies(
  private val context: Context,
  val bluetooth: FakeBluetoothEnvironment = FakeBluetoothEnvironment(),
  val scanner: FakeScanner = FakeScanner(),
  notifier: StageNotifier = SilentNotifier,
) {
  init {
    context.deleteSharedPreferences(PREFS)
  }

  private val texts = ResourcesRecipeTexts(context)
  private val photosDir = File(context.cacheDir, "test_app_photos").also { it.deleteRecursively() }
  private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  val db: HistoryDatabase = HistoryDatabase.inMemory(context, texts)
  val brewSettings = BrewSettings(context, PREFS)
  val targetSettings = TargetSettings(context, PREFS)
  val themeSettings = ThemeSettings(context, PREFS)
  val experienceSettings = ExperienceSettings(context, PREFS)
  val connectionSettings = ConnectionSettings(context, PREFS)
  val brews = BrewRepository(db)
  val photoStore = CoffeePhotoStore(photosDir, context.cacheDir)
  val coffees = CoffeeRepository(db, photoStore)
  val recipes = RecipeRepository(db, texts)
  val grinderSettings = GrinderSettings(context, PREFS)
  val grindTexts = ResourcesGrindTexts(context)
  val grinders = GrinderRepository(db, grinderSettings, grindTexts)

  /** Cada destino se conecta a una báscula simulada, también el "real"; un test puede cambiarlo. */
  @Volatile var connectionFor: (ScaleTarget) -> ScaleConnection = { SimulatedScaleConnection() }
  val connectionsMade = java.util.Collections.synchronizedList(mutableListOf<ScaleTarget>())

  val scaleSession: ScaleSessionManager = ScaleSessionManager(
    connections = ScaleConnectionFactory { target ->
      connectionsMade += target
      connectionFor(target)
    },
    scope = MainScope(),
    autoFinishSeconds = brewSettings.autoFinishSeconds,
    saver = brews,
    applicationScope = appScope,
    references = targetSettings,
    targets = RepositoryTargetReferences(recipes, brews),
    notifier = notifier,
    coffeeStore = brewSettings,
    coffees = RepositoryScaleCoffees(coffees),
    grind = SessionGrind { grindSelection.current() },
    memory = connectionSettings,
  )

  val grindSelection = GrindSelection(
    targets = scaleSession.ui.map { it.target },
    grinders = RepositorySessionGrinders(grinders),
    defaults = grinderSettings,
    texts = grindTexts,
    scope = appScope,
  )

  val autoReconnect = AutoReconnect(scaleSession, connectionSettings, bluetooth, MainScope())

  val dependencies = AppDependencies(
    brewSettings = brewSettings,
    targetSettings = targetSettings,
    themeSettings = themeSettings,
    experienceSettings = experienceSettings,
    freeBrewDefaults = brewSettings,
    scaleMemory = connectionSettings,
    brewRepository = brews,
    coffeeRepository = coffees,
    recipeRepository = recipes,
    recipeTexts = texts,
    grinderSettings = grinderSettings,
    grinderRepository = grinders,
    grindTexts = grindTexts,
    fourSixTexts = ResourcesFourSixTexts(context),
    grindSelection = grindSelection,
    coffeePhotoStore = photoStore,
    coffeeImages = CoffeeImages(photoStore, maxBytes = 1024 * 1024),
    labelOcr = FakeLabelOcr(),
    scaleSession = scaleSession,
    autoReconnect = autoReconnect,
    bluetooth = bluetooth,
    scanner = scanner,
  )

  /** Siembra el catálogo de [json] (por defecto el de prueba, con marcas ficticias) con el sembrador real. */
  fun seedGrinders(json: String = TestGrinderCatalog.JSON): SyncResult = runBlocking {
    GrinderCatalogSeeder(db.grinderDao(), { json }, grinderSettings, grinderSettings).sync()
  }

  /** Marca el primer arranque como hecho (sin esto aparece la elección de modo). */
  fun skipOnboarding(mode: ExperienceMode = ExperienceMode.BASIC) =
    experienceSettings.completeOnboarding(mode)

  fun close() {
    scaleSession.disconnect()
    appScope.cancel()
    db.close()
    photosDir.deleteRecursively()
    context.deleteSharedPreferences(PREFS)
  }

  private companion object {
    const val PREFS = "settings_test"
  }
}

/** Monta `MainNavigation` con [deps], el tema y el modo como `MainActivity`. */
@Composable
fun TestApp(deps: TestAppDependencies) {
  val mode by deps.experienceSettings.mode.collectAsState()
  // Claro salvo que el test pida oscuro: el modo del sistema del emulador no debe cambiar los tests.
  val theme by deps.themeSettings.mode.collectAsState()
  CompositionLocalProvider(
    LocalExperienceMode provides mode,
    LocalCoffeeImages provides deps.dependencies.coffeeImages,
  ) {
    DotScaleTheme(darkTheme = theme == ThemeMode.DARK) { MainNavigation(deps.dependencies) }
  }
}

/** Las descripciones (es y en) de los iconos de sección que ya no están en ninguna barra superior. */
val SectionIconDescriptions = listOf("Abrir cafés", "Open coffees", "Abrir recetas", "Open recipes", "Abrir historial", "Open history")
