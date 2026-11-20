package dev.josueamaya.pourline.session

import dev.josueamaya.pourline.brew.BrewPhase
import dev.josueamaya.pourline.brew.BrewState
import dev.josueamaya.pourline.brew.BrewSummary
import dev.josueamaya.pourline.brew.FinishReason
import dev.josueamaya.pourline.protocol.ScaleMessage
import dev.josueamaya.pourline.protocol.TimerStatus
import dev.josueamaya.pourline.protocol.WeightUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScaleUiStateTest {

  private fun weight(raw: Int, unit: WeightUnit = WeightUnit.GRAMS) =
    ScaleMessage.Weight(rawWeight = raw, rawFlow = 25, seconds = 30, unit = unit, outOfRange = false)

  @Test
  fun `weight message updates live values`() {
    val state = ScaleUiState().reduce(weight(2400))
    assertEquals(240.0, state.weight, 0.001)
    assertEquals(2.5, state.flowRate, 0.001)
    assertEquals(30, state.seconds)
  }

  @Test
  fun `ratio is water over dose`() {
    val state = ScaleUiState(dose = 16.0).reduce(weight(2400))
    assertEquals(15.0, state.ratio!!, 0.001)
  }

  @Test
  fun `changing unit clears the dose`() {
    val state = ScaleUiState(dose = 16.0).reduce(ScaleMessage.UnitChanged(WeightUnit.OUNCES))
    assertNull(state.dose)
    assertEquals(WeightUnit.OUNCES, state.unit)
  }

  @Test
  fun `timer status is tracked`() {
    assertEquals(TimerStatus.RUNNING, ScaleUiState().reduce(ScaleMessage.Timer(TimerStatus.RUNNING)).timer)
  }

  @Test
  fun `the session unit is grams without a scale and the scale unit otherwise`() {
    val ounces = ScaleUiState(unit = WeightUnit.OUNCES)

    assertEquals(WeightUnit.OUNCES, ounces.sessionUnit)
    assertEquals(WeightUnit.GRAMS, ounces.copy(scaleless = true).sessionUnit)
    assertEquals(WeightUnit.GRAMS, ScaleUiState().sessionUnit)
  }

  @Test
  fun `data import is blocked during a session and while the last brew is saving`() {
    val plan = BrewPlan(doseG = 15.0, ratio = 16.0)
    val finished = BrewState(
      phase = BrewPhase.Finished(
        summary = BrewSummary(
          totalTimeMs = 1_000, totalWaterG = 100.0, avgFlowGps = 1.0, maxFlowGps = 2.0, stabilityScore = null, stabilityLevel = null,
          levelShare = emptyMap(), pours = emptyList(), finalRatio = null, reason = FinishReason.MANUAL, interrupted = false,
        ),
        rearmed = false,
        pourIgnored = false,
      ),
      samples = emptyList(),
      currentStability = null,
      currentLevel = null,
    )

    // Sin sesión: se puede importar.
    assertFalse(ScaleUiState().blocksDataImport)
    // Con sesión abierta (guiada, sin báscula o en cuenta atrás): no.
    assertTrue(ScaleUiState(plan = plan).blocksDataImport)
    assertTrue(ScaleUiState(plan = plan, scaleless = true).blocksDataImport)
    assertTrue(ScaleUiState(plan = plan, countdownS = 3).blocksDataImport)
    assertTrue(ScaleUiState(plan = plan, brew = finished).blocksDataImport)
    // Tras "Listo" la sesión se cierra, pero el guardado puede seguir.
    assertTrue(ScaleUiState(savedBrew = SavedBrew.Saving).blocksDataImport)
    assertFalse(ScaleUiState(savedBrew = SavedBrew.Saved(7)).blocksDataImport)
    assertFalse(ScaleUiState(savedBrew = SavedBrew.Failed).blocksDataImport)
  }
}
