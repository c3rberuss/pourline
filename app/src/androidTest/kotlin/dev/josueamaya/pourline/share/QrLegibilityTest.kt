package dev.josueamaya.pourline.share

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
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
import java.util.Random

/**
 * Mide hasta qué versión de QR se lee con ML Kit el QR de la tarjeta de receta (432 px, en su sitio de un lienzo de
 * 1080 × 1350), tal cual y tras reducir la imagen al 50 % y recomprimirla en JPEG calidad 70 (D8 de SPEC-009).
 * [sweep] no afirma nada: escribe en logcat (`QrLegibility`) la tabla que fija `MAX_URI_LENGTH`.
 */
class QrLegibilityTest {
  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private lateinit var reader: MlKitQrImageReader

  @Before
  fun setUp() {
    reader = MlKitQrImageReader(context)
    awaitModel()
  }

  /** El modelo de Play Services se descarga bajo demanda: se espera a que lea un QR antes de medir. */
  private fun awaitModel() {
    val uri = uriOfLength(300, 0)
    val deadline = System.nanoTime() + 240_000_000_000L
    while (true) {
      try {
        if (runBlocking { reader.readBitmap(canvasWithQr(uri)) }.contains(uri)) return
      } catch (e: Exception) {
        Log.i(TAG, "modelo no listo: ${e.message}")
      }
      check(System.nanoTime() < deadline) { "el modelo de lectura de QR no se descargó en 240 s" }
      Thread.sleep(3_000)
    }
  }

  @After
  fun tearDown() = reader.close()

  private class Outcome(val version: Int, val asIs: Boolean, val reduced: Boolean)

  @Test
  fun sweep() {
    val outDir = File(context.getExternalFilesDir(null), "qr-legibility").apply { mkdirs() }
    val saved = mutableSetOf<Int>()
    Log.i(TAG, "length version asIs reduced (samples=$SAMPLES, qr=${QR_SIZE_PX}px)")
    for (length in 200..800 step 25) {
      val outcomes = (0 until SAMPLES).map { check(uriOfLength(length, it)) }
      val version = outcomes.first().version
      Log.i(TAG, "$length v$version ${outcomes.count { it.asIs }}/$SAMPLES ${outcomes.count { it.reduced }}/$SAMPLES")
      if (saved.add(version)) {
        File(outDir, "qr-v$version-len$length.png").outputStream().use { out ->
          canvasWithQr(uriOfLength(length, 0)).compress(Bitmap.CompressFormat.PNG, 100, out)
        }
      }
    }
  }

  @Test
  fun maxUriLengthIsLegible() {
    val outcomes = (0 until 20).map { check(uriOfLength(RecipeShareCodec.MAX_URI_LENGTH, 100 + it)) }
    Log.i(TAG, "MAX_URI_LENGTH=${RecipeShareCodec.MAX_URI_LENGTH} v${outcomes.first().version}")
    assertTrue("tal cual: ${outcomes.count { it.asIs }}/20", outcomes.all { it.asIs })
    assertTrue("reducido y en JPEG 70: ${outcomes.count { it.reduced }}/20", outcomes.all { it.reduced })
  }

  private fun check(uri: String): Outcome {
    val canvas = canvasWithQr(uri)
    val asIs = runBlocking { reader.readBitmap(canvas) }.contains(uri)
    val half = Bitmap.createScaledBitmap(canvas, canvas.width / 2, canvas.height / 2, true)
    val jpeg = ByteArrayOutputStream().also { half.compress(Bitmap.CompressFormat.JPEG, 70, it) }.toByteArray()
    val back = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
    val reduced = runBlocking { reader.readBitmap(back) }.contains(uri)
    return Outcome(qrVersion(uri), asIs, reduced)
  }

  private fun canvasWithQr(uri: String): Bitmap {
    val bitmap = Bitmap.createBitmap(1080, 1350, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(Color.WHITE)
    drawQr(canvas, qrMatrix(uri), 1080 - 48 - QR_SIZE_PX, QR_TOP_PX, QR_SIZE_PX)
    return bitmap
  }

  private fun uriOfLength(length: Int, seed: Int): String {
    val prefix = "pourline://recipe?v=1&d="
    val random = Random(length * 31L + seed)
    val body = CharArray(length - prefix.length) { ALPHABET[random.nextInt(ALPHABET.length)] }
    return prefix + String(body)
  }

  private companion object {
    const val TAG = "QrLegibility"
    const val SAMPLES = 5
    /** `-Pandroid.testInstrumentationRunnerArguments.qrSize=<px>` mide otro tamaño de caja (por defecto, 432 px). */
    val QR_SIZE_PX: Int = InstrumentationRegistry.getArguments().getString("qrSize")?.toInt() ?: 432
    const val QR_TOP_PX = 228
    const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
  }
}

/** Dibuja [matrix] con zona de silencio dentro de una caja de [sizePx], en píxeles enteros y negro sobre blanco. */
internal fun drawQr(canvas: Canvas, matrix: QrMatrix, left: Int, top: Int, sizePx: Int) {
  val cells = matrix.modules + 2 * QR_QUIET_ZONE
  canvas.drawRect(left.toFloat(), top.toFloat(), (left + sizePx).toFloat(), (top + sizePx).toFloat(), Paint().apply { color = Color.WHITE })
  val dark = Paint().apply { color = Color.BLACK }
  for (y in 0 until matrix.modules) for (x in 0 until matrix.modules) {
    if (!matrix[x, y]) continue
    canvas.drawRect(
      left + qrCellEdge(x + QR_QUIET_ZONE, cells, sizePx).toFloat(),
      top + qrCellEdge(y + QR_QUIET_ZONE, cells, sizePx).toFloat(),
      left + qrCellEdge(x + QR_QUIET_ZONE + 1, cells, sizePx).toFloat(),
      top + qrCellEdge(y + QR_QUIET_ZONE + 1, cells, sizePx).toFloat(),
      dark,
    )
  }
}
