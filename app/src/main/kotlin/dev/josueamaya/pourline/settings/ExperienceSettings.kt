package dev.josueamaya.pourline.settings

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Dónde se guarda el modo. Lo implementa [ExperienceSettings]; en los tests, un falso. */
interface ModeStore {
  val mode: StateFlow<ExperienceMode>

  fun setMode(mode: ExperienceMode)
}

/**
 * El modo Básico/Experto y si ya se mostró la elección del primer arranque, en el mismo archivo `"settings"`
 * que [BrewSettings]. Una sola instancia por proceso (ver `PourlineApp`). Un valor desconocido se lee como `BASIC`.
 */
class ExperienceSettings(
  context: Context,
  prefsName: String = BrewSettings.PREFS_NAME,
) : ModeStore {
  private val prefs = context.applicationContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

  private val _mode = MutableStateFlow(readMode())
  override val mode: StateFlow<ExperienceMode> = _mode.asStateFlow()

  private val _onboardingDone = MutableStateFlow(prefs.getBoolean(KEY_ONBOARDING_DONE, false))

  /** `true` cuando el usuario ya eligió modo o saltó la pantalla del primer arranque. */
  val onboardingDone: StateFlow<Boolean> = _onboardingDone.asStateFlow()

  override fun setMode(mode: ExperienceMode) {
    prefs.edit { putString(KEY_MODE, mode.name) }
    _mode.value = mode
  }

  /** Guarda el modo elegido y marca el primer arranque como hecho en una sola escritura. */
  fun completeOnboarding(mode: ExperienceMode) {
    prefs.edit {
      putString(KEY_MODE, mode.name)
      putBoolean(KEY_ONBOARDING_DONE, true)
    }
    _mode.value = mode
    _onboardingDone.value = true
  }

  private fun readMode(): ExperienceMode =
    prefs.getString(KEY_MODE, null)?.let { stored -> ExperienceMode.entries.firstOrNull { it.name == stored } } ?: ExperienceMode.BASIC

  companion object {
    const val KEY_MODE = "experience_mode"
    const val KEY_ONBOARDING_DONE = "onboarding_done"
  }
}
