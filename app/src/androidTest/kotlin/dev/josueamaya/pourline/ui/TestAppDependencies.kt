package dev.josueamaya.pourline.ui

import android.content.Context
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.josueamaya.pourline.AppDependencies
import dev.josueamaya.pourline.brew.StageNotifier
import dev.josueamaya.pourline.brew.TargetStage
import dev.josueamaya.pourline.MainNavigation
import dev.josueamaya.pourline.backup.AppVersion
import dev.josueamaya.pourline.backup.BackupManager
import dev.josueamaya.pourline.backup.BackupOperations
import dev.josueamaya.pourline.backup.ContentResolverStreams
import dev.josueamaya.pourline.backup.ThumbnailMaker
import dev.josueamaya.pourline.ble.BluetoothEnvironment
import dev.josueamaya.pourline.ble.DeviceScanner
import dev.josueamaya.pourline.ble.ScannedDevice
import dev.josueamaya.pourline.coffee.CoffeeRepository
import dev.josueamaya.pourline.coffee.photo.CoffeeImages
import dev.josueamaya.pourline.coffee.photo.CoffeePhotoStore
import dev.josueamaya.pourline.coffee.photo.LocalCoffeeImages
import dev.josueamaya.pourline.grinder.GrinderCatalogSeeder
import dev.josueamaya.pourline.grinder.GrinderRepository
import dev.josueamaya.pourline.grinder.ResourcesGrindTexts
import dev.josueamaya.pourline.grinder.SyncResult
import dev.josueamaya.pourline.grinder.TestGrinderCatalog
import dev.josueamaya.pourline.history.BrewRepository
import dev.josueamaya.pourline.history.HistoryDatabase
import dev.josueamaya.pourline.protocol.Frame
import dev.josueamaya.pourline.protocol.ScaleMessage
import dev.josueamaya.pourline.recipe.RecipeRepository
import dev.josueamaya.pourline.recipe.ResourcesRecipeTexts
import dev.josueamaya.pourline.recipe.fourSix
import dev.josueamaya.pourline.recipe.v60Classic
import dev.josueamaya.pourline.recipe.catalog.CatalogCache
import dev.josueamaya.pourline.recipe.catalog.CatalogFetcher
import dev.josueamaya.pourline.recipe.catalog.CatalogTargetSource
import dev.josueamaya.pourline.recipe.catalog.FakeCatalogFetcher
import dev.josueamaya.pourline.recipe.catalog.RecipeCatalogRepository
import dev.josueamaya.pourline.recipe.catalog.ResourcesCatalogLanguage
import dev.josueamaya.pourline.recipe.catalog.TestRecipeCatalog
import dev.josueamaya.pourline.recipe.catalog.grinderCatalogByKey
import dev.josueamaya.pourline.recipe.share.PendingRecipeImport
import dev.josueamaya.pourline.recipe.share.QrReadResult
import dev.josueamaya.pourline.share.QrImageReader
import dev.josueamaya.pourline.scale.ConnectionState
import dev.josueamaya.pourline.scale.ConnectionStep
import dev.josueamaya.pourline.scale.ScaleConnection
import dev.josueamaya.pourline.session.AutoReconnect
import dev.josueamaya.pourline.session.GrindSelection
import dev.josueamaya.pourline.session.RepositorySessionGrinders
import dev.josueamaya.pourline.session.SessionGrind
import dev.josueamaya.pourline.session.RepositoryScaleCoffees
import dev.josueamaya.pourline.session.RepositoryTargetReferences
import dev.josueamaya.pourline.session.ScaleConnectionFactory
import dev.josueamaya.pourline.session.ScaleSessionManager
import dev.josueamaya.pourline.session.ScaleTarget
import dev.josueamaya.pourline.settings.BrewSettings
import dev.josueamaya.pourline.settings.ConnectionSettings
import dev.josueamaya.pourline.settings.ExperienceMode
import dev.josueamaya.pourline.settings.ExperienceSettings
import dev.josueamaya.pourline.settings.GrinderSettings
import dev.josueamaya.pourline.settings.LocalExperienceMode
import dev.josueamaya.pourline.settings.TargetSettings
import dev.josueamaya.pourline.settings.ThemeMode
import dev.josueamaya.pourline.settings.ThemeSettings
import dev.josueamaya.pourline.settings.TourSettings
import dev.josueamaya.pourline.sim.SimulatedScaleConnection
import dev.josueamaya.pourline.tools.ResourcesFourSixTexts
import dev.josueamaya.pourline.ui.coffees.FakeLabelOcr
import dev.josueamaya.pourline.ui.scale.SilentNotifier
import dev.josueamaya.pourline.ui.theme.PourlineTheme
import dev.josueamaya.pourline.ui.tour.Tour
import dev.josueamaya.pourline.ui.tour.Tours
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

  val nextStepTicks = java.util.Collections.synchronizedList(mutableListOf<Int>())

  override fun stageStarted(stage: TargetStage) {
    started += stage
  }

  override fun nextStepTick(remainingS: Int) {
    nextStepTicks += remainingS
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
  /** El reloj monotónico del gestor; un test lo adelanta a mano para no esperar los minutos de una receta. */
  clock: () -> Long = SystemClock::elapsedRealtime,
  /** Los segundos de la cuenta regresiva de "Empezar timer"; 0 (sin cuenta) salvo en los tests que la prueban. */
  countdownSeconds: Int = 0,
  /** El modo de báscula ya elegido; `null` es una instalación que aún no lo eligió (la pregunta de quien actualiza). */
  useScale: Boolean? = true,
  /** La copia de seguridad; `null` usa un [BackupManager] real sobre la base y las fotos de prueba. */
  backup: BackupOperations? = null,
  /** El archivo del catálogo de recetas "empaquetado en el APK" (SPEC-012). */
  catalogJson: String = TestRecipeCatalog.json(),
  /** La descarga del catálogo; por defecto falla siempre y cuenta las llamadas. */
  val catalogFetcher: CatalogFetcher = FakeCatalogFetcher(),
  /** Siembra la V60 clásica y el método 4:6 como recetas propias; las pruebas de una instalación nueva pasan `false`. */
  seedRecipes: Boolean = true,
) {
  init {
    context.deleteSharedPreferences(PREFS)
  }

  private val texts = ResourcesRecipeTexts(context)
  private val photosDir = File(context.cacheDir, "test_app_photos").also { it.deleteRecursively() }

  /** El directorio de trabajo de la importación, en el mismo volumen que [photosDir] (el renombrado es atómico). */
  private val backupDir = File(context.cacheDir, "test_app_backup").also { it.deleteRecursively() }
  private val catalogDir = File(context.cacheDir, "test_catalog").also { it.deleteRecursively() }
  private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  val db: HistoryDatabase = HistoryDatabase.inMemory(context)
  val brewSettings = BrewSettings(context, PREFS)
  val targetSettings = TargetSettings(context, PREFS)
  val themeSettings = ThemeSettings(context, PREFS)
  val experienceSettings = ExperienceSettings(context, PREFS)
  val tourSettings = TourSettings(context, PREFS)
  /** Con báscula: los tests de Preparar conectan la simulada. Los que prueban el modo sin báscula lo cambian. */
  val connectionSettings = ConnectionSettings(context, PREFS).also { settings -> useScale?.let(settings::setUseScale) }
  val brews = BrewRepository(db)
  val photoStore = CoffeePhotoStore(photosDir, context.cacheDir)
  val coffees = CoffeeRepository(db, photoStore)
  val recipes = RecipeRepository(db, texts).also { repository ->
    if (seedRecipes) {
      runBlocking {
        repository.save(v60Classic(texts))
        repository.save(fourSix(texts))
      }
    }
  }
  val recipeImports = PendingRecipeImport()

  /** El catálogo de recetas recomendadas con su caché en `cacheDir/test_catalog` y los molinos de prueba. */
  val recipeCatalog = RecipeCatalogRepository(
    bundled = { catalogJson },
    cache = CatalogCache(catalogDir),
    fetcher = catalogFetcher,
    grinders = { grinderCatalogByKey { TestGrinderCatalog.JSON } },
    scope = appScope,
  )
  val catalogLanguage = ResourcesCatalogLanguage(context)

  /** El lector de QR de las imágenes; un test lo cambia por el suyo. Por defecto no encuentra ninguna receta. */
  var qrImageReader: QrImageReader = QrImageReader { QrReadResult.NoRecipe }
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
    targets = RepositoryTargetReferences(recipes, brews, CatalogTargetSource(recipeCatalog, grinders, catalogLanguage, grindTexts)),
    notifier = notifier,
    coffeeStore = brewSettings,
    coffees = RepositoryScaleCoffees(coffees),
    grind = SessionGrind { grindSelection.current() },
    memory = connectionSettings,
    countdownSeconds = MutableStateFlow(countdownSeconds),
    clock = clock,
  )

  val images = CoffeeImages(photoStore, maxBytes = 1024 * 1024)

  val grindSelection = GrindSelection(
    targets = scaleSession.ui.map { it.target },
    grinders = RepositorySessionGrinders(grinders),
    defaults = grinderSettings,
    texts = grindTexts,
    scope = appScope,
  )

  val autoReconnect = AutoReconnect(scaleSession, connectionSettings, bluetooth, MainScope()) { connectionSettings.useScale.value }

  val backup: BackupOperations = backup ?: BackupManager(
    db = db,
    photosDir = photosDir,
    workDir = backupDir,
    settings = listOf(themeSettings, experienceSettings, connectionSettings, brewSettings, targetSettings, grinderSettings),
    seeder = GrinderCatalogSeeder(db.grinderDao(), { TestGrinderCatalog.JSON }, grinderSettings, grinderSettings),
    streams = ContentResolverStreams(context.contentResolver),
    thumbnails = ThumbnailMaker(photoStore::regenerateThumbnail),
    appVersion = AppVersion("1.0", 1),
    scope = appScope,
    invalidateCaches = {
      brews.clearPendingUndo()
      recipes.clearPendingUndo()
      grindSelection.clearChoice()
      images.clear()
    },
  )

  val dependencies = AppDependencies(
    brewSettings = brewSettings,
    targetSettings = targetSettings,
    themeSettings = themeSettings,
    experienceSettings = experienceSettings,
    tourSettings = tourSettings,
    freeBrewDefaults = brewSettings,
    scaleMemory = connectionSettings,
    scaleMode = connectionSettings,
    brewRepository = brews,
    coffeeRepository = coffees,
    recipeRepository = recipes,
    recipeTexts = texts,
    recipeCatalog = recipeCatalog,
    catalogLanguage = catalogLanguage,
    recipeImports = recipeImports,
    qrImageReader = { uri -> qrImageReader.read(uri) },
    grinderSettings = grinderSettings,
    grinderRepository = grinders,
    grindTexts = grindTexts,
    fourSixTexts = ResourcesFourSixTexts(context),
    grindSelection = grindSelection,
    coffeePhotoStore = photoStore,
    coffeeImages = images,
    labelOcr = FakeLabelOcr(),
    scaleSession = scaleSession,
    autoReconnect = autoReconnect,
    bluetooth = bluetooth,
    scanner = scanner,
    backup = this.backup,
  )

  /** Siembra el catálogo de [json] (por defecto el de prueba, con marcas ficticias) con el sembrador real. */
  fun seedGrinders(json: String = TestGrinderCatalog.JSON): SyncResult = runBlocking {
    GrinderCatalogSeeder(db.grinderDao(), { json }, grinderSettings, grinderSettings).sync()
  }

  /**
   * Marca el primer arranque como hecho (sin esto aparece la elección de modo) y apaga los recorridos guiados, salvo los
   * de [showTours]: los tests que los prueban dejan pendientes solo esos.
   */
  fun skipOnboarding(mode: ExperienceMode = ExperienceMode.BASIC, showTours: Collection<Tour> = emptyList()) {
    experienceSettings.completeOnboarding(mode)
    skipTours(except = showTours)
  }

  /** Marca como vistos todos los pasos de los recorridos, menos los de [except]. */
  fun skipTours(except: Collection<Tour> = emptyList()) {
    val shown = except.flatMap { tour -> tour.steps.map { it.id } }.toSet()
    tourSettings.markSeen(Tours.allStepIds - shown)
  }

  fun close() {
    scaleSession.disconnect()
    appScope.cancel()
    db.close()
    photosDir.deleteRecursively()
    backupDir.deleteRecursively()
    catalogDir.deleteRecursively()
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
    PourlineTheme(darkTheme = theme == ThemeMode.DARK) { MainNavigation(deps.dependencies) }
  }
}

/** Las descripciones (es y en) de los iconos de sección que ya no están en ninguna barra superior. */
val SectionIconDescriptions = listOf("Abrir cafés", "Open coffees", "Abrir recetas", "Open recipes", "Abrir historial", "Open history")
