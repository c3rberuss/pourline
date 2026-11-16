package dev.dotscale

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.dotscale.coffee.photo.LocalCoffeeImages
import dev.dotscale.settings.LocalExperienceMode
import dev.dotscale.settings.isDark
import dev.dotscale.ui.theme.AntTheme
import dev.dotscale.ui.theme.DotScaleTheme

class MainActivity : ComponentActivity() {
  private val app get() = application as DotScaleApp
  private val deps get() = app.dependencies

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    enableEdgeToEdge()
    setContent {
      val themeMode by deps.themeSettings.mode.collectAsStateWithLifecycle()
      val experienceMode by deps.experienceSettings.mode.collectAsStateWithLifecycle()
      val darkTheme = themeMode.isDark()

      // Los iconos de las barras del sistema siguen al tema de la app, no solo al del teléfono.
      DisposableEffect(darkTheme) {
        enableEdgeToEdge(
          statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
          navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { darkTheme },
        )
        onDispose {}
      }

      // El primer ON_START del proceso es el arranque de la app; los siguientes, volver a primer plano (D29).
      LifecycleEventEffect(Lifecycle.Event.ON_START) { deps.autoReconnect.onForeground() }

      CompositionLocalProvider(
        LocalExperienceMode provides experienceMode,
        LocalCoffeeImages provides deps.coffeeImages,
      ) {
        DotScaleTheme(darkTheme = darkTheme) {
          Box(Modifier.fillMaxSize().background(AntTheme.colors.bgLayout)) { MainNavigation(deps) }
        }
      }
    }
  }

  private companion object {
    val LIGHT_SCRIM = Color.argb(0xE6, 0xFF, 0xFF, 0xFF)
    val DARK_SCRIM = Color.argb(0x80, 0x1B, 0x1B, 0x1B)
  }
}
