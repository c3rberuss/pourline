package dev.josueamaya.pourline.share

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import dev.josueamaya.pourline.coffee.photo.scaledSize
import dev.josueamaya.pourline.protocol.WeightUnit
import dev.josueamaya.pourline.recipe.Recipe
import dev.josueamaya.pourline.recipe.share.DecodeResult
import dev.josueamaya.pourline.recipe.share.QrReadResult
import dev.josueamaya.pourline.recipe.share.RecipeShareCodec
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * La tarjeta de la receta de peor caso (20 pasos, nombre de 60 caracteres, molienda en 2 líneas y notas poco comprimibles
 * hasta casi llenar `MAX_URI_LENGTH` sin recorte), capturada con [ShareCaptureHost] a 1080 px de ancho y de alto lo que pida
 * su contenido, se lee con ML Kit de tres maneras (enmienda 1 de SPEC-009, D34 y D46): tal cual, reducida al 50 % y en JPEG
 * 70, y como la lee la importación (reducida a [QR_IMAGE_MAX_SIDE] px de lado mayor). Necesita el modelo de Play Services:
 * `emulator-5560` (AVD `SG_API36_play`).
 *
 * [cardSweep] es la medición que fija `MAX_URI_LENGTH`; se salta si el runner no recibe `-e lengths <lista>` (`-e samples <n>`
 * da las muestras por longitud, 20 por defecto).
 */
class RecipeQrLegibilityTest {
  @get:Rule
  val rule = createComposeRule()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private lateinit var reader: MlKitQrImageReader

  /** Una sola composición por test: las tarjetas se capturan una tras otra en ella. */
  private val capturer by lazy { CardCapturer(rule).also { it.start() } }

  @Before
  fun setUp() {
    reader = MlKitQrImageReader(context)
    awaitQrModel(reader)
  }

  @After
  fun tearDown() = reader.close()

  private fun readsBack(bitmap: Bitmap, uri: String): Boolean = runBlocking { reader.readBitmap(bitmap) }.contains(uri)

  private fun halvedInJpeg70(card: Bitmap): Bitmap {
    val half = Bitmap.createScaledBitmap(card, card.width / 2, card.height / 2, true)
    val jpeg = ByteArrayOutputStream().also { half.compress(Bitmap.CompressFormat.JPEG, 70, it) }.toByteArray()
    return BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
  }

  /** Como `MlKitQrImageReader.read`: lado mayor limitado a [QR_IMAGE_MAX_SIDE] con el mismo escalado. */
  private fun scaledLikeTheImport(card: Bitmap): Bitmap {
    val (width, height) = scaledSize(card.width, card.height, QR_IMAGE_MAX_SIDE)
    return if (width == card.width && height == card.height) card else Bitmap.createScaledBitmap(card, width, height, true)
  }

  private fun cardRequest(uri: String, recipe: Recipe, grindText: String?) =
    ShareRequest.Recipe(RecipeShareData(recipe, grindText, WeightUnit.GRAMS, qr = qrMatrix(uri)))

  @Test
  fun theWorstCaseCardIsReadableAsIsAndAfterHalvingAndJpeg70() {
    val worst = worstCaseRecipe(RecipeShareCodec.MAX_URI_LENGTH)
    val card = capturer.capture(cardRequest(worst.uri, worst.recipe, worst.grindText))
    assertEquals(1080, card.width)
    assertTrue("alto de ${card.height} px", card.height > 1350)
    Log.i(TAG, "tarjeta de peor caso: 1080 × ${card.height}, URI de ${worst.uri.length} caracteres")

    assertTrue("tal cual", readsBack(card, uri = worst.uri))
    assertTrue("reducida al 50 % y en JPEG 70", readsBack(halvedInJpeg70(card), worst.uri))
    // La ruta real de la importación: el PNG guardado y leído por `read(uri)`, que reduce a QR_IMAGE_MAX_SIDE.
    val file = File(context.cacheDir, "worst-case-card.png")
    file.outputStream().use { card.compress(Bitmap.CompressFormat.PNG, 100, it) }
    try {
      val read = runBlocking { reader.read(Uri.fromFile(file)) }
      assertTrue("por la ruta de importación: $read", read is QrReadResult.Found && read.text.contains(worst.uri))
    } finally {
      file.delete()
    }

    val decoded = RecipeShareCodec.decode(worst.uri) as DecodeResult.Ok
    assertEquals(worst.recipe.name, decoded.shared.recipe.name)
    assertEquals(worst.recipe.stages.size, decoded.shared.recipe.stages.size)
    assertEquals(worst.recipe.notes, decoded.shared.recipe.notes)
    assertTrue(decoded.shared.trimmed.isEmpty())
  }

