package dev.dotscale.ui.coffees

import android.content.Intent
import android.util.Log
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import dev.dotscale.coffee.CoffeeRepository
import dev.dotscale.coffee.label.MlKitLabelOcr
import dev.dotscale.coffee.label.SyntheticLabel
import dev.dotscale.coffee.photo.CoffeeImages
import dev.dotscale.coffee.photo.CoffeePhotoStore
import dev.dotscale.coffee.photo.LocalCoffeeImages
import dev.dotscale.history.BrewRepository
import dev.dotscale.history.HistoryDatabase
import dev.dotscale.history.TestBrews
import dev.dotscale.history.UsedCoffee
import dev.dotscale.share.ShareSender
import dev.dotscale.ui.history.BrewDetailScreen
import dev.dotscale.ui.history.BrewDetailViewModel
import dev.dotscale.ui.theme.DotScaleTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.io.FileInputStream
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Objetivos de rendimiento de SPEC-004 §5 (plan §7.2) con una base `coffee-perf.db` y fotos en un directorio aparte,
 * borrados al terminar. Cada tiempo se mide con una pasada de calentamiento que no cuenta y la mediana de 5.
 * Pensado para un emulador con API 36 o superior.
 * - Lista: 200 cafés con foto y miniatura y 3 preparaciones vinculadas cada uno, hasta la primera fila: menos de 1 s.
 * - OCR: una etiqueta sintética de 1600 × 1200 px: menos de 3 s.
 * - Imagen: del toque en compartir hasta el `ShareSender`, con el PNG escrito: menos de 1 s para el café con foto y
 *   para una preparación de 10 min con objetivo.
 */
class CoffeePerformanceTest {
  @get:Rule
  val rule = createComposeRule()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private lateinit var root: File
  private lateinit var store: CoffeePhotoStore
  private lateinit var db: HistoryDatabase
  private lateinit var coffees: CoffeeRepository
  private lateinit var brews: BrewRepository
  private lateinit var images: CoffeeImages
  private val sent = CopyOnWriteArrayList<Intent>()
  private val sender = ShareSender { sent += it }
  private var photoCoffeeId = 0L

  @Before
  fun setUp() {
    root = File(context.cacheDir, "coffee-perf").apply {
      deleteRecursively()
      mkdirs()
    }
    context.deleteDatabase(DB_NAME)
    store = CoffeePhotoStore(File(root, "photos"), File(root, "cache"))
    db = HistoryDatabase.create(context, DB_NAME)
    coffees = CoffeeRepository(db, store)
    brews = BrewRepository(db)
    images = CoffeeImages(store, maxBytes = 32 * 1024 * 1024)
    runBlocking {
      val source = File(root, "label.jpg")
      SyntheticLabel.writeJpeg(source)
      val template = store.import { FileInputStream(source) }
      repeat(COFFEES) { i ->
        // Cada café con su propio par foto y miniatura: se copian los archivos de la primera importación.
        val name = "${UUID.randomUUID()}.jpg"
        store.file(template).copyTo(store.file(name))
        store.thumbFile(template).copyTo(store.thumbFile(name))
        val id = coffees.save(TestBrews.coffeeDraft(name = "Café $i", photo1 = name))
        if (i == 0) photoCoffeeId = id
        repeat(BREWS_PER_COFFEE) { j ->
          brews.save(
            TestBrews.finished(totalMs = 60_000, seed = (i * BREWS_PER_COFFEE + j).toLong()),
            TestBrews.context(startedAtEpochMs = 1_700_000_000_000 + (i * BREWS_PER_COFFEE + j) * 3_600_000L, coffee = UsedCoffee(id, "Café $i")),
          )
        }
      }
    }
  }

  @After
  fun tearDown() {
    db.close()
    context.deleteDatabase(DB_NAME)
    root.deleteRecursively()
  }

  private fun median(values: List<Long>) = values.sorted()[values.size / 2]

  private fun exists(tag: String) = rule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

