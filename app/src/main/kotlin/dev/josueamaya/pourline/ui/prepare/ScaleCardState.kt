package dev.josueamaya.pourline.ui.prepare

import dev.josueamaya.pourline.scale.ConnectionState
import dev.josueamaya.pourline.scale.ConnectionStep
import dev.josueamaya.pourline.session.RememberedScale
import dev.josueamaya.pourline.session.ScaleTarget

/** Lo que muestra la tarjeta de báscula de Preparar (§3.3 del spec y D12). */
sealed interface ScaleCardState {
  /** Faltan los permisos de Bluetooth. */
  data object NoPermission : ScaleCardState

  data object BluetoothOff : ScaleCardState

  /** Hay permisos y Bluetooth, pero ninguna báscula recordada ni elegida. */
  data object NoScale : ScaleCardState

  data class Connecting(val name: String, val step: ConnectionStep) : ScaleCardState

  data class Connected(val name: String, val batteryPercent: Int?, val simulated: Boolean) : ScaleCardState

  /** La última conexión falló antes de llegar a `Ready`. */
  data class Failed(val name: String) : ScaleCardState

  /** Se desconectó, por el usuario o por pérdida, o aún no se ha conectado a la recordada. */
  data class Disconnected(val name: String) : ScaleCardState
}

/**
 * El estado de la tarjeta. La simulada no necesita permisos ni Bluetooth: si está conectada o conectando, manda
 * sobre las dos primeras filas. Sin conexión, la báscula que se nombra es la del último destino ([target]) o, si no
 * hay, la recordada ([remembered]).
 */
fun scaleCardState(
  permissions: Boolean,
  bluetoothOn: Boolean,
  remembered: RememberedScale?,
  target: ScaleTarget?,
  connection: ConnectionState,
  lastFailure: Boolean,
  batteryPercent: Int? = null,
): ScaleCardState {
  val simulated = target is ScaleTarget.Simulated
  val name = target?.name ?: remembered?.name
  return when {
    connection is ConnectionState.Ready && name != null -> ScaleCardState.Connected(name, batteryPercent, simulated)
    connection is ConnectionState.Connecting && name != null -> ScaleCardState.Connecting(name, connection.step)
    simulated && name != null -> if (lastFailure) ScaleCardState.Failed(name) else ScaleCardState.Disconnected(name)
    !permissions -> ScaleCardState.NoPermission
    !bluetoothOn -> ScaleCardState.BluetoothOff
    name == null -> ScaleCardState.NoScale
    lastFailure -> ScaleCardState.Failed(name)
    else -> ScaleCardState.Disconnected(name)
  }
}
