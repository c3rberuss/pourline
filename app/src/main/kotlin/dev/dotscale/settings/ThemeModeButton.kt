package dev.dotscale.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.dotscale.R
import dev.dotscale.ui.components.AntDrawer
import dev.dotscale.ui.components.AntIconButton
import dev.dotscale.ui.components.AntIcons
import dev.dotscale.ui.components.AntSegmented
import dev.dotscale.ui.components.AntSegmentedOption
import dev.dotscale.ui.components.AntText
import dev.dotscale.ui.components.AntTextType

/** Botón de la barra superior que abre el selector de apariencia (Sistema / Claro / Oscuro). */
@Composable
fun ThemeModeButton(modifier: Modifier = Modifier) {
  val settings = LocalThemeSettings.current ?: return
  val mode by settings.mode.collectAsStateWithLifecycle()
  var open by rememberSaveable { mutableStateOf(false) }

  AntIconButton(
    icon = mode.icon,
    contentDescription = stringResource(R.string.theme_change),
    onClick = { open = true },
    modifier = modifier,
  )

  if (open) {
    AntDrawer(title = stringResource(R.string.theme_title), onClose = { open = false }) {
      AntSegmented(
        options = listOf(
          AntSegmentedOption(ThemeMode.SYSTEM, stringResource(R.string.theme_system), ThemeMode.SYSTEM.icon),
          AntSegmentedOption(ThemeMode.LIGHT, stringResource(R.string.theme_light), ThemeMode.LIGHT.icon),
          AntSegmentedOption(ThemeMode.DARK, stringResource(R.string.theme_dark), ThemeMode.DARK.icon),
        ),
        selected = mode,
        onSelect = settings::setMode,
        modifier = Modifier.fillMaxWidth(),
      )
      AntText(stringResource(R.string.theme_hint), type = AntTextType.Secondary)
    }
  }
}

private val ThemeMode.icon: ImageVector
  get() = when (this) {
    ThemeMode.SYSTEM -> AntIcons.Contrast
    ThemeMode.LIGHT -> AntIcons.Sun
    ThemeMode.DARK -> AntIcons.Moon
  }