  @Test
  fun theListOpensWithTheFirstRowInUnderASecond() {
    var open by mutableStateOf(false)
    rule.setContent {
      DotScaleTheme(darkTheme = false) {
        CompositionLocalProvider(LocalCoffeeImages provides images) {
          if (open) {
            val viewModel = remember { CoffeesViewModel(coffees) }
            CoffeesScreen(viewModel, onBack = {}, onOpenCoffee = {}, onAddCoffee = {})
          }
        }
      }
    }
    val times = mutableListOf<Long>()
    repeat(1 + SAMPLES) { run ->
      open = false
      rule.waitForIdle()
      val start = System.nanoTime()
      open = true
      rule.waitUntil(10_000) { exists("coffee_row") }
      val ms = (System.nanoTime() - start) / 1_000_000
      if (run > 0) times += ms
    }
    val report = "lista=$times mediana=${median(times)} ms con $COFFEES cafés"
    Log.i(TAG, report)
    assertTrue("la lista tarda demasiado: $report", median(times) < LIST_LIMIT_MS)
  }

  @Test
  fun theOcrReadsAFullSizeLabelInUnderThreeSeconds() {
    val ocr = MlKitLabelOcr()
    try {
      val photo = File(root, "ocr.jpg").also { SyntheticLabel.writeJpeg(it) }
      val times = mutableListOf<Long>()
      repeat(1 + SAMPLES) { run ->
        val start = System.nanoTime()
        runBlocking { ocr.read(photo) }
        val ms = (System.nanoTime() - start) / 1_000_000
        if (run > 0) times += ms
      }
      val report = "ocr=$times mediana=${median(times)} ms"
      Log.i(TAG, report)
      assertTrue("el OCR tarda demasiado: $report", median(times) < OCR_LIMIT_MS)
    } finally {
      ocr.close()
    }
  }

  @Test
  fun theCoffeeCardImageIsReadyInUnderASecond() {
    val viewModel = CoffeeDetailViewModel(coffees, photoCoffeeId)
    rule.setContent {
      DotScaleTheme(darkTheme = false) {
        CompositionLocalProvider(LocalCoffeeImages provides images) {
          CoffeeDetailScreen(viewModel, onBack = {}, onEdit = {}, onNewBag = {}, onOpenBrew = {}, shareSender = sender)
        }
      }
    }
    measureShare("coffee_detail_share", "imagen del café")
  }

  @Test
  fun theBrewImageWithATargetIsReadyInUnderASecond() {
    val id = runBlocking {
      brews.save(
        TestBrews.finished(totalMs = 600_000),
        TestBrews.context(startedAtEpochMs = 1_700_000_000_000, target = TestBrews.usedTarget(score = 92), coffee = UsedCoffee(photoCoffeeId, "Café 0")),
      )
    }
    val viewModel = BrewDetailViewModel(brews, coffees, id)
    rule.setContent {
      DotScaleTheme(darkTheme = false) {
        CompositionLocalProvider(LocalCoffeeImages provides images) {
          BrewDetailScreen(viewModel, onBack = {}, onEdit = {}, onCompare = {}, onSaveAsRecipe = {}, shareSender = sender)
        }
      }
    }
    measureShare("brew_detail_share", "imagen de la preparación")
  }

  /** Toca [tag] seis veces (la primera de calentamiento) y mide hasta que el `ShareSender` recibe el chooser. */
  private fun measureShare(tag: String, label: String) {
    rule.waitUntil(10_000) { exists(tag) }
    val times = mutableListOf<Long>()
    repeat(1 + SAMPLES) { run ->
      val before = sent.size
      val start = System.nanoTime()
      rule.onNodeWithTag(tag, useUnmergedTree = true).performClick()
      rule.waitUntil(15_000) { sent.size > before }
      val ms = (System.nanoTime() - start) / 1_000_000
      if (run > 0) times += ms
    }
    val report = "$label=$times mediana=${median(times)} ms"
    Log.i(TAG, report)
    assertTrue("la $label tarda demasiado: $report", median(times) < IMAGE_LIMIT_MS)
  }

  private companion object {
    const val TAG = "CoffeePerf"
    const val DB_NAME = "coffee-perf.db"
    const val COFFEES = 200
    const val BREWS_PER_COFFEE = 3
    const val SAMPLES = 5
    const val LIST_LIMIT_MS = 1_000L
    const val OCR_LIMIT_MS = 3_000L
    const val IMAGE_LIMIT_MS = 1_000L
  }
}
