package dev.dotscale.ui.scale

import dev.dotscale.protocol.WeightUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Locale

class TargetGuideFormatTest {
  private val previous = Locale.getDefault()

  @Before
  fun setUp() = Locale.setDefault(Locale.US)

  @After
  fun tearDown() = Locale.setDefault(previous)

  @Test
  fun `amounts are whole grams or ounces with two decimals`() {
    assertEquals("150 g", formatGuideAmount(150.0, WeightUnit.GRAMS))
    assertEquals("55 g", formatGuideAmount(54.67, WeightUnit.GRAMS))
    assertEquals("5.29 oz", formatGuideAmount(150.0, WeightUnit.OUNCES))
  }

  @Test
  fun `deviation carries a plus or a real minus sign`() {
    assertEquals("+6 g", formatDeviation(6.2, WeightUnit.GRAMS))
    assertEquals("−4 g", formatDeviation(-3.6, WeightUnit.GRAMS))
    assertEquals("+0.21 oz", formatDeviation(6.0, WeightUnit.OUNCES))
    assertEquals("−0.14 oz", formatDeviation(-4.0, WeightUnit.OUNCES))
  }

  @Test
  fun `a deviation that rounds to zero has no sign`() {
    assertEquals("0 g", formatDeviation(0.4, WeightUnit.GRAMS))
    assertEquals("0 g", formatDeviation(-0.4, WeightUnit.GRAMS))
    assertEquals("0.00 oz", formatDeviation(0.1, WeightUnit.OUNCES))
  }

  @Test
  fun `fit view computes the differences against the target`() {
    val fit = fitView(86, "V60", targetWaterG = 250.0, targetEndMs = 100_000, totalWaterG = 246.0, totalTimeMs = 103_000)!!
    assertEquals(-4.0, fit.waterDiffG, 1e-9)
    assertEquals(-0.016, fit.waterDiffShare, 1e-9)
    assertEquals(3_000L, fit.timeDiffMs)
    assertEquals(86, fit.score)
  }

  @Test
  fun `fit view is null when the target has no water`() {
    assertEquals(null, fitView(null, null, 0.0, 0, 10.0, 1_000))
  }
}
