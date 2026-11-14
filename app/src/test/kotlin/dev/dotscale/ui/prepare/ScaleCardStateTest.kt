package dev.dotscale.ui.prepare

import dev.dotscale.scale.ConnectionState
import dev.dotscale.scale.ConnectionStep
import dev.dotscale.session.RememberedScale
import dev.dotscale.session.ScaleTarget
import org.junit.Assert.assertEquals
import org.junit.Test

/** Cada fila de la tabla de §3.3 del spec y las variantes de D12. */
class ScaleCardStateTest {
  private val remembered = RememberedScale("AA:BB", "DOT-A1B2")
  private val real = ScaleTarget.Real("AA:BB", "DOT-A1B2")
  private val simulated = ScaleTarget.Simulated("Simulación")

  private fun state(
    permissions: Boolean = true,
    bluetoothOn: Boolean = true,
    remembered: RememberedScale? = null,
    target: ScaleTarget? = null,
    connection: ConnectionState = ConnectionState.Disconnected,
    lastFailure: Boolean = false,
    battery: Int? = null,
  ) = scaleCardState(permissions, bluetoothOn, remembered, target, connection, lastFailure, battery)

  @Test
  fun withoutPermissionsTheCardAsksForThem() {
    assertEquals(ScaleCardState.NoPermission, state(permissions = false, remembered = remembered))
  }

  @Test
  fun withBluetoothOffTheCardAsksToTurnItOn() {
    assertEquals(ScaleCardState.BluetoothOff, state(bluetoothOn = false, remembered = remembered))
  }

  @Test
  fun permissionsComeBeforeBluetooth() {
    assertEquals(ScaleCardState.NoPermission, state(permissions = false, bluetoothOn = false))
  }

  @Test
  fun withoutARememberedScaleTheCardOffersToConnect() {
    assertEquals(ScaleCardState.NoScale, state())
  }

  @Test
  fun aRememberedScaleThatIsNotConnectedShowsAsDisconnected() {
    assertEquals(ScaleCardState.Disconnected("DOT-A1B2"), state(remembered = remembered))
  }

  @Test
  fun connectingShowsTheNameAndTheStep() {
    assertEquals(
      ScaleCardState.Connecting("DOT-A1B2", ConnectionStep.BONDING),
      state(target = real, connection = ConnectionState.Connecting(ConnectionStep.BONDING)),
    )
  }

  @Test
  fun readyShowsTheNameAndTheBattery() {
    assertEquals(
      ScaleCardState.Connected("DOT-A1B2", 80, simulated = false),
      state(remembered = remembered, target = real, connection = ConnectionState.Ready, battery = 80),
    )
  }

  @Test
  fun aFailedConnectionOffersToRetry() {
    assertEquals(
      ScaleCardState.Failed("DOT-A1B2"),
      state(remembered = remembered, target = real, connection = ConnectionState.Failed("timeout"), lastFailure = true),
    )
  }

  @Test
  fun aLostConnectionIsDisconnectedNotFailed() {
    assertEquals(
      ScaleCardState.Disconnected("DOT-A1B2"),
      state(remembered = remembered, target = real, connection = ConnectionState.Failed("perdida"), lastFailure = false),
    )
  }

  @Test
  fun aUserDisconnectIsDisconnected() {
    assertEquals(
      ScaleCardState.Disconnected("DOT-A1B2"),
      state(remembered = remembered, target = real, connection = ConnectionState.Disconnected),
    )
  }

  @Test
  fun theSimulatedScaleConnectedIsMarkedAsSimulated() {
    assertEquals(
      ScaleCardState.Connected("Simulación", null, simulated = true),
      state(target = simulated, connection = ConnectionState.Ready),
    )
  }

  @Test
  fun theSimulatedScaleNeedsNeitherPermissionsNorBluetooth() {
    assertEquals(
      ScaleCardState.Connected("Simulación", null, simulated = true),
      state(permissions = false, bluetoothOn = false, target = simulated, connection = ConnectionState.Ready),
    )
  }

  @Test
  fun usingTheSimulatedScaleDoesNotHideTheRememberedOneWhenItDisconnects() {
    // P4: la real recordada sigue en la memoria; la tarjeta nombra el último destino (la simulada).
    assertEquals(
      ScaleCardState.Disconnected("Simulación"),
      state(remembered = remembered, target = simulated, connection = ConnectionState.Disconnected),
    )
  }
}
