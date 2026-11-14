package dev.dotscale.settings

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Dónde se recuerda el último café elegido en Scale. Lo implementa [BrewSettings]; en los tests, un falso. */
interface CoffeeStore {
  val lastCoffeeId: StateFlow<Long?>

  fun setLastCoffeeId(id: Long?)
}

/** Sin memoria del último café: el valor por defecto de `ScaleViewModel`. */
object NoCoffeeStore : CoffeeStore {
  override val lastCoffeeId: StateFlow<Long?> = MutableStateFlow(null)

  override fun setLastCoffeeId(id: Long?) = Unit
}

/**
 * Preferencias de la preparación, en el mismo archivo `"settings"` que [ThemeSettings].
 * Hay una sola instancia por proceso (ver `DotScaleApp`), para que el ViewModel de la pantalla
 * Scale y la pantalla de ajustes observen el mismo flujo.
 */
class BrewSettings(
  context: Context,
  prefsName: String = PREFS_NAME,
) : CoffeeStore {
  private val prefs = context.applicationContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
  private val _autoFinishSeconds = MutableStateFlow(readAutoFinish())

  /** Segundos sin verter tras los que la preparación se da por terminada. */
  val autoFinishSeconds: StateFlow<Int> = _autoFinishSeconds.asStateFlow()

  fun setAutoFinishSeconds(seconds: Int) {
    require(seconds in ALLOWED) { "Valor no permitido: $seconds" }
    prefs.edit { putInt(KEY_AUTO_FINISH, seconds) }
    _autoFinishSeconds.value = seconds
  }

  private val _lastCoffeeId = MutableStateFlow(readLastCoffee())

  /** El último café elegido en Scale; `null` si no hay. Scale no lo preselecciona si está archivado o borrado. */
  override val lastCoffeeId: StateFlow<Long?> = _lastCoffeeId.asStateFlow()

  override fun setLastCoffeeId(id: Long?) {
    prefs.edit { if (id == null) remove(KEY_LAST_COFFEE) else putLong(KEY_LAST_COFFEE, id) }
    _lastCoffeeId.value = id
  }

  private fun readLastCoffee(): Long? = if (prefs.contains(KEY_LAST_COFFEE)) prefs.getLong(KEY_LAST_COFFEE, 0L) else null

  private fun readAutoFinish(): Int = prefs.getInt(KEY_AUTO_FINISH, DEFAULT_SECONDS).takeIf { it in ALLOWED } ?: DEFAULT_SECONDS

  companion object {
    val ALLOWED = listOf(30, 45, 60, 90, 120)
    const val DEFAULT_SECONDS = 60
    const val PREFS_NAME = "settings"
    const val KEY_AUTO_FINISH = "auto_finish_seconds"
    const val KEY_LAST_COFFEE = "last_coffee_id"
  }
}
