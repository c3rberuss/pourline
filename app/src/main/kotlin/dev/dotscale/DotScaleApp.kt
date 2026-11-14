package dev.dotscale

import android.app.Application
import dev.dotscale.ble.AndroidBluetoothEnvironment
import dev.dotscale.ble.ScaleScanner
import dev.dotscale.coffee.CoffeeRepository
import dev.dotscale.coffee.label.MlKitLabelOcr
import dev.dotscale.coffee.photo.CoffeeImages
import dev.dotscale.coffee.photo.CoffeePhotoStore
import dev.dotscale.history.BrewRepository
import dev.dotscale.history.HistoryDatabase
import dev.dotscale.recipe.RecipeRepository
import dev.dotscale.recipe.ResourcesRecipeTexts
import dev.dotscale.session.AndroidScaleConnections
import dev.dotscale.session.AutoReconnect
import dev.dotscale.session.RepositoryScaleCoffees
import dev.dotscale.session.RepositoryTargetReferences
import dev.dotscale.session.ScaleSessionManager
import dev.dotscale.settings.BrewSettings
import dev.dotscale.settings.ConnectionSettings
import dev.dotscale.settings.ExperienceSettings
import dev.dotscale.settings.TargetSettings
import dev.dotscale.settings.ThemeSettings
import dev.dotscale.ui.scale.AndroidStageNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.TimeUnit

class DotScaleApp : Application() {
  /** Una sola instancia por proceso: sobrevive a la rotación y comparte el flujo con el ViewModel. */
  val brewSettings by lazy { BrewSettings(this) }

  /** Referencia activa y avisos de etapa; una sola instancia, como [brewSettings]. */
  val targetSettings by lazy { TargetSettings(this) }

  /** Apariencia (sistema, claro, oscuro); una sola instancia, como [brewSettings]. */
  val themeSettings by lazy { ThemeSettings(this) }

  /** Modo Básico/Experto y primer arranque; una sola instancia, como [brewSettings]. */
  val experienceSettings by lazy { ExperienceSettings(this) }

  override fun onCreate() {
    super.onCreate()
    // Borra las fotos que ningún café referencia (p. ej. si el proceso murió a mitad de una edición).
    applicationScope.launch(Dispatchers.IO) {
      coffeePhotoStore.sweepOrphans(coffeeRepository.referencedPhotos(), TimeUnit.HOURS.toMillis(24))
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

  /** La última báscula real que llegó a `Ready`; una sola instancia, como [brewSettings]. */
  val connectionSettings by lazy { ConnectionSettings(this) }

  /**
   * La conexión con la báscula y la sesión de preparación: un objeto por proceso. Su ámbito es el hilo principal
   * del proceso, así que cambiar de pestaña o rotar no la toca.
   */
  val scaleSession by lazy {
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
      memory = connectionSettings,
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
