package dev.josueamaya.pourline.ui.recipes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.josueamaya.pourline.R
import dev.josueamaya.pourline.grinder.GrindFormat
import dev.josueamaya.pourline.grinder.GrindPick
import dev.josueamaya.pourline.grinder.GrindResolution
import dev.josueamaya.pourline.grinder.GrindTexts
import dev.josueamaya.pourline.grinder.Grinder
import dev.josueamaya.pourline.protocol.WeightUnit
import dev.josueamaya.pourline.recipe.Recipe
import dev.josueamaya.pourline.recipe.RecipeStage
import dev.josueamaya.pourline.recipe.StageType
import dev.josueamaya.pourline.share.recipeMethodText
import dev.josueamaya.pourline.share.weightNumber
import dev.josueamaya.pourline.ui.components.AntIcons
import dev.josueamaya.pourline.ui.components.AntSelect
import dev.josueamaya.pourline.ui.components.AntSelectOption
import dev.josueamaya.pourline.ui.components.AntText
import dev.josueamaya.pourline.ui.components.AntTextType
import dev.josueamaya.pourline.ui.session.formatClock
import dev.josueamaya.pourline.ui.session.stepTypeIcon
import dev.josueamaya.pourline.ui.session.stepTypeLabel
import dev.josueamaya.pourline.ui.theme.AntTheme
import kotlin.math.roundToLong

/*
 * Las piezas de solo lectura que comparten la vista previa de una receta importada (SPEC-009) y el detalle de una
 * recomendada (SPEC-012, D42). [tagPrefix] da las etiquetas de prueba de cada pantalla: "recipe_import" o "catalog_detail".
 */

private val StepStartWidth = 44.dp
private val StepIconSize = 16.dp
private val StepTypeWidth = 64.dp
private val StepDurationWidth = 40.dp
private val StepWaterWidth = 56.dp

/** Las columnas llevan texto: crecen con la escala de letra (en `dp` fijos "0:10" se cortaba a 1,3). */
@Composable
private fun scaledWidth(base: Dp): Dp = with(LocalDensity.current) { base.value.sp.toDp() }

/** Nombre, método y "15 g · 250 g · 1:16,7 · 1:55 · 93 °C". */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RecipeSummary(recipe: Recipe, unit: WeightUnit, tagPrefix: String) {
  Column(verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.xxs)) {
    AntText(recipe.name, Modifier.testTag("${tagPrefix}_name"), style = AntTheme.typography.heading4)
    recipeMethodText(recipe.method, recipe.methodOther)?.let {
      AntText(it, Modifier.testTag("${tagPrefix}_method"), type = AntTextType.Secondary)
    }
    val dose = stringResource(R.string.recipe_card_dose, weightNumber(recipe.doseG, unit), unit.symbol)
    val water = stringResource(R.string.recipe_card_water, weightNumber(recipe.totalWaterG, unit), unit.symbol)
    val ratio = stringResource(R.string.recipe_card_ratio, formatRatioNumber(recipe.ratio))
    val temp = recipe.waterTempC?.let { stringResource(R.string.recipe_card_temp, formatGrams(it)) }
    val time = formatClock(recipe.totalDurationMs)
    val timeDescription = stringResource(R.string.recipe_total_time_description, time)
    // Cada dato lleva su "·" detrás, salvo el último, para que el separador no quede solo al principio de una línea.
    FlowRow(
      Modifier.testTag("${tagPrefix}_facts"),
      horizontalArrangement = Arrangement.spacedBy(AntTheme.spacing.xs),
      verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.xxs),
    ) {
      AntText("$dose ·")
      AntText("$water ·")
      AntText("$ratio ·")
      Row(horizontalArrangement = Arrangement.spacedBy(AntTheme.spacing.xxs), verticalAlignment = Alignment.CenterVertically) {
        Icon(AntIcons.Clock, contentDescription = null, modifier = Modifier.size(StepIconSize), tint = AntTheme.colors.textSecondary)
        AntText(time, Modifier.testTag("${tagPrefix}_duration").semantics { contentDescription = timeDescription })
        if (temp != null) AntText("·")
      }
      temp?.let { AntText(it) }
    }
  }
}

