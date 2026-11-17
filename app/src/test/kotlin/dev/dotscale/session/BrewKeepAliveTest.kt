package dev.dotscale.session

import dev.dotscale.brew.BrewPhase
import dev.dotscale.brew.BrewState
import dev.dotscale.brew.BrewSummary
import dev.dotscale.brew.FinishReason
import dev.dotscale.protocol.WeightUnit
import dev.dotscale.scale.ConnectionState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrewKeepAliveTest {

  private val plan = BrewPlan(doseG = 15.0, ratio = 16.0)
  private val summary =
    BrewSummary(60_000, 250.0, 4.0, 5.0, null, null, emptyMap(), emptyList(), null, FinishReason.MANUAL, false)

  private fun open(phase: BrewPhase = BrewPhase.Armed, t0: Long? = null, savedBrew: SavedBrew? = null) = ScaleUiState(
    connection = ConnectionState.Ready,
    plan = plan,
    prep = PrepState(step = PrepStep.DOSE),
    brew = BrewState(phase, emptyList(), currentStability = null, currentLevel = null, t0MonoMs = t0),
    savedBrew = savedBrew,
  )

  private val finished = BrewPhase.Finished(summary, rearmed = false, pourIgnored = false)

  @Test
  fun `no session needs no service`() {
    assertFalse(ScaleUiState().needsKeepAlive())
  }

  @Test
  fun `an open session needs the service from the first step to the pour`() {
    assertTrue(open().needsKeepAlive())
    assertTrue(open(BrewPhase.Active(pouring = true), t0 = 1_000).needsKeepAlive())
  }

  @Test
  fun `a finished session keeps the service only while saving`() {
    assertTrue(open(finished, savedBrew = SavedBrew.Saving).needsKeepAlive())
    assertFalse(open(finished, savedBrew = SavedBrew.Saved(1)).needsKeepAlive())
    assertFalse(open(finished, savedBrew = SavedBrew.Failed).needsKeepAlive())
  }

  @Test
  fun `the prep steps show the step`() {
    val notice = open().toBrewNotice()
    assertEquals(BrewNoticeStage.PREP, notice.stage)
    assertEquals(PrepStep.DOSE, notice.prepStep)
    assertNull(notice.water)
    assertNull(notice.t0MonoMs)
  }

  @Test
  fun `the pour shows the water rounded to the gram and the start`() {
    val notice = open(BrewPhase.Active(pouring = true), t0 = 5_000).copy(weight = 123.46, targetWaterG = 250.0).toBrewNotice()
    assertEquals(BrewNoticeStage.POURING, notice.stage)
    assertEquals(123.0, notice.water!!, 0.0)
    assertEquals(250.0, notice.targetWater!!, 0.0)
    assertEquals(5_000L, notice.t0MonoMs)
    assertNull(notice.prepStep)
  }

  @Test
  fun `ounces keep one decimal`() {
    val notice = open(BrewPhase.Active(pouring = true), t0 = 0)
      .copy(weight = 4.26, unit = WeightUnit.OUNCES, targetWaterG = 283.495)
      .toBrewNotice()
    assertEquals(4.3, notice.water!!, 1e-9)
    assertEquals(10.0, notice.targetWater!!, 1e-9)
  }

  @Test
  fun `small changes in the weight do not change the notice`() {
    val active = open(BrewPhase.Active(pouring = true), t0 = 0)
    assertEquals(active.copy(weight = 100.2).toBrewNotice(), active.copy(weight = 99.8).toBrewNotice())
  }

  @Test
  fun `a lost scale is shown before saving but not while saving`() {
    assertTrue(open().copy(connection = ConnectionState.Disconnected).toBrewNotice().disconnected)
    val saving = open(finished, savedBrew = SavedBrew.Saving).copy(connection = ConnectionState.Disconnected).toBrewNotice()
    assertEquals(BrewNoticeStage.SAVING, saving.stage)
    assertFalse(saving.disconnected)
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  @Test
  fun `the service starts each time a session opens`() = runTest(UnconfinedTestDispatcher()) {
    val ui = MutableStateFlow(ScaleUiState())
    var starts = 0
    BrewKeepAlive(ui, backgroundScope) { starts++ }
    assertEquals(0, starts)

    ui.value = open()
    ui.value = open(BrewPhase.Active(pouring = false), t0 = 0)
    assertEquals(1, starts)

    ui.value = open(finished, savedBrew = SavedBrew.Saved(1))
    ui.value = open()
    assertEquals(2, starts)
  }
}
