package dev.dotscale.coffee.label

import android.media.ExifInterface
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import dev.dotscale.coffee.photo.CoffeePhotoStore
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.io.FileInputStream
import java.time.LocalDate

/**
 * Captura de los fixtures del OCR con las fotos reales (D31). Solo corre con `-e labelsCapture true` y se lanza
 * con `am instrument` (no con `connectedDebugAndroidTest`, que desinstala la app y borraría
 * `Android/data/dev.dotscale/`). Las fotos están en `getExternalFilesDir("labels")`, se importan como en la app
 * (reducidas y giradas según su EXIF) y pasan por ML Kit. Escribe un JSON por foto en `labels-out/` y, si junto
 * a las fotos hay un `expected.tsv`, el informe en `labels-report/report.txt`. Falla si no se cumplen los dos
 * objetivos, después de escribir los archivos.
 */
class LabelFixtureCaptureTest {
  private val context = InstrumentationRegistry.getInstrumentation().targetContext

  @Test
  fun captureLabels() {
    assumeTrue(InstrumentationRegistry.getArguments().getString("labelsCapture") == "true")
    val input = context.getExternalFilesDir("labels")!!
    val output = File(context.getExternalFilesDir(null), "labels-out").apply {
      deleteRecursively()
      mkdirs()
    }
    val reportDir = File(context.getExternalFilesDir(null), "labels-report").apply { mkdirs() }
    val work = File(context.cacheDir, "label-capture").apply {
      deleteRecursively()
      mkdirs()
    }
    val store = CoffeePhotoStore(File(work, "photos"), File(work, "cache"))
    val capturedOn = LocalDate.now()
    val ocr = MlKitLabelOcr()
    val photos = input.listFiles { f -> f.extension.lowercase() in setOf("jpg", "jpeg") }.orEmpty().sortedBy { it.name }
    assertTrue("No hay fotos en ${input.path}", photos.isNotEmpty())

    val parsed = linkedMapOf<String, LabelFields>()
    val orientations = mutableMapOf<String, String>()
    try {
      runBlocking {
        photos.forEach { photo ->
          val name = store.import { FileInputStream(photo) }
          val lines = ocr.read(store.file(name))
          val json = JSONObject()
            .put("file", photo.name)
            .put("capturedOn", capturedOn.toString())
            .put(
              "lines",
              JSONArray().also { array ->
                lines.forEach { array.put(JSONObject().put("text", it.text).put("heightPx", it.heightPx)) }
              },
            )
          File(output, photo.nameWithoutExtension + ".json").writeText(json.toString(2) + "\n")
          parsed[photo.name] = LabelParser.parse(lines, capturedOn)
          orientations[photo.name] = "EXIF " + ExifInterface(photo.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)
        }
      }
    } finally {
      ocr.close()
      work.deleteRecursively()
    }

    val expectedFile = File(input, "expected.tsv")
    if (!expectedFile.exists()) {
      Log.i(TAG, "Sin expected.tsv: solo se capturaron ${photos.size} etiquetas")
      return
    }
    val score = score(parseExpected(expectedFile.readText()), parsed)
    val report = score.toReport(details = orientations)
    File(reportDir, "report.txt").writeText(report)
    report.lines().forEach { Log.i(TAG, it) }
    assertTrue("No se cumplen los objetivos:\n$report", score.meetsTarget())
  }

  private companion object {
    const val TAG = "LabelReport"
  }
}
