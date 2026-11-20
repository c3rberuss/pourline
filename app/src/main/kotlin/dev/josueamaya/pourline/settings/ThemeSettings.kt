package dev.josueamaya.pourline.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Composable
fun ThemeMode.isDark(): Boolean = when (this) {
  ThemeMode.SYSTEM -> isSystemInDarkTheme()
  ThemeMode.LIGHT -> false
  ThemeMode.DARK -> true
}

/** Preferencia de apariencia del usuario, guardada en SharedPreferences. */
class ThemeSettings internal constructor(private val prefs: SharedPreferences) : BackupableSettings {
  constructor(context: Context, prefsName: String = BrewSettings.PREFS_NAME) :
    this(context.applicationContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE))

  private val _mode = MutableStateFlow(readMode())
  val mode: StateFlow<ThemeMode> = _mode.asStateFlow()

  fun setMode(mode: ThemeMode) {
    prefs.edit { putString(KEY_MODE, mode.name) }
    _mode.value = mode
  }

  override fun exportInto(snapshot: SettingsSnapshot): SettingsSnapshot = snapshot.copy(themeMode = prefs.storedString(KEY_MODE))

  override fun restoreFrom(snapshot: SettingsSnapshot) {
    prefs.edit(commit = true) { putOrRemove(KEY_MODE, snapshot.themeMode) }
    _mode.value = readMode()
  }

  private fun readMode(): ThemeMode =
    prefs.getString(KEY_MODE, null)?.let { stored -> ThemeMode.entries.firstOrNull { it.name == stored } } ?: ThemeMode.SYSTEM

  companion object {
    const val KEY_MODE = "theme_mode"
  }
}
