package dev.josueamaya.pourline.ui.history

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import dev.josueamaya.pourline.brew.BrewSample
import dev.josueamaya.pourline.brew.ChartAxis
import dev.josueamaya.pourline.protocol.WeightUnit
import dev.josueamaya.pourline.session.rampSamples
import dev.josueamaya.pourline.ui.theme.AntColors
import dev.josueamaya.pourline.ui.theme.AntPalette
import dev.josueamaya.pourline.ui.theme.antDarkColors
import dev.josueamaya.pourline.ui.theme.antLightColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompareColorsTest {
  private fun ratio(color: Color, background: Color) =
    AntPalette.contrast(color.compositeOver(background).toArgb(), background.toArgb())

  private fun check(colors: AntColors) {
    val mode = if (colors.isDark) "dark" else "light"
    val compare = compareColors(colors)
    val background = colors.bgContainer
    assertTrue("$mode A = ${ratio(compare.a, background)}", ratio(compare.a, background) >= 3.0)
    assertTrue("$mode B = ${ratio(compare.b, background)}", ratio(compare.b, background) >= 3.0)
    assertNotEquals(compare.a, compare.b)
  }

  @Test
  fun `both series are visible in light mode`() = check(antLightColors())

  @Test
  fun `both series are visible in dark mode`() = check(antDarkColors())

  @Test
  fun `A is the brand blue`() {
    val colors = antLightColors()
    assertEquals(colors.primary.base, compareColors(colors).a)
  }

  @Test
  fun `both charts share the scale of the larger brew`() {
    val small = CompareSeries(Color.Red, rampSamples(totalMs = 60_000, totalG = 100.0))
    val large = CompareSeries(Color.Blue, rampSamples(totalMs = 120_000, totalG = 300.0))

    val scale = compareScale(listOf(small, large), CompareKind.WATER, WeightUnit.GRAMS, xMaxMs = 120_000)

    assertEquals(ChartAxis.timeTopMs(120_000), scale.xTopMs)
    assertEquals(ChartAxis.waterTop(300.0, WeightUnit.GRAMS), scale.waterTop, 0.0)
    assertEquals(scale.waterTop, scale.flowTop, 0.0) // un solo eje
  }

  @Test
  fun `the flow chart uses the flow axis and ignores invalid samples`() {
    val samples = listOf(
      BrewSample(0, 0.0, 2.0, null, valid = true),
      BrewSample(1_000, 2.0, 99.0, null, valid = false),
    )
    val scale = compareScale(listOf(CompareSeries(Color.Red, samples)), CompareKind.FLOW, WeightUnit.GRAMS, xMaxMs = 1_000)

    assertEquals(ChartAxis.flowTop(2.0, WeightUnit.GRAMS), scale.waterTop, 0.0)
  }
}
