package dev.dotscale.brew

import dev.dotscale.recipe.v60Classic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StageAlertTrackerTest {
  private val curve = TargetCurve.fromRecipe(v60Classic()) // etapas en 0, 45 s y 90 s

  @Test
  fun `a stage that starts at zero never alerts`() {
    val tracker = StageAlertTracker()
    assertNull(tracker.update(1_000, curve, 0))
    assertNull(tracker.update(1_000, curve, 5_000))
  }

  @Test
  fun `each stage alerts once when the clock crosses its start`() {
    val tracker = StageAlertTracker()
    assertNull(tracker.update(1_000, curve, 44_999))
    assertEquals(1, tracker.update(1_000, curve, 45_000)?.index)
    assertNull(tracker.update(1_000, curve, 45_100))
    assertNull(tracker.update(1_000, curve, 60_000))
    assertEquals(2, tracker.update(1_000, curve, 90_000)?.index)
    assertNull(tracker.update(1_000, curve, 90_100))
    assertNull(tracker.update(1_000, curve, 200_000))
  }

  @Test
  fun `a time jump across several stages alerts only the last one`() {
    val tracker = StageAlertTracker()
    assertNull(tracker.update(1_000, curve, 10_000))
    assertEquals(2, tracker.update(1_000, curve, 100_000)?.index)
    assertNull(tracker.update(1_000, curve, 100_100))
  }

  @Test
  fun `the first update of a session that is already past several stages alerts only the last`() {
    val tracker = StageAlertTracker()
    assertEquals(2, tracker.update(1_000, curve, 95_000)?.index)
  }

  @Test
  fun `a new session restarts the tracker`() {
    val tracker = StageAlertTracker()
    assertEquals(1, tracker.update(1_000, curve, 50_000)?.index)
    assertNull(tracker.update(1_000, curve, 51_000))
    assertEquals(1, tracker.update(2_000, curve, 50_000)?.index)
  }

  @Test
  fun `leaving the session also restarts it, even if the next t0 repeats`() {
    val tracker = StageAlertTracker()
    assertEquals(1, tracker.update(1_000, curve, 50_000)?.index)
    assertNull(tracker.update(null, curve, null))
    assertEquals(1, tracker.update(1_000, curve, 50_000)?.index)
  }

  @Test
  fun `no target or no clock gives no alert`() {
    val tracker = StageAlertTracker()
    assertNull(tracker.update(1_000, null, 50_000))
    assertNull(tracker.update(1_000, curve, null))
  }
}
