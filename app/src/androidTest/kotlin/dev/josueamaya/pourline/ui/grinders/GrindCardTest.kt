package dev.josueamaya.pourline.ui.grinders

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.platform.app.InstrumentationRegistry
import dev.josueamaya.pourline.R
import dev.josueamaya.pourline.grinder.Grinder
import dev.josueamaya.pourline.grinder.GrindSetting
import dev.josueamaya.pourline.grinder.GrindUnit
import dev.josueamaya.pourline.grinder.GrindUnitLevel
import dev.josueamaya.pourline.grinder.TestGrinders
import dev.josueamaya.pourline.grinder.addCalibration
import dev.josueamaya.pourline.grinder.addGrinder
import dev.josueamaya.pourline.history.BrewMethod
import dev.josueamaya.pourline.recipe.Recipe
import dev.josueamaya.pourline.recipe.RecipeStage
import dev.josueamaya.pourline.settings.ExperienceMode
import dev.josueamaya.pourline.settings.ReferenceKey
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

/** La tarjeta de molienda de Preparar con Room en memoria: Básico y Experto, "Moler con", la fuente y los casos sin ajuste. */
class GrindCardTest {
  @get:Rule
  val rule = createAndroidComposeRule<ComponentActivity>()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private val env = TestAppDependencies(context)
  private val ui = GrindersUi(rule, env)

  @After
  fun tearDown() = env.close()

  private fun recipe(grind: GrindSetting?, method: BrewMethod = BrewMethod.V60, name: String = "Mi V60") = runBlocking {
    env.recipes.save(Recipe(name = name, method = method, grind = grind, doseG = 15.0, stages = listOf(RecipeStage("Bloom", 0, 40.0, 10_000))))
  }

  private fun show(recipeId: Long?, mode: ExperienceMode = ExperienceMode.BASIC) {
    env.skipOnboarding(mode)
    rule.setContent { TestApp(env) }
    if (recipeId != null) {
      rule.runOnUiThread { env.scaleSession.selectTarget(ReferenceKey.Recipe(recipeId)) }
    }
    ui.waitFor("grind_card")
  }

  private fun text(tag: String) = ui.node(tag, unmerged = true)

  @Test
  fun basicHasNoRulerAndExpertHasIt() {
    val uno = runBlocking { env.db.addGrinder(TestGrinders.uno) }
    val id = recipe(GrindSetting(uno, 20.0, "x"))
    show(id, ExperienceMode.BASIC)
    ui.waitFor("grind_card_value")
    assertFalse(ui.exists("grind_card_ruler"))
  }

  @Test
  fun expertShowsTheRulerOfTheChosenGrinder() {
    val uno = runBlocking { env.db.addGrinder(TestGrinders.uno) }
    val id = recipe(GrindSetting(uno, 20.0, "x"))
    show(id, ExperienceMode.EXPERT)
    ui.waitFor("grind_card_ruler")
  }

  @Test
  fun theRecipeGrinderIsPreselectedAndShownAsIs() {
    val uno = runBlocking { env.db.addGrinder(TestGrinders.uno) }
    runBlocking { env.db.addGrinder(TestGrinders.dos, 2_000L) }
    val id = recipe(GrindSetting(uno, 20.0, "x"))
    show(id)
    ui.waitFor("grind_card_value")
    ui.node("grind_card_source").assertTextContains("Uno · 20", substring = true)
    ui.node("grind_card_grinder").assertTextContains("Acme Uno", substring = true)
    text("grind_card_value").assertTextContains("20", substring = true)
  }

  @Test
  fun changingGrindWithShowsTheConvertedSetting() {
    val uno = runBlocking { env.db.addGrinder(TestGrinders.uno) }
    runBlocking { env.db.addGrinder(TestGrinders.dos, 2_000L) }
    val id = recipe(GrindSetting(uno, 20.0, "x"))
    show(id)
    ui.waitFor("grind_card_value")

    ui.choose("grind_card_grinder", "Acme Dos")
    // Uno 20 está en MEDIUM (nivel 3) y Dos tiene MEDIUM en 30: la conversión es por curvas de grosor, no por el rango V60.
    rule.waitUntil(5_000) { runCatching { text("grind_card_value").assertTextContains("30", substring = true) }.isSuccess }
    assertFalse(ui.exists("grind_card_ruler"))
    assertEquals(30.0, env.grindSelection.current()!!.value!!, 0.0)
  }

