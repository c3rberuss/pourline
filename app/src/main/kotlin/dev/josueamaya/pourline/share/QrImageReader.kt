package dev.josueamaya.pourline.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.graphics.scale
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import dev.josueamaya.pourline.coffee.label.await
import dev.josueamaya.pourline.coffee.photo.inSampleSize
import dev.josueamaya.pourline.coffee.photo.scaledSize
import dev.josueamaya.pourline.recipe.share.RecipeShareCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.IOException

/** Lado mayor al que se reduce una imagen antes de buscarle el QR. */
const val QR_IMAGE_MAX_SIDE = 2048

/** Lee el texto del QR de una receta que hay en una imagen. */
fun interface QrImageReader {
  /** El texto del primer QR que empieza por [RecipeShareCodec.URI_PREFIX], o `null` si no hay ninguno o no se puede leer. */
  suspend fun read(uri: Uri): String?
}

/** ML Kit `barcode-scanning` con el modelo incluido en el APK: lee sin red (D18). */
class MlKitQrImageReader(private val context: Context) : QrImageReader, Closeable {
  private val scanner by lazy {
    BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build())
  }

  /** El texto de todos los QR que ML Kit encuentra en [bitmap]. */
  suspend fun readBitmap(bitmap: Bitmap): List<String> =
    scanner.process(InputImage.fromBitmap(bitmap, 0)).await().mapNotNull { it.rawValue }

  override suspend fun read(uri: Uri): String? {
    val bitmap = withContext(Dispatchers.IO) { decode(uri) } ?: return null
    return try {
      readBitmap(bitmap).firstOrNull { it.startsWith(RecipeShareCodec.URI_PREFIX) }
    } catch (_: RuntimeException) {
      null
    } finally {
      bitmap.recycle()
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
