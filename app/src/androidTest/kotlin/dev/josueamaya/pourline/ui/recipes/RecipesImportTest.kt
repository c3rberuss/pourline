package dev.josueamaya.pourline.ui.recipes

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.core.app.ActivityOptionsCompat
import androidx.test.platform.app.InstrumentationRegistry
import dev.josueamaya.pourline.R
import dev.josueamaya.pourline.Tab
import dev.josueamaya.pourline.protocol.WeightUnit
import dev.josueamaya.pourline.recipe.Recipe
import dev.josueamaya.pourline.recipe.RecipeStage
import dev.josueamaya.pourline.recipe.StageType
import dev.josueamaya.pourline.recipe.share.QrReadResult
import dev.josueamaya.pourline.recipe.share.RecipeShareCodec
import dev.josueamaya.pourline.settings.ExperienceMode
import dev.josueamaya.pourline.share.MlKitQrImageReader
import dev.josueamaya.pourline.share.RecipeShareData
import dev.josueamaya.pourline.share.ShareCaptureHost
import dev.josueamaya.pourline.share.ShareImages
import dev.josueamaya.pourline.share.ShareRequest
import dev.josueamaya.pourline.share.awaitQrModel
import dev.josueamaya.pourline.share.drawQr
import dev.josueamaya.pourline.share.qrMatrix
import dev.josueamaya.pourline.ui.TestApp
import dev.josueamaya.pourline.ui.TestAppDependencies
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * "Importar" en Recetas (F7): el menú, el escáner (falso) y la galería (un `ActivityResultRegistry` falso que devuelve el
 * `Uri` del test). Los casos con una imagen real usan el lector de Play Services y su modelo: `emulator-5560`.
 */
