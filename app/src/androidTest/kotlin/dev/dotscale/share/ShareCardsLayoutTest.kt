package dev.dotscale.share

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.platform.app.InstrumentationRegistry
import dev.dotscale.coffee.CoffeeProcess
import dev.dotscale.coffee.CoffeeRepository
import dev.dotscale.coffee.PhotoFiles
import dev.dotscale.coffee.RoastLevel
import dev.dotscale.history.BrewDetails
import dev.dotscale.history.BrewMethod
import dev.dotscale.history.BrewRepository
import dev.dotscale.history.HistoryDatabase
import dev.dotscale.history.TestBrews
import dev.dotscale.history.UsedCoffee
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/**
 * Las cuatro tarjetas con los datos más largos posibles: cada una se captura con [ShareCaptureHost] y tiene que
 * dar 1080 × 1350 px, con la marca dentro del alto, las notas en 3 líneas como máximo y la gráfica de al menos
 * 144 dp (432 px).
 */
class ShareCardsLayoutTest {
  @get:Rule
  val rule = createComposeRule()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private lateinit var db: HistoryDatabase
  private lateinit var brews: BrewRepository
  private lateinit var coffees: CoffeeRepository
  private var captured: ImageBitmap? = null
  private var failed = false

  private val longName = "Etiopía Guji Hambela Natural Anaeróbico Reserva Especial de la Cosecha Tardía 2026 X"
  private val longNotes = "Jazmín, bergamota y durazno maduro con final de miel, panela y un recuerdo de té negro. ".repeat(6).take(500)

  @Before
  fun setUp() {
    db = HistoryDatabase.inMemory(context)
    brews = BrewRepository(db)
    coffees = CoffeeRepository(db, PhotoFiles { })
  }

  @After
  fun tearDown() = db.close()

  private fun capture(request: ShareRequest) {
    // La tarjeta sigue compuesta tras la captura para poder medir sus nodos.
    rule.setContent { ShareCaptureHost(request, onCaptured = { captured = it }, onFailed = { failed = true }) }
    rule.waitUntil(10_000) { captured != null || failed }
    assertTrue("la captura falló", !failed)
  }

  private fun assertSize() {
    assertEquals(1080, captured!!.width)
    assertEquals(1350, captured!!.height)
  }

  private fun node(tag: String) = rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()

  private fun lines(tag: String): Int {
    val results = mutableListOf<TextLayoutResult>()
    node(tag).config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
    return results.single().lineCount
  }

  private fun brandInside() {
    val brand = node("share_brand")
    assertTrue("la marca debe quedar dentro del alto", brand.positionInRoot.y + brand.size.height <= 1350)
    assertTrue(brand.positionInRoot.y >= 0f)
  }

