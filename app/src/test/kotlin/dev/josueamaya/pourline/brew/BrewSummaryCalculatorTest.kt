package dev.josueamaya.pourline.brew

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrewSummaryCalculatorTest {
  private fun s(tMs: Long, flow: Double, stability: Int? = null, water: Double = 0.0, valid: Boolean = true) =
    BrewSample(tMs, water, flow, stability, valid)

  private fun summary(
    samples: List<BrewSample>,
    pours: List<Pour> = listOf(Pour(1, 0, samples.last().tMs, 0.0)),
    dose: Double? = null,
    reason: FinishReason = FinishReason.MANUAL,
  ) = summarize(samples, pours, samples.last().tMs, reason, dose)

  @Test
  fun `average flow is weighted by time and only counts pours`() {
    val samples = listOf(s(0, 2.0), s(100, 2.0), s(200, 6.0), s(300, 6.0), s(5_000, 0.0))
    val result = summary(samples, pours = listOf(Pour(1, 0, 300, 0.0)))
    // Pesos 0, 100, 100, 100: (2·100 + 6·100 + 6·100) / 300.
    assertEquals(1_400.0 / 300, result.avgFlowGps, 1e-9)
    assertEquals(6.0, result.maxFlowGps, 0.0)
  }

  @Test
  fun `a long gap weighs at most 500 ms`() {
    val samples = listOf(s(0, 2.0), s(10_000, 10.0))
    val result = summary(samples)
    assertEquals(10.0, result.avgFlowGps, 1e-9)
  }

  @Test
  fun `level share only counts scored time and adds up to one`() {
    val samples = listOf(
      s(0, 4.0),
      s(100, 4.0, stability = 100),
      s(200, 4.0, stability = 60),
      s(300, 4.0, stability = 20),
      s(400, 4.0, stability = null),
      s(500, 4.0, stability = null),
    )
    val result = summary(samples)
    assertEquals(1.0 / 3, result.levelShare.getValue(StabilityLevel.STABLE), 1e-9)
    assertEquals(1.0 / 3, result.levelShare.getValue(StabilityLevel.IRREGULAR), 1e-9)
    assertEquals(1.0 / 3, result.levelShare.getValue(StabilityLevel.TURBULENT), 1e-9)
    assertEquals(1.0, result.levelShare.values.sum(), 1e-9)
    assertEquals(60, result.stabilityScore)
    assertEquals(StabilityLevel.IRREGULAR, result.stabilityLevel)
  }

  @Test
  fun `without scored samples there is no stability`() {
    val result = summary(listOf(s(0, 4.0), s(100, 4.0)))
    assertNull(result.stabilityScore)
    assertNull(result.stabilityLevel)
    assertEquals(0.0, result.levelShare.values.sum(), 0.0)
    assertEquals(StabilityLevel.entries.toSet(), result.levelShare.keys)
  }

  @Test
  fun `invalid samples are excluded from the metrics`() {
    val samples = listOf(s(0, 4.0), s(100, 50.0, stability = 0, valid = false), s(200, 4.0))
    val result = summary(samples)
    assertEquals(4.0, result.maxFlowGps, 0.0)
    assertNull(result.stabilityScore)
  }

  @Test
  fun `total water comes from the last valid sample`() {
    val samples = listOf(s(0, 4.0, water = 1.0), s(100, 4.0, water = 250.0), s(200, 0.0, water = -30.0, valid = false))
    assertEquals(250.0, summary(samples).totalWaterG, 0.0)
  }

  @Test
  fun `final ratio needs a dose`() {
    val samples = listOf(s(0, 4.0, water = 0.0), s(100, 4.0, water = 250.0))
    assertEquals(250.0 / 15.0, summary(samples, dose = 15.0).finalRatio!!, 1e-9)
    assertNull(summary(samples, dose = null).finalRatio)
    assertNull(summary(samples, dose = 0.0).finalRatio)
  }

  @Test
  fun `interrupted only when disconnected`() {
    val samples = listOf(s(0, 4.0), s(100, 4.0))
    assertTrue(summary(samples, reason = FinishReason.DISCONNECTED).interrupted)
    assertFalse(summary(samples, reason = FinishReason.MANUAL).interrupted)
  }
}
