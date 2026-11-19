package dev.josueamaya.pourline.ui.session

import org.junit.Assert.assertEquals
import org.junit.Test

class StepStatesTest {

  @Test
  fun `the steps before the current one are done and the ones after are pending`() {
    assertEquals(
      listOf(StepState.DONE, StepState.DONE, StepState.CURRENT, StepState.PENDING, StepState.PENDING),
      stepStates(stageCount = 5, currentIndex = 2, finished = false),
    )
  }

  @Test
  fun `the first step as current leaves the rest pending`() {
    assertEquals(
      listOf(StepState.CURRENT, StepState.PENDING, StepState.PENDING),
      stepStates(3, 0, finished = false),
    )
  }

  @Test
  fun `without a current step everything is pending`() {
    assertEquals(List(4) { StepState.PENDING }, stepStates(4, null, finished = false))
  }

  @Test
  fun `a finished recipe has every step done`() {
    assertEquals(List(5) { StepState.DONE }, stepStates(5, 4, finished = true))
    assertEquals(List(5) { StepState.DONE }, stepStates(5, null, finished = true))
  }

  @Test
  fun `the last step can be the current one`() {
    assertEquals(
      listOf(StepState.DONE, StepState.DONE, StepState.CURRENT),
      stepStates(3, 2, finished = false),
    )
  }

  @Test
  fun `no steps give an empty list`() {
    assertEquals(emptyList<StepState>(), stepStates(0, null, finished = false))
  }
}
