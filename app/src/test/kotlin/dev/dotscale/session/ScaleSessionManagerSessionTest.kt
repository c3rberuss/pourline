package dev.dotscale.session

import dev.dotscale.brew.BrewPhase
import dev.dotscale.brew.FinishReason
import dev.dotscale.coffee.PickedCoffee
import dev.dotscale.protocol.ScaleCommands
import dev.dotscale.protocol.ScaleMessage
import dev.dotscale.protocol.TimerStatus
import dev.dotscale.protocol.WeightUnit
import dev.dotscale.recipe.v60Classic
import dev.dotscale.scale.ConnectionState
import dev.dotscale.settings.ReferenceKey
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

/** La sesión guiada del gestor (F7): pasos previos, compuerta, salida, descarte y reinicio. */
@OptIn(ExperimentalCoroutinesApi::class)
class ScaleSessionManagerSessionTest {

  private val connection = FakeScaleConnection()
  private val saver = FakeBrewSaver()
  private val references = FakeReferenceStore()
  private val targets = FakeTargetReferences()
  private val coffees = FakeScaleCoffees()
  private val coffeeStore = FakeCoffeeStore()
  private val notifier = FakeNotifier()
  private val scheduler = TestCoroutineScheduler()
  private val autoFinish = MutableStateFlow(120)
  private var now = 1_000L
  private lateinit var manager: ScaleSessionManager
  private lateinit var managerScope: CoroutineScope
  private lateinit var applicationScope: CoroutineScope

  private val ui get() = manager.ui.value
  private val plan = BrewPlan(doseG = 15.0, ratio = 16.0)

  @Before
  fun setUp() {
    val dispatcher = UnconfinedTestDispatcher(scheduler)
    Dispatchers.setMain(dispatcher)
    applicationScope = CoroutineScope(SupervisorJob() + dispatcher)
    managerScope = CoroutineScope(SupervisorJob() + dispatcher)
    manager = ScaleSessionManager(
      { connection },
      managerScope,
      autoFinish,
      saver,
      applicationScope,
      references,
      targets,
      notifier,
      coffeeStore = coffeeStore,
      coffees = coffees,
      clock = { now },
      wallClock = { 1_700_000_000_000L + now },
    )
    manager.connect(ScaleTarget.Real("00:11:22:33:44:55", "Test"))
  }

  @After
  fun tearDown() {
    manager.disconnect()
    managerScope.cancel()
    applicationScope.cancel()
    Dispatchers.resetMain()
  }

  private fun advanceSeconds(seconds: Int) = repeat(seconds) {
    now += 1_000
    scheduler.advanceTimeBy(1_000)
    scheduler.runCurrent()
  }

  private fun weight(grams: Double, flowGps: Double = 0.0) {
    now += 100
    connection.messages.tryEmit(
      ScaleMessage.Weight(rawWeight = (grams * 10).toInt(), rawFlow = (flowGps * 10).toInt(), seconds = 0, unit = WeightUnit.GRAMS, outOfRange = false),
    )
  }

  private fun pour() {
    repeat(5) { weight(0.0) }
    repeat(30) { weight(4.0, 4.0) }
  }

  /** Salta los pasos previos hasta armar (sin dosis). */
  private fun armed() {
    manager.startSession(plan)
    manager.prepSkip()
    manager.prepSkipDose()
    manager.prepSkip()
    assertEquals(PrepStep.POUR, ui.prep!!.step)
  }

  private fun selectRecipe() {
    targets.putRecipe(1, v60Classic())
    references.setReference(ReferenceKey.Recipe(1))
  }

  private fun pickCoffee() {
    coffees.put(PickedCoffee(7, "Etiopía", "Tostador", null))
    coffeeStore.setLastCoffeeId(7)
  }

  // --- Abrir la sesión ---

  @Test
  fun `startSession creates the prep at the first step and the session is open`() {
    assertFalse(ui.hasSession)
    manager.startSession(plan)

    assertTrue(ui.hasSession)
    assertEquals(plan, ui.plan)
    assertEquals(PrepStep.TARE, ui.prep!!.step)
    assertFalse(manager.isArmed)
  }

  @Test
  fun `a pour before the pour step creates no session and saves nothing`() {
    manager.startSession(plan)
    pour()

    assertTrue(ui.brew.phase is BrewPhase.Armed)
    assertTrue(saver.calls.isEmpty())
  }

