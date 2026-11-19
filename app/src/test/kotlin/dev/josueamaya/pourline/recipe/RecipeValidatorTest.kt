package dev.josueamaya.pourline.recipe

import dev.josueamaya.pourline.history.BrewMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeValidatorTest {
  private fun valid() = recipeOf(stage(0, 40.0, 10), stage(45, 150.0, 20))

  private fun errors(recipe: Recipe) = RecipeValidator.validate(recipe)

  @Test
  fun `a good recipe has no errors`() {
    assertTrue(errors(valid()).isEmpty())
  }

  @Test
  fun `the name is required once trimmed`() {
    assertTrue(errors(valid().copy(name = "")).contains(RecipeError.NameRequired))
    assertTrue(errors(valid().copy(name = "   ")).contains(RecipeError.NameRequired))
    assertTrue(errors(valid().copy(name = " V60 ")).isEmpty())
  }

  @Test
  fun `the dose goes from 5 to 60 g with one decimal at most`() {
    assertTrue(errors(valid().copy(doseG = 5.0)).isEmpty())
    assertTrue(errors(valid().copy(doseG = 60.0)).isEmpty())
    assertTrue(errors(valid().copy(doseG = 16.4)).isEmpty())
    assertEquals(listOf(RecipeError.DoseInvalid), errors(valid().copy(doseG = 4.9)))
    assertEquals(listOf(RecipeError.DoseInvalid), errors(valid().copy(doseG = 60.1)))
    assertEquals(listOf(RecipeError.DoseInvalid), errors(valid().copy(doseG = 15.25)))
    assertEquals(listOf(RecipeError.DoseInvalid), errors(valid().copy(doseG = Double.NaN)))
  }

  @Test
  fun `from 1 to 20 stages`() {
    assertEquals(listOf(RecipeError.StageCountInvalid), errors(valid().copy(stages = emptyList())))
    val twenty = (0 until 20).map { stage(it * 30, 40.0 + it * 10, 10) }
    assertTrue(errors(valid().copy(stages = twenty)).isEmpty())
    val twentyOne = (0 until 21).map { stage(it * 30, 40.0 + it * 10, 10) }
    assertEquals(listOf(RecipeError.StageCountInvalid), errors(valid().copy(stages = twentyOne)))
  }

  @Test
  fun `a recipe needs at least one pour`() {
    assertEquals(
      listOf(RecipeError.NoPour),
      errors(recipeOf(stage(0, 0.0, 10, type = StageType.WAIT))),
    )
    assertEquals(
      listOf(RecipeError.NoPour),
      errors(recipeOf(stage(0, 0.0, 10, type = StageType.SWIRL), stage(10, 0.0, 10, type = StageType.WAIT))),
    )
  }

  @Test
  fun `water only grows between pours and a wait with the same total is valid`() {
    assertTrue(
      errors(recipeOf(stage(0, 40.0, 10), stage(10, 40.0, 30, type = StageType.WAIT), stage(40, 100.0, 10))).isEmpty(),
    )
    assertTrue(
      errors(recipeOf(stage(0, 40.0, 10), stage(10, 0.0, 5, type = StageType.SWIRL), stage(15, 100.0, 10))).isEmpty(),
    )
    assertEquals(
      listOf(RecipeError.WaterNotIncreasing(2)),
      errors(recipeOf(stage(0, 40.0, 10), stage(10, 40.0, 30, type = StageType.WAIT), stage(40, 40.0, 10))),
    )
  }

  @Test
  fun `the instruction has 200 characters at most`() {
    assertTrue(errors(recipeOf(stage(0, 40.0, 10, instruction = "a".repeat(200)))).isEmpty())
    assertEquals(
      listOf(RecipeError.InstructionTooLong(0)),
      errors(recipeOf(stage(0, 40.0, 10, instruction = "a".repeat(201)))),
    )
  }

  @Test
  fun `the duration depends on the type, a wait goes up to 600 s`() {
    val pour = stage(0, 40.0, 10)
    fun waitOf(seconds: Long) = RecipeStage(null, 10_000, 40.0, seconds * 1_000, StageType.WAIT)
    fun swirlOf(seconds: Long) = RecipeStage(null, 10_000, 40.0, seconds * 1_000, StageType.SWIRL)
    assertTrue(errors(recipeOf(pour, waitOf(600))).isEmpty())
    assertEquals(listOf(RecipeError.DurationOutOfRange(1)), errors(recipeOf(pour, waitOf(601))))
    assertEquals(listOf(RecipeError.DurationOutOfRange(1)), errors(recipeOf(pour, waitOf(0))))
    assertTrue(errors(recipeOf(pour, swirlOf(120))).isEmpty())
    assertEquals(listOf(RecipeError.DurationOutOfRange(1)), errors(recipeOf(pour, swirlOf(121))))
  }

  @Test
  fun `a swirl that starts before the previous pour ends overlaps`() {
    assertEquals(
      listOf(RecipeError.StartOverlaps(1)),
      errors(recipeOf(stage(0, 40.0, 10), stage(5, 40.0, 5, type = StageType.SWIRL))),
    )
  }

  @Test
  fun `accumulated water is positive, strictly increasing and at most 1000 g`() {
    assertEquals(listOf(RecipeError.WaterOutOfRange(0)), errors(recipeOf(stage(0, 0.0, 10))))
    assertTrue(errors(recipeOf(stage(0, 0.1, 10))).isEmpty())
    assertTrue(errors(recipeOf(stage(0, 1000.0, 10))).isEmpty())
    assertEquals(listOf(RecipeError.WaterOutOfRange(0)), errors(recipeOf(stage(0, 1000.1, 10))))
    assertEquals(
      listOf(RecipeError.WaterNotIncreasing(1)),
      errors(recipeOf(stage(0, 100.0, 10), stage(30, 100.0, 10))),
    )
    assertEquals(
      listOf(RecipeError.WaterNotIncreasing(1)),
      errors(recipeOf(stage(0, 100.0, 10), stage(30, 90.0, 10))),
    )
  }

  @Test
  fun `the first stage starts at 0 or later and each one at the end of the previous or later`() {
    assertEquals(listOf(RecipeError.StartNegative(0)), errors(recipeOf(RecipeStage(null, -1, 40.0, 10_000))))
    assertTrue(errors(recipeOf(stage(0, 40.0, 10), stage(10, 80.0, 10))).isEmpty()) // justo al terminar
    assertEquals(
      listOf(RecipeError.StartOverlaps(1)),
      errors(recipeOf(stage(0, 40.0, 10), RecipeStage(null, 9_999, 80.0, 10_000))),
    )
  }

  @Test
  fun `the pour lasts from 1 to 120 s`() {
    assertEquals(listOf(RecipeError.DurationOutOfRange(0)), errors(recipeOf(RecipeStage(null, 0, 40.0, 999))))
    assertTrue(errors(recipeOf(RecipeStage(null, 0, 40.0, 1_000))).isEmpty())
    assertTrue(errors(recipeOf(RecipeStage(null, 0, 40.0, 120_000))).isEmpty())
    assertEquals(listOf(RecipeError.DurationOutOfRange(0)), errors(recipeOf(RecipeStage(null, 0, 40.0, 120_001))))
  }

  @Test
  fun `the last stage must start before 10 minutes`() {
    assertTrue(errors(recipeOf(RecipeStage(null, 599_999, 40.0, 10_000))).isEmpty())
    assertEquals(listOf(RecipeError.LastStartTooLate), errors(recipeOf(RecipeStage(null, 600_000, 40.0, 10_000))))
  }

  @Test
  fun `the temperature is optional, from 0 to 100 with one decimal`() {
    assertTrue(errors(valid().copy(waterTempC = null)).isEmpty())
    assertTrue(errors(valid().copy(waterTempC = 0.0)).isEmpty())
    assertTrue(errors(valid().copy(waterTempC = 100.0)).isEmpty())
    assertTrue(errors(valid().copy(waterTempC = 93.5)).isEmpty())
    assertEquals(listOf(RecipeError.TempInvalid), errors(valid().copy(waterTempC = -0.1)))
    assertEquals(listOf(RecipeError.TempInvalid), errors(valid().copy(waterTempC = 100.1)))
    assertEquals(listOf(RecipeError.TempInvalid), errors(valid().copy(waterTempC = 93.45)))
  }

  @Test
  fun `the method does not matter`() {
    assertTrue(errors(valid().copy(method = null)).isEmpty())
    assertTrue(errors(valid().copy(method = BrewMethod.OTHER, methodOther = null)).isEmpty())
  }

  @Test
  fun `an action lasts from 1 to 120 s`() {
    val pour = stage(0, 40.0, 10)
    fun actionOf(seconds: Long) = RecipeStage(null, 10_000, 40.0, seconds * 1_000, StageType.ACTION)
    assertTrue(errors(recipeOf(pour, actionOf(1))).isEmpty())
    assertTrue(errors(recipeOf(pour, actionOf(120))).isEmpty())
    assertEquals(listOf(RecipeError.DurationOutOfRange(1)), errors(recipeOf(pour, actionOf(0))))
    assertEquals(listOf(RecipeError.DurationOutOfRange(1)), errors(recipeOf(pour, actionOf(121))))
  }

  @Test
  fun `a recipe with only actions, or with actions and waits, has no pour`() {
    assertEquals(listOf(RecipeError.NoPour), errors(recipeOf(stage(0, 0.0, 10, type = StageType.ACTION))))
    assertEquals(
      listOf(RecipeError.NoPour),
      errors(recipeOf(stage(0, 0.0, 10, type = StageType.ACTION), stage(10, 0.0, 10, type = StageType.WAIT))),
    )
  }

  @Test
  fun `an action without instruction is valid`() {
    val recipe = recipeOf(stage(0, 40.0, 10), stage(10, 40.0, 5, type = StageType.ACTION, instruction = null))
    assertTrue(errors(recipe).isEmpty())
    assertTrue(errors(aeroPressWithActions()).isEmpty())
  }

  @Test
  fun `an action that starts before the previous step ends overlaps`() {
    assertEquals(
      listOf(RecipeError.StartOverlaps(1)),
      errors(recipeOf(stage(0, 40.0, 10), stage(5, 40.0, 5, type = StageType.ACTION))),
    )
  }
}
