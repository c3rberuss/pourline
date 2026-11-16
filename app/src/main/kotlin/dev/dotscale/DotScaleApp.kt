package dev.dotscale

import android.app.Application
import android.util.Log
import dev.dotscale.ble.AndroidBluetoothEnvironment
import dev.dotscale.ble.ScaleScanner
import dev.dotscale.coffee.CoffeeRepository
import dev.dotscale.coffee.label.MlKitLabelOcr
import dev.dotscale.coffee.photo.CoffeeImages
import dev.dotscale.coffee.photo.CoffeePhotoStore
import dev.dotscale.grinder.AssetCatalogSource
import dev.dotscale.grinder.GrinderCatalogSeeder
import dev.dotscale.grinder.GrinderRepository
import dev.dotscale.grinder.ResourcesGrindTexts
import dev.dotscale.grinder.SyncResult
import dev.dotscale.history.BrewRepository
import dev.dotscale.history.HistoryDatabase
import dev.dotscale.recipe.RecipeRepository
import dev.dotscale.recipe.ResourcesRecipeTexts
import dev.dotscale.session.AndroidScaleConnections
import dev.dotscale.session.AutoReconnect
import dev.dotscale.session.GrindSelection
import dev.dotscale.session.RepositorySessionGrinders
import dev.dotscale.session.SessionGrind
import dev.dotscale.session.RepositoryScaleCoffees
import dev.dotscale.session.RepositoryTargetReferences
import dev.dotscale.session.ScaleSessionManager
import dev.dotscale.settings.BrewSettings
import dev.dotscale.settings.ConnectionSettings
import dev.dotscale.settings.ExperienceSettings
import dev.dotscale.settings.GrinderSettings
import dev.dotscale.settings.TargetSettings
import dev.dotscale.settings.ThemeSettings
import dev.dotscale.ui.scale.AndroidStageNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.TimeUnit

private const val SEEDER_TAG = "GrinderCatalogSeeder"

class DotScaleApp : Application() {
  /** Una sola instancia por proceso: sobrevive a la rotación y comparte el flujo con el ViewModel. */
  val brewSettings by lazy { BrewSettings(this) }

  /** Referencia activa y avisos de etapa; una sola instancia, como [brewSettings]. */
  val targetSettings by lazy { TargetSettings(this) }

  /** Apariencia (sistema, claro, oscuro); una sola instancia, como [brewSettings]. */
  val themeSettings by lazy { ThemeSettings(this) }

  /** Modo Básico/Experto y primer arranque; una sola instancia, como [brewSettings]. */
  val experienceSettings by lazy { ExperienceSettings(this) }

  /** Molino predeterminado y versión del catálogo; una sola instancia, como [brewSettings]. */
  val grinderSettings by lazy { GrinderSettings(this) }

  val grindTexts by lazy { ResourcesGrindTexts(this) }

  override fun onCreate() {
    super.onCreate()
    // Borra las fotos que ningún café referencia (p. ej. si el proceso murió a mitad de una edición).
    applicationScope.launch(Dispatchers.IO) {
      coffeePhotoStore.sweepOrphans(coffeeRepository.referencedPhotos(), TimeUnit.HOURS.toMillis(24))
    }
    // Sincroniza el catálogo de molinos con la base (idempotente); un archivo inválido no cambia nada y solo se registra.
    applicationScope.launch(Dispatchers.IO) {
      when (val result = grinderCatalogSeeder.sync()) {
        is SyncResult.Failed -> Log.w(SEEDER_TAG, "Catalog sync failed: ${result.reason}")
        else -> Log.i(SEEDER_TAG, "Catalog sync: $result")
      }
    }
  }

  /** Ámbito del proceso, para trabajo que no debe cancelarse al destruir una pantalla (p. ej. guardar una preparación). */
  val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  val historyDatabase by lazy { HistoryDatabase.create(this) }

  val brewRepository by lazy { BrewRepository(historyDatabase) }

  /** Fotos de las etiquetas, con sus miniaturas, en `filesDir/coffee_photos`. */
  val coffeePhotoStore by lazy { CoffeePhotoStore(File(filesDir, "coffee_photos"), cacheDir) }

  /** Decodifica y cachea (16 MB) las fotos para la interfaz. */
  val coffeeImages by lazy { CoffeeImages(coffeePhotoStore, maxBytes = 16 * 1024 * 1024) }

  /** El OCR se crea al leer la primera etiqueta. */
  val labelOcr by lazy { MlKitLabelOcr() }

  val coffeeRepository by lazy { CoffeeRepository(historyDatabase, coffeePhotoStore) }

  val recipeRepository by lazy { RecipeRepository(historyDatabase, ResourcesRecipeTexts(this)) }

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
    AutoReconnect(scaleSession, connectionSettings, bluetoothEnvironment, MainScope())
  }

  /** Lo que reciben las pantallas (D32). */
  val dependencies by lazy {
    AppDependencies(
      brewSettings = brewSettings,
      targetSettings = targetSettings,
      themeSettings = themeSettings,
      experienceSettings = experienceSettings,
      freeBrewDefaults = brewSettings,
      scaleMemory = connectionSettings,
      brewRepository = brewRepository,
      coffeeRepository = coffeeRepository,
      recipeRepository = recipeRepository,
      recipeTexts = ResourcesRecipeTexts(this),
      grinderSettings = grinderSettings,
      grinderRepository = grinderRepository,
      grindTexts = grindTexts,
      grindSelection = grindSelection,
      coffeePhotoStore = coffeePhotoStore,
      coffeeImages = coffeeImages,
      labelOcr = labelOcr,
      scaleSession = scaleSession,
      autoReconnect = autoReconnect,
      bluetooth = bluetoothEnvironment,
      scanner = ScaleScanner(this),
    )
  }
}
