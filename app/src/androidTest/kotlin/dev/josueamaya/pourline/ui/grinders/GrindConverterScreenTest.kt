package dev.josueamaya.pourline.ui.grinders

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import dev.josueamaya.pourline.R
import dev.josueamaya.pourline.grinder.Grinder
import dev.josueamaya.pourline.grinder.GrindUnit
import dev.josueamaya.pourline.grinder.GrindUnitLevel
import dev.josueamaya.pourline.grinder.TestGrinders
import dev.josueamaya.pourline.grinder.addCalibration
import dev.josueamaya.pourline.grinder.addGrinder
import dev.josueamaya.pourline.ui.TestApp
import dev.josueamaya.pourline.ui.TestAppDependencies
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** El convertidor suelto (Ajustes → Molinos → icono): origen, ajuste, una fila por cada molino mío y "Corregir". */
class GrindConverterScreenTest {
  @get:Rule
  val rule = createAndroidComposeRule<ComponentActivity>()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private val env = TestAppDependencies(context)
  private val ui = GrindersUi(rule, env)

  @After
  fun tearDown() = env.close()

  private fun openConverter() {
    env.skipOnboarding()
    rule.setContent { TestApp(env) }
    ui.openGrinders()
    ui.click("grinders_converter")
    ui.waitFor("converter")
  }

  private fun text(tag: String) = ui.node(tag, unmerged = true)

  @Test
  fun withoutMyGrindersItShowsTheEmptyStateWithTheLink() {
    openConverter()
    ui.waitFor("converter_empty")
    ui.click("converter_empty_action")
    ui.waitFor("grinders_empty")
  }

  @Test
  fun chooseASourceAndASettingAndEachOfMyOtherGrindersGetsARow() {
    val uno = runBlocking { env.db.addGrinder(TestGrinders.uno) }
    val dos = runBlocking { env.db.addGrinder(TestGrinders.dos, 2_000L) }
    val tres = runBlocking { env.db.addGrinder(TestGrinders.tres, 3_000L) }
    openConverter()
    ui.waitFor("converter_hint_source")

    ui.click("converter_source")
    ui.waitFor("grinder_picker")
    ui.click("grinder_picker_row_$uno")
    ui.waitFor("converter_value_0_inc")
    ui.waitFor("converter_hint_value")

    ui.click("converter_value_0_inc") // el primer toque fija el valor inicial: 20 (centro de la escala de Uno)

    ui.waitFor("converter_row_$dos")
    assertTrue(ui.exists("converter_row_$tres"))
    assertFalse(ui.exists("converter_row_$uno"))
    // Sin método, por las curvas: Uno 20 es MEDIUM, que en Dos es 30 y en Tres es 100 (2 rotaciones, 2 números, 0 clics).
    text("converter_value_$dos").assertTextContains("30", substring = true)
    text("converter_value_$tres").assertTextContains("2 · 2 · 0", substring = true)
    // Solo molinos propios: sin fuente.
    assertFalse(ui.exists("converter_attribution"))
  }

  @Test
  fun thePickerOffersMyGrindersAndTheCatalogWithoutRetiredOnes() {
    env.seedGrinders()
    val mine = runBlocking { env.db.addGrinder(TestGrinders.uno) }
    openConverter()
    ui.click("converter_source")
    ui.waitFor("grinder_picker")
    assertTrue(ui.exists("grinder_picker_owned"))
    assertTrue(ui.exists("grinder_picker_catalog"))
    assertTrue(ui.exists("grinder_picker_row_$mine"))
    val catalog = runBlocking { env.grinders.catalog().first() }
    assertTrue(catalog.isNotEmpty())
    catalog.forEach { assertTrue(ui.exists("grinder_picker_row_${it.id}")) }
  }

  @Test
  fun changingTheSourceClearsTheSetting() {
    val uno = runBlocking { env.db.addGrinder(TestGrinders.uno) }
    val dos = runBlocking { env.db.addGrinder(TestGrinders.dos, 2_000L) }
    openConverter()
    ui.click("converter_source")
    ui.waitFor("grinder_picker")
    ui.click("grinder_picker_row_$uno")
    ui.waitFor("converter_value_0_inc")
    ui.click("converter_value_0_inc")
    ui.waitFor("converter_row_$dos")

    ui.click("converter_source")
    ui.waitFor("grinder_picker")
    ui.click("grinder_picker_row_$dos")
    ui.waitFor("converter_hint_value")
    assertFalse(ui.exists("converter_row_$uno"))
  }