  @Test
  fun `a pour without any session open creates none either`() {
    pour()
    assertTrue(ui.brew.phase is BrewPhase.Armed)
    assertFalse(ui.hasSession)
  }

  @Test
  fun `arming opens the gate and the pour creates the session`() {
    armed()
    assertTrue(manager.isArmed)
    pour()
    assertTrue(ui.brew.phase is BrewPhase.Active)
  }

  @Test
  fun `the guided steps advance by themselves with the tare frame and a stable dose`() {
    manager.startSession(plan)
    connection.messages.tryEmit(ScaleMessage.Tared(success = true))
    assertEquals(PrepStep.DOSE, ui.prep!!.step)

    repeat(25) { weight(15.0) }
    assertEquals(PrepStep.TARE_FOR_POUR, ui.prep!!.step)
    assertEquals(15.0, ui.dose!!, 1e-9)

    connection.messages.tryEmit(ScaleMessage.Tared(success = true))
    assertEquals(PrepStep.POUR, ui.prep!!.step)
    assertTrue(manager.isArmed)
  }

  @Test
  fun `tare in the prep sends the tare to the scale`() {
    manager.startSession(plan)
    manager.prepTare()
    assertEquals(listOf(ScaleCommands.tare()), connection.sent)
    assertTrue(ui.prep!!.waitingTare)
  }

  @Test
  fun `capture now sets the dose without taring and the water follows it`() {
    manager.startSession(BrewPlan(15.0, 16.0))
    manager.prepSkip()
    weight(18.0)
    connection.sent.clear()
    manager.prepCaptureNow()

    assertEquals(18.0, ui.dose!!, 1e-9)
    assertEquals(PrepStep.TARE_FOR_POUR, ui.prep!!.step)
    assertTrue(connection.sent.isEmpty())
    assertEquals(18.0 * 16.0, ui.targetWaterG!!, 1e-9)
  }

  @Test
  fun `without a dose and without a recipe the target water uses the plan`() {
    armed()
    assertEquals(15.0 * 16.0, ui.targetWaterG!!, 1e-9)
  }

  @Test
  fun `with a recipe the target water is the one of its curve`() {
    selectRecipe()
    armed()
    assertEquals(v60Classic().totalWaterG, ui.targetWaterG!!, 1e-9)
  }

  @Test
  fun `start timer moves to Active with the start timer command`() {
    armed()
    connection.sent.clear()
    manager.prepStartTimer()

    assertTrue(ui.brew.phase is BrewPhase.Active)
    assertEquals(listOf(ScaleCommands.startTimer()), connection.sent)
  }

  // --- Compuerta al terminar ---

  @Test
  fun `in Finished a tare does not rearm and another pour creates no session`() {
    armed()
    pour()
    manager.finishBrew()
    assertTrue(ui.brew.phase is BrewPhase.Finished)
    assertFalse(manager.isArmed)

    connection.messages.tryEmit(ScaleMessage.Tared(success = true))
    pour()

    assertTrue(ui.brew.phase is BrewPhase.Finished)
    assertEquals(1, saver.calls.size)
  }

  // --- Descartar ---

  @Test
  fun `discarding never calls the saver and clears the dose, the target and the coffee`() {
    pickCoffee()
    selectRecipe()
    armed()
    weight(15.0)
    manager.captureDose(tare = false)
    pour()
    assertTrue(ui.brew.phase is BrewPhase.Active)
    assertNotNull(ui.coffee)

    manager.discardSession()

    assertTrue(saver.calls.isEmpty())
    assertTrue(ui.brew.phase is BrewPhase.Armed)
    assertNull(ui.dose)
    assertFalse(ui.hasSession)
    assertFalse(manager.isArmed)
    assertNull(ui.savedBrew)
    advanceSeconds(5)
    assertTrue(saver.calls.isEmpty())
  }

  @Test
  fun `discarding with the timer running pauses it`() {
    armed()
    connection.messages.tryEmit(ScaleMessage.Timer(TimerStatus.RUNNING))
    pour()
    connection.sent.clear()
    manager.discardSession()
    assertEquals(listOf(ScaleCommands.pauseTimer()), connection.sent)
  }

  @Test
  fun `after discarding a new session starts clean`() {
    armed()
    pour()
    manager.discardSession()

    armed()
    pour()
    assertTrue(ui.brew.phase is BrewPhase.Active)
    manager.finishBrew()
    assertEquals(1, saver.calls.size)
  }

