package dev.dotscale.settings

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** La referencia (objetivo) activa: una receta o una preparación guardada. */
sealed interface ReferenceKey {
  data class Recipe(val id: Long) : ReferenceKey

  data class Brew(val id: Long) : ReferenceKey
}

/** Dónde se recuerda la referencia activa. Lo implementa [TargetSettings]; en los tests, un falso. */
interface ReferenceStore {
  val reference: StateFlow<ReferenceKey?>

  fun setReference(key: ReferenceKey?)
}

/**
 * Referencia activa y avisos de etapa, en el mismo archivo `"settings"` que [BrewSettings]. Hay una sola
 * instancia por proceso (ver `DotScaleApp`).
 */
class TargetSettings(
  context: Context,
  prefsName: String = BrewSettings.PREFS_NAME,
) : ReferenceStore {
  private val prefs = context.applicationContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

  private val _reference = MutableStateFlow(readReference())
  override val reference: StateFlow<ReferenceKey?> = _reference.asStateFlow()

  private val _stageVibration = MutableStateFlow(prefs.getBoolean(KEY_STAGE_VIBRATION, true))

  /** Vibrar al empezar cada etapa. Activado por defecto. */
  val stageVibration: StateFlow<Boolean> = _stageVibration.asStateFlow()

  private val _stageSound = MutableStateFlow(prefs.getBoolean(KEY_STAGE_SOUND, true))

  /** Pitido al empezar cada etapa. Activado por defecto. */
  val stageSound: StateFlow<Boolean> = _stageSound.asStateFlow()

  override fun setReference(key: ReferenceKey?) {
    prefs.edit {
      when (key) {
        null -> {
          remove(KEY_REF_TYPE)
          remove(KEY_REF_ID)
        }
        is ReferenceKey.Recipe -> {
          putString(KEY_REF_TYPE, TYPE_RECIPE)
          putLong(KEY_REF_ID, key.id)
        }
        is ReferenceKey.Brew -> {
          putString(KEY_REF_TYPE, TYPE_BREW)
          putLong(KEY_REF_ID, key.id)
        }
      }
    }
    _reference.value = key
  }

  fun setStageVibration(enabled: Boolean) {
    prefs.edit { putBoolean(KEY_STAGE_VIBRATION, enabled) }
    _stageVibration.value = enabled
  }

  fun setStageSound(enabled: Boolean) {
    prefs.edit { putBoolean(KEY_STAGE_SOUND, enabled) }
    _stageSound.value = enabled
  }

  private fun readReference(): ReferenceKey? {
    if (!prefs.contains(KEY_REF_ID)) return null
    val id = prefs.getLong(KEY_REF_ID, 0)
    return when (prefs.getString(KEY_REF_TYPE, null)) {
      TYPE_RECIPE -> ReferenceKey.Recipe(id)
      TYPE_BREW -> ReferenceKey.Brew(id)
      else -> null
    }
  }

  companion object {
    const val KEY_REF_TYPE = "target_ref_type"
    const val KEY_REF_ID = "target_ref_id"
    const val KEY_STAGE_VIBRATION = "stage_vibration"
    const val KEY_STAGE_SOUND = "stage_sound"
    private const val TYPE_RECIPE = "RECIPE"
    private const val TYPE_BREW = "BREW"
  }
}
