package dev.josueamaya.pourline.session

import dev.josueamaya.pourline.brew.BrewPhase
import dev.josueamaya.pourline.brew.FinishReason
import dev.josueamaya.pourline.protocol.ScaleMessage
import dev.josueamaya.pourline.protocol.WeightUnit
import dev.josueamaya.pourline.scale.ConnectionState
import kotlinx.coroutines.CompletableDeferred
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Conexión bajo demanda, última báscula real y compuerta de la sesión (D15, D16, D28). */
@OptIn(ExperimentalCoroutinesApi::class)
class ScaleSessionManagerConnectionTest {

  private val factory = RecordingConnectionFactory()
  private val memory = FakeScaleMemory()
  private val saver = FakeBrewSaver()
  private val scheduler = TestCoroutineScheduler()
  private var now = 1_000L
  private lateinit var scope: CoroutineScope
  private lateinit var manager: ScaleSessionManager

  private val real = ScaleTarget.Real("AA:BB:CC:DD:EE:FF", "DOT-A1B2")
  private val simulated = ScaleTarget.Simulated("Báscula simulada")

  @Before
  fun setUp() {
    val dispatcher = UnconfinedTestDispatcher(scheduler)
    Dispatchers.setMain(dispatcher)
    scope = CoroutineScope(SupervisorJob() + dispatcher)
    manager = ScaleSessionManager(
      connections = factory,
      scope = scope,
      autoFinishSeconds = MutableStateFlow(60),
      saver = saver,
      applicationScope = CoroutineScope(SupervisorJob() + dispatcher),
      references = FakeReferenceStore(),
      targets = FakeTargetReferences(),
      notifier = FakeNotifier(),
      memory = memory,
      clock = { now },
      wallClock = { WALL_BASE + now },
    )
  }

  @After
  fun tearDown() {
    manager.disconnect()
    scope.cancel()
    Dispatchers.resetMain()
  }

  private fun weight(flowGps: Double, connection: FakeScaleConnection = factory.connections.last()) {
    now += 100
    val raw = (flowGps * 10).toInt()
    connection.messages.tryEmit(
      ScaleMessage.Weight(rawWeight = raw, rawFlow = raw, seconds = 0, unit = WeightUnit.GRAMS, outOfRange = false),
    )
  }

  private fun pour() {
    repeat(5) { weight(0.0) }
    repeat(30) { weight(4.0) }
  }

  private val phase get() = manager.ui.value.brew.phase

  @Test
  fun `nothing is created until connect is called`() {
    assertTrue(factory.targets.isEmpty())
    assertEquals(ConnectionState.Disconnected, manager.ui.value.connection)
    assertEquals(null, manager.target.value)
  }

  @Test
  fun `a real scale is remembered only when it reaches Ready`() {
    factory.configure = { it.connectGate = CompletableDeferred() }
    manager.connect(real)
    assertTrue(manager.ui.value.connection is ConnectionState.Connecting)
    assertTrue(memory.remembered.isEmpty())

    factory.connections.single().connectGate!!.complete(Unit)

    assertEquals(ConnectionState.Ready, manager.ui.value.connection)
    assertEquals(listOf(RememberedScale(real.address, real.name)), memory.remembered)
  }

  @Test
  fun `the simulated scale is never remembered`() {
    manager.connect(simulated)

    assertEquals(ConnectionState.Ready, manager.ui.value.connection)
    assertTrue(manager.ui.value.isSimulated)
    assertTrue(memory.remembered.isEmpty())
  }

  @Test
  fun `using the simulated scale does not forget the remembered real one`() {
    manager.connect(real)
    manager.connect(simulated)

    assertEquals(RememberedScale(real.address, real.name), memory.lastScale.value)
    assertEquals(1, memory.remembered.size)
  }

  @Test
  fun `connecting another scale closes the previous one and saves its active session`() {
    manager.connect(simulated)
    manager.arm()
    pour()
    assertTrue(phase is BrewPhase.Active)

    manager.connect(real)

    val first = factory.connections.first()
    assertEquals(1, first.disconnects)
    assertEquals(FinishReason.MANUAL, saver.calls.single().brew.summary.reason)
    assertEquals(listOf<ScaleTarget>(simulated, real), factory.targets)
    assertFalse(manager.ui.value.isSimulated)
    assertEquals(0.0, manager.ui.value.weight, 0.0)
  }

