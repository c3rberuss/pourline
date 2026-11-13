package dev.dotscale.recipe

import dev.dotscale.history.BrewMethod
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
    assertEquals(listOf("Bloom", "2.º vertido", "3.er vertido"), recipe.stages.map { it.name })
    assertEquals(listOf(0L, 45_000L, 90_000L), recipe.stages.map { it.startMs })
    assertEquals(listOf(40.0, 150.0, 250.0), recipe.stages.map { it.targetWaterG })
    assertEquals(listOf(10_000L, 20_000L, 25_000L), recipe.stages.map { it.durationMs })
    assertNull(recipe.notes)
  }

  @Test
  fun `the 4 to 6 method is 300 g with 20 g in five pours`() {
    val recipe = BuiltInRecipes.recipe(BuiltInRecipes.FOUR_SIX, texts)
    assertEquals(20.0, recipe.doseG, 0.0)
    assertEquals(300.0, recipe.totalWaterG, 0.0)
    assertEquals(15.0, recipe.ratio, 1e-9)
    assertEquals(listOf(0L, 45_000L, 90_000L, 135_000L, 180_000L), recipe.stages.map { it.startMs })
    assertEquals(listOf(60.0, 120.0, 180.0, 240.0, 300.0), recipe.stages.map { it.targetWaterG })
    assertTrue(recipe.stages.all { it.durationMs == 10_000L })
  }

  @Test
  fun `names come from the texts`() {
    assertEquals("Classic V60", BuiltInRecipes.recipe(BuiltInRecipes.V60_CLASSIC, FakeRecipeTexts("en")).name)
    assertEquals("4:6 method", BuiltInRecipes.recipe(BuiltInRecipes.FOUR_SIX, FakeRecipeTexts("en")).name)
  }
}
