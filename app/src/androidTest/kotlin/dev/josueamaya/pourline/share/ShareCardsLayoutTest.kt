package dev.josueamaya.pourline.share

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.platform.app.InstrumentationRegistry
import dev.josueamaya.pourline.R
import dev.josueamaya.pourline.coffee.CoffeeProcess
import dev.josueamaya.pourline.coffee.CoffeeRepository
import dev.josueamaya.pourline.coffee.PhotoFiles
import dev.josueamaya.pourline.coffee.RoastLevel
import dev.josueamaya.pourline.history.BrewDetails
import dev.josueamaya.pourline.history.BrewMethod
import dev.josueamaya.pourline.history.BrewRepository
import dev.josueamaya.pourline.history.HistoryDatabase
import dev.josueamaya.pourline.history.TestBrews
import dev.josueamaya.pourline.history.UsedCoffee
import dev.josueamaya.pourline.protocol.WeightUnit
import dev.josueamaya.pourline.recipe.Recipe
import dev.josueamaya.pourline.recipe.RecipeStage
import dev.josueamaya.pourline.recipe.StageType
import dev.josueamaya.pourline.recipe.aeroPressWithActions
import dev.josueamaya.pourline.recipe.share.RecipeShareCodec
import dev.josueamaya.pourline.ui.session.formatClock
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

  private fun assertSize(height: Int = 1350) {
    assertEquals(1080, captured!!.width)
    assertEquals(height, captured!!.height)
  }

  private fun node(tag: String) = rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()

  private fun lines(tag: String): Int {
    val results = mutableListOf<TextLayoutResult>()
    node(tag).config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
    return results.single().lineCount
  }

  private fun brandInside(height: Int = 1350) {
    val brand = node("share_brand")
    assertTrue("la marca debe quedar dentro del alto", brand.positionInRoot.y + brand.size.height <= height)
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

  @Test
  fun aScalelessCardFitsWithoutChartOrStability() {
    val coffeeId = longCoffee()
    val id = runBlocking {
      brews.save(
        TestBrews.scalelessFinished(totalTimeMs = 235_000),
        TestBrews.scalelessContext(startedAtEpochMs = 1_700_000_000_000, coffee = UsedCoffee(coffeeId, "x")),
      )
    }
    val record = runBlocking { brews.get(id) }!!
    capture(
      ShareRequest.Brew(
        BrewShareData(
          samples = record.samples,
          summary = record.summary,
          unit = record.entity.unit,
          target = null,
          title = longName,
          subtitle = "13 nov 2026, 8:30 · V60",
          fitScore = null,
          rating = 4,
          notes = longNotes,
          footer = footerLines(FooterCoffee(coffeeId, longName, "Café de Altura Tostadores Especializados de Origen Único", 9)),
          scaleless = true,
          doseG = record.entity.doseG,
        ),
      ),
    )

    assertSize()
    brandInside()
    assertNotNull(node("share_scaleless"))
    assertNotNull(node("share_scaleless_metrics"))
    assertTrue(lines("share_notes") <= 3)
    // Ni gráfica ni estabilidad: la rejilla ocupa su hueco.
    assertEquals(0, rule.onAllNodesWithTag("brew_chart", useUnmergedTree = true).fetchSemanticsNodes().size)
    assertEquals(0, rule.onAllNodesWithTag("share_metrics", useUnmergedTree = true).fetchSemanticsNodes().size)
    assertEquals(0, rule.onAllNodesWithTag("history_row_fit", useUnmergedTree = true).fetchSemanticsNodes().size)
    val grid = node("share_scaleless_metrics")
    assertTrue("la rejilla debe quedar dentro", grid.positionInRoot.y >= 0f && grid.positionInRoot.y + grid.size.height <= 1350)
  }

  // --- Tarjeta de receta ---

  /** [count] pasos que alternan `POUR` y `WAIT`, con nombre en los `POUR` y una instrucción en todos. */
  private fun stages(count: Int, instruction: String? = null): List<RecipeStage> {
    var startMs = 0L
    var water = 0.0
    return List(count) { i ->
      val pour = i % 2 == 0
      if (pour) water += 25.0
      RecipeStage(
        name = if (pour) "Vertido $i" else null,
        startMs = startMs,
        targetWaterG = water,
        durationMs = 20_000,
        type = if (pour) StageType.POUR else StageType.WAIT,
        instruction = instruction,
      ).also { startMs += 25_000 }
    }
  }

  private fun recipe(
    count: Int,
    name: String = "V60 clásica",
    notes: String? = null,
    instruction: String? = null,
    temp: Double? = 93.0,
  ) = Recipe(
    id = 1,
    name = name,
    method = BrewMethod.V60,
    waterTempC = temp,
    notes = notes,
    doseG = 15.0,
    stages = stages(count, instruction),
  )

  private fun recipeData(recipe: Recipe, grindText: String? = "Timemore Chestnut C3 Pro · 24", withQr: Boolean = true): RecipeShareData {
    val uri = RecipeShareCodec.encode(recipe, grinder = null)?.uri
    return RecipeShareData(recipe, grindText, WeightUnit.GRAMS, qr = if (withQr && uri != null) qrMatrix(uri) else null)
  }

  private fun worstCaseData(): Pair<WorstCase, RecipeShareData> {
    val worst = worstCaseRecipe(RecipeShareCodec.MAX_URI_LENGTH)
    return worst to RecipeShareData(worst.recipe, worst.grindText, WeightUnit.GRAMS, qr = qrMatrix(worst.uri))
  }

  private fun fourSixData(): RecipeShareData {
    val recipe = fourSixUserRecipe()
    val uri = RecipeShareCodec.encode(recipe, grinder = null)!!.uri
    return RecipeShareData(recipe, "Femobook A2 · 34", WeightUnit.GRAMS, qr = qrMatrix(uri))
  }

  /** Cada paso queda por encima de [below] (el tag de la franja del QR o de la marca) y no hay un paso más. */
  private fun stepsAbove(below: String, count: Int) {
    val limit = node(below).positionInRoot.y
    for (i in 0 until count) {
      val row = node("share_recipe_step_$i")
      assertTrue("el paso $i debe quedar por encima de $below", row.positionInRoot.y + row.size.height <= limit)
    }
    assertEquals(0, rule.onAllNodesWithTag("share_recipe_step_$count", useUnmergedTree = true).fetchSemanticsNodes().size)
  }

  private fun textOf(tag: String): String =
    node(tag).config.getOrNull(SemanticsProperties.Text)!!.joinToString("") { it.text }

  private fun assertQrBoxIsPlacedOnEvenPixels() {
    val box = node("share_recipe_qr")
    assertEquals("x de la caja del QR", 0f, box.positionInRoot.x % 2f, 0f)
    assertEquals("y de la caja del QR", 0f, box.positionInRoot.y % 2f, 0f)
  }

  @Test
  fun theTotalTimeIsOnTheFactsLineAndTheCardKeepsItsSize() {
    val recipe = recipe(4)
    capture(ShareRequest.Recipe(recipeData(recipe)))

    assertSize()
    brandInside()
    assertEquals(formatClock(recipe.totalDurationMs), textOf("share_recipe_duration"))
    assertEquals(node("share_recipe_ratio").positionInRoot.y, node("share_recipe_duration").positionInRoot.y, 0f)
  }

  @Test
  fun noStepTypeLabelIsCutOnTheRecipeCard() {
    val recipe = aeroPressWithActions()
    assertEquals(StageType.entries.toSet(), recipe.stages.map { it.type }.toSet())
    capture(ShareRequest.Recipe(recipeData(recipe)))

    recipe.stages.indices.forEach { i ->
      val results = mutableListOf<TextLayoutResult>()
      node("share_recipe_step_type_$i").config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
      val layout = results.single()
      assertTrue("la etiqueta del paso $i no debe cortarse", !layout.hasVisualOverflow && !layout.isLineEllipsized(0))
    }
  }

  @Test
  fun aRecipeCardWithActionsKeepsTheMinimumSizeAndShowsTheTotalTime() {
    val recipe = aeroPressWithActions()
    capture(ShareRequest.Recipe(recipeData(recipe)))

    assertSize()
    brandInside()
    stepsAbove("share_recipe_qr_strip", 6)
    val action = context.getString(R.string.step_type_action)
    for (i in listOf(2, 4, 5)) {
      rule.onNode(hasTestTag("share_recipe_step_$i") and hasAnyDescendant(hasText(action)), useUnmergedTree = true).assertExists()
    }
    assertEquals("2:00", textOf("share_recipe_duration"))
  }

  @Test
  fun aShortRecipeCardKeepsTheMinimum1080x1350WithTheQrBelowTheSteps() {
    val data = recipeData(recipe(4))
    capture(ShareRequest.Recipe(data))

    assertSize()
    brandInside()
    val qr = node("share_recipe_qr")
    val side = data.qr!!.cells * 6
    assertEquals("caja de ${qr.size.width} × ${qr.size.height} px", side to side, qr.size.width to qr.size.height)
    assertNotNull(node("share_recipe_qr_caption"))
    stepsAbove("share_recipe_qr_strip", 4)
    val strip = node("share_recipe_qr_strip")
    assertTrue("la franja del QR debe quedar por encima de la marca", strip.positionInRoot.y + strip.size.height <= node("share_brand").positionInRoot.y)
  }

  @Test
  fun theRecipeCardGrowsWithItsContent() {
    val capturer = CardCapturer(rule).also { it.start() }
    val name = randomName(60)
    val heights = listOf(4, 10, 20).map { count -> capturer.capture(ShareRequest.Recipe(recipeData(recipe(count, name = name)))).height }

    assertEquals(1350, heights[0])
    assertTrue("alturas $heights", heights[0] <= heights[1] && heights[1] <= heights[2])
    assertTrue("con 20 pasos: ${heights[2]} px", heights[2] > 1350)
    Log.i("RecipeCard", "alturas con 4, 10 y 20 pasos: $heights")
  }

  @Test
  fun theTallestRecipeCardShowsEveryStepAndTheQr() {
    val (worst, data) = worstCaseData()
    capture(ShareRequest.Recipe(data))

    val height = captured!!.height
    Log.i("RecipeCard", "peor caso: ${worst.uri.length} caracteres, QR de ${data.qr!!.cells} celdas, tarjeta de 1080 × $height")
    assertSize(height)
    assertTrue("alto de $height px", height > 1350)
    brandInside(height)
    assertEquals(2, lines("share_recipe_name"))
    assertEquals(2, lines("share_recipe_grind"))
    stepsAbove("share_recipe_qr_strip", 20)
    val qr = node("share_recipe_qr")
    assertEquals(data.qr.cells * 6, qr.size.width)
    assertEquals(qrMatrix(uriOfLength(RecipeShareCodec.MAX_URI_LENGTH, 0)).cells * 6, qr.size.width)
  }

  @Test
  fun everyQrModuleIs6x6AndTheBoxIsModulesPlus8Times6() {
    val capturer = CardCapturer(rule).also { it.start() }
    val cases = listOf(recipeData(recipe(4)), worstCaseData().second)
    for (data in cases) {
      val bitmap = capturer.capture(ShareRequest.Recipe(data))
      val box = node("share_recipe_qr")
      val matrix = data.qr!!
      assertEquals((matrix.modules + 8) * 6, box.size.width)
      assertEquals((matrix.modules + 8) * 6, box.size.height)
      val left = box.positionInRoot.x.toInt()
      val top = box.positionInRoot.y.toInt()
      assertEquals(android.graphics.Point(left, top), findQrBox(bitmap, 6))
      assertQrModules(bitmap, matrix, left, top, 6)
    }
  }

  @Test
  fun theQrBoxSitsOnEvenPixels() {
    val capturer = CardCapturer(rule).also { it.start() }
    val worst = worstCaseData().second
    val requests = listOf(
      recipeData(recipe(4)),
      recipeData(recipe(4), grindText = null),
      fourSixData(),
      fourSixData().copy(grindText = null),
      worst,
      worst.copy(grindText = null),
    )
    for (data in requests) {
      capturer.capture(ShareRequest.Recipe(data))
      assertQrBoxIsPlacedOnEvenPixels()
    }
  }

  @Test
  fun theHeaderUsesTheFullWidth() {
    capture(ShareRequest.Recipe(recipeData(recipe(4, name = randomName(60)))))

    assertEquals(960, node("share_recipe_name").size.width)
    assertEquals(2, lines("share_recipe_name"))
    assertEquals(960, node("share_recipe_grind").size.width)
  }

  @Test
  fun aRecipeCardWithoutQrHasNeitherQrNorCaption() {
    capture(ShareRequest.Recipe(recipeData(recipe(4), withQr = false)))

    assertSize()
    brandInside()
    assertEquals(0, rule.onAllNodesWithTag("share_recipe_qr", useUnmergedTree = true).fetchSemanticsNodes().size)
    assertEquals(0, rule.onAllNodesWithTag("share_recipe_qr_caption", useUnmergedTree = true).fetchSemanticsNodes().size)
    assertEquals(0, rule.onAllNodesWithTag("share_recipe_qr_strip", useUnmergedTree = true).fetchSemanticsNodes().size)
    assertTrue(node("share_recipe_facts").size.width > 0)
  }

  @Test
  fun theRecipeCardHasNoNotesNorInstructions() {
    capture(ShareRequest.Recipe(recipeData(recipe(4, notes = "NOTAS-DE-LA-RECETA", instruction = "INSTRUCCION-DEL-PASO"))))

    rule.onAllNodesWithText("NOTAS-DE-LA-RECETA", substring = true, useUnmergedTree = true).assertCountEquals(0)
    rule.onAllNodesWithText("INSTRUCCION-DEL-PASO", substring = true, useUnmergedTree = true).assertCountEquals(0)
  }

  @Test
  fun missingFactsAreNotShown() {
    capture(ShareRequest.Recipe(recipeData(recipe(4, temp = null), grindText = null)))

    assertEquals(0, rule.onAllNodesWithTag("share_recipe_temp", useUnmergedTree = true).fetchSemanticsNodes().size)
    assertEquals(0, rule.onAllNodesWithTag("share_recipe_grind", useUnmergedTree = true).fetchSemanticsNodes().size)
    assertNotNull(node("share_recipe_dose"))
    assertNotNull(node("share_recipe_water"))
    assertNotNull(node("share_recipe_ratio"))
  }

  @Test
  fun theQrIsBrandBlueOnWhite() {
    val data = recipeData(recipe(4))
    capture(ShareRequest.Recipe(data))

    val box = node("share_recipe_qr")
    val left = box.positionInRoot.x.toInt()
    val top = box.positionInRoot.y.toInt()
    val side = box.size.width
    // La captura es un bitmap de hardware: no deja leer píxeles hasta copiarlo.
    val bitmap = captured!!.asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false)
    // La zona de silencio (4 módulos) es blanca en las cuatro esquinas, aunque la tarjeta sea clara u oscura.
    for ((x, y) in listOf(2 to 2, side - 3 to 2, 2 to side - 3, side - 3 to side - 3)) {
      assertEquals("esquina ($x, $y)", Color.WHITE, bitmap.getPixel(left + x, top + y))
    }
    val module = (QR_QUIET_ZONE) * 6
    // El módulo de sincronismo (8, 6) es un módulo de datos oscuro: el azul 8 de la paleta.
    val timing = bitmap.getPixel(left + module + 8 * 6 + 3, top + module + 6 * 6 + 3)
    assertTrue("sincronismo: #${Integer.toHexString(timing)}", timing.isNear(SHARE_QR_DARK.toArgb()))
    // El ojo de arriba a la izquierda: anillo en (21, 2) y centro en (21, 21), contados desde su esquina.
    val ring = bitmap.getPixel(left + module + 21, top + module + 2)
    val center = bitmap.getPixel(left + module + 21, top + module + 21)
    assertTrue("anillo: #${Integer.toHexString(ring)}", ring.isNear(SHARE_QR_EYE_RING.toArgb()))
    assertTrue("centro: #${Integer.toHexString(center)}", center.isNear(SHARE_QR_DARK.toArgb()))
    assertTrue(data.qr!!.cells * 6 == side)
  }

  @Test
  fun theEyesAreRoundedWithTheBrandColors() {
    val capturer = CardCapturer(rule).also { it.start() }
    for (data in listOf(recipeData(recipe(4)), worstCaseData().second)) {
      val bitmap = capturer.capture(ShareRequest.Recipe(data))
      val box = node("share_recipe_qr")
      val left = box.positionInRoot.x.toInt()
      val top = box.positionInRoot.y.toInt()
      val white = Color.WHITE
      val ring = SHARE_QR_EYE_RING.toArgb()
      val dark = SHARE_QR_DARK.toArgb()
      // Sondas en píxeles desde la esquina de cada ojo, a más de 1 px de cualquier curva (D53).
      val probes = listOf(
        Triple(0 to 0, white, "esquina del anillo"), Triple(41 to 0, white, "esquina del anillo"),
        Triple(0 to 41, white, "esquina del anillo"), Triple(41 to 41, white, "esquina del anillo"),
        Triple(6 to 6, ring, "esquina del hueco"), Triple(35 to 35, ring, "esquina del hueco"),
        Triple(12 to 12, white, "esquina del centro"),
        Triple(21 to 2, ring, "anillo"), Triple(21 to 9, white, "hueco"), Triple(21 to 21, dark, "centro"),
      )
      for ((ex, ey) in data.qr!!.eyes) {
        val originX = left + (ex + QR_QUIET_ZONE) * 6
        val originY = top + (ey + QR_QUIET_ZONE) * 6
        for ((point, expected, what) in probes) {
          val actual = bitmap.getPixel(originX + point.first, originY + point.second)
          assertTrue(
            "ojo ($ex, $ey), $what $point: #${Integer.toHexString(actual)} debía ser #${Integer.toHexString(expected)}",
            actual.isNear(expected),
          )
        }
      }
    }
  }

  @Test
  fun aDeletedGrinderShowsItsLabel() {
    capture(ShareRequest.Recipe(recipeData(recipe(4), grindText = "Molino viejo · 18")))

    assertTrue(textOf("share_recipe_grind").contains("Molino viejo · 18"))
  }

  @Test
  fun aLiveGrinderShowsTheEditorLabel() {
    capture(ShareRequest.Recipe(recipeData(recipe(4), grindText = "Chestnut C3 Pro · 24")))

    assertTrue(textOf("share_recipe_grind").contains("Chestnut C3 Pro · 24"))
  }

  private fun randomName(length: Int) = "Ñandú Reserva Especial ".repeat(4).take(length)
}
