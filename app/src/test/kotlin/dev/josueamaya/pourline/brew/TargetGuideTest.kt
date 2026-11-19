package dev.josueamaya.pourline.brew

import dev.josueamaya.pourline.recipe.StageType
import dev.josueamaya.pourline.recipe.recipeOf
import dev.josueamaya.pourline.recipe.stage
import dev.josueamaya.pourline.recipe.v60Classic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TargetGuideTest {
  private val config = TargetConfig()
  private val curve = TargetCurve.fromRecipe(v60Classic()) // pasos en 0, 10, 45, 65 y 90 s (vertido, espera, vertido, espera, vertido); 250 g
  private val active = BrewPhase.Active(pouring = true)

  private fun guide(tMs: Long?, waterG: Double? = null, phase: BrewPhase = active) =
    TargetGuide.at(curve, phase, tMs, waterG, config)

  @Test
  fun `armed announces the first stage and the second as next`() {
    val g = guide(tMs = null, phase = BrewPhase.Armed)
    assertEquals(0, g.stage?.index)
    assertEquals(1, g.nextStage?.index)
    assertNull(g.countdownS)
    assertNull(g.deviationG)
    assertNull(g.deviationLevel)
    assertFalse(g.allStagesDone)
  }

  @Test
  fun `active picks the stage for the clock`() {
    assertEquals(0, guide(5_000).stage?.index)
    assertEquals(1, guide(5_000).nextStage?.index)
    assertEquals(2, guide(45_000).stage?.index)
    assertEquals(3, guide(45_000).nextStage?.index)
    assertEquals(4, guide(100_000).stage?.index)
    assertNull(guide(100_000).nextStage)
  }

  @Test
  fun `in a gap the current stage is the wait and the next one is the pour`() {
    val g = guide(30_000)
    assertEquals(StageType.WAIT, g.stage?.type)
    assertEquals(1, g.stage?.index)
    assertEquals(StageType.POUR, g.nextStage?.type)
    assertEquals(2, g.nextStage?.index)
  }

  @Test
  fun `the countdown also runs before a wait`() {
    assertNull(guide(4_999).countdownS) // quedan 5001 ms hasta el WAIT de los 10 s
    assertEquals(5, guide(5_000).countdownS)
    assertEquals(1, guide(9_999).countdownS)
  }

  @Test
  fun `a final wait keeps the guide alive until it ends`() {
    val withWait = TargetCurve.fromRecipe(
      recipeOf(stage(0, 40.0, 10), stage(10, 40.0, 20, type = StageType.WAIT)),
    )
    val during = TargetGuide.at(withWait, active, 25_000, 40.0, config)
    assertFalse(during.allStagesDone)
    assertEquals(StageType.WAIT, during.stage?.type)
    assertTrue(TargetGuide.at(withWait, active, 30_000, 40.0, config).allStagesDone)
  }

  @Test
  fun `before a first stage that starts after zero there is no stage and the next is the first`() {
    val late = TargetCurve.fromRecipe(recipeOf(stage(10, 40.0, 10)))
    val early = TargetGuide.at(late, active, 3_000, 0.0, config)
    assertNull(early.stage)
    assertEquals(0, early.nextStage?.index)
    assertNull(early.countdownS) // quedan 7 s
    val soon = TargetGuide.at(late, active, 6_000, 0.0, config)
    assertNull(soon.stage)
    assertEquals(4, soon.countdownS)
  }

  @Test
  fun `finished clears everything`() {
    val summary = BrewSummary(60_000, 250.0, 4.0, 5.0, 80, StabilityLevel.STABLE, emptyMap(), emptyList(), null, FinishReason.MANUAL, false)
    val g = guide(50_000, 100.0, BrewPhase.Finished(summary, rearmed = false, pourIgnored = false))
    assertNull(g.stage)
    assertNull(g.nextStage)
    assertNull(g.countdownS)
    assertNull(g.deviationG)
    assertNull(g.deviationLevel)
    assertFalse(g.allStagesDone)
  }

  @Test
  fun `countdown runs from 5 to 1 in the last 5 seconds before the next stage`() {
    assertNull(guide(39_999).countdownS) // quedan 5001 ms
    assertEquals(5, guide(40_000).countdownS) // 5000 ms
    assertEquals(5, guide(40_999).countdownS) // 4001 ms
    assertEquals(4, guide(41_000).countdownS)
    assertEquals(2, guide(43_000).countdownS)
    assertEquals(1, guide(44_000).countdownS)
    assertEquals(1, guide(44_999).countdownS) // queda 1 ms
    assertNull(guide(45_000).countdownS) // la etapa ya empezó
    assertNull(guide(100_000).countdownS) // sin siguiente
  }

  @Test
  fun `deviation carries its sign`() {
    val above = guide(20_000, 46.0) // el objetivo vale 40 g en la meseta
    assertEquals(6.0, above.deviationG!!, 1e-9)
    val below = guide(20_000, 32.0)
    assertEquals(-8.0, below.deviationG!!, 1e-9)
  }

  @Test
  fun `the three levels hold at their limits, inclusive at 5 and 10 percent`() {
    // 250 g de objetivo: 12,5 g y 25 g.
    fun level(waterG: Double) = guide(20_000, waterG).deviationLevel
    assertEquals(DeviationLevel.OK, level(40.0))
    assertEquals(DeviationLevel.OK, level(52.5)) // +12,5 g: justo el 5 %
    assertEquals(DeviationLevel.OK, level(27.5)) // −12,5 g
    assertEquals(DeviationLevel.WARNING, level(52.6))
    assertEquals(DeviationLevel.WARNING, level(65.0)) // +25 g: justo el 10 %
    assertEquals(DeviationLevel.WARNING, level(15.0)) // −25 g
    assertEquals(DeviationLevel.ERROR, level(65.1))
    assertEquals(DeviationLevel.ERROR, level(14.9))
  }

  @Test
  fun `the limits use the final water of the scaled curve`() {
    val scaled = curve.scaled(2.0) // 500 g: el 5 % son 25 g
    val g = TargetGuide.at(scaled, active, 20_000, 80.0 + 25.0, config)
    assertEquals(25.0, g.deviationG!!, 1e-9)
    assertEquals(DeviationLevel.OK, g.deviationLevel)
  }

  @Test
  fun `an invalid sample leaves the deviation null but keeps the stage and the countdown`() {
    val g = guide(41_000, waterG = null)
    assertNull(g.deviationG)
    assertNull(g.deviationLevel)
    assertEquals(1, g.stage?.index)
    assertEquals(4, g.countdownS)
  }

  @Test
  fun `after the last stage the guide reports that all stages are done and keeps the deviation`() {
    val before = guide(114_999, 249.0)
    assertFalse(before.allStagesDone)
    val after = guide(130_000, 255.0)
    assertTrue(after.allStagesDone)
    assertEquals(4, after.stage?.index)
    assertEquals(5.0, after.deviationG!!, 1e-9)
    assertEquals(DeviationLevel.OK, after.deviationLevel)
  }
}
