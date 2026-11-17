package dev.josueamaya.pourline.session

import dev.josueamaya.pourline.brew.BrewPhase
import dev.josueamaya.pourline.protocol.ScaleCommands
import dev.josueamaya.pourline.protocol.ScaleMessage
import dev.josueamaya.pourline.protocol.WeightUnit
import dev.josueamaya.pourline.scale.ConnectionState
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** La cuenta regresiva de "Empezar timer", con báscula y sin ella: el reloj virtual del planificador es el del gestor. */
@OptIn(ExperimentalCoroutinesApi::class)
class ScaleSessionManagerCountdownTest {

  private val scheduler = TestCoroutineScheduler()
  private val connection = FakeScaleConnection()
  private val notifier = FakeNotifier()
  private val countdown = MutableStateFlow(3)
  private val plan = BrewPlan(doseG = 15.0, ratio = 16.0)
  private lateinit var manager: ScaleSessionManager
  private lateinit var managerScope: CoroutineScope
  private lateinit var applicationScope: CoroutineScope

  private val ui get() = manager.ui.value

  @Before
  fun setUp() {
    val dispatcher = UnconfinedTestDispatcher(scheduler)
    Dispatchers.setMain(dispatcher)
    applicationScope = CoroutineScope(SupervisorJob() + dispatcher)
    managerScope = CoroutineScope(SupervisorJob() + dispatcher)
    scheduler.advanceTimeBy(1_000)
    manager = ScaleSessionManager(
      { connection },
      managerScope,
      MutableStateFlow(120),
      FakeBrewSaver(),
      applicationScope,
      FakeReferenceStore(),
      FakeTargetReferences(),
      notifier,
      coffeeStore = FakeCoffeeStore(),
      coffees = FakeScaleCoffees(),
      countdownSeconds = countdown,
      clock = { scheduler.currentTime },
      wallClock = { 1_700_000_000_000L + scheduler.currentTime },
    )
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

  /** Conecta y salta los pasos previos hasta el de verter (sin dosis). */
  private fun armedWithScale() {
    manager.connect(ScaleTarget.Real("00:11:22:33:44:55", "Test"))
    assertEquals(ConnectionState.Ready, ui.connection)
    manager.startSession(plan)
    manager.prepSkip()
    manager.prepSkipDose()
    manager.prepSkip()
    assertEquals(PrepStep.POUR, ui.prep!!.step)
    connection.sent.clear()
  }

  private fun weight(grams: Double, flowGps: Double) {
    connection.messages.tryEmit(
      ScaleMessage.Weight(rawWeight = (grams * 10).toInt(), rawFlow = (flowGps * 10).toInt(), seconds = 0, unit = WeightUnit.GRAMS, outOfRange = false),
    )
    advanceMs(100)
  }

  // --- Con báscula ---

  @Test
  fun `with a scale, start timer counts down before going Active and starting the scale timer`() {
    armedWithScale()
    manager.prepStartTimer()

    assertEquals(3, ui.countdownS)
    assertEquals(BrewPhase.Armed, ui.brew.phase)
    assertTrue(connection.sent.isEmpty())

    advanceMs(1_000)
    assertEquals(2, ui.countdownS)
    advanceMs(1_000)
    assertEquals(1, ui.countdownS)
    assertEquals(BrewPhase.Armed, ui.brew.phase)

    val goAt = scheduler.currentTime + 1_000
    advanceMs(1_000)
    assertNull(ui.countdownS)
    assertTrue(ui.brew.phase is BrewPhase.Active)
    assertEquals(goAt, ui.brew.t0MonoMs)
    assertEquals(listOf(ScaleCommands.startTimer()), connection.sent)
    assertEquals(listOf(3, 2, 1, 0), notifier.countdown)
  }

  @Test
  fun `without countdown, start timer goes Active at once`() {
    countdown.value = 0
    armedWithScale()
    manager.prepStartTimer()

    assertNull(ui.countdownS)
    assertTrue(ui.brew.phase is BrewPhase.Active)
    assertTrue(notifier.countdown.isEmpty())
  }

  @Test
  fun `pouring during the countdown starts the brew and ends the countdown`() {
    countdown.value = 10
    armedWithScale()
    manager.prepStartTimer()
    advanceMs(1_000)

    val pourAt = scheduler.currentTime
    repeat(10) { weight(4.0, 4.0) }

    assertNull(ui.countdownS)
    assertTrue(ui.brew.phase is BrewPhase.Active)
    assertEquals(pourAt, ui.brew.t0MonoMs)

    // La cuenta cortada no vuelve a arrancar la sesión al cumplirse.
    advanceMs(10_000)
    assertEquals(pourAt, ui.brew.t0MonoMs)
    assertEquals(listOf(10, 9), notifier.countdown)
  }

  @Test
  fun `losing the scale during the countdown cancels it`() {
    armedWithScale()
    manager.prepStartTimer()
    advanceMs(1_000)

    connection.state.value = ConnectionState.Disconnected
    assertNull(ui.countdownS)
    assertEquals(PrepStep.TARE, ui.prep!!.step)

    advanceMs(5_000)
    assertEquals(BrewPhase.Armed, ui.brew.phase)
  }

  @Test
  fun `cancel keeps the session ready and it can start again`() {
    armedWithScale()
    manager.prepStartTimer()
    advanceMs(1_000)

    manager.cancelCountdown()
    assertNull(ui.countdownS)
    advanceMs(5_000)
    assertEquals(BrewPhase.Armed, ui.brew.phase)
    assertEquals(PrepStep.POUR, ui.prep!!.step)

    manager.prepStartTimer()
    assertEquals(3, ui.countdownS)
    advanceMs(3_000)
    assertTrue(ui.brew.phase is BrewPhase.Active)
  }

  @Test
  fun `tapping start again during the countdown does not restart it`() {
    armedWithScale()
    manager.prepStartTimer()
    advanceMs(2_000)
    manager.prepStartTimer()

    assertEquals(1, ui.countdownS)
    advanceMs(1_000)
    assertTrue(ui.brew.phase is BrewPhase.Active)
  }

  @Test
  fun `leaving the session during the countdown cancels it`() {
    armedWithScale()
    manager.prepStartTimer()
    manager.discardSession()

    assertNull(ui.countdownS)
    advanceMs(5_000)
    assertEquals(BrewPhase.Armed, ui.brew.phase)
    assertTrue(connection.sent.isEmpty())
  }

  // --- Sin báscula ---

  @Test
  fun `without a scale, start timer counts down before the clock starts`() {
    manager.startScalelessSession(plan)
    manager.scalelessStartTimer()

    assertEquals(3, ui.countdownS)
    assertEquals(BrewPhase.Armed, ui.brew.phase)

    val goAt = scheduler.currentTime + 3_000
    advanceMs(3_000)
    assertNull(ui.countdownS)
    assertTrue(ui.brew.phase is BrewPhase.Active)
    assertEquals(goAt, ui.brew.t0MonoMs)
    assertEquals(0L, ui.elapsedMs)
  }

  @Test
  fun `without a scale, cancel and end session stop the countdown`() {
    manager.startScalelessSession(plan)
    manager.scalelessStartTimer()
    manager.cancelCountdown()
    advanceMs(5_000)
    assertEquals(BrewPhase.Armed, ui.brew.phase)

    manager.scalelessStartTimer()
    manager.endSession()
    advanceMs(5_000)
    assertNull(ui.countdownS)
    assertEquals(BrewPhase.Armed, ui.brew.phase)
    assertFalse(ui.scaleless)
  }
}
