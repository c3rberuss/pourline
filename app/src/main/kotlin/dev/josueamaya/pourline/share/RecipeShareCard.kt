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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.josueamaya.pourline.R
import dev.josueamaya.pourline.brew.fromGrams
import dev.josueamaya.pourline.history.BrewMethod
import dev.josueamaya.pourline.protocol.WeightUnit
import dev.josueamaya.pourline.recipe.RecipeStage
import dev.josueamaya.pourline.recipe.StageType
import dev.josueamaya.pourline.ui.components.AntIcons
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
private const val CAPTION_MAX_LINES = 2
private val NameGap = 8.dp
private val InfoGap = 12.dp
private val QrGap = 12.dp
private val CaptionGap = 4.dp
private val InfoLineGap = 2.dp
private val StepIconSize = 12.dp
private val StepStartWidth = 32.dp
private val StepDurationWidth = 32.dp
private val StepWaterWidth = 44.dp
private val StepGap = 4.dp

/**
 * Centra la caja del QR en píxeles pares: como mucho 1 px a la izquierda del centro exacto (D31). La franja empieza en
 * una x par (20 dp = 60 px), así que la caja de módulos de 6 px también, y al reducir la imagen al 50 % cada módulo cae
 * en 3 px exactos.
 */
private val EvenCenterHorizontally = Alignment.Horizontal { size, space, _ -> ((space - size) / 2) and 1.inv() }

/**
 * La tarjeta de una receta (SPEC-009 §3.3, enmienda 1): el nombre y los datos (dosis, agua, ratio y tiempo total con un
 * reloj, SPEC-010) a todo el ancho, una fila de alto fijo por paso y, al final, una franja con el QR de módulos de 6 px
 * y su texto. Sin notas ni instrucciones. Mide 1080 px de ancho y al menos 1350 de alto: crece con el contenido (ver
 * [ShareFrame]). Sin [RecipeShareData.qr] no hay franja.
 */
@Composable
fun RecipeShareCard(data: RecipeShareData, modifier: Modifier = Modifier) {
  val recipe = data.recipe
  ShareFrame(modifier, fitContent = true) {
    AntText(
      recipe.name,
      Modifier.fillMaxWidth().testTag("share_recipe_name"),
      style = AntTheme.typography.heading5,
      maxLines = NAME_MAX_LINES,
      overflow = TextOverflow.Ellipsis,
    )
    Spacer(Modifier.height(NameGap))
    RecipeFacts(data, Modifier.fillMaxWidth())
    Spacer(Modifier.height(InfoGap))
    val typeWidth = stepTypeWidth()
    recipe.stages.forEachIndexed { index, stage -> StepRow(stage, index, data.unit, typeWidth) }
    if (data.qr != null) {
      Spacer(Modifier.height(QrGap))
      QrStrip(data.qr)
    }
  }
}

