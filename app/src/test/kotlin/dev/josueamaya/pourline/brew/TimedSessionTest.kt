package dev.josueamaya.pourline.brew

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** La sesión cronometrada sin báscula (SPEC-007 §3.2). */
class TimedSessionTest {

  private val amounts = PlannedAmounts(doseG = 15.0, waterG = 250.0)

  private fun armed() = TimedSession().also { it.arm(amounts) }

  private fun TimedSession.finishedBrew(effects: List<BrewEffect>): FinishedBrew =
    (effects.single() as BrewEffect.Finished).brew

  @Test
  fun `starts armed with no samples and no t0`() {
    val session = TimedSession()

    assertEquals(BrewPhase.Armed, session.state.phase)
    assertTrue(session.state.samples.isEmpty())
    assertNull(session.state.t0MonoMs)
    assertNull(session.state.currentStability)
    assertNull(session.state.currentLevel)
  }

  @Test
  fun `start moves to active with t0 now and pouring false`() {
    val session = armed()

    assertTrue(session.start(5_000).isEmpty())

    assertEquals(BrewPhase.Active(pouring = false), session.state.phase)
    assertEquals(5_000L, session.state.t0MonoMs)
    assertTrue(session.state.samples.isEmpty())
  }

  @Test
  fun `start without arm or outside armed is ignored`() {
    val unarmed = TimedSession()
    unarmed.start(1_000)
    assertEquals(BrewPhase.Armed, unarmed.state.phase)
    assertNull(unarmed.state.t0MonoMs)

    val session = armed()
    session.start(1_000)
    session.start(9_000)
    assertEquals(1_000L, session.state.t0MonoMs)

    session.finish(10_000)
    session.start(20_000)
    assertTrue(session.state.phase is BrewPhase.Finished)
  }

  @Test
  fun `finish emits a finished brew with empty samples and the plan summary`() {
    val session = armed()
    session.start(1_000)

    val brew = session.finishedBrew(session.finish(116_000))

    assertEquals(1_000L, brew.t0MonoMs)
    assertTrue(brew.samples.isEmpty())
    assertEquals(15.0, brew.doseG!!, 1e-9)
    val summary = brew.summary
    assertEquals(115_000L, summary.totalTimeMs)
    assertEquals(250.0, summary.totalWaterG, 1e-9)
    assertEquals(0.0, summary.avgFlowGps, 0.0)
    assertEquals(0.0, summary.maxFlowGps, 0.0)
    assertNull(summary.stabilityScore)
    assertNull(summary.stabilityLevel)
    assertTrue(summary.levelShare.isEmpty())
    assertTrue(summary.pours.isEmpty())
    assertEquals(250.0 / 15.0, summary.finalRatio!!, 1e-9)
    assertEquals(FinishReason.MANUAL, summary.reason)
    assertEquals(false, summary.interrupted)
    val phase = session.state.phase as BrewPhase.Finished
    assertEquals(summary, phase.summary)
    assertEquals(1_000L, session.state.t0MonoMs)
  }

  @Test
  fun `the ratio is water over dose and null without a dose`() {
    assertEquals(16.0, PlannedAmounts(15.0, 240.0).ratio!!, 1e-9)
    assertNull(PlannedAmounts(null, 240.0).ratio)
    assertNull(PlannedAmounts(0.0, 240.0).ratio)

    val session = TimedSession().also { it.arm(PlannedAmounts(null, 250.0)) }
    session.start(0)
    val brew = session.finishedBrew(session.finish(10_000))
    assertNull(brew.doseG)
    assertNull(brew.summary.finalRatio)
  }

  @Test
  fun `tick before 15 minutes does nothing`() {
    val session = armed()
    session.start(0)

    assertTrue(session.tick(899_999).isEmpty())
    assertEquals(BrewPhase.Active(pouring = false), session.state.phase)
  }

  @Test
  fun `tick at 15 minutes finishes with max duration and the total capped`() {
    val session = armed()
    session.start(2_000)

    val brew = session.finishedBrew(session.tick(2_000 + 900_000 + 700))

    assertEquals(FinishReason.MAX_DURATION, brew.summary.reason)
    assertEquals(900_000L, brew.summary.totalTimeMs)
    assertTrue(session.state.phase is BrewPhase.Finished)
    // Una vez terminada, el reloj ya no emite nada.
    assertTrue(session.tick(2_000 + 950_000).isEmpty())
  }

  @Test
  fun `a manual finish past the limit caps the total`() {
    val session = armed()
    session.start(0)

    val brew = session.finishedBrew(session.finish(2_000_000))

    assertEquals(900_000L, brew.summary.totalTimeMs)
  }

  @Test
  fun `finish outside active emits nothing`() {
    val session = armed()
    assertTrue(session.finish(1_000).isEmpty())
    assertEquals(BrewPhase.Armed, session.state.phase)

    session.start(0)
    session.finish(5_000)
    assertTrue(session.finish(6_000).isEmpty())
  }

  @Test
  fun `discard from active or finished returns to armed without emitting`() {
    val active = armed()
    active.start(0)
    assertTrue(active.discard().isEmpty())
    assertEquals(BrewPhase.Armed, active.state.phase)
    assertNull(active.state.t0MonoMs)

    val finished = armed()
    finished.start(0)
    finished.finish(5_000)
    assertTrue(finished.discard().isEmpty())
    assertEquals(BrewPhase.Armed, finished.state.phase)

    // Conserva las cantidades: se puede volver a empezar.
    finished.start(10_000)
    val brew = finished.finishedBrew(finished.finish(20_000))
    assertEquals(250.0, brew.summary.totalWaterG, 1e-9)
    assertEquals(10_000L, brew.t0MonoMs)
  }

  @Test
  fun `a finish reason other than manual or max duration is rejected`() {
    val session = armed()
    session.start(0)

    listOf(FinishReason.INACTIVITY, FinishReason.TIMER_PAUSED, FinishReason.TIMER_RESET, FinishReason.DISCONNECTED).forEach {
      assertThrows(IllegalArgumentException::class.java) { session.finish(1_000, it) }
    }
  }
}
