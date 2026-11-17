package dev.josueamaya.pourline.ui.grinders

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import dev.josueamaya.pourline.grinder.Grinder
import dev.josueamaya.pourline.grinder.GrindUnit
import dev.josueamaya.pourline.grinder.GrindUnitLevel
import dev.josueamaya.pourline.grinder.TestGrinders
import dev.josueamaya.pourline.grinder.addGrinder
import dev.josueamaya.pourline.ui.TestApp
import dev.josueamaya.pourline.ui.TestAppDependencies
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** El convertidor suelto (Ajustes → Molinos → icono): origen, método, ajuste y una fila por cada molino mío. */
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
  fun chooseASourceAMethodAndASettingAndEachOfMyOtherGrindersGetsARow() {
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

    ui.choose("converter_method", "V60")
    ui.click("converter_value_0_inc") // el primer toque fija el valor inicial: 20 (centro del rango V60 de Uno)

    ui.waitFor("converter_row_$dos")
    assertTrue(ui.exists("converter_row_$tres"))
    assertFalse(ui.exists("converter_row_$uno"))
    // Uno V60 16–24 en 20 es el 50 %; Dos V60 20–36 → 28 y Tres V60 60–100 → 80 (2 rotaciones, 0 números, 0 clics).
    text("converter_value_$dos").assertTextContains("28", substring = true)
    text("converter_value_$tres").assertTextContains("2 · 0 · 0", substring = true)
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
    ui.click("converter_fix_$bareId")
    ui.waitFor("grinder_edit_save")
  }
}
