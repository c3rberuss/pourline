package dev.josueamaya.pourline.ui.session

import dev.josueamaya.pourline.R
import dev.josueamaya.pourline.brew.TargetStage
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
}
