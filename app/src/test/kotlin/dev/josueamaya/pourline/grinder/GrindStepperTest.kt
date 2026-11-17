package dev.josueamaya.pourline.grinder

import dev.josueamaya.pourline.grinder.GrinderFixtures.dos
import dev.josueamaya.pourline.grinder.GrinderFixtures.tres
import dev.josueamaya.pourline.grinder.GrinderFixtures.uno
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GrindStepperTest {
  private fun parts(v: Double) = GrindFormat.decompose(v, tres.units)

  @Test
  fun carryGoesUpWhenTheBaseUnitOverflows() {
    // 1 · 3 · 9 + 1 clic = 2 · 0 · 0
    val v = GrindFormat.compose(listOf(1.0, 3.0, 9.0), tres.units)
    assertEquals(listOf(2.0, 0.0, 0.0), parts(GrindStepper.step(v, 2, +1, tres, 0.0)))
  }

  @Test
  fun borrowGoesDownWhenTheBaseUnitUnderflows() {
    // 2 · 0 · 0 − 1 clic = 1 · 3 · 9
    val v = GrindFormat.compose(listOf(2.0, 0.0, 0.0), tres.units)
    assertEquals(listOf(1.0, 3.0, 9.0), parts(GrindStepper.step(v, 2, -1, tres, 0.0)))
  }

  @Test
  fun anUpperUnitAddsItsMultiplier() {
    assertEquals(listOf(2.0, 2.0, 3.0), parts(GrindStepper.step(63.0, 0, +1, tres, 0.0)))
    assertEquals(listOf(1.0, 3.0, 3.0), parts(GrindStepper.step(63.0, 1, +1, tres, 0.0)))
  }

  @Test
  fun valueIsClampedToTheScaleOnBothEnds() {
    assertEquals(40.0, GrindStepper.step(40.0, 0, +1, uno, 0.0), 0.0)
    assertEquals(0.0, GrindStepper.step(0.0, 0, -1, uno, 0.0), 0.0)
    assertEquals(200.0, GrindStepper.step(180.0, 0, +1, tres, 0.0), 0.0)
    assertFalse(GrindStepper.canStep(40.0, 0, +1, uno, 0.0))
    assertTrue(GrindStepper.canStep(40.0, 0, -1, uno, 0.0))
  }

  @Test
  fun halfStepsAreRespected() {
    assertEquals(10.5, GrindStepper.step(10.0, 0, +1, dos, 0.0), 0.0)
    assertEquals(9.5, GrindStepper.step(10.0, 0, -1, dos, 0.0), 0.0)
  }

  @Test
  fun firstTapSetsTheStartWithoutAdding() {
    assertEquals(22.0, GrindStepper.step(null, 0, +1, uno, 22.0), 0.0)
    assertEquals(22.0, GrindStepper.step(null, 0, -1, uno, 22.0), 0.0)
    assertTrue(GrindStepper.canStep(null, 0, +1, uno, 22.0))
  }
}
