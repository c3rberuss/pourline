package dev.josueamaya.pourline.ui.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.josueamaya.pourline.R
import dev.josueamaya.pourline.brew.BrewConfig
import dev.josueamaya.pourline.brew.BrewSample
import dev.josueamaya.pourline.brew.StabilityLevel
import dev.josueamaya.pourline.brew.TargetCurve
import dev.josueamaya.pourline.brew.fromGrams
import dev.josueamaya.pourline.protocol.WeightUnit
import dev.josueamaya.pourline.recipe.StageType
import dev.josueamaya.pourline.ui.components.AntProgressCircle
import dev.josueamaya.pourline.ui.components.AntProgressStop
import dev.josueamaya.pourline.ui.components.AntText
import dev.josueamaya.pourline.ui.components.AntTextType
import dev.josueamaya.pourline.ui.scale.brewChartColors
import dev.josueamaya.pourline.ui.scale.formatNumber
import dev.josueamaya.pourline.ui.theme.AntTheme
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

/** Un tramo del aro relleno, hasta [end] (0..1), vertido con el nivel de estabilidad [level]; `null` sin puntuación. */
data class DialStretch(val end: Float, val level: StabilityLevel?)

/**
 * Los tramos del aro coloreados por estabilidad: el agua que cada muestra válida añade sobre el máximo alcanzado hasta
 * entonces se pinta con el nivel de esa muestra, y los tramos seguidos del mismo nivel se juntan. El agua que baja
 * (goteo, ruido) no pinta nada, y el aro se satura al 100 % como [dialFraction].
 */
internal fun dialStretches(samples: List<BrewSample>, targetWaterG: Double?, config: BrewConfig = BrewConfig()): List<DialStretch> {
  if (targetWaterG == null || targetWaterG <= 0.0) return emptyList()
  val out = ArrayList<DialStretch>()
  var reached = 0f
  for (sample in samples) {
    if (!sample.valid) continue
    val fraction = dialFraction(sample.waterG, targetWaterG)
    if (fraction <= reached) continue
    val level = sample.stability?.let(config::levelOf)
    val last = out.lastOrNull()
    if (last != null && last.level == level) out[out.lastIndex] = DialStretch(fraction, level) else out += DialStretch(fraction, level)
    reached = fraction
  }
  return out
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
 * En Experto el dial reúne además lo de la gráfica: el relleno se colorea por [stretches] con los colores de
 * estabilidad de la gráfica (sin puntuación, el color de siempre), una marca indica [expectedWaterG] (dónde debería
 * ir el agua según la curva objetivo) y, bajo los números, la minigráfica del flujo de [flowSamples].
 *
 * @param ratio el ratio agua:café en vivo; `null` sin dosis ("—").
 * @param flowGps el flujo, en la unidad de la báscula por segundo (el mismo valor que ve el usuario).
 * @param expectedWaterG el agua que marca la curva objetivo ahora; `null` sin marca.
 * @param flowSamples las muestras para la minigráfica del flujo; `null` sin minigráfica.
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
  stretches: List<DialStretch> = emptyList(),
  expectedWaterG: Double? = null,
  flowSamples: List<BrewSample>? = null,
) {
  val water = "${formatNumber(fromGrams(waterG, unit), unit)} ${unit.symbol}"
  val goal = targetWaterG?.let { "${formatNumber(fromGrams(it, unit), unit)} ${unit.symbol}" }.orEmpty()
  val percent = dialPercent(waterG, targetWaterG)
  val marker = expectedWaterG?.takeIf { targetWaterG != null && targetWaterG > 0.0 }
  val description = if (marker == null) {
    stringResource(R.string.session_dial_description, water, goal, percent)
  } else {
    val expected = "${formatNumber(fromGrams(marker, unit), unit)} ${unit.symbol}"
    stringResource(R.string.session_dial_description_expected, water, goal, percent, expected)
  }
  val chartColors = brewChartColors(AntTheme.colors)
  val primary = AntTheme.colors.primary.base
  val stops = stretches.map { AntProgressStop(it.end, it.level?.let(chartColors::levelColor) ?: primary) }
  val ratioText = ratio?.let { stringResource(R.string.scale_ratio_value, it) } ?: stringResource(R.string.scale_ratio_empty)
  val flowText = stringResource(R.string.scale_flow_value, "${formatNumber(flowGps, unit)} ${unit.symbol}")

  AntProgressCircle(
    fraction = dialFraction(waterG, targetWaterG),
    modifier = modifier.testTag("session_dial").semantics { contentDescription = description },
    segments = segments,
    stops = stops,
    marker = marker?.let { dialFraction(it, targetWaterG) },
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
      if (flowSamples != null) {
        FlowSparkline(
          flowSamples,
          unit,
          Modifier.padding(top = AntTheme.spacing.xxs).fillMaxWidth(SPARKLINE_WIDTH).height(SparklineHeight),
        )
      }
    }
  }
}

/** La minigráfica no llega al ancho del texto: a esa altura del dial el círculo ya se estrecha. */
private const val SPARKLINE_WIDTH = 0.6f
private val SparklineHeight = 24.dp

/**
 * El dial de la sesión sin báscula (SPEC-007 §3.4): el mismo aro que [BrewDial], pero su avance es el del paso en curso y
 * dentro van el reloj y, debajo, la línea de acción ([action]). No tiene agua, ratio ni flujo.
 *
 * @param fraction avance 0..1 del paso en curso.
 * @param color el relleno del aro: el color del tipo del paso en curso ([stepTypeColors]).
 * @param description lo que lee el lector de pantalla (el reloj y la acción).
 * @param actionTag la etiqueta de pruebas de la línea de acción.
 */
@Composable
fun TimedDial(
  fraction: Float,
  clock: String,
  action: String?,
  description: String,
  modifier: Modifier = Modifier,
  color: Color = AntTheme.colors.primary.base,
  actionTag: String = "scaleless_action",
) {
  AntProgressCircle(
    fraction = fraction,
    color = color,
    modifier = modifier.testTag("session_dial").semantics { contentDescription = description },
  ) {
    Column(
      Modifier.padding(horizontal = AntTheme.spacing.lg),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.xxs),
    ) {
      AntText(
        clock,
        Modifier.fillMaxWidth().testTag("scaleless_clock"),
        style = AntTheme.typography.heading1.copy(fontSize = 48.sp, lineHeight = 56.sp, fontWeight = FontWeight.Light),
        textAlign = TextAlign.Center,
        maxLines = 1,
      )
      if (action != null) {
        AntText(
          action,
          Modifier.fillMaxWidth().testTag(actionTag),
          type = AntTextType.Secondary,
          textAlign = TextAlign.Center,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}
