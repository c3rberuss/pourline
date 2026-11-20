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

  /** El usuario ya eligió el modo (en el onboarding, en Ajustes o en la pregunta de quien actualiza). */
  val scaleModeChosen: StateFlow<Boolean>

  fun setUseScale(enabled: Boolean)
}

/**
 * La última báscula real conectada y el modo de báscula, en el mismo archivo `"settings"` que [BrewSettings]. Una sola
 * instancia por proceso (ver `PourlineApp`). La simulada nunca se guarda. Mientras el usuario no elige el modo, se usa
 * báscula si hay una recordada.
 */
class ConnectionSettings internal constructor(private val prefs: SharedPreferences) : ScaleMemory, ScaleModeStore, BackupableSettings {
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

  private val _useScale = MutableStateFlow(readUseScale())
  override val useScale: StateFlow<Boolean> = _useScale.asStateFlow()

  private val _scaleModeChosen = MutableStateFlow(prefs.contains(KEY_USE_SCALE))
  override val scaleModeChosen: StateFlow<Boolean> = _scaleModeChosen.asStateFlow()

  override fun setUseScale(enabled: Boolean) {
    prefs.edit { putBoolean(KEY_USE_SCALE, enabled) }
    _useScale.value = enabled
    _scaleModeChosen.value = true
  }

  override fun exportInto(snapshot: SettingsSnapshot): SettingsSnapshot = snapshot.copy(
    useScale = prefs.storedBoolean(KEY_USE_SCALE),
    lastScaleAddress = prefs.storedString(KEY_ADDRESS),
    lastScaleName = prefs.storedString(KEY_NAME),
  )

  override fun restoreFrom(snapshot: SettingsSnapshot) {
    prefs.edit(commit = true) {
      putOrRemove(KEY_USE_SCALE, snapshot.useScale)
      putOrRemove(KEY_ADDRESS, snapshot.lastScaleAddress)
      putOrRemove(KEY_NAME, snapshot.lastScaleName)
    }
    _lastScale.value = read()
    _useScale.value = readUseScale()
    _scaleModeChosen.value = prefs.contains(KEY_USE_SCALE)
  }

  /** Sin elección, se usa báscula si hay una recordada. */
  private fun readUseScale(): Boolean =
    if (prefs.contains(KEY_USE_SCALE)) prefs.getBoolean(KEY_USE_SCALE, true) else _lastScale.value != null

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
