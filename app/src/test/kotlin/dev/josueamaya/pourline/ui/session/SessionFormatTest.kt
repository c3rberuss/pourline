package dev.josueamaya.pourline.ui.session

import dev.josueamaya.pourline.R
import dev.josueamaya.pourline.brew.BrewPhase
import dev.josueamaya.pourline.brew.GuideState
import dev.josueamaya.pourline.brew.TargetConfig
import dev.josueamaya.pourline.brew.TargetCurve
import dev.josueamaya.pourline.brew.TargetGuide
import dev.josueamaya.pourline.brew.TargetStage
import dev.josueamaya.pourline.recipe.BuiltInRecipes
import dev.josueamaya.pourline.recipe.FakeRecipeTexts
import dev.josueamaya.pourline.recipe.StageType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionFormatTest {

  private fun stage(startS: Int, name: String? = null, type: StageType = StageType.POUR) =
    TargetStage(1, name, startS * 1_000L, (startS + 10) * 1_000L, 150.0, type)

  @Test
  fun `the clock is minutes and seconds without padding the minutes`() {
    assertEquals("0:00", formatClock(0))
    assertEquals("0:09", formatClock(9_999))
    assertEquals("1:05", formatClock(65_000))
    assertEquals("12:00", formatClock(720_000))
  }

  @Test
  fun `a negative time is shown as zero`() {
    assertEquals("0:00", formatClock(-500))
  }

  @Test
  fun `a remaining time rounds up to the second`() {
    assertEquals("0:00", formatRemaining(0))
    assertEquals("0:00", formatRemaining(-500))
    assertEquals("0:01", formatRemaining(1))
    assertEquals("0:05", formatRemaining(5_000))
    assertEquals("0:06", formatRemaining(5_001))
    assertEquals("1:00", formatRemaining(59_400))
  }

  @Test
  fun `the remaining clock hands over to the countdown without repeating a second`() {
    val config = TargetConfig()
    val curve = TargetCurve(emptyList(), listOf(TargetStage(0, null, 0, 30_000, 50.0), TargetStage(1, null, 30_000, 60_000, 100.0)))
    val next = curve.stages[1]
    // Lo que dice "Siguiente … en" en cada segundo entero y a mitad de segundo, de 8 s antes del paso hasta 1 ms antes.
    val shown = (8_000L downTo 1L step 500).plus(1L).distinct().map { before ->
      val tMs = next.startMs - before
      val guide = TargetGuide.at(curve, BrewPhase.Active(pouring = false), tMs, null, config)
      val info = nextStepInfo(guide.nextStage, tMs, guide.countdownS)!!
      info.countdownS?.toString() ?: nextStepClock(info).substringAfter(":").toInt().toString()
    }
    // Cada valor dura un segundo (dos muestras de medio segundo) y bajan de uno en uno.
    assertEquals(listOf("8", "8", "7", "7", "6", "6", "5", "5", "4", "4", "3", "3", "2", "2", "1", "1"), shown.dropLast(1))
    assertEquals("1", shown.last())
  }

  @Test
  fun `each step type has its own label and icon`() {
    assertEquals(R.string.step_type_pour, stepTypeLabel(StageType.POUR))
    assertEquals(R.string.step_type_swirl, stepTypeLabel(StageType.SWIRL))
    assertEquals(R.string.step_type_wait, stepTypeLabel(StageType.WAIT))
    assertEquals(3, StageType.entries.map { stepTypeIcon(it) }.distinct().size)
  }

  @Test
  fun `next step with a name outside the last five seconds shows the remaining clock`() {
    val info = nextStepInfo(stage(45, "2.º vertido"), elapsedMs = 20_000, countdownS = null)!!

    assertEquals("2.º vertido", info.name)
    assertEquals(25_000, info.remainingMs)
    assertEquals("0:25", nextStepClock(info))
    assertFalse(info.emphasized)
  }

  @Test
  fun `next step without a name has no name`() {
    val info = nextStepInfo(stage(10, name = null, type = StageType.WAIT), elapsedMs = 0, countdownS = null)!!
    assertNull(info.name)
    assertEquals(StageType.WAIT, info.stage.type)
    assertEquals("0:10", nextStepClock(info))
  }

  @Test
  fun `a blank name counts as no name`() {
    assertNull(nextStepInfo(stage(10, name = "  "), 0, null)!!.name)
  }

  @Test
  fun `inside the last five seconds the countdown is emphasized`() {
    val info = nextStepInfo(stage(45), elapsedMs = 41_000, countdownS = 4)!!
    assertTrue(info.emphasized)
    assertEquals(4, info.countdownS)
    assertEquals(4_000, info.remainingMs)
  }

  @Test
  fun `the remaining time never goes below zero`() {
    assertEquals(0, nextStepInfo(stage(10), elapsedMs = 30_000, countdownS = null)!!.remainingMs)
  }

  @Test
  fun `without a next step there is no text`() {
    assertNull(nextStepInfo(null, 0, null))
  }

  // --- timedStepView ---

  private fun v60() = TargetCurve.fromRecipe(BuiltInRecipes.recipe(BuiltInRecipes.V60_CLASSIC, FakeRecipeTexts()))

  private fun view(curve: TargetCurve, tMs: Long): TimedStepView? {
    val guide = TargetGuide.at(curve, BrewPhase.Active(pouring = false), tMs, null, TargetConfig())
    return timedStepView(guide, curve, tMs)
  }

  @Test
  fun `a timed step at 5 seconds is step 1 of 5 with 5 seconds left and half the progress`() {
    val view = view(v60(), 5_000)!!

    assertEquals(1, view.number)
    assertEquals(5, view.count)
    assertEquals(5_000L, view.remainingMs)
    assertEquals(0.5f, view.fraction, 1e-6f)
  }

  @Test
  fun `a timed step at 45 seconds is step 3 of 5 with 20 seconds left and no progress`() {
    val view = view(v60(), 45_000)!!

    assertEquals(3, view.number)
    assertEquals(20_000L, view.remainingMs)
    assertEquals(0f, view.fraction, 1e-6f)
  }

  @Test
  fun `the timed step view is null before the first step and with all steps done`() {
    val curve = TargetCurve(
      points = emptyList(),
      stages = listOf(TargetStage(0, null, 5_000, 15_000, 40.0)),
    )
    assertNull(view(curve, 2_000))
    assertNull(view(v60(), 115_000))
    assertNull(timedStepView(null, curve, 1_000))
    assertNull(timedStepView(GuideState(curve.stages[0], null, null, null, null), null, 1_000))
  }

  @Test
  fun `a zero duration step has full progress`() {
    val stage = TargetStage(0, null, 5_000, 5_000, 40.0)
    val curve = TargetCurve(emptyList(), listOf(stage, TargetStage(1, null, 5_000, 15_000, 80.0)))
    val guide = GuideState(stage, curve.stages[1], null, null, null)

    val view = timedStepView(guide, curve, 5_000)!!

    assertEquals(1f, view.fraction, 0f)
    assertEquals(0L, view.remainingMs)
  }

  @Test
  fun `the view agrees with TargetGuide every 100 ms`() {
    for (key in BuiltInRecipes.keys) {
      val curve = TargetCurve.fromRecipe(BuiltInRecipes.recipe(key, FakeRecipeTexts()))
      var tMs = 0L
      while (tMs <= 330_000) {
        val guide = TargetGuide.at(curve, BrewPhase.Active(pouring = false), tMs, null, TargetConfig())
        val view = timedStepView(guide, curve, tMs)
        val stage = guide.stage
        if (stage == null || guide.allStagesDone) {
          assertNull("$key $tMs", view)
        } else {
          assertEquals("$key $tMs", stage.index + 1, view!!.number)
          assertEquals("$key $tMs", stage.endMs - tMs, view.remainingMs)
          assertEquals("$key $tMs", curve.stages.size, view.count)
        }
        tMs += 100
      }
    }
  }
}
