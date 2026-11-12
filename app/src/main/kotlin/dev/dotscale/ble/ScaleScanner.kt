package dev.dotscale.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import dev.dotscale.protocol.AdvertisementInfo
import dev.dotscale.protocol.ScaleGatt
import dev.dotscale.protocol.ScaleModel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

data class ScannedDevice(
  val address: String,
  val name: String?,
  val rssi: Int,
  val advertisement: AdvertisementInfo?,
  val advertisesScaleService: Boolean,
) {
  val model: ScaleModel? get() = advertisement?.model?.takeIf { it != ScaleModel.UNKNOWN }

  val looksLikeScale: Boolean
    get() = model != null || advertisesScaleService || name?.contains("TIMEMORE", ignoreCase = true) == true
}

@SuppressLint("MissingPermission") // ScanScreen pide los permisos antes de escanear.
class ScaleScanner(context: Context) {
  private val adapter = context.applicationContext.getSystemService(BluetoothManager::class.java)?.adapter

  val isBluetoothEnabled: Boolean get() = adapter?.isEnabled == true

  /** Escanea hasta que se cancele el collect. */
  fun scan(): Flow<ScannedDevice> = callbackFlow {
    val scanner = adapter?.takeIf { it.isEnabled }?.bluetoothLeScanner
    if (scanner == null) {
      close(IllegalStateException("El Bluetooth está apagado"))
      return@callbackFlow
    }
    val callback = object : ScanCallback() {
      override fun onScanResult(callbackType: Int, result: ScanResult) {
        trySend(result.toScannedDevice())
      }

      override fun onBatchScanResults(results: MutableList<ScanResult>) {
        results.forEach { trySend(it.toScannedDevice()) }
      }

      override fun onScanFailed(errorCode: Int) {
        close(IllegalStateException("El escaneo falló (código $errorCode)"))
      }
    }
    val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
    scanner.startScan(null, settings, callback)
    awaitClose { runCatching { scanner.stopScan(callback) } }
  }

  private fun ScanResult.toScannedDevice(): ScannedDevice {
    val record = scanRecord
    return ScannedDevice(
      address = device.address,
      name = record?.deviceName ?: runCatching { device.name }.getOrNull(),
      rssi = rssi,
      advertisement = AdvertisementInfo.parse(record?.getManufacturerSpecificData(AdvertisementInfo.MANUFACTURER_ID)),
      advertisesScaleService = record?.serviceUuids?.contains(SCALE_SERVICE) == true,
    )
  }

  private companion object {
    val SCALE_SERVICE: ParcelUuid = ParcelUuid.fromString(ScaleGatt.SERVICE)
  }
}
