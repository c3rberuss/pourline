package dev.josueamaya.pourline

import android.app.Application
import android.util.Log
import androidx.core.content.pm.PackageInfoCompat
import dev.josueamaya.pourline.backup.AppVersion
import dev.josueamaya.pourline.backup.BackupManager
import dev.josueamaya.pourline.backup.ContentResolverStreams
import dev.josueamaya.pourline.backup.ThumbnailMaker
import dev.josueamaya.pourline.ble.AndroidBluetoothEnvironment
import dev.josueamaya.pourline.ble.ScaleScanner
import dev.josueamaya.pourline.coffee.CoffeeRepository
import dev.josueamaya.pourline.coffee.label.MlKitLabelOcr
import dev.josueamaya.pourline.coffee.photo.CoffeeImages
import dev.josueamaya.pourline.coffee.photo.COFFEE_PHOTOS_DIR
import dev.josueamaya.pourline.coffee.photo.CoffeePhotoStore
import dev.josueamaya.pourline.grinder.AssetCatalogSource
import dev.josueamaya.pourline.grinder.GrinderCatalogSeeder
import dev.josueamaya.pourline.grinder.GrinderRepository
import dev.josueamaya.pourline.grinder.ResourcesGrindTexts
import dev.josueamaya.pourline.grinder.SyncResult
import dev.josueamaya.pourline.history.BrewRepository
import dev.josueamaya.pourline.history.HistoryDatabase
import dev.josueamaya.pourline.recipe.RecipeRepository
import dev.josueamaya.pourline.recipe.ResourcesRecipeTexts
import dev.josueamaya.pourline.recipe.catalog.CatalogCache
import dev.josueamaya.pourline.recipe.catalog.HttpCatalogFetcher
import dev.josueamaya.pourline.recipe.catalog.RecipeCatalogRepository
import dev.josueamaya.pourline.recipe.catalog.ResourcesCatalogLanguage
import dev.josueamaya.pourline.recipe.catalog.grinderCatalogByKey
import dev.josueamaya.pourline.recipe.share.PendingRecipeImport
import dev.josueamaya.pourline.share.MlKitQrImageReader
import dev.josueamaya.pourline.session.AndroidScaleConnections
import dev.josueamaya.pourline.session.AutoReconnect
import dev.josueamaya.pourline.session.BrewKeepAlive
import dev.josueamaya.pourline.session.BrewService
import dev.josueamaya.pourline.session.GrindSelection
import dev.josueamaya.pourline.session.RepositorySessionGrinders
import dev.josueamaya.pourline.session.SessionGrind
import dev.josueamaya.pourline.session.RepositoryScaleCoffees
import dev.josueamaya.pourline.session.RepositoryTargetReferences
import dev.josueamaya.pourline.session.ScaleSessionManager
import dev.josueamaya.pourline.settings.BrewSettings
import dev.josueamaya.pourline.settings.ConnectionSettings
import dev.josueamaya.pourline.settings.ExperienceSettings
import dev.josueamaya.pourline.settings.GrinderSettings
import dev.josueamaya.pourline.settings.TargetSettings
import dev.josueamaya.pourline.settings.ThemeSettings
import dev.josueamaya.pourline.settings.TourSettings
import dev.josueamaya.pourline.tools.ResourcesFourSixTexts
import dev.josueamaya.pourline.ui.scale.AndroidStageNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.TimeUnit

private const val SEEDER_TAG = "GrinderCatalogSeeder"

/** El directorio de trabajo de la importación (staging, marcador y fotos anteriores), dentro de `filesDir`. */
private const val BACKUP_WORK_DIR = "backup_import"

class PourlineApp : Application() {
  /** Una sola instancia por proceso: sobrevive a la rotación y comparte el flujo con el ViewModel. */
  val brewSettings by lazy { BrewSettings(this) }

  /** Referencia activa y avisos de etapa; una sola instancia, como [brewSettings]. */
  val targetSettings by lazy { TargetSettings(this) }

  /** Apariencia (sistema, claro, oscuro); una sola instancia, como [brewSettings]. */
  val themeSettings by lazy { ThemeSettings(this) }

  /** Modo Básico/Experto y primer arranque; una sola instancia, como [brewSettings]. */
  val experienceSettings by lazy { ExperienceSettings(this) }

  /** Pasos de los recorridos guiados ya vistos; una sola instancia, como [brewSettings]. */
  val tourSettings by lazy { TourSettings(this) }

