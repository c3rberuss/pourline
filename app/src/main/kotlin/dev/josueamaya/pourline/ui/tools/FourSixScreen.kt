package dev.josueamaya.pourline.ui.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.josueamaya.pourline.R
import dev.josueamaya.pourline.tools.FourSixParams
import dev.josueamaya.pourline.tools.FourSixPlan
import dev.josueamaya.pourline.tools.FourSixStrength
import dev.josueamaya.pourline.tools.FourSixTaste
import dev.josueamaya.pourline.tools.RatioCalculator
import dev.josueamaya.pourline.tools.labelRes
import dev.josueamaya.pourline.ui.components.AntButton
import dev.josueamaya.pourline.ui.components.AntButtonType
import dev.josueamaya.pourline.ui.components.AntCard
import dev.josueamaya.pourline.ui.components.AntCheckableTag
import dev.josueamaya.pourline.ui.components.AntIconButton
import dev.josueamaya.pourline.ui.components.AntIcons
import dev.josueamaya.pourline.ui.components.AntInput
import dev.josueamaya.pourline.ui.components.AntInputStatus
import dev.josueamaya.pourline.ui.components.AntListItem
import dev.josueamaya.pourline.ui.components.AntStatistic
import dev.josueamaya.pourline.ui.components.AntText
import dev.josueamaya.pourline.ui.components.AntTextType
import dev.josueamaya.pourline.ui.components.AntTopBar
import dev.josueamaya.pourline.ui.recipes.formatStart
import dev.josueamaya.pourline.ui.theme.AntSize
import dev.josueamaya.pourline.ui.theme.AntTheme

/**
 * El generador 4:6: café y ratio, sabor (dulce ↔ ácido) e intensidad (diluido ↔ cargado). Muestra cada vertido con
 * su inicio, sus gramos y el acumulado, y [onSaveAsRecipe] abre el editor de recetas ya relleno.
 */
@Composable
fun FourSixScreen(viewModel: FourSixViewModel, onBack: () -> Unit, onSaveAsRecipe: (FourSixParams) -> Unit) {
  val plan = viewModel.plan
  Scaffold(
    containerColor = AntTheme.colors.bgLayout,
    topBar = {
      AntTopBar(
        title = stringResource(R.string.four_six_title),
        navigationIcon = {
          AntIconButton(AntIcons.ArrowLeft, contentDescription = stringResource(R.string.scale_back), onClick = onBack)
        },
      )
    },
  ) { padding ->
    Column(
      Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(AntTheme.spacing.base).testTag("four_six"),
      verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.sm),
    ) {
      AmountsCard(viewModel, plan)
      ChoiceCard(
        title = R.string.four_six_taste,
        help = R.string.four_six_taste_help,
        options = FourSixTaste.entries,
        selected = viewModel.taste,
        label = { it.labelRes },
        tag = { "four_six_taste_${it.name.lowercase()}" },
        onSelect = { viewModel.taste = it },
      )
      ChoiceCard(
        title = R.string.four_six_strength,
        help = R.string.four_six_strength_help,
        options = FourSixStrength.entries,
        selected = viewModel.strength,
        label = { it.labelRes },
        tag = { "four_six_strength_${it.name.lowercase()}" },
        onSelect = { viewModel.strength = it },
      )
      if (plan != null) {
        PlanCard(plan)
        AntText(stringResource(R.string.four_six_note), Modifier.fillMaxWidth(), type = AntTextType.Secondary, style = AntTheme.typography.textSM)
      }
      AntButton(
        text = stringResource(R.string.four_six_save),
        onClick = { plan?.let { onSaveAsRecipe(it.params) } },
        modifier = Modifier.fillMaxWidth().testTag("four_six_save"),
        type = AntButtonType.Primary,
        size = AntSize.Large,
        enabled = plan != null,
        icon = AntIcons.Recipe,
      )
    }
  }
}

