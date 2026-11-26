package dev.josueamaya.pourline.ui.recipes

import android.content.ComponentName
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.platform.app.InstrumentationRegistry
import dev.josueamaya.pourline.R
import dev.josueamaya.pourline.grinder.FakeDefaultGrinderStore
import dev.josueamaya.pourline.grinder.GrindUnit
import dev.josueamaya.pourline.grinder.GrindUnitLevel
import dev.josueamaya.pourline.grinder.Grinder
import dev.josueamaya.pourline.grinder.GrinderRepository
import dev.josueamaya.pourline.grinder.ResourcesGrindTexts
import dev.josueamaya.pourline.grinder.addGrinder
import dev.josueamaya.pourline.grinder.grinderRepository
import dev.josueamaya.pourline.history.BrewMethod
import dev.josueamaya.pourline.history.BrewRepository
import dev.josueamaya.pourline.history.HistoryDatabase
import dev.josueamaya.pourline.history.TestBrews
import dev.josueamaya.pourline.recipe.Recipe
import dev.josueamaya.pourline.recipe.RecipeRepository
import dev.josueamaya.pourline.recipe.RecipeStage
import dev.josueamaya.pourline.recipe.ResourcesRecipeTexts
import dev.josueamaya.pourline.recipe.StageType
import dev.josueamaya.pourline.recipe.aeroPressWithActions
import dev.josueamaya.pourline.recipe.share.DecodeResult
import dev.josueamaya.pourline.recipe.share.QrReadResult
import dev.josueamaya.pourline.recipe.share.RecipeShareCodec
import dev.josueamaya.pourline.recipe.share.isIdentical
import dev.josueamaya.pourline.recipe.v60Classic
import dev.josueamaya.pourline.settings.ExperienceMode
import dev.josueamaya.pourline.settings.LocalExperienceMode
import dev.josueamaya.pourline.share.MlKitQrImageReader
import dev.josueamaya.pourline.share.ShareSender
import dev.josueamaya.pourline.share.assertQrModules
import dev.josueamaya.pourline.share.fourSixUserRecipe
import dev.josueamaya.pourline.share.awaitQrModel
import dev.josueamaya.pourline.share.findQrBox
import dev.josueamaya.pourline.share.qrMatrix
import dev.josueamaya.pourline.ui.theme.PourlineTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.Random
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Compartir desde el editor de recetas (F5): el icono, los dos diálogos del §3.4 y el PNG que sale por el selector.
 * Un [ShareSender] falso recoge el `Intent`. Los tests que leen el QR del PNG necesitan el modelo de Play Services:
 * `emulator-5560` (AVD `SG_API36_play`).
 */
class RecipeShareTest {
  @get:Rule
  val rule = createComposeRule()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private val texts = ResourcesRecipeTexts(context)
  private lateinit var db: HistoryDatabase
  private lateinit var recipes: RecipeRepository
  private lateinit var brews: BrewRepository
  private lateinit var grinders: GrinderRepository
  private val defaults = FakeDefaultGrinderStore()
  private val sent = CopyOnWriteArrayList<Intent>()
  private val sender = ShareSender { sent += it }
  private val sharedDir get() = File(context.cacheDir, "shared")

  @Before
  fun setUp() {
    sharedDir.deleteRecursively()
    db = HistoryDatabase.inMemory(context)
    recipes = RecipeRepository(db, texts)
    brews = BrewRepository(db)
    grinders = db.grinderRepository(context, defaults)
  }

  @After
  fun tearDown() {
    db.close()
    sharedDir.deleteRecursively()
  }

  private fun show(
    id: Long? = null,
    fromBrewId: Long? = null,
    draft: Recipe? = null,
    mode: ExperienceMode = ExperienceMode.EXPERT,
  ): RecipeEditViewModel {
    val viewModel = RecipeEditViewModel(recipes, brews, texts, grinders, defaults, ResourcesGrindTexts(context), id, fromBrewId, draft)
    rule.setContent {
      CompositionLocalProvider(LocalExperienceMode provides mode) {
        PourlineTheme(darkTheme = false) {
          RecipeEditScreen(viewModel, onBack = {}, onDuplicated = {}, shareSender = sender)
        }
      }
    }
    rule.waitUntil(5_000) { exists("recipe_edit_save") }
    return viewModel
  }

  private fun exists(tag: String) = rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

  private fun tapShare() = rule.onNodeWithTag("recipe_edit_share").performClick()

  private fun waitForChooser() = rule.waitUntil(15_000) { sent.isNotEmpty() }

  private fun randomText(length: Int, seed: Long): String {
    val letters = ('a'..'z') + ('A'..'Z') + ('0'..'9')
    val random = Random(seed)
    return String(CharArray(length) { letters[random.nextInt(letters.size)] })
  }

