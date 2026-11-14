package dev.dotscale.session

import dev.dotscale.brew.BrewPhase
import dev.dotscale.brew.FinishReason
import dev.dotscale.protocol.ScaleMessage
import dev.dotscale.protocol.WeightUnit
import dev.dotscale.scale.ConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** El gestor se monta sin `ViewModel`: con un ámbito de test, conecta, recibe pesos y `disconnect()` guarda la sesión. */
@OptIn(ExperimentalCoroutinesApi::class)
class ScaleSessionManagerSmokeTest {

  @Test
  fun `disconnect ends and saves an active session without any ViewModel`() = runTest(UnconfinedTestDispatcher()) {
    val factory = RecordingConnectionFactory()
    val saver = FakeBrewSaver()
    val notifier = FakeNotifier()
    var now = 1_000L
    val manager = ScaleSessionManager(
      connections = factory,
      scope = backgroundScope,
      autoFinishSeconds = MutableStateFlow(60),
      saver = saver,
      applicationScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
      references = FakeReferenceStore(),
      targets = FakeTargetReferences(),
      notifier = notifier,
      clock = { now },
      wallClock = { 1_700_000_000_000L + now },
    )
    manager.connect(ScaleTarget.Simulated("Test"))
    manager.arm()
    runCurrent()
    assertEquals(ConnectionState.Ready, manager.ui.value.connection)
    val connection = factory.connections.single()

    fun weight(flowGps: Double) {
      now += 100
      val raw = (flowGps * 10).toInt()
      connection.messages.tryEmit(
        ScaleMessage.Weight(rawWeight = raw, rawFlow = raw, seconds = 0, unit = WeightUnit.GRAMS, outOfRange = false),
      )
    }
    repeat(5) { weight(0.0) }
    repeat(30) { weight(4.0) }
    assertTrue(manager.ui.value.brew.phase is BrewPhase.Active)
    assertTrue(saver.calls.isEmpty())

    manager.disconnect()

    val call = saver.calls.single()
    assertEquals(FinishReason.MANUAL, call.brew.summary.reason)
    assertEquals(1, notifier.released)
  }
}
