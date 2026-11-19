package dev.josueamaya.pourline

import android.content.Intent
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
import dev.josueamaya.pourline.coffee.photo.LocalCoffeeImages
import dev.josueamaya.pourline.settings.LocalExperienceMode
import dev.josueamaya.pourline.settings.isDark
import dev.josueamaya.pourline.ui.theme.AntTheme
import dev.josueamaya.pourline.ui.theme.PourlineTheme

class MainActivity : ComponentActivity() {
  private val app get() = application as PourlineApp
  private val deps get() = app.dependencies
  private val recipeIntents by lazy {
    RecipeIntentHandler(deps.recipeImports, deps.qrImageReader, { deps.scaleSession.ui.value.hasSession }, app.applicationScope)
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // Con una actividad recreada (rotación, proceso restaurado) o abierta desde Recientes, el intent ya se procesó (§3.5).
    if (shouldHandleLaunchIntent(recreated = savedInstanceState != null, intentFlags = intent.flags)) recipeIntents.handle(intent)

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
        PourlineTheme(darkTheme = darkTheme) {
          Box(Modifier.fillMaxSize().background(AntTheme.colors.bgLayout)) { MainNavigation(deps) }
        }
      }
    }
  }

  /** `singleTask`: una receta que llega con la app ya abierta entra aquí, sin crear otra instancia. */
  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    recipeIntents.handle(intent)
  }

  private companion object {
    val LIGHT_SCRIM = Color.argb(0xE6, 0xFF, 0xFF, 0xFF)
    val DARK_SCRIM = Color.argb(0x80, 0x1B, 0x1B, 0x1B)
  }
}
