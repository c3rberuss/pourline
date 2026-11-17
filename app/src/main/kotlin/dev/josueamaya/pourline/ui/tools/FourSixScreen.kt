package dev.josueamaya.pourline.ui.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.josueamaya.pourline.R
import dev.josueamaya.pourline.tools.FourSix
import dev.josueamaya.pourline.tools.FourSixParams
import dev.josueamaya.pourline.tools.FourSixPhase
import dev.josueamaya.pourline.tools.FourSixPlan
import dev.josueamaya.pourline.tools.FourSixStrength
import dev.josueamaya.pourline.tools.FourSixTaste
import dev.josueamaya.pourline.tools.RatioCalculator
import dev.josueamaya.pourline.tools.labelRes
import dev.josueamaya.pourline.ui.components.AntButton
import dev.josueamaya.pourline.ui.components.AntButtonType
import dev.josueamaya.pourline.ui.components.AntCard
import dev.josueamaya.pourline.ui.components.AntIconButton
import dev.josueamaya.pourline.ui.components.AntIcons
import dev.josueamaya.pourline.ui.components.AntInput
import dev.josueamaya.pourline.ui.components.AntInputStatus
import dev.josueamaya.pourline.ui.components.AntListItem
import dev.josueamaya.pourline.ui.components.AntSlider
import dev.josueamaya.pourline.ui.components.AntStatistic
import dev.josueamaya.pourline.ui.components.AntText
import dev.josueamaya.pourline.ui.components.AntTextType
import dev.josueamaya.pourline.ui.components.AntTopBar
import dev.josueamaya.pourline.ui.recipes.formatStart
import dev.josueamaya.pourline.ui.theme.AntSize
import dev.josueamaya.pourline.ui.theme.AntTheme

/**
 * El generador 4:6: café y ratio, sabor (dulce ↔ brillante) e intensidad (suave ↔ intensa). Muestra cada vertido con
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
      ProfileCard(viewModel, plan)
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

/** Sabor e intensidad: un slider de cinco niveles cada uno y, debajo, cómo queda repartida el agua en vertidos. */
@Composable
private fun ProfileCard(viewModel: FourSixViewModel, plan: FourSixPlan?) {
  AntCard(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.base)) {
    val taste = viewModel.taste
    val tasteLabel = stringResource(taste.labelRes)
    LevelSlider(
      title = stringResource(R.string.four_six_taste),
      value = plan?.let {
        stringResource(
          R.string.four_six_level_value,
          tasteLabel,
          stringResource(R.string.four_six_taste_split, RatioCalculator.format(it.pours[0].pourG), RatioCalculator.format(it.pours[1].pourG)),
        )
      } ?: tasteLabel,
      stateDescription = tasteLabel,
      start = stringResource(R.string.four_six_taste_start),
      end = stringResource(R.string.four_six_taste_end),
      selected = taste.ordinal,
      count = FourSixTaste.entries.size,
      onSelect = { viewModel.taste = FourSixTaste.entries[it] },
      tag = "four_six_taste",
    )
    val strength = viewModel.strength
    val strengthLabel = stringResource(strength.labelRes)
    LevelSlider(
      title = stringResource(R.string.four_six_strength),
      value = stringResource(
        R.string.four_six_level_value,
        strengthLabel,
        pluralStringResource(R.plurals.four_six_strength_pours, strength.pours, strength.pours),
      ),
      stateDescription = strengthLabel,
      start = stringResource(R.string.four_six_strength_start),
      end = stringResource(R.string.four_six_strength_end),
      selected = strength.ordinal,
      count = FourSixStrength.entries.size,
      onSelect = { viewModel.strength = FourSixStrength.entries[it] },
      tag = "four_six_strength",
    )
    plan?.let { PourSplit(it) }
  }
}

@Composable
private fun LevelSlider(
  title: String,
  value: String,
  stateDescription: String,
  start: String,
  end: String,
  selected: Int,
  count: Int,
  onSelect: (Int) -> Unit,
  tag: String,
) {
  Column {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AntTheme.spacing.xs), verticalAlignment = Alignment.CenterVertically) {
      AntText(title, Modifier.weight(1f), strong = true)
      AntText(value, Modifier.testTag("${tag}_value"), type = AntTextType.Secondary)
    }
    AntSlider(
      value = selected,
      onValueChange = onSelect,
      count = count,
      modifier = Modifier.fillMaxWidth().testTag(tag),
      stateDescription = stateDescription,
    )
    Row(Modifier.fillMaxWidth().clearAndSetSemantics {}, horizontalArrangement = Arrangement.SpaceBetween) {
      AntText(start, type = AntTextType.Secondary, style = AntTheme.typography.textSM)
      AntText(end, type = AntTextType.Secondary, style = AntTheme.typography.textSM)
    }
  }
}

/**
 * Una barra por vertido, de ancho proporcional a sus gramos: claras las dos del 40 % (sabor) y sólidas las del 60 %
 * (intensidad). Repite lo que ya dice la lista de vertidos, así que TalkBack la salta.
 */
@Composable
private fun PourSplit(plan: FourSixPlan) {
  val colors = AntTheme.colors
  val shape = RoundedCornerShape(AntTheme.radius.sm)
  Column(Modifier.fillMaxWidth().clearAndSetSemantics {}, verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.xxs)) {
    Row(Modifier.fillMaxWidth().height(28.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
      plan.pours.forEach { pour ->
        val taste = pour.phase == FourSixPhase.TASTE
        Box(
          Modifier
            .weight(pour.pourG.toFloat())
            .fillMaxHeight()
            .background(if (taste) colors.primary.bg else colors.primary.solid, shape)
            .then(if (taste) Modifier.border(1.dp, colors.primary.border, shape) else Modifier),
          contentAlignment = Alignment.Center,
        ) {
          Text(
            RatioCalculator.format(pour.pourG),
            color = if (taste) colors.primary.text else colors.textLightSolid,
            style = AntTheme.typography.textSM,
            maxLines = 1,
            softWrap = false,
          )
        }
      }
    }
    Row(Modifier.fillMaxWidth()) {
      AntText(
        stringResource(R.string.four_six_split_taste),
        Modifier.weight(FourSix.TASTE_SHARE.toFloat()),
        type = AntTextType.Secondary,
        style = AntTheme.typography.textSM,
      )
      AntText(
        stringResource(R.string.four_six_split_strength),
        Modifier.weight(1 - FourSix.TASTE_SHARE.toFloat()),
        type = AntTextType.Secondary,
        style = AntTheme.typography.textSM,
      )
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