class RecipesImportTest {
  @get:Rule
  val rule = createAndroidComposeRule<ComponentActivity>()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private lateinit var env: TestAppDependencies
  private var scanResult: ScanResult = ScanResult.Cancelled
  private val scanner = QrScanner { scanResult }
  private var pickerResult: Uri? = null
  private val registryOwner = object : ActivityResultRegistryOwner {
    override val activityResultRegistry = object : ActivityResultRegistry() {
      override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
        @Suppress("UNCHECKED_CAST")
        dispatchResult(requestCode, pickerResult as O)
      }
    }
  }
  private var captureRequest by mutableStateOf<ShareRequest?>(null)
  private var captured: ImageBitmap? = null
  private val tempFiles = mutableListOf<File>()

  @Before
  fun setUp() {
    env = TestAppDependencies(context)
  }

  @After
  fun tearDown() {
    env.close()
    tempFiles.forEach { it.delete() }
  }

  private fun show(mode: ExperienceMode = ExperienceMode.BASIC) {
    env.skipOnboarding(mode)
    rule.setContent {
      CompositionLocalProvider(LocalQrScanner provides scanner, LocalActivityResultRegistryOwner provides registryOwner) {
        Box {
          ShareCaptureHost(captureRequest, onCaptured = { captured = it }, onFailed = {})
          TestApp(env)
        }
      }
    }
    rule.waitUntil(5_000) { exists("prepare_start") }
  }

  private fun exists(tag: String) = rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

  private fun waitFor(tag: String) = rule.waitUntil(5_000) { exists(tag) }

  private fun showsText(text: String) = rule.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()

  private fun openImportMenu() {
    rule.onNodeWithTag(Tab.RECIPES.testTag).performClick()
    waitFor("recipes_import")
    rule.onNodeWithTag("recipes_import").performClick()
  }

  private fun scan() {
    openImportMenu()
    rule.onNodeWithTag("recipes_import_scan").performClick()
  }

  private fun pickImage() {
    openImportMenu()
    rule.onNodeWithTag("recipes_import_image").performClick()
  }

  private fun stages() = listOf(
    RecipeStage("Bloom", 0, 40.0, 30_000, StageType.POUR),
    RecipeStage("Segundo", 45_000, 250.0, 60_000, StageType.POUR),
  )

  private fun recipe() = Recipe(name = "Receta de la galería", doseG = 15.0, stages = stages())

  private fun uri(): String = RecipeShareCodec.encode(recipe(), grinder = null)!!.uri

  private fun waitForMessage(res: Int) = rule.waitUntil(5_000) { showsText(context.getString(res)) }

  private fun assertNoPreviewNorMessage() {
    rule.waitForIdle()
    Thread.sleep(1_000)
    rule.waitForIdle()
    assertFalse(exists("recipe_import_save"))
    listOf(
      R.string.recipe_import_not_pourline,
      R.string.recipe_import_no_recipe,
      R.string.recipe_import_scanner_unavailable,
      R.string.recipe_import_reader_preparing,
    ).forEach { assertFalse(showsText(context.getString(it))) }
  }

  /** Un PNG con la tarjeta real de [recipe()] servido por el `FileProvider`. */
  private fun recipeCardUri(): Uri {
    val data = RecipeShareData(recipe(), grindText = null, unit = WeightUnit.GRAMS, qr = qrMatrix(uri()))
    captureRequest = ShareRequest.Recipe(data)
    rule.waitUntil(10_000) { captured != null }
    val images = ShareImages(context)
    val file = runBlocking { images.write(captured!!, "recipe-import-test.png") }
    tempFiles += file
    return images.uri(file)
  }

  private fun pngOf(bitmap: Bitmap, name: String): Uri {
    val images = ShareImages(context)
    val file = runBlocking { images.write(bitmap.asImageBitmap(), name) }
    tempFiles += file
    return images.uri(file)
  }

  private fun blank(width: Int, height: Int, config: Bitmap.Config = Bitmap.Config.ARGB_8888) =
    Bitmap.createBitmap(width, height, config).also { Canvas(it).drawColor(Color.WHITE) }

  private fun withQr(text: String, width: Int, height: Int, qrSize: Int, config: Bitmap.Config = Bitmap.Config.ARGB_8888): Bitmap =
    blank(width, height, config).also { drawQr(Canvas(it), qrMatrix(text), (width - qrSize) / 2, (height - qrSize) / 2, qrSize) }

  /** Con una imagen real, el modelo de Play Services tiene que estar descargado: si no, la lectura no encontraría nada. */
  private fun useRealReader() {
    val reader = MlKitQrImageReader(context)
    awaitQrModel(reader)
    env.qrImageReader = reader
  }

  @Test
  fun theImportMenuOffersScanAndImageInBasic() = assertMenu(ExperienceMode.BASIC)

  @Test
  fun theImportMenuOffersScanAndImageInExpert() = assertMenu(ExperienceMode.EXPERT)

  private fun assertMenu(mode: ExperienceMode) {
    show(mode)
    openImportMenu()
    rule.onNodeWithTag("recipes_import_scan").assertIsDisplayed()
    rule.onNodeWithTag("recipes_import_image").assertIsDisplayed()
  }

  @Test
  fun aScannedRecipeOpensThePreview() {
    show()
    scanResult = ScanResult.Scanned(uri())

    scan()

    waitFor("recipe_import_save")
    rule.onNodeWithTag("recipe_import_name").assertIsDisplayed()
  }

  @Test
  fun aForeignQrShowsNotPourline() {
    show()
    scanResult = ScanResult.Scanned("https://example.com")

    scan()

    waitForMessage(R.string.recipe_import_not_pourline)
    assertFalse(exists("recipe_import_save"))
  }

  @Test
  fun cancellingTheScannerDoesNothing() {
    show()
    scanResult = ScanResult.Cancelled

    scan()

    assertNoPreviewNorMessage()
  }

  @Test
  fun anUnavailableScannerSuggestsTheImage() {
    show()
    scanResult = ScanResult.Unavailable

    scan()

    waitForMessage(R.string.recipe_import_scanner_unavailable)
    assertFalse(exists("recipe_import_save"))
  }

  @Test
  fun aGalleryImageWithARecipeOpensThePreview() {
    useRealReader()
    show()
    pickerResult = recipeCardUri()

    pickImage()

    waitFor("recipe_import_save")
    rule.onNodeWithTag("recipe_import_name").assertIsDisplayed()
  }

  @Test
  fun aGalleryImageWithoutQrShowsNoRecipeFound() {
    useRealReader()
    show()
    pickerResult = pngOf(blank(800, 600), "blank-test.png")

    pickImage()

    waitForMessage(R.string.recipe_import_no_recipe)
    assertFalse(exists("recipe_import_save"))
  }

  @Test
  fun aGalleryImageWithAForeignQrShowsNoRecipeFound() {
    useRealReader()
    show()
    pickerResult = pngOf(withQr("https://example.com/not-a-recipe", 1080, 1350, 432), "foreign-test.png")

    pickImage()

    waitForMessage(R.string.recipe_import_no_recipe)
    assertFalse(exists("recipe_import_save"))
  }

  @Test
  fun cancellingThePickerDoesNothing() {
    show()
    pickerResult = null

    pickImage()

    assertNoPreviewNorMessage()
  }

  @Test
  fun aHugeImageIsReducedAndStillRead() {
    useRealReader()
    show()
    // 6 000 × 6 000 px con el QR a 2 400 px: se reduce a 2 048 de lado mayor y sigue legible. RGB_565 para no agotar el heap.
    pickerResult = pngOf(withQr(uri(), 6_000, 6_000, 2_400, Bitmap.Config.RGB_565), "huge-test.png")

    pickImage()

    rule.waitUntil(30_000) { exists("recipe_import_save") || showsText(context.getString(R.string.recipe_import_no_recipe)) }
    assertTrue("la imagen enorme debía leerse", exists("recipe_import_save"))
  }

  @Test
  fun anUnreadableFileShowsNoRecipeFound() {
    env.qrImageReader = MlKitQrImageReader(context)
    show()
    val images = ShareImages(context)
    val file = File(context.cacheDir, "shared").apply { mkdirs() }.resolve("garbage-test.png")
    file.writeBytes(ByteArray(2_000) { (it * 31).toByte() })
    tempFiles += file
    pickerResult = images.uri(file)

    pickImage()

    waitForMessage(R.string.recipe_import_no_recipe)
    assertFalse(exists("recipe_import_save"))
  }

  @Test
  fun aGalleryImageWhileTheReaderIsNotReadyShowsThePreparingMessage() {
    env.qrImageReader = { QrReadResult.ModelNotReady }
    show()
    pickerResult = Uri.parse("content://dev.josueamaya.pourline.fileprovider/shared/none.png")

    pickImage()

    waitForMessage(R.string.recipe_import_reader_preparing)
    assertFalse(exists("recipe_import_save"))
  }
}
