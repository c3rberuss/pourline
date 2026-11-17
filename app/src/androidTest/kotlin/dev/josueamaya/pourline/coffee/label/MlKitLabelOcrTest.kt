package dev.josueamaya.pourline.coffee.label

import androidx.test.platform.app.InstrumentationRegistry
import dev.josueamaya.pourline.coffee.CoffeeProcess
import dev.josueamaya.pourline.coffee.fold
import dev.josueamaya.pourline.coffee.photo.CoffeePhotoStore
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.FileInputStream
import java.time.LocalDate

/** ML Kit sobre una etiqueta dibujada por el test. El modelo va en el APK: no necesita red. */
class MlKitLabelOcrTest {
  private lateinit var root: File
  private lateinit var store: CoffeePhotoStore
  private val ocr = MlKitLabelOcr()
  private val today = LocalDate.of(2026, 11, 13)

  @Before
  fun setUp() {
    root = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "ocr-test").apply {
      deleteRecursively()
      mkdirs()
    }
    store = CoffeePhotoStore(File(root, "photos"), File(root, "cache"))
  }

  @After
  fun tearDown() {
    ocr.close()
    root.deleteRecursively()
  }

  private suspend fun linesOf(sideways: Boolean): List<OcrLine> {
    val source = File(root, "label.jpg")
    SyntheticLabel.writeJpeg(source, sideways)
    val name = store.import { FileInputStream(source) }
    return ocr.read(store.file(name))
  }

  @Test
  fun readsTheSyntheticLabelAndTheNameIsTheTallestLine() = runTest(timeout = kotlin.time.Duration.parse("2m")) {
    val lines = linesOf(sideways = false)
    val text = lines.joinToString("\n") { it.text }
    assertTrue(text, fold(text).contains("etiopia"))
    assertTrue(text, text.contains("250"))
    val tallest = lines.maxBy { it.heightPx }
    assertTrue(tallest.text, fold(tallest.text).contains("etiopia"))
  }

  @Test
  fun theParserExtractsTheFieldsFromTheSyntheticLabel() = runTest(timeout = kotlin.time.Duration.parse("2m")) {
    val fields = LabelParser.parse(linesOf(sideways = false), today)
    assertTrue(fields.toString(), sameName("Etiopía Guji", fields.name))
    assertEquals(LocalDate.of(2026, 10, 12), fields.roastDate)
    assertEquals(CoffeeProcess.NATURAL, fields.process)
    assertEquals(1900, fields.altitudeMinM)
    assertEquals(250.0, fields.bagWeightG!!, 0.0)
  }

  @Test
  fun aSidewaysPhotoWithExifGivesTheSameLinesAsAnUprightOne() = runTest(timeout = kotlin.time.Duration.parse("2m")) {
    val upright = linesOf(sideways = false).map { fold(it.text) }
    val sideways = linesOf(sideways = true).map { fold(it.text) }
    assertEquals(upright, sideways)
  }
}
