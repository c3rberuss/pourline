package dev.josueamaya.pourline.recipe

import dev.josueamaya.pourline.brew.TargetCurve
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class RecipeTest {
  @Test
  fun `the total duration is the end of the last step, gaps and final wait included`() {
    assertEquals(115_000L, v60Classic().totalDurationMs)
    val withGap = recipeOf(
      stage(0, 40.0, 10),
      stage(30, 100.0, 20), // hueco de 20 s tras el primer vertido
      stage(50, 100.0, 30, type = StageType.WAIT), // espera final de 30 s
    )
    assertEquals(80_000L, withGap.totalDurationMs)
  }

  @Test
  fun `the total duration does not depend on the order of the stages`() {
    val stages = listOf(stage(0, 40.0, 10), stage(15, 100.0, 20), stage(40, 100.0, 5, type = StageType.WAIT))
    assertEquals(45_000L, recipeOf(*stages.toTypedArray()).totalDurationMs)
    assertEquals(45_000L, recipeOf(*stages.reversed().toTypedArray()).totalDurationMs)
  }

  @Test
  fun `a recipe without stages lasts zero`() {
    assertEquals(0L, recipeOf().totalDurationMs)
  }

  @Test
  fun `the total duration is not the end of the last pour`() {
    val recipe = recipeOf(stage(0, 40.0, 10), stage(10, 100.0, 20), stage(30, 100.0, 45, type = StageType.WAIT))
    val curve = TargetCurve.fromRecipe(recipe)
    assertEquals(30_000L, curve.endMs)
    assertEquals(75_000L, curve.lastStepEndMs)
    assertEquals(curve.lastStepEndMs, recipe.totalDurationMs)
    assertNotEquals(curve.endMs, recipe.totalDurationMs)
  }

  @Test
  fun `an action takes the water of the previous pour`() {
    val derived = listOf(
      stage(0, 0.0, 10, type = StageType.ACTION),
      stage(10, 40.0, 10),
      stage(20, 999.0, 5, type = StageType.ACTION),
      stage(25, 0.0, 5, type = StageType.WAIT),
      stage(30, 100.0, 10),
      stage(40, 7.0, 5, type = StageType.ACTION),
    ).withDerivedWater()
    assertEquals(listOf(0.0, 40.0, 40.0, 40.0, 100.0, 100.0), derived.map { it.targetWaterG })
  }

  @Test
  fun `an action adds no water and does not change the ratio`() {
    val recipe = aeroPressWithActions()
    assertEquals(listOf(200.0, 200.0, 200.0, 200.0, 200.0, 200.0), recipe.stages.map { it.targetWaterG })
    assertEquals(200.0, recipe.totalWaterG, 0.0)
    assertEquals(200.0 / 15.0, recipe.ratio, 1e-9)
  }

  @Test
  fun `the total duration of the aeropress is two minutes`() {
    assertEquals(120_000L, aeroPressWithActions().totalDurationMs)
  }
}