  @Test
  fun aPredefinedGrinderShowsTheSourceAndNoEquivalenceOffersTheFix() {
    env.seedGrinders()
    val uno = runBlocking { env.grinders.catalog().first().single { it.model == "Uno" }.id }
    runBlocking { env.grinders.setOwned(uno, true) }
    val bare = Grinder(
      brand = "Acme",
      model = "Vacío",
      units = listOf(GrindUnitLevel(GrindUnit.CLICKS)),
      scaleMin = 0.0,
      scaleMax = 40.0,
      owned = true,
    )
    val bareId = runBlocking { env.db.addGrinder(bare, 2_000L) }
    openConverter()
    ui.click("converter_source")
    ui.waitFor("grinder_picker")
    ui.click("grinder_picker_row_$uno")
    ui.waitFor("converter_value_0_inc")
    ui.click("converter_value_0_inc")

    ui.waitFor("converter_row_$bareId")
    ui.waitFor("converter_attribution")
    assertTrue(ui.exists("converter_attribution_link_acme-uno"))
    ui.waitFor("converter_fix_$bareId")
    // El origen es mío: la fila también ofrece añadir una equivalencia.
    assertTrue(ui.exists("converter_correct_$bareId"))
    ui.click("converter_fix_$bareId")
    ui.waitFor("grinder_edit_save")
  }

  /** Abre el convertidor con [source] elegido y el primer toque dado (el valor inicial: el centro de la escala). */
  private fun startFrom(source: Long, row: Long) {
    openConverter()
    ui.click("converter_source")
    ui.waitFor("grinder_picker")
    ui.click("grinder_picker_row_$source")
    ui.waitFor("converter_value_0_inc")
    ui.click("converter_value_0_inc")
    ui.waitFor("converter_row_$row")
  }

  private fun pairs(grinder: Long) = runBlocking { env.grinders.calibrations(grinder).first() }.map { it.valueOf(grinder) to it.valueOf(it.otherId(grinder)) }.sortedBy { it.first }

  @Test
  fun correctSavesAPairAndTheRowShowsTheCorrectedValue() {
    val uno = runBlocking { env.db.addGrinder(TestGrinders.uno) }
    val dos = runBlocking { env.db.addGrinder(TestGrinders.dos, 2_000L) }
    startFrom(uno, dos)
    text("converter_value_$dos").assertTextContains("30", substring = true)
    ui.node("converter_correct_$dos").assertTextContains(context.getString(R.string.converter_correct))

    ui.click("converter_correct_$dos")
    ui.waitFor("calibration_sheet")
    // El origen viene fijo: no hay selector de molino ni el ajuste de Uno para cambiar.
    assertTrue(ui.exists("calibration_source"))
    assertFalse(ui.exists("calibration_grinder"))
    assertFalse(ui.exists("calibration_value_0_inc"))
    ui.click("calibration_other_value_0_inc")
    ui.click("calibration_other_value_0_inc") // medios clics: 30 → 31
    ui.click("calibration_save")
    ui.waitGone("calibration_sheet")

    assertEquals(listOf(20.0 to 31.0), pairs(uno))
    rule.waitUntil(5_000) { runCatching { text("converter_value_$dos").assertTextContains("31", substring = true) }.isSuccess }
  }

  @Test
  fun twoPairsGivePerYourCalibrationInBetween() {
    val uno = runBlocking { env.db.addGrinder(TestGrinders.uno) }
    val dos = runBlocking { env.db.addGrinder(TestGrinders.dos, 2_000L) }
    runBlocking {
      env.db.addCalibration(uno, 10.0, dos, 14.0)
      env.db.addCalibration(uno, 30.0, dos, 50.0)
    }
    startFrom(uno, dos)
    // Entre los dos pares: 14 + (20 − 10) / 20 × 36 = 32.
    rule.waitUntil(5_000) { runCatching { text("converter_value_$dos").assertTextContains("32", substring = true) }.isSuccess }
    ui.node("converter_row_$dos").assertTextContains(context.getString(R.string.grind_card_calibrated), substring = true)
  }

