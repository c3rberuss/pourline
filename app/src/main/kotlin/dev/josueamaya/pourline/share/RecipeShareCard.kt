package dev.josueamaya.pourline.share

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.josueamaya.pourline.R
import dev.josueamaya.pourline.brew.fromGrams
import dev.josueamaya.pourline.history.BrewMethod
import dev.josueamaya.pourline.protocol.WeightUnit
import dev.josueamaya.pourline.recipe.RecipeStage
import dev.josueamaya.pourline.recipe.StageType
import dev.josueamaya.pourline.ui.components.AntText
import dev.josueamaya.pourline.ui.components.AntTextType
import dev.josueamaya.pourline.ui.history.label
import dev.josueamaya.pourline.ui.recipes.formatGrams
import dev.josueamaya.pourline.ui.recipes.formatRatioNumber
import dev.josueamaya.pourline.ui.scale.formatNumber
import dev.josueamaya.pourline.ui.session.formatClock
import dev.josueamaya.pourline.ui.session.stepTypeIcon
import dev.josueamaya.pourline.ui.session.stepTypeLabel
import dev.josueamaya.pourline.ui.theme.AntTheme
import kotlin.math.roundToLong

private const val NAME_MAX_LINES = 2
private const val GRIND_MAX_LINES = 2
private const val CAPTION_MAX_LINES = 3
private val NameGap = 8.dp
private val InfoGap = 12.dp
private val ColumnGap = 12.dp
private val InfoLineGap = 2.dp
private val StepIconSize = 12.dp
private val StepStartWidth = 32.dp
private val StepTypeWidth = 44.dp
private val StepDurationWidth = 32.dp
private val StepWaterWidth = 44.dp
private val StepGap = 4.dp

/**
 * La tarjeta de una receta (SPEC-009 §3.3): nombre, método, dosis, agua, ratio, temperatura y molienda a un lado y el QR
 * al otro, y una fila de alto fijo por paso. Sin notas ni instrucciones. Mide 4:5 o 9:16 según [recipeCardSize]; sin
 * [RecipeShareData.qr] la columna de datos ocupa todo el ancho.
 */
@Composable
fun RecipeShareCard(data: RecipeShareData, modifier: Modifier = Modifier) {
  val recipe = data.recipe
  ShareFrame(modifier, size = recipeCardSize(recipe.stages.size)) {
    AntText(
      recipe.name,
      Modifier.fillMaxWidth().testTag("share_recipe_name"),
      style = AntTheme.typography.heading5,
      maxLines = NAME_MAX_LINES,
      overflow = TextOverflow.Ellipsis,
    )
    Spacer(Modifier.height(NameGap))
    Row(Modifier.fillMaxWidth().height(SHARE_QR_SIZE)) {
      RecipeFacts(data, Modifier.weight(1f))
      if (data.qr != null) {
        Spacer(Modifier.width(ColumnGap))
        QrCodeBox(data.qr, Modifier.testTag("share_recipe_qr"))
      }
    }
    Spacer(Modifier.height(InfoGap))
    recipe.stages.forEachIndexed { index, stage -> StepRow(stage, index, data.unit) }
  }
}

