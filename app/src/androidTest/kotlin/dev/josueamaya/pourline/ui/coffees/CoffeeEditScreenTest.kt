package dev.josueamaya.pourline.ui.coffees

import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import dev.josueamaya.pourline.R
import dev.josueamaya.pourline.coffee.CoffeeProcess
import dev.josueamaya.pourline.coffee.CoffeeRepository
import dev.josueamaya.pourline.coffee.label.OcrLine
import dev.josueamaya.pourline.coffee.photo.CoffeePhotoStore
import dev.josueamaya.pourline.history.HistoryDatabase
import dev.josueamaya.pourline.history.TestBrews
import dev.josueamaya.pourline.ui.theme.PourlineTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

class CoffeeEditScreenTest {
  @get:Rule
  val rule = createComposeRule()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private val today = LocalDate.of(2026, 11, 13)
  private lateinit var db: HistoryDatabase
  private lateinit var coffees: CoffeeRepository
  private lateinit var photos: CoffeePhotoStore
  private lateinit var photoDir: File
  private lateinit var results: FakeActivityResults
  private val ocr = FakeLabelOcr(
    lines = listOf(OcrLine("Etiopía Guji", 90), OcrLine("Proceso: Natural", 30), OcrLine("Tostado: 12/10/2026", 30)),
  )
  private var savedId: Long? = null
  private var backs = 0

  @Before
  fun setUp() {
    db = HistoryDatabase.inMemory(context)
    photoDir = File(context.cacheDir, "edit-test-${System.nanoTime()}")
    photos = CoffeePhotoStore(photoDir, context.cacheDir)
    coffees = CoffeeRepository(db, photos, { today }, { ZoneId.of("UTC") })
    results = FakeActivityResults(context.cacheDir)
  }

  @After
  fun tearDown() {
    db.close()
    photoDir.deleteRecursively()
  }

  private fun show(id: Long? = null, fromCoffeeId: Long? = null) {
    val viewModel = CoffeeEditViewModel(coffees, photos, ocr, id, fromCoffeeId, { today })
    rule.setContent {
      CompositionLocalProvider(LocalActivityResultRegistryOwner provides results) {
        PourlineTheme(darkTheme = false) {
          CoffeeEditScreen(
            viewModel,
            newCameraTarget = { photos.newCameraTarget().also { results.cameraTarget = it } },
            onBack = { backs++ },
            onSaved = { savedId = it },
            today = { today },
            isEdit = id != null,
          )
        }
      }
    }
  }

  private fun exists(tag: String) = rule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

  private fun waitFor(tag: String) = rule.waitUntil(10_000) { exists(tag) }

  private fun textCount(text: String) = rule.onAllNodesWithText(text).fetchSemanticsNodes().size

  private fun photosOnDisk() = photoDir.listFiles().orEmpty().toList()

  @Test
  fun registeringFromTheGalleryReadsTheLabelMarksTheFieldsAndSaves() {
    show()
    rule.onNodeWithTag("coffee_photo_gallery_1").performClick()
    waitFor("ocr_mark_name")
    waitFor("ocr_mark_roast_date")
    waitFor("ocr_mark_process")
    rule.onNodeWithTag("coffee_edit_ocr_alert").assertIsDisplayed()
    rule.onNodeWithTag("coffee_edit_name").assertTextContains("Etiopía Guji")

    rule.onNodeWithTag("coffee_edit_save").performClick()
    rule.waitUntil(10_000) { savedId != null }
    val saved = runBlocking { coffees.get(savedId!!) }!!
    assertEquals("Etiopía Guji", saved.name)
    assertEquals(CoffeeProcess.NATURAL, saved.process)
    assertEquals(LocalDate.of(2026, 10, 12), saved.roastDate)
    val photo = saved.photo1!!
    assertTrue(photos.file(photo).exists())
    assertTrue(photos.thumbFile(photo).exists())
  }

