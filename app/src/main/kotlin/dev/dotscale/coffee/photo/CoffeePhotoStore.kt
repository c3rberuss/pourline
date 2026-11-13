package dev.dotscale.coffee.photo

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import androidx.core.graphics.scale
import dev.dotscale.coffee.PhotoFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.UUID

/** La foto no se pudo leer, reducir o guardar. */
class PhotoImportException(message: String, cause: Throwable? = null) : IOException(message, cause)

/**
 * Fotos de las etiquetas en [dir] (`filesDir/coffee_photos`): `<uuid>.jpg` (lado mayor ≤ 1600 px, JPEG 85) y
 * `<uuid>_thumb.jpg` (256 × 256 recortada al centro, JPEG 80). Las columnas del catálogo guardan solo el nombre
 * principal. La rotación EXIF se aplica al importar (D25), así que el OCR lee la foto ya derecha.
 */
class CoffeePhotoStore(private val dir: File, private val cacheDir: File) : PhotoFiles {

  /** Importa la foto que abre [open] (se abre dos veces: límites y píxeles; y de nuevo para el EXIF) y devuelve su nombre. */
  suspend fun import(open: () -> InputStream): String = withContext(Dispatchers.IO) {
    try {
      dir.mkdirs()
      val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
      open().use { BitmapFactory.decodeStream(it, null, bounds) }
      if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw PhotoImportException("No es una imagen")
      val options = BitmapFactory.Options().apply {
        inSampleSize = inSampleSize(bounds.outWidth, bounds.outHeight, PHOTO_MAX_SIDE)
      }
      val decoded = open().use { BitmapFactory.decodeStream(it, null, options) }
        ?: throw PhotoImportException("No se pudo decodificar la imagen")
      val orientation = open().use { stream ->
        try {
          ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } catch (_: IOException) {
          ExifInterface.ORIENTATION_NORMAL
        }
      }
      val upright = rotateAndShrink(decoded, exifRotationDegrees(orientation))
      val name = "${UUID.randomUUID()}.jpg"
      try {
        write(upright, file(name), 85)
        val crop = centerSquare(upright.width, upright.height)
        val square = Bitmap.createBitmap(upright, crop.left, crop.top, crop.side, crop.side)
        val thumb = square.scale(THUMB_SIDE, THUMB_SIDE)
        write(thumb, thumbFile(name), 80)
      } catch (e: IOException) {
        delete(listOf(name))
        throw PhotoImportException("No se pudo guardar la foto", e)
      }
      name
    } catch (e: PhotoImportException) {
      throw e
    } catch (e: IOException) {
      throw PhotoImportException("No se pudo leer la foto", e)
    } catch (e: OutOfMemoryError) {
      throw PhotoImportException("La foto es demasiado grande", e)
    } catch (e: RuntimeException) {
      throw PhotoImportException("No se pudo procesar la foto", e)
    }
  }

  private fun rotateAndShrink(source: Bitmap, degrees: Int): Bitmap {
    val rotatedW = if (degrees % 180 == 0) source.width else source.height
    val rotatedH = if (degrees % 180 == 0) source.height else source.width
    val (w, h) = scaledSize(rotatedW, rotatedH, PHOTO_MAX_SIDE)
    val scale = w.toFloat() / rotatedW
    if (degrees == 0 && scale >= 1f) return source
    val matrix = Matrix().apply {
      postRotate(degrees.toFloat())
      postScale(scale, scale)
    }
    return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
  }

  private fun write(bitmap: Bitmap, target: File, quality: Int) {
    target.outputStream().use { if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it)) throw IOException("compress") }
  }

  /** Solo el nombre de archivo: una columna nunca debe sacar la ruta de [dir]. */
  fun file(name: String): File = File(dir, File(name).name)

  fun thumbFile(name: String): File = File(dir, thumbName(File(name).name))

  /** Borra las fotos y sus miniaturas. */
  override fun delete(names: Collection<String>) {
    names.forEach { name ->
      file(name).delete()
      thumbFile(name).delete()
    }
  }

  /**
   * Borra los archivos de [dir] que no referencia ningún café ([referenced]: nombres principales) y que tienen
   * más de [olderThanMs]: cubre la muerte del proceso a mitad de una edición (D26).
   */
  fun sweepOrphans(referenced: Set<String>, olderThanMs: Long, now: Long = System.currentTimeMillis()) {
    val keep = referenced.flatMap { listOf(it, thumbName(it)) }.toSet()
    dir.listFiles()?.forEach { file ->
      if (file.isFile && file.name !in keep && now - file.lastModified() > olderThanMs) file.delete()
    }
  }

  /** Un archivo nuevo de `cacheDir/camera/` para `TakePicture`; vacía las capturas anteriores. */
  fun newCameraTarget(): File {
    val camera = File(cacheDir, "camera").apply { mkdirs() }
    camera.listFiles()?.forEach { it.delete() }
    return File(camera, "capture-${System.nanoTime()}.jpg")
  }

  private fun thumbName(name: String) = name.removeSuffix(".jpg") + "_thumb.jpg"
}