  /** Molino predeterminado y versión del catálogo; una sola instancia, como [brewSettings]. */
  val grinderSettings by lazy { GrinderSettings(this) }

  val grindTexts by lazy { ResourcesGrindTexts(this) }

  override fun onCreate() {
    super.onCreate()
    applicationScope.launch(Dispatchers.IO) {
      // Primero, terminar o deshacer una importación que la muerte del proceso dejó a medias (SPEC-011 §3.5): el barrido de
      // fotos y la sincronización del catálogo no pueden correr sobre un cambio de fotos o de ajustes a medio hacer.
      try {
        backupManager.recoverInterruptedImport()
      } catch (e: Exception) {
        Log.e("BackupManager", "Import recovery failed", e)
      }
      // Borra las fotos que ningún café referencia (p. ej. si el proceso murió a mitad de una edición).
      launch {
        coffeePhotoStore.sweepOrphans(coffeeRepository.referencedPhotos(), TimeUnit.HOURS.toMillis(24))
      }
      // Sincroniza el catálogo de molinos con la base (idempotente); un archivo inválido no cambia nada y solo se registra.
      launch {
        when (val result = grinderCatalogSeeder.sync()) {
          is SyncResult.Failed -> Log.w(SEEDER_TAG, "Catalog sync failed: ${result.reason}")
          else -> Log.i(SEEDER_TAG, "Catalog sync: $result")
        }
      }
    }
    // Con una preparación abierta, un servicio en primer plano evita que Android congele o mate el proceso al salir.
    BrewKeepAlive(scaleSession.ui, MainScope()) { BrewService.start(this, scaleSession.ui.value.scaleless) }
  }

  /** Ámbito del proceso, para trabajo que no debe cancelarse al destruir una pantalla (p. ej. guardar una preparación). */
  val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  val historyDatabase by lazy { HistoryDatabase.create(this) }

  val brewRepository by lazy { BrewRepository(historyDatabase) }

  /** Fotos de las etiquetas, con sus miniaturas, en `filesDir/coffee_photos`. */
  val coffeePhotoStore by lazy { CoffeePhotoStore(File(filesDir, COFFEE_PHOTOS_DIR), cacheDir) }

  /** Decodifica y cachea (16 MB) las fotos para la interfaz. */
  val coffeeImages by lazy { CoffeeImages(coffeePhotoStore, maxBytes = 16 * 1024 * 1024) }

  /** El OCR se crea al leer la primera etiqueta. */
  val labelOcr by lazy { MlKitLabelOcr() }

  val coffeeRepository by lazy { CoffeeRepository(historyDatabase, coffeePhotoStore) }

  val recipeRepository by lazy { RecipeRepository(historyDatabase, ResourcesRecipeTexts(this)) }

  /** El idioma de los textos del catálogo de recetas; lee el recurso en cada llamada. */
  val catalogLanguage by lazy { ResourcesCatalogLanguage(this) }

  /**
   * El catálogo de recetas recomendadas (SPEC-012): la copia del APK, la caché en `filesDir/catalog` y la descarga de
   * `BuildConfig.RECIPE_CATALOG_URL`. Nada la descarga hasta que una pantalla lo pide.
   */
  val recipeCatalog by lazy {
    RecipeCatalogRepository(
      bundled = AssetCatalogSource(this, "catalog/recipes.json"),
      cache = CatalogCache(File(filesDir, "catalog")),
      fetcher = HttpCatalogFetcher(BuildConfig.RECIPE_CATALOG_URL),
      grinders = { grinderCatalogByKey(AssetCatalogSource(this)) },
      scope = applicationScope,
    )
  }

  /** Las recetas importadas que esperan su vista previa: todas las entradas de SPEC-009 la usan. */
  val recipeImports = PendingRecipeImport()

  /** El lector de QR de ML Kit sobre Play Services; el modelo se descarga bajo demanda. */
  val qrImageReader by lazy { MlKitQrImageReader(this) }

  val grinderRepository by lazy { GrinderRepository(historyDatabase, grinderSettings, grindTexts) }

  /** Sincroniza el catálogo de molinos con la base; se invoca al arrancar ([onCreate]). */
  val grinderCatalogSeeder by lazy {
    GrinderCatalogSeeder(historyDatabase.grinderDao(), AssetCatalogSource(this), grinderSettings, grinderSettings)
  }

  /** La última báscula real que llegó a `Ready`; una sola instancia, como [brewSettings]. */
  val connectionSettings by lazy { ConnectionSettings(this) }

