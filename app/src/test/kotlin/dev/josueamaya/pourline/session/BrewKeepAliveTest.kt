package dev.josueamaya.pourline.session

import dev.josueamaya.pourline.brew.BrewPhase
import dev.josueamaya.pourline.brew.BrewState
import dev.josueamaya.pourline.brew.BrewSummary
import dev.josueamaya.pourline.brew.FinishReason
import dev.josueamaya.pourline.brew.GuideState
import dev.josueamaya.pourline.brew.TargetCurve
import dev.josueamaya.pourline.history.BrewDetails
import dev.josueamaya.pourline.history.ReferenceType
import dev.josueamaya.pourline.recipe.StageType
import dev.josueamaya.pourline.recipe.aeroPressWithActions
import dev.josueamaya.pourline.recipe.v60Classic
import dev.josueamaya.pourline.settings.ReferenceKey
import dev.josueamaya.pourline.protocol.WeightUnit
import dev.josueamaya.pourline.scale.ConnectionState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrewKeepAliveTest {

  private val plan = BrewPlan(doseG = 15.0, ratio = 16.0)
  private val summary =
    BrewSummary(60_000, 250.0, 4.0, 5.0, null, null, emptyMap(), emptyList(), null, FinishReason.MANUAL, false)

  private fun open(phase: BrewPhase = BrewPhase.Armed, t0: Long? = null, savedBrew: SavedBrew? = null) = ScaleUiState(
    connection = ConnectionState.Ready,
    plan = plan,
    prep = PrepState(step = PrepStep.DOSE),
    brew = BrewState(phase, emptyList(), currentStability = null, currentLevel = null, t0MonoMs = t0),
    savedBrew = savedBrew,
  )

  private val finished = BrewPhase.Finished(summary, rearmed = false, pourIgnored = false)

  @Test
  fun `no session needs no service`() {
    assertFalse(ScaleUiState().needsKeepAlive())
  }

  @Test
  fun `an open session needs the service from the first step to the pour`() {
    assertTrue(open().needsKeepAlive())
    assertTrue(open(BrewPhase.Active(pouring = true), t0 = 1_000).needsKeepAlive())
  }

  @Test
  fun `a finished session keeps the service only while saving`() {
    assertTrue(open(finished, savedBrew = SavedBrew.Saving).needsKeepAlive())
    assertFalse(open(finished, savedBrew = SavedBrew.Saved(1)).needsKeepAlive())
    assertFalse(open(finished, savedBrew = SavedBrew.Failed).needsKeepAlive())
  }

  @Test
  fun `the prep steps show the step`() {
    val notice = open().toBrewNotice()
    assertEquals(BrewNoticeStage.PREP, notice.stage)
    assertEquals(PrepStep.DOSE, notice.prepStep)
    assertNull(notice.water)
    assertNull(notice.t0MonoMs)
  }

  @Test
  fun `the pour shows the water rounded to the gram and the start`() {
    val notice = open(BrewPhase.Active(pouring = true), t0 = 5_000).copy(weight = 123.46, targetWaterG = 250.0).toBrewNotice()
    assertEquals(BrewNoticeStage.POURING, notice.stage)
    assertEquals(123.0, notice.water!!, 0.0)
    assertEquals(250.0, notice.targetWater!!, 0.0)
    assertEquals(5_000L, notice.t0MonoMs)
    assertNull(notice.prepStep)
  }

  @Test
  fun `ounces keep one decimal`() {
    val notice = open(BrewPhase.Active(pouring = true), t0 = 0)
      .copy(weight = 4.26, unit = WeightUnit.OUNCES, targetWaterG = 283.495)
      .toBrewNotice()
    assertEquals(4.3, notice.water!!, 1e-9)
    assertEquals(10.0, notice.targetWater!!, 1e-9)
  }

  @Test
  fun `small changes in the weight do not change the notice`() {
    val active = open(BrewPhase.Active(pouring = true), t0 = 0)
    assertEquals(active.copy(weight = 100.2).toBrewNotice(), active.copy(weight = 99.8).toBrewNotice())
  }

  @Test
  fun `a lost scale is shown before saving but not while saving`() {
    assertTrue(open().copy(connection = ConnectionState.Disconnected).toBrewNotice().disconnected)
    val saving = open(finished, savedBrew = SavedBrew.Saving).copy(connection = ConnectionState.Disconnected).toBrewNotice()
    assertEquals(BrewNoticeStage.SAVING, saving.stage)
    assertFalse(saving.disconnected)
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  @Test
  fun `the service starts each time a session opens`() = runTest(UnconfinedTestDispatcher()) {
    val ui = MutableStateFlow(ScaleUiState())
    var starts = 0
    BrewKeepAlive(ui, backgroundScope) { starts++ }
    assertEquals(0, starts)

    ui.value = open()
    ui.value = open(BrewPhase.Active(pouring = false), t0 = 0)
    assertEquals(1, starts)

    ui.value = open(finished, savedBrew = SavedBrew.Saved(1))
    ui.value = open()
    assertEquals(2, starts)
  }

  // --- Sin báscula (SPEC-007) ---

  private val curve = TargetCurve.fromRecipe(v60Classic())

  private fun scaleless(
    phase: BrewPhase = BrewPhase.Armed,
    t0: Long? = null,
    withRecipe: Boolean = true,
    stageIndex: Int? = 0,
    done: Boolean = false,
    targetCurve: TargetCurve = curve,
  ): ScaleUiState {
    val target = if (withRecipe) {
      ActiveTarget(ReferenceKey.Recipe(1), ReferenceType.RECIPE, "V60 clásica", targetCurve, 1.0, 15.0, null, BrewDetails())
    } else {
      null
    }
    val guide = target?.let {
      GuideState(stageIndex?.let { i -> targetCurve.stages[i] }, null, null, null, null, allStagesDone = done)
    }
    return open(phase, t0).copy(
      scaleless = true,
      connection = ConnectionState.Disconnected,
      target = target,
      guide = guide,
      targetWaterG = 250.0,
      weight = 99.0,
      unit = WeightUnit.OUNCES,
    )
  }

  @Test
  fun `a scaleless session needs the service like any other`() {
    assertTrue(scaleless().needsKeepAlive())
    assertTrue(scaleless(BrewPhase.Active(pouring = false), t0 = 0).needsKeepAlive())
  }

  @Test
  fun `a scaleless notice has no water, no disconnection and always grams`() {
    val notice = scaleless(BrewPhase.Active(pouring = false), t0 = 7_000).toBrewNotice()
    assertTrue(notice.scaleless)
    assertEquals(BrewNoticeStage.POURING, notice.stage)
    assertNull(notice.water)
    assertFalse(notice.disconnected)
    assertEquals(WeightUnit.GRAMS, notice.unit)
    assertEquals(7_000L, notice.t0MonoMs)
    assertEquals(250.0, notice.targetWater!!, 0.0)
  }

  @Test
  fun `a scaleless notice carries the current step and the pour goal only on a pour`() {
    val pour = scaleless(BrewPhase.Active(pouring = false), t0 = 0, stageIndex = 0).toBrewNotice().step!!
    assertEquals(1, pour.number)
    assertEquals(curve.stages.size, pour.count)
    assertEquals(StageType.POUR, pour.type)
    assertEquals(Math.round(curve.stages[0].targetWaterG).toDouble(), pour.targetWaterG!!, 0.0)

    val other = curve.stages.indexOfFirst { it.type != StageType.POUR }
    if (other >= 0) {
      val step = scaleless(BrewPhase.Active(pouring = false), t0 = 0, stageIndex = other).toBrewNotice().step!!
      assertNull(step.targetWaterG)
    }
  }

  @Test
  fun `a scaleless notice of an action step has its type and no water`() {
    val aeroPress = TargetCurve.fromRecipe(aeroPressWithActions())
    val notice = scaleless(BrewPhase.Active(pouring = false), t0 = 0, stageIndex = 2, targetCurve = aeroPress).toBrewNotice()
    val step = notice.step!!
    assertEquals(StageType.ACTION, step.type)
    assertNull(step.targetWaterG)
    assertEquals(3, step.number)
    assertEquals(6, step.count)
  }

  @Test
  fun `the scaleless notice does not change while the clock runs inside a step`() {
    val a = scaleless(BrewPhase.Active(pouring = false), t0 = 0).copy(elapsedMs = 1_000).toBrewNotice()
    val b = scaleless(BrewPhase.Active(pouring = false), t0 = 0).copy(elapsedMs = 2_000).toBrewNotice()
    assertEquals(a, b)
  }

  @Test
  fun `a scaleless notice reports the steps done, the ready state and the saving`() {
    val done = scaleless(BrewPhase.Active(pouring = false), t0 = 0, stageIndex = null, done = true).toBrewNotice()
    assertTrue(done.stepsDone)
    assertNull(done.step)

    val ready = scaleless().toBrewNotice()
    assertEquals(BrewNoticeStage.PREP, ready.stage)
    assertNull(ready.t0MonoMs)

    val saving = scaleless(finished).copy(savedBrew = SavedBrew.Saving).toBrewNotice()
    assertEquals(BrewNoticeStage.SAVING, saving.stage)
  }

  @Test
  fun `a scaleless session without a recipe has no step`() {
    val notice = scaleless(BrewPhase.Active(pouring = false), t0 = 0, withRecipe = false).toBrewNotice()
    assertNull(notice.step)
    assertFalse(notice.stepsDone)
    assertEquals(250.0, notice.targetWater!!, 0.0)
  }
}
