package dev.dotscale.ui.scale

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import dev.dotscale.R
import dev.dotscale.protocol.WeightUnit
import dev.dotscale.ui.components.AntStatistic
import dev.dotscale.ui.components.AntText
import dev.dotscale.ui.components.AntTextType
import dev.dotscale.ui.theme.AntTheme
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Lo que muestra el bloque "Ajuste al objetivo". Es puro: Scale lo construye con el `BrewFit` recién calculado
 * y el detalle del historial, con las columnas guardadas, así que las cifras son las mismas en los dos sitios.
 */
data class FitView(
  /** 0-100; `null` si no se pudo calcular. */
  val score: Int?,
  val referenceName: String?,
  /** Agua real − agua objetivo, en gramos. */
  val waterDiffG: Double,
  /** `waterDiffG / agua objetivo`. */
  val waterDiffShare: Double,
  /** Tiempo real − tiempo objetivo, en ms. */
  val timeDiffMs: Long,
)

/** Calcula las diferencias de una preparación con el agua y el tiempo del objetivo. `null` si el objetivo no tiene agua. */
fun fitView(
  score: Int?,
  referenceName: String?,
  targetWaterG: Double,
  targetEndMs: Long,
  totalWaterG: Double,
  totalTimeMs: Long,
): FitView? {
  if (targetWaterG <= 0.0) return null
  val diff = totalWaterG - targetWaterG
  return FitView(score, referenceName, diff, diff / targetWaterG, totalTimeMs - targetEndMs)
}

/** Porcentaje con signo ("+1,6 %", "−1,6 %") y un decimal. */
@Composable
internal fun formatSignedPercent(share: Double): String {
  val percent = share * 100
  val number = String.format(Locale.getDefault(), "%.1f", abs(percent))
  val isZero = number.all { it == '0' || it == '.' || it == ',' }
  val sign = if (isZero) "" else if (percent > 0) "+" else "−"
  return stringResource(R.string.fit_percent, "$sign$number")
}

/** Segundos enteros con signo ("+3 s", "−12 s"). */
@Composable
internal fun formatSignedSeconds(ms: Long): String {
  val seconds = (abs(ms) / 1000.0).roundToLong()
  val sign = if (seconds == 0L) "" else if (ms > 0) "+" else "−"
  return stringResource(R.string.fit_seconds, "$sign$seconds")
}

/** "Ajuste al objetivo": la puntuación, las diferencias de agua y de tiempo, y la referencia. */
@Composable
fun FitBlock(fit: FitView, unit: WeightUnit, modifier: Modifier = Modifier) {
  Column(modifier.fillMaxWidth().testTag("fit_block"), verticalArrangement = Arrangement.spacedBy(AntTheme.spacing.xxs)) {
    AntText(stringResource(R.string.fit_title), type = AntTextType.Secondary)
    val score = fit.score
    AntStatistic(
      value = if (score != null) stringResource(R.string.fit_score, score) else stringResource(R.string.fit_score_none),
      modifier = Modifier.testTag("fit_score"),
      valueColor = AntTheme.colors.primary.base,
    )
    AntText(
      text = stringResource(R.string.fit_water, formatDeviation(fit.waterDiffG, unit), formatSignedPercent(fit.waterDiffShare)),
      modifier = Modifier.testTag("fit_water"),
    )
    AntText(stringResource(R.string.fit_time, formatSignedSeconds(fit.timeDiffMs)), Modifier.testTag("fit_time"))
    fit.referenceName?.let {
      AntText(stringResource(R.string.fit_reference, it), Modifier.testTag("fit_reference"), type = AntTextType.Secondary)
    }
  }
}
