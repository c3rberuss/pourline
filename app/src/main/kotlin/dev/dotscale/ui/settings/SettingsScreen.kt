package dev.dotscale.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.dotscale.R
import dev.dotscale.settings.BrewSettings
import dev.dotscale.ui.components.AntCard
import dev.dotscale.ui.components.AntIconButton
import dev.dotscale.ui.components.AntIcons
import dev.dotscale.ui.components.AntSegmented
import dev.dotscale.ui.components.AntSegmentedOption
import dev.dotscale.ui.components.AntText
import dev.dotscale.ui.components.AntTextType
import dev.dotscale.ui.components.AntTopBar
import dev.dotscale.ui.theme.AntTheme

/** Recibe [BrewSettings] por parámetro (sin `CompositionLocal`) para poder probarla aislada. */
@Composable
fun SettingsScreen(settings: BrewSettings, onBack: () -> Unit) {
  val autoFinish by settings.autoFinishSeconds.collectAsStateWithLifecycle()

  Scaffold(
    containerColor = AntTheme.colors.bgLayout,
    topBar = {
      AntTopBar(
        title = stringResource(R.string.settings_title),
        navigationIcon = {
          AntIconButton(AntIcons.ArrowLeft, contentDescription = stringResource(R.string.scale_back), onClick = onBack)
        },
      )
    },
  ) { padding ->
    Column(Modifier.fillMaxSize().padding(padding).padding(AntTheme.spacing.base)) {
      AntCard(Modifier.fillMaxWidth(), title = stringResource(R.string.settings_auto_finish_title)) {
        AntSegmented(
          options = BrewSettings.ALLOWED.map {
            AntSegmentedOption(it, stringResource(R.string.settings_auto_finish_value, it))
          },
          selected = autoFinish,
          onSelect = settings::setAutoFinishSeconds,
          modifier = Modifier.fillMaxWidth(),
        )
        AntText(stringResource(R.string.settings_auto_finish_hint), type = AntTextType.Secondary)
      }
    }
  }
}
