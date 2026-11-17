package dev.josueamaya.pourline.settings

import android.content.Context
import androidx.core.content.edit
import dev.josueamaya.pourline.session.RememberedScale
import dev.josueamaya.pourline.session.ScaleMemory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * La última báscula real conectada, en el mismo archivo `"settings"` que [BrewSettings]. Una sola instancia
 * por proceso (ver `PourlineApp`). La simulada nunca se guarda.
 */
class ConnectionSettings(
  context: Context,
  prefsName: String = BrewSettings.PREFS_NAME,
) : ScaleMemory {
  private val prefs = context.applicationContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

  private val _lastScale = MutableStateFlow(read())
  override val lastScale: StateFlow<RememberedScale?> = _lastScale.asStateFlow()

  override fun remember(scale: RememberedScale) {
    prefs.edit {
      putString(KEY_ADDRESS, scale.address)
      putString(KEY_NAME, scale.name)
    }
    _lastScale.value = scale
  }

  private fun read(): RememberedScale? {
    val address = prefs.getString(KEY_ADDRESS, null) ?: return null
    return RememberedScale(address, prefs.getString(KEY_NAME, null) ?: address)
  }

  companion object {
    const val KEY_ADDRESS = "last_scale_address"
    const val KEY_NAME = "last_scale_name"
  }
}