@Composable
private fun RecipeFacts(data: RecipeShareData, modifier: Modifier) {
  val recipe = data.recipe
  val unit = data.unit
  val style = compactStyle()
  Column(modifier, verticalArrangement = Arrangement.spacedBy(InfoLineGap)) {
    recipeMethodText(recipe.method, recipe.methodOther)?.let {
      AntText(it, Modifier.testTag("share_recipe_method"), style = style, strong = true, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    Row(Modifier.testTag("share_recipe_facts"), horizontalArrangement = Arrangement.spacedBy(StepGap)) {
      AntText(stringResource(R.string.recipe_card_dose, weightNumber(recipe.doseG, unit), unit.symbol), Modifier.testTag("share_recipe_dose"), style = style, maxLines = 1)
      Separator(style)
      AntText(stringResource(R.string.recipe_card_water, weightNumber(recipe.totalWaterG, unit), unit.symbol), Modifier.testTag("share_recipe_water"), style = style, maxLines = 1)
      Separator(style)
      AntText(stringResource(R.string.recipe_card_ratio, formatRatioNumber(recipe.ratio)), Modifier.testTag("share_recipe_ratio"), style = style, maxLines = 1)
      Separator(style)
      Icon(AntIcons.Clock, contentDescription = null, modifier = Modifier.size(StepIconSize).align(Alignment.CenterVertically), tint = AntTheme.colors.textSecondary)
      AntText(formatClock(recipe.totalDurationMs), Modifier.testTag("share_recipe_duration"), style = style, maxLines = 1)
    }
    recipe.waterTempC?.let {
      AntText(stringResource(R.string.recipe_card_temp, formatGrams(it)), Modifier.testTag("share_recipe_temp"), style = style, maxLines = 1)
    }
    data.grindText?.let {
      AntText(
        stringResource(R.string.recipe_card_grind, it),
        Modifier.fillMaxWidth().testTag("share_recipe_grind"),
        style = style,
        maxLines = GRIND_MAX_LINES,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

/** La franja del final: la caja del QR centrada en píxeles pares (D31) y su texto debajo, centrado. */
@Composable
private fun QrStrip(matrix: QrMatrix) {
  Column(Modifier.fillMaxWidth().testTag("share_recipe_qr_strip")) {
    QrCodeBox(matrix, Modifier.align(EvenCenterHorizontally).testTag("share_recipe_qr"))
    Spacer(Modifier.height(CaptionGap))
    AntText(
      stringResource(R.string.recipe_card_qr_caption),
      Modifier.fillMaxWidth().testTag("share_recipe_qr_caption"),
      type = AntTextType.Secondary,
      style = compactStyle(),
      textAlign = TextAlign.Center,
      maxLines = CAPTION_MAX_LINES,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

@Composable
private fun Separator(style: TextStyle) {
  AntText("·", type = AntTextType.Secondary, style = style, maxLines = 1)
}

/** Una fila de [RECIPE_STEP_ROW_HEIGHT]: inicio, icono y tipo, nombre, duración y agua acumulada (solo en `POUR`). */
@Composable
private fun StepRow(stage: RecipeStage, index: Int, unit: WeightUnit, typeWidth: Dp) {
  val style = compactStyle()
  Row(
    Modifier.fillMaxWidth().height(RECIPE_STEP_ROW_HEIGHT).testTag("share_recipe_step_$index"),
    horizontalArrangement = Arrangement.spacedBy(StepGap),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    AntText(formatClock(stage.startMs), Modifier.width(StepStartWidth), type = AntTextType.Secondary, style = style, maxLines = 1)
    Icon(stepTypeIcon(stage.type), contentDescription = null, modifier = Modifier.size(StepIconSize), tint = AntTheme.colors.textSecondary)
    AntText(stringResource(stepTypeLabel(stage.type)), Modifier.width(typeWidth).testTag("share_recipe_step_type_$index"), style = style, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
 * El QR dibujado en negro sobre blanco, sea cual sea el tema. Cada módulo mide [SHARE_QR_MODULE] (6 px enteros a la
 * densidad de la captura) y la caja, con la zona de silencio, `matrix.cells × 6` px: sin antialias entre módulos (D9, D30).
 */
@Composable
fun QrCodeBox(matrix: QrMatrix, modifier: Modifier = Modifier) {
  val density = LocalDensity.current
  val modulePx = with(density) { SHARE_QR_MODULE.roundToPx() }
  val sidePx = matrix.cells * modulePx
  Canvas(modifier.size(with(density) { sidePx.toDp() })) {
    drawRect(Color.White, Offset.Zero, Size(sidePx.toFloat(), sidePx.toFloat()))
    val module = Size(modulePx.toFloat(), modulePx.toFloat())
    for (y in 0 until matrix.modules) {
      for (x in 0 until matrix.modules) {
        if (!matrix[x, y]) continue
        drawRect(Color.Black, Offset(((x + QR_QUIET_ZONE) * modulePx).toFloat(), ((y + QR_QUIET_ZONE) * modulePx).toFloat()), module)
      }
    }
  }
}

/**
 * El ancho de la columna del tipo: el de la etiqueta más larga en el idioma actual, para que ninguna se corte y las
 * columnas sigan alineadas entre filas.
 */
@Composable
private fun stepTypeWidth(): Dp {
  val style = compactStyle()
  val measurer = rememberTextMeasurer()
  val labels = StageType.entries.map { stringResource(stepTypeLabel(it)) }
  val widthPx = labels.maxOf { measurer.measure(it, style, maxLines = 1, softWrap = false).size.width }
  return with(LocalDensity.current) { widthPx.toDp() }
}

/** `textSM` con 16 sp de interlineado: las filas de la tarjeta tienen alto fijo (D10). */
@Composable
private fun compactStyle(): TextStyle = AntTheme.typography.textSM.copy(lineHeight = 16.sp)

/** El número sin unidad: gramos sin decimales si es entero y con uno si no; onzas con 2 decimales (D11). */
internal fun weightNumber(valueG: Double, unit: WeightUnit): String =
  if (unit == WeightUnit.OUNCES) formatNumber(fromGrams(valueG, unit), unit) else formatGrams(valueG)

/** El método con su nombre; con `OTHER`, el texto de `methodOther` si lo hay. `null` sin método. */
@Composable
internal fun recipeMethodText(method: BrewMethod?, other: String?): String? = when {
  method == null -> null
  method == BrewMethod.OTHER && !other.isNullOrBlank() -> other
  else -> method.label()
}