  /** [count] pasos que alternan `POUR` y `WAIT`: válidos para el validador hasta 20. */
  private fun stages(count: Int, instructionLength: Int = 0): List<RecipeStage> {
    var startMs = 0L
    var water = 0.0
    return List(count) { i ->
      val pour = i % 2 == 0
      if (pour) water += 20.0
      RecipeStage(
        name = if (pour) "Vertido ${i + 1}" else null,
        startMs = startMs,
        targetWaterG = water,
        durationMs = 20_000,
        type = if (pour) StageType.POUR else StageType.WAIT,
        instruction = if (instructionLength > 0) randomText(instructionLength, 10L + i) else null,
      ).also { startMs += 25_000 }
    }
  }

  private fun save(
    name: String = "Mi V60",
    stageCount: Int = 6,
    notes: String? = null,
    instructionLength: Int = 0,
  ): Long = runBlocking {
    recipes.save(
      Recipe(name = name, method = BrewMethod.V60, waterTempC = 93.0, notes = notes, doseG = 15.0, stages = stages(stageCount, instructionLength)),
    )
  }

  @Suppress("DEPRECATION")
  private fun sentStream(): Uri = sent.last().getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)!!

  private fun pngBounds(): Pair<Int, Int> {
    val file = sharedDir.listFiles().orEmpty().single()
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    return bounds.outWidth to bounds.outHeight
  }

  private fun assertNothingIsSent() {
    rule.waitForIdle()
    Thread.sleep(1_500)
    rule.waitForIdle()
    assertTrue("no debía abrirse el selector", sent.isEmpty())
    assertNull(sharedDir.listFiles()?.firstOrNull())
  }

  private fun showsText(text: String) = rule.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()

  @Test
  fun aSavedRecipeSharesAPngInBasic() {
    show(save(), mode = ExperienceMode.BASIC)
    rule.onNodeWithTag("recipe_edit_share").assertIsEnabled()
    tapShare()
    waitForChooser()
    assertEquals(Intent.ACTION_CHOOSER, sent.single().action)
    assertEquals(1080 to 1350, pngBounds())
    assertTrue(sharedDir.listFiles().orEmpty().single().name.startsWith("recipe-"))
  }

  @Test
  fun aSavedRecipeSharesAPngInExpert() {
    show(save(), mode = ExperienceMode.EXPERT)
    tapShare()
    waitForChooser()
    assertEquals(1080 to 1350, pngBounds())
  }

  @Test
  fun theFormerFactoryV60AsAnOwnRecipeCanBeShared() {
    show(runBlocking { recipes.save(v60Classic(texts)) })
    rule.onNodeWithTag("recipe_edit_share").assertIsEnabled()
    tapShare()
    waitForChooser()
    assertEquals(1080, pngBounds().first)
  }

  @Test
  fun theIconIsDisabledWithUnsavedChanges() {
    show(save(name = "Mi V60"))
    rule.onNodeWithTag("recipe_edit_share").assertIsEnabled()

    rule.onNodeWithTag("recipe_edit_name").performScrollTo().performTextInput("!")
    rule.onNodeWithTag("recipe_edit_share").assertIsNotEnabled()
    tapShare()
    assertTrue(sent.isEmpty())

    rule.onNodeWithTag("recipe_edit_name").performScrollTo().performTextReplacement("Mi V60")
    rule.onNodeWithTag("recipe_edit_share").assertIsEnabled()
  }

  @Test
  fun theIconIsDisabledForANewRecipe() {
    show()
    rule.onNodeWithTag("recipe_edit_share").assertIsNotEnabled()
  }

  @Test
  fun theIconIsDisabledWhenCreatingFromABrew() {
    val brewId = runBlocking { brews.save(TestBrews.finished(doseG = 20.0), TestBrews.context()) }
    show(fromBrewId = brewId)
    rule.onNodeWithTag("recipe_edit_share").assertIsNotEnabled()
  }

  @Test
  fun theIconIsDisabledForAFourSixDraft() {
    show(draft = Recipe(name = "4:6", method = BrewMethod.V60, doseG = 20.0, stages = stages(5)))
    rule.onNodeWithTag("recipe_edit_share").assertIsNotEnabled()
  }

  @Test
  fun aLongRecipeListsWhatWillBeMissingAndCancelSendsNothing() {
    show(save(name = randomText(400, 3), stageCount = 5, notes = randomText(300, 99), instructionLength = 200))
    tapShare()
    rule.waitUntil(10_000) { exists("ant_modal") }
    val missing = context.getString(
      R.string.recipe_trimmed_list_two,
      context.getString(R.string.recipe_trimmed_notes),
      context.getString(R.string.recipe_trimmed_instructions),
    )
    assertTrue(showsText(context.getString(R.string.recipe_share_trimmed_text, missing)))

    rule.onNodeWithTag("ant_modal_cancel").performClick()
    assertTrue(!exists("ant_modal"))
    assertNothingIsSent()
  }

  @Test
  fun confirmingTheTrimDialogShares() {
    show(save(name = randomText(400, 3), stageCount = 5, notes = randomText(300, 99), instructionLength = 200))
    tapShare()
    rule.waitUntil(10_000) { exists("ant_modal") }
    rule.onNodeWithTag("ant_modal_confirm").performClick()
    waitForChooser()
    val (width, height) = pngBounds()
    assertEquals(1080, width)
    assertTrue("alto de $height px", height >= 1350)
    assertTrue(!exists("ant_modal"))
  }

  @Test
  fun aLongRecipeKeepsTheFirstInstructionsAndListsSomeSteps() {
    val reader = MlKitQrImageReader(context)
    try {
      awaitQrModel(reader)
      val id = save(stageCount = 10, notes = randomText(300, 99), instructionLength = 120)
      val saved = runBlocking { recipes.get(id) }!!
      val encoded = RecipeShareCodec.encode(saved, grinder = null)!!
      assertTrue("el recorte debía ser parcial: ${encoded.droppedInstructions} quitadas y ${encoded.keptInstructions} conservadas", encoded.partialInstructions)

      show(id)
      tapShare()
      rule.waitUntil(10_000) { exists("ant_modal") }
      val missing = context.getString(
        R.string.recipe_trimmed_list_two,
        context.getString(R.string.recipe_trimmed_notes),
        context.getString(R.string.recipe_trimmed_some_instructions),
      )
      assertTrue(showsText(context.getString(R.string.recipe_share_trimmed_text, missing)))
      rule.onNodeWithTag("ant_modal_confirm").performClick()
      waitForChooser()

      val read = runBlocking { reader.read(sentStream()) }
      assertTrue("no se leyó ningún QR: $read", read is QrReadResult.Found)
      val shared = (RecipeShareCodec.decode((read as QrReadResult.Found).text) as DecodeResult.Ok).shared
      assertTrue(shared.partialInstructions)
      assertEquals(saved.stages.first().instruction, shared.recipe.stages.first().instruction)
      assertNull(shared.recipe.stages.last().instruction)
      assertNull(shared.recipe.notes)
      assertEquals(encoded.keptInstructions, shared.recipe.stages.count { it.instruction != null })
    } finally {
      reader.close()
    }
  }

  @Test
  fun aRecipeWithActionsSharesAQrThatKeepsTheActions() {
    val reader = MlKitQrImageReader(context)
    try {
      awaitQrModel(reader)
      val id = runBlocking { recipes.save(aeroPressWithActions()) }
      show(id)
      tapShare()
      waitForChooser()

      val read = runBlocking { reader.read(sentStream()) }
      assertTrue("no se leyó ningún QR: $read", read is QrReadResult.Found)
      val shared = (RecipeShareCodec.decode((read as QrReadResult.Found).text) as DecodeResult.Ok).shared
      val stages = shared.recipe.stages
      assertEquals(
        listOf(StageType.POUR, StageType.SWIRL, StageType.ACTION, StageType.WAIT, StageType.ACTION, StageType.ACTION),
        stages.map { it.type },
      )
      assertEquals(listOf(null, null, "Pon la tapa", null, "Invierte", null), stages.map { it.instruction })
    } finally {
      reader.close()
    }
  }

  @Test
  fun anInvalidSavedRecipeHasItsOwnNoticeAndSharesWithoutQr() {
    val reader = MlKitQrImageReader(context)
    try {
      awaitQrModel(reader)
      // El repositorio no valida: una dosis de 70 g (el máximo es 60) queda guardada.
      val id = runBlocking {
        recipes.save(Recipe(name = "Dosis no válida", method = BrewMethod.V60, doseG = 70.0, stages = stages(4)))
      }
      show(id)
      tapShare()
      rule.waitUntil(10_000) { exists("ant_modal") }
      assertTrue(showsText(context.getString(R.string.recipe_share_invalid_text)))
      assertTrue(!showsText(context.getString(R.string.recipe_share_too_long_text)))
      rule.onNodeWithTag("ant_modal_confirm").performClick()
      waitForChooser()

      assertEquals("la imagen no debe llevar ningún QR", QrReadResult.NoRecipe, runBlocking { reader.read(sentStream()) })
    } finally {
      reader.close()
    }
  }

  @Test
  fun theUsersFourSixRecipeSharesWithoutAskingAndKeepsNotesAndInstructions() {
    val reader = MlKitQrImageReader(context)
    try {
      awaitQrModel(reader)
      val grinderId = runBlocking {
        db.addGrinder(
          Grinder(
            catalogKey = "femobook-a2",
            brand = "Femobook",
            model = "A2",
            units = listOf(GrindUnitLevel(GrindUnit.ROTATIONS), GrindUnitLevel(GrindUnit.CLICKS, perParent = 40)),
            scaleMin = 0.0,
            scaleMax = 120.0,
            owned = true,
          ),
        )
      }
      val recipe = fourSixUserRecipe(grinderId)
      val id = runBlocking { recipes.save(recipe) }
      show(id)
      tapShare()
      waitForChooser()
      assertTrue("no debía abrirse ningún diálogo", !exists("ant_modal"))

      val read = runBlocking { reader.read(sentStream()) }
      assertTrue("no se leyó ningún QR: $read", read is QrReadResult.Found)
      val shared = (RecipeShareCodec.decode((read as QrReadResult.Found).text) as DecodeResult.Ok).shared
      assertTrue(shared.trimmed.isEmpty())
      assertEquals(recipe.notes, shared.recipe.notes)
      assertEquals(recipe.stages.map { it.instruction }, shared.recipe.stages.map { it.instruction })
      assertEquals(recipe.stages.map { it.name }, shared.recipe.stages.map { it.name })
      val grind = shared.grind!!
      assertEquals("femobook-a2", grind.catalogKey)
      assertEquals(34.0, grind.value!!, 1e-9)
      val (width, height) = pngBounds()
      assertEquals(1080, width)
      assertTrue("alto de $height px", height > 1350)
    } finally {
      reader.close()
    }
  }

  @Test
  fun aRecipeThatDoesNotFitSharesTheImageWithoutQr() {
    val reader = MlKitQrImageReader(context)
    try {
      awaitQrModel(reader)
      show(save(name = randomText(800, 5)))
      tapShare()
      rule.waitUntil(10_000) { exists("ant_modal") }
      assertTrue(showsText(context.getString(R.string.recipe_share_too_long_text)))
      rule.onNodeWithTag("ant_modal_confirm").performClick()
      waitForChooser()

      assertEquals("la imagen no debe llevar ningún QR", QrReadResult.NoRecipe, runBlocking { reader.read(sentStream()) })
    } finally {
      reader.close()
    }
  }

  @Test
  fun thePngIsReadableThroughTheFileProviderAndItsQrDecodesToTheRecipe() {
    val reader = MlKitQrImageReader(context)
    try {
      awaitQrModel(reader)
      val shortId = save(name = "Mi V60", stageCount = 6)
      show(shortId)
      tapShare()
      waitForChooser()
      assertEquals(1080 to 1350, pngBounds())
      val read = runBlocking { reader.read(sentStream()) }
      assertTrue("no se leyó ningún QR: $read", read is QrReadResult.Found)
      val decoded = RecipeShareCodec.decode((read as QrReadResult.Found).text) as DecodeResult.Ok
      val saved = runBlocking { recipes.get(shortId) }!!
      assertTrue(isIdentical(saved, decoded.shared.recipe))
    } finally {
      reader.close()
    }
  }

  @Test
  fun aTwentyStepRecipeGrowsAndItsQrDecodes() {
    val reader = MlKitQrImageReader(context)
    try {
      awaitQrModel(reader)
      val id = save(name = "Veinte pasos", stageCount = 20)
      show(id)
      tapShare()
      waitForChooser()
      val (width, height) = pngBounds()
      assertEquals(1080, width)
      assertTrue("alto de $height px", height > 1350)
      val decoded = RecipeShareCodec.decode((runBlocking { reader.read(sentStream()) } as QrReadResult.Found).text) as DecodeResult.Ok
      assertEquals(20, decoded.shared.recipe.stages.size)
    } finally {
      reader.close()
    }
  }

  @Test
  fun theSharedPngHas6x6QrModules() {
    val id = save(name = "Mi V60", stageCount = 12)
    show(id)
    tapShare()
    waitForChooser()
    val saved = runBlocking { recipes.get(id) }!!
    val uri = RecipeShareCodec.encode(saved, grinder = null)!!.uri
    val matrix = qrMatrix(uri)
    val png = BitmapFactory.decodeFile(sharedDir.listFiles().orEmpty().single().path)
    val box = findQrBox(png, 6)
    assertEquals("x par de la caja", 0, box.x % 2)
    assertQrModules(png, matrix, box.x, box.y, 6)
    assertEquals(1080, png.width)
    assertTrue(box.x + matrix.cells * 6 <= png.width)
  }

  @Test
  fun theChooserExcludesPourline() {
    show(save())
    tapShare()
    waitForChooser()
    @Suppress("DEPRECATION")
    val excluded = sent.single().getParcelableArrayExtra(Intent.EXTRA_EXCLUDE_COMPONENTS)!!.map { it as ComponentName }
    assertTrue(excluded.contains(ComponentName(context.packageName, "dev.josueamaya.pourline.MainActivity")))
  }
}
