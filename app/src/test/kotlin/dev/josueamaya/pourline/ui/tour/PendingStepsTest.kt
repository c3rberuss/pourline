package dev.josueamaya.pourline.ui.tour

import dev.josueamaya.pourline.settings.ExperienceMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingStepsTest {

  private fun step(id: String, anchor: String? = id, expert: Boolean = false) = TourStep(id, anchor, 0, 0, expert)

  private val tour = Tour("t", listOf(step("t.a"), step("t.b"), step("t.c"), step("t.d"), step("t.e")))
  private val allAnchors = tour.steps.mapNotNull { it.anchor }.toSet()

  private fun ids(steps: List<TourStep>) = steps.map { it.id }

  @Test
  fun `unseen steps are pending and seen ones are not`() {
    val pending = pendingSteps(tour, setOf("t.a", "t.c"), ExperienceMode.BASIC, allAnchors)

    assertEquals(listOf("t.b", "t.d", "t.e"), ids(pending))
  }

  @Test
  fun `expert only steps are hidden in basic and shown in expert`() {
    val withExpert = Tour("t", listOf(step("t.a"), step("t.b", expert = true), step("t.c")))
    val anchors = setOf("t.a", "t.b", "t.c")

    assertEquals(listOf("t.a", "t.c"), ids(pendingSteps(withExpert, emptySet(), ExperienceMode.BASIC, anchors)))
    assertEquals(listOf("t.a", "t.b", "t.c"), ids(pendingSteps(withExpert, emptySet(), ExperienceMode.EXPERT, anchors)))
  }

  @Test
  fun `a step whose anchor is missing is left out and not consumed`() {
    val seen = emptySet<String>()
    val pending = pendingSteps(tour, seen, ExperienceMode.BASIC, allAnchors - "t.b")

    assertEquals(listOf("t.a", "t.c", "t.d", "t.e"), ids(pending))
    // No se consume: cuando el ancla aparece, el paso vuelve a estar pendiente.
    assertTrue("t.b" in ids(pendingSteps(tour, seen, ExperienceMode.BASIC, allAnchors)))
  }

  @Test
  fun `a centered step alone is not shown`() {
    val welcome = step("t.welcome", anchor = null)
    val centered = Tour("t", listOf(welcome, step("t.a"), step("t.b")))

    // Todos los pasos con ancla vistos: la bienvenida sola no sale.
    assertTrue(pendingSteps(centered, setOf("t.a", "t.b"), ExperienceMode.BASIC, setOf("t.a", "t.b")).isEmpty())
    // Todos los pasos con ancla sin su ancla presente.
    assertTrue(pendingSteps(centered, emptySet(), ExperienceMode.BASIC, emptySet()).isEmpty())
    // Un recorrido de solo pasos centrados tampoco sale.
    assertTrue(pendingSteps(Tour("t", listOf(welcome)), emptySet(), ExperienceMode.BASIC, emptySet()).isEmpty())
  }

  @Test
  fun `a centered step is shown when an anchored step is pending`() {
    val centered = Tour("t", listOf(step("t.welcome", anchor = null), step("t.a"), step("t.mid", anchor = null), step("t.b")))

    val pending = pendingSteps(centered, setOf("t.b"), ExperienceMode.BASIC, setOf("t.a", "t.b"))

    assertEquals(listOf("t.welcome", "t.a", "t.mid"), ids(pending))
  }

  @Test
  fun `the order is the tour order`() {
    val shuffledAnchors = setOf("t.e", "t.c", "t.a", "t.d", "t.b")

    assertEquals(listOf("t.a", "t.b", "t.c", "t.d", "t.e"), ids(pendingSteps(tour, emptySet(), ExperienceMode.BASIC, shuffledAnchors)))
  }

  @Test
  fun `the counter is over the pending list of the opening`() {
    val pending = pendingSteps(tour, setOf("t.a", "t.b"), ExperienceMode.BASIC, allAnchors)
    val run = TourRun(tour.id, pending)

    assertEquals(1, run.position)
    assertEquals(3, run.count)
  }

  @Test
  fun `unknown seen ids do not change anything`() {
    val clean = pendingSteps(tour, setOf("t.a"), ExperienceMode.BASIC, allAnchors)
    val withUnknown = pendingSteps(tour, setOf("t.a", "gone.step", "otro.x"), ExperienceMode.BASIC, allAnchors)

    assertEquals(clean, withUnknown)
  }

  @Test
  fun `after a reset every visible step is pending again`() {
    val seen = setOf("t.a", "t.b", "t.c", "t.d", "t.e")
    assertTrue(pendingSteps(tour, seen, ExperienceMode.BASIC, allAnchors).isEmpty())

    val afterReset = pendingSteps(tour, emptySet(), ExperienceMode.BASIC, allAnchors)

    assertEquals(tour.steps.size, afterReset.size)
  }
}
