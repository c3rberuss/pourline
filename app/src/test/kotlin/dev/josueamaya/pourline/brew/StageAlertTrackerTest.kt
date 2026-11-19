package dev.josueamaya.pourline.brew

import dev.josueamaya.pourline.recipe.StageType
import dev.josueamaya.pourline.recipe.aeroPressWithActions
import dev.josueamaya.pourline.recipe.recipeOf
import dev.josueamaya.pourline.recipe.stage
import dev.josueamaya.pourline.recipe.v60Classic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StageAlertTrackerTest {
  private val curve = TargetCurve.fromRecipe(v60Classic()) // pasos en 0, 10 (espera), 45, 65 (espera) y 90 s

  @Test
  fun `a stage that starts at zero never alerts`() {
    val tracker = StageAlertTracker()
    assertNull(tracker.update(1_000, curve, 0))
    assertNull(tracker.update(1_000, curve, 5_000))
  }

  @Test
  fun `each step alerts once when the clock crosses its start, waits included`() {
    val tracker = StageAlertTracker()
    assertNull(tracker.update(1_000, curve, 9_999))
    val wait = tracker.update(1_000, curve, 10_000)
    assertEquals(1, wait?.index)
    assertEquals(StageType.WAIT, wait?.type)
    assertNull(tracker.update(1_000, curve, 10_100))
    assertNull(tracker.update(1_000, curve, 44_999))
    assertEquals(2, tracker.update(1_000, curve, 45_000)?.index)
    assertNull(tracker.update(1_000, curve, 45_100))
    assertEquals(3, tracker.update(1_000, curve, 65_000)?.index)
    assertEquals(4, tracker.update(1_000, curve, 90_000)?.index)
    assertNull(tracker.update(1_000, curve, 90_100))
    assertNull(tracker.update(1_000, curve, 200_000))
  }

  @Test
  fun `a swirl alerts like any other step`() {
    val withSwirl = TargetCurve.fromRecipe(
      recipeOf(stage(0, 40.0, 10), stage(10, 40.0, 5, type = StageType.SWIRL), stage(15, 100.0, 10)),
    )
    val tracker = StageAlertTracker()
    assertNull(tracker.update(1_000, withSwirl, 9_000))
    val swirl = tracker.update(1_000, withSwirl, 10_000)
    assertEquals(StageType.SWIRL, swirl?.type)
  }

  @Test
  fun `an action alerts like any other step`() {
    val withAction = TargetCurve.fromRecipe(aeroPressWithActions())
    val tracker = StageAlertTracker()
    assertNull(tracker.update(1_000, withAction, 5_000))
    assertEquals(StageType.SWIRL, tracker.update(1_000, withAction, 10_000)?.type)
    assertNull(tracker.update(1_000, withAction, 19_999))
    val action = tracker.update(1_000, withAction, 20_000)
    assertEquals(2, action?.index)
    assertEquals(StageType.ACTION, action?.type)
  }

  @Test
  fun `a time jump across several steps alerts only the last one`() {
    val tracker = StageAlertTracker()
    assertNull(tracker.update(1_000, curve, 5_000))
    assertEquals(4, tracker.update(1_000, curve, 100_000)?.index)
    assertNull(tracker.update(1_000, curve, 100_100))
  }

  @Test
  fun `the first update of a session that is already past several steps alerts only the last`() {
    val tracker = StageAlertTracker()
    assertEquals(4, tracker.update(1_000, curve, 95_000)?.index)
  }

  @Test
  fun `a new session restarts the tracker`() {
    val tracker = StageAlertTracker()
    assertEquals(2, tracker.update(1_000, curve, 50_000)?.index)
    assertNull(tracker.update(1_000, curve, 51_000))
    assertEquals(2, tracker.update(2_000, curve, 50_000)?.index)
  }

  @Test
  fun `leaving the session also restarts it, even if the next t0 repeats`() {
    val tracker = StageAlertTracker()
    assertEquals(2, tracker.update(1_000, curve, 50_000)?.index)
    assertNull(tracker.update(null, curve, null))
    assertEquals(2, tracker.update(1_000, curve, 50_000)?.index)
  }

  @Test
  fun `no target or no clock gives no alert`() {
    val tracker = StageAlertTracker()
    assertNull(tracker.update(1_000, null, 50_000))
    assertNull(tracker.update(1_000, curve, null))
  }

  // --- Tics hacia el siguiente paso ---

  @Test
  fun `each countdown second ticks once even if the guide is published several times`() {
    val tracker = StageAlertTracker()
    val next = curve.stages[2]
    assertEquals(5, tracker.tick(1_000, next, 5))
    assertNull(tracker.tick(1_000, next, 5))
    assertEquals(4, tracker.tick(1_000, next, 4))
    assertNull(tracker.tick(1_000, next, 4))
    assertEquals(3, tracker.tick(1_000, next, 3))
  }

  @Test
  fun `outside the countdown there is no tick`() {
    val tracker = StageAlertTracker()
    assertNull(tracker.tick(1_000, curve.stages[2], null))
    assertNull(tracker.tick(1_000, null, 3))
    assertNull(tracker.tick(null, curve.stages[2], 3))
  }

  @Test
  fun `the same second ticks again for another step or another session`() {
    val tracker = StageAlertTracker()
    assertEquals(1, tracker.tick(1_000, curve.stages[1], 1))
    assertEquals(1, tracker.tick(1_000, curve.stages[2], 1))
    assertEquals(1, tracker.tick(2_000, curve.stages[2], 1))
    assertNull(tracker.tick(null, null, null))
    assertEquals(1, tracker.tick(2_000, curve.stages[2], 1))
  }
}
