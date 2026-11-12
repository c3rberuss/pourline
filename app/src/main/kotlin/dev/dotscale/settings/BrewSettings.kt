package dev.dotscale.settings

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Preferencias de la preparación, en el mismo archivo `"settings"` que [ThemeSettings].
 * Hay una sola instancia por proceso (ver `DotScaleApp`), para que el ViewModel de la pantalla
 * Scale y la pantalla de ajustes observen el mismo flujo.
 */
class BrewSettings(context: Context) {
  private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
  private val _autoFinishSeconds = MutableStateFlow(readAutoFinish())

  /** Segundos sin verter tras los que la preparación se da por terminada. */
  val autoFinishSeconds: StateFlow<Int> = _autoFinishSeconds.asStateFlow()

  fun setAutoFinishSeconds(seconds: Int) {
    require(seconds in ALLOWED) { "Valor no permitido: $seconds" }
    prefs.edit { putInt(KEY_AUTO_FINISH, seconds) }
    _autoFinishSeconds.value = seconds
  }

  private fun readAutoFinish(): Int = prefs.getInt(KEY_AUTO_FINISH, DEFAULT_SECONDS).takeIf { it in ALLOWED } ?: DEFAULT_SECONDS

  companion object {
    val ALLOWED = listOf(30, 45, 60, 90, 120)
    const val DEFAULT_SECONDS = 60
    const val PREFS_NAME = "settings"
    const val KEY_AUTO_FINISH = "auto_finish_seconds"
  }
}
