package dev.josueamaya.pourline.recipe

import dev.josueamaya.pourline.brew.TargetCurve
import dev.josueamaya.pourline.history.BrewMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BuiltInRecipesTest {
  private val texts = FakeRecipeTexts()

  @Test
  fun `both pass validation`() {
    BuiltInRecipes.all(texts).forEach { assertTrue(it.name, RecipeValidator.validate(it).isEmpty()) }
  }

  private val oldV60 = listOf(
    RecipeStage("Bloom", 0, 40.0, 10_000),
    RecipeStage("2.º vertido", 45_000, 150.0, 20_000),
    RecipeStage("3.er vertido", 90_000, 250.0, 25_000),
  )
  private val oldFourSix = (0..4).map { RecipeStage(null, it * 45_000L, 60.0 * (it + 1), 10_000) }

  @Test
  fun `the classic V60 is 250 g with 15 g`() {
    val recipe = BuiltInRecipes.recipe(BuiltInRecipes.V60_CLASSIC, texts)
    assertEquals("V60 clásica", recipe.name)
    assertEquals(15.0, recipe.doseG, 0.0)
    assertEquals(250.0, recipe.totalWaterG, 0.0)
    assertEquals(250.0 / 15, recipe.ratio, 1e-9)
    assertEquals(BrewMethod.V60, recipe.method)
    assertEquals(BuiltInRecipes.V60_CLASSIC, recipe.builtInKey)
    assertTrue(recipe.localizedNames)
    assertEquals(listOf("Bloom", null, "2.º vertido", null, "3.er vertido"), recipe.stages.map { it.name })
    assertEquals(listOf(0L, 10_000L, 45_000L, 65_000L, 90_000L), recipe.stages.map { it.startMs })
    assertEquals(listOf(40.0, 40.0, 150.0, 150.0, 250.0), recipe.stages.map { it.targetWaterG })
    assertEquals(listOf(10_000L, 35_000L, 20_000L, 25_000L, 25_000L), recipe.stages.map { it.durationMs })
    assertEquals(
      listOf(StageType.POUR, StageType.WAIT, StageType.POUR, StageType.WAIT, StageType.POUR),
      recipe.stages.map { it.type },
    )
    assertNull(recipe.notes)
  }

  @Test
  fun `the 4 to 6 method is 300 g with 20 g in five pours`() {
    val recipe = BuiltInRecipes.recipe(BuiltInRecipes.FOUR_SIX, texts)
    assertEquals(20.0, recipe.doseG, 0.0)
    assertEquals(300.0, recipe.totalWaterG, 0.0)
    assertEquals(15.0, recipe.ratio, 1e-9)
    val pours = recipe.stages.filter { it.type == StageType.POUR }
    assertEquals(9, recipe.stages.size)
    assertEquals(listOf(0L, 45_000L, 90_000L, 135_000L, 180_000L), pours.map { it.startMs })
    assertEquals(listOf(60.0, 120.0, 180.0, 240.0, 300.0), pours.map { it.targetWaterG })
    assertTrue(pours.all { it.durationMs == 10_000L })
    assertTrue(recipe.stages.filter { it.type == StageType.WAIT }.all { it.durationMs == 35_000L })
  }

  @Test
  fun `the new definitions are the old ones with the gaps filled and draw the same curve`() {
    val v60 = BuiltInRecipes.recipe(BuiltInRecipes.V60_CLASSIC, texts)
    val fourSix = BuiltInRecipes.recipe(BuiltInRecipes.FOUR_SIX, texts)
    assertEquals(
      StageGaps.fillWaits(oldV60).map { Triple(it.startMs, it.targetWaterG, it.durationMs) },
      v60.stages.map { Triple(it.startMs, it.targetWaterG, it.durationMs) },
    )
    assertEquals(
      StageGaps.fillWaits(oldFourSix).map { Triple(it.startMs, it.targetWaterG, it.durationMs) },
      fourSix.stages.map { Triple(it.startMs, it.targetWaterG, it.durationMs) },
    )
    assertEquals(
      TargetCurve.fromRecipe(v60.copy(stages = oldV60)).points,
      TargetCurve.fromRecipe(v60).points,
    )
    assertEquals(
      TargetCurve.fromRecipe(fourSix.copy(stages = oldFourSix)).points,
      TargetCurve.fromRecipe(fourSix).points,
    )
  }

  @Test
  fun `names and instructions have one entry per step and the waits have no name`() {
    BuiltInRecipes.all(texts).forEach { recipe ->
      val key = checkNotNull(recipe.builtInKey)
      assertEquals(recipe.stages.size, texts.builtInStageNames(key).size)
      assertEquals(recipe.stages.size, texts.builtInStageInstructions(key).size)
      recipe.stages.forEachIndexed { i, stage ->
        if (stage.type == StageType.WAIT) assertNull(stage.name)
        assertEquals(texts.builtInStageInstructions(key)[i], stage.instruction)
      }
    }
  }

  @Test
  fun `names come from the texts`() {
    assertEquals("Classic V60", BuiltInRecipes.recipe(BuiltInRecipes.V60_CLASSIC, FakeRecipeTexts("en")).name)
    assertEquals("4:6 method", BuiltInRecipes.recipe(BuiltInRecipes.FOUR_SIX, FakeRecipeTexts("en")).name)
  }
}
