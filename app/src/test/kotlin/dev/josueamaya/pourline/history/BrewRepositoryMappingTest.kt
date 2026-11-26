package dev.josueamaya.pourline.history

import dev.josueamaya.pourline.brew.BrewConfig
import dev.josueamaya.pourline.brew.BrewEffect
import dev.josueamaya.pourline.brew.BrewFit
import dev.josueamaya.pourline.brew.FinishReason
import dev.josueamaya.pourline.brew.FinishedBrew
import dev.josueamaya.pourline.brew.PlannedAmounts
import dev.josueamaya.pourline.brew.TargetCurve
import dev.josueamaya.pourline.brew.TimedSession
import dev.josueamaya.pourline.protocol.ScaleModel
import dev.josueamaya.pourline.protocol.WeightUnit
import dev.josueamaya.pourline.recipe.v60Classic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Qué columnas de `brews` se rellenan al guardar una preparación sin báscula y con báscula (SPEC-007 §3.5). */
class BrewRepositoryMappingTest {

  private val preview = ByteArray(BrewPreview.SIZE)

  private fun scalelessFinished(amounts: PlannedAmounts): FinishedBrew {
    val session = TimedSession()
    session.arm(amounts)
    session.start(1_000)
    return (session.finish(116_000).single() as BrewEffect.Finished).brew
  }

  private fun v60Target(): UsedTarget {
    val curve = TargetCurve.fromRecipe(v60Classic())
    // Sin muestras, `brewFit` da `score = null`, el agua final y el fin del objetivo.
    return UsedTarget(ReferenceType.RECIPE, 7, "V60 clásica", curve, BrewFit(null, curve.finalWaterG, curve.endMs))
  }

  private fun scalelessContext(target: UsedTarget? = null, details: BrewDetails? = null) = BrewContext(
    startedAtEpochMs = 1_700_000_000_000,
    unit = WeightUnit.GRAMS,
    scaleModel = null,
    simulated = false,
    target = target,
    details = details,
    scaleless = true,
  )

  @Test
  fun `a scaleless brew with a recipe fills every column of the plan`() {
    val target = v60Target()

    val entity = brewEntity(scalelessFinished(PlannedAmounts(15.0, 250.0)), scalelessContext(target), preview)

    assertTrue(entity.scaleless)
    assertEquals(1_700_000_000_000, entity.startedAtEpochMs)
    assertEquals(115_000L, entity.totalTimeMs)
    assertEquals(250.0, entity.totalWaterG, 0.0)
    assertEquals(15.0, entity.doseG!!, 0.0)
    assertEquals(250.0 / 15.0, entity.finalRatio!!, 1e-9)
    assertEquals(0.0, entity.avgFlowGps, 0.0)
    assertEquals(0.0, entity.maxFlowGps, 0.0)
    assertNull(entity.stabilityScore)
    assertNull(entity.stabilityLevel)
    assertEquals(0.0, entity.shareStable, 0.0)
    assertEquals(0.0, entity.shareIrregular, 0.0)
    assertEquals(0.0, entity.shareTurbulent, 0.0)
    assertEquals(FinishReason.MANUAL, entity.finishReason)
    assertFalse(entity.interrupted)
    assertEquals(WeightUnit.GRAMS, entity.unit)
    assertNull(entity.scaleModel)
    assertFalse(entity.simulated)
    assertEquals(BrewConfig.METRIC_VERSION, entity.metricVersion)
    assertEquals(ByteArray(48).toList(), entity.preview.toList())
    assertEquals(ReferenceType.RECIPE, entity.referenceType)
    assertEquals(7L, entity.referenceId)
    assertEquals("V60 clásica", entity.referenceName)
    assertEquals(250.0, entity.targetWaterG!!, 0.0)
    assertEquals(target.curve.endMs, entity.targetEndMs)
    assertNull(entity.fitScore)
  }

  @Test
  fun `a scaleless brew without a recipe has no reference columns`() {
    val entity = brewEntity(scalelessFinished(PlannedAmounts(15.0, 240.0)), scalelessContext(), preview)

    assertTrue(entity.scaleless)
    assertEquals(240.0, entity.totalWaterG, 0.0)
    assertEquals(16.0, entity.finalRatio!!, 1e-9)
    assertNull(entity.referenceType)
    assertNull(entity.referenceId)
    assertNull(entity.referenceName)
    assertNull(entity.targetWaterG)
    assertNull(entity.targetEndMs)
    assertNull(entity.fitScore)
  }

  @Test
  fun `a scaleless brew without a dose has no dose and no ratio`() {
    val entity = brewEntity(scalelessFinished(PlannedAmounts(null, 250.0)), scalelessContext(v60Target()), preview)

    assertNull(entity.doseG)
    assertNull(entity.finalRatio)
    assertEquals(250.0, entity.totalWaterG, 0.0)
  }

  @Test
  fun `the prefill of the recipe is kept in a scaleless brew`() {
    val details = BrewDetails(method = BrewMethod.V60, waterTempC = 93.0, notes = "  ", rating = 9)

    val entity = brewEntity(scalelessFinished(PlannedAmounts(15.0, 250.0)), scalelessContext(details = details), preview)

    assertEquals(BrewMethod.V60, entity.method)
    assertEquals(93.0, entity.waterTempC!!, 0.0)
    assertNull(entity.notes)
    assertNull(entity.rating)
  }

  @Test
  fun `a brew with a scale keeps scaleless false by default`() {
    val context = BrewContext(
      startedAtEpochMs = 1,
      unit = WeightUnit.OUNCES,
      scaleModel = ScaleModel.DOT,
      simulated = true,
    )
    val session = scalelessFinished(PlannedAmounts(15.0, 250.0))

    val entity = brewEntity(session, context, preview)

    assertFalse(context.scaleless)
    assertFalse(entity.scaleless)
    assertEquals(WeightUnit.OUNCES, entity.unit)
    assertEquals(ScaleModel.DOT, entity.scaleModel)
    assertTrue(entity.simulated)
  }
}