  /**
   * La conexión con la báscula y la sesión de preparación: un objeto por proceso. Su ámbito es el hilo principal
   * del proceso, así que cambiar de pestaña o rotar no la toca.
   */
  val scaleSession: ScaleSessionManager by lazy {
    ScaleSessionManager(
      connections = AndroidScaleConnections(this),
      scope = MainScope(),
      autoFinishSeconds = brewSettings.autoFinishSeconds,
      saver = brewRepository,
      applicationScope = applicationScope,
      references = targetSettings,
      targets = RepositoryTargetReferences(recipeRepository, brewRepository),
      notifier = AndroidStageNotifier(this, targetSettings),
      coffeeStore = brewSettings,
      coffees = RepositoryScaleCoffees(coffeeRepository),
      grind = SessionGrind { grindSelection.current() },
      memory = connectionSettings,
      countdownSeconds = brewSettings.countdownSeconds,
    )
  }

  /** "Moler con" y el estado de la tarjeta de molienda; el gestor congela su ajuste al empezar la sesión (D23). */
  val grindSelection: GrindSelection by lazy {
    GrindSelection(
      targets = scaleSession.ui.map { it.target },
      grinders = RepositorySessionGrinders(grinderRepository),
      defaults = grinderSettings,
      texts = grindTexts,
      scope = applicationScope,
    )
  }

  /** El Bluetooth del teléfono: permisos y encendido (receptor de `ACTION_STATE_CHANGED`). */
  val bluetoothEnvironment by lazy { AndroidBluetoothEnvironment(this) }

  /** Reconecta a la última báscula real al abrir la app, al volver a primer plano y al encender el Bluetooth. */
  val autoReconnect by lazy {
    AutoReconnect(scaleSession, connectionSettings, bluetoothEnvironment, MainScope()) { connectionSettings.useScale.value }
  }

  /** Exporta e importa todos los datos y los ajustes de uso (SPEC-011); una sola instancia por proceso. */
  val backupManager by lazy {
    BackupManager(
      db = historyDatabase,
      photosDir = File(filesDir, COFFEE_PHOTOS_DIR),
      workDir = File(filesDir, BACKUP_WORK_DIR),
      // `TourSettings` no va: el estado del recorrido guiado es del dispositivo.
      settings = listOf(themeSettings, experienceSettings, connectionSettings, brewSettings, targetSettings, grinderSettings),
      seeder = grinderCatalogSeeder,
      streams = ContentResolverStreams(contentResolver),
      thumbnails = ThumbnailMaker(coffeePhotoStore::regenerateThumbnail),
      appVersion = appVersion(),
      scope = applicationScope,
      // Lo que Room no invalida: deshacer pendientes (reinsertarían un id que ahora es de otra fila), "Moler con" y las fotos.
      invalidateCaches = {
        brewRepository.clearPendingUndo()
        recipeRepository.clearPendingUndo()
        grindSelection.clearChoice()
        coffeeImages.clear()
      },
    )
  }

  @Suppress("DEPRECATION")
  private fun appVersion(): AppVersion {
    val info = packageManager.getPackageInfo(packageName, 0)
    return AppVersion(info.versionName.orEmpty(), PackageInfoCompat.getLongVersionCode(info))
  }

  /** Lo que reciben las pantallas (D32). */
  val dependencies by lazy {
    AppDependencies(
      brewSettings = brewSettings,
      targetSettings = targetSettings,
      themeSettings = themeSettings,
      experienceSettings = experienceSettings,
      tourSettings = tourSettings,
      freeBrewDefaults = brewSettings,
      scaleMemory = connectionSettings,
      scaleMode = connectionSettings,
      brewRepository = brewRepository,
      coffeeRepository = coffeeRepository,
      recipeRepository = recipeRepository,
      recipeTexts = ResourcesRecipeTexts(this),
      recipeCatalog = recipeCatalog,
      catalogLanguage = catalogLanguage,
      recipeImports = recipeImports,
      qrImageReader = qrImageReader,
      grinderSettings = grinderSettings,
      grinderRepository = grinderRepository,
      grindTexts = grindTexts,
      fourSixTexts = ResourcesFourSixTexts(this),
      grindSelection = grindSelection,
      coffeePhotoStore = coffeePhotoStore,
      coffeeImages = coffeeImages,
      labelOcr = labelOcr,
      scaleSession = scaleSession,
      autoReconnect = autoReconnect,
      bluetooth = bluetoothEnvironment,
      scanner = ScaleScanner(this),
      backup = backupManager,
    )
  }
}
