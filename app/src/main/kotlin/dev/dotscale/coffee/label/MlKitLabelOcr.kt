package dev.dotscale.coffee.label

import android.graphics.BitmapFactory
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import java.io.IOException

/**
 * OCR con ML Kit (alfabeto latino, modelo incluido en el APK: funciona sin red). Lee la foto ya reducida y
 * girada por `CoffeePhotoStore` y devuelve una [OcrLine] por línea de texto, en el orden de lectura de ML Kit.
 */
class MlKitLabelOcr : LabelOcr, Closeable {
  private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

  override suspend fun read(photo: File): List<OcrLine> {
    val bitmap = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(photo.path) }
      ?: throw IOException("No se pudo leer la foto ${photo.name}")
    try {
      val text = recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()
      return text.textBlocks.flatMap { it.lines }.map { OcrLine(it.text, it.boundingBox?.height() ?: 0) }
    } finally {
      bitmap.recycle()
    }
  }

  override fun close() {
    recognizer.close()
  }
}
