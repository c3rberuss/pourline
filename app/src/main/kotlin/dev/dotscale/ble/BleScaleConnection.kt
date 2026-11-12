package dev.dotscale.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import dev.dotscale.protocol.Frame
import dev.dotscale.protocol.FrameDecoder
import dev.dotscale.protocol.ScaleCommands
import dev.dotscale.protocol.ScaleGatt
import dev.dotscale.protocol.ScaleMessage
import dev.dotscale.protocol.ScaleMessageParser
import dev.dotscale.scale.ConnectionState
import dev.dotscale.scale.ConnectionStep
import dev.dotscale.scale.ScaleConnection
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Conexión GATT con una báscula TIMEMORE del protocolo 2025.
 *
 * Secuencia (igual que `BleService` de la app oficial):
 * conectar → descubrir servicios → emparejar si hace falta → MTU 247 → activar notify en FFF1
 * → esperar 500 ms → lecturas del handshake.
 *
 * Android solo admite una operación GATT a la vez, así que todas pasan por [gattOp].
 */
@SuppressLint("MissingPermission") // ScanScreen pide los permisos antes de crear la conexión.
class BleScaleConnection(
  context: Context,
  private val address: String,
) : ScaleConnection {

  private val context = context.applicationContext
  private val adapter = this.context.getSystemService(BluetoothManager::class.java)?.adapter

  private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
  override val state: StateFlow<ConnectionState> = _state.asStateFlow()

  private val _messages = MutableSharedFlow<ScaleMessage>(extraBufferCapacity = 64)
  override val messages: SharedFlow<ScaleMessage> = _messages.asSharedFlow()

  private val decoder = FrameDecoder()
  private val parser = ScaleMessageParser()
  private val gattMutex = Mutex()

  @Volatile private var pendingOp: CompletableDeferred<Int>? = null
  @Volatile private var connected = CompletableDeferred<Unit>()
  @Volatile private var gatt: BluetoothGatt? = null
  @Volatile private var writeCharacteristic: BluetoothGattCharacteristic? = null

  private val callback = object : BluetoothGattCallback() {
    override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
      Log.d(TAG, "connection state=$newState status=$status")
      when (newState) {
        BluetoothProfile.STATE_CONNECTED -> connected.complete(Unit)
        BluetoothProfile.STATE_DISCONNECTED -> {
          val error = IOException("La báscula se desconectó (status $status)")
          connected.completeExceptionally(error)
          pendingOp?.completeExceptionally(error)
          g.close()
          gatt = null
          writeCharacteristic = null
          _state.value =
            if (status == BluetoothGatt.GATT_SUCCESS) ConnectionState.Disconnected
            else ConnectionState.Failed(error.message.orEmpty())
        }
      }
    }

    override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
      pendingOp?.complete(status)
    }

    override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
      Log.d(TAG, "MTU=$mtu status=$status")
      pendingOp?.complete(status)
    }

    override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) {
      pendingOp?.complete(status)
    }

    override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
      pendingOp?.complete(status)
    }

    override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) {
      onNotification(value)
    }

    @Deprecated("Usado solo en Android 12 y anteriores")
    override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
      @Suppress("DEPRECATION")
      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) onNotification(c.value ?: return)
    }
  }

  override suspend fun connect() {
    val adapter = adapter ?: throw IOException("Este dispositivo no tiene Bluetooth")
    if (!adapter.isEnabled) throw IOException("El Bluetooth está apagado")
    val device = adapter.getRemoteDevice(address)
    try {
      _state.value = ConnectionState.Connecting(ConnectionStep.CONNECTING)
      decoder.reset()
      connected = CompletableDeferred()
      // API 37 añade connectGatt(BluetoothGattConnectionSettings, …); esta variante funciona desde API 26.
      @Suppress("DEPRECATION")
      gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
      withTimeout(CONNECT_TIMEOUT_MS) { connected.await() }

      _state.value = ConnectionState.Connecting(ConnectionStep.DISCOVERING)
      delay(300) // algunos stacks fallan si se descubren servicios justo al conectar
      requireSuccess(gattOp(10_000) { it.discoverServices() }, "descubrir servicios")
      val service = gatt?.getService(UUID.fromString(ScaleGatt.SERVICE))
        ?: throw IOException("No se encontró el servicio FFF0: ¿es una báscula TIMEMORE?")
      val notify = service.getCharacteristic(UUID.fromString(ScaleGatt.NOTIFY_CHARACTERISTIC))
        ?: throw IOException("No se encontró la característica FFF1")
      writeCharacteristic = service.getCharacteristic(UUID.fromString(ScaleGatt.WRITE_CHARACTERISTIC))
        ?: throw IOException("No se encontró la característica FFF2")

      if (device.bondState == BluetoothDevice.BOND_NONE) {
        _state.value = ConnectionState.Connecting(ConnectionStep.BONDING)
        bond(device)
      }

      _state.value = ConnectionState.Connecting(ConnectionStep.CONFIGURING)
      // Si falla el MTU se sigue con el de por defecto: las tramas normales caben en 20 bytes.
      runCatching { gattOp(3_000) { it.requestMtu(ScaleGatt.PREFERRED_MTU) } }
      enableNotifications(notify)

      _state.value = ConnectionState.Connecting(ConnectionStep.HANDSHAKE)
      delay(500)
      ScaleCommands.handshake.forEach { send(it) }
      _state.value = ConnectionState.Ready
    } catch (e: TimeoutCancellationException) {
      fail(IOException("Tiempo de espera agotado", e))
    } catch (e: CancellationException) {
      disconnect()
      throw e
    } catch (e: Exception) {
      fail(e)
    }
  }

  override suspend fun send(frame: Frame) {
    val characteristic = writeCharacteristic ?: throw IOException("La báscula no está conectada")
    val bytes = frame.encode()
    val writeType =
      if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) {
        BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
      } else {
        BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
      }
    Log.d(TAG, "TX $frame")
    val status = gattOp { g ->
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        g.writeCharacteristic(characteristic, bytes, writeType) == BluetoothStatusCodes.SUCCESS
      } else {
        @Suppress("DEPRECATION")
        characteristic.writeType = writeType
        @Suppress("DEPRECATION")
        characteristic.value = bytes
        @Suppress("DEPRECATION")
        g.writeCharacteristic(characteristic)
      }
    }
    requireSuccess(status, "enviar comando")
  }

  override fun disconnect() {
    gatt?.let {
      it.disconnect()
      it.close()
    }
    gatt = null
    writeCharacteristic = null
    _state.value = ConnectionState.Disconnected
  }

  private fun onNotification(value: ByteArray) {
    synchronized(decoder) {
      decoder.feed(value).forEach { frame ->
        if (!frame.crcValid) Log.w(TAG, "CRC inválido (normal en la DOT): $frame")
        val message = parser.parse(frame)
        if (message is ScaleMessage.Unknown) Log.d(TAG, "RX sin interpretar: $frame")
        _messages.tryEmit(message)
      }
    }
  }

  private suspend fun enableNotifications(characteristic: BluetoothGattCharacteristic) {
    val g = gatt ?: throw IOException("La báscula no está conectada")
    if (!g.setCharacteristicNotification(characteristic, true)) {
      throw IOException("No se pudieron activar las notificaciones")
    }
    val cccd = characteristic.getDescriptor(UUID.fromString(ScaleGatt.CLIENT_CONFIG_DESCRIPTOR))
      ?: throw IOException("FFF1 no tiene descriptor CCCD")
    val value =
      if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) {
        BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
      } else {
        BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
      }
    val status = gattOp { gg ->
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        gg.writeDescriptor(cccd, value) == BluetoothStatusCodes.SUCCESS
      } else {
        @Suppress("DEPRECATION")
        cccd.value = value
        @Suppress("DEPRECATION")
        gg.writeDescriptor(cccd)
      }
    }
    requireSuccess(status, "activar notificaciones")
  }

  /** La app oficial empareja antes de pedir el MTU. Si no se completa, se intenta seguir igual. */
  private suspend fun bond(device: BluetoothDevice) {
    val result = CompletableDeferred<Boolean>()
    val receiver = object : BroadcastReceiver() {
      override fun onReceive(c: Context, intent: Intent) {
        val d = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        if (d?.address != address) return
        when (intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)) {
          BluetoothDevice.BOND_BONDED -> result.complete(true)
          BluetoothDevice.BOND_NONE -> result.complete(false)
        }
      }
    }
    ContextCompat.registerReceiver(
      context,
      receiver,
      IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED),
      ContextCompat.RECEIVER_NOT_EXPORTED,
    )
    try {
      if (!device.createBond()) {
        Log.w(TAG, "createBond() rechazado; se continúa sin emparejar")
        return
      }
      val bonded = withTimeoutOrNull(BOND_TIMEOUT_MS) { result.await() }
      Log.d(TAG, "bond result=$bonded")
    } finally {
      context.unregisterReceiver(receiver)
    }
  }

  private suspend fun gattOp(timeoutMs: Long = 5_000, start: (BluetoothGatt) -> Boolean): Int =
    gattMutex.withLock {
      val g = gatt ?: throw IOException("La báscula no está conectada")
      val op = CompletableDeferred<Int>()
      pendingOp = op
      try {
        if (!start(g)) throw IOException("Android rechazó la operación GATT")
        withTimeout(timeoutMs) { op.await() }
      } finally {
        pendingOp = null
      }
    }

  private fun requireSuccess(status: Int, action: String) {
    if (status != BluetoothGatt.GATT_SUCCESS) throw IOException("Error al $action (status $status)")
  }

  private fun fail(error: Exception): Nothing {
    Log.e(TAG, "connect failed", error)
    gatt?.close()
    gatt = null
    writeCharacteristic = null
    _state.value = ConnectionState.Failed(error.message ?: error.javaClass.simpleName)
    throw error
  }

  private companion object {
    const val TAG = "BleScale"
    const val CONNECT_TIMEOUT_MS = 15_000L
    const val BOND_TIMEOUT_MS = 30_000L
  }
}
