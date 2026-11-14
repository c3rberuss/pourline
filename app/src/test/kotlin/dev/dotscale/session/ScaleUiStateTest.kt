package dev.dotscale.session

import dev.dotscale.protocol.ScaleMessage
import dev.dotscale.protocol.TimerStatus
import dev.dotscale.protocol.WeightUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
