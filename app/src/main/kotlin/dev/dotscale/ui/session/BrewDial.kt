package dev.dotscale.ui.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import dev.dotscale.R
import dev.dotscale.brew.TargetCurve
import dev.dotscale.brew.fromGrams
import dev.dotscale.protocol.WeightUnit
import dev.dotscale.recipe.StageType
import dev.dotscale.ui.components.AntProgressCircle
import dev.dotscale.ui.components.AntText
import dev.dotscale.ui.components.AntTextType
import dev.dotscale.ui.scale.formatNumber
import dev.dotscale.ui.theme.AntTheme
import kotlin.math.roundToInt

/**
 * Las fronteras del aro (0..1) en el agua acumulada de cada paso `POUR` (D4): los `SWIRL` y `WAIT` no añaden agua, así
 * que no tienen segmento. La última frontera (el 100 %) no se dibuja.
 */
internal fun dialSegments(curve: TargetCurve): List<Float> {
  val total = curve.finalWaterG
  if (total <= 0.0) return emptyList()
  return curve.stages
    .filter { it.type == StageType.POUR }
    .map { (it.targetWaterG / total).toFloat() }
    .filter { it > 0f && it < 1f }
    .distinct()
}

/** `agua / agua objetivo`, saturado en 0..1 (el aro se satura al 100 %). */
internal fun dialFraction(waterG: Double, targetWaterG: Double?): Float =
  if (targetWaterG == null || targetWaterG <= 0.0) 0f else (waterG / targetWaterG).coerceIn(0.0, 1.0).toFloat()

/** El porcentaje del agua objetivo que se lee en voz alta; no se satura (un 110 % es un 110 %). */
internal fun dialPercent(waterG: Double, targetWaterG: Double?): Int =
  if (targetWaterG == null || targetWaterG <= 0.0) 0 else (waterG / targetWaterG * 100).coerceAtLeast(0.0).roundToInt()

/**
 * El dial del vertido: el aro marca el progreso `agua / agua objetivo` (con receta, dividido en segmentos por paso) y
 * dentro van el agua vertida en grande y, debajo, el ratio en vivo y el flujo (solo números, en los dos modos).
 *
 * @param ratio el ratio agua:café en vivo; `null` sin dosis ("—").
 * @param flowGps el flujo, en la unidad de la báscula por segundo (el mismo valor que ve el usuario).
 */
@Composable
fun BrewDial(
  waterG: Double,
  targetWaterG: Double?,
  segments: List<Float>,
  ratio: Double?,
  flowGps: Double,
  unit: WeightUnit,
  modifier: Modifier = Modifier,
) {
  val water = "${formatNumber(fromGrams(waterG, unit), unit)} ${unit.symbol}"
  val goal = targetWaterG?.let { "${formatNumber(fromGrams(it, unit), unit)} ${unit.symbol}" }.orEmpty()
  val description = stringResource(R.string.session_dial_description, water, goal, dialPercent(waterG, targetWaterG))
  val ratioText = ratio?.let { stringResource(R.string.scale_ratio_value, it) } ?: stringResource(R.string.scale_ratio_empty)
  val flowText = stringResource(R.string.scale_flow_value, "${formatNumber(flowGps, unit)} ${unit.symbol}")

  AntProgressCircle(
    fraction = dialFraction(waterG, targetWaterG),
    modifier = modifier.testTag("session_dial").semantics { contentDescription = description },
    segments = segments,
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.xxs),
    ) {
      AntText(
        water,
        Modifier.fillMaxWidth().testTag("session_water"),
        style = AntTheme.typography.heading1.copy(fontSize = 40.sp, lineHeight = 46.sp, fontWeight = FontWeight.Light),
        textAlign = TextAlign.Center,
        maxLines = 1,
      )
      AntText(
        stringResource(R.string.session_dial_ratio_flow, ratioText, flowText),
        Modifier.fillMaxWidth().testTag("session_ratio_flow"),
        type = AntTextType.Secondary,
        textAlign = TextAlign.Center,
        maxLines = 1,
      )
    }
  }
}
