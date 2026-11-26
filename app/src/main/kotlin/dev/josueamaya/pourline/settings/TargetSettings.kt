package dev.josueamaya.pourline.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** La referencia (objetivo) activa: una receta, una preparación guardada o una receta del catálogo (por su clave). */
sealed interface ReferenceKey {
  data class Recipe(val id: Long) : ReferenceKey

  data class Brew(val id: Long) : ReferenceKey

  /** Una receta del catálogo de recomendadas, sin guardarla (SPEC-012). */
  data class Catalog(val key: String) : ReferenceKey
}

/** Dónde se recuerda la referencia activa. Lo implementa [TargetSettings]; en los tests, un falso. */
interface ReferenceStore {
  val reference: StateFlow<ReferenceKey?>

  fun setReference(key: ReferenceKey?)
}

/**
 * Referencia activa y avisos de etapa, en el mismo archivo `"settings"` que [BrewSettings]. Hay una sola
 * instancia por proceso (ver `PourlineApp`).
 */
class TargetSettings internal constructor(private val prefs: SharedPreferences) : ReferenceStore, BackupableSettings {
  constructor(context: Context, prefsName: String = BrewSettings.PREFS_NAME) :
    this(context.applicationContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE))

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
          remove(KEY_REF_KEY)
        }
        is ReferenceKey.Recipe -> {
          putString(KEY_REF_TYPE, TYPE_RECIPE)
          putLong(KEY_REF_ID, key.id)
          remove(KEY_REF_KEY)
        }
        is ReferenceKey.Brew -> {
          putString(KEY_REF_TYPE, TYPE_BREW)
          putLong(KEY_REF_ID, key.id)
          remove(KEY_REF_KEY)
        }
        is ReferenceKey.Catalog -> {
          putString(KEY_REF_TYPE, TYPE_CATALOG)
          putString(KEY_REF_KEY, key.key)
          remove(KEY_REF_ID)
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

  override fun exportInto(snapshot: SettingsSnapshot): SettingsSnapshot = snapshot.copy(
    targetRefType = prefs.storedString(KEY_REF_TYPE),
    targetRefId = prefs.storedLong(KEY_REF_ID),
    targetRefKey = prefs.storedString(KEY_REF_KEY),
    stageVibration = prefs.storedBoolean(KEY_STAGE_VIBRATION),
    stageSound = prefs.storedBoolean(KEY_STAGE_SOUND),
  )

  override fun restoreFrom(snapshot: SettingsSnapshot) {
    prefs.edit(commit = true) {
      putOrRemove(KEY_REF_TYPE, snapshot.targetRefType)
      putOrRemove(KEY_REF_ID, snapshot.targetRefId)
      putOrRemove(KEY_REF_KEY, snapshot.targetRefKey)
      putOrRemove(KEY_STAGE_VIBRATION, snapshot.stageVibration)
      putOrRemove(KEY_STAGE_SOUND, snapshot.stageSound)
    }
    _reference.value = readReference()
    _stageVibration.value = prefs.getBoolean(KEY_STAGE_VIBRATION, true)
    _stageSound.value = prefs.getBoolean(KEY_STAGE_SOUND, true)
  }

  private fun readReference(): ReferenceKey? {
    return when (prefs.getString(KEY_REF_TYPE, null)) {
      TYPE_CATALOG -> prefs.storedString(KEY_REF_KEY)?.takeIf { it.isNotBlank() }?.let { ReferenceKey.Catalog(it) }
      TYPE_RECIPE -> prefs.storedLong(KEY_REF_ID)?.let { ReferenceKey.Recipe(it) }
      TYPE_BREW -> prefs.storedLong(KEY_REF_ID)?.let { ReferenceKey.Brew(it) }
      else -> null
    }
  }

  companion object {
    const val KEY_REF_TYPE = "target_ref_type"
    const val KEY_REF_ID = "target_ref_id"
    const val KEY_REF_KEY = "target_ref_key"
    const val KEY_STAGE_VIBRATION = "stage_vibration"
    const val KEY_STAGE_SOUND = "stage_sound"
    private const val TYPE_RECIPE = "RECIPE"
    private const val TYPE_BREW = "BREW"
    private const val TYPE_CATALOG = "CATALOG"
  }
}
