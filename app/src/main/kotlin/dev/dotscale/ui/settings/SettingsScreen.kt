package dev.dotscale.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.dotscale.R
import dev.dotscale.settings.BrewSettings
import dev.dotscale.settings.ExperienceMode
import dev.dotscale.settings.ExpertFeature
import dev.dotscale.settings.ModeStore
import dev.dotscale.settings.TargetSettings
import dev.dotscale.settings.ThemeMode
import dev.dotscale.settings.ThemeSettings
import dev.dotscale.ui.components.AntButton
import dev.dotscale.ui.components.AntCard
import dev.dotscale.ui.components.AntIconButton
import dev.dotscale.ui.components.AntIcons
import dev.dotscale.ui.components.AntMessageHost
import dev.dotscale.ui.components.AntModal
import dev.dotscale.ui.components.AntSegmented
import dev.dotscale.ui.components.AntSegmentedOption
import dev.dotscale.ui.components.AntStatus
import dev.dotscale.ui.components.AntSwitch
import dev.dotscale.ui.components.AntText
import dev.dotscale.ui.components.AntTextType
import dev.dotscale.ui.components.AntTopBar
import dev.dotscale.ui.components.showAntMessage
import dev.dotscale.ui.theme.AntTheme
import kotlinx.coroutines.launch

/**
 * Recibe los ajustes por parámetro (sin `CompositionLocal`) para poder probarla aislada.
 * [onRestoreRecipes] devuelve las recetas predefinidas a fábrica, tras confirmar. Con [onBack] `null` (es una
 * pestaña) no hay flecha de volver.
 */
@Composable
fun SettingsScreen(
  settings: BrewSettings,
  targetSettings: TargetSettings,
  experienceSettings: ModeStore,
  themeSettings: ThemeSettings,
  onRestoreRecipes: suspend () -> Unit,
  onBack: (() -> Unit)?,
) {
  val autoFinish by settings.autoFinishSeconds.collectAsStateWithLifecycle()
  val experience by experienceSettings.mode.collectAsStateWithLifecycle()
  val theme by themeSettings.mode.collectAsStateWithLifecycle()
  val vibration by targetSettings.stageVibration.collectAsStateWithLifecycle()
  val sound by targetSettings.stageSound.collectAsStateWithLifecycle()
  val snackbar = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()
  var confirmRestore by rememberSaveable { mutableStateOf(false) }
  val restoredText = stringResource(R.string.settings_recipes_restored)

  Scaffold(
    containerColor = AntTheme.colors.bgLayout,
    topBar = {
      AntTopBar(
        title = stringResource(R.string.settings_title),
        navigationIcon = onBack?.let { back ->
          {
            AntIconButton(AntIcons.ArrowLeft, contentDescription = stringResource(R.string.scale_back), onClick = back)
          }
        },
      )
    },
    snackbarHost = { AntMessageHost(snackbar) },
  ) { padding ->
    Column(
      Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(AntTheme.spacing.base),
      verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.base),
    ) {
      AntCard(Modifier.fillMaxWidth(), title = stringResource(R.string.mode_title)) {
        AntSegmented(
          options = listOf(
            AntSegmentedOption(ExperienceMode.BASIC, stringResource(R.string.mode_basic)),
            AntSegmentedOption(ExperienceMode.EXPERT, stringResource(R.string.mode_expert)),
          ),
          selected = experience,
          onSelect = experienceSettings::setMode,
          modifier = Modifier.fillMaxWidth().testTag("settings_mode"),
        )
        AntText(
          stringResource(if (experience == ExperienceMode.BASIC) R.string.mode_basic_hint else R.string.mode_expert_hint),
          type = AntTextType.Secondary,
        )
      }
      AntCard(Modifier.fillMaxWidth(), title = stringResource(R.string.theme_title)) {
        AntSegmented(
          options = ThemeMode.entries.map { AntSegmentedOption(it, stringResource(it.labelRes), it.icon) },
          selected = theme,
          onSelect = themeSettings::setMode,
          modifier = Modifier.fillMaxWidth().testTag("settings_theme"),
        )
        AntText(stringResource(R.string.theme_hint), type = AntTextType.Secondary)
      }
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
      AntCard(Modifier.fillMaxWidth(), title = stringResource(R.string.settings_stage_title)) {
        SwitchRow(
          label = stringResource(R.string.settings_stage_vibration),
          checked = vibration,
          onCheckedChange = targetSettings::setStageVibration,
          tag = "settings_stage_vibration",
        )
        SwitchRow(
          label = stringResource(R.string.settings_stage_sound),
          checked = sound,
          onCheckedChange = targetSettings::setStageSound,
          tag = "settings_stage_sound",
        )
      }
      if (experience.shows(ExpertFeature.RESTORE_BUILT_INS)) {
        AntCard(Modifier.fillMaxWidth(), title = stringResource(R.string.settings_recipes_title)) {
          AntButton(
            text = stringResource(R.string.settings_recipes_restore),
            onClick = { confirmRestore = true },
            modifier = Modifier.fillMaxWidth().testTag("settings_recipes_restore"),
          )
        }
      }
    }
  }

  if (confirmRestore) {
    AntModal(
      title = stringResource(R.string.settings_recipes_restore_title),
      text = stringResource(R.string.settings_recipes_restore_text),
      confirmText = stringResource(R.string.settings_recipes_restore_confirm),
      cancelText = stringResource(R.string.settings_recipes_restore_cancel),
      onConfirm = {
        confirmRestore = false
        scope.launch {
          onRestoreRecipes()
          snackbar.showAntMessage(restoredText, AntStatus.Success)
        }
      },
      onDismiss = { confirmRestore = false },
    )
  }
}

private val ThemeMode.labelRes: Int
  get() = when (this) {
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
  }

private val ThemeMode.icon: ImageVector
  get() = when (this) {
    ThemeMode.SYSTEM -> AntIcons.Contrast
    ThemeMode.LIGHT -> AntIcons.Sun
    ThemeMode.DARK -> AntIcons.Moon
  }

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, tag: String) {
  Row(
    Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(AntTheme.spacing.sm),
  ) {
    AntText(label, Modifier.weight(1f))
    AntSwitch(checked, onCheckedChange, Modifier.testTag(tag))
  }
}
