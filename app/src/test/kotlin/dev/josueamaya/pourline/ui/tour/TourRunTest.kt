package dev.josueamaya.pourline.ui.tour

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TourRunTest {

  private fun step(id: String, anchor: String? = id) = TourStep(id, anchor, 0, 0)

  private val tour = Tour("t", listOf(step("t.a"), step("t.b"), step("t.c")))

  private fun run(index: Int = 0) = TourRun(tour.id, tour.steps, index)

  @Test
  fun `next marks the current step and moves on`() {
    val t = run().reduce(TourAction.Next)

    assertEquals(1, t.run!!.index)
    assertEquals(listOf("t.a"), t.markSeen)
    assertFalse(t.skippedHint)
  }

  @Test
  fun `next on the last step marks it and closes without a hint`() {
    val t = run(2).reduce(TourAction.Next)

    assertNull(t.run)
    assertEquals(listOf("t.c"), t.markSeen)
    assertFalse(t.skippedHint)
  }

  @Test
  fun `previous goes back without marking and does nothing on the first step`() {
    val back = run(2).reduce(TourAction.Prev)
    assertEquals(1, back.run!!.index)
    assertTrue(back.markSeen.isEmpty())

    val first = run(0).reduce(TourAction.Prev)
    assertEquals(0, first.run!!.index)
    assertTrue(first.markSeen.isEmpty())
    assertFalse(first.skippedHint)
  }

  @Test
  fun `back on the first step skips with the hint`() {
    val t = run(0).reduce(TourAction.Back)

    assertNull(t.run)
    assertEquals(listOf("t.a", "t.b", "t.c"), t.markSeen)
    assertTrue(t.skippedHint)
  }

  @Test
  fun `back on a later step goes to the previous one`() {
    val t = run(1).reduce(TourAction.Back)

    assertEquals(0, t.run!!.index)
    assertTrue(t.markSeen.isEmpty())
    assertFalse(t.skippedHint)
  }

  @Test
  fun `skip marks only the steps of this opening`() {
    // La apertura solo tenía a y c: b (de Experto o sin ancla) y los pasos de otros recorridos no se marcan.
    val opening = TourRun(tour.id, listOf(tour.steps[0], tour.steps[2]), 1)

    val t = opening.reduce(TourAction.Skip)

    assertNull(t.run)
    assertEquals(listOf("t.a", "t.c"), t.markSeen)
    assertTrue(t.skippedHint)
  }

  @Test
  fun `skip after an anchor was lost does not mark the lost step`() {
    val lost = run(1).reduce(TourAction.AnchorLost).run!!

    val t = lost.reduce(TourAction.Skip)

    assertEquals(listOf("t.a", "t.c"), t.markSeen)
  }

  @Test
  fun `anchor lost drops the step unmarked and shows the next one`() {
    val t = run(1).reduce(TourAction.AnchorLost)

    val next = t.run!!
    assertEquals(listOf("t.a", "t.c"), next.steps.map { it.id })
    assertEquals("t.c", next.current.id)
    assertEquals(2, next.count)
    assertTrue(t.markSeen.isEmpty())
    assertFalse(t.skippedHint)
  }

  @Test
  fun `anchor lost on the last step or the only remaining step closes without a hint`() {
    val last = run(2).reduce(TourAction.AnchorLost)
    assertNull(last.run)
    assertTrue(last.markSeen.isEmpty())

    val only = TourRun(tour.id, listOf(tour.steps[0])).reduce(TourAction.AnchorLost)
    assertNull(only.run)
    assertTrue(only.markSeen.isEmpty())
    assertFalse(only.skippedHint)
  }

  @Test
  fun `cancel closes without marking and without a hint`() {
    val t = run(1).reduce(TourAction.Cancel)

    assertNull(t.run)
    assertTrue(t.markSeen.isEmpty())
    assertFalse(t.skippedHint)
  }

  @Test
  fun `restore drops unknown ids and clamps the index`() {
    val restored = restore(tour, listOf("t.a", "t.gone", "t.c"), index = 5)!!

    assertEquals(listOf("t.a", "t.c"), restored.steps.map { it.id })
    assertEquals(1, restored.index)
    assertNull(restore(tour, listOf("t.gone"), 0))
    assertEquals(0, restore(tour, listOf("t.a", "t.b"), -3)!!.index)
  }

  private val invited = TourRun("t", listOf(TourStep("t.ask", null, 0, 0, invitation = true)) + tour.steps)

  @Test
  fun `the invitation does not count in the counter`() {
    assertEquals(0, invited.position)
    assertEquals(3, invited.count)

    val accepted = invited.reduce(TourAction.Next)
    assertEquals(1, accepted.run!!.position)
    assertEquals(3, accepted.run!!.count)
    assertEquals(listOf("t.ask"), accepted.markSeen)
  }

  @Test
  fun `declining the invitation marks every step of every tour and shows the hint`() {
    val t = invited.reduce(TourAction.Decline)

    assertNull(t.run)
    assertEquals(Tours.allStepIds, t.markSeen.toSet())
    assertTrue(t.skippedHint)
  }

  @Test
  fun `back on the invitation declines it`() {
    assertEquals(invited.reduce(TourAction.Decline), invited.reduce(TourAction.Back))
  }

  @Test
  fun `the step after the invitation acts as the first one`() {
    val first = invited.copy(index = 1)

    assertEquals(first, first.reduce(TourAction.Prev).run)
    val back = first.reduce(TourAction.Back)
    assertNull(back.run)
    assertEquals(listOf("t.ask", "t.a", "t.b", "t.c"), back.markSeen)
    assertTrue(back.skippedHint)
  }

  @Test
  fun `back on a first step without invitation still skips only this opening`() {
    val t = run().reduce(TourAction.Back)

    assertEquals(listOf("t.a", "t.b", "t.c"), t.markSeen)
  }
}