@Composable
private fun AmountsCard(viewModel: FourSixViewModel, plan: FourSixPlan?) {
  AntCard(Modifier.fillMaxWidth()) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AntTheme.spacing.sm)) {
      Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.xxs)) {
        AntText(stringResource(R.string.ratio_coffee), type = AntTextType.Secondary, style = AntTheme.typography.textSM)
        AntInput(
          value = viewModel.dose,
          onValueChange = { viewModel.dose = it },
          modifier = Modifier.fillMaxWidth().testTag("four_six_dose"),
          suffix = { AntText(stringResource(R.string.prepare_grams), type = AntTextType.Secondary) },
          status = if (viewModel.error == null) AntInputStatus.Default else AntInputStatus.Error,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
      }
      Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.xxs)) {
        AntText(stringResource(R.string.ratio_ratio), type = AntTextType.Secondary, style = AntTheme.typography.textSM)
        AntInput(
          value = viewModel.ratio,
          onValueChange = { viewModel.ratio = it },
          modifier = Modifier.fillMaxWidth().testTag("four_six_ratio"),
          prefix = { AntText(stringResource(R.string.prepare_ratio_prefix), type = AntTextType.Secondary) },
          status = if (viewModel.error == null) AntInputStatus.Default else AntInputStatus.Error,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
      }
      AntStatistic(
        value = plan?.let { RatioCalculator.format(it.totalWaterG) } ?: stringResource(R.string.prepare_empty_value),
        modifier = Modifier.weight(1f).testTag("four_six_water"),
        title = stringResource(R.string.ratio_water),
        suffix = plan?.let { stringResource(R.string.prepare_grams) },
      )
    }
    viewModel.error?.let { error ->
      AntText(
        stringResource(if (error == FourSixInputError.WATER) R.string.four_six_water_error else R.string.four_six_range_error),
        Modifier.testTag("four_six_error"),
        type = AntTextType.Danger,
        style = AntTheme.typography.textSM,
      )
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChoiceCard(
  title: Int,
  help: Int,
  options: List<T>,
  selected: T,
  label: (T) -> Int,
  tag: (T) -> String,
  onSelect: (T) -> Unit,
) {
  AntCard(Modifier.fillMaxWidth(), title = stringResource(title)) {
    AntText(stringResource(help), type = AntTextType.Secondary, style = AntTheme.typography.textSM)
    // Sin hueco vertical: cada chip ya ocupa su área táctil de 48 dp.
    FlowRow(horizontalArrangement = Arrangement.spacedBy(AntTheme.spacing.xs)) {
      options.forEach { option ->
        AntCheckableTag(
          text = stringResource(label(option)),
          checked = option == selected,
          onCheckedChange = { onSelect(option) },
          modifier = Modifier.testTag(tag(option)),
        )
      }
    }
  }
}

@Composable
private fun PlanCard(plan: FourSixPlan) {
  val pourNames = stringArrayResource(R.array.four_six_pour_names)
  AntCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp), verticalArrangement = Arrangement.Top) {
    Row(
      Modifier.fillMaxWidth().padding(AntTheme.spacing.base),
      horizontalArrangement = Arrangement.spacedBy(AntTheme.spacing.sm),
    ) {
      AntStatistic(
        value = plan.pours.size.toString(),
        modifier = Modifier.weight(1f).testTag("four_six_pour_count"),
        title = stringResource(R.string.four_six_pours),
      )
      AntStatistic(
        value = formatStart(plan.totalTimeMs),
        modifier = Modifier.weight(1f).testTag("four_six_time"),
        title = stringResource(R.string.four_six_time),
      )
      AntStatistic(
        value = RatioCalculator.format(plan.totalWaterG),
        modifier = Modifier.weight(1f),
        title = stringResource(R.string.ratio_water),
        suffix = stringResource(R.string.prepare_grams),
      )
    }
    plan.pours.forEachIndexed { i, pour ->
      AntListItem(
        title = pourNames.getOrNull(i) ?: stringResource(R.string.four_six_pour_name_n, i + 1),
        description = stringResource(R.string.four_six_pour_detail, formatStart(pour.startMs), RatioCalculator.format(pour.totalG)),
        modifier = Modifier.testTag("four_six_pour_$i"),
        extra = {
          AntText(
            stringResource(R.string.four_six_pour_amount, RatioCalculator.format(pour.pourG)),
            Modifier.testTag("four_six_pour_amount_$i"),
            strong = true,
          )
        },
      )
    }
    AntListItem(
      title = stringResource(R.string.four_six_end),
      modifier = Modifier.testTag("four_six_end"),
      extra = { AntText(formatStart(plan.totalTimeMs), type = AntTextType.Secondary) },
      divider = false,
    )
  }
}
