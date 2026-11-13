package dev.dotscale.sim

import dev.dotscale.brew.BrewFit
import dev.dotscale.brew.BrewPhase
import dev.dotscale.brew.BrewSummary
import dev.dotscale.brew.FakeScale
import dev.dotscale.brew.FinishReason
import dev.dotscale.brew.FinishedBrew
import dev.dotscale.brew.TargetConfig
import dev.dotscale.brew.TargetCurve
import dev.dotscale.brew.brewFit
import dev.dotscale.recipe.BuiltInRecipes
import dev.dotscale.recipe.FakeRecipeTexts
import dev.dotscale.recipe.v60Classic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SimulatedTargetPourTest {
  private val curve = TargetCurve.fromRecipe(v60Classic()) // 250 g, termina a los 115 s

  private class Result(val brew: FinishedBrew, val fit: BrewFit, val target: TargetCurve) {
    val summary: BrewSummary get() = brew.summary
    val waterShare: Double get() = (summary.totalWaterG - target.finalWaterG) / target.finalWaterG
    val timeDiffMs: Long get() = summary.totalTimeMs - target.endMs
  }

  /** Pasa el guion por una sesión, con un tick cada segundo y 60 s de fin automático, hasta que termine por inactividad. */
  private fun run(target: TargetCurve, mode: FollowMode, seed: Int = 42, autoFinishMs: Long = 60_000): Result {
    val scale = FakeScale()
    scale.idle(500)
    val pour = SimulatedTargetPour(target, mode, seed)
    var i = 0
    while (true) {
      val flow = pour.next() ?: break
      scale.sample(flow)
      if (++i % 10 == 0) {
        scale.tick(autoFinishMs)
        assertTrue("terminó durante el guion, en el tick $i", scale.phase is BrewPhase.Active)
      }
    }
    // Sin más vertido, la sesión termina por inactividad.
    var idleTicks = 0
    while (scale.phase is BrewPhase.Active && idleTicks < 2_000) {
      scale.sample(0.0)
      if (++idleTicks % 10 == 0) scale.tick(autoFinishMs)
    }
    assertTrue("la sesión no terminó", scale.phase is BrewPhase.Finished)
    val brew = scale.finishedBrews.single()
    assertEquals(FinishReason.INACTIVITY, brew.summary.reason)
    return Result(brew, brewFit(brew.samples, brew.summary.totalTimeMs, target, TargetConfig()), target)
  }

  @Test
  fun `the faithful profile follows the classic V60 within 3 percent, 5 seconds and fit 90`() {
    val result = run(curve, FollowMode.FAITHFUL)
    assertTrue("water ${result.summary.totalWaterG} g (${result.waterShare})", abs(result.waterShare) <= 0.03)
    assertTrue("time diff ${result.timeDiffMs} ms", abs(result.timeDiffMs) <= 5_000)
    assertTrue("fit ${result.fit.score}", result.fit.score!! >= 90)
    assertEquals(3, result.summary.pours.size)
  }

  @Test
  fun `the deviated profile is out of both tolerances and scores 60 or less`() {
    val result = run(curve, FollowMode.DEVIATED)
    assertTrue("water share ${result.waterShare}", abs(result.waterShare) > 0.03)
    assertTrue("time diff ${result.timeDiffMs} ms", abs(result.timeDiffMs) > 5_000)
    assertTrue("fit ${result.fit.score}", result.fit.score!! <= 60)
    // Vierte el 80 % del agua, y 8 s tarde.
    assertEquals(0.8 * 250.0, result.summary.totalWaterG, 5.0)
    assertEquals(8_000.0, result.timeDiffMs.toDouble(), 2_000.0)
  }

  @Test
  fun `the first stage of the deviated profile is not late`() {
    val faithful = SimulatedTargetPour(curve, FollowMode.FAITHFUL).let { p -> generateSequence { p.next() }.toList() }
    val deviated = SimulatedTargetPour(curve, FollowMode.DEVIATED).let { p -> generateSequence { p.next() }.toList() }
    // Los primeros 10 s (el bloom) vierten desde el primer tick en los dos perfiles.
    assertTrue(faithful.first() > 0.5)
    assertTrue(deviated.first() > 0.5)
    assertTrue(deviated.size > faithful.size) // termina 8 s más tarde
  }

  @Test
  fun `the same seed gives the same flows and a different one does not`() {
    fun flows(seed: Int) = SimulatedTargetPour(curve, FollowMode.FAITHFUL, seed).let { p -> generateSequence { p.next() }.toList() }
    assertEquals(flows(42), flows(42))
    assertTrue(flows(42) != flows(7))
    assertEquals(run(curve, FollowMode.FAITHFUL).summary.totalWaterG, run(curve, FollowMode.FAITHFUL).summary.totalWaterG, 0.0)
  }

  @Test
  fun `the script ends after the last stage and stays ended`() {
    val pour = SimulatedTargetPour(curve, FollowMode.FAITHFUL)
    val flows = generateSequence { pour.next() }.toList()
    // ~115 s de etapas más la cola del suavizado.
    assertTrue("ticks=${flows.size}", flows.size in 1_150..1_300)
    assertTrue(flows.last() < 0.05)
    assertNull(pour.next())
  }

  @Test
  fun `it follows a curve scaled to another dose`() {
    val scaled = curve.scaled(16.4 / 15)
    val result = run(scaled, FollowMode.FAITHFUL)
    assertTrue("water share ${result.waterShare}", abs(result.waterShare) <= 0.03)
    assertTrue("fit ${result.fit.score}", result.fit.score!! >= 90)
  }

  @Test
  fun `it follows the 4 to 6 method too`() {
    val fourSix = TargetCurve.fromRecipe(BuiltInRecipes.recipe(BuiltInRecipes.FOUR_SIX, FakeRecipeTexts()))
    val result = run(fourSix, FollowMode.FAITHFUL)
    assertTrue("water share ${result.waterShare}", abs(result.waterShare) <= 0.03)
    assertTrue("time diff ${result.timeDiffMs} ms", abs(result.timeDiffMs) <= 5_000)
    assertTrue("fit ${result.fit.score}", result.fit.score!! >= 90)
    assertEquals(5, result.summary.pours.size)
  }

  @Test
  fun `it follows a curve taken from a brew`() {
    val reference = run(curve, FollowMode.FAITHFUL)
    val fromBrew = TargetCurve.fromBrew(reference.brew.samples, reference.summary.pours, reference.summary.totalTimeMs)!!
    val result = run(fromBrew, FollowMode.FAITHFUL, seed = 7)
    assertTrue("water share ${result.waterShare}", abs(result.waterShare) <= 0.03)
    assertTrue("fit ${result.fit.score}", result.fit.score!! >= 85)
  }

  @Test
  fun `a curve whose first stage starts late still begins pouring at once`() {
    val late = TargetCurve.fromRecipe(
      dev.dotscale.recipe.recipeOf(
        dev.dotscale.recipe.stage(10, 40.0, 10),
        dev.dotscale.recipe.stage(50, 100.0, 10),
      ),
    )
    val pour = SimulatedTargetPour(late, FollowMode.FAITHFUL)
    assertTrue(pour.next()!! > 0.5)
  }
}
