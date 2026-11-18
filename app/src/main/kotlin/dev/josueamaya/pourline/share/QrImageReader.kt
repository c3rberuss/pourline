package dev.josueamaya.pourline.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.graphics.scale
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import dev.josueamaya.pourline.coffee.label.await
import dev.josueamaya.pourline.coffee.photo.inSampleSize
import dev.josueamaya.pourline.coffee.photo.scaledSize
import dev.josueamaya.pourline.recipe.share.QrReadResult
import dev.josueamaya.pourline.recipe.share.RecipeShareCodec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.IOException

/** Lado mayor al que se reduce una imagen antes de buscarle el QR. */
const val QR_IMAGE_MAX_SIDE = 2048

/** Lee el texto del QR de una receta que hay en una imagen. */
fun interface QrImageReader {
  /**
   * El primer QR que empieza por [RecipeShareCodec.URI_PREFIX] ([QrReadResult.Found]), [QrReadResult.NoRecipe] si no hay
   * ninguno o la imagen no se puede leer, o [QrReadResult.ModelNotReady] si Play Services aún no tiene el modelo.
   */
  suspend fun read(uri: Uri): QrReadResult
}

/**
 * ML Kit `barcode-scanning` de Google Play Services: el modelo lo descarga Play Services y no va en el APK (D18). Hasta
 * que lo tiene, [read] da [QrReadResult.ModelNotReady] y pide la descarga.
 */
class MlKitQrImageReader(private val context: Context) : QrImageReader, Closeable {
  private val scanner by lazy {
    BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build())
  }

  /** El texto de todos los QR que ML Kit encuentra en [bitmap]. */
  suspend fun readBitmap(bitmap: Bitmap): List<String> =
    scanner.process(InputImage.fromBitmap(bitmap, 0)).await().mapNotNull { it.rawValue }

  override suspend fun read(uri: Uri): QrReadResult {
    val bitmap = withContext(Dispatchers.IO) { decode(uri) } ?: return QrReadResult.NoRecipe
    return try {
      readBitmap(bitmap).firstOrNull { it.startsWith(RecipeShareCodec.URI_PREFIX) }
        ?.let { QrReadResult.Found(it) } ?: QrReadResult.NoRecipe
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      if (e.isModelUnavailable()) {
        requestModel()
        QrReadResult.ModelNotReady
      } else {
        QrReadResult.NoRecipe
      }
    } finally {
      bitmap.recycle()
    }
  }

  /** `UNAVAILABLE` es "esperando a que se descargue el módulo de códigos de barras" (D18). */
  private fun Exception.isModelUnavailable(): Boolean =
    generateSequence<Throwable>(this) { it.cause }.any { it is MlKitException && it.errorCode == MlKitException.UNAVAILABLE }

  /** Pide a Play Services el modelo, sin esperar el resultado: la próxima lectura ya lo tendrá. */
  private fun requestModel() {
    try {
      ModuleInstall.getClient(context)
        .installModules(ModuleInstallRequest.newBuilder().addApi(scanner).build())
    } catch (_: Exception) {
      // Sin Play Services no hay nada que pedir: el mensaje de "se está preparando" ya se muestra.
    }
  }

  /** El bitmap reducido a [QR_IMAGE_MAX_SIDE], o `null` si no se puede leer el archivo. */
  private fun decode(uri: Uri): Bitmap? = try {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
      null
    } else {
      val options = BitmapFactory.Options().apply {
        inSampleSize = inSampleSize(bounds.outWidth, bounds.outHeight, QR_IMAGE_MAX_SIDE)
      }
      val decoded = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
      decoded?.let { scaled(it) }
    }
  } catch (_: SecurityException) {
    null
  } catch (_: IOException) {
    null
  } catch (_: RuntimeException) {
    null
  } catch (_: OutOfMemoryError) {
    null
  }

  private fun scaled(bitmap: Bitmap): Bitmap {
    val (width, height) = scaledSize(bitmap.width, bitmap.height, QR_IMAGE_MAX_SIDE)
    if (width == bitmap.width && height == bitmap.height) return bitmap
    return bitmap.scale(width, height).also { if (it !== bitmap) bitmap.recycle() }
  }

  override fun close() {
    scanner.close()
  }
}