  @Test
  fun twoPairsShowPerYourCalibration() {
    val uno = runBlocking { env.db.addGrinder(TestGrinders.uno) }
    val dos = runBlocking { env.db.addGrinder(TestGrinders.dos, 2_000L) }
    runBlocking {
      env.db.addCalibration(uno, 10.0, dos, 14.0)
      env.db.addCalibration(uno, 30.0, dos, 50.0)
    }
    val id = recipe(GrindSetting(uno, 20.0, "x"))
    show(id)
    ui.waitFor("grind_card_value")

    ui.choose("grind_card_grinder", "Acme Dos")
    // Uno 10 = Dos 14 y Uno 30 = Dos 50: Uno 20 queda a mitad, 32.
    rule.waitUntil(5_000) { runCatching { text("grind_card_value").assertTextContains("32", substring = true) }.isSuccess }
    assertTrue(ui.exists("grind_card_marks"))
    assertTrue(rule.onAllNodesWithText(context.getString(R.string.grind_card_calibrated)).fetchSemanticsNodes().isNotEmpty())
    assertEquals(32.0, env.grindSelection.current()!!.value!!, 0.0)
  }

  @Test
  fun onePairWithoutACurveOffersToAddAnotherEquivalence() {
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
    runBlocking { env.db.addCalibration(uno, 20.0, bareId, 10.0) }
    val id = recipe(GrindSetting(uno, 25.0, "x"))
    show(id)
    ui.waitFor("grind_card_value")
    ui.choose("grind_card_grinder", "Acme Vacío")
    ui.waitFor("grind_card_fix")
    ui.node("grind_card_fix").assertTextContains(context.getString(R.string.grind_card_fix_calibration), substring = true)
    ui.click("grind_card_fix")
    // El detalle del molino elegido, con la sección "Equivalencias" ya a la vista.
    ui.waitFor("grinder_calibrations")
    ui.node("grinder_calibrations").assertIsDisplayed()
  }

  @Test
  fun withoutOwnedGrindersItShowsTheLinkAndNoSelector() {
    val foreign = runBlocking { env.db.addGrinder(TestGrinders.uno.copy(owned = false)) }
    val id = recipe(GrindSetting(foreign, 20.0, "x"))
    show(id)
    ui.waitFor("grind_card_add")
    assertFalse(ui.exists("grind_card_grinder"))
    assertTrue(ui.exists("grind_card_source"))
  }

  @Test
  fun theLinkOpensMyGrinders() {
    val id = recipe(null)
    show(id)
    ui.waitFor("grind_card_add")
    ui.click("grind_card_add")
    ui.waitFor("grinders_empty")
  }

  @Test
  fun aRecipeWithoutGrindSuggestsTheMiddleOfTheRangeOrSaysNothing() {
    runBlocking { env.db.addGrinder(TestGrinders.uno) }
    val id = recipe(null)
    show(id)
    ui.waitFor("grind_card_value")
    // Uno V60 16–24 → 20, "sugerido".
    text("grind_card_value").assertTextContains("20", substring = true)
    assertTrue(rule.onAllNodesWithText(context.getString(R.string.grind_card_suggested)).fetchSemanticsNodes().isNotEmpty())
    rule.onAllNodesWithText(context.getString(R.string.grind_card_recipe_none)).fetchSemanticsNodes().let { assertTrue(it.isNotEmpty()) }
  }

  @Test
  fun noEquivalenceOffersToCompleteTheRangesOfMyGrinder() {
    val uno = runBlocking { env.db.addGrinder(TestGrinders.uno) }
    val bare = Grinder(
      brand = "Acme",
      model = "Vacío",
      units = listOf(GrindUnitLevel(GrindUnit.CLICKS)),
      scaleMin = 0.0,
      scaleMax = 40.0,
      owned = true,
    )
    runBlocking { env.db.addGrinder(bare, 2_000L) }
    val id = recipe(GrindSetting(uno, 20.0, "x"))
    show(id)
    ui.waitFor("grind_card_value")
    ui.choose("grind_card_grinder", "Acme Vacío")
    ui.waitFor("grind_card_fix")
    text("grind_card_value").assertTextContains(context.getString(R.string.grind_card_no_equivalent), substring = true)
    ui.click("grind_card_fix")
    ui.waitFor("grinder_edit_save")
  }

  @Test
  fun aDeletedGrinderOnTheRecipeCannotBeConverted() {
    runBlocking { env.db.addGrinder(TestGrinders.uno) }
    val id = recipe(GrindSetting(null, 30.0, "Chestnut S3 · 30 clics"))
    show(id)
    ui.waitFor("grind_card_orphan")
    assertTrue(ui.exists("grind_card_source"))
  }

  @Test
  fun aPredefinedGrinderShowsItsSource() {
    env.seedGrinders()
    val uno = runBlocking { env.grinders.catalog().first().single { it.model == "Uno" }.id }
    runBlocking { env.grinders.setOwned(uno, true) }
    val id = recipe(GrindSetting(uno, 20.0, "x"))
    show(id)
    ui.waitFor("grind_card_attribution")
    assertTrue(ui.exists("grind_card_attribution_link_acme-uno"))
  }
}
