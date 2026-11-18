package dev.josueamaya.pourline.session

import dev.josueamaya.pourline.brew.BrewPhase
import dev.josueamaya.pourline.brew.FinishReason
import dev.josueamaya.pourline.brew.StageNotifier
import dev.josueamaya.pourline.coffee.PickedCoffee
import dev.josueamaya.pourline.grinder.GrindSetting
import dev.josueamaya.pourline.history.ReferenceType
import dev.josueamaya.pourline.protocol.ScaleMessage
import dev.josueamaya.pourline.protocol.TimerStatus
import dev.josueamaya.pourline.protocol.WeightUnit
import dev.josueamaya.pourline.recipe.recipeOf
import dev.josueamaya.pourline.recipe.stage
import dev.josueamaya.pourline.recipe.v60Classic
import dev.josueamaya.pourline.scale.ConnectionState
import dev.josueamaya.pourline.settings.ReferenceKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** La sesión sin báscula en el gestor (SPEC-007 §3.3): el reloj virtual del planificador es también el del gestor. */
@OptIn(ExperimentalCoroutinesApi::class)
class ScaleSessionManagerScalelessTest {

  private val scheduler = TestCoroutineScheduler()
  private val factory = RecordingConnectionFactory()
  private val saver = FakeBrewSaver()
  private val references = FakeReferenceStore()
  private val targets = FakeTargetReferences()
  private val coffees = FakeScaleCoffees()
  private val coffeeStore = FakeCoffeeStore()
  private val notifier = TimestampedNotifier { scheduler.currentTime }
  private var grindNow: GrindSetting? = null
  private val plan = BrewPlan(doseG = 15.0, ratio = 16.0)
  private lateinit var manager: ScaleSessionManager
  private lateinit var managerScope: CoroutineScope
  private lateinit var applicationScope: CoroutineScope

  private val ui get() = manager.ui.value

  private fun build(stageNotifier: StageNotifier = notifier, store: ReferenceStoreAndTargets? = null): ScaleSessionManager =
    ScaleSessionManager(
      factory,
      managerScope,
      MutableStateFlow(100_000),
      saver,
      applicationScope,
      store?.references ?: references,
      store?.targets ?: targets,
      stageNotifier,
      coffeeStore = coffeeStore,
      coffees = coffees,
      grind = { grindNow },
      clock = { scheduler.currentTime },
      wallClock = { WALL_BASE + scheduler.currentTime },
    )

  private class ReferenceStoreAndTargets(val references: FakeReferenceStore, val targets: FakeTargetReferences)

  @Before
  fun setUp() {
    val dispatcher = UnconfinedTestDispatcher(scheduler)
    Dispatchers.setMain(dispatcher)
    applicationScope = CoroutineScope(SupervisorJob() + dispatcher)
    managerScope = CoroutineScope(SupervisorJob() + dispatcher)
    scheduler.advanceTimeBy(1_000)
    manager = build()
  }

  @After
  fun tearDown() {
    manager.disconnect()
    managerScope.cancel()
    applicationScope.cancel()
    Dispatchers.resetMain()
  }

  private fun advanceMs(ms: Long) {
    var left = ms
    while (left > 0) {
      val step = minOf(100L, left)
      scheduler.advanceTimeBy(step)
      scheduler.runCurrent()
      left -= step
    }
  }

  private fun advanceSeconds(seconds: Int) = advanceMs(seconds * 1_000L)

  private fun selectV60(id: Long = 1) {
    targets.putRecipe(id, v60Classic())
    references.setReference(ReferenceKey.Recipe(id))
  }

  private fun startWithV60() {
    selectV60()
    manager.startScalelessSession(plan)
  }

  private fun weight(grams: Double, flowGps: Double = 0.0) {
    factory.connections.last().messages.tryEmit(
      ScaleMessage.Weight(rawWeight = (grams * 10).toInt(), rawFlow = (flowGps * 10).toInt(), seconds = 0, unit = WeightUnit.GRAMS, outOfRange = false),
    )
  }

  private fun connectScale() {
    manager.connect(ScaleTarget.Real("00:11:22:33:44:55", "Test"))
    assertEquals(ConnectionState.Ready, ui.connection)
  }

