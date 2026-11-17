package dev.josueamaya.pourline.recipe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class StageGapsTest {

  @Test
  fun `without gaps nothing changes`() {
    val stages = listOf(stage(0, 40.0, 10), stage(10, 100.0, 10))
    assertEquals(stages, StageGaps.fillWaits(stages))
  }

  @Test
  fun `a gap becomes a wait with the previous water`() {
    val result = StageGaps.fillWaits(listOf(stage(0, 40.0, 10), stage(45, 150.0, 20)))
    assertEquals(3, result.size)
    val wait = result[1]
    assertEquals(StageType.WAIT, wait.type)
    assertEquals(10_000L, wait.startMs)
    assertEquals(35_000L, wait.durationMs)
    assertEquals(40.0, wait.targetWaterG, 0.0)
  }

  @Test
  fun `a late first step gets a wait from zero with zero water`() {
    val result = StageGaps.fillWaits(listOf(stage(5, 40.0, 10)))
    assertEquals(2, result.size)
    assertEquals(StageType.WAIT, result[0].type)
    assertEquals(0L, result[0].startMs)
    assertEquals(5_000L, result[0].durationMs)
    assertEquals(0.0, result[0].targetWaterG, 0.0)
  }

  @Test
  fun `an overlap inserts nothing and keeps the steps`() {
    val stages = listOf(stage(0, 40.0, 10), RecipeStage(null, 9_000, 100.0, 10_000))
    assertEquals(stages, StageGaps.fillWaits(stages))
  }

  @Test
  fun `ten pours with nine gaps and a leading one make twenty steps`() {
    val pours = (0 until 10).map { stage(5 + it * 30, 20.0 * (it + 1), 10) }
    val result = StageGaps.fillWaits(pours)
    assertEquals(20, result.size)
    assertEquals(10, result.count { it.type == StageType.POUR })
    assertTrue(RecipeValidator.validate(recipeOf(*result.toTypedArray())).isEmpty())
  }

  @Test
  fun `the waits have no name and no instruction`() {
    val result = StageGaps.fillWaits(listOf(stage(0, 40.0, 10, "Bloom", instruction = "Despacio"), stage(45, 100.0, 10)))
    assertNull(result[1].name)
    assertNull(result[1].instruction)
    assertEquals("Bloom", result[0].name)
  }

  @Test
  fun `a swirl keeps the water of the pour before it for the next wait`() {
    val result = StageGaps.fillWaits(
      listOf(stage(0, 40.0, 10), stage(10, 40.0, 5, type = StageType.SWIRL), stage(30, 100.0, 10)),
    )
    assertEquals(4, result.size)
    assertEquals(40.0, result[2].targetWaterG, 0.0)
    assertSame(StageType.WAIT, result[2].type)
  }

  @Test
  fun `withDerivedWater copies the accumulated water into swirls and waits`() {
    val derived = listOf(
      stage(0, 40.0, 10),
      stage(10, 999.0, 5, type = StageType.SWIRL),
      stage(15, 0.0, 20, type = StageType.WAIT),
      stage(35, 100.0, 10),
    ).withDerivedWater()
    assertEquals(listOf(40.0, 40.0, 40.0, 100.0), derived.map { it.targetWaterG })
  }
}
