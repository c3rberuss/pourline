package dev.josueamaya.pourline.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.josueamaya.pourline.R
import dev.josueamaya.pourline.grinder.GrinderRepository
import dev.josueamaya.pourline.settings.BrewSettings
import dev.josueamaya.pourline.settings.DefaultGrinderStore
import dev.josueamaya.pourline.settings.ExperienceMode
import dev.josueamaya.pourline.settings.ExpertFeature
import dev.josueamaya.pourline.settings.ModeStore
import dev.josueamaya.pourline.settings.TargetSettings
import dev.josueamaya.pourline.settings.ThemeMode
import dev.josueamaya.pourline.settings.ThemeSettings
import dev.josueamaya.pourline.ui.components.AntButton
import dev.josueamaya.pourline.ui.components.AntCard
import dev.josueamaya.pourline.ui.components.AntIconButton
import dev.josueamaya.pourline.ui.components.AntIcons
import dev.josueamaya.pourline.ui.components.AntListItem
import dev.josueamaya.pourline.ui.components.AntMessageHost
import dev.josueamaya.pourline.ui.components.AntModal
import dev.josueamaya.pourline.ui.components.AntSegmented
import dev.josueamaya.pourline.ui.components.AntSegmentedOption
import dev.josueamaya.pourline.ui.components.AntStatus
import dev.josueamaya.pourline.ui.components.AntSwitch
import dev.josueamaya.pourline.ui.components.AntText
import dev.josueamaya.pourline.ui.components.AntTextType
import dev.josueamaya.pourline.ui.components.AntTopBar
import dev.josueamaya.pourline.ui.components.showAntMessage
import dev.josueamaya.pourline.ui.theme.AntTheme
import dev.josueamaya.pourline.ui.tour.TourHost
import dev.josueamaya.pourline.ui.tour.Tours
import dev.josueamaya.pourline.ui.tour.tourAnchor
import kotlinx.coroutines.launch

/**
 * Recibe los ajustes por parámetro (sin `CompositionLocal`) para poder probarla aislada.
 * [onRestoreRecipes] devuelve las recetas predefinidas a fábrica, tras confirmar; [onOpenGrinders] abre Molinos;
 * [onReplayTour] borra los recorridos vistos y vuelve a Preparar. Con [onBack] `null` (es una
 * pestaña) no hay flecha de volver.
 */
@Composable
fun SettingsScreen(
  settings: BrewSettings,
  targetSettings: TargetSettings,
  experienceSettings: ModeStore,
  themeSettings: ThemeSettings,
  grinderRepository: GrinderRepository,
  grinderDefaults: DefaultGrinderStore,
  onRestoreRecipes: suspend () -> Unit,
  onOpenGrinders: () -> Unit,
  onReplayTour: () -> Unit,
  onBack: (() -> Unit)?,
) {
  val autoFinish by settings.autoFinishSeconds.collectAsStateWithLifecycle()
  val countdown by settings.countdownSeconds.collectAsStateWithLifecycle()
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
      GrindersRow(grinderRepository, grinderDefaults, onOpenGrinders)
      AntCard(Modifier.fillMaxWidth(), title = stringResource(R.string.mode_title)) {
        AntSegmented(
          options = listOf(
            AntSegmentedOption(ExperienceMode.BASIC, stringResource(R.string.mode_basic)),
            AntSegmentedOption(ExperienceMode.EXPERT, stringResource(R.string.mode_expert)),
          ),
          selected = experience,
          onSelect = experienceSettings::setMode,
          modifier = Modifier.fillMaxWidth().tourAnchor("settings_mode").testTag("settings_mode"),
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
          modifier = Modifier.fillMaxWidth().tourAnchor("settings_theme").testTag("settings_theme"),
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
      AntCard(Modifier.fillMaxWidth(), title = stringResource(R.string.settings_countdown_title)) {
        AntSegmented(
          options = BrewSettings.COUNTDOWN_ALLOWED.map {
            AntSegmentedOption(
              it,
              if (it == 0) stringResource(R.string.settings_countdown_off) else stringResource(R.string.settings_countdown_value, it),
            )
          },
          selected = countdown,
          onSelect = settings::setCountdownSeconds,
          modifier = Modifier.fillMaxWidth().tourAnchor("settings_countdown").testTag("settings_countdown"),
        )
        AntText(stringResource(R.string.settings_countdown_hint), type = AntTextType.Secondary)
      }
      AntCard(Modifier.fillMaxWidth(), title = stringResource(R.string.settings_stage_title)) {
        SwitchRow(
          label = stringResource(R.string.settings_stage_vibration),
          checked = vibration,
          onCheckedChange = targetSettings::setStageVibration,
          tag = "settings_stage_vibration",
          anchor = true,
        )
        SwitchRow(
          label = stringResource(R.string.settings_stage_sound),
          checked = sound,
          onCheckedChange = targetSettings::setStageSound,
          tag = "settings_stage_sound",
        )
      }
      ReplayTourRow(onReplayTour)
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

  TourHost(Tours.settings)

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
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, tag: String, anchor: Boolean = false) {
  Row(
    // El hueco del recorrido cubre la fila entera (texto e interruptor), no solo el interruptor.
    Modifier.fillMaxWidth().tourAnchor(tag, enabled = anchor),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(AntTheme.spacing.sm),
  ) {
    AntText(label, Modifier.weight(1f))
    AntSwitch(checked, onCheckedChange, Modifier.testTag(tag))
  }
}

/** La fila "Ver recorrido": vuelve a mostrar los recorridos de todas las pantallas. */
@Composable
private fun ReplayTourRow(onReplay: () -> Unit) {
  AntCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
    AntListItem(
      title = stringResource(R.string.settings_tour_replay),
      description = stringResource(R.string.settings_tour_replay_hint),
      modifier = Modifier.tourAnchor("settings_tour_replay").testTag("settings_tour_replay"),
      extra = { Icon(AntIcons.Right, contentDescription = null, modifier = Modifier.size(16.dp), tint = AntTheme.colors.textTertiary) },
      divider = false,
      onClick = onReplay,
    )
  }
}

/** La fila "Molinos": el predeterminado y cuántos molinos hay (D7, F0.3). */
@Composable
private fun GrindersRow(repository: GrinderRepository, defaults: DefaultGrinderStore, onOpen: () -> Unit) {
  val owned by repository.owned().collectAsStateWithLifecycle(initialValue = null)
  val defaultId by defaults.defaultGrinderId.collectAsStateWithLifecycle()
  val list = owned
  val count = list?.size ?: 0
  val default = list?.firstOrNull { it.id == defaultId }
  val description = when {
    list == null -> null
    count == 0 -> stringResource(R.string.settings_grinders_none)
    default != null -> stringResource(R.string.settings_grinders_default, default.model, pluralStringResource(R.plurals.settings_grinders_count, count, count))
    else -> pluralStringResource(R.plurals.settings_grinders_count, count, count)
  }
  AntCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
    AntListItem(
      title = stringResource(R.string.settings_grinders),
      description = description,
      modifier = Modifier.tourAnchor("settings_grinders").testTag("settings_grinders"),
      avatar = { Icon(AntIcons.Grinder, contentDescription = null, modifier = Modifier.size(24.dp), tint = AntTheme.colors.textSecondary) },
      extra = { Icon(AntIcons.Right, contentDescription = null, modifier = Modifier.size(16.dp), tint = AntTheme.colors.textTertiary) },
      divider = false,
      onClick = onOpen,
    )
  }
}