/**
 * `Linked` y `Text` son texto fijo; `Convertible` un selector con "Usar con…" por molino y "Guardar como texto". Con
 * [GrindResolution.None] no dibuja nada.
 */
@Composable
internal fun GrindSection(
  resolution: GrindResolution,
  pick: GrindPick?,
  onPick: (GrindPick) -> Unit,
  texts: GrindTexts,
  tagPrefix: String,
) {
  val label = stringResource(R.string.recipe_import_grind)
  when (resolution) {
    GrindResolution.None -> Unit
    is GrindResolution.Linked -> FixedGrind(label, linkedText(resolution.grinder, resolution.value, texts), "${tagPrefix}_grind_text")
    is GrindResolution.Text -> FixedGrind(label, resolution.label, "${tagPrefix}_grind_text")
    is GrindResolution.Convertible -> {
      val options = resolution.options.map { option ->
        val setting = (if (option.rounded) "≈ " else "") + GrindFormat.format(option.value, option.grinder, texts)
        AntSelectOption<GrindPick>(
          GrindPick.Use(option.grinder.id),
          stringResource(R.string.recipe_import_grind_use, "${option.grinder.brand} ${option.grinder.model}", setting),
        )
      } + AntSelectOption<GrindPick>(GrindPick.AsText, stringResource(R.string.recipe_import_grind_as_text))
      AntSelect(
        options = options,
        selected = pick,
        onSelect = onPick,
        label = label,
        modifier = Modifier.fillMaxWidth(),
        testTag = "${tagPrefix}_grind",
      )
    }
  }
}

@Composable
private fun FixedGrind(label: String, text: String, tag: String) {
  Column(verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.xxs)) {
    AntText(label, type = AntTextType.Secondary, style = AntTheme.typography.textSM)
    AntText(text, Modifier.testTag(tag))
  }
}

private fun linkedText(grinder: Grinder, value: Double?, texts: GrindTexts): String {
  val name = "${grinder.brand} ${grinder.model}"
  return if (value == null) name else name + GrindFormat.SEPARATOR + GrindFormat.format(value, grinder, texts)
}

/** Inicio, tipo, nombre, duración y agua acumulada (solo en `POUR`), y debajo la instrucción si la hay. */
@Composable
internal fun StageRow(stage: RecipeStage, index: Int, unit: WeightUnit, tagPrefix: String) {
  val startWidth = scaledWidth(StepStartWidth)
  Column(Modifier.fillMaxWidth().testTag("${tagPrefix}_step_$index"), verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.xxs)) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AntTheme.spacing.xs), verticalAlignment = Alignment.CenterVertically) {
      AntText(formatClock(stage.startMs), Modifier.width(startWidth), type = AntTextType.Secondary, maxLines = 1)
      Icon(stepTypeIcon(stage.type), contentDescription = null, modifier = Modifier.size(StepIconSize), tint = AntTheme.colors.textSecondary)
      AntText(stringResource(stepTypeLabel(stage.type)), Modifier.width(scaledWidth(StepTypeWidth)), maxLines = 1, overflow = TextOverflow.Ellipsis)
      AntText(stage.name.orEmpty(), Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
      AntText(
        stringResource(R.string.recipe_card_step_duration, (stage.durationMs / 1000.0).roundToLong().toInt()),
        Modifier.width(scaledWidth(StepDurationWidth)),
        type = AntTextType.Secondary,
        textAlign = TextAlign.End,
        maxLines = 1,
      )
      AntText(
        if (stage.type == StageType.POUR) "${weightNumber(stage.targetWaterG, unit)} ${unit.symbol}" else "",
        Modifier.width(scaledWidth(StepWaterWidth)),
        textAlign = TextAlign.End,
        maxLines = 1,
      )
    }
    stage.instruction?.let {
      AntText(
        it,
        Modifier.padding(start = startWidth + StepIconSize + AntTheme.spacing.xs * 2).testTag("${tagPrefix}_step_instruction_$index"),
        type = AntTextType.Secondary,
        style = AntTheme.typography.textSM,
      )
    }
  }
}
