package dev.dotscale

import android.app.Application
import dev.dotscale.coffee.CoffeeRepository
import dev.dotscale.coffee.PhotoFiles
import dev.dotscale.history.BrewRepository
import dev.dotscale.history.HistoryDatabase
import dev.dotscale.recipe.RecipeRepository
import dev.dotscale.recipe.ResourcesRecipeTexts
import dev.dotscale.settings.BrewSettings
import dev.dotscale.settings.TargetSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class DotScaleApp : Application() {
  /** Una sola instancia por proceso: sobrevive a la rotación y comparte el flujo con el ViewModel. */
  val brewSettings by lazy { BrewSettings(this) }

  /** Referencia activa y avisos de etapa; una sola instancia, como [brewSettings]. */
  val targetSettings by lazy { TargetSettings(this) }

  /** Ámbito del proceso, para trabajo que no debe cancelarse al destruir una pantalla (p. ej. guardar una preparación). */
  val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  val historyDatabase by lazy { HistoryDatabase.create(this) }

  val brewRepository by lazy { BrewRepository(historyDatabase) }

  val coffeeRepository by lazy { CoffeeRepository(historyDatabase, PhotoFiles { }) }

  val recipeRepository by lazy { RecipeRepository(historyDatabase, ResourcesRecipeTexts(this)) }
}