  @Test
  fun maxUriLengthIsLegibleOnTheCard() {
    val results = measure(RecipeShareCodec.MAX_URI_LENGTH, samples = 20, firstSeed = 20_000, saveFailures = true)
    Log.i(TAG, "maxUriLengthIsLegibleOnTheCard ${RecipeShareCodec.MAX_URI_LENGTH}: $results")
    assertTrue("tal cual: ${results.asIs}/${results.samples}", results.asIs == results.samples)
    assertTrue("reducida y JPEG 70: ${results.reduced}/${results.samples}", results.reduced == results.samples)
    assertTrue("por la importación: ${results.import}/${results.samples}", results.import == results.samples)
  }

  @Test
  fun cardSweep() {
    val lengths = InstrumentationRegistry.getArguments().getString("lengths")?.split(',')?.map { it.trim().toInt() }
    assumeTrue("sin -e lengths no se mide", lengths != null)
    val samples = InstrumentationRegistry.getArguments().getString("samples")?.toInt() ?: 20
    val firstSeed = InstrumentationRegistry.getArguments().getString("seed")?.toInt() ?: 10_000
    for (length in lengths!!) {
      val r = measure(length, samples, firstSeed, saveFailures = true)
      Log.i(TAG, "card $length v${r.version} asIs ${r.asIs}/$samples reduced ${r.reduced}/$samples import ${r.import}/$samples qrTop=${r.qrTop} height=${r.height}")
    }
  }

  private class Measured(val version: Int, val samples: Int, val asIs: Int, val reduced: Int, val import: Int, val qrTop: Int, val height: Int) {
    override fun toString() = "v$version asIs $asIs reduced $reduced import $import height $height"
  }

  /** [samples] tarjetas de peor caso con un QR de un URI aleatorio de [length] caracteres, leídas de las tres maneras. */
  private fun measure(length: Int, samples: Int, firstSeed: Int, saveFailures: Boolean): Measured {
    val worst = worstCaseRecipe(RecipeShareCodec.MAX_URI_LENGTH)
    val outDir = File(context.getExternalFilesDir(null), "qr-legibility-card").apply { mkdirs() }
    var asIs = 0
    var reduced = 0
    var imported = 0
    var version = 0
    var qrTop = 0
    var height = 0
    for (i in 0 until samples) {
      val uri = uriOfLength(length, firstSeed + i.toLong())
      version = qrVersion(uri)
      val card = capturer.capture(cardRequest(uri, worst.recipe, worst.grindText))
      height = card.height
      qrTop = findQrBox(card, 6).y
      val a = readsBack(card, uri)
      val r = readsBack(halvedInJpeg70(card), uri)
      val m = readsBack(scaledLikeTheImport(card), uri)
      if (a) asIs++
      if (r) reduced++
      if (m) imported++
      if (saveFailures && !(a && r && m)) {
        File(outDir, "card-len$length-seed${firstSeed + i}-${if (a) "A" else "a"}${if (r) "R" else "r"}${if (m) "I" else "i"}.png").outputStream().use { card.compress(Bitmap.CompressFormat.PNG, 100, it) }
      }
    }
    return Measured(version, samples, asIs, reduced, imported, qrTop, height)
  }

  private companion object {
    const val TAG = "QrLegibility"
  }
}
