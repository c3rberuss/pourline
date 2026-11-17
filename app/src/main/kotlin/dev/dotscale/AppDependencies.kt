package dev.dotscale

import dev.dotscale.ble.BluetoothEnvironment
import dev.dotscale.ble.DeviceScanner
import dev.dotscale.coffee.CoffeeRepository
import dev.dotscale.coffee.label.LabelOcr
import dev.dotscale.coffee.photo.CoffeeImages
import dev.dotscale.coffee.photo.CoffeePhotoStore
import dev.dotscale.grinder.GrindTexts
import dev.dotscale.grinder.GrinderRepository
import dev.dotscale.history.BrewRepository
import dev.dotscale.recipe.RecipeRepository
import dev.dotscale.recipe.RecipeTexts
import dev.dotscale.session.AutoReconnect
import dev.dotscale.session.GrindSelection
import dev.dotscale.session.ScaleMemory
import dev.dotscale.session.ScaleSessionManager
import dev.dotscale.settings.BrewSettings
import dev.dotscale.settings.ExperienceSettings
import dev.dotscale.settings.FreeBrewDefaults
import dev.dotscale.settings.GrinderSettings
import dev.dotscale.settings.TargetSettings
import dev.dotscale.settings.ThemeSettings
import dev.dotscale.tools.FourSixTexts

/**
 * Los singletons de la app que usan las pantallas (D32). `DotScaleApp.dependencies` los arma para producción;
 * los tests de pestañas y del recorrido del usuario nuevo montan `MainNavigation` con los suyos (Room en memoria,
 * otras preferencias, la báscula simulada).
 */
class AppDependencies(
  val brewSettings: BrewSettings,
  val targetSettings: TargetSettings,
  val themeSettings: ThemeSettings,
  val experienceSettings: ExperienceSettings,
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