  @Test
  fun aNameTypedBeforeReadingTheLabelIsNotOverwritten() {
    show()
    rule.onNodeWithTag("coffee_edit_name").performTextInput("Mi café")
    rule.onNodeWithTag("coffee_photo_gallery_1").performClick()
    waitFor("ocr_mark_process")
    rule.onNodeWithTag("coffee_edit_name").assertTextContains("Mi café")
    assertTrue(!exists("ocr_mark_name"))
  }

  @Test
  fun editingAMarkedFieldRemovesItsMark() {
    show()
    rule.onNodeWithTag("coffee_photo_gallery_1").performClick()
    waitFor("ocr_mark_name")
    rule.onNodeWithTag("coffee_edit_name").performTextInput("!")
    rule.waitUntil(5_000) { !exists("ocr_mark_name") }
    assertTrue(exists("ocr_mark_process"))
  }

  @Test
  fun readLabelIsDisabledWithoutPhotosAndWhileReading() {
    show()
    rule.onNodeWithTag("coffee_edit_read_label").assertIsNotEnabled()
    val gate = CompletableDeferred<Unit>()
    ocr.gate = gate
    rule.onNodeWithTag("coffee_photo_gallery_1").performClick()
    rule.waitUntil(10_000) { ocr.reads == 1 }
    rule.onNodeWithTag("coffee_edit_read_label").assertIsNotEnabled()
    gate.complete(Unit)
    waitFor("ocr_mark_name")
    rule.onNodeWithTag("coffee_edit_read_label").assertIsEnabled()
  }

  @Test
  fun anUnreadableLabelShowsAWarningAndLeavesTheFormAlone() {
    ocr.lines = emptyList()
    show()
    rule.onNodeWithTag("coffee_photo_gallery_1").performClick()
    rule.waitUntil(10_000) { rule.onAllNodesWithTag("coffee_photo_remove_1").fetchSemanticsNodes().isNotEmpty() }
    rule.waitUntil(10_000) { textCount(context.getString(R.string.ocr_failed)) > 0 }
    assertTrue(!exists("coffee_edit_ocr_alert"))
    rule.onNodeWithTag("coffee_edit_save").assertIsNotEnabled()
  }

  @Test
  fun anEngineFailureIsTreatedAsNoResults() {
    ocr.failure = IllegalStateException("boom")
    show()
    rule.onNodeWithTag("coffee_photo_gallery_1").performClick()
    rule.waitUntil(10_000) { textCount(context.getString(R.string.ocr_failed)) > 0 }
  }

  @Test
  fun validationErrorsAppearUnderTheirFieldAndBlockSaving() {
    show()
    rule.onNodeWithTag("coffee_edit_name").performTextInput("x")
    rule.onNodeWithTag("coffee_edit_save").assertIsEnabled()
    rule.onNodeWithTag("coffee_edit_name").performTextClearance()
    rule.onNodeWithTag("coffee_edit_name_error").assertIsDisplayed()
    rule.onNodeWithTag("coffee_edit_save").assertIsNotEnabled()

    rule.onNodeWithTag("coffee_edit_name").performTextInput("Café")
    rule.onNodeWithTag("coffee_edit_altitude_min").performTextInput("2000")
    rule.onNodeWithTag("coffee_edit_altitude_max").performTextInput("1800")
    rule.onNodeWithTag("coffee_edit_altitude_min_error").performScrollTo().assertIsDisplayed()
    rule.onNodeWithText(context.getString(R.string.coffee_error_altitude_range)).assertExists()
    rule.onNodeWithTag("coffee_edit_save").assertIsNotEnabled()
    rule.onNodeWithTag("coffee_edit_altitude_max").performTextClearance()
    rule.onNodeWithTag("coffee_edit_altitude_max").performTextInput("2100")

    rule.onNodeWithTag("coffee_edit_bag_weight").performTextInput("0")
    rule.onNodeWithTag("coffee_edit_bag_weight_error").performScrollTo().assertIsDisplayed()
    rule.onNodeWithTag("coffee_edit_save").assertIsNotEnabled()
    rule.onNodeWithTag("coffee_edit_bag_weight").performTextClearance()
    rule.onNodeWithTag("coffee_edit_bag_weight").performTextInput("250")
    rule.onNodeWithTag("coffee_edit_save").assertIsEnabled()
  }