  @Test
  fun `start leaves the session armed without prep, scaleless and with the plan`() {
    manager.startScalelessSession(plan)

    assertTrue(ui.scaleless)
    assertNull(ui.prep)
    assertEquals(plan, ui.plan)
    assertTrue(ui.hasSession)
    assertEquals(BrewPhase.Armed, ui.brew.phase)
    assertEquals(WeightUnit.GRAMS, ui.sessionUnit)
    assertEquals(15.0, ui.plannedDoseG!!, 1e-9)
    assertEquals(240.0, ui.targetWaterG!!, 1e-9)
    assertFalse(manager.isArmed)
  }

  @Test
  fun `start with a recipe plans the water of its curve and its dose`() {
    startWithV60()

    assertEquals(15.0, ui.plannedDoseG!!, 1e-9)
    assertEquals(250.0, ui.targetWaterG!!, 1e-9)
    assertNotNull(ui.target)
    assertEquals(0, ui.guide!!.stage!!.index)
  }

  @Test
  fun `a dose typed for the recipe scales its water and steps`() {
    selectV60()
    val nominal = ui.target!!.curve
    manager.setRecipeDose(12.0)

    assertEquals(12.0, ui.target!!.scaledDoseG!!, 1e-9)
    assertEquals(200.0, ui.target!!.curve.finalWaterG, 1e-9)
    manager.startScalelessSession(plan)

    assertEquals(12.0, ui.plannedDoseG!!, 1e-9)
    assertEquals(200.0, ui.targetWaterG!!, 1e-9)
    nominal.stages.zip(ui.target!!.curve.stages).forEach { (before, after) ->
      assertEquals(before.targetWaterG * 0.8, after.targetWaterG, 1e-9)
      assertEquals(before.startMs, after.startMs)
    }
    manager.scalelessStartTimer()
    advanceSeconds(10)
    manager.finishBrew()
    assertEquals(12.0, saver.calls.single().brew.doseG!!, 1e-9)
  }

  @Test
  fun `the typed dose is forgotten when another recipe is picked`() {
    selectV60()
    manager.setRecipeDose(12.0)
    targets.putRecipe(2, v60Classic())
    references.setReference(ReferenceKey.Recipe(2))
    references.setReference(ReferenceKey.Recipe(1))

    assertNull(ui.target!!.scaledDoseG)
    assertEquals(250.0, ui.target!!.curve.finalWaterG, 1e-9)
  }

  @Test
  fun `the typed dose does nothing without a recipe`() {
    manager.setRecipeDose(12.0)
    manager.startScalelessSession(plan)

    assertEquals(15.0, ui.plannedDoseG!!, 1e-9)
  }

  @Test
  fun `it freezes the recipe, the coffee and the grind at the start`() {
    selectV60()
    coffees.put(PickedCoffee(7, "Etiopía", null, null))
    coffeeStore.setLastCoffeeId(7)
    grindNow = GrindSetting(3, 21.0, "Acme · 21 clics")
    manager.startScalelessSession(plan)
    val frozenTarget = ui.target
    assertEquals(7L, ui.coffee?.id)

    // Cambiar la referencia, el café y la molienda, o borrar la receta, no toca la sesión abierta.
    targets.putRecipe(2, recipeOf(stage(0, 30.0, 10, "Otra")))
    references.setReference(ReferenceKey.Recipe(2))
    coffees.put(PickedCoffee(8, "Kenia", null, null))
    coffeeStore.setLastCoffeeId(8)
    grindNow = GrindSetting(3, 30.0, "Acme · 30 clics")
    targets.putRecipe(1, null)
    advanceSeconds(1)
    assertEquals(frozenTarget, ui.target)
    assertEquals(7L, ui.coffee?.id)

    manager.scalelessStartTimer()
    advanceSeconds(10)
    manager.finishBrew()

    val context = saver.calls.single().context
    assertEquals(ReferenceType.RECIPE, context.target!!.type)
    assertEquals(1L, context.target!!.id)
    assertEquals(7L, context.coffee!!.id)
    assertEquals("Acme · 21 clics", context.details!!.grind!!.label)
  }

