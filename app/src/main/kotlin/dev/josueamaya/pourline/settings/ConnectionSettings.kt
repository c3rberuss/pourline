package dev.josueamaya.pourline.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dev.josueamaya.pourline.session.RememberedScale
import dev.josueamaya.pourline.session.ScaleMemory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Si Preparar trabaja con báscula o sin ella. Lo implementa [ConnectionSettings]; en los tests, uno en memoria. */
interface ScaleModeStore {
  /**
   * `true`: Preparar muestra la tarjeta de báscula, "Empezar" guía con ella y se reconecta sola a la recordada.
   * `false`: sin tarjeta ni reconexión, y "Empezar" abre la preparación sin báscula.
   */
  val useScale: StateFlow<Boolean>

  fun setUseScale(enabled: Boolean)
}

/**
 * La última báscula real conectada y el modo de báscula, en el mismo archivo `"settings"` que [BrewSettings]. Una sola
 * instancia por proceso (ver `PourlineApp`). La simulada nunca se guarda. Mientras el usuario no elige el modo, se usa
 * báscula si hay una recordada.
 */
class ConnectionSettings internal constructor(private val prefs: SharedPreferences) : ScaleMemory, ScaleModeStore {
  constructor(context: Context, prefsName: String = BrewSettings.PREFS_NAME) :
    this(context.applicationContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE))

  private val _lastScale = MutableStateFlow(read())
  override val lastScale: StateFlow<RememberedScale?> = _lastScale.asStateFlow()

  override fun remember(scale: RememberedScale) {
    prefs.edit {
      putString(KEY_ADDRESS, scale.address)
      putString(KEY_NAME, scale.name)
    }
    _lastScale.value = scale
  }

  private val _useScale = MutableStateFlow(
    if (prefs.contains(KEY_USE_SCALE)) prefs.getBoolean(KEY_USE_SCALE, true) else _lastScale.value != null,
  )
  override val useScale: StateFlow<Boolean> = _useScale.asStateFlow()

  override fun setUseScale(enabled: Boolean) {
    prefs.edit { putBoolean(KEY_USE_SCALE, enabled) }
    _useScale.value = enabled
  }

  private fun read(): RememberedScale? {
    val address = prefs.getString(KEY_ADDRESS, null) ?: return null
    return RememberedScale(address, prefs.getString(KEY_NAME, null) ?: address)
  }

  companion object {
    const val KEY_ADDRESS = "last_scale_address"
    const val KEY_NAME = "last_scale_name"
    const val KEY_USE_SCALE = "use_scale"
  }
}
