package dev.dotscale.ui.scale

import androidx.lifecycle.viewModelScope
import dev.dotscale.brew.BrewPhase
import dev.dotscale.brew.FinishReason
import dev.dotscale.protocol.Frame
import dev.dotscale.protocol.ScaleCommands
import dev.dotscale.protocol.ScaleMessage
import dev.dotscale.protocol.TimerStatus
import dev.dotscale.protocol.WeightUnit
import dev.dotscale.scale.ConnectionState
import dev.dotscale.scale.ScaleConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScaleViewModelBrewTest {

  private class FakeConnection : ScaleConnection {
    override val state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val messages = MutableSharedFlow<ScaleMessage>(extraBufferCapacity = 64)
    val sent = mutableListOf<Frame>()

    override suspend fun connect() {
      state.value = ConnectionState.Ready
    }

    override suspend fun send(frame: Frame) {
      sent += frame
    }

    override fun disconnect() = Unit
  }

  private val connection = FakeConnection()
  private val scheduler = TestCoroutineScheduler()
  private val autoFinish = MutableStateFlow(60)
  private var now = 1_000L
  private lateinit var viewModel: ScaleViewModel

  @Before
  fun setUp() {
    Dispatchers.setMain(UnconfinedTestDispatcher(scheduler))
    viewModel = ScaleViewModel(connection, autoFinish, clock = { now })
  }

  /** Pasa el tiempo sin notificaciones: avanza el reloj y el tick de 1 s del ViewModel. */
  private fun advanceSeconds(seconds: Int) = repeat(seconds) {
    now += 1_000
    scheduler.advanceTimeBy(1_000)
    scheduler.runCurrent()
  }

  @After
  fun tearDown() {
    viewModel.viewModelScope.cancel() // el tick de 1 s no termina solo
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
}
