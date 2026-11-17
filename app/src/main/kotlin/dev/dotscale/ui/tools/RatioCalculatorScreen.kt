package dev.dotscale.ui.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import dev.dotscale.R
import dev.dotscale.tools.RatioCalculatorState
import dev.dotscale.tools.RatioField
import dev.dotscale.ui.components.AntCard
import dev.dotscale.ui.components.AntIconButton
import dev.dotscale.ui.components.AntIcons
import dev.dotscale.ui.components.AntInput
import dev.dotscale.ui.components.AntInputStatus
import dev.dotscale.ui.components.AntText
import dev.dotscale.ui.components.AntTextType
import dev.dotscale.ui.components.AntTopBar
import dev.dotscale.ui.theme.AntSize
import dev.dotscale.ui.theme.AntTheme

@Composable
fun RatioCalculatorScreen(viewModel: RatioCalculatorViewModel, onBack: () -> Unit) {
  val state = viewModel.state
  Scaffold(
    containerColor = AntTheme.colors.bgLayout,
    topBar = {
      AntTopBar(
        title = stringResource(R.string.tools_ratio_title),
        navigationIcon = {
          AntIconButton(AntIcons.ArrowLeft, contentDescription = stringResource(R.string.scale_back), onClick = onBack)
        },
      )
    },
  ) { padding ->
    Column(
      Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(AntTheme.spacing.base).testTag("ratio_calculator"),
      verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.sm),
    ) {
      AntCard(Modifier.fillMaxWidth()) {
        RatioInput(state, RatioField.DOSE, R.string.ratio_coffee, viewModel::edit, suffix = R.string.prepare_grams)
        RatioInput(state, RatioField.RATIO, R.string.ratio_ratio, viewModel::edit, prefix = R.string.prepare_ratio_prefix)
        RatioInput(state, RatioField.WATER, R.string.ratio_water, viewModel::edit, suffix = R.string.prepare_grams, last = true)
      }
      AntText(stringResource(R.string.ratio_hint), Modifier.fillMaxWidth(), type = AntTextType.Secondary)
      if (state.invalid.isNotEmpty()) {
        AntText(
          stringResource(R.string.ratio_error),
          Modifier.fillMaxWidth().testTag("ratio_error"),
          type = AntTextType.Danger,
          style = AntTheme.typography.textSM,
        )
      }
    }
  }
}

@Composable
private fun RatioInput(
  state: RatioCalculatorState,
  field: RatioField,
  label: Int,
  onEdit: (RatioField, String) -> Unit,
  prefix: Int? = null,
  suffix: Int? = null,
  last: Boolean = false,
) {
  Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.xxs)) {
    AntText(stringResource(label), type = AntTextType.Secondary, style = AntTheme.typography.textSM)
    AntInput(
      value = state.text(field),
      onValueChange = { onEdit(field, it) },
      modifier = Modifier.fillMaxWidth().testTag("ratio_${field.name.lowercase()}"),
      prefix = prefix?.let { { AntText(stringResource(it), type = AntTextType.Secondary) } },
      suffix = suffix?.let { { AntText(stringResource(it), type = AntTextType.Secondary) } },
      size = AntSize.Large,
      status = if (field in state.invalid) AntInputStatus.Error else AntInputStatus.Default,
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = if (last) ImeAction.Done else ImeAction.Next),
    )
  }
}
