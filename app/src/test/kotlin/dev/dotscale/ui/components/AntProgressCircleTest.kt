package dev.dotscale.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AntProgressCircleTest {
  private fun assertSpan(expectedStart: Float, expectedSweep: Float, actual: ArcSpan) {
    assertEquals(expectedStart, actual.start, 0.001f)
    assertEquals(expectedSweep, actual.sweep, 0.001f)
  }

  @Test
  fun withoutSegmentsTheArcIsContinuous() {
    val spans = sweepAngles(0.5f, emptyList(), 2f)
    assertEquals(1, spans.size)
    assertSpan(0f, 180f, spans.single())
  }

  @Test
  fun fractionIsClampedToZeroAndOne() {
    assertTrue(sweepAngles(-1f, emptyList(), 2f).isEmpty())
    assertTrue(sweepAngles(0f, listOf(0.5f), 2f).isEmpty())
    assertSpan(0f, 360f, sweepAngles(2f, emptyList(), 2f).single())
  }

  @Test
  fun gapsAreCenteredOnTheBoundaries() {
    val spans = sweepAngles(1f, listOf(0.25f, 0.75f), 2f)
    assertEquals(3, spans.size)
    assertSpan(0f, 89f, spans[0])
    assertSpan(91f, 178f, spans[1])
    assertSpan(271f, 89f, spans[2])
  }

  @Test
  fun gapsBeyondTheFilledArcAreNotDrawn() {
    val spans = sweepAngles(0.5f, listOf(0.25f, 0.75f), 2f)
    assertEquals(2, spans.size)
    assertSpan(0f, 89f, spans[0])
    assertSpan(91f, 89f, spans[1])
  }

  @Test
  fun boundariesAtTheEdgesAndDuplicatesAreIgnored() {
    val spans = sweepAngles(1f, listOf(0f, 1f, 0.5f, 0.5f, 1.5f, -0.2f), 2f)
    assertEquals(2, spans.size)
    assertSpan(0f, 179f, spans[0])
    assertSpan(181f, 179f, spans[1])
  }

  @Test
  fun aFilledArcEndingInsideAGapIsCutAtTheGapStart() {
    val spans = sweepAngles(0.25f, listOf(0.25f), 2f)
    assertEquals(1, spans.size)
    assertSpan(0f, 89f, spans.single())
  }
}