  @Test
  fun `start is ignored while a scale or a scaleless session is active`() {
    connectScale()
    manager.beginArmedSession(plan)
    manager.prepStartTimer()
    assertTrue(ui.brew.phase is BrewPhase.Active)

    manager.startScalelessSession(plan)
    assertFalse(ui.scaleless)
    manager.finishBrew()
    manager.endSession()

    manager.startScalelessSession(plan)
    manager.scalelessStartTimer()
    assertTrue(ui.brew.phase is BrewPhase.Active)
    val t0 = ui.brew.t0MonoMs

    advanceSeconds(3)
    manager.startScalelessSession(BrewPlan(20.0, 15.0))
    manager.startSession(BrewPlan(20.0, 15.0))

    assertTrue(ui.scaleless)
    assertEquals(plan, ui.plan)
    assertEquals(t0, ui.brew.t0MonoMs)
    assertNull(ui.prep)
  }

  @Test
  fun `start timer moves to active and publishes the elapsed time and the guide without deviation`() {
    startWithV60()
    assertNull(ui.elapsedMs)

    manager.scalelessStartTimer()
    assertEquals(BrewPhase.Active(pouring = false), ui.brew.phase)
    advanceMs(5_000)

    assertEquals(5_000L, ui.elapsedMs)
    val guide = ui.guide!!
    assertEquals(0, guide.stage!!.index)
    assertNull(guide.deviationG)
    assertNull(guide.deviationLevel)
    assertTrue(ui.brew.samples.isEmpty())
    assertNull(ui.brew.currentStability)

    advanceMs(41_000)
    assertEquals(46_000L, ui.elapsedMs)
    assertEquals(2, ui.guide!!.stage!!.index)
  }

  @Test
  fun `start timer without a session or after the start does nothing`() {
    manager.scalelessStartTimer()
    assertFalse(ui.scaleless)
    assertEquals(BrewPhase.Armed, ui.brew.phase)

    startWithV60()
    manager.scalelessStartTimer()
    advanceSeconds(2)
    val t0 = ui.brew.t0MonoMs
    manager.scalelessStartTimer()
    assertEquals(t0, ui.brew.t0MonoMs)
  }

  @Test
  fun `weights, tares and timer messages from a connected scale do not change the session`() {
    connectScale()
    startWithV60()
    manager.scalelessStartTimer()

    repeat(50) {
      advanceMs(100)
      weight(grams = 100.0 + it, flowGps = 4.0)
    }
    factory.connections.last().messages.tryEmit(ScaleMessage.Tared(success = true))
    factory.connections.last().messages.tryEmit(ScaleMessage.Timer(TimerStatus.PAUSED))
    factory.connections.last().messages.tryEmit(ScaleMessage.Timer(TimerStatus.RESET))
    advanceMs(100)

    assertEquals(BrewPhase.Active(pouring = false), ui.brew.phase)
    assertTrue(ui.brew.samples.isEmpty())
    assertNull(ui.brew.currentStability)
    assertTrue(saver.calls.isEmpty())
    // La tarjeta de la báscula sigue viva, pero no se le manda ninguna trama.
    assertEquals(149.0, ui.weight, 1e-9)
    assertTrue(factory.connections.last().sent.isEmpty())
  }

  @Test
  fun `connecting, losing and reconnecting the scale keeps the session active`() {
    startWithV60()
    manager.scalelessStartTimer()
    advanceSeconds(5)

    connectScale()
    advanceSeconds(5)
    factory.connections.last().state.value = ConnectionState.Disconnected
    advanceSeconds(5)
    manager.connect(ScaleTarget.Simulated("Simulada"))
    advanceSeconds(5)
    manager.disconnect()
    advanceSeconds(5)

    assertEquals(BrewPhase.Active(pouring = false), ui.brew.phase)
    assertTrue(ui.scaleless)
    assertTrue(saver.calls.isEmpty())
    assertEquals(25_000L, ui.elapsedMs)
  }

