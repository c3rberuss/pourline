package dev.dotscale.share

import android.content.ClipData
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import dev.dotscale.coffee.CoffeeRepository
import dev.dotscale.coffee.PhotoFiles
import dev.dotscale.history.BrewRecord
import dev.dotscale.history.BrewRepository
import dev.dotscale.history.HistoryDatabase
import dev.dotscale.history.TestBrews
import dev.dotscale.history.UsedCoffee
import dev.dotscale.protocol.WeightUnit
import dev.dotscale.ui.coffees.CoffeeDetailScreen
import dev.dotscale.ui.coffees.CoffeeDetailViewModel
import dev.dotscale.ui.history.BrewCompareScreen
import dev.dotscale.ui.history.BrewCompareViewModel
import dev.dotscale.ui.history.BrewDetailScreen
import dev.dotscale.ui.history.BrewDetailViewModel
import dev.dotscale.ui.scale.BrewSummaryCard
import dev.dotscale.ui.theme.DotScaleTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Cada punto de entrada de "Compartir" acaba en un selector de apps con `ACTION_SEND` de `image/png`, un `Uri` de
 * `dev.dotscale.fileprovider` con permiso de lectura y un PNG de 1080 × 1350 en `cacheDir/shared/`. Un [ShareSender]
 * falso recoge el `Intent` en lugar de abrir el selector.
 */
class ShareIntentTest {
  @get:Rule
  val rule = createComposeRule()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private lateinit var db: HistoryDatabase
  private lateinit var brews: BrewRepository
  private lateinit var coffees: CoffeeRepository
  private val sent = CopyOnWriteArrayList<Intent>()
  private val sender = ShareSender { sent += it }
  private val sharedDir get() = File(context.cacheDir, "shared")

  @Before
  fun setUp() {
    sharedDir.deleteRecursively()
    db = HistoryDatabase.inMemory(context)
    brews = BrewRepository(db)
    coffees = CoffeeRepository(db, PhotoFiles { })
  }

  @After
  fun tearDown() {
    db.close()
    sharedDir.deleteRecursively()
  }

  private fun saveCoffee() = runBlocking { coffees.save(TestBrews.coffeeDraft(name = "Etiopía Guji")) }

  private fun saveBrew(coffeeId: Long?, startedAt: Long) = runBlocking {
    brews.save(
      TestBrews.finished(totalMs = 60_000),
      TestBrews.context(startedAtEpochMs = startedAt, coffee = coffeeId?.let { UsedCoffee(it, "x") }),
    )
  }

  private fun exists(tag: String) = rule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

  private fun tapShare(tag: String) {
    rule.waitUntil(5_000) { exists(tag) }
    rule.onNodeWithTag(tag, useUnmergedTree = true).performClick()
  }

  private fun waitForChooser(count: Int = 1) {
    rule.waitUntil(15_000) { sent.size >= count }
  }

  private fun assertChooser(chooser: Intent) {
    assertEquals(Intent.ACTION_CHOOSER, chooser.action)
    assertTrue(chooser.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    @Suppress("DEPRECATION")
    val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
    assertEquals(Intent.ACTION_SEND, send.action)
    assertEquals("image/png", send.type)
    assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    @Suppress("DEPRECATION")
    val uri = send.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)!!
    assertEquals("${context.packageName}.fileprovider", uri.authority)
    assertEquals("dev.dotscale.fileprovider", uri.authority)
    val clip: ClipData = send.clipData!!
    assertEquals(uri, clip.getItemAt(0).uri)
    assertImageOnDisk()
  }

  private fun assertImageOnDisk() {
    val files = sharedDir.listFiles().orEmpty()
    assertEquals("solo debe quedar la última imagen", 1, files.size)
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(files.single().path, bounds)
    assertEquals(1080, bounds.outWidth)
    assertEquals(1350, bounds.outHeight)
    assertNotNull(files.single().name.takeIf { it.endsWith(".png") })
  }

  @Test
  fun coffeeDetailSharesAPngAndTwoSharesLeaveOneFile() {
    val id = saveCoffee()
    val viewModel = CoffeeDetailViewModel(coffees, id)
    rule.setContent {
      DotScaleTheme(darkTheme = false) {
        CoffeeDetailScreen(viewModel, onBack = {}, onEdit = {}, onNewBag = {}, onOpenBrew = {}, shareSender = sender)
      }
    }
    tapShare("coffee_detail_share")
    waitForChooser()
    assertChooser(sent[0])

    tapShare("coffee_detail_share")
    waitForChooser(2)
    assertChooser(sent[1])
  }

  @Test
  fun brewDetailSharesAPng() {
    val id = saveBrew(saveCoffee(), startedAt = 1_000)
    val viewModel = BrewDetailViewModel(brews, coffees, id)
    rule.setContent {
      DotScaleTheme(darkTheme = false) {
        BrewDetailScreen(viewModel, onBack = {}, onEdit = {}, onCompare = {}, onSaveAsRecipe = {}, shareSender = sender)
      }
    }
    tapShare("brew_detail_share")
    waitForChooser()
    assertChooser(sent[0])
  }

  @Test
  fun compareSharesAPng() {
    val coffeeId = saveCoffee()
    val a = saveBrew(coffeeId, startedAt = 1_000)
    val b = saveBrew(null, startedAt = 2_000)
    val viewModel = BrewCompareViewModel(brews, coffees, a, b)
    rule.setContent {
      DotScaleTheme(darkTheme = false) { BrewCompareScreen(viewModel, onBack = {}, shareSender = sender) }
    }
    tapShare("compare_share")
    waitForChooser()
    assertChooser(sent[0])
  }

  @Test
  fun theSummaryButtonSharesAPng() {
    val id = saveBrew(null, startedAt = 1_000)
    val record: BrewRecord = runBlocking { brews.get(id) }!!
    rule.setContent {
      DotScaleTheme(darkTheme = false) {
        val snackbar = remember { SnackbarHostState() }
        val launcher = rememberShareLauncher(sender, snackbar)
        BrewSummaryCard(
          summary = record.summary,
          unit = WeightUnit.GRAMS,
          onShare = {
            launcher.share(
              ShareTarget.Summary,
              ShareContent.Brew(
                BrewShareData(
                  samples = record.samples,
                  summary = record.summary,
                  unit = WeightUnit.GRAMS,
                  target = null,
                  title = "Resumen",
                  subtitle = null,
                  fitScore = null,
                  rating = null,
                  notes = null,
                  footer = footerLines(null),
                ),
              ),
            )
          },
        )
        launcher.Host()
      }
    }
    tapShare("brew_summary_share_image")
    waitForChooser()
    assertChooser(sent[0])
  }
}
