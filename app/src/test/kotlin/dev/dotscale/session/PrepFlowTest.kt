package dev.dotscale.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PrepFlowTest {

  private val flow = PrepFlow(objectiveDoseG = 15.0)
  private var now = 0L

  /** Alimenta lecturas de [grams] cada 100 ms durante [ms] y devuelve los efectos emitidos. */
  private fun hold(grams: Double, ms: Long, valid: Boolean = true, noise: Double = 0.0): List<PrepEffect> {
    val effects = mutableListOf<PrepEffect>()
    var i = 0
    var elapsed = 0L
    while (elapsed < ms) {
      now += 100
      elapsed += 100
      val jitter = if (i++ % 2 == 0) noise else -noise
      effects += flow.onWeight(grams + jitter, valid, now)
    }
    return effects
  }

  private fun step() = flow.state.step

  private fun toDoseStep() {
    flow.start(knownDoseG = null)
    flow.onTared(now)
    assertEquals(PrepStep.DOSE, step())
  }

  private fun toTareForPour(dose: Double = 15.0) {
    toDoseStep()
    hold(dose, 2_500)
    assertEquals(PrepStep.TARE_FOR_POUR, step())
  }

  // --- Paso 1: tara ---

  @Test
  fun `it starts at the tare step`() {
    flow.start(knownDoseG = null)
    assertEquals(PrepStep.TARE, step())
    assertNull(flow.state.doseG)
    assertFalse(flow.state.waitingTare)
  }

  @Test
  fun `a tare frame confirms the tare`() {
    flow.start(null)
    assertTrue(flow.onTared(now).isEmpty())
    assertEquals(PrepStep.DOSE, step())
  }

  @Test
  fun `tare in the app sends the tare and waits for its confirmation`() {
    flow.start(null)
    assertEquals(listOf(PrepEffect.SendTare), flow.tare(now))
    assertTrue(flow.state.waitingTare)
    assertEquals(PrepStep.TARE, step())
  }

  @Test
  fun `after tare in the app the weight at zero for a second confirms it`() {
    flow.start(null)
    hold(80.0, 300)
    flow.tare(now)
    hold(0.0, 700)
    assertEquals(PrepStep.TARE, step()) // aún no ha pasado 1 s en cero
    hold(0.0, 500)
    assertEquals(PrepStep.DOSE, step())
    assertFalse(flow.state.waitingTare)
  }

  @Test
  fun `after tare in the app a weight that never reaches zero does not confirm`() {
    flow.start(null)
    hold(80.0, 300)
    flow.tare(now)
    hold(80.0, 6_000)
    assertEquals(PrepStep.TARE, step())
    assertFalse(flow.state.waitingTare) // el plazo de 5 s caducó
  }

  @Test
  fun `the physical tare button is detected as a drop from at least 1 g to a stable zero`() {
    flow.start(null)
    hold(120.0, 500)
    hold(0.0, 500)
    assertEquals(PrepStep.TARE, step())
    hold(0.0, 700)
    assertEquals(PrepStep.DOSE, step())
  }

  @Test
  fun `a weight that stays at zero without a transition or a tare does not confirm`() {
    flow.start(null)
    hold(0.0, 10_000)
    assertEquals(PrepStep.TARE, step())
  }

  @Test
  fun `a zero below 1 g before is not a transition`() {
    flow.start(null)
    hold(0.5, 500)
    hold(0.0, 3_000)
    assertEquals(PrepStep.TARE, step())
  }

  @Test
  fun `skip moves from the first tare to the dose`() {
    flow.start(null)
    flow.skip()
    assertEquals(PrepStep.DOSE, step())
  }

  @Test
  fun `a tare frame outside the tare steps is ignored`() {
    toDoseStep()
    assertTrue(flow.onTared(now).isEmpty())
    assertEquals(PrepStep.DOSE, step())
  }

  // --- Paso 2: dosis ---

  @Test
  fun `a stable dose is captured after two seconds and not before`() {
    toDoseStep()
    val early = hold(15.0, 1_800)
    assertTrue(early.isEmpty())
    assertEquals(PrepStep.DOSE, step())
    assertTrue(flow.state.stableProgress in 0.8f..1f)

    val effects = hold(15.0, 400)
    assertEquals(listOf(PrepEffect.SetDose(15.0)), effects)
    assertEquals(PrepStep.TARE_FOR_POUR, step())
    assertEquals(15.0, flow.state.doseG!!, 1e-9)
  }

  @Test
  fun `the dose is the mean of the window rounded to a tenth`() {
    toDoseStep()
    val effects = hold(15.04, 2_500)
    assertEquals(listOf(PrepEffect.SetDose(15.0)), effects)
    assertEquals(PrepStep.TARE_FOR_POUR, step())
  }

  @Test
  fun `the first spoonful under half of the target dose is not captured`() {
    toDoseStep()
    val effects = hold(5.0, 6_000)
    assertTrue(effects.isEmpty())
    assertEquals(PrepStep.DOSE, step())
    assertEquals(0f, flow.state.stableProgress)
  }

  @Test
  fun `noise of 0,05 g does not break the stability`() {
    toDoseStep()
    val effects = hold(15.0, 2_500, noise = 0.05)
    assertEquals(listOf(PrepEffect.SetDose(15.0)), effects)
  }

  @Test
  fun `a weight that keeps changing never stabilizes`() {
    toDoseStep()
    var grams = 8.0
    repeat(80) {
      now += 100
      grams += 0.4 // 4 g/s: no hay 2 s dentro de 0,3 g
      assertTrue(flow.onWeight(grams, valid = true, now).isEmpty())
    }
    assertEquals(PrepStep.DOSE, step())
  }

  @Test
  fun `an invalid reading restarts the window`() {
    toDoseStep()
    hold(15.0, 1_500)
    hold(15.0, 100, valid = false)
    assertEquals(0f, flow.state.stableProgress)
    val effects = hold(15.0, 1_500)
    assertTrue(effects.isEmpty())
    assertEquals(PrepStep.DOSE, step())
  }

  @Test
  fun `capture now takes the current weight without waiting`() {
    toDoseStep()
    assertEquals(listOf(PrepEffect.SetDose(12.3)), flow.captureNow(12.34))
    assertEquals(PrepStep.TARE_FOR_POUR, step())
    assertEquals(12.3, flow.state.doseG!!, 1e-9)
  }

  @Test
  fun `capture now with no weight does nothing`() {
    toDoseStep()
    assertTrue(flow.captureNow(0.0).isEmpty())
    assertEquals(PrepStep.DOSE, step())
  }

  @Test
  fun `skipping the dose goes on without a dose`() {
    toDoseStep()
    assertTrue(flow.skipDose().isEmpty())
    assertEquals(PrepStep.TARE_FOR_POUR, step())
    assertNull(flow.state.doseG)
    assertTrue(flow.state.doseSkipped)
  }

  @Test
  fun `a known dose makes the dose step a given`() {
    flow.start(knownDoseG = 18.0)
    flow.onTared(now)
    assertEquals(PrepStep.TARE_FOR_POUR, step())
    assertEquals(18.0, flow.state.doseG!!, 1e-9)
  }

  // --- Paso 3: tara para verter ---

  @Test
  fun `a stable weight different from the dose replaces it before the second tare`() {
    toTareForPour(dose = 12.0)
    val effects = hold(15.5, 2_500)
    assertEquals(listOf(PrepEffect.SetDose(15.5)), effects)
    assertEquals(15.5, flow.state.doseG!!, 1e-9)
    assertEquals(PrepStep.TARE_FOR_POUR, step())
  }

  @Test
  fun `a stable weight within 0,3 g of the dose does not replace it`() {
    toTareForPour(dose = 15.0)
    val effects = hold(15.2, 3_000)
    assertTrue(effects.isEmpty())
    assertEquals(15.0, flow.state.doseG!!, 1e-9)
  }

  @Test
  fun `a weight over 150 percent of the target is water and is never captured as coffee`() {
    toTareForPour(dose = 15.0)
    val effects = hold(30.0, 3_000)
    assertTrue(effects.isEmpty())
    assertEquals(15.0, flow.state.doseG!!, 1e-9)
  }

  @Test
  fun `there is no recapture when the dose was skipped`() {
    toDoseStep()
    flow.skipDose()
    val effects = hold(15.0, 3_000)
    assertTrue(effects.isEmpty())
    assertNull(flow.state.doseG)
  }

  @Test
  fun `no recapture while the tare is in flight`() {
    toTareForPour(dose = 12.0)
    flow.tare(now)
    val effects = hold(15.5, 1_500)
    assertTrue(effects.isEmpty())
    assertEquals(12.0, flow.state.doseG!!, 1e-9)
  }

  @Test
  fun `the second tare by frame reaches the pour step and arms`() {
    toTareForPour()
    assertEquals(listOf(PrepEffect.Arm), flow.onTared(now))
    assertEquals(PrepStep.POUR, step())
  }

  @Test
  fun `the second tare by weight at zero reaches the pour step and arms`() {
    toTareForPour()
    flow.tare(now)
    val effects = hold(0.0, 1_500)
    assertEquals(listOf(PrepEffect.Arm), effects)
    assertEquals(PrepStep.POUR, step())
  }

  @Test
  fun `skip in the second tare reaches the pour step and arms`() {
    toTareForPour()
    assertEquals(listOf(PrepEffect.Arm), flow.skip())
    assertEquals(PrepStep.POUR, step())
  }

  // --- Paso 4 ---

  @Test
  fun `start timer emits start now only at the pour step`() {
    toTareForPour()
    assertTrue(flow.startTimer().isEmpty())
    flow.skip()
    assertEquals(listOf(PrepEffect.StartNow), flow.startTimer())
  }

  @Test
  fun `weights are ignored once at the pour step`() {
    toTareForPour()
    flow.skip()
    assertTrue(hold(80.0, 3_000).isEmpty())
    assertEquals(PrepStep.POUR, step())
  }

  // --- Desconexión ---

  @Test
  fun `a disconnection returns to the first step keeping the dose and flags the interruption`() {
    toTareForPour(dose = 14.0)
    flow.onDisconnected()
    assertEquals(PrepStep.TARE, step())
    assertEquals(14.0, flow.state.doseG!!, 1e-9)
    assertTrue(flow.state.interrupted)
  }

  @Test
  fun `a disconnection at the pour step also returns to the first step`() {
    toTareForPour(dose = 14.0)
    flow.skip()
    flow.onDisconnected()
    assertEquals(PrepStep.TARE, step())
  }

  @Test
  fun `reconnecting returns to the first step with the dose and clears the interruption`() {
    toTareForPour(dose = 14.0)
    flow.onDisconnected()
    flow.onReconnected()
    assertEquals(PrepStep.TARE, step())
    assertEquals(14.0, flow.state.doseG!!, 1e-9)
    assertFalse(flow.state.interrupted)
    // Con la dosis conservada, tras la tara se salta el paso de la dosis.
    flow.onTared(now)
    assertEquals(PrepStep.TARE_FOR_POUR, step())
  }

  @Test
  fun `forgetting the dose clears it`() {
    toTareForPour(dose = 14.0)
    flow.forgetDose()
    assertNull(flow.state.doseG)
  }
}
