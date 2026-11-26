package dev.josueamaya.pourline.ui.session

import dev.josueamaya.pourline.brew.BrewSample
import dev.josueamaya.pourline.brew.StabilityLevel
import dev.josueamaya.pourline.brew.TargetCurve
import dev.josueamaya.pourline.recipe.StageType
import dev.josueamaya.pourline.recipe.fourSixClassic
import dev.josueamaya.pourline.recipe.recipeOf
import dev.josueamaya.pourline.recipe.stage
import dev.josueamaya.pourline.recipe.v60Classic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrewDialTest {

  @Test
  fun `the segments of the classic V60 are at 40 and 150 over 250`() {
    val segments = dialSegments(TargetCurve.fromRecipe(v60Classic()))
    assertEquals(listOf(0.16f, 0.6f), segments)
  }

  @Test
  fun `swirls and waits add no segment`() {
    val recipe = recipeOf(
      stage(0, 50.0, 10, "Bloom"),
      stage(10, 0.0, 10, type = StageType.SWIRL),
      stage(20, 0.0, 15, type = StageType.WAIT),
      stage(35, 200.0, 20, "Vertido"),
    )
    assertEquals(listOf(0.25f), dialSegments(TargetCurve.fromRecipe(recipe)))
  }

  @Test
  fun `the four six recipe has one boundary per pour but the last`() {
    val recipe = fourSixClassic()
    val pours = recipe.stages.count { it.type == StageType.POUR }
    assertEquals(pours - 1, dialSegments(TargetCurve.fromRecipe(recipe)).size)
  }

  @Test
  fun `a curve without water has no segments`() {
    val curve = TargetCurve.fromRecipe(recipeOf(stage(0, 0.0, 10, type = StageType.WAIT)))
    assertTrue(dialSegments(curve).isEmpty())
  }

  @Test
  fun `a scaled curve keeps the same proportions`() {
    val curve = TargetCurve.fromRecipe(v60Classic()).scaled(1.2)
    assertEquals(listOf(0.16f, 0.6f), dialSegments(curve))
  }

  @Test
  fun `the fraction is water over target`() {
    assertEquals(0.5f, dialFraction(125.0, 250.0), 1e-6f)
    assertEquals(0f, dialFraction(0.0, 250.0), 0f)
  }

  @Test
  fun `the ring saturates at one hundred percent`() {
    assertEquals(1f, dialFraction(250.0, 250.0), 0f)
    assertEquals(1f, dialFraction(310.0, 250.0), 0f)
  }

  @Test
  fun `a negative water or a missing target give an empty ring`() {
    assertEquals(0f, dialFraction(-3.0, 250.0), 0f)
    assertEquals(0f, dialFraction(100.0, null), 0f)
    assertEquals(0f, dialFraction(100.0, 0.0), 0f)
  }

  @Test
  fun `the spoken percent is not saturated`() {
    assertEquals(61, dialPercent(152.0, 250.0))
    assertEquals(124, dialPercent(310.0, 250.0))
    assertEquals(0, dialPercent(10.0, null))
  }

  private fun sample(tMs: Long, waterG: Double, stability: Int? = null, valid: Boolean = true) =
    BrewSample(tMs, waterG, flowGps = 0.0, stability = stability, valid = valid)

  @Test
  fun `the stretches follow the stability of each sample and merge the same level`() {
    val samples = listOf(
      sample(0, 10.0),
      sample(100, 20.0),
      sample(200, 50.0, stability = 90),
      sample(300, 100.0, stability = 85),
      sample(400, 150.0, stability = 30),
    )
    assertEquals(
      listOf(
        DialStretch(0.1f, null),
        DialStretch(0.5f, StabilityLevel.STABLE),
        DialStretch(0.75f, StabilityLevel.TURBULENT),
      ),
      dialStretches(samples, 200.0),
    )
  }

  @Test
  fun `water going down or invalid samples paint nothing`() {
    val samples = listOf(
      sample(0, 100.0, stability = 90),
      sample(100, 80.0, stability = 30),
      sample(200, 180.0, stability = 30, valid = false),
      sample(300, 100.0, stability = 60),
      sample(400, 120.0, stability = 60),
    )
    assertEquals(
      listOf(DialStretch(0.5f, StabilityLevel.STABLE), DialStretch(0.6f, StabilityLevel.IRREGULAR)),
      dialStretches(samples, 200.0),
    )
  }

  @Test
  fun `the stretches saturate at one hundred percent`() {
    val samples = listOf(sample(0, 150.0, stability = 90), sample(100, 300.0, stability = 30), sample(200, 320.0, stability = 90))
    assertEquals(
      listOf(DialStretch(0.75f, StabilityLevel.STABLE), DialStretch(1f, StabilityLevel.TURBULENT)),
      dialStretches(samples, 200.0),
    )
  }

  @Test
  fun `without a target there are no stretches`() {
    assertTrue(dialStretches(listOf(sample(0, 50.0, stability = 90)), null).isEmpty())
    assertTrue(dialStretches(listOf(sample(0, 50.0, stability = 90)), 0.0).isEmpty())
  }

  @Test
  fun `the sparkline window keeps the last thirty seconds ending at the right edge`() {
    val samples = (0..60).map { sample(it * 1_000L, it.toDouble()) }
    val window = sparklineWindow(samples)
    assertEquals(31, window.size)
    assertEquals(0L, window.first().tMs)
    assertEquals(30.0, window.first().waterG, 0.0)
    assertEquals(SPARKLINE_WINDOW_MS, window.last().tMs)
  }

  @Test
  fun `a short brew sits at the right of the sparkline and keeps its gaps`() {
    val samples = listOf(sample(0, 0.0), sample(1_000, 5.0, valid = false), sample(2_000, 10.0))
    val window = sparklineWindow(samples)
    assertEquals(listOf(28_000L, 29_000L, 30_000L), window.map { it.tMs })
    assertEquals(listOf(true, false, true), window.map { it.valid })
    assertTrue(sparklineWindow(emptyList()).isEmpty())
  }
}
