package dev.josueamaya.pourline

import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
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
import dev.josueamaya.pourline.ui.session.SessionDriver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * `ACTION_VIEW` y `ACTION_SEND` (F8, D28): se monta `TestApp(env)` y se le pasan al [RecipeIntentHandler] `Intent` reales.
 * Lanzar la `MainActivity` real usaría el `history.db` de verdad; su pegamento (`singleTask`, `onNewIntent`, recreación) se
 * prueba con `ManifestPermissionsTest` y por adb. Los casos con una imagen real necesitan el modelo: `emulator-5560`.
 */
class RecipeIntentImportTest {
  @get:Rule
  val rule = createAndroidComposeRule<ComponentActivity>()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private lateinit var env: TestAppDependencies
  private lateinit var handler: RecipeIntentHandler
  private val scope = CoroutineScope(SupervisorJob())
  private var captureRequest by mutableStateOf<ShareRequest?>(null)
  private var captured: ImageBitmap? = null
  private val tempFiles = mutableListOf<File>()

  @Before
  fun setUp() {
    env = TestAppDependencies(context)
    handler = RecipeIntentHandler(
      env.dependencies.recipeImports,
      env.dependencies.qrImageReader,
      { env.scaleSession.ui.value.hasSession },
      scope,
    )
  }

  @After
  fun tearDown() {
    scope.cancel()
    env.close()
    tempFiles.forEach { it.delete() }
  }

  private fun show() {
    env.skipOnboarding(ExperienceMode.BASIC)
    rule.setContent {
      Box {
        ShareCaptureHost(captureRequest, onCaptured = { captured = it }, onFailed = {})
        TestApp(env)
      }
    }
    rule.waitUntil(5_000) { exists("prepare_start") }
  }

  private fun exists(tag: String) = rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

  private fun waitFor(tag: String) = rule.waitUntil(10_000) { exists(tag) }

  private fun showsText(res: Int) = rule.onAllNodes(hasText(context.getString(res))).fetchSemanticsNodes().isNotEmpty()

  private fun waitForMessage(res: Int) = rule.waitUntil(10_000) { showsText(res) }

  private fun recipe(name: String = "Receta compartida") = Recipe(
    name = name,
    doseG = 15.0,
    stages = listOf(RecipeStage("Bloom", 0, 40.0, 30_000, StageType.POUR), RecipeStage("Segundo", 45_000, 250.0, 60_000, StageType.POUR)),
  )

  private fun uri(name: String = "Receta compartida") = RecipeShareCodec.encode(recipe(name), grinder = null)!!.uri

  private fun view(text: String?) = Intent(Intent.ACTION_VIEW, text?.let { Uri.parse(it) }).addCategory(Intent.CATEGORY_BROWSABLE)

  private fun send(stream: Uri?, withClip: Boolean = false) = Intent(Intent.ACTION_SEND).apply {
    type = "image/png"
    if (stream != null) {
      if (withClip) clipData = ClipData.newRawUri(null, stream) else putExtra(Intent.EXTRA_STREAM, stream)
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
  }

  private fun runHandler(intent: Intent): Boolean {
    var result = false
    rule.runOnUiThread { result = handler.handle(intent) }
    return result
  }

  private fun savedNames() = runBlocking { env.recipes.all() }.filter { it.builtInKey == null }.map { it.name }

  private fun useRealReader() {
    val reader = MlKitQrImageReader(context)
    awaitQrModel(reader)
    env.qrImageReader = reader
  }

  private fun cardPng(name: String = "Receta compartida"): Uri {
    captureRequest = ShareRequest.Recipe(RecipeShareData(recipe(name), null, WeightUnit.GRAMS, qrMatrix(uri(name))))
    rule.waitUntil(10_000) { captured != null }
    val images = ShareImages(context)
    return images.uri(runBlocking { images.write(captured!!, "intent-card-test.png") }.also { tempFiles += it })
  }

  private fun pngOf(bitmap: Bitmap, name: String): Uri {
    val images = ShareImages(context)
    return images.uri(runBlocking { images.write(bitmap.asImageBitmap(), name) }.also { tempFiles += it })
  }

  private fun blank() = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888).also { Canvas(it).drawColor(Color.WHITE) }

  @Test
  fun aViewIntentOpensThePreviewAndSavingAddsTheRecipe() {
    show()

    assertTrue(runHandler(view(uri())))

    waitFor("recipe_import_save")
    rule.onNodeWithTag("recipe_import_save").performScrollTo().performClick()
    waitFor("recipes_add")
    waitForMessage(R.string.recipe_import_added)
    assertEquals(listOf("Receta compartida"), savedNames())
  }

