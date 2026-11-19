package dev.josueamaya.pourline.coffee.photo

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.scale
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Decodifica las fotos fuera del hilo principal y las guarda en una `LruCache` por bytes: sin añadir una
 * librería de carga de imágenes. La decodificación usa como mucho dos hilos.
 */
class CoffeeImages(
  private val store: CoffeePhotoStore,
  maxBytes: Int,
  private val dispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(2),
) {
  private val cache = object : LruCache<String, ImageBitmap>(maxBytes) {
    override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
  }

  /** La miniatura de 256 px de la foto [name]; `null` si no se pudo leer. */
  suspend fun thumbnail(name: String): ImageBitmap? = load("thumb:$name") { decode(store.thumbFile(name), THUMB_SIDE) }

  /** La foto [name] con el lado mayor de a lo sumo [maxSidePx] (sin ampliar); `null` si no se pudo leer. */
  suspend fun photo(name: String, maxSidePx: Int): ImageBitmap? =
    load("photo:$name@$maxSidePx") { decode(store.file(name), maxSidePx) }

  private suspend fun load(key: String, decode: () -> Bitmap?): ImageBitmap? {
    cache.get(key)?.let { return it }
    return withContext(dispatcher) {
      cache.get(key) ?: decode()?.asImageBitmap()?.also { cache.put(key, it) }
    }
  }

  private fun decode(file: File, maxSide: Int): Bitmap? {
    if (!file.exists()) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    if (bounds.outWidth <= 0) return null
    val options = BitmapFactory.Options().apply { inSampleSize = inSampleSize(bounds.outWidth, bounds.outHeight, maxSide) }
    val bitmap = BitmapFactory.decodeFile(file.path, options) ?: return null
    val (w, h) = scaledSize(bitmap.width, bitmap.height, maxSide)
    return if (w == bitmap.width && h == bitmap.height) bitmap else bitmap.scale(w, h)
  }
}

/** Las fotos llegan a los composables por aquí (D14); con `null` se dibuja el marcador sin foto. */
val LocalCoffeeImages = staticCompositionLocalOf<CoffeeImages?> { null }
