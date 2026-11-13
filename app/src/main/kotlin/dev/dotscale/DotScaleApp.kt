package dev.dotscale

import android.app.Application
import dev.dotscale.settings.BrewSettings

class DotScaleApp : Application() {
  /** Una sola instancia por proceso: sobrevive a la rotación y comparte el flujo con el ViewModel. */
  val brewSettings by lazy { BrewSettings(this) }
}
