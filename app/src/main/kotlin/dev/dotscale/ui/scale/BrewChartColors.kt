package dev.dotscale.ui.scale

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import dev.dotscale.brew.BrewConfig
import dev.dotscale.brew.StabilityLevel
import dev.dotscale.ui.theme.AntColors

/**
 * Colores de la gráfica. Las series (agua, niveles y neutro) cumplen 3:1 contra el fondo de la
 * tarjeta (WCAG 1.4.11); los tonos `base` de `success` y `warning` no llegan a 3:1 sobre blanco,
 * por eso se usan los `text`.
 */
@Immutable
data class BrewChartColors(
  val water: Color,
  val waterFill: Color,
  val stable: Color,
  val irregular: Color,
  val turbulent: Color,
  val neutral: Color,
  /** Curva objetivo: gris discontinuo. */
  val target: Color,
  val grid: Color,
  val axisLabel: Color,
) {
  /** Color de un tramo de la curva de flujo; sin puntuación, el neutro. */
  fun flowColor(stability: Int?): Color = when (stability?.let(LEVELS::levelOf)) {
    StabilityLevel.STABLE -> stable
    StabilityLevel.IRREGULAR -> irregular
    StabilityLevel.TURBULENT -> turbulent
    null -> neutral
  }

  private companion object {
    val LEVELS = BrewConfig()
  }
}

fun brewChartColors(colors: AntColors) = BrewChartColors(
  water = colors.primary.base,
  waterFill = colors.primary.base.copy(alpha = 0.15f),
  stable = colors.success.text,
  irregular = colors.warning.text,
  turbulent = colors.error.text,
  neutral = colors.textSecondary,
  target = colors.textSecondary,
  grid = colors.split,
  axisLabel = colors.textSecondary,
)