  @Test
  fun `retry connects again to the same target with a new connection`() {
    manager.connect(real)
    manager.retry()

    assertEquals(listOf<ScaleTarget>(real, real), factory.targets)
    assertEquals(2, factory.connections.size)
    assertEquals(1, factory.connections.first().disconnects)
    assertEquals(real, manager.target.value)
  }

  @Test
  fun `retry after a disconnect keeps the target`() {
    manager.connect(real)
    manager.disconnect()
    assertEquals(ConnectionState.Disconnected, manager.ui.value.connection)
    assertEquals(real, manager.target.value)

    manager.retry()

    assertEquals(2, factory.connections.size)
    assertEquals(ConnectionState.Ready, manager.ui.value.connection)
  }

  @Test
  fun `a connection that fails before Ready is a failure and a retry clears it`() {
    factory.configure = { it.connectGate = CompletableDeferred() }
    manager.connect(real)
    factory.connections.single().state.value = ConnectionState.Failed("timeout")

    assertTrue(manager.ui.value.lastFailure)

    factory.configure = {}
    manager.retry()

    assertFalse(manager.ui.value.lastFailure)
    assertEquals(ConnectionState.Ready, manager.ui.value.connection)
  }

  @Test
  fun `losing an established connection is not a failure`() {
    manager.connect(real)
    factory.connections.single().state.value = ConnectionState.Failed("perdida")

    assertFalse(manager.ui.value.lastFailure)
  }

  @Test
  fun `disconnect marks the user choice until the next connect`() {
    assertFalse(manager.userDisconnected)
    manager.connect(real)
    assertFalse(manager.userDisconnected)

    manager.disconnect()
    assertTrue(manager.userDisconnected)

    manager.connect(real)
    assertFalse(manager.userDisconnected)
  }

  @Test
  fun `replacing the connection is not a user disconnect`() {
    manager.connect(real)
    manager.connect(simulated)

    assertFalse(manager.userDisconnected)
  }

  @Test
  fun `a pour while disarmed neither creates a session nor saves anything`() {
    manager.connect(simulated)
    pour()

    assertFalse(manager.isArmed)
    assertFalse(phase is BrewPhase.Active)
    assertTrue(manager.ui.value.brew.samples.isEmpty())
    assertTrue(manager.ui.value.weight > 0.0) // la pantalla sí ve el peso
    manager.disconnect()
    assertTrue(saver.calls.isEmpty())
  }

  @Test
  fun `a pour while armed creates a session and saves it`() {
    manager.connect(simulated)
    manager.arm()
    pour()
    assertTrue(phase is BrewPhase.Active)

    manager.finishBrew()

    assertEquals(1, saver.calls.size)
  }

  @Test
  fun `disarming stops feeding the session`() {
    manager.connect(simulated)
    manager.arm()
    manager.disarm()
    pour()

    assertFalse(phase is BrewPhase.Active)
  }

  @Test
  fun `losing the connection while armed before the pour cleans up without saving`() {
    manager.connect(simulated)
    manager.arm()
    repeat(5) { weight(0.0) }
    assertEquals(BrewPhase.Armed, phase)

    factory.connections.last().state.value = ConnectionState.Disconnected

    assertTrue(saver.calls.isEmpty())
    assertEquals(BrewPhase.Armed, phase)
  }

  @Test
  fun `losing the connection during an active session saves it as disconnected`() {
    manager.connect(simulated)
    manager.arm()
    pour()

    factory.connections.last().state.value = ConnectionState.Disconnected

    assertEquals(FinishReason.DISCONNECTED, saver.calls.single().brew.summary.reason)
  }

  @Test
  fun `disconnect during an active session saves it as manual and disarms`() {
    manager.connect(simulated)
    manager.arm()
    pour()

    manager.disconnect()

    assertEquals(FinishReason.MANUAL, saver.calls.single().brew.summary.reason)
    assertFalse(manager.isArmed)
    assertEquals(1, factory.connections.single().disconnects)
    assertEquals(ConnectionState.Disconnected, manager.ui.value.connection)
  }

  private companion object {
    const val WALL_BASE = 1_700_000_000_000L
  }
}