  @Test
  fun aSendIntentWithAGeneratedPngOpensThePreviewAndSavingAddsTheRecipe() {
    useRealReader()
    show()

    assertTrue(runHandler(send(cardPng())))

    waitFor("recipe_import_save")
    rule.onNodeWithTag("recipe_import_save").performScrollTo().performClick()
    waitFor("recipes_add")
    assertEquals(listOf("Receta compartida"), savedNames())
  }

  @Test
  fun aSendIntentCarryingTheImageInClipDataIsRead() {
    useRealReader()
    show()

    assertTrue(runHandler(send(cardPng(), withClip = true)))

    waitFor("recipe_import_save")
  }

  @Test
  fun aSendIntentWithAnImageWithoutQrShowsNoRecipeFound() {
    useRealReader()
    show()

    runHandler(send(pngOf(blank(), "blank-intent-test.png")))

    waitForMessage(R.string.recipe_import_no_recipe)
    assertFalse(exists("recipe_import_save"))
  }

  @Test
  fun aSendIntentWithAForeignQrShowsNoRecipeFound() {
    useRealReader()
    show()
    val bitmap = blank().let { Bitmap.createBitmap(1080, 1350, Bitmap.Config.ARGB_8888) }
    Canvas(bitmap).apply { drawColor(Color.WHITE); drawQr(this, qrMatrix("https://example.com/x"), 324, 459, 432) }

    runHandler(send(pngOf(bitmap, "foreign-intent-test.png")))

    waitForMessage(R.string.recipe_import_no_recipe)
    assertFalse(exists("recipe_import_save"))
  }

  @Test
  fun aSendIntentWithoutStreamShowsNoRecipeFound() {
    show()

    assertTrue(runHandler(send(null)))

    waitForMessage(R.string.recipe_import_no_recipe)
  }

  @Test
  fun aSendIntentWhileTheReaderIsNotReadyShowsThePreparingMessage() {
    env.qrImageReader = { QrReadResult.ModelNotReady }
    show()

    runHandler(send(Uri.parse("content://dev.josueamaya.pourline.fileprovider/shared/none.png")))

    waitForMessage(R.string.recipe_import_reader_preparing)
    assertFalse(exists("recipe_import_save"))
  }

  @Test
  fun aViewIntentWithoutPayloadShowsNotPourline() {
    show()

    assertTrue(runHandler(view("pourline://recipe")))

    waitForMessage(R.string.recipe_import_not_pourline)
    assertFalse(exists("recipe_import_save"))
  }

  @Test
  fun withASessionTheIntentDoesNotNavigateAndThePreviewOpensWhenItEnds() {
    show()
    val driver = SessionDriver(rule, env)
    driver.connect()
    driver.selectRecipe()
    driver.start()
    rule.runOnUiThread { env.scaleSession.startScalelessSession(dev.josueamaya.pourline.session.BrewPlan(doseG = 15.0, ratio = 16.0)) }
    driver.waitFor("scaleless_ready")

    runHandler(view(uri()))

    waitForMessage(R.string.recipe_import_deferred)
    assertTrue("la sesión sigue en pantalla", exists("scaleless_ready"))
    assertFalse(exists("recipe_import_save"))

    rule.runOnUiThread { env.scaleSession.discardSession() }

    waitFor("recipe_import_save")
  }

  @Test
  fun aSecondIntentDuringTheSessionReplacesThePendingOne() {
    show()
    rule.runOnUiThread { env.scaleSession.startScalelessSession(dev.josueamaya.pourline.session.BrewPlan(doseG = 15.0, ratio = 16.0)) }

    runHandler(view(uri("Primera")))
    runHandler(view(uri("Segunda")))
    assertEquals(uri("Segunda"), env.dependencies.recipeImports.pending.value)

    rule.runOnUiThread { env.scaleSession.discardSession() }
    waitFor("recipe_import_save")
    rule.onNodeWithTag("recipe_import_save").performScrollTo().performClick()
    waitFor("recipes_add")
    assertEquals(listOf("Segunda"), savedNames())
  }

  @Test
  fun otherIntentsAreIgnored() {
    show()

    assertFalse(runHandler(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)))
    assertFalse(runHandler(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))))
    assertFalse(runHandler(Intent(Intent.ACTION_SEND).setType("text/plain")))
    rule.waitForIdle()
    assertFalse(exists("recipe_import_save"))
    assertEquals(null, env.dependencies.recipeImports.pending.value)
  }
}
