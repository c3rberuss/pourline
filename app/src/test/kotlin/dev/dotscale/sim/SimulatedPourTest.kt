package dev.dotscale.sim

import dev.dotscale.brew.BrewPhase
import dev.dotscale.brew.FakeScale
import dev.dotscale.brew.FinishReason
import dev.dotscale.brew.StabilityLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SimulatedPourTest {

  /** Pasa el guion completo por una sesión, con un tick de 1 s, sin que termine por inactividad. */
  private fun run(profile: PourProfile, autoFinishMs: Long): FakeScale {
    val scale = FakeScale()
    scale.idle(500)
    val pour = SimulatedPour(profile)
    var i = 0
    while (true) {
      val flow = pour.next() ?: break
      scale.sample(flow)
      if (++i % 10 == 0) {
        scale.tick(autoFinishMs)
        assertTrue("terminó en el tick $i", scale.phase is BrewPhase.Active)
      }
    }
    scale.finish()
    return scale
  }

  @Test
  fun `the script ends and is deterministic`() {
    fun flows(profile: PourProfile) = SimulatedPour(profile).let { p -> generateSequence { p.next() }.toList() }
    val stable = flows(PourProfile.STABLE)
    assertTrue("ticks=${stable.size}", stable.size in 700..900) // ~80 s
    assertEquals(flows(PourProfile.IRREGULAR), flows(PourProfile.IRREGULAR))
    assertNull(SimulatedPour(PourProfile.STABLE).also { p -> generateSequence { p.next() }.toList() }.next())
  }

  @Test
  fun `stable profile is mostly stable with two pours and about 250 g`() {
    val summary = run(PourProfile.STABLE, autoFinishMs = 60_000).finished.summary
    assertEquals(2, summary.pours.size)
    assertEquals(250.0, summary.totalWaterG, 10.0)
    val stable = summary.levelShare.getValue(StabilityLevel.STABLE)
    assertTrue("stable=$stable", stable >= 0.7)
    assertEquals(FinishReason.MANUAL, summary.reason)
  }

  @Test
  fun `irregular profile is mostly irregular or turbulent`() {
    val summary = run(PourProfile.IRREGULAR, autoFinishMs = 60_000).finished.summary
    val notStable = summary.levelShare.getValue(StabilityLevel.IRREGULAR) +
      summary.levelShare.getValue(StabilityLevel.TURBULENT)
    assertTrue("notStable=$notStable", notStable >= 0.7)
    assertNotNull(summary.stabilityScore)
  }

  @Test
  fun `the bloom pause does not end the session with 30 s or 60 s`() {
    run(PourProfile.STABLE, autoFinishMs = 30_000)
    run(PourProfile.STABLE, autoFinishMs = 60_000)
    run(PourProfile.IRREGULAR, autoFinishMs = 30_000)
  }
}
