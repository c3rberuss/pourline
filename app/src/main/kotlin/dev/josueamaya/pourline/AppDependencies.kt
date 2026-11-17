package dev.josueamaya.pourline

import dev.josueamaya.pourline.ble.BluetoothEnvironment
import dev.josueamaya.pourline.ble.DeviceScanner
import dev.josueamaya.pourline.coffee.CoffeeRepository
import dev.josueamaya.pourline.coffee.label.LabelOcr
import dev.josueamaya.pourline.coffee.photo.CoffeeImages
import dev.josueamaya.pourline.coffee.photo.CoffeePhotoStore
import dev.josueamaya.pourline.grinder.GrindTexts
import dev.josueamaya.pourline.grinder.GrinderRepository
import dev.josueamaya.pourline.history.BrewRepository
import dev.josueamaya.pourline.recipe.RecipeRepository
import dev.josueamaya.pourline.recipe.RecipeTexts
import dev.josueamaya.pourline.session.AutoReconnect
import dev.josueamaya.pourline.session.GrindSelection
import dev.josueamaya.pourline.session.ScaleMemory
import dev.josueamaya.pourline.session.ScaleSessionManager
import dev.josueamaya.pourline.settings.BrewSettings
import dev.josueamaya.pourline.settings.ExperienceSettings
import dev.josueamaya.pourline.settings.FreeBrewDefaults
import dev.josueamaya.pourline.settings.GrinderSettings
import dev.josueamaya.pourline.settings.TargetSettings
import dev.josueamaya.pourline.settings.ThemeSettings
import dev.josueamaya.pourline.settings.TourSettings
import dev.josueamaya.pourline.tools.FourSixTexts

/**
 * Los singletons de la app que usan las pantallas (D32). `PourlineApp.dependencies` los arma para producción;
 * los tests de pestañas y del recorrido del usuario nuevo montan `MainNavigation` con los suyos (Room en memoria,
 * otras preferencias, la báscula simulada).
 */
class AppDependencies(
  val brewSettings: BrewSettings,
  val targetSettings: TargetSettings,
  val themeSettings: ThemeSettings,
  val experienceSettings: ExperienceSettings,
  val tourSettings: TourSettings,
  val freeBrewDefaults: FreeBrewDefaults,
  val scaleMemory: ScaleMemory,
  val brewRepository: BrewRepository,
  val coffeeRepository: CoffeeRepository,
  val recipeRepository: RecipeRepository,
  val recipeTexts: RecipeTexts,
  val grinderSettings: GrinderSettings,
  val grinderRepository: GrinderRepository,
  val grindTexts: GrindTexts,
  val fourSixTexts: FourSixTexts,
  val grindSelection: GrindSelection,
  val coffeePhotoStore: CoffeePhotoStore,
  val coffeeImages: CoffeeImages,
  val labelOcr: LabelOcr,
  val scaleSession: ScaleSessionManager,
  val autoReconnect: AutoReconnect,
  val bluetooth: BluetoothEnvironment,
  val scanner: DeviceScanner,
)
