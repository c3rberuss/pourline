package dev.dotscale.ui.session

import dev.dotscale.brew.TargetCurve
import dev.dotscale.recipe.BuiltInRecipes
import dev.dotscale.recipe.FakeRecipeTexts
import dev.dotscale.recipe.StageType
import dev.dotscale.recipe.recipeOf
import dev.dotscale.recipe.stage
import dev.dotscale.recipe.v60Classic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrewDialTest {

  @Test
  fun `the segments of the classic V60 are at 40 and 150 over 250`() {
    val segments = dialSegments(TargetCurve.fromRecipe(v60Classic()))
    assertEquals(listOf(0.16f, 0.6f), segments)
  }

  @Test
  fun `swirls and waits add no segment`() {
    val recipe = recipeOf(
      stage(0, 50.0, 10, "Bloom"),
      stage(10, 0.0, 10, type = StageType.SWIRL),
      stage(20, 0.0, 15, type = StageType.WAIT),
      stage(35, 200.0, 20, "Vertido"),
    )
    assertEquals(listOf(0.25f), dialSegments(TargetCurve.fromRecipe(recipe)))
  }

  @Test
  fun `the four six recipe has one boundary per pour but the last`() {
    val recipe = BuiltInRecipes.recipe(BuiltInRecipes.FOUR_SIX, FakeRecipeTexts())
    val pours = recipe.stages.count { it.type == StageType.POUR }
    assertEquals(pours - 1, dialSegments(TargetCurve.fromRecipe(recipe)).size)
  }

  @Test
  fun `a curve without water has no segments`() {
    val curve = TargetCurve.fromRecipe(recipeOf(stage(0, 0.0, 10, type = StageType.WAIT)))
    assertTrue(dialSegments(curve).isEmpty())
  }

  @Test
  fun `a scaled curve keeps the same proportions`() {
    val curve = TargetCurve.fromRecipe(v60Classic()).scaled(1.2)
    assertEquals(listOf(0.16f, 0.6f), dialSegments(curve))
  }

  @Test
  fun `the fraction is water over target`() {
    assertEquals(0.5f, dialFraction(125.0, 250.0), 1e-6f)
    assertEquals(0f, dialFraction(0.0, 250.0), 0f)
  }

  @Test
  fun `the ring saturates at one hundred percent`() {
    assertEquals(1f, dialFraction(250.0, 250.0), 0f)
    assertEquals(1f, dialFraction(310.0, 250.0), 0f)
  }

  @Test
  fun `a negative water or a missing target give an empty ring`() {
    assertEquals(0f, dialFraction(-3.0, 250.0), 0f)
    assertEquals(0f, dialFraction(100.0, null), 0f)
    assertEquals(0f, dialFraction(100.0, 0.0), 0f)
  }

  @Test
  fun `the spoken percent is not saturated`() {
    assertEquals(61, dialPercent(152.0, 250.0))
    assertEquals(124, dialPercent(310.0, 250.0))
    assertEquals(0, dialPercent(10.0, null))
  }
}
