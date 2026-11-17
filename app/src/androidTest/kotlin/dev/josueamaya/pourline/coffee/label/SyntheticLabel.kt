package dev.josueamaya.pourline.coffee.label

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface
import android.media.ExifInterface
import java.io.File

/** Una etiqueta sintética de 1600 × 1200 con texto grande y pequeño, para probar el OCR sin fotos reales. */
object SyntheticLabel {
  const val WIDTH = 1600
  const val HEIGHT = 1200

  val lines = listOf(
    "ETIOPÍA GUJI" to 140f,
    "Café de Altura" to 70f,
    "Tostado 12/10/2026" to 60f,
    "Natural" to 60f,
    "1900 msnm" to 60f,
    "250 g" to 60f,
  )

  fun bitmap(): Bitmap {
    val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(Color.WHITE)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.BLACK
      typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }
    var y = 60f
    lines.forEach { (text, size) ->
      paint.textSize = size
      y += size
      canvas.drawText(text, 80f, y, paint)
      y += size * 0.6f
    }
    return bitmap
  }

  /** Escribe la etiqueta como JPEG. Con [sideways] los píxeles van girados 90° y el EXIF 6 la devuelve a su sitio. */
  fun writeJpeg(file: File, sideways: Boolean = false) {
    var bitmap = bitmap()
    if (sideways) {
      bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(-90f) }, true)
    }
    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
    if (sideways) {
      ExifInterface(file.path).apply {
        setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
        saveAttributes()
      }
    }
  }
}