  private fun photo(): ImageBitmap =
    Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(120, 80, 40)) }.asImageBitmap()

  private fun saveBrew(coffeeId: Long?, startedAt: Long, withTarget: Boolean = true, details: BrewDetails? = null): Long = runBlocking {
    val id = brews.save(
      TestBrews.finished(totalMs = 240_000),
      TestBrews.context(
        startedAtEpochMs = startedAt,
        target = if (withTarget) TestBrews.usedTarget(score = 92) else null,
        coffee = coffeeId?.let { UsedCoffee(it, "x") },
      ),
    )
    if (details != null) brews.updateDetails(id, details)
    id
  }

  private fun longCoffee(name: String = longName): Long = runBlocking {
    coffees.save(
      TestBrews.coffeeDraft(name = name).copy(
        roaster = "Café de Altura Tostadores Especializados de Origen Único S.A.",
        originRegion = "Guji Zone, Oromia Region, Ethiopia",
        farm = "Hambela Estate Washing Station Cooperative Union",
        varieties = listOf("Heirloom", "74110", "74112", "Typica", "Bourbon", "Geisha"),
        process = CoffeeProcess.OTHER,
        processOther = "Fermentación láctica anaeróbica de 72 horas",
        altitudeMinM = 1800,
        altitudeMaxM = 2200,
        roastLevel = RoastLevel.LIGHT,
        roastDate = LocalDate.of(2026, 10, 12),
        roasterNotes = longNotes,
        price = 18.5,
        bagWeightG = 250.0,
      ),
    )
  }

  @Test
  fun theCoffeeCardIsFullSizeWithPhotoAndTheLongestData() {
    val id = longCoffee()
    val entity = runBlocking { coffees.get(id) }!!
    capture(ShareRequest.Coffee(entity.toShareData(daysSinceRoast = 9), photo()))

    assertSize()
    brandInside()
    assertTrue(lines("share_name") <= 2)
    assertTrue(lines("share_notes") <= 3)
    assertNotNull(node("share_photo"))
    // Nunca precio ni stock.
    assertEquals(0, rule.onAllNodesWithTag("share_fact_bag", useUnmergedTree = true).fetchSemanticsNodes().size)
  }

  @Test
  fun theCoffeeCardWithoutPhotoOrNotesKeepsTheSize() {
    val id = runBlocking { coffees.save(TestBrews.coffeeDraft(name = "Solo nombre").copy(roaster = null, roastDate = null, roasterNotes = null)) }
    val entity = runBlocking { coffees.get(id) }!!
    capture(ShareRequest.Coffee(entity.toShareData(daysSinceRoast = null), photo = null))

    assertSize()
    brandInside()
    assertEquals(0, rule.onAllNodesWithTag("share_photo", useUnmergedTree = true).fetchSemanticsNodes().size)
    assertEquals(0, rule.onAllNodesWithTag("share_notes", useUnmergedTree = true).fetchSemanticsNodes().size)
  }

  @Test
  fun theSummaryCardIsFullSizeWithTheMinimumChart() {
    val coffeeId = longCoffee()
    val id = saveBrew(coffeeId, 1_700_000_000_000)
    val record = runBlocking { brews.get(id) }!!
    val footer = footerLines(FooterCoffee(coffeeId, longName, "Café de Altura Tostadores Especializados de Origen Único", 9))
    capture(
      ShareRequest.Brew(
        BrewShareData(
          samples = record.samples,
          summary = record.summary,
          unit = record.entity.unit,
          target = record.target,
          title = "13 nov 2026, 8:30",
          subtitle = "Objetivo: V60 clásica con un nombre bastante largo para forzar el recorte de la línea",
          fitScore = 92,
          rating = null,
          notes = null,
          footer = footer,
        ),
      ),
    )

    assertSize()
    brandInside()
    assertTrue("gráfica de ${node("share_chart").size.height} px", node("share_chart").size.height >= 432)
  }

  @Test
  fun theDetailCardIsFullSizeWithNotesRatingAndFooter() {
    val coffeeId = longCoffee()
    val id = saveBrew(coffeeId, 1_700_000_000_000, details = BrewDetails(name = longName, method = BrewMethod.V60, notes = longNotes, rating = 4))
    val record = runBlocking { brews.get(id) }!!
    capture(
      ShareRequest.Brew(
        BrewShareData(
          samples = record.samples,
          summary = record.summary,
          unit = record.entity.unit,
          target = record.target,
          title = longName,
          subtitle = "13 nov 2026, 8:30 · V60",
          fitScore = 92,
          rating = 4,
          notes = longNotes,
          footer = footerLines(FooterCoffee(coffeeId, longName, "Café de Altura Tostadores Especializados de Origen Único", 9)),
        ),
      ),
    )

    assertSize()
    brandInside()
    assertTrue(lines("share_notes") <= 3)
    val height = node("share_chart").size.height
    assertTrue("gráfica de $height px", height >= 432)
  }

  @Test
  fun theComparisonCardIsFullSizeWithTwoCoffeesInTheFooter() {
    val first = longCoffee()
    val second = longCoffee(name = "Kenia Kiambu AA Lavado Reserva")
    val a = saveBrew(first, 1_700_000_000_000, details = BrewDetails(name = longName))
    val b = saveBrew(second, 1_700_000_100_000, details = BrewDetails(name = longName))
    val recordA = runBlocking { brews.get(a) }!!
    val recordB = runBlocking { brews.get(b) }!!
    capture(
      ShareRequest.Compare(
        CompareShareData(
          a = recordA,
          b = recordB,
          titleA = "$longName · 13 nov 2026, 8:30",
          titleB = "$longName · 12 nov 2026, 17:10",
          footer = footerLines(FooterCoffee(first, longName, "Café de Altura Tostadores", 9), FooterCoffee(second, "Kenia Kiambu AA", "Origen Tostadores", 4)),
        ),
      ),
    )

    assertSize()
    brandInside()
    val height = node("share_chart").size.height
    assertTrue("gráfica de $height px", height >= 432)
    assertNotNull(node("share_table"))
  }
}
