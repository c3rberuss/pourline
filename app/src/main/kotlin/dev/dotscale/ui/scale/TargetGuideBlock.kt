package dev.dotscale.ui.scale

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import dev.dotscale.R
import dev.dotscale.brew.DeviationLevel
import dev.dotscale.brew.GuideState
import dev.dotscale.brew.TargetCurve
import dev.dotscale.brew.TargetStage
import dev.dotscale.brew.fromGrams
import dev.dotscale.protocol.WeightUnit
import dev.dotscale.ui.components.AntText
import dev.dotscale.ui.components.AntTextType
import dev.dotscale.ui.theme.AntTheme
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

private const val MINUS = '−'

/** Tamaño de las cifras de la guía: a 64 sp no caben "Hasta 150 g" y "+6 g" en 360 dp. */
private val GuideFigureSize = 40.sp

/** Cantidad de agua de la guía: gramos enteros u onzas con 2 decimales, con su unidad. */
internal fun formatGuideAmount(grams: Double, unit: WeightUnit): String {
  val value = fromGrams(grams, unit)
  val number = if (unit == WeightUnit.OUNCES) String.format(Locale.getDefault(), "%.2f", value) else value.roundToLong().toString()
  return "$number ${unit.symbol}"
}

/**
 * Desvío con "+" o "−" (U+2212), en gramos enteros o en onzas con 2 decimales. Un desvío que se redondea
 * a cero se escribe sin signo.
 */
internal fun formatDeviation(grams: Double, unit: WeightUnit): String {
  val value = fromGrams(grams, unit)
  val magnitude = abs(value)
  val number = if (unit == WeightUnit.OUNCES) {
    String.format(Locale.getDefault(), "%.2f", magnitude)
  } else {
    magnitude.roundToLong().toString()
  }
  val isZero = number.all { it == '0' || it == '.' || it == ',' }
  val sign = when {
    isZero -> ""
    value > 0 -> "+"
    else -> MINUS.toString()
  }
  return "$sign$number ${unit.symbol}"
}

/** "2/3" o "2/3 · 2.º vertido". */
@Composable
internal fun stageLabel(stage: TargetStage, curve: TargetCurve): String {
  val total = curve.stages.size
  val name = stage.name
  return if (name.isNullOrBlank()) {
    stringResource(R.string.target_stage_label, stage.index + 1, total)
  } else {
    stringResource(R.string.target_stage_label_named, stage.index + 1, total, name)
  }
}

private fun DeviationLevel?.textType(): AntTextType = when (this) {
  DeviationLevel.OK -> AntTextType.Success
  DeviationLevel.WARNING -> AntTextType.Warning
  DeviationLevel.ERROR -> AntTextType.Danger
  null -> AntTextType.Secondary
}

/**
 * La guía de la etapa, pensada para leerse de reojo con la tetera en la mano: la etapa en texto pequeño, y
 * "Hasta X g" y el desvío en la tipografía de cifras de Scale. El texto de la etapa es una región `Polite`,
 * así que el cambio de etapa se anuncia.
 */
@Composable
fun TargetGuideBlock(curve: TargetCurve, guide: GuideState, unit: WeightUnit, modifier: Modifier = Modifier) {
  val figure = scaleFigureStyle(GuideFigureSize)
  val stage = guide.stage
  val stageText = when {
    guide.allStagesDone -> stringResource(R.string.target_all_done)
    stage != null -> stageLabel(stage, curve)
    else -> stringResource(R.string.target_before_first, curve.stages.size)
  }
  val deviation = guide.deviationG?.let { formatDeviation(it, unit) }
  val deviationDescription = stringResource(R.string.target_deviation_description, deviation ?: stringResource(R.string.target_deviation_none))

  Column(modifier.fillMaxWidth().testTag("target_guide"), verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.xxs)) {
    AntText(
      text = stageText,
      modifier = Modifier.testTag("target_guide_stage").semantics { liveRegion = LiveRegionMode.Polite },
      type = AntTextType.Secondary,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(AntTheme.spacing.sm)) {
      val goal = (stage ?: guide.nextStage)?.takeIf { !guide.allStagesDone }
      Column(Modifier.weight(1f)) {
        if (goal != null) {
          AntText(
            text = stringResource(R.string.target_up_to, formatGuideAmount(goal.targetWaterG, unit)),
            modifier = Modifier.testTag("target_guide_goal"),
            style = figure,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
      AntText(
        text = deviation ?: stringResource(R.string.target_deviation_none),
        modifier = Modifier.testTag("target_guide_deviation").semantics { contentDescription = deviationDescription },
        type = guide.deviationLevel.textType(),
        style = figure,
        maxLines = 1,
      )
    }
    guide.countdownS?.let { seconds ->
      AntText(
        text = stringResource(R.string.target_next_in, seconds),
        modifier = Modifier.testTag("target_guide_countdown"),
        style = figure,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}
