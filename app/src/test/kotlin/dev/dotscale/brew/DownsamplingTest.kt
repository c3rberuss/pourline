package dev.dotscale.brew

import kotlin.math.floor
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownsamplingTest {
  private fun sample(tMs: Long, flow: Double, valid: Boolean = true) =
    BrewSample(tMs, waterG = 0.0, flowGps = flow, stability = null, valid = valid)

  @Test
  fun `output has at most two points per pixel column`() {
    val samples = List(9_000) { sample(it * 100L, 4 + 3 * sin(it / 7.0)) }
    val width = 300
    val out = downsample(samples, width, xTopMs = 900_000, value = { it.flowGps })
    assertTrue("size=${out.size}", out.size <= 2 * width)
  }

  @Test
  fun `keeps the minimum and the maximum of every column`() {
    val samples = List(9_000) { sample(it * 100L, 4 + 3 * sin(it / 7.0)) }
    val width = 100
    val xTop = 900_000L
    fun column(tMs: Long) = floor(tMs.toDouble() / xTop * width).toInt().coerceIn(0, width - 1)

    val out = downsample(samples, width, xTop, value = { it.flowGps })
    val outByColumn = out.groupBy { column(it.tMs) }
    samples.groupBy { column(it.tMs) }.forEach { (col, group) ->
      val values = outByColumn.getValue(col).map { it.value }
      assertEquals("min col $col", group.minOf { it.flowGps }, values.min(), 0.0)
      assertEquals("max col $col", group.maxOf { it.flowGps }, values.max(), 0.0)
    }
    assertEquals(out.map { it.tMs }, out.map { it.tMs }.sorted())
  }

  @Test
  fun `marks a break after a gap of invalid samples`() {
    val samples = List(30) { sample(it * 100L, 4.0, valid = it !in 10..14) }
    val out = downsample(samples, widthPx = 30, xTopMs = 3_000, value = { it.flowGps })
    assertEquals(25, out.size)
    assertEquals(listOf(1_500L), out.filter { it.breakBefore }.map { it.tMs })
    assertTrue(out.none { it.tMs in 1_000..1_400 })
  }

  @Test
  fun `leading invalid samples do not create a break`() {
    val samples = List(10) { sample(it * 100L, 4.0, valid = it >= 3) }
    val out = downsample(samples, widthPx = 10, xTopMs = 1_000, value = { it.flowGps })
    assertEquals(7, out.size)
    assertFalse(out.first().breakBefore)
  }

  @Test
  fun `non positive width returns an empty list`() {
    val samples = List(10) { sample(it * 100L, 4.0) }
    assertTrue(downsample(samples, 0, 1_000, { it.flowGps }).isEmpty())
    assertTrue(downsample(samples, -5, 1_000, { it.flowGps }).isEmpty())
  }
}