  // --- Cerrar la sesión y la dosis ---

  @Test
  fun `ending from Finished clears the dose`() {
    manager.startSession(plan)
    manager.prepSkip()
    weight(15.0)
    manager.prepCaptureNow()
    manager.prepSkip()
    pour()
    manager.finishBrew()
    assertEquals(15.0, ui.dose!!, 1e-9)

    manager.endSession()

    assertNull(ui.dose)
    assertFalse(ui.hasSession)
    assertNull(ui.prep)
  }

  @Test
  fun `leaving before Active keeps the dose and the next session skips the dose step`() {
    manager.startSession(plan)
    manager.prepSkip()
    weight(16.0)
    manager.prepCaptureNow()
    manager.endSession()

    assertEquals(16.0, ui.dose!!, 1e-9)
    assertFalse(ui.hasSession)
    assertFalse(manager.isArmed)

    manager.startSession(plan)
    connection.messages.tryEmit(ScaleMessage.Tared(success = true))
    assertEquals(PrepStep.TARE_FOR_POUR, ui.prep!!.step)
    assertEquals(16.0, ui.prep!!.doseG!!, 1e-9)
  }

  // --- Desconexión ---

  @Test
  fun `a disconnection in the prep steps saves nothing and closes the gate`() {
    armed()
    connection.state.value = ConnectionState.Disconnected

    assertTrue(saver.calls.isEmpty())
    assertFalse(manager.isArmed)
    assertEquals(PrepStep.TARE, ui.prep!!.step)
    assertTrue(ui.prep!!.interrupted)
  }

  @Test
  fun `reconnecting returns the prep to the first step with the dose`() {
    manager.startSession(plan)
    manager.prepSkip()
    weight(15.0)
    manager.prepCaptureNow()
    connection.state.value = ConnectionState.Disconnected
    connection.state.value = ConnectionState.Ready

    assertEquals(PrepStep.TARE, ui.prep!!.step)
    assertFalse(ui.prep!!.interrupted)
    assertEquals(15.0, ui.prep!!.doseG!!, 1e-9)
  }

  @Test
  fun `a disconnection in Active saves the brew as disconnected`() {
    armed()
    pour()
    connection.state.value = ConnectionState.Disconnected

    val call = saver.calls.single()
    assertEquals(FinishReason.DISCONNECTED, call.brew.summary.reason)
    assertFalse(manager.isArmed)
  }

  // --- Tiempo ---

  @Test
  fun `elapsedMs follows the clock in Active and the total time in Finished`() {
    armed()
    assertNull(ui.elapsedMs)
    pour()
    val first = ui.elapsedMs!!
    advanceSeconds(3)
    assertEquals(first + 3_000, ui.elapsedMs!!)

    manager.finishBrew()
    val finished = ui.brew.phase as BrewPhase.Finished
    assertEquals(finished.summary.totalTimeMs, ui.elapsedMs)
  }

  // --- Reiniciar ---

  @Test
  fun `restart before Active returns to the first step and forgets the dose`() {
    manager.startSession(plan)
    manager.prepSkip()
    weight(15.0)
    manager.prepCaptureNow()
    assertEquals(PrepStep.TARE_FOR_POUR, ui.prep!!.step)

    manager.restartPrep()

    assertEquals(PrepStep.TARE, ui.prep!!.step)
    assertNull(ui.prep!!.doseG)
    assertNull(ui.dose)
    assertTrue(ui.hasSession)
    assertFalse(manager.isArmed)
  }

  @Test
  fun `restart in Active does nothing`() {
    armed()
    pour()
    manager.restartPrep()
    assertTrue(ui.brew.phase is BrewPhase.Active)
    assertTrue(saver.calls.isEmpty())
  }

  @Test
  fun `restart in Finished starts another session with the same recipe and coffee`() {
    pickCoffee()
    selectRecipe()
    armed()
    pour()
    manager.finishBrew()

    manager.restartPrep()

    assertTrue(ui.hasSession)
    assertTrue(ui.brew.phase is BrewPhase.Armed)
    assertEquals(PrepStep.TARE, ui.prep!!.step)
    assertEquals(ReferenceKey.Recipe(1), ui.target!!.key)
    assertEquals(7L, ui.coffee!!.id)
    assertNull(ui.dose)
    assertNull(ui.savedBrew)
  }
}