  @Test
  fun `stage alerts fire at the same instants as a simulated scale session with the same recipe`() {
    // Con báscula: otra instancia con la V60, pesos cada 100 ms y la misma hora de inicio.
    val scaleNotifier = TimestampedNotifier { scheduler.currentTime }
    val scaleReferences = FakeReferenceStore()
    val scaleTargets = FakeTargetReferences().also { it.putRecipe(1, v60Classic()) }
    scaleReferences.setReference(ReferenceKey.Recipe(1))
    val scaleManager = build(scaleNotifier, ReferenceStoreAndTargets(scaleReferences, scaleTargets))
    scaleManager.connect(ScaleTarget.Simulated("Simulada"))
    val scaleConnection = factory.connections.last()
    scaleManager.beginArmedSession(plan)

    startWithV60()
    scaleManager.prepStartTimer()
    manager.scalelessStartTimer()

    val scaleCountdown = mutableListOf<Int?>()
    val scalelessCountdown = mutableListOf<Int?>()
    repeat(1_170) {
      advanceMs(100)
      scaleConnection.messages.tryEmit(
        ScaleMessage.Weight(rawWeight = 0, rawFlow = 0, seconds = 0, unit = WeightUnit.GRAMS, outOfRange = false),
      )
      scaleCountdown += scaleManager.ui.value.guide?.countdownS
      scalelessCountdown += ui.guide?.countdownS
    }

    val instants = notifier.started.filter { it.first < 4 || it.second < 100_000 }
    assertEquals(listOf(1, 2, 3, 4), instants.map { it.first })
    assertEquals(listOf(11_000L, 46_000L, 66_000L, 91_000L), instants.map { it.second })
    assertEquals(scaleNotifier.started.map { it.first to it.second }, instants)
    assertEquals(scaleCountdown, scalelessCountdown)
    assertTrue(scalelessCountdown.any { it == 5 } && scalelessCountdown.any { it == 1 })
    scaleManager.disconnect()
  }

  @Test
  fun `the done alert fires once at the end of the last step and the session stays active`() {
    startWithV60()
    manager.scalelessStartTimer()

    advanceSeconds(114)
    assertEquals(listOf(1, 2, 3, 4), notifier.started.map { it.first })
    assertFalse(ui.guide!!.allStagesDone)

    advanceSeconds(2)
    assertTrue(ui.guide!!.allStagesDone)
    assertEquals(BrewPhase.Active(pouring = false), ui.brew.phase)
    assertEquals(listOf(1, 2, 3, 4, 4), notifier.started.map { it.first })
    assertEquals(116_000L, notifier.started.last().second)

    advanceSeconds(30)
    assertEquals(5, notifier.started.size)
    assertTrue(ui.brew.phase is BrewPhase.Active)
  }

  @Test
  fun `a free scaleless session has no stage alerts and shows the water goal`() {
    manager.startScalelessSession(plan)
    manager.scalelessStartTimer()

    advanceSeconds(130)

    assertTrue(notifier.started.isEmpty())
    assertNull(ui.guide)
    assertNull(ui.target)
    assertEquals(240.0, ui.targetWaterG!!, 1e-9)
    assertTrue(ui.brew.phase is BrewPhase.Active)
  }

  @Test
  fun `finish saves with grams, no model, not simulated, scaleless and the target with a null fit score`() {
    manager.connect(ScaleTarget.Simulated("Simulada"))
    assertTrue(ui.isSimulated)
    startWithV60()
    manager.scalelessStartTimer()
    advanceSeconds(116)

    manager.finishBrew()

    val call = saver.calls.single()
    assertTrue(call.brew.samples.isEmpty())
    assertEquals(15.0, call.brew.doseG!!, 1e-9)
    assertEquals(250.0, call.brew.summary.totalWaterG, 1e-9)
    assertEquals(250.0 / 15.0, call.brew.summary.finalRatio!!, 1e-9)
    assertEquals(116_000L, call.brew.summary.totalTimeMs)
    assertEquals(FinishReason.MANUAL, call.brew.summary.reason)
    assertEquals(WeightUnit.GRAMS, call.context.unit)
    assertNull(call.context.scaleModel)
    assertFalse(call.context.simulated)
    assertTrue(call.context.scaleless)
    assertNull(call.context.target!!.fit.score)
    assertEquals(250.0, call.context.target!!.fit.targetWaterG, 1e-9)
    assertNull(ui.lastFit)
    assertTrue(ui.savedBrew is SavedBrew.Saved)
    assertEquals(116_000L, ui.elapsedMs)
  }

