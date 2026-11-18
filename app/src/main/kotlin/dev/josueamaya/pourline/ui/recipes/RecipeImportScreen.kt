package dev.josueamaya.pourline.ui.recipes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.josueamaya.pourline.R
import dev.josueamaya.pourline.grinder.GrindFormat
import dev.josueamaya.pourline.grinder.GrindPick
import dev.josueamaya.pourline.grinder.GrindResolution
import dev.josueamaya.pourline.grinder.GrindTexts
import dev.josueamaya.pourline.grinder.Grinder
import dev.josueamaya.pourline.grinder.ResourcesGrindTexts
import dev.josueamaya.pourline.protocol.WeightUnit
import dev.josueamaya.pourline.recipe.Recipe
import dev.josueamaya.pourline.recipe.RecipeStage
import dev.josueamaya.pourline.recipe.StageType
import dev.josueamaya.pourline.share.recipeMethodText
import dev.josueamaya.pourline.share.weightNumber
import dev.josueamaya.pourline.ui.components.AntAlert
import dev.josueamaya.pourline.ui.components.AntButton
import dev.josueamaya.pourline.ui.components.AntButtonType
import dev.josueamaya.pourline.ui.components.AntIconButton
import dev.josueamaya.pourline.ui.components.AntIcons
import dev.josueamaya.pourline.ui.components.AntSelect
import dev.josueamaya.pourline.ui.components.AntSelectOption
import dev.josueamaya.pourline.ui.components.AntSpin
import dev.josueamaya.pourline.ui.components.AntStatus
import dev.josueamaya.pourline.ui.components.AntText
import dev.josueamaya.pourline.ui.components.AntTextType
import dev.josueamaya.pourline.ui.components.AntTopBar
import dev.josueamaya.pourline.ui.session.formatClock
import dev.josueamaya.pourline.ui.session.stepTypeIcon
import dev.josueamaya.pourline.ui.session.stepTypeLabel
import dev.josueamaya.pourline.ui.theme.AntTheme
import kotlin.math.roundToLong

private val StepStartWidth = 36.dp
private val StepIconSize = 16.dp
private val StepTypeWidth = 64.dp
private val StepDurationWidth = 40.dp
private val StepWaterWidth = 56.dp

/**
 * La vista previa de una receta importada (SPEC-009 §3.6), de solo lectura: los avisos, los datos, la molienda, las
 * notas y los pasos con su instrucción. [onDone] cierra la pantalla (guardar, cancelar o un URI que ya no se lee).
 */
@Composable
fun RecipeImportScreen(viewModel: RecipeImportViewModel, onDone: () -> Unit, unit: WeightUnit = WeightUnit.GRAMS) {
  val ui by viewModel.ui.collectAsStateWithLifecycle()
  val current = ui
  LaunchedEffect(current) {
    if (current is RecipeImportUi.Saved || current is RecipeImportUi.Failed) onDone()
  }
  val context = LocalContext.current
  val texts = remember(context) { ResourcesGrindTexts(context) }

  Scaffold(
    containerColor = AntTheme.colors.bgLayout,
    topBar = {
      AntTopBar(
        title = stringResource(R.string.recipe_import_title),
        navigationIcon = {
          AntIconButton(AntIcons.ArrowLeft, contentDescription = stringResource(R.string.scale_back), onClick = onDone)
        },
      )
    },
  ) { padding ->
    val ready = current as? RecipeImportUi.Ready
    if (ready == null) {
      AntSpin(Modifier.padding(padding).padding(AntTheme.spacing.xl))
      return@Scaffold
    }
    Column(
      Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(AntTheme.spacing.base),
      verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.base),
    ) {
      if (ready.shared.trimmed.isNotEmpty()) {
        AntAlert(
          message = stringResource(R.string.recipe_import_trimmed, trimmedPartsText(ready.shared.trimmed)),
          type = AntStatus.Warning,
          modifier = Modifier.testTag("recipe_import_trimmed"),
        )
      }
      if (ready.identical) {
        AntAlert(
          message = stringResource(R.string.recipe_import_identical),
          type = AntStatus.Info,
          modifier = Modifier.testTag("recipe_import_identical"),
        )
      }
      RecipeSummary(ready.shared.recipe, unit)
      GrindSection(ready, texts, onPick = viewModel::pick)
      ready.shared.recipe.notes?.let { notes ->
        Column(verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.xxs)) {
          AntText(stringResource(R.string.recipe_import_notes), type = AntTextType.Secondary, style = AntTheme.typography.textSM)
          AntText(notes, Modifier.testTag("recipe_import_notes"))
        }
      }
      AntText(stringResource(R.string.recipe_edit_stages), style = AntTheme.typography.heading5)
      Column(verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.xs)) {
        ready.shared.recipe.stages.forEachIndexed { index, stage -> StageRow(stage, index, unit) }
      }
      AntButton(
        text = stringResource(R.string.recipe_import_save),
        onClick = viewModel::save,
        modifier = Modifier.fillMaxWidth().testTag("recipe_import_save"),
        type = AntButtonType.Primary,
        loading = ready.saving,
        enabled = !ready.saving,
      )
      AntButton(
        text = stringResource(R.string.recipe_import_cancel),
        onClick = onDone,
        modifier = Modifier.fillMaxWidth().testTag("recipe_import_cancel"),
      )
    }
  }
}

