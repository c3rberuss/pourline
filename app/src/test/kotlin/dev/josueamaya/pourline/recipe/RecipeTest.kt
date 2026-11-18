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
}
