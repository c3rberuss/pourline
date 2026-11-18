package dev.josueamaya.pourline.share

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import dev.josueamaya.pourline.history.BrewMethod
import dev.josueamaya.pourline.protocol.WeightUnit
import dev.josueamaya.pourline.recipe.Recipe
import dev.josueamaya.pourline.recipe.RecipeStage
import dev.josueamaya.pourline.recipe.StageType
import dev.josueamaya.pourline.recipe.share.DecodeResult
import dev.josueamaya.pourline.recipe.share.RecipeShareCodec
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.Random

/**
 * La tarjeta de la receta de peor caso (20 pasos, nombre de 60 caracteres y notas poco comprimibles hasta casi llenar
 * `MAX_URI_LENGTH` sin recorte), capturada con [ShareCaptureHost] a 1080 × 1920 px, se lee con ML Kit tal cual y tras
 * reducirla al 50 % y recomprimirla en JPEG 70 (§6 del plan, F4). Necesita el modelo de Play Services: `emulator-5560`.
 */
class RecipeQrLegibilityTest {
  @get:Rule
  val rule = createComposeRule()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private lateinit var reader: MlKitQrImageReader
  private var captured: ImageBitmap? = null
  private var failed = false

  @Before
  fun setUp() {
    reader = MlKitQrImageReader(context)
    awaitQrModel(reader)
  }

  @After
  fun tearDown() = reader.close()

  private fun stages(): List<RecipeStage> {
    var startMs = 0L
    var water = 0.0
    return List(20) { i ->
      val pour = i % 2 == 0
      if (pour) water += 10.0 + i % 4
      RecipeStage(null, startMs, water, durationMs = (5L + i % 7 * 3) * 1_000, type = if (pour) StageType.POUR else StageType.WAIT)
        .also { startMs += it.durationMs }
    }
  }

  private fun randomText(length: Int): String {
    val letters = ('a'..'z') + ('A'..'Z') + ('0'..'9')
    val random = Random(7)
    return String(CharArray(length) { letters[random.nextInt(letters.size)] })
  }

  private fun recipe(notesLength: Int) = Recipe(
    id = 1,
    name = randomText(60).replaceFirstChar { 'R' },
    method = BrewMethod.V60,
    waterTempC = 93.0,
    notes = if (notesLength > 0) randomText(notesLength) else null,
    doseG = 20.0,
    stages = stages(),
  )

  /** La receta con las notas más largas que caben sin recorte y el URI resultante, entre `MAX_URI_LENGTH − 8` y `MAX_URI_LENGTH`. */
  private fun worstCase(): Pair<Recipe, String> {
    fun fit(length: Int): String? = RecipeShareCodec.encode(recipe(length), grinder = null)?.takeIf { it.trimmed.isEmpty() }?.uri
    var low = 0
    var high = 400
    while (low < high) {
      val mid = (low + high + 1) / 2
      if (fit(mid) != null) low = mid else high = mid - 1
    }
    val best = (maxOf(0, low - 4)..low + 4).mapNotNull { n -> fit(n)?.let { recipe(n) to it } }.maxBy { it.second.length }
    val max = RecipeShareCodec.MAX_URI_LENGTH
    assertTrue("URI de ${best.second.length} caracteres", best.second.length in max - 8..max)
    return best
  }

  private fun readsBack(bitmap: Bitmap, uri: String): Boolean = runBlocking { reader.readBitmap(bitmap) }.contains(uri)

  @Test
  fun theWorstCaseCardIsReadableAsIsAndAfterHalvingAndJpeg70() {
    val (recipe, uri) = worstCase()
    rule.setContent {
      ShareCaptureHost(
        ShareRequest.Recipe(RecipeShareData(recipe, grindText = null, unit = WeightUnit.GRAMS, qr = qrMatrix(uri))),
        onCaptured = { captured = it },
        onFailed = { failed = true },
      )
    }
    rule.waitUntil(10_000) { captured != null || failed }
    assertTrue("la captura falló", !failed)
    val card = captured!!.asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false)
    assertEquals(1080, card.width)
    assertEquals(1920, card.height)

    assertTrue("tal cual", readsBack(card, uri))
    val half = Bitmap.createScaledBitmap(card, card.width / 2, card.height / 2, true)
    val jpeg = ByteArrayOutputStream().also { half.compress(Bitmap.CompressFormat.JPEG, 70, it) }.toByteArray()
    val reduced = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
    assertTrue("reducida al 50 % y en JPEG 70", readsBack(reduced, uri))

    val decoded = RecipeShareCodec.decode(uri) as DecodeResult.Ok
    assertEquals(recipe.name, decoded.shared.recipe.name)
    assertEquals(recipe.stages.size, decoded.shared.recipe.stages.size)
    assertEquals(recipe.notes, decoded.shared.recipe.notes)
    assertTrue(decoded.shared.trimmed.isEmpty())
  }
}
