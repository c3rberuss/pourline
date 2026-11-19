package dev.josueamaya.pourline.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** El estado del Bluetooth del teléfono que necesitan Preparar y la reconexión automática. */
interface BluetoothEnvironment {
  /** Los permisos de [BLUETOOTH_PERMISSIONS] están concedidos (se consulta cada vez: el usuario puede cambiarlos fuera de la app). */
  fun permissionsGranted(): Boolean

  /** El adaptador está encendido; sigue a `ACTION_STATE_CHANGED`. */
  val enabled: StateFlow<Boolean>
}

/** Producción: lee el adaptador y escucha `ACTION_STATE_CHANGED` mientras viva el proceso. */
class AndroidBluetoothEnvironment(context: Context) : BluetoothEnvironment {
  private val appContext = context.applicationContext
  private val adapter = appContext.getSystemService(BluetoothManager::class.java)?.adapter

  private val _enabled = MutableStateFlow(adapter?.isEnabled == true)
  override val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

  init {
    val receiver = object : BroadcastReceiver() {
      override fun onReceive(context: Context, intent: Intent) {
        val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
        if (state == BluetoothAdapter.STATE_ON) _enabled.value = true
        if (state == BluetoothAdapter.STATE_OFF) _enabled.value = false
      }
    }
    appContext.registerReceiver(receiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
    // El estado pudo cambiar entre leerlo y registrar el receptor.
    _enabled.value = adapter?.isEnabled == true
  }

  override fun permissionsGranted(): Boolean = appContext.hasBluetoothPermissions()
}
