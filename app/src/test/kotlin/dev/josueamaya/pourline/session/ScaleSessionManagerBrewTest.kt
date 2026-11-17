package dev.josueamaya.pourline.session

import dev.josueamaya.pourline.brew.BrewPhase
import dev.josueamaya.pourline.brew.FinishReason
import dev.josueamaya.pourline.protocol.ScaleCommands
import dev.josueamaya.pourline.protocol.ScaleMessage
import dev.josueamaya.pourline.protocol.ScaleModel
import dev.josueamaya.pourline.protocol.TimerStatus
import dev.josueamaya.pourline.protocol.WeightUnit
import dev.josueamaya.pourline.scale.ConnectionState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScaleSessionManagerBrewTest {

  private val connection = FakeScaleConnection()
  private val saver = FakeBrewSaver()
  private val scheduler = TestCoroutineScheduler()
  private val autoFinish = MutableStateFlow(60)
  private val errors = mutableListOf<ScaleError>()
  private var now = 1_000L
  private lateinit var viewModel: ScaleSessionManager
  private lateinit var managerScope: CoroutineScope
  private lateinit var applicationScope: CoroutineScope

  @Before
  fun setUp() {
    val dispatcher = UnconfinedTestDispatcher(scheduler)
    Dispatchers.setMain(dispatcher)
    applicationScope = CoroutineScope(SupervisorJob() + dispatcher)
    managerScope = CoroutineScope(SupervisorJob() + dispatcher)
    viewModel = ScaleSessionManager(
        { connection },
        managerScope,
        autoFinish,
        saver,
        applicationScope,
        FakeReferenceStore(),
        FakeTargetReferences(),
        FakeNotifier(),
        clock = { now },
        wallClock = { WALL_BASE + now },
    )
    viewModel.connect(ScaleTarget.Real("00:11:22:33:44:55", "Test"))
    viewModel.arm()
    applicationScope.launch { viewModel.errors.collect { errors += it } }
  }

  /** Pasa el tiempo sin notificaciones: avanza el reloj y el tick de 1 s del gestor. */
  private fun advanceSeconds(seconds: Int) = repeat(seconds) {
    now += 1_000
    scheduler.advanceTimeBy(1_000)
    scheduler.runCurrent()
  }

  @After
  fun tearDown() {
    viewModel.disconnect()
    managerScope.cancel() // el tick de 1 s no termina solo
    applicationScope.cancel()
    Dispatchers.resetMain()
  }

  private fun weight(flowGps: Double) {
    now += 100
    val raw = (flowGps * 10).toInt()
    connection.messages.tryEmit(ScaleMessage.Weight(rawWeight = raw, rawFlow = raw, seconds = 0, unit = WeightUnit.GRAMS, outOfRange = false))
  }

  @Test
  fun `a pour sends start timer exactly once and activates the session`() {
    repeat(5) { weight(0.0) }
    repeat(40) { weight(4.0) }

    assertTrue(viewModel.ui.value.brew.phase is BrewPhase.Active)
    assertEquals(listOf(ScaleCommands.startTimer()), connection.sent)
    assertTrue(viewModel.ui.value.brew.samples.isNotEmpty())
  }

  @Test
  fun `no start timer is sent when the timer is already running`() {
    connection.messages.tryEmit(ScaleMessage.Timer(TimerStatus.RUNNING))
    repeat(5) { weight(0.0) }
    repeat(40) { weight(4.0) }

    assertTrue(viewModel.ui.value.brew.phase is BrewPhase.Active)
    assertTrue(connection.sent.isEmpty())
  }

  @Test
  fun `the finish button ends the session`() {
    repeat(5) { weight(0.0) }
    repeat(20) { weight(4.0) }
    viewModel.finishBrew()

    val phase = viewModel.ui.value.brew.phase as BrewPhase.Finished
    assertEquals(FinishReason.MANUAL, phase.summary.reason)
  }

  @Test
  fun `losing the connection ends the session as interrupted`() {
    repeat(5) { weight(0.0) }
    repeat(20) { weight(4.0) }
    connection.state.value = ConnectionState.Disconnected

    val phase = viewModel.ui.value.brew.phase as BrewPhase.Finished
    assertEquals(FinishReason.DISCONNECTED, phase.summary.reason)
    assertTrue(phase.summary.interrupted)
  }

  @Test
  fun `a timer pause ends the session`() {
    repeat(5) { weight(0.0) }
    repeat(20) { weight(4.0) }
    connection.messages.tryEmit(ScaleMessage.Timer(TimerStatus.PAUSED))

    val phase = viewModel.ui.value.brew.phase as BrewPhase.Finished
    assertEquals(FinishReason.TIMER_PAUSED, phase.summary.reason)
  }

  @Test
  fun `the session ends by inactivity after the configured 30 s`() {
    autoFinish.value = 30
    repeat(5) { weight(0.0) }
    repeat(40) { weight(4.0) }

    advanceSeconds(29)
    assertTrue(viewModel.ui.value.brew.phase is BrewPhase.Active)
    advanceSeconds(1)
    val phase = viewModel.ui.value.brew.phase as BrewPhase.Finished
    assertEquals(FinishReason.INACTIVITY, phase.summary.reason)
    assertEquals(3_900L, phase.summary.totalTimeMs)
  }

  @Test
  fun `changing the auto finish setting applies to the running session`() {
    repeat(5) { weight(0.0) }
    repeat(40) { weight(4.0) }

    advanceSeconds(40) // con 60 s todavía no termina
    assertTrue(viewModel.ui.value.brew.phase is BrewPhase.Active)

    autoFinish.value = 30 // ya han pasado más de 30 s sin verter
    advanceSeconds(1)
    val phase = viewModel.ui.value.brew.phase as BrewPhase.Finished
    assertEquals(FinishReason.INACTIVITY, phase.summary.reason)
  }

  // --- Guardado (SPEC-002) ---

  private fun activePour() {
    repeat(5) { weight(0.0) }
    repeat(30) { weight(4.0) }
  }

  @Test
  fun `a finished brew is saved once with the right context and wall clock start`() {
    connection.messages.tryEmit(ScaleMessage.Model(ScaleModel.DOT, "TES017"))
    activePour()
    viewModel.finishBrew()

    val call = saver.calls.single()
    assertEquals(FinishReason.MANUAL, call.brew.summary.reason)
    assertEquals(WeightUnit.GRAMS, call.context.unit)
    assertEquals(ScaleModel.DOT, call.context.scaleModel)
    assertEquals(false, call.context.simulated)
    assertEquals(WALL_BASE + call.brew.t0MonoMs, call.context.startedAtEpochMs)
  }

  @Test
  fun `saved brew goes through Saving and Saved`() {
    saver.gate = CompletableDeferred()
    activePour()
    assertNull(viewModel.ui.value.savedBrew)
    viewModel.finishBrew()
    assertEquals(SavedBrew.Saving, viewModel.ui.value.savedBrew)

    saver.gate!!.complete(Unit)
    assertEquals(SavedBrew.Saved(1L), viewModel.ui.value.savedBrew)
  }

  @Test
  fun `a failed save reaches errors and leaves Failed`() {
    saver.failure = IllegalStateException("disco lleno")
    activePour()
    viewModel.finishBrew()

    assertEquals(SavedBrew.Failed, viewModel.ui.value.savedBrew)
    assertEquals(listOf<ScaleError>(ScaleError.SaveFailed), errors)
    assertTrue(viewModel.ui.value.brew.phase is BrewPhase.Finished) // el resumen sigue en pantalla
  }

  @Test
  fun `disconnecting the manager with an active session saves it as manual`() {
    activePour()
    viewModel.disconnect()

    val call = saver.calls.single()
    assertEquals(FinishReason.MANUAL, call.brew.summary.reason)
  }

  @Test
  fun `disconnecting the manager after the session finished does not save again`() {
    activePour()
    viewModel.finishBrew()
    viewModel.disconnect()
    assertEquals(1, saver.calls.size)
  }

  @Test
  fun `disconnecting the manager without a session saves nothing`() {
    viewModel.disconnect()
    assertTrue(saver.calls.isEmpty())
  }

  @Test
  fun `the save is not cancelled when the manager disconnects`() {
    saver.gate = CompletableDeferred()
    activePour()
    viewModel.disconnect()
    saver.gate!!.complete(Unit)
    assertEquals(1, saver.calls.size)
    assertEquals(2L, saver.nextId) // `save` llegó hasta el final
  }

  @Test
  fun `a new session during a slow save leaves savedBrew null when the old save ends`() {
    saver.gate = CompletableDeferred()
    activePour()
    viewModel.finishBrew()
    assertEquals(SavedBrew.Saving, viewModel.ui.value.savedBrew)

    viewModel.beginArmedSession()
    repeat(5) { weight(0.0) }
    repeat(30) { weight(4.0) }
    assertTrue(viewModel.ui.value.brew.phase is BrewPhase.Active)
    assertNull(viewModel.ui.value.savedBrew)

    saver.gate!!.complete(Unit)
    assertNull(viewModel.ui.value.savedBrew)
    assertEquals(1, saver.calls.size) // el guardado anterior se completó
  }

  // --- Temporizador (SPEC-002 §3.5) ---

  @Test
  fun `finishing sends pause timer and a tare in the summary neither rearms nor sends anything`() {
    activePour()
    connection.messages.tryEmit(ScaleMessage.Timer(TimerStatus.RUNNING))
    connection.sent.clear()
    viewModel.finishBrew()
    assertEquals(listOf(ScaleCommands.pauseTimer()), connection.sent)
    assertFalse(viewModel.isArmed)

    connection.messages.tryEmit(ScaleMessage.Timer(TimerStatus.PAUSED))
    connection.sent.clear()
    connection.messages.tryEmit(ScaleMessage.Tared(success = true))
    assertTrue(connection.sent.isEmpty())
    repeat(5) { weight(0.0) }
    repeat(30) { weight(4.0) }
    assertTrue(viewModel.ui.value.brew.phase is BrewPhase.Finished)
    assertEquals(1, saver.calls.size)
  }

  @Test
  fun `the next guided session after a paused timer resets and starts the timer`() {
    activePour()
    connection.messages.tryEmit(ScaleMessage.Timer(TimerStatus.RUNNING))
    viewModel.finishBrew()
    connection.messages.tryEmit(ScaleMessage.Timer(TimerStatus.PAUSED))
    connection.sent.clear()

    viewModel.beginArmedSession()
    activePour()

    assertEquals(listOf(ScaleCommands.resetTimer(), ScaleCommands.startTimer()), connection.sent)
    assertTrue(viewModel.ui.value.brew.phase is BrewPhase.Active)
  }

  @Test
  fun `a pour with the timer paused sends reset timer and then start timer`() {
    connection.messages.tryEmit(ScaleMessage.Timer(TimerStatus.PAUSED))
    activePour()

    assertEquals(listOf(ScaleCommands.resetTimer(), ScaleCommands.startTimer()), connection.sent)
    assertTrue(viewModel.ui.value.brew.phase is BrewPhase.Active)
  }

  @Test
  fun `a failed reset timer does not stop start timer and the error reaches errors`() {
    connection.failing += ScaleCommands.resetTimer()
    connection.messages.tryEmit(ScaleMessage.Timer(TimerStatus.PAUSED))
    activePour()

    assertEquals(listOf(ScaleCommands.resetTimer(), ScaleCommands.startTimer()), connection.sent)
    assertEquals(listOf<ScaleError>(ScaleError.Command("fallo simulado")), errors)
  }

  private companion object {
    const val WALL_BASE = 1_700_000_000_000L
  }
}