  @Test
  fun aContradictingCorrectionShowsTheConflictAndReplaceSavesIt() {
    val uno = runBlocking { env.db.addGrinder(TestGrinders.uno) }
    val tres = runBlocking { env.db.addGrinder(TestGrinders.tres, 3_000L) }
    runBlocking {
      env.db.addCalibration(uno, 10.0, tres, 40.0)
      env.db.addCalibration(uno, 30.0, tres, 120.0)
    }
    startFrom(uno, tres)
    ui.click("converter_correct_$tres")
    ui.waitFor("calibration_sheet")
    // Las rotaciones de Tres valen 40 clics: de 80 a 160, que pasa por encima de Uno 30 = Tres 120.
    ui.click("calibration_other_value_0_inc")
    ui.click("calibration_other_value_0_inc")
    ui.click("calibration_save")
    ui.waitFor("calibration_conflict")
    assertTrue(ui.exists("calibration_sheet"))
    assertEquals(listOf(10.0 to 40.0, 30.0 to 120.0), pairs(uno))

    ui.click("calibration_replace")
    ui.waitGone("calibration_sheet")
    assertEquals(listOf(10.0 to 40.0, 20.0 to 160.0), pairs(uno))
    // 160 son 4 rotaciones.
    rule.waitUntil(5_000) { runCatching { text("converter_value_$tres").assertTextContains("4 · 0 · 0", substring = true) }.isSuccess }
  }

  @Test
  fun aRepeatedValueShowsEquivalenceUpdated() {
    val uno = runBlocking { env.db.addGrinder(TestGrinders.uno) }
    val dos = runBlocking { env.db.addGrinder(TestGrinders.dos, 2_000L) }
    runBlocking { env.db.addCalibration(uno, 20.0, dos, 31.0) }
    startFrom(uno, dos)
    ui.click("converter_correct_$dos")
    ui.waitFor("calibration_sheet")
    ui.click("calibration_other_value_0_inc") // 31 → 31,5
    ui.click("calibration_save")
    ui.waitGone("calibration_sheet")

    rule.waitUntil(5_000) {
      rule.onAllNodesWithText(context.getString(R.string.grind_calibration_updated)).fetchSemanticsNodes().isNotEmpty()
    }
    assertEquals(listOf(20.0 to 31.5), pairs(uno))
  }

  @Test
  fun invalidValuesKeepTheSheetOpenWithTheNotice() {
    val uno = runBlocking { env.db.addGrinder(TestGrinders.uno) }
    val dos = runBlocking { env.db.addGrinder(TestGrinders.dos, 2_000L) }
    startFrom(uno, dos)
    ui.click("converter_correct_$dos")
    ui.waitFor("calibration_sheet")
    // Dos se borra mientras la hoja está abierta: el par ya no es válido.
    runBlocking { env.grinders.delete(dos) }
    ui.click("calibration_save")
    ui.waitFor("calibration_invalid")
    assertTrue(ui.exists("calibration_sheet"))
    assertEquals(emptyList<Pair<Double, Double>>(), pairs(uno))
  }

  @Test
  fun noCorrectWhenTheSourceIsNotMine() {
    env.seedGrinders()
    val catalogUno = runBlocking { env.grinders.catalog().first().single { it.model == "Uno" }.id }
    val dos = runBlocking { env.db.addGrinder(TestGrinders.dos, 2_000L) }
    startFrom(catalogUno, dos)
    assertFalse(ui.exists("converter_correct_$dos"))
  }

  @Test
  fun noEquivalenceOffersAddEquivalenceWhenTheSourceIsMine() {
    val uno = runBlocking { env.db.addGrinder(TestGrinders.uno) }
    val bare = Grinder(
      brand = "Acme",
      model = "Vacío",
      units = listOf(GrindUnitLevel(GrindUnit.CLICKS)),
      scaleMin = 0.0,
      scaleMax = 40.0,
      owned = true,
    )
    val bareId = runBlocking { env.db.addGrinder(bare, 2_000L) }
    runBlocking { env.db.addCalibration(uno, 10.0, bareId, 10.0) }
    startFrom(uno, bareId)
    // Un solo par y sin curva en Vacío: sin equivalencia. "Añadir equivalencia" hace el trabajo del enlace, que no sale.
    text("converter_value_$bareId").assertTextContains(context.getString(R.string.grind_card_no_equivalent))
    ui.node("converter_correct_$bareId").assertTextContains(context.getString(R.string.grind_calibration_add))
    assertFalse(ui.exists("converter_fix_$bareId"))

    ui.click("converter_correct_$bareId")
    ui.waitFor("calibration_sheet")
    ui.click("calibration_save") // Uno 20 = Vacío 20 (el centro de su escala)
    ui.waitGone("calibration_sheet")
    // Con dos pares ya hay equivalencia, exacta sobre un par.
    rule.waitUntil(5_000) { runCatching { text("converter_value_$bareId").assertTextContains("20", substring = true) }.isSuccess }
    assertEquals(listOf(10.0 to 10.0, 20.0 to 20.0), pairs(uno))
  }
}
