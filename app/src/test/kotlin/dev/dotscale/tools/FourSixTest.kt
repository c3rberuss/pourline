package dev.dotscale.tools

import dev.dotscale.recipe.BuiltInRecipes
import dev.dotscale.recipe.FakeRecipeTexts
import dev.dotscale.recipe.RecipeStage
import dev.dotscale.recipe.RecipeValidator
import dev.dotscale.recipe.StageType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Textos falsos y deterministas: no necesitan `Resources`. */
class FakeFourSixTexts : FourSixTexts {
  override fun recipeName(taste: FourSixTaste, strength: FourSixStrength) = "4:6 · $taste · $strength"

  override fun pourName(index: Int) = "V${index + 1}"

  override fun pourInstruction(phase: FourSixPhase, last: Boolean) = if (last) "last" else phase.name

  override fun waitInstruction() = "wait"

  override fun drainInstruction() = "drain"

  override fun notes() = "notes"
}

class FourSixTest {
  private fun plan(
    dose: Double = 20.0,
    ratio: Double = 15.0,
    taste: FourSixTaste = FourSixTaste.BALANCED,
    strength: FourSixStrength = FourSixStrength.STANDARD,
  ) = FourSix.plan(FourSixParams(dose, ratio, taste, strength))!!

  @Test
  fun theClassicIsFivePoursOfSixtyGramsInThreeAndAHalfMinutes() {
    val plan = plan()
    assertEquals(listOf(60.0, 60.0, 60.0, 60.0, 60.0), plan.pours.map { it.pourG })
    assertEquals(listOf(60.0, 120.0, 180.0, 240.0, 300.0), plan.pours.map { it.totalG })
    assertEquals(listOf(0L, 45_000L, 90_000L, 135_000L, 180_000L), plan.pours.map { it.startMs })
    assertTrue(plan.pours.all { it.durationMs == 10_000L })
    assertEquals(210_000L, plan.totalTimeMs)
    assertEquals(300.0, plan.totalWaterG, 0.0)
  }

  @Test
  fun theClassicMatchesTheBuiltInRecipe() {
    val builtIn = BuiltInRecipes.recipe(BuiltInRecipes.FOUR_SIX, FakeRecipeTexts())
    val generated = FourSix.recipe(plan(), FakeFourSixTexts())
    val pours = { stages: List<RecipeStage> ->
      stages.filter { it.type == StageType.POUR }.map { Triple(it.startMs, it.targetWaterG, it.durationMs) }
    }
    assertEquals(pours(builtIn.stages), pours(generated.stages))
    assertEquals(builtIn.doseG, generated.doseG, 0.0)
  }

  @Test
  fun theTasteSplitsTheFirstFortyPercent() {
    val expected = mapOf(
      FourSixTaste.SWEET to listOf(40.0, 80.0),
      FourSixTaste.MEDIUM_SWEET to listOf(50.0, 70.0),
      FourSixTaste.BALANCED to listOf(60.0, 60.0),
      FourSixTaste.MEDIUM_BRIGHT to listOf(70.0, 50.0),
      FourSixTaste.BRIGHT to listOf(80.0, 40.0),
    )
    expected.forEach { (taste, firstTwo) ->
      val pours = plan(taste = taste).pours
      assertEquals("$taste", firstTwo, pours.take(2).map { it.pourG })
      assertTrue(pours.take(2).all { it.phase == FourSixPhase.TASTE })
    }
  }

  @Test
  fun theStrengthSplitsTheRemainingSixtyPercentInEqualPours() {
    val expected = mapOf(
      FourSixStrength.LIGHTEST to listOf(180.0),
      FourSixStrength.LIGHT to listOf(90.0, 90.0),
      FourSixStrength.STANDARD to listOf(60.0, 60.0, 60.0),
      FourSixStrength.STRONG to listOf(45.0, 45.0, 45.0, 45.0),
      FourSixStrength.STRONGEST to listOf(36.0, 36.0, 36.0, 36.0, 36.0),
    )
    expected.forEach { (strength, rest) ->
      val pours = plan(strength = strength).pours
      assertEquals("$strength", 2 + strength.pours, pours.size)
      assertEquals("$strength", rest, pours.drop(2).map { it.pourG })
      assertTrue(pours.drop(2).all { it.phase == FourSixPhase.STRENGTH })
      assertEquals(300.0, pours.last().totalG, 0.0)
    }
  }