@Composable
private fun RecipeFacts(data: RecipeShareData, modifier: Modifier) {
  val recipe = data.recipe
  val unit = data.unit
  val style = compactStyle()
  Column(modifier.height(SHARE_QR_SIZE), verticalArrangement = Arrangement.spacedBy(InfoLineGap)) {
    recipeMethodText(recipe.method, recipe.methodOther)?.let {
      AntText(it, Modifier.testTag("share_recipe_method"), style = style, strong = true, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    Row(Modifier.testTag("share_recipe_facts"), horizontalArrangement = Arrangement.spacedBy(StepGap)) {
      AntText(stringResource(R.string.recipe_card_dose, weightNumber(recipe.doseG, unit), unit.symbol), Modifier.testTag("share_recipe_dose"), style = style, maxLines = 1)
      Separator(style)
      AntText(stringResource(R.string.recipe_card_water, weightNumber(recipe.totalWaterG, unit), unit.symbol), Modifier.testTag("share_recipe_water"), style = style, maxLines = 1)
      Separator(style)
      AntText(stringResource(R.string.recipe_card_ratio, formatRatioNumber(recipe.ratio)), Modifier.testTag("share_recipe_ratio"), style = style, maxLines = 1)
    }
    recipe.waterTempC?.let {
      AntText(stringResource(R.string.recipe_card_temp, formatGrams(it)), Modifier.testTag("share_recipe_temp"), style = style, maxLines = 1)
    }
    data.grindText?.let {
      AntText(
        stringResource(R.string.recipe_card_grind, it),
        Modifier.testTag("share_recipe_grind"),
        style = style,
        maxLines = GRIND_MAX_LINES,
        overflow = TextOverflow.Ellipsis,
      )
    }
    if (data.qr != null) {
      Spacer(Modifier.weight(1f))
      AntText(
        stringResource(R.string.recipe_card_qr_caption),
        Modifier.testTag("share_recipe_qr_caption"),
        type = AntTextType.Secondary,
        style = style,
        maxLines = CAPTION_MAX_LINES,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
private fun Separator(style: TextStyle) {
  AntText("·", type = AntTextType.Secondary, style = style, maxLines = 1)
}

/** Una fila de [RECIPE_STEP_ROW_HEIGHT]: inicio, icono y tipo, nombre, duración y agua acumulada (solo en `POUR`). */
@Composable
private fun StepRow(stage: RecipeStage, index: Int, unit: WeightUnit) {
  val style = compactStyle()
  Row(
    Modifier.fillMaxWidth().height(RECIPE_STEP_ROW_HEIGHT).testTag("share_recipe_step_$index"),
    horizontalArrangement = Arrangement.spacedBy(StepGap),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    AntText(formatClock(stage.startMs), Modifier.width(StepStartWidth), type = AntTextType.Secondary, style = style, maxLines = 1)
    Icon(stepTypeIcon(stage.type), contentDescription = null, modifier = Modifier.size(StepIconSize), tint = AntTheme.colors.textSecondary)
    AntText(stringResource(stepTypeLabel(stage.type)), Modifier.width(StepTypeWidth), style = style, maxLines = 1, overflow = TextOverflow.Ellipsis)
    AntText(stage.name.orEmpty(), Modifier.weight(1f), style = style, maxLines = 1, overflow = TextOverflow.Ellipsis)
    AntText(
      stringResource(R.string.recipe_card_step_duration, (stage.durationMs / 1000.0).roundToLong().toInt()),
      Modifier.width(StepDurationWidth),
      type = AntTextType.Secondary,
      style = style,
      textAlign = TextAlign.End,
      maxLines = 1,
    )
    AntText(
      if (stage.type == StageType.POUR) "${weightNumber(stage.targetWaterG, unit)} ${unit.symbol}" else "",
      Modifier.width(StepWaterWidth),
      style = style,
      textAlign = TextAlign.End,
      maxLines = 1,
    )
  }
}

/**
 * El QR dibujado en negro sobre blanco, sea cual sea el tema. La caja mide [SHARE_QR_SIZE] e incluye la zona de
 * silencio: cada módulo ocupa de `qrCellEdge(i)` a `qrCellEdge(i + 1)` píxeles enteros, sin antialias entre módulos (D9).
 */
@Composable
fun QrCodeBox(matrix: QrMatrix, modifier: Modifier = Modifier) {
  Canvas(modifier.size(SHARE_QR_SIZE)) {
    val sizePx = size.width.toInt()
    val cells = matrix.modules + 2 * QR_QUIET_ZONE
    drawRect(Color.White, Offset.Zero, Size(sizePx.toFloat(), sizePx.toFloat()))
    for (y in 0 until matrix.modules) {
      for (x in 0 until matrix.modules) {
        if (!matrix[x, y]) continue
        val left = qrCellEdge(x + QR_QUIET_ZONE, cells, sizePx)
        val top = qrCellEdge(y + QR_QUIET_ZONE, cells, sizePx)
        val right = qrCellEdge(x + QR_QUIET_ZONE + 1, cells, sizePx)
        val bottom = qrCellEdge(y + QR_QUIET_ZONE + 1, cells, sizePx)
        drawRect(Color.Black, Offset(left.toFloat(), top.toFloat()), Size((right - left).toFloat(), (bottom - top).toFloat()))
      }
    }
  }
}

/** `textSM` con 16 sp de interlineado: las filas de la tarjeta tienen alto fijo (D10). */
@Composable
private fun compactStyle(): TextStyle = AntTheme.typography.textSM.copy(lineHeight = 16.sp)

/** El número sin unidad: gramos sin decimales si es entero y con uno si no; onzas con 2 decimales (D11). */
private fun weightNumber(valueG: Double, unit: WeightUnit): String =
  if (unit == WeightUnit.OUNCES) formatNumber(fromGrams(valueG, unit), unit) else formatGrams(valueG)

/** El método con su nombre; con `OTHER`, el texto de `methodOther` si lo hay. `null` sin método. */
@Composable
private fun recipeMethodText(method: BrewMethod?, other: String?): String? = when {
  method == null -> null
  method == BrewMethod.OTHER && !other.isNullOrBlank() -> other
  else -> method.label()
}
