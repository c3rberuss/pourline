package dev.josueamaya.pourline.share

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.util.Log
import kotlinx.coroutines.runBlocking

/**
 * El modelo de lectura de Play Services se descarga bajo demanda: espera, hasta 240 s, a que [reader] lea un QR
 * antes de medir nada. Hace falta un emulador con Play Store (`emulator-5560`, AVD `SG_API36_play`).
 */
internal fun awaitQrModel(reader: MlKitQrImageReader) {
  val uri = "pourline://recipe?v=1&d=" + "AbCdEfGh0123456789-_".repeat(14).take(276)
  val bitmap = Bitmap.createBitmap(1080, 1350, Bitmap.Config.ARGB_8888)
  Canvas(bitmap).apply {
    drawColor(Color.WHITE)
    drawQr(this, qrMatrix(uri), 1080 - 48 - 432, 228, 432)
  }
  val deadline = System.nanoTime() + 240_000_000_000L
  while (true) {
    try {
      if (runBlocking { reader.readBitmap(bitmap) }.contains(uri)) return
    } catch (e: Exception) {
      Log.i("QrTestSupport", "modelo no listo: ${e.message}")
    }
    check(System.nanoTime() < deadline) { "el modelo de lectura de QR no se descargó en 240 s" }
    Thread.sleep(3_000)
  }
}
