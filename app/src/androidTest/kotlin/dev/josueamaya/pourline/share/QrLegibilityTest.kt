package dev.josueamaya.pourline.share

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import dev.josueamaya.pourline.recipe.share.RecipeShareCodec
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Mide hasta qué versión de QR se lee con ML Kit el QR de la tarjeta de receta, en el lienzo de 1080 × 1350 y con la caja
 * en su sitio, tal cual y tras reducir la imagen al 50 % y recomprimirla en JPEG calidad 70 (D8 de SPEC-009). [sweep] no
 * afirma nada: escribe en logcat (`QrLegibility`) la tabla que fija `MAX_URI_LENGTH`. Argumentos del runner
 * (`-e <nombre> <valor>`): `modulePx` (caja de `celdas × modulePx`, prioritario sobre `qrSize`), `qrSize` (caja fija, 432 px
 * por defecto), `from`, `to`, `step`, `samples` y `left` (x de la caja; por defecto `1080 − 48 − caja`).
 */
class QrLegibilityTest {
  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private val args = InstrumentationRegistry.getArguments()
  private lateinit var reader: MlKitQrImageReader

  @Before
  fun setUp() {
    reader = MlKitQrImageReader(context)
    awaitQrModel(reader)
  }

  @After
  fun tearDown() = reader.close()

  private class Outcome(val version: Int, val asIs: Boolean, val reduced: Boolean)

  private fun arg(name: String): Int? = args.getString(name)?.toInt()

  @Test
  fun sweep() {
    val modulePx = arg("modulePx")
    val qrSize = arg("qrSize") ?: 432
    val from = arg("from") ?: 200
    val to = arg("to") ?: 800
    val step = arg("step") ?: 25
    val samples = arg("samples") ?: 5
    val left = arg("left")
    val outDir = File(context.getExternalFilesDir(null), "qr-legibility").apply { mkdirs() }
    val saved = mutableSetOf<Int>()
    Log.i(TAG, "length version asIs reduced (samples=$samples, ${if (modulePx != null) "${modulePx}px per module" else "qr=${qrSize}px"}, left=${left ?: "default"})")
    for (length in from..to step step) {
      val outcomes = (0 until samples).map { check(uriOfLength(length, it.toLong()), modulePx, qrSize, left) }
      val version = outcomes.first().version
      Log.i(TAG, "$length v$version ${outcomes.count { it.asIs }}/$samples ${outcomes.count { it.reduced }}/$samples")
      if (saved.add(version)) {
        File(outDir, "qr-v$version-len$length.png").outputStream().use { out ->
          canvasWithQr(uriOfLength(length, 0), modulePx, qrSize, left).compress(Bitmap.CompressFormat.PNG, 100, out)
        }
      }
    }
  }

  @Test
  fun maxUriLengthIsLegible() {
    val outcomes = (0 until 20).map { check(uriOfLength(RecipeShareCodec.MAX_URI_LENGTH, 100L + it), MODULE_PX, 0, null) }
    Log.i(TAG, "MAX_URI_LENGTH=${RecipeShareCodec.MAX_URI_LENGTH} v${outcomes.first().version}")
    assertTrue("tal cual: ${outcomes.count { it.asIs }}/20", outcomes.all { it.asIs })
    assertTrue("reducido y en JPEG 70: ${outcomes.count { it.reduced }}/20", outcomes.all { it.reduced })
  }

  private fun check(uri: String, modulePx: Int?, qrSize: Int, left: Int?): Outcome {
    val canvas = canvasWithQr(uri, modulePx, qrSize, left)
    val asIs = runBlocking { reader.readBitmap(canvas) }.contains(uri)
    val half = Bitmap.createScaledBitmap(canvas, canvas.width / 2, canvas.height / 2, true)
    val jpeg = ByteArrayOutputStream().also { half.compress(Bitmap.CompressFormat.JPEG, 70, it) }.toByteArray()
    val back = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
    val reduced = runBlocking { reader.readBitmap(back) }.contains(uri)
    return Outcome(qrVersion(uri), asIs, reduced)
  }

  private fun canvasWithQr(uri: String, modulePx: Int?, qrSize: Int, left: Int?): Bitmap {
    val bitmap = Bitmap.createBitmap(1080, 1350, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(Color.WHITE)
    val matrix = qrMatrix(uri)
    val box = if (modulePx != null) matrix.cells * modulePx else qrSize
    drawQr(canvas, matrix, left ?: (1080 - 48 - box), QR_TOP_PX, box)
    return bitmap
  }

  private companion object {
    const val TAG = "QrLegibility"
    const val QR_TOP_PX = 228
    const val MODULE_PX = 6
  }
}