  @Test
  fun goingBackWithChangesAsksAndDiscardingDeletesTheNewPhoto() {
    show()
    rule.onNodeWithTag("coffee_photo_gallery_1").performClick()
    waitFor("ocr_mark_name")
    assertTrue(photosOnDisk().isNotEmpty())
    rule.onNodeWithContentDescription(context.getString(R.string.scale_back)).performClick()
    assertEquals(0, backs)
    rule.onNodeWithText(context.getString(R.string.brew_edit_discard_title)).assertIsDisplayed()
    rule.onNodeWithText(context.getString(R.string.brew_edit_keep)).performClick()
    assertEquals(0, backs)
    rule.onNodeWithContentDescription(context.getString(R.string.scale_back)).performClick()
    rule.onNodeWithText(context.getString(R.string.brew_edit_discard)).performClick()
    rule.waitUntil(5_000) { backs == 1 }
    assertEquals(emptyList<File>(), photosOnDisk())
  }

  @Test
  fun registeringWithTheCameraTakesThePhotoAndSaves() {
    show()
    rule.onNodeWithTag("coffee_photo_camera_1").performClick()
    waitFor("coffee_photo_remove_1")
    rule.onNodeWithTag("coffee_edit_name").performTextInput("Con cámara")
    rule.onNodeWithTag("coffee_edit_save").performClick()
    rule.waitUntil(10_000) { savedId != null }
    assertNotNull(runBlocking { coffees.get(savedId!!) }!!.photo1)
  }

  @Test
  fun cancellingTheGalleryOrTheCameraDoesNothing() {
    results.cancel = true
    show()
    rule.onNodeWithTag("coffee_photo_gallery_1").performClick()
    rule.onNodeWithTag("coffee_photo_camera_2").performClick()
    rule.waitForIdle()
    assertEquals(2, results.launches)
    assertTrue(!exists("coffee_photo_remove_1"))
    assertTrue(!exists("coffee_photo_remove_2"))
    assertEquals(0, ocr.reads)
  }

  @Test
  fun removingAPhotoAndTheVarietiesField() {
    show()
    rule.onNodeWithTag("coffee_photo_gallery_1").performClick()
    waitFor("coffee_photo_remove_1")
    rule.onNodeWithTag("coffee_photo_remove_1").performClick()
    rule.waitUntil(5_000) { !exists("coffee_photo_remove_1") }
    assertEquals(emptyList<File>(), photosOnDisk())

    rule.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("coffee_edit_variety_input"))).performTextInput("Heirloom")
    rule.onNodeWithTag("coffee_edit_variety_add").performClick()
    rule.onNodeWithText("Heirloom").assertIsDisplayed()
  }

  @Test
  fun aNewBagStartsFromTheCoffeeWithoutDatesOrPhotos() {
    val original = runBlocking {
      coffees.save(TestBrews.coffeeDraft(name = "Etiopía Guji", roastDate = LocalDate.of(2026, 11, 4)))
    }
    show(fromCoffeeId = original)
    rule.onNodeWithText(context.getString(R.string.coffee_edit_title_bag)).assertIsDisplayed()
    rule.waitUntil(5_000) { exists("coffee_edit_name") }
    rule.onNodeWithTag("coffee_edit_name").assertTextContains("Etiopía Guji")
    rule.onNodeWithTag("coffee_edit_save").performClick()
    rule.waitUntil(10_000) { savedId != null }
    assertTrue(savedId != original)
    assertNull(runBlocking { coffees.get(savedId!!) }!!.roastDate)
    assertEquals(2, runBlocking { coffees.pickable().first() }.size)
  }
}