  @Test
  fun `a free session saves the plan dose and dose times ratio`() {
    manager.startScalelessSession(BrewPlan(doseG = 18.0, ratio = 15.0))
    manager.scalelessStartTimer()
    advanceSeconds(60)

    manager.finishBrew()

    val call = saver.calls.single()
    assertEquals(18.0, call.brew.doseG!!, 1e-9)
    assertEquals(270.0, call.brew.summary.totalWaterG, 1e-9)
    assertEquals(15.0, call.brew.summary.finalRatio!!, 1e-9)
    assertNull(call.context.target)
  }

  @Test
  fun `at 15 minutes the session finishes with max duration and is saved`() {
    startWithV60()
    manager.scalelessStartTimer()

    advanceSeconds(899)
    assertTrue(ui.brew.phase is BrewPhase.Active)
    assertTrue(saver.calls.isEmpty())
    advanceSeconds(2)

    val finished = ui.brew.phase as BrewPhase.Finished
    assertEquals(FinishReason.MAX_DURATION, finished.summary.reason)
    assertEquals(900_000L, finished.summary.totalTimeMs)
    assertEquals(1, saver.calls.size)
    assertEquals(900_000L, ui.elapsedMs)
    assertTrue(ui.savedBrew is SavedBrew.Saved)
  }

  @Test
  fun `discard saves nothing and clears scaleless`() {
    startWithV60()
    manager.scalelessStartTimer()
    advanceSeconds(20)

    manager.discardSession()
    advanceSeconds(5)

    assertTrue(saver.calls.isEmpty())
    assertFalse(ui.scaleless)
    assertFalse(ui.hasSession)
    assertEquals(BrewPhase.Armed, ui.brew.phase)
    assertNull(ui.plannedDoseG)
    assertNull(ui.dose)
  }

  @Test
  fun `restart from finished starts another scaleless session with the same plan and does nothing from armed`() {
    startWithV60()
    manager.restartPrep()
    assertEquals(BrewPhase.Armed, ui.brew.phase)
    assertTrue(ui.scaleless)

    manager.scalelessStartTimer()
    advanceSeconds(10)
    manager.restartPrep()
    assertTrue(ui.brew.phase is BrewPhase.Active)

    manager.finishBrew()
    manager.restartPrep()

    assertTrue(ui.scaleless)
    assertEquals(BrewPhase.Armed, ui.brew.phase)
    assertEquals(plan, ui.plan)
    assertNull(ui.savedBrew)
    assertEquals(1, saver.calls.size)
  }

  @Test
  fun `end session from finished clears scaleless and the dose`() {
    connectScale()
    weight(grams = 18.0)
    manager.captureDose(tare = false)
    startWithV60()
    manager.scalelessStartTimer()
    advanceSeconds(10)
    manager.finishBrew()
    assertTrue(ui.brew.phase is BrewPhase.Finished)

    manager.endSession()

    assertFalse(ui.scaleless)
    assertFalse(ui.hasSession)
    assertNull(ui.dose)
    assertNull(ui.plannedDoseG)
    assertEquals(BrewPhase.Armed, ui.brew.phase)
  }

  @Test
  fun `ending an active scaleless session finishes and saves it`() {
    startWithV60()
    manager.scalelessStartTimer()
    advanceSeconds(20)

    manager.endSession()

    assertEquals(1, saver.calls.size)
    assertTrue(saver.calls.single().context.scaleless)
    assertFalse(ui.scaleless)
  }

  @Test
  fun `a scale session after a scaleless one has its prep steps as before`() {
    startWithV60()
    manager.scalelessStartTimer()
    advanceSeconds(10)
    manager.finishBrew()
    manager.endSession()

    connectScale()
    manager.startSession(plan)

    assertFalse(ui.scaleless)
    assertEquals(PrepStep.TARE, ui.prep!!.step)
    assertEquals(BrewPhase.Armed, ui.brew.phase)
    assertEquals(WeightUnit.GRAMS, ui.sessionUnit)
  }

  @Test
  fun `select target and coffee are ignored while the scaleless session is active`() {
    startWithV60()
    manager.scalelessStartTimer()
    advanceSeconds(1)
    val before = references.sets.size

    manager.selectTarget(ReferenceKey.Recipe(2))
    manager.selectCoffee(9)

    assertEquals(before, references.sets.size)
    assertTrue(coffeeStore.sets.isEmpty())
  }

  private companion object {
    const val WALL_BASE = 1_700_000_000_000L
  }
}
