package dev.dotscale.brew

import dev.dotscale.recipe.StageGaps
import dev.dotscale.recipe.StageType
import dev.dotscale.recipe.recipeOf
import dev.dotscale.recipe.stage
import dev.dotscale.recipe.v60Classic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TargetCurveTest {

  private fun sample(tMs: Long, waterG: Double, valid: Boolean = true) = BrewSample(tMs, waterG, 0.0, null, valid)

  @Test
  fun `fromRecipe draws a ramp per pour and a plateau per wait`() {
    val curve = TargetCurve.fromRecipe(v60Classic())
    assertEquals(
      listOf(
        TargetPoint(0, 0.0),
        TargetPoint(10_000, 40.0),
        TargetPoint(45_000, 40.0),
        TargetPoint(65_000, 150.0),
        TargetPoint(90_000, 150.0),
        TargetPoint(115_000, 250.0),
      ),
      curve.points,
    )
    assertEquals(listOf(0L, 10_000L, 45_000L, 65_000L, 90_000L), curve.stages.map { it.startMs })
    assertEquals(listOf(10_000L, 45_000L, 65_000L, 90_000L, 115_000L), curve.stages.map { it.endMs })
    assertEquals(listOf(0, 1, 2, 3, 4), curve.stages.map { it.index })
    assertEquals(
      listOf(StageType.POUR, StageType.WAIT, StageType.POUR, StageType.WAIT, StageType.POUR),
      curve.stages.map { it.type },
    )
    assertEquals(listOf(40.0, 40.0, 150.0, 150.0, 250.0), curve.stages.map { it.targetWaterG })
    assertEquals(115_000L, curve.endMs)
    assertEquals(115_000L, curve.lastStepEndMs)
    assertEquals(250.0, curve.finalWaterG, 0.0)
  }

  @Test
  fun `the stages carry the type and the instruction of every step`() {
    val curve = TargetCurve.fromRecipe(
      recipeOf(
        stage(0, 40.0, 10, "Bloom", instruction = "Despacio"),
        stage(10, 40.0, 5, type = StageType.SWIRL, instruction = "Gira"),
        stage(15, 40.0, 20, type = StageType.WAIT),
      ),
    )
    assertEquals(listOf("Despacio", "Gira", null), curve.stages.map { it.instruction })
    assertEquals(listOf(StageType.POUR, StageType.SWIRL, StageType.WAIT), curve.stages.map { it.type })
  }

  @Test
  fun `a swirl and a wait are plateaus`() {
    val curve = TargetCurve.fromRecipe(
      recipeOf(
        stage(0, 40.0, 10),
        stage(10, 40.0, 5, type = StageType.SWIRL),
        stage(15, 40.0, 20, type = StageType.WAIT),
        stage(35, 100.0, 10),
      ),
    )
    assertEquals(40.0, curve.waterAt(12_000), 0.0)
    assertEquals(40.0, curve.waterAt(30_000), 0.0)
    assertEquals(70.0, curve.waterAt(40_000), 1e-9)
  }

  @Test
  fun `endMs is the end of the last pour even when the recipe ends with a wait`() {
    val curve = TargetCurve.fromRecipe(
      recipeOf(stage(0, 40.0, 10), stage(10, 40.0, 30, type = StageType.WAIT)),
    )
    assertEquals(10_000L, curve.endMs)
    assertEquals(40_000L, curve.lastStepEndMs)
    assertEquals(40.0, curve.finalWaterG, 0.0)
  }

  @Test
  fun `filling the gaps with waits does not change the points`() {
    val withGaps = recipeOf(stage(5, 40.0, 10), stage(30, 100.0, 10), stage(50, 150.0, 10))
    val filled = withGaps.copy(stages = StageGaps.fillWaits(withGaps.stages))
    assertEquals(TargetCurve.fromRecipe(withGaps).points, TargetCurve.fromRecipe(filled).points)
    val noInitialGap = recipeOf(stage(0, 40.0, 10), stage(30, 100.0, 10))
    val filled2 = noInitialGap.copy(stages = StageGaps.fillWaits(noInitialGap.stages))
    assertEquals(TargetCurve.fromRecipe(noInitialGap).points, TargetCurve.fromRecipe(filled2).points)
  }

  @Test
  fun `fromRecipe adds the origin when the first stage starts after zero`() {
    val curve = TargetCurve.fromRecipe(recipeOf(stage(5, 40.0, 10)))
    assertEquals(
      listOf(TargetPoint(0, 0.0), TargetPoint(5_000, 0.0), TargetPoint(15_000, 40.0)),
      curve.points,
    )
    assertEquals(0.0, curve.waterAt(5_000), 0.0)
  }

  @Test
  fun `fromRecipe does not repeat a time when a stage starts where the previous one ends`() {
    val curve = TargetCurve.fromRecipe(recipeOf(stage(0, 40.0, 10), stage(10, 100.0, 10)))
    assertEquals(
      listOf(TargetPoint(0, 0.0), TargetPoint(10_000, 40.0), TargetPoint(20_000, 100.0)),
      curve.points,
    )
    assertEquals(curve.points.map { it.tMs }.distinct(), curve.points.map { it.tMs })
  }

  @Test
  fun `waterAt is zero before the curve, interpolates inside and holds the last value after`() {
    val curve = TargetCurve.fromRecipe(v60Classic())
    assertEquals(0.0, curve.waterAt(-1), 0.0)
    assertEquals(0.0, curve.waterAt(0), 0.0)
    assertEquals(20.0, curve.waterAt(5_000), 1e-9)
    assertEquals(40.0, curve.waterAt(10_000), 0.0)
    assertEquals(40.0, curve.waterAt(30_000), 0.0)
    assertEquals(95.0, curve.waterAt(55_000), 1e-9)
    assertEquals(150.0, curve.waterAt(80_000), 0.0)
    assertEquals(250.0, curve.waterAt(115_000), 0.0)
    assertEquals(250.0, curve.waterAt(10_000_000), 0.0)
  }

  @Test
  fun `waterAt before the first point of a curve that starts late is zero`() {
    val curve = TargetCurve(listOf(TargetPoint(1_000, 5.0), TargetPoint(2_000, 10.0)), listOf(TargetStage(0, null, 1_000, 2_000, 10.0)))
    assertEquals(0.0, curve.waterAt(999), 0.0)
    assertEquals(5.0, curve.waterAt(1_000), 0.0)
    assertEquals(7.5, curve.waterAt(1_500), 1e-9)
  }

  @Test
  fun `fromBrew keeps the last valid sample of every 250 ms bucket`() {
    val samples = (0..30).map { sample(it * 100L, it * 1.0) } // 0..3000 ms
    val curve = TargetCurve.fromBrew(samples, emptyList(), 3_000)!!
    // Tramos [0,250) → 200; [250,500) → 400; [500,750) → 700...; el último, 3000.
    assertEquals(listOf(200L, 400L, 700L, 900L, 1_200L, 1_400L, 1_700L, 1_900L, 2_200L, 2_400L, 2_700L, 2_900L, 3_000L), curve.points.map { it.tMs })
    assertEquals(3_000.0 / 100, curve.points.last().waterG, 0.0)
  }

  @Test
  fun `fromBrew skips invalid samples`() {
    val samples = listOf(sample(0, 0.0), sample(1_000, 99.0, valid = false), sample(2_000, 10.0))
    val curve = TargetCurve.fromBrew(samples, emptyList(), 2_000)!!
    assertEquals(listOf(0L, 2_000L), curve.points.map { it.tMs })
  }

  @Test
  fun `fromBrew builds stages from the pours with accumulated water`() {
    // Agua = 5 g/s entre 0 y 4 s (20 g), quieta hasta 10 s, y 5 g/s entre 10 y 14 s (40 g).
    val samples = (0..140).map { i ->
      val t = i * 100L
      val water = when {
        t <= 4_000 -> t * 0.005
        t <= 10_000 -> 20.0
        else -> 20.0 + (t - 10_000) * 0.005
      }
      sample(t, water)
    }
    val pours = listOf(Pour(1, 0, 4_000, 20.0), Pour(2, 10_000, 14_000, 20.0))
    val curve = TargetCurve.fromBrew(samples, pours, 14_000)!!
    assertEquals(2, curve.stages.size)
    assertEquals(0L, curve.stages[0].startMs)
    assertEquals(4_000L, curve.stages[0].endMs)
    assertEquals(20.0, curve.stages[0].targetWaterG, 0.5)
    assertEquals(10_000L, curve.stages[1].startMs)
    // Acumulada: 40 g, no los 20 g del propio vertido.
    assertEquals(40.0, curve.stages[1].targetWaterG, 0.3)
    assertEquals(40.0, curve.finalWaterG, 0.3)
    assertEquals(14_000L, curve.endMs)
    assertTrue(curve.stages.all { it.name == null })
  }

  @Test
  fun `fromBrew without pours makes a single stage from zero to the total time`() {
    val samples = (0..50).map { sample(it * 100L, it * 0.1) }
    val curve = TargetCurve.fromBrew(samples, emptyList(), 5_000)!!
    assertEquals(1, curve.stages.size)
    assertEquals(0L, curve.stages.single().startMs)
    assertEquals(5_000L, curve.stages.single().endMs)
    assertEquals(5.0, curve.stages.single().targetWaterG, 1e-9)
  }

  @Test
  fun `fromBrew returns null without valid samples`() {
    assertNull(TargetCurve.fromBrew(emptyList(), emptyList(), 1_000))
    assertNull(TargetCurve.fromBrew(listOf(sample(0, 1.0, valid = false)), emptyList(), 1_000))
  }

  @Test
  fun `scaled multiplies the water of points and stages but not the times`() {
    val curve = TargetCurve.fromRecipe(v60Classic())
    val scaled = curve.scaled(16.4 / 15)
    assertEquals(curve.points.map { it.tMs }, scaled.points.map { it.tMs })
    assertEquals(curve.stages.map { it.startMs }, scaled.stages.map { it.startMs })
    assertEquals(curve.stages.map { it.endMs }, scaled.stages.map { it.endMs })
    assertEquals(250.0 * 16.4 / 15, scaled.finalWaterG, 1e-9)
    assertEquals(40.0 * 16.4 / 15, scaled.stages[0].targetWaterG, 1e-9)
    assertEquals(curve.waterAt(55_000) * 16.4 / 15, scaled.waterAt(55_000), 1e-9)
    assertEquals(curve, curve.scaled(1.0))
  }
}
