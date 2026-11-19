package dev.josueamaya.pourline.brew

import dev.josueamaya.pourline.protocol.WeightUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartAxisTest {
  private val g = WeightUnit.GRAMS
  private val oz = WeightUnit.OUNCES
  private val delta = 1e-9

  @Test
  fun `time axis starts at 60 s and grows by 30 s`() {
    assertEquals(60_000, ChartAxis.timeTopMs(0))
    assertEquals(60_000, ChartAxis.timeTopMs(51_000))
    assertEquals(90_000, ChartAxis.timeTopMs(51_001))
    assertEquals(90_000, ChartAxis.timeTopMs(76_500))
    assertEquals(120_000, ChartAxis.timeTopMs(76_501))
  }

  @Test
  fun `water steps in grams with the 85 percent rule`() {
    listOf(
      0.0 to 60.0, 51.0 to 60.0, 51.1 to 120.0, 102.0 to 120.0, 102.1 to 200.0, 170.0 to 200.0,
      170.1 to 300.0, 255.0 to 300.0, 255.1 to 400.0, 340.0 to 400.0, 340.1 to 600.0,
      510.0 to 600.0, 510.1 to 800.0, 680.0 to 800.0,
    ).forEach { (max, top) -> assertEquals("max=$max", top, ChartAxis.waterTop(max, g), delta) }
  }

  @Test
  fun `water grows by 200 g beyond 800 g`() {
    assertEquals(1_000.0, ChartAxis.waterTop(680.1, g), delta)
    assertEquals(1_000.0, ChartAxis.waterTop(850.0, g), delta)
    assertEquals(1_200.0, ChartAxis.waterTop(850.1, g), delta)
    assertEquals(1_400.0, ChartAxis.waterTop(1_100.0, g), delta)
  }

  @Test
  fun `water steps in ounces and grows by 8 oz beyond 32 oz`() {
    listOf(
      1.7 to 2.0, 1.71 to 4.0, 3.4 to 4.0, 3.41 to 8.0, 6.8 to 8.0, 6.81 to 12.0, 10.2 to 12.0,
      10.21 to 16.0, 13.6 to 16.0, 13.61 to 24.0, 20.4 to 24.0, 20.41 to 32.0, 27.2 to 32.0,
      27.21 to 40.0, 34.0 to 40.0, 34.01 to 48.0,
    ).forEach { (max, top) -> assertEquals("max=$max", top, ChartAxis.waterTop(max, oz), delta) }
  }

  @Test
  fun `flow steps in g per second`() {
    listOf(
      0.0 to 8.0, 6.8 to 8.0, 6.81 to 12.0, 10.2 to 12.0, 10.21 to 16.0, 13.6 to 16.0,
      13.61 to 20.0, 17.0 to 20.0, 17.01 to 24.0, 20.41 to 28.0,
    ).forEach { (max, top) -> assertEquals("max=$max", top, ChartAxis.flowTop(max, g), delta) }
  }

  @Test
  fun `flow steps in oz per second`() {
    listOf(
      0.0 to 0.25, 0.21 to 0.25, 0.22 to 0.5, 0.42 to 0.5, 0.43 to 0.75, 0.63 to 0.75,
      0.64 to 1.0, 0.85 to 1.0, 0.86 to 1.25,
    ).forEach { (max, top) -> assertEquals("max=$max", top, ChartAxis.flowTop(max, oz), delta) }
  }

  @Test
  fun `tops never decrease while the maximum grows`() {
    var water = 0.0
    var flow = 0.0
    var time = 0L
    var maxWater = 0.0
    var maxFlow = 0.0
    var maxTime = 0L
    for (i in 0..2_000) {
      maxWater = i * 0.7
      maxFlow = i * 0.015
      maxTime = i * 450L
      val w = ChartAxis.waterTop(maxWater, g)
      val f = ChartAxis.flowTop(maxFlow, g)
      val t = ChartAxis.timeTopMs(maxTime)
      assertTrue(w >= water && f >= flow && t >= time)
      water = w
      flow = f
      time = t
    }
    assertTrue(maxWater <= water * 0.85 + delta && maxFlow <= flow * 0.85 + delta && maxTime <= time * 0.85 + delta)
  }
}