@Composable
private fun RecipeSummary(recipe: Recipe, unit: WeightUnit) {
  Column(verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.xxs)) {
    AntText(recipe.name, Modifier.testTag("recipe_import_name"), style = AntTheme.typography.heading4)
    recipeMethodText(recipe.method, recipe.methodOther)?.let {
      AntText(it, Modifier.testTag("recipe_import_method"), type = AntTextType.Secondary)
    }
    val facts = buildList {
      add(stringResource(R.string.recipe_card_dose, weightNumber(recipe.doseG, unit), unit.symbol))
      add(stringResource(R.string.recipe_card_water, weightNumber(recipe.totalWaterG, unit), unit.symbol))
      add(stringResource(R.string.recipe_card_ratio, formatRatioNumber(recipe.ratio)))
      recipe.waterTempC?.let { add(stringResource(R.string.recipe_card_temp, formatGrams(it))) }
    }
    AntText(facts.joinToString(" · "), Modifier.testTag("recipe_import_facts"))
  }
}

/** `Linked` y `Text` son texto fijo; `Convertible` un selector con "Usar con…" por molino y "Guardar como texto". */
@Composable
private fun GrindSection(ready: RecipeImportUi.Ready, texts: GrindTexts, onPick: (GrindPick) -> Unit) {
  val label = stringResource(R.string.recipe_import_grind)
  when (val grind = ready.grind) {
    GrindResolution.None -> Unit
    is GrindResolution.Linked -> FixedGrind(label, linkedText(grind.grinder, grind.value, texts))
    is GrindResolution.Text -> FixedGrind(label, grind.label)
    is GrindResolution.Convertible -> {
      val options = grind.options.map { option ->
        val setting = (if (option.rounded) "≈ " else "") + GrindFormat.format(option.value, option.grinder, texts)
        AntSelectOption<GrindPick>(
          GrindPick.Use(option.grinder.id),
          stringResource(R.string.recipe_import_grind_use, "${option.grinder.brand} ${option.grinder.model}", setting),
        )
      } + AntSelectOption<GrindPick>(GrindPick.AsText, stringResource(R.string.recipe_import_grind_as_text))
      AntSelect(
        options = options,
        selected = ready.pick,
        onSelect = onPick,
        label = label,
        modifier = Modifier.fillMaxWidth(),
        testTag = "recipe_import_grind",
      )
    }
  }
}

@Composable
private fun FixedGrind(label: String, text: String) {
  Column(verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.xxs)) {
    AntText(label, type = AntTextType.Secondary, style = AntTheme.typography.textSM)
    AntText(text, Modifier.testTag("recipe_import_grind_text"))
  }
}

private fun linkedText(grinder: Grinder, value: Double?, texts: GrindTexts): String {
  val name = "${grinder.brand} ${grinder.model}"
  return if (value == null) name else name + GrindFormat.SEPARATOR + GrindFormat.format(value, grinder, texts)
}

/** Inicio, tipo, nombre, duración y agua acumulada (solo en `POUR`), y debajo la instrucción si la hay. */
@Composable
private fun StageRow(stage: RecipeStage, index: Int, unit: WeightUnit) {
  Column(Modifier.fillMaxWidth().testTag("recipe_import_step_$index"), verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.xxs)) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AntTheme.spacing.xs), verticalAlignment = Alignment.CenterVertically) {
      AntText(formatClock(stage.startMs), Modifier.width(StepStartWidth), type = AntTextType.Secondary, maxLines = 1)
      Icon(stepTypeIcon(stage.type), contentDescription = null, modifier = Modifier.size(StepIconSize), tint = AntTheme.colors.textSecondary)
      AntText(stringResource(stepTypeLabel(stage.type)), Modifier.width(StepTypeWidth), maxLines = 1, overflow = TextOverflow.Ellipsis)
      AntText(stage.name.orEmpty(), Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
      AntText(
        stringResource(R.string.recipe_card_step_duration, (stage.durationMs / 1000.0).roundToLong().toInt()),
        Modifier.width(StepDurationWidth),
        type = AntTextType.Secondary,
        textAlign = TextAlign.End,
        maxLines = 1,
      )
      AntText(
        if (stage.type == StageType.POUR) "${weightNumber(stage.targetWaterG, unit)} ${unit.symbol}" else "",
        Modifier.width(StepWaterWidth),
        textAlign = TextAlign.End,
        maxLines = 1,
      )
    }
    stage.instruction?.let {
      AntText(it, Modifier.padding(start = StepStartWidth + StepIconSize + AntTheme.spacing.xs * 2).testTag("recipe_import_step_instruction_$index"), type = AntTextType.Secondary, style = AntTheme.typography.textSM)
    }
  }
}
