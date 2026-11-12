package dev.dotscale.settings

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
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
class ThemeSettings(context: Context) {
  private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
  private val _mode = MutableStateFlow(readMode())
  val mode: StateFlow<ThemeMode> = _mode.asStateFlow()

  fun setMode(mode: ThemeMode) {
    prefs.edit { putString(KEY_MODE, mode.name) }
    _mode.value = mode
  }

  private fun readMode(): ThemeMode =
    prefs.getString(KEY_MODE, null)?.let { stored -> ThemeMode.entries.firstOrNull { it.name == stored } } ?: ThemeMode.SYSTEM

  private companion object {
    const val PREFS_NAME = "settings"
    const val KEY_MODE = "theme_mode"
  }
}

/** Ajustes de tema de la app; `null` en previews, donde el selector no se muestra. */
val LocalThemeSettings = staticCompositionLocalOf<ThemeSettings?> { null }
