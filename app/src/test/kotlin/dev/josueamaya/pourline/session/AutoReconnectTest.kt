package dev.josueamaya.pourline.session

import dev.josueamaya.pourline.ble.BluetoothEnvironment
import dev.josueamaya.pourline.scale.ConnectionState
import dev.josueamaya.pourline.scale.ConnectionStep
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

class FakeBluetoothEnvironment(permissions: Boolean = true, bluetoothOn: Boolean = true) : BluetoothEnvironment {
  var permissions = permissions
  val on = MutableStateFlow(bluetoothOn)
  override fun permissionsGranted() = permissions
  override val enabled: StateFlow<Boolean> get() = on
}

/** La política pura y los tres disparadores de la reconexión automática (D29). */
@OptIn(ExperimentalCoroutinesApi::class)
class AutoReconnectTest {
  private val remembered = RememberedScale("AA:BB:CC:DD:EE:FF", "DOT-A1B2")
  private val real = ScaleTarget.Real(remembered.address, remembered.name)

  // --- política pura ---

  private fun should(
    remembered: RememberedScale? = this.remembered,
    permissions: Boolean = true,
    bluetooth: Boolean = true,
    connection: ConnectionState = ConnectionState.Disconnected,
    userDisconnected: Boolean = false,
  ) = shouldReconnect(remembered, permissions, bluetooth, connection, userDisconnected)

  @Test
  fun `reconnects with a remembered scale, permissions and Bluetooth`() {
    assertTrue(should())
  }

  @Test
  fun `reconnects after a failure or a lost connection`() {
    assertTrue(should(connection = ConnectionState.Failed("timeout")))
  }

  @Test
  fun `does not reconnect without permissions`() {
    assertFalse(should(permissions = false))
  }

  @Test
  fun `does not reconnect with Bluetooth off`() {
    assertFalse(should(bluetooth = false))
  }

  @Test
  fun `does not reconnect without a remembered scale`() {
    assertFalse(should(remembered = null))
  }

  @Test
  fun `does not reconnect after the user disconnected`() {
    assertFalse(should(userDisconnected = true))
  }

  @Test
  fun `does nothing when already connected or connecting`() {
    assertFalse(should(connection = ConnectionState.Ready))
    assertFalse(should(connection = ConnectionState.Connecting(ConnectionStep.BONDING)))
  }

  // --- con el gestor ---

  private val factory = RecordingConnectionFactory()
  private val memory = FakeScaleMemory()
  private val bluetooth = FakeBluetoothEnvironment()
  private val scheduler = TestCoroutineScheduler()
  private lateinit var scope: CoroutineScope
  private lateinit var manager: ScaleSessionManager
  private lateinit var autoReconnect: AutoReconnect

  @Before
  fun setUp() {
    val dispatcher = UnconfinedTestDispatcher(scheduler)
    Dispatchers.setMain(dispatcher)
    scope = CoroutineScope(SupervisorJob() + dispatcher)
    manager = ScaleSessionManager(
      connections = factory,
      scope = scope,
      autoFinishSeconds = MutableStateFlow(60),
      saver = FakeBrewSaver(),
      applicationScope = CoroutineScope(SupervisorJob() + dispatcher),
      references = FakeReferenceStore(),
      targets = FakeTargetReferences(),
      notifier = FakeNotifier(),
      memory = memory,
      clock = { 1_000L },
      wallClock = { 1_000L },
    )
    autoReconnect = AutoReconnect(manager, memory, bluetooth, scope)
  }

  @After
  fun tearDown() {
    manager.disconnect()
    scope.cancel()
    Dispatchers.resetMain()
  }

  private fun remember() = memory.remember(remembered).also { memory.remembered.clear() }

  @Test
  fun `the first foreground is the app start and connects to the remembered scale`() {
    remember()

    autoReconnect.onForeground()

    assertEquals(listOf<ScaleTarget>(real), factory.targets)
    assertEquals(ConnectionState.Ready, manager.ui.value.connection)
  }

  @Test
  fun `without a remembered scale nothing connects`() {
    autoReconnect.onForeground()

    assertTrue(factory.targets.isEmpty())
  }

  @Test
  fun `only the simulated scale in use leaves nothing to reconnect`() {
    manager.connect(ScaleTarget.Simulated("Simulación"))
    manager.disconnect()
    manager.retry()
    manager.disconnect()

    autoReconnect.onForeground()

    assertTrue(factory.targets.all { it is ScaleTarget.Simulated })
  }

  @Test
  fun `without permissions it does not connect and Bluetooth turning on does not either`() {
    remember()
    bluetooth.permissions = false
    bluetooth.on.value = false

    autoReconnect.onForeground()
    bluetooth.on.value = true

    assertTrue(factory.targets.isEmpty())
  }

  @Test
  fun `Bluetooth turning on reconnects`() {
    remember()
    bluetooth.on.value = false
    autoReconnect.onForeground()
    assertTrue(factory.targets.isEmpty())

    bluetooth.on.value = true

    assertEquals(listOf<ScaleTarget>(real), factory.targets)
  }

  @Test
  fun `a failure is not retried until the next trigger`() {
    remember()
    factory.configure = { it.connectGate = CompletableDeferred() }
    autoReconnect.onForeground()
    factory.connections.single().state.value = ConnectionState.Failed("timeout")

    assertEquals(1, factory.targets.size)

    autoReconnect.onForeground()

    assertEquals(2, factory.targets.size)
  }

  @Test
  fun `after the user disconnects, returning to the app does not reconnect`() {
    remember()
    autoReconnect.onForeground()
    manager.disconnect()

    autoReconnect.onForeground()

    assertEquals(1, factory.targets.size)
    assertEquals(ConnectionState.Disconnected, manager.ui.value.connection)
  }

  @Test
  fun `already connected or connecting, a trigger does nothing`() {
    remember()
    factory.configure = { it.connectGate = CompletableDeferred() }
    autoReconnect.onForeground()
    assertTrue(manager.ui.value.connection is ConnectionState.Connecting)

    autoReconnect.onForeground()
    bluetooth.on.value = false
    bluetooth.on.value = true

    assertEquals(1, factory.targets.size)
  }

  @Test
  fun `the second foreground is a resume`() {
    remember()
    autoReconnect.onForeground()
    factory.connections.single().state.value = ConnectionState.Disconnected

    autoReconnect.onForeground()

    assertEquals(2, factory.targets.size)
  }
}
