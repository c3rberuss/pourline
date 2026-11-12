package dev.dotscale.ui.scale

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import dev.dotscale.ui.theme.AntColors
import dev.dotscale.ui.theme.AntPalette
import dev.dotscale.ui.theme.antDarkColors
import dev.dotscale.ui.theme.antLightColors
import org.junit.Assert.assertTrue
import org.junit.Test

/** Las series deben tener ≥ 3:1 contra el fondo de la tarjeta (WCAG 1.4.11) y las etiquetas ≥ 4.5:1. */
class BrewChartColorsTest {

  /** `AntPalette.contrast` espera colores opacos, así que los translúcidos se componen antes. */
  private fun ratio(color: Color, background: Color) =
    AntPalette.contrast(color.compositeOver(background).toArgb(), background.toArgb())

  private fun check(colors: AntColors) {
    val mode = if (colors.isDark) "dark" else "light"
    val chart = brewChartColors(colors)
    val background = colors.bgContainer
    mapOf(
      "water" to chart.water,
      "stable" to chart.stable,
      "irregular" to chart.irregular,
      "turbulent" to chart.turbulent,
      "neutral" to chart.neutral,
    ).forEach { (name, color) ->
      val ratio = ratio(color, background)
      assertTrue("$mode $name = $ratio", ratio >= 3.0)
    }
    val label = ratio(chart.axisLabel, background)
    assertTrue("$mode axis label = $label", label >= 4.5)
  }

  @Test
  fun `series are visible in light mode`() = check(antLightColors())

  @Test
  fun `series are visible in dark mode`() = check(antDarkColors())

  @Test
  fun `flow color follows the level and falls back to neutral`() {
    val chart = brewChartColors(antLightColors())
    assertTrue(chart.flowColor(null) == chart.neutral)
    assertTrue(chart.flowColor(80) == chart.stable)
    assertTrue(chart.flowColor(60) == chart.irregular)
    assertTrue(chart.flowColor(10) == chart.turbulent)
  }
}
