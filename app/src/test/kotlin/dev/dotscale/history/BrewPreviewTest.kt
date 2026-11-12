package dev.dotscale.history

import dev.dotscale.brew.BrewSample
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrewPreviewTest {

  private fun sample(tMs: Long, waterG: Double, valid: Boolean = true) = BrewSample(tMs, waterG, 0.0, null, valid)

  private fun Byte.u() = toInt() and 0xFF

  @Test
  fun `returns 48 values normalized to 0 to 255`() {
    val samples = (0..480).map { sample(it * 10L, it * 0.5) } // 0..240 g en 4,8 s
    val preview = BrewPreview.compute(samples, 4_800, 240.0)
    assertEquals(BrewPreview.SIZE, preview.size)
    assertEquals(255, preview.last().u())
    assertTrue(preview.map { it.u() }.zipWithNext().all { (a, b) -> a <= b })
  }

  @Test
  fun `takes the maximum of each bin`() {
    // Tramo 0 de 0 a 99 ms con totalTimeMs = 4800.
    val samples = listOf(sample(0, 10.0), sample(50, 30.0), sample(99, 20.0), sample(4_799, 100.0))
    val preview = BrewPreview.compute(samples, 4_800, 100.0)
    assertEquals(77, preview[0].u()) // 30 / 100 * 255 = 76,5
    assertEquals(255, preview[47].u())
  }

  @Test
  fun `an empty bin repeats the previous value and the start is zero`() {
    val samples = listOf(sample(2_500, 50.0), sample(4_799, 100.0))
    val preview = BrewPreview.compute(samples, 4_800, 100.0)
    assertEquals(0, preview[0].u())
    assertEquals(0, preview[24].u())
    assertEquals(128, preview[25].u())
    assertEquals(128, preview[46].u())
    assertEquals(255, preview[47].u())
  }

  @Test
  fun `samples after the total time land in the last bin and invalid ones are skipped`() {
    val samples = listOf(sample(100, 50.0), sample(5_000, 100.0), sample(200, 999.0, valid = false))
    val preview = BrewPreview.compute(samples, 4_800, 100.0)
    assertEquals(255, preview[47].u())
    assertEquals(128, preview[1].u())
  }

  @Test
  fun `zero total water gives zeros`() {
    assertArrayEquals(ByteArray(48), BrewPreview.compute(listOf(sample(0, 5.0)), 4_800, 0.0))
  }

  @Test
  fun `decode maps bytes to 0 to 1`() {
    val decoded = BrewPreview.decode(byteArrayOf(0, 51, 255.toByte()))
    assertEquals(listOf(0f, 0.2f, 1f), decoded)
  }
}
