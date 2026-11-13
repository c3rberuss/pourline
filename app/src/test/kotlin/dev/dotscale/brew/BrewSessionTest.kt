package dev.dotscale.brew

import dev.dotscale.protocol.TimerStatus
import dev.dotscale.protocol.WeightUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class BrewSessionTest {

  private fun FakeScale.assertActive(pouring: Boolean) = assertEquals(BrewPhase.Active(pouring), phase)

  /** Un vertido típico de 3 s a 4 g/s tras 500 ms de reposo. */
  private fun FakeScale.simplePour(ms: Int = 3_000) {
    idle(500)
    pour(4.0, ms)
  }

  // --- Inicio ---

  @Test
  fun `starts after 500 ms above the threshold with t0 at the first sample of the streak`() {
    val scale = FakeScale()
    scale.idle(1_000)
    assertEquals(BrewPhase.Armed, scale.phase)

    scale.pour(4.0, 500) // 5 muestras: la racha dura 400 ms, aún no se confirma
    assertEquals(BrewPhase.Armed, scale.phase)
    assertTrue(scale.state.samples.isEmpty())

    scale.pour(4.0, 100) // 6.ª muestra: 500 ms
    scale.assertActive(pouring = true)
    val samples = scale.state.samples
    assertEquals(6, samples.size)
    assertEquals(0L, samples.first().tMs)
    assertEquals(500L, samples.last().tMs)
  }

  @Test
  fun `a spike shorter than 500 ms does not start a session`() {
    val scale = FakeScale()
    scale.idle(500)
    scale.pour(8.0, 400)
    scale.idle(1_000)
    scale.pour(8.0, 400)
    assertEquals(BrewPhase.Armed, scale.phase)
    assertTrue(scale.state.samples.isEmpty())
    assertEquals(0, scale.startTimerCount)
  }

  @Test
  fun `without a previous tare the water starts at zero`() {
    val scale = FakeScale()
    scale.grossG = 50.0 // taza y café ya sobre la báscula
    scale.idle(1_000)
    scale.pour(4.0, 3_000)
    val samples = scale.state.samples
    assertTrue("first=${samples.first().waterG}", samples.first().waterG < 1.0)
    assertEquals(12.0, samples.last().waterG, 0.5)
  }

  @Test
  fun `a tare while armed discards the previous weight as baseline`() {
    val scale = FakeScale()
    scale.grossG = 50.0
    scale.idle(500)
    scale.tare()
    scale.pour(4.0, 1_000)
    scale.assertActive(pouring = true)
    assertEquals(0.0, scale.state.samples.first().waterG, 1e-9)
  }

  @Test
  fun `a tare while active keeps the water curve continuous`() {
    val scale = FakeScale()
    scale.simplePour(2_000)
    scale.tare()
    scale.pour(4.0, 2_000)
    val water = scale.state.samples.map { it.waterG }
    assertTrue(water.zipWithNext().all { (a, b) -> b - a < 1.0 && b >= a - 0.2 })
    assertEquals(16.0, water.last(), 0.5)
  }

  // --- Vertidos ---

  @Test
  fun `a pour closes after 2 s below the threshold and the next one is counted apart`() {
    val scale = FakeScale()
    scale.simplePour(3_000)
    scale.idle(1_900)
    scale.assertActive(pouring = true)
    scale.idle(100)
    scale.assertActive(pouring = false)

    scale.pour(4.0, 3_000)
    scale.assertActive(pouring = true)
    scale.finish()
    val pours = scale.finished.summary.pours
    assertEquals(2, pours.size)
    assertEquals(listOf(1, 2), pours.map { it.index })
    assertEquals(0L, pours[0].startMs)
    assertEquals(2_900L, pours[0].endMs)
    assertEquals(12.0, pours[0].waterG, 0.5)
    assertEquals(12.0, pours[1].waterG, 0.5)
    assertTrue(pours[1].startMs > pours[0].endMs)
  }

  @Test
  fun `a dip shorter than 2 s does not split the pour`() {
    val scale = FakeScale()
    scale.simplePour(3_000)
    scale.idle(1_500)
    scale.pour(4.0, 3_000)
    scale.finish()
    assertEquals(1, scale.finished.summary.pours.size)
  }

  @Test
  fun `the tick closes the open pour when notifications stop`() {
    val scale = FakeScale()
    scale.simplePour(3_000)
    scale.advance(2_100)
    scale.tick()
    scale.assertActive(pouring = false)
    assertNull(scale.state.currentStability)
  }

  // --- Estabilidad ---

  @Test
  fun `the ramp is excluded and the indicator is measuring`() {
    val scale = FakeScale()
    scale.idle(500)
    scale.pour(4.0, 1_600) // t = 0..1500: rampa hasta 1000, ventana corta después
    scale.assertActive(pouring = true)
    assertNull(scale.state.currentStability)
    assertNull(scale.state.currentLevel)

    scale.pour(4.0, 1_000) // t = 1600..2500
    assertEquals(100, scale.state.currentStability)
    assertEquals(StabilityLevel.STABLE, scale.state.currentLevel)

    val samples = scale.state.samples
    assertTrue(samples.filter { it.tMs < 2_000 }.all { it.stability == null })
    assertTrue(samples.filter { it.tMs >= 2_000 }.all { it.stability == 100 })
  }

  @Test
  fun `the window never mixes pours`() {
    val scale = FakeScale()
    scale.idle(500)
    scale.pour(4.0, 5_000)
    scale.idle(2_000)
    scale.pour(8.0, 5_000)
    val scored = scale.state.samples.filter { it.stability != null }
    assertTrue(scored.isNotEmpty())
    assertTrue("scores=${scored.map { it.stability }.distinct()}", scored.all { it.stability == 100 })
    assertTrue(scored.any { it.flowGps == 8.0 })
    assertTrue(scored.any { it.flowGps == 4.0 })
  }

  @Test
  fun `an irregular pour scores low`() {
    val scale = FakeScale()
    scale.idle(500)
    scale.pourWith(6_000) { if (it % 2 == 0) 2.0 else 6.0 }
    scale.finish()
    val summary = scale.finished.summary
    assertEquals(StabilityLevel.TURBULENT, summary.stabilityLevel)
    assertTrue(summary.levelShare.getValue(StabilityLevel.TURBULENT) > 0.9)
  }

  @Test
  fun `samples under the threshold inside a pour keep the pour open and are not scored`() {
    val scale = FakeScale()
    scale.simplePour(3_000)
    scale.idle(1_000)
    scale.assertActive(pouring = true)
    assertEquals(100, scale.state.currentStability) // D2: conserva la última puntuación
    assertTrue(scale.state.samples.takeLast(10).all { it.stability == null })
  }

  // --- Fin ---

  @Test
  fun `finishes by inactivity at the end of the last pour`() {
    val scale = FakeScale()
    scale.simplePour(3_000)
    scale.advance(59_000)
    scale.tick(60_000)
    scale.assertActive(pouring = false)
    scale.advance(2_000)
    scale.tick(60_000)
    val summary = scale.finished.summary
    assertEquals(FinishReason.INACTIVITY, summary.reason)
    assertEquals(2_900L, summary.totalTimeMs)
    assertFalse(summary.interrupted)
  }

  @Test
  fun `the auto finish setting is applied on the next tick`() {
    val scale = FakeScale()
    scale.simplePour(3_000)
    scale.advance(31_000)
    scale.tick(60_000)
    scale.assertActive(pouring = false)
    scale.tick(30_000)
    assertEquals(FinishReason.INACTIVITY, scale.finished.summary.reason)
  }

  @Test
  fun `finishes when the timer is paused`() {
    val scale = FakeScale()
    scale.simplePour(2_000)
    scale.timerEvent(TimerStatus.PAUSED)
    val summary = scale.finished.summary
    assertEquals(FinishReason.TIMER_PAUSED, summary.reason)
    assertEquals(1_900L, summary.totalTimeMs)
  }

  @Test
  fun `finishes when the timer is reset and does not rearm in the same event`() {
    val scale = FakeScale()
    scale.simplePour(2_000)
    scale.timerEvent(TimerStatus.RESET)
    val finished = scale.finished
    assertEquals(FinishReason.TIMER_RESET, finished.summary.reason)
    assertEquals(1_900L, finished.summary.totalTimeMs)
    assertFalse(finished.rearmed)
  }

  @Test
  fun `finishes manually`() {
    val scale = FakeScale()
    scale.simplePour(2_000)
    scale.finish()
    assertEquals(FinishReason.MANUAL, scale.finished.summary.reason)
    assertEquals(1_900L, scale.finished.summary.totalTimeMs)
  }

  @Test
  fun `a disconnection finishes as interrupted at the last sample`() {
    val scale = FakeScale()
    scale.simplePour(2_000)
    scale.advance(5_000)
    scale.disconnect()
    val summary = scale.finished.summary
    assertEquals(FinishReason.DISCONNECTED, summary.reason)
    assertEquals(1_900L, summary.totalTimeMs)
    assertTrue(summary.interrupted)
  }

  @Test
  fun `finishes at the maximum duration`() {
    val scale = FakeScale(config = BrewConfig(maxDurationMs = 2_000))
    scale.idle(500)
    scale.pour(4.0, 3_000)
    val summary = scale.finished.summary
    assertEquals(FinishReason.MAX_DURATION, summary.reason)
    assertEquals(2_000L, summary.totalTimeMs)
  }

  @Test
  fun `the tick also enforces the maximum duration`() {
    val scale = FakeScale(config = BrewConfig(maxDurationMs = 10_000))
    scale.simplePour(3_000)
    scale.advance(8_000)
    scale.tick(60_000)
    val summary = scale.finished.summary
    assertEquals(FinishReason.MAX_DURATION, summary.reason)
    assertEquals(10_000L, summary.totalTimeMs)
  }

  @Test
  fun `the idle trail after the end is trimmed`() {
    val scale = FakeScale()
    scale.simplePour(3_000)
    scale.idle(10_000)
    scale.tick(5_000)
    val finished = scale.finished
    assertEquals(FinishReason.INACTIVITY, finished.summary.reason)
    val total = finished.summary.totalTimeMs
    assertEquals(2_900L, total)
    assertTrue(scale.state.samples.all { it.tMs <= total + 2_000 })
    assertEquals(scale.state.samples.last().waterG, finished.summary.totalWaterG, 1e-9)
  }

  @Test
  fun `finishing does nothing outside an active session`() {
    val scale = FakeScale()
    scale.finish()
    scale.timerEvent(TimerStatus.PAUSED)
    scale.tick()
    assertEquals(BrewPhase.Armed, scale.phase)
  }

  // --- Vertido tras el fin y rearme ---

  @Test
  fun `a pour after the end without rearming is ignored`() {
    val scale = FakeScale()
    scale.simplePour(2_000)
    scale.finish()
    val before = scale.state

    scale.idle(1_000)
    scale.pour(4.0, 1_000)
    val after = scale.finished
    assertTrue(after.pourIgnored)
    assertFalse(after.rearmed)
    assertEquals(before.samples, scale.state.samples)
    assertEquals((before.phase as BrewPhase.Finished).summary, after.summary)
  }

  @Test
  fun `a tare rearms without clearing the summary and the next pour starts a new chart`() {
    val scale = FakeScale()
    scale.simplePour(2_000)
    scale.finish()
    val previousSamples = scale.state.samples
    val previousSummary = scale.finished.summary

    scale.tare()
    assertTrue(scale.finished.rearmed)
    assertEquals(previousSamples, scale.state.samples)
    assertEquals(previousSummary, scale.finished.summary)

    scale.idle(500)
    scale.pour(4.0, 1_000)
    scale.assertActive(pouring = true)
    assertEquals(0L, scale.state.samples.first().tMs)
    assertTrue(scale.state.samples.size < previousSamples.size)
  }

  @Test
  fun `a timer reset rearms a finished session`() {
    val scale = FakeScale()
    scale.simplePour(2_000)
    scale.finish()
    scale.idle(1_000)
    scale.pour(4.0, 1_000)
    assertTrue(scale.finished.pourIgnored)

    scale.timerEvent(TimerStatus.RESET)
    val finished = scale.finished
    assertTrue(finished.rearmed)
    assertFalse(finished.pourIgnored)
  }

  @Test
  fun `a reset that ended the session needs another reset to rearm`() {
    val scale = FakeScale()
    scale.simplePour(2_000)
    scale.timerEvent(TimerStatus.RESET)
    assertFalse(scale.finished.rearmed)
    scale.timerEvent(TimerStatus.RESET)
    assertTrue(scale.finished.rearmed)
  }

  @Test
  fun `a tare while finished discards the previous weight as baseline`() {
    val scale = FakeScale()
    scale.simplePour(2_000)
    scale.finish()
    scale.tare()
    scale.pour(4.0, 1_000)
    scale.assertActive(pouring = true)
    assertEquals(0.0, scale.state.samples.first().waterG, 1e-9)
  }

  // --- Unidades y muestras inválidas ---

  @Test
  fun `ounces are converted to grams`() {
    val scale = FakeScale(unit = WeightUnit.OUNCES)
    scale.simplePour(3_000)
    val samples = scale.state.samples
    assertEquals(12.0, samples.last().waterG, 0.6)
    // 4 g/s = 0,1411 oz/s → 0,14 oz/s tras cuantizar.
    assertEquals(0.14 * GRAMS_PER_OUNCE, samples.last().flowGps, 1e-9)
  }

  @Test
  fun `invalid samples are kept as gaps and excluded from the metrics`() {
    val scale = FakeScale()
    scale.simplePour(3_000)
    repeat(3) { scale.sample(flowGps = 50.0, outOfRange = true) }
    scale.sample(flowGps = 4.0, netOverrideG = -5.0)
    scale.pour(4.0, 3_000)
    scale.finish()

    val invalid = scale.state.samples.filter { !it.valid }
    assertEquals(4, invalid.size)
    assertTrue(invalid.all { it.stability == null })
    assertEquals(4.0, scale.finished.summary.maxFlowGps, 0.0)
    assertEquals(1, scale.finished.summary.pours.size)
  }

  @Test
  fun `small negative noise after a tare is still valid`() {
    val scale = FakeScale()
    scale.simplePour(2_000)
    scale.sample(flowGps = 0.0, netOverrideG = -0.5)
    assertTrue(scale.state.samples.last().valid)
  }

  // --- Efectos ---

  @Test
  fun `start timer is emitted once per session`() {
    val scale = FakeScale()
    scale.simplePour(10_000)
    assertEquals(1, scale.startTimerCount)

    scale.finish()
    scale.tare()
    scale.timer = TimerStatus.RESET
    scale.idle(500)
    scale.pour(4.0, 3_000)
    assertEquals(2, scale.startTimerCount)
  }

  @Test
  fun `start timer is not emitted when the timer is already running`() {
    val scale = FakeScale()
    scale.timer = TimerStatus.RUNNING
    scale.simplePour(3_000)
    assertEquals(0, scale.startTimerCount)
    scale.assertActive(pouring = true)
  }

  // --- Dosis y publicación ---

  @Test
  fun `the dose set before finishing gives the final ratio`() {
    val scale = FakeScale()
    scale.session.setDoseGrams(2.0)
    scale.simplePour(3_000)
    scale.finish()
    assertEquals(scale.finished.summary.totalWaterG / 2.0, scale.finished.summary.finalRatio!!, 1e-9)
  }

  @Test
  fun `the dose set after finishing does not change the summary`() {
    val scale = FakeScale()
    scale.simplePour(3_000)
    scale.finish()
    scale.session.setDoseGrams(15.0)
    assertNull(scale.finished.summary.finalRatio)
  }

  @Test
  fun `the state instance is reused while nothing changes`() {
    val scale = FakeScale()
    val initial = scale.state
    scale.idle(1_000)
    assertSame(initial, scale.state)

    scale.simplePour(2_000)
    scale.finish()
    val finished = scale.state
    scale.session.setDoseGrams(10.0)
    scale.session.onTick(scale.nowMs, 60_000)
    scale.timerEvent(TimerStatus.PAUSED)
    assertSame(finished, scale.state)
    assertNotNull(scale.finished)
  }

  // --- Fin de sesión y temporizador (SPEC-002 §3.5) ---

  private fun FakeScale.activePour() {
    simplePour(3_000)
    assertActive(pouring = true)
  }

  @Test
  fun `finished is emitted exactly once with each of the six reasons`() {
    val cases: List<Pair<FinishReason, (FakeScale) -> Unit>> = listOf(
      FinishReason.INACTIVITY to { s -> s.advance(61_000); s.tick(60_000) },
      FinishReason.TIMER_PAUSED to { s -> s.timerEvent(TimerStatus.PAUSED) },
      FinishReason.TIMER_RESET to { s -> s.timerEvent(TimerStatus.RESET) },
      FinishReason.MANUAL to { s -> s.finish() },
      FinishReason.DISCONNECTED to { s -> s.disconnect() },
      FinishReason.MAX_DURATION to { s -> s.advance(1_000_000); s.tick(100_000_000) },
    )
    for ((reason, trigger) in cases) {
      val scale = FakeScale()
      scale.activePour()
      trigger(scale)
      // Eventos posteriores no vuelven a emitir `Finished`.
      scale.finish()
      scale.tick()
      scale.disconnect()
      scale.timerEvent(TimerStatus.PAUSED)

      val brew = scale.finishedBrews.single()
      assertEquals(reason.name, reason, brew.summary.reason)
      assertEquals(reason.name, scale.finished.summary, brew.summary)
    }
  }

  @Test
  fun `finished carries the trimmed samples, t0 and the dose`() {
    val scale = FakeScale(startMs = 10_000)
    scale.session.setDoseGrams(20.0)
    scale.idle(500)
    scale.pour(4.0, 3_000)
    scale.advance(30_000)
    scale.sample(0.0)
    val t0 = 10_000 + 500 + FakeScale.STEP_MS
    scale.finish()

    val brew = scale.finishedBrews.single()
    assertEquals(t0, brew.t0MonoMs)
    assertEquals(20.0, brew.doseG!!, 1e-9)
    assertEquals(scale.state.samples, brew.samples)
    assertTrue(brew.samples.last().tMs <= brew.summary.totalTimeMs + BrewConfig().pourGapMs)
  }

  @Test
  fun `pause timer is sent with inactivity, manual and max duration when the timer is running`() {
    val cases: List<(FakeScale) -> Unit> = listOf(
      { s -> s.advance(61_000); s.tick(60_000) },
      { s -> s.finish() },
      { s -> s.advance(1_000_000); s.tick(100_000_000) },
    )
    for (trigger in cases) {
      val scale = FakeScale()
      scale.activePour()
      assertEquals(TimerStatus.RUNNING, scale.timer)
      trigger(scale)
      assertEquals(1, scale.pauseTimerCount)
      assertEquals(TimerStatus.PAUSED, scale.timer)
      // PauseTimer va antes de Finished.
      assertEquals(listOf(BrewEffect.PauseTimer), scale.effects.filter { it == BrewEffect.PauseTimer })
      assertTrue(scale.effects.indexOf(BrewEffect.PauseTimer) < scale.effects.indexOfFirst { it is BrewEffect.Finished })
    }
  }

  @Test
  fun `pause timer is not sent when the timer was paused, reset or the scale disconnected`() {
    for (trigger in listOf<(FakeScale) -> Unit>(
      { s -> s.timerEvent(TimerStatus.PAUSED) },
      { s -> s.timerEvent(TimerStatus.RESET) },
      { s -> s.disconnect() },
    )) {
      val scale = FakeScale()
      scale.activePour()
      trigger(scale)
      assertEquals(0, scale.pauseTimerCount)
    }
  }

  @Test
  fun `pause timer is not sent at the end when the timer is not running`() {
    val scale = FakeScale()
    scale.timer = TimerStatus.RUNNING
    scale.simplePour(3_000) // la DOT lo arrancó sola: no hay StartTimer
    scale.timerEvent(TimerStatus.PAUSED) // el usuario lo pausa y la sesión termina...
    assertEquals(0, scale.pauseTimerCount)

    val other = FakeScale()
    other.activePour()
    other.timer = TimerStatus.PAUSED // el último estado conocido ya no es RUNNING
    other.sample(4.0)
    other.finish()
    assertEquals(0, other.pauseTimerCount)
  }

  @Test
  fun `a tare that rearms a finished session sends reset timer once`() {
    val scale = FakeScale()
    scale.activePour()
    scale.finish() // PauseTimer: el reloj queda en PAUSED
    assertEquals(0, scale.resetTimerCount)

    scale.tare()
    assertEquals(1, scale.resetTimerCount)
    assertEquals(TimerStatus.RESET, scale.timer)
    assertTrue(scale.finished.rearmed)

    scale.tare() // el reloj ya está en RESET: no se repite
    assertEquals(1, scale.resetTimerCount)
  }

  @Test
  fun `a timer reset that rearms sends nothing`() {
    val scale = FakeScale()
    scale.activePour()
    scale.finish()
    scale.clearEffects()
    scale.timerEvent(TimerStatus.RESET)
    assertTrue(scale.effects.isEmpty())
    assertTrue(scale.finished.rearmed)
  }

  @Test
  fun `the next pour after a rearming tare sends start timer`() {
    val scale = FakeScale()
    scale.activePour()
    scale.finish()
    scale.tare()
    scale.idle(500)
    scale.pour(4.0, 3_000)
    assertEquals(2, scale.startTimerCount)
    assertEquals(1, scale.resetTimerCount)
    assertEquals(1, scale.pauseTimerCount)
  }

  @Test
  fun `a pour with the timer paused sends reset timer and then start timer`() {
    val scale = FakeScale()
    scale.timer = TimerStatus.PAUSED // p. ej. el reloj quedó en 0:42 de un uso anterior
    scale.simplePour(3_000)
    assertEquals(listOf(BrewEffect.ResetTimer, BrewEffect.StartTimer), scale.effects)
    // El RESET que responde la báscula al `resetTimer` no termina la sesión recién iniciada.
    scale.assertActive(pouring = true)
    assertEquals(TimerStatus.RUNNING, scale.timer)
  }

  @Test
  fun `a user reset after the start still ends the session`() {
    val scale = FakeScale()
    scale.timer = TimerStatus.PAUSED
    scale.simplePour(3_000)
    scale.timerEvent(TimerStatus.RESET)
    assertEquals(FinishReason.TIMER_RESET, scale.finished.summary.reason)
  }

  @Test
  fun `a pour with the timer running sends no timer effects`() {
    val scale = FakeScale()
    scale.timer = TimerStatus.RUNNING
    scale.activePour()
    assertTrue(scale.effects.isEmpty())
  }
}
