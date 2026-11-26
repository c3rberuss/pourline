package dev.josueamaya.pourline.tools

import dev.josueamaya.pourline.recipe.RecipeStage
import dev.josueamaya.pourline.recipe.RecipeValidator
import dev.josueamaya.pourline.recipe.StageType
import dev.josueamaya.pourline.recipe.fourSixClassic
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
    pace: FourSixPace = FourSixPace.NORMAL,
    rate: Double = pace.gramsPerSecond!!,
  ) = FourSix.plan(FourSixParams(dose, ratio, taste, strength, rate))!!

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
  fun theClassicMatchesTheFormerBuiltInRecipe() {
    val generated = FourSix.recipe(plan(), FakeFourSixTexts())
    val pours = { stages: List<RecipeStage> ->
      stages.filter { it.type == StageType.POUR }.map { Triple(it.startMs, it.targetWaterG, it.durationMs) }
    }
    // Los vertidos de la antigua receta de fábrica 4:6 (20 g, 300 g).
    val former = listOf(
      Triple(0L, 60.0, 10_000L),
      Triple(45_000L, 120.0, 10_000L),
      Triple(90_000L, 180.0, 10_000L),
      Triple(135_000L, 240.0, 10_000L),
      Triple(180_000L, 300.0, 10_000L),
    )
    assertEquals(former, pours(generated.stages))
    assertEquals(20.0, generated.doseG, 0.0)
    assertEquals(former, pours(fourSixClassic().stages))
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
    // 960 g: el único vertido de intensidad es de 576 g; a 6 g/s, 96 s.
    assertEquals(96_000L, plan.pours.last().durationMs)
    plan.pours.zipWithNext().forEach { (a, b) -> assertTrue(b.startMs >= a.endMs + 10_000L) }
  }

  @Test
  fun aSlowerPaceLengthensThePoursAndShortensTheWaitsWithoutMovingTheStarts() {
    val normal = plan()
    val slow = plan(pace = FourSixPace.SLOW)
    val slowest = plan(pace = FourSixPace.SLOWEST)
    assertTrue(slow.pours.all { it.durationMs == 15_000L })
    assertTrue(slowest.pours.all { it.durationMs == 20_000L })
    assertEquals(normal.pours.map { it.startMs }, slow.pours.map { it.startMs })
    assertEquals(normal.pours.map { it.startMs }, slowest.pours.map { it.startMs })
    assertEquals(210_000L, slow.totalTimeMs)
    // El último vertido acaba a 3:20 y se dejan 15 s de drenado.
    assertEquals(215_000L, slowest.totalTimeMs)
    // Las esperas de la receta encajan solas: 45 s − 20 s entre vertidos y 15 s de drenado al final.
    val waits = FourSix.recipe(slowest, FakeFourSixTexts()).stages.filter { it.type == StageType.WAIT }.map { it.durationMs }
    assertEquals(listOf(25_000L, 25_000L, 25_000L, 25_000L, 15_000L), waits)
  }

  @Test
  fun aSlowerPaceAlsoSlowsSmallPours() {
    // 36 g a 6 g/s se quedarían en 6 s: el mínimo es lo que se tarda en verter 60 g al ritmo elegido.
    assertTrue(plan(strength = FourSixStrength.STRONGEST).pours.all { it.durationMs == 10_000L })
    assertTrue(plan(strength = FourSixStrength.STRONGEST, pace = FourSixPace.SLOW).pours.all { it.durationMs == 15_000L })
  }

  @Test
  fun aVerySlowBigPourDelaysTheNextOnes() {
    // 60 g a 1:12, muy suave: vertidos de 144, 144 y 432 g. A 3 g/s, 48 s (más 10 s de espera) no caben en 45 s.
    val plan = plan(dose = 60.0, ratio = 12.0, strength = FourSixStrength.LIGHTEST, pace = FourSixPace.SLOWEST)
    // El último, 144 s, se queda en el máximo de un paso: 2 min.
    assertEquals(listOf(48_000L, 48_000L, 120_000L), plan.pours.map { it.durationMs })
    assertEquals(listOf(0L, 58_000L, 116_000L), plan.pours.map { it.startMs })
    assertEquals(116_000L + 135_000L, plan.totalTimeMs)
  }

  @Test
  fun aCustomRateIsHonouredUpToTwoMinutesAPour() {
    // 2,5 g/s: 60 g en 24 s; el vertido único de 180 g, en 72 s, retrasa el final.
    val plan = plan(strength = FourSixStrength.LIGHTEST, rate = 2.5)
    assertEquals(listOf(24_000L, 24_000L, 72_000L), plan.pours.map { it.durationMs })
    assertEquals(listOf(0L, 45_000L, 90_000L), plan.pours.map { it.startMs })
    // A 2 g/s, 576 g serían 288 s: se queda en el máximo de un paso, 2 min.
    assertEquals(120_000L, plan(dose = 60.0, ratio = 16.0, strength = FourSixStrength.LIGHTEST, rate = 2.0).pours.last().durationMs)
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
    assertNull(FourSix.plan(FourSixParams(20.0, 15.0, pourRateGPerS = 1.9)))
    assertNull(FourSix.plan(FourSixParams(20.0, 15.0, pourRateGPerS = 15.1)))
    // 60 g a 1:17 son 1020 g: más de lo que admite una receta.
    assertNull(FourSix.plan(FourSixParams(60.0, 17.0)))
    assertNotNull(FourSix.plan(FourSixParams(50.0, 20.0)))
  }

  @Test
  fun everyCombinationGivesAValidRecipe() {
    val doses = listOf(5.0, 12.5, 20.0, 33.3, 50.0, 60.0)
    val ratios = listOf(10.0, 15.0, 16.6, 17.5, 20.0)
    val rates = listOf(FourSix.MIN_POUR_RATE_G_PER_S, 3.0, 4.5, 6.0, FourSix.MAX_POUR_RATE_G_PER_S)
    for (dose in doses) for (ratio in ratios) for (taste in FourSixTaste.entries) for (strength in FourSixStrength.entries) for (rate in rates) {
      val plan = FourSix.plan(FourSixParams(dose, ratio, taste, strength, rate)) ?: continue
      val recipe = FourSix.recipe(plan, FakeFourSixTexts())
      assertEquals("$dose 1:$ratio $taste $strength $rate g/s", emptyList<Any>(), RecipeValidator.validate(recipe))
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
