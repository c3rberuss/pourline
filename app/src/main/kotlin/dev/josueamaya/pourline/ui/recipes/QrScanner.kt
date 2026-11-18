package dev.josueamaya.pourline.ui.recipes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Lo que da el escáner de QR. */
sealed interface ScanResult {
  data class Scanned(val text: String) : ScanResult

  /** El usuario cerró el escáner: no pasa nada. */
  data object Cancelled : ScanResult

  /** Sin Google Play Services, o el módulo del escáner no está descargado y no hay conexión. */
  data object Unavailable : ScanResult
}

/** Abre la cámara del sistema para leer un QR. En los tests, uno falso (D19). */
fun interface QrScanner {
  suspend fun scan(): ScanResult
}

/** Los tests ponen aquí un escáner falso; sin él, [rememberQrScanner] usa el de Google. */
val LocalQrScanner = staticCompositionLocalOf<QrScanner?> { null }

/** El [QrScanner] de la pantalla: el de [LocalQrScanner] o el Code Scanner de Google Play Services (sin permiso de cámara). */
@Composable
fun rememberQrScanner(): QrScanner {
  val override = LocalQrScanner.current
  val context = LocalContext.current
  return override ?: remember(context) {
    val client = GmsBarcodeScanning.getClient(
      context,
      GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).enableAutoZoom().build(),
    )
    QrScanner {
      suspendCancellableCoroutine { continuation ->
        client.startScan()
          .addOnSuccessListener { if (continuation.isActive) continuation.resume(ScanResult.Scanned(it.rawValue.orEmpty())) }
          .addOnCanceledListener { if (continuation.isActive) continuation.resume(ScanResult.Cancelled) }
          .addOnFailureListener { e ->
            // Algunas versiones del escáner informan de "cerrado sin leer" como un fallo con este código.
            val cancelled = e is MlKitException && e.errorCode == MlKitException.CODE_SCANNER_CANCELLED
            if (continuation.isActive) continuation.resume(if (cancelled) ScanResult.Cancelled else ScanResult.Unavailable)
          }
      }
    }
  }
}
