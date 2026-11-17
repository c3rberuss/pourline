package dev.josueamaya.pourline.session

import dev.josueamaya.pourline.ble.BluetoothEnvironment
import dev.josueamaya.pourline.scale.ConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/** Los momentos en los que se intenta reconectar a la última báscula real (D29). */
enum class ReconnectTrigger { APP_START, APP_RESUMED, BLUETOOTH_ON }

/**
 * La política de la reconexión automática: solo con una báscula real recordada, permisos, Bluetooth encendido,
 * nada conectado ni conectando, y si el usuario no pulsó "Desconectar" desde la última conexión. Un fallo no se
 * reintenta solo: tiene que llegar otro disparador.
 */
fun shouldReconnect(
  remembered: RememberedScale?,
  permissionsGranted: Boolean,
  bluetoothOn: Boolean,
  connection: ConnectionState,
  userDisconnected: Boolean,
): Boolean = remembered != null &&
  permissionsGranted &&
  bluetoothOn &&
  !userDisconnected &&
  (connection is ConnectionState.Disconnected || connection is ConnectionState.Failed)

/**
 * Reconecta a la última báscula real al abrir la app, al volver a primer plano y al encenderse el Bluetooth.
 * No hay bucles en segundo plano: cada intento sale de un disparador.
 */
class AutoReconnect(
  private val manager: ScaleSessionManager,
  private val memory: ScaleMemory,
  private val bluetooth: BluetoothEnvironment,
  scope: CoroutineScope,
) {
  private var started = false

  init {
    // El paso de apagado a encendido; el valor inicial no es un disparador (para eso está `APP_START`).
    scope.launch {
      bluetooth.enabled.drop(1).filter { it }.collect { onTrigger(ReconnectTrigger.BLUETOOTH_ON) }
    }
  }

  /** `ON_START` de la actividad: el primero del proceso es `APP_START` y los siguientes, `APP_RESUMED`. */
  fun onForeground() {
    onTrigger(if (started) ReconnectTrigger.APP_RESUMED else ReconnectTrigger.APP_START)
    started = true
  }

  fun onTrigger(@Suppress("UNUSED_PARAMETER") trigger: ReconnectTrigger) {
    val remembered = memory.lastScale.value
    val reconnect = shouldReconnect(
      remembered,
      bluetooth.permissionsGranted(),
      bluetooth.enabled.value,
      manager.ui.value.connection,
      manager.userDisconnected,
    )
    if (reconnect && remembered != null) manager.connect(ScaleTarget.Real(remembered.address, remembered.name))
  }
}
