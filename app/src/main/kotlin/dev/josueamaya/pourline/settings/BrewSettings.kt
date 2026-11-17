package dev.josueamaya.pourline.settings

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

/** La dosis y el ratio de una preparación sin receta (D27). Lo implementa [BrewSettings]; en los tests, un falso. */
interface FreeBrewDefaults {
  /** Dosis en gramos, de [MIN_DOSE_G] a [MAX_DOSE_G] con un decimal. */
  val defaultDoseG: StateFlow<Double>

  /** Ratio agua:café (16,0 → 1:16), de [MIN_RATIO] a [MAX_RATIO] con un decimal. */
  val defaultRatio: StateFlow<Double>

  fun setDefaultDoseG(grams: Double)

  fun setDefaultRatio(ratio: Double)

  companion object {
    const val MIN_DOSE_G = 5.0
    const val MAX_DOSE_G = 60.0
    const val DEFAULT_DOSE_G = 15.0
    const val MIN_RATIO = 10.0
    const val MAX_RATIO = 20.0
    const val DEFAULT_RATIO = 16.0
  }
}

/**
 * Preferencias de la preparación, en el mismo archivo `"settings"` que [ThemeSettings].
 * Hay una sola instancia por proceso (ver `PourlineApp`), para que el ViewModel de la pantalla
 * Scale y la pantalla de ajustes observen el mismo flujo.
 */
class BrewSettings(
  context: Context,
  prefsName: String = PREFS_NAME,
) : CoffeeStore, FreeBrewDefaults {
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

  private val _defaultDoseG = MutableStateFlow(readFloat(KEY_DEFAULT_DOSE, FreeBrewDefaults.MIN_DOSE_G, FreeBrewDefaults.MAX_DOSE_G, FreeBrewDefaults.DEFAULT_DOSE_G))
  override val defaultDoseG: StateFlow<Double> = _defaultDoseG.asStateFlow()

  override fun setDefaultDoseG(grams: Double) {
    require(grams in FreeBrewDefaults.MIN_DOSE_G..FreeBrewDefaults.MAX_DOSE_G) { "Dosis fuera de rango: $grams" }
    prefs.edit { putFloat(KEY_DEFAULT_DOSE, grams.toFloat()) }
    _defaultDoseG.value = grams
  }

  private val _defaultRatio = MutableStateFlow(readFloat(KEY_DEFAULT_RATIO, FreeBrewDefaults.MIN_RATIO, FreeBrewDefaults.MAX_RATIO, FreeBrewDefaults.DEFAULT_RATIO))
  override val defaultRatio: StateFlow<Double> = _defaultRatio.asStateFlow()

  override fun setDefaultRatio(ratio: Double) {
    require(ratio in FreeBrewDefaults.MIN_RATIO..FreeBrewDefaults.MAX_RATIO) { "Ratio fuera de rango: $ratio" }
    prefs.edit { putFloat(KEY_DEFAULT_RATIO, ratio.toFloat()) }
    _defaultRatio.value = ratio
  }

  /** Un decimal guardado como `Float` se lee redondeado a un decimal (15,3 no debe salir como 15,300000190734863). */
  private fun readFloat(key: String, min: Double, max: Double, default: Double): Double =
    if (prefs.contains(key)) {
      (Math.round(prefs.getFloat(key, default.toFloat()) * 10.0) / 10.0).takeIf { it in min..max } ?: default
    } else {
      default
    }

  private fun readLastCoffee(): Long? = if (prefs.contains(KEY_LAST_COFFEE)) prefs.getLong(KEY_LAST_COFFEE, 0L) else null

  private fun readAutoFinish(): Int = prefs.getInt(KEY_AUTO_FINISH, DEFAULT_SECONDS).takeIf { it in ALLOWED } ?: DEFAULT_SECONDS

  companion object {
    val ALLOWED = listOf(30, 45, 60, 90, 120)
    const val DEFAULT_SECONDS = 60
    const val PREFS_NAME = "settings"
    const val KEY_AUTO_FINISH = "auto_finish_seconds"
    const val KEY_LAST_COFFEE = "last_coffee_id"
    const val KEY_DEFAULT_DOSE = "default_dose_g"
    const val KEY_DEFAULT_RATIO = "default_ratio"
  }
}
