package dev.dotscale.ui.history

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.dotscale.R
import dev.dotscale.history.BrewMethod
import dev.dotscale.ui.components.AntAutoComplete
import dev.dotscale.ui.components.AntButton
import dev.dotscale.ui.components.AntButtonType
import dev.dotscale.ui.components.AntIconButton
import dev.dotscale.ui.components.AntIcons
import dev.dotscale.ui.components.AntInput
import dev.dotscale.ui.components.AntInputStatus
import dev.dotscale.ui.components.AntModal
import dev.dotscale.ui.components.AntSelect
import dev.dotscale.ui.components.AntSelectOption
import dev.dotscale.ui.components.AntSpin
import dev.dotscale.ui.components.AntText
import dev.dotscale.ui.components.AntTextType
import dev.dotscale.ui.components.AntTopBar
import dev.dotscale.ui.theme.AntTheme

@Composable
fun BrewEditScreen(viewModel: BrewEditViewModel, onBack: () -> Unit) {
  val status by viewModel.status.collectAsStateWithLifecycle()
  val form by viewModel.form.collectAsStateWithLifecycle()
  val initial by viewModel.initial.collectAsStateWithLifecycle()
  val coffees by viewModel.coffees.collectAsStateWithLifecycle()
  val startedAt by viewModel.startedAtEpochMs.collectAsStateWithLifecycle()
  var confirmDiscard by rememberSaveable { mutableStateOf(false) }

  // `Saved` y `Gone` (la fila se borró mientras se editaba) vuelven atrás.
  LaunchedEffect(status) { if (status == BrewEditStatus.Saved || status == BrewEditStatus.Gone) onBack() }

  val dirty = form.isDirty(initial) && status == BrewEditStatus.Ready
  val requestBack = { if (dirty) confirmDiscard = true else onBack() }
  BackHandler(enabled = dirty) { confirmDiscard = true }

  Scaffold(
    containerColor = AntTheme.colors.bgLayout,
    topBar = {
      AntTopBar(
        title = stringResource(R.string.brew_edit_title),
        navigationIcon = {
          AntIconButton(AntIcons.ArrowLeft, contentDescription = stringResource(R.string.scale_back), onClick = requestBack)
        },
      )
    },
  ) { padding ->
    if (status == BrewEditStatus.Loading) {
      AntSpin(Modifier.padding(padding).padding(AntTheme.spacing.xl))
      return@Scaffold
    }
    Column(
      Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(AntTheme.spacing.base),
      verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.base),
    ) {
      Field(stringResource(R.string.brew_edit_name)) {
        AntInput(
          value = form.name,
          onValueChange = { v -> viewModel.update { it.copy(name = v) } },
          modifier = Modifier.fillMaxWidth().testTag("brew_edit_name"),
          placeholder = formatBrewDateTime(startedAt),
        )
      }
      Field(stringResource(R.string.brew_edit_coffee)) {
        AntAutoComplete(
          value = form.coffee,
          onValueChange = { v -> viewModel.update { it.copy(coffee = v) } },
          suggestions = coffees,
          modifier = Modifier.fillMaxWidth().testTag("brew_edit_coffee"),
        )
      }
      val noMethod = stringResource(R.string.brew_edit_method_none)
      val options = listOf(AntSelectOption<BrewMethod?>(null, noMethod)) + BrewMethod.entries.map { AntSelectOption<BrewMethod?>(it, it.label()) }
      AntSelect(
        options = options,
        selected = form.method,
        onSelect = { m -> viewModel.update { it.copy(method = m) } },
        label = stringResource(R.string.brew_edit_method),
        modifier = Modifier.fillMaxWidth(),
        testTag = "brew_edit_method",
      )
      if (form.method == BrewMethod.OTHER) {
        AntInput(
          value = form.methodOther,
          onValueChange = { v -> viewModel.update { it.copy(methodOther = v) } },
          modifier = Modifier.fillMaxWidth().testTag("brew_edit_method_other"),
          placeholder = stringResource(R.string.brew_edit_method_other),
        )
      }
      Field(stringResource(R.string.brew_edit_grind)) {
        AntInput(
          value = form.grind,
          onValueChange = { v -> viewModel.update { it.copy(grind = v) } },
          modifier = Modifier.fillMaxWidth().testTag("brew_edit_grind"),
          placeholder = stringResource(R.string.brew_edit_grind_hint),
        )
      }
      Field(stringResource(R.string.brew_edit_temp)) {
        val invalid = !form.isValid
        AntInput(
          value = form.waterTemp,
          onValueChange = { v -> viewModel.update { it.copy(waterTemp = v) } },
          modifier = Modifier.fillMaxWidth().testTag("brew_edit_temp"),
          status = if (invalid) AntInputStatus.Error else AntInputStatus.Default,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        if (invalid) {
          AntText(stringResource(R.string.brew_edit_temp_error), Modifier.testTag("brew_edit_temp_error"), type = AntTextType.Danger)
        }
      }
      Field(stringResource(R.string.brew_edit_notes)) {
        AntInput(
          value = form.notes,
          onValueChange = { v -> viewModel.update { it.copy(notes = v) } },
          modifier = Modifier.fillMaxWidth().testTag("brew_edit_notes"),
          singleLine = false,
          minLines = 3,
        )
      }
      AntButton(
        text = stringResource(R.string.brew_edit_save),
        onClick = viewModel::save,
        modifier = Modifier.fillMaxWidth().testTag("brew_edit_save"),
        type = AntButtonType.Primary,
        enabled = form.isValid && status == BrewEditStatus.Ready,
        loading = status == BrewEditStatus.Saving,
      )
    }
  }

  if (confirmDiscard) {
    AntModal(
      title = stringResource(R.string.brew_edit_discard_title),
      text = stringResource(R.string.brew_edit_discard_text),
      confirmText = stringResource(R.string.brew_edit_discard),
      cancelText = stringResource(R.string.brew_edit_keep),
      onConfirm = {
        confirmDiscard = false
        onBack()
      },
      onDismiss = { confirmDiscard = false },
      danger = true,
    )
  }
}

@Composable
private fun Field(label: String, content: @Composable () -> Unit) {
  Column(verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.xxs)) {
    AntText(label, type = AntTextType.Secondary, style = AntTheme.typography.textSM)
    content()
  }
}