  @Test
  fun morePoursTakeLonger() {
    assertEquals(135_000L, plan(strength = FourSixStrength.LIGHTEST).totalTimeMs)
    assertEquals(300_000L, plan(strength = FourSixStrength.STRONGEST).totalTimeMs)
  }

  @Test
  fun aBigSinglePourLastsLongerAndStillDrains() {
    // 180 g a 6 g/s son 30 s; el filtro se retira 15 s después de terminar, no a los 30 s del inicio.
    val plan = plan(strength = FourSixStrength.LIGHTEST)
    assertEquals(30_000L, plan.pours.last().durationMs)
    assertEquals(plan.pours.last().startMs + 45_000L, plan.totalTimeMs)
  }

  @Test
  fun bigDosesSpaceThePoursSoTheyNeverOverlap() {
    val plan = plan(dose = 60.0, ratio = 16.0, strength = FourSixStrength.LIGHTEST)
    // 960 g: el único vertido de intensidad es de 576 g, que se recorta a 60 s.
    assertEquals(60_000L, plan.pours.last().durationMs)
    plan.pours.zipWithNext().forEach { (a, b) -> assertTrue(b.startMs >= a.endMs + 10_000L) }
  }

  @Test
  fun totalsAreRoundedToTheGramAndAddUp() {
    val plan = plan(dose = 15.5, ratio = 15.0, taste = FourSixTaste.SWEET, strength = FourSixStrength.STRONGEST)
    assertEquals(233.0, plan.totalWaterG, 0.0) // 232,5 → 233
    plan.pours.forEach { assertEquals(it.totalG, Math.rint(it.totalG), 0.0) }
    assertEquals(plan.totalWaterG, plan.pours.sumOf { it.pourG }, 1e-9)
  }

  @Test
  fun outOfRangeInputsGiveNoPlan() {
    assertNull(FourSix.plan(FourSixParams(4.9, 15.0)))
    assertNull(FourSix.plan(FourSixParams(60.1, 15.0)))
    assertNull(FourSix.plan(FourSixParams(20.0, 9.9)))
    assertNull(FourSix.plan(FourSixParams(20.0, 20.1)))
    // 60 g a 1:17 son 1020 g: más de lo que admite una receta.
    assertNull(FourSix.plan(FourSixParams(60.0, 17.0)))
    assertNotNull(FourSix.plan(FourSixParams(50.0, 20.0)))
  }

  @Test
  fun everyCombinationGivesAValidRecipe() {
    val doses = listOf(5.0, 12.5, 20.0, 33.3, 50.0)
    val ratios = listOf(10.0, 15.0, 17.5, 20.0)
    for (dose in doses) for (ratio in ratios) for (taste in FourSixTaste.entries) for (strength in FourSixStrength.entries) {
      val plan = FourSix.plan(FourSixParams(dose, ratio, taste, strength)) ?: continue
      val recipe = FourSix.recipe(plan, FakeFourSixTexts())
      assertEquals("$dose 1:$ratio $taste $strength", emptyList<Any>(), RecipeValidator.validate(recipe))
      assertEquals(plan.totalTimeMs, recipe.stages.last().endMs)
      assertEquals(plan.totalWaterG, recipe.totalWaterG, 0.0)
    }
  }

  @Test
  fun theRecipeAlternatesPoursAndWaitsAndEndsDraining() {
    val recipe = FourSix.recipe(plan(), FakeFourSixTexts())
    assertEquals("4:6 · BALANCED · STANDARD", recipe.name)
    assertEquals(10, recipe.stages.size)
    recipe.stages.forEachIndexed { i, stage -> assertEquals(if (i % 2 == 0) StageType.POUR else StageType.WAIT, stage.type) }
    assertEquals(listOf("V1", "V2", "V3", "V4", "V5"), recipe.stages.filter { it.type == StageType.POUR }.map { it.name })
    assertEquals(listOf("TASTE", "TASTE", "STRENGTH", "STRENGTH", "last"), recipe.stages.filter { it.type == StageType.POUR }.map { it.instruction })
    assertEquals("drain", recipe.stages.last().instruction)
    assertEquals(20_000L, recipe.stages.last().durationMs)
    // Cada espera llega justo al siguiente vertido.
    recipe.stages.zipWithNext().forEach { (a, b) -> assertEquals(a.endMs, b.startMs) }
    assertEquals(FourSix.WATER_TEMP_C, recipe.waterTempC!!, 0.0)
    assertEquals(20.0, recipe.doseG, 0.0)
    assertEquals(15.0, recipe.ratio, 0.0)
  }
}
