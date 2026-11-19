package dev.josueamaya.pourline.ui.prepare

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.platform.app.InstrumentationRegistry
import dev.josueamaya.pourline.R
import dev.josueamaya.pourline.coffee.CoffeeDraft
import dev.josueamaya.pourline.grinder.TestGrinders
import dev.josueamaya.pourline.grinder.addGrinder
import dev.josueamaya.pourline.scale.ConnectionState
import dev.josueamaya.pourline.session.ScaleTarget
import dev.josueamaya.pourline.settings.ReferenceKey
import dev.josueamaya.pourline.sim.SimulatedScaleConnection
import dev.josueamaya.pourline.ui.ScriptedConnection
import dev.josueamaya.pourline.ui.TestApp
import dev.josueamaya.pourline.ui.TestAppDependencies
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Preparar con Room en memoria, preferencias propias y la báscula simulada: la tarjeta, la hoja y la configuración. */
class PrepareScreenTest {
  @get:Rule
  val rule = createComposeRule()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private lateinit var env: TestAppDependencies

  @Before
  fun setUp() {
    env = TestAppDependencies(context)
    env.skipOnboarding()
  }

  @After
  fun tearDown() = env.close()

  private fun show() = rule.setContent { TestApp(env) }

  private fun exists(tag: String) = rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

  private fun waitFor(tag: String) = rule.waitUntil(5_000) { exists(tag) }

  private fun connectSimulated() {
    rule.onNodeWithTag("scale_card_connect").performClick()
    waitFor("connect_simulated")
    rule.onNodeWithTag("connect_simulated").performClick()
    rule.waitUntil(10_000) { env.scaleSession.ui.value.connection == ConnectionState.Ready }
    waitFor("scale_card_menu")
  }

  /** El texto del estado está en un hijo de la tarjeta: la tarjeta no fusiona su semántica. */
  private fun assertCardHas(text: String) {
    rule.onNodeWithTag("scale_card").assert(hasAnyDescendant(hasText(text)))
  }

  private fun recipeName() = runBlocking { env.recipes.list().first().first { it.name.contains("V60") }.name }

  // --- la tarjeta de báscula ---

  @Test
  fun withoutPermissionsTheCardAsksForThemWithoutTheSimulatedScale() {
    env.bluetooth.permissions = false
    show()

    assertCardHas(context.getString(R.string.scale_card_no_permission))
    rule.onNodeWithTag("scale_card_grant").assertIsDisplayed()
    // La simulada solo se ofrece en la hoja de conexión.
    assertTrue(!exists("scale_card_simulated"))
  }

  @Test
  fun withBluetoothOffTheCardOffersToTurnItOnWithoutTheSimulatedScale() {
    env.bluetooth.on.value = false
    show()

    assertCardHas(context.getString(R.string.scale_card_bluetooth_off))
    rule.onNodeWithTag("scale_card_enable").assertIsDisplayed()
    assertTrue(!exists("scale_card_simulated"))
  }

  @Test
  fun withoutAScaleTheCardOffersToConnect() {
    show()

    assertCardHas(context.getString(R.string.scale_card_connect_prompt))
    rule.onNodeWithTag("scale_card_connect").assertIsDisplayed()
  }

  @Test
  fun connectingShowsTheNameAndTheStep() {
    env.connectionFor = { ScriptedConnection(hang = true) }
    show()
    rule.onNodeWithTag("scale_card_connect").performClick()
    waitFor("connect_simulated")
    rule.onNodeWithTag("connect_simulated").performClick()

    waitFor("scale_card_step")
    val name = env.scaleSession.target.value!!.name
    assertCardHas(context.getString(R.string.scale_card_connecting, name))
    rule.onNodeWithTag("scale_card_step").assert(hasText(context.getString(R.string.conn_bonding)))
    rule.onNodeWithTag("prepare_start").assertIsNotEnabled()
  }

  @Test
  fun aFailedConnectionOffersToRetryAndToChangeScale() {
    env.connectionFor = { ScriptedConnection(hang = false) }
    show()
    rule.onNodeWithTag("scale_card_connect").performClick()
    waitFor("connect_simulated")
    rule.onNodeWithTag("connect_simulated").performClick()

    waitFor("scale_card_retry")
    val name = env.scaleSession.target.value!!.name
    assertCardHas(context.getString(R.string.scale_card_failed, name))
    rule.onNodeWithTag("scale_card_change").assertIsDisplayed()

    env.connectionFor = { SimulatedScaleConnection() }
    rule.onNodeWithTag("scale_card_retry").performClick()
    rule.waitUntil(10_000) { env.scaleSession.ui.value.connection == ConnectionState.Ready }
  }

  @Test
  fun aConnectedScaleShowsItsNameAndAMenuToDisconnectAndChange() {
    show()
    connectSimulated()
    val name = env.scaleSession.target.value!!.name
    assertCardHas(context.getString(R.string.scale_card_connected, name))

    rule.onNodeWithTag("scale_card_menu").performClick()
    rule.onNodeWithTag("scale_card_disconnect").assertIsDisplayed()
    rule.onNodeWithTag("scale_card_change").assertIsDisplayed()
    rule.onNodeWithTag("scale_card_disconnect").performClick()

    waitFor("scale_card_connect")
    assertCardHas(context.getString(R.string.scale_card_disconnected, name))
    assertTrue(env.scaleSession.userDisconnected)
    rule.onNodeWithTag("prepare_start").assertIsNotEnabled()
  }

  // --- la hoja de conexión ---

  @Test
  fun theSheetOffersSearchShowAllAndTheSimulatedScale() {
    show()
    rule.onNodeWithTag("scale_card_connect").performClick()

    waitFor("connect_simulated")
    rule.onNodeWithTag("connect_scan_toggle").assertIsDisplayed()
    rule.onNodeWithText(context.getString(R.string.scan_show_all)).assertIsDisplayed()
    rule.onNodeWithTag("connect_simulated").assertIsDisplayed()
    // El escaneo empieza al abrir y la báscula que encuentra se lista.
    rule.waitUntil(5_000) { rule.onAllNodesWithText("DOT-A1B2").fetchSemanticsNodes().isNotEmpty() }
  }

  @Test
  fun choosingTheSimulatedScaleClosesTheSheetAndConnects() {
    show()
    connectSimulated()

    assertTrue(!exists("connection_sheet"))
    assertEquals(1, env.connectionsMade.size)
    assertTrue(env.connectionsMade.single() is ScaleTarget.Simulated)
  }

  @Test
  fun choosingADeviceConnectsToItAndRemembersItWhenReady() {
    show()
    rule.onNodeWithTag("scale_card_connect").performClick()
    rule.waitUntil(5_000) { rule.onAllNodesWithText("DOT-A1B2").fetchSemanticsNodes().isNotEmpty() }

    rule.onNodeWithText("DOT-A1B2").performClick()

    rule.waitUntil(10_000) { env.scaleSession.ui.value.connection == ConnectionState.Ready }
    assertTrue(!exists("connection_sheet"))
    assertEquals(ScaleTarget.Real("AA:BB:CC:DD:EE:01", "DOT-A1B2"), env.connectionsMade.single())
    assertEquals("DOT-A1B2", env.connectionSettings.lastScale.value?.name)
  }

  // --- empezar ---

  @Test
  fun startIsDisabledUntilTheScaleIsReadyAndExplainsWhy() {
    show()

    rule.onNodeWithTag("prepare_start").assertIsNotEnabled()
    rule.onNodeWithTag("prepare_start_hint").assert(hasText(context.getString(R.string.prepare_start_disabled)))

    connectSimulated()

    rule.onNodeWithTag("prepare_start").assertIsEnabled()
    assertTrue(!exists("prepare_start_hint"))
  }

  // --- empezar sin báscula (SPEC-007) ---

  private fun pickV60() {
    rule.onNodeWithTag("target_chip").performClick()
    waitFor("target_picker")
    rule.onNodeWithText(recipeName()).performClick()
    rule.waitUntil(5_000) { env.scaleSession.ui.value.target != null }
  }

  @Test
  fun theScalelessStartIsVisibleAndEnabledWithARecipeAndNoScale() {
    show()
    pickV60()

    rule.onNodeWithTag("prepare_start_scaleless").performScrollTo().assertIsDisplayed()
    rule.onNodeWithTag("prepare_start_scaleless").assertIsEnabled()
    rule.onNodeWithTag("prepare_start_scaleless").assert(hasText(context.getString(R.string.prepare_start_scaleless)))
    // "Empezar" sigue necesitando la báscula, y su ayuda se mantiene.
    rule.onNodeWithTag("prepare_start").assertIsNotEnabled()
    rule.onNodeWithTag("prepare_start_hint").assert(hasText(context.getString(R.string.prepare_start_disabled)))
  }

  @Test
  fun theScalelessStartIsVisibleWithTheScaleReady() {
    show()
    connectSimulated()

    rule.onNodeWithTag("prepare_start_scaleless").performScrollTo().assertIsDisplayed()
    rule.onNodeWithTag("prepare_start_scaleless").assertIsEnabled()
    rule.onNodeWithTag("prepare_start").assertIsEnabled()
  }

  @Test
  fun theScalelessStartIsDisabledWithAnInvalidFreeDoseOrRatioAndEnabledAgainWhenFixed() {
    show()
    rule.onNodeWithTag("prepare_start_scaleless").assertIsEnabled()

    rule.onNodeWithTag("prepare_dose").performTextReplacement("80")
    rule.onNodeWithTag("prepare_start_scaleless").assertIsNotEnabled()
    rule.onNodeWithTag("prepare_dose").performTextReplacement("15")
    rule.onNodeWithTag("prepare_start_scaleless").assertIsEnabled()

    rule.onNodeWithTag("prepare_ratio").performTextReplacement("25")
    rule.onNodeWithTag("prepare_start_scaleless").assertIsNotEnabled()
    rule.onNodeWithTag("prepare_ratio").performTextReplacement("15,5")
    rule.onNodeWithTag("prepare_start_scaleless").assertIsEnabled()

    rule.onNodeWithTag("prepare_dose").performTextReplacement("15.55")
    rule.onNodeWithTag("prepare_start_scaleless").assertIsNotEnabled()
  }

  @Test
  fun theScalelessStartOpensTheSessionReadyToStart() {
    show()
    pickV60()

    rule.onNodeWithTag("prepare_start_scaleless").performScrollTo().performClick()

    waitFor("scaleless_start")
    assertTrue(env.scaleSession.ui.value.scaleless)
    assertTrue(!exists("session_prep"))
    assertTrue(env.connectionsMade.isEmpty())
  }

  @Test
  fun aFreeScalelessStartUsesTheTypedDoseAndRatio() {
    show()
    rule.onNodeWithTag("prepare_dose").performTextReplacement("18")
    rule.onNodeWithTag("prepare_ratio").performTextReplacement("15")

    rule.onNodeWithTag("prepare_start_scaleless").performScrollTo().performClick()

    waitFor("scaleless_plan_water")
    rule.onNodeWithTag("scaleless_plan_water").assert(hasText("270", substring = true))
    assertEquals(18.0, env.scaleSession.ui.value.plannedDoseG!!, 0.0)
  }

  @Test
  fun togglingTheRecipeResetsTheFreeTextsAsBefore() {
    show()
    // Un texto fuera de rango no se guarda: el plan sigue en 15 y 16.
    rule.onNodeWithTag("prepare_dose").performTextReplacement("80")
    rule.onNodeWithTag("prepare_start_scaleless").assertIsNotEnabled()
    pickV60()
    // Con receta, la dosis es la suya (no el "80" libre).
    rule.onNodeWithTag("prepare_dose").assert(hasText("15", substring = true))
    rule.onNodeWithTag("prepare_start_scaleless").assertIsEnabled()

    rule.onNodeWithTag("target_chip").performClick()
    waitFor("target_picker")
    rule.onNodeWithTag("target_picker_clear").performClick()
    rule.waitUntil(5_000) { env.scaleSession.ui.value.target == null }

    // Los campos vuelven a editarse con el plan guardado: el "80" no se arrastra.
    rule.onNodeWithTag("prepare_dose").assert(hasText("15", substring = true))
    rule.onNodeWithTag("prepare_ratio").assert(hasText("16", substring = true))
    rule.onNodeWithTag("prepare_start_scaleless").assertIsEnabled()
  }

  // --- dosis y ratio ---

  @Test
  fun withoutARecipeTheDoseAndRatioAreEditableAndTheWaterIsTheirProduct() {
    show()
    rule.onNodeWithTag("prepare_water").assert(hasText("240", substring = true))

    rule.onNodeWithTag("prepare_dose").performTextReplacement("18")
    rule.onNodeWithTag("prepare_ratio").performTextReplacement("15")

    rule.onNodeWithTag("prepare_water").assert(hasText("270", substring = true))
    assertEquals(18.0, env.brewSettings.defaultDoseG.value, 0.0)
    assertEquals(15.0, env.brewSettings.defaultRatio.value, 0.0)
  }

  @Test
  fun theDoseAndRatioSurviveRecreation() {
    val tester = StateRestorationTester(rule)
    tester.setContent { TestApp(env) }
    rule.onNodeWithTag("prepare_dose").performTextReplacement("18")
    rule.onNodeWithTag("prepare_ratio").performTextReplacement("15")

    tester.emulateSavedInstanceStateRestore()

    rule.onNodeWithTag("prepare_water").assert(hasText("270", substring = true))
    rule.onNodeWithTag("prepare_dose").assert(hasText("18", substring = true))
    rule.onNodeWithTag("prepare_ratio").assert(hasText("15", substring = true))
  }

  @Test
  fun aValueOutOfRangeMarksTheFieldAndIsNotSaved() {
    show()

    rule.onNodeWithTag("prepare_dose").performTextReplacement("80")
    rule.onNodeWithTag("prepare_ratio").performTextReplacement("5")

    rule.onNodeWithTag("prepare_range_error").assertIsDisplayed()
    assertEquals(15.0, env.brewSettings.defaultDoseG.value, 0.0)
    assertEquals(16.0, env.brewSettings.defaultRatio.value, 0.0)
    rule.onNodeWithTag("prepare_water").assert(hasText("240", substring = true))

    rule.onNodeWithTag("prepare_dose").performTextReplacement("20")
    rule.onNodeWithTag("prepare_ratio").performTextReplacement("14,5")

    assertTrue(!exists("prepare_range_error"))
    assertEquals(20.0, env.brewSettings.defaultDoseG.value, 0.0)
    assertEquals(14.5, env.brewSettings.defaultRatio.value, 0.0)
  }

  @Test
  fun withARecipeTheValuesComeFromItAndOnlyTheDoseIsEditable() {
    show()
    rule.onNodeWithTag("target_chip").performClick()
    waitFor("target_picker")
    rule.onNodeWithText(recipeName()).performClick()

    rule.waitUntil(5_000) { env.scaleSession.ui.value.target != null }
    rule.onNodeWithTag("prepare_water").assert(hasText("250", substring = true))
    rule.onNodeWithTag("prepare_dose").assert(hasText("15", substring = true))
    rule.onNodeWithTag("prepare_ratio").assert(hasText("1:16", substring = true))
    rule.onNodeWithTag("prepare_ratio").assert(SemanticsMatcher.keyNotDefined(SemanticsActions.SetText))
    assertTrue(!exists("prepare_recipe_scaled"))
  }

  @Test
  fun aDoseTypedForTheRecipeScalesItAndTheScalelessBrew() {
    show()
    pickV60()

    rule.onNodeWithTag("prepare_dose").performTextReplacement("12")

    // 250 g para 15 g: con 12 g, 200 g y el mismo ratio.
    rule.onNodeWithTag("prepare_water").assert(hasText("200", substring = true))
    rule.onNodeWithTag("prepare_ratio").assert(hasText("1:16", substring = true))
    rule.onNodeWithTag("prepare_recipe_scaled").assertIsDisplayed()

    rule.onNodeWithTag("prepare_dose").performTextReplacement("80")
    rule.onNodeWithTag("prepare_range_error").assertIsDisplayed()
    rule.onNodeWithTag("prepare_start_scaleless").assertIsNotEnabled()

    rule.onNodeWithTag("prepare_dose").performTextReplacement("12")
    rule.onNodeWithTag("prepare_start_scaleless").performScrollTo().performClick()

    waitFor("scaleless_start")
    assertEquals(12.0, env.scaleSession.ui.value.plannedDoseG!!, 0.0)
    assertEquals(200.0, env.scaleSession.ui.value.targetWaterG!!, 1e-9)
  }

  // --- sin báscula ---

  @Test
  fun withoutAScaleThereIsNoCardAndASingleStartOpensTheScalelessSession() {
    env.connectionSettings.setUseScale(false)
    show()
    waitFor("prepare_start_scaleless")

    assertTrue(!exists("scale_card"))
    assertTrue(!exists("prepare_start"))
    assertTrue(!exists("prepare_start_hint"))
    rule.onNodeWithTag("prepare_start_scaleless").assert(hasText(context.getString(R.string.prepare_start)))

    rule.onNodeWithTag("prepare_start_scaleless").performScrollTo().assertIsEnabled().performClick()

    waitFor("scaleless_start")
    assertTrue(env.scaleSession.ui.value.scaleless)
    assertTrue(env.connectionsMade.isEmpty())
  }

  @Test
  fun switchingToWithoutAScaleInSettingsHidesTheCardAndDisconnects() {
    show()
    connectSimulated()

    rule.onNodeWithTag("prepare_settings").performClick()
    waitFor("settings_scale")
    rule.onNodeWithText(context.getString(R.string.settings_scale_without)).performClick()
    rule.onNodeWithContentDescription(context.getString(R.string.scale_back)).performClick()

    waitFor("prepare_start_scaleless")
    assertTrue(!exists("scale_card"))
    rule.waitUntil(5_000) { env.scaleSession.ui.value.connection != ConnectionState.Ready }
  }

  // --- receta y café ---

  @Test
  fun theXClearsTheRecipeAndTheCoffeeWithoutOpeningThePickers() {
    runBlocking { env.coffees.save(CoffeeDraft(name = "Etiopía Guji")) }
    show()
    assertTrue(!exists("target_clear"))
    assertTrue(!exists("coffee_clear"))
    pickV60()
    rule.onNodeWithTag("coffee_trigger").performClick()
    waitFor("coffee_picker_row")
    rule.onNodeWithTag("coffee_picker_row").performClick()
    rule.waitUntil(5_000) { env.scaleSession.ui.value.coffee != null }
    waitFor("coffee_clear")

    rule.onNodeWithTag("target_clear").performClick()
    rule.waitUntil(5_000) { env.scaleSession.ui.value.target == null }
    rule.onNodeWithTag("coffee_clear").performClick()
    rule.waitUntil(5_000) { env.scaleSession.ui.value.coffee == null }

    assertTrue(!exists("target_picker"))
    assertTrue(!exists("coffee_picker_row"))
    rule.onNodeWithTag("target_chip").assert(hasText(context.getString(R.string.brew_setup_target_none)))
    rule.onNodeWithTag("coffee_trigger").assert(hasText(context.getString(R.string.brew_setup_coffee_none)))
    assertTrue(!exists("target_clear"))
    assertTrue(!exists("coffee_clear"))
  }

  @Test
  fun theRecipeRowStartsWithoutARecipeAndShowsTheChosenOne() {
    show()
    rule.onNodeWithTag("target_chip").assert(hasText(context.getString(R.string.brew_setup_target_none)))

    rule.onNodeWithTag("target_chip").performClick()
    waitFor("target_picker")
    val name = recipeName()
    rule.onNodeWithText(name).performClick()

    rule.waitUntil(5_000) { env.scaleSession.ui.value.target?.name == name }
    rule.onNodeWithTag("target_chip").assert(hasText(name))
    assertTrue(!exists("target_picker"))
  }

  @Test
  fun manageRecipesSelectsTheRecipesTab() {
    show()
    rule.onNodeWithTag("target_chip").performClick()
    waitFor("target_picker_manage")

    rule.onNodeWithTag("target_picker_manage").performClick()

    waitFor("recipes_add")
    rule.onNodeWithTag("recipes_add").assertIsDisplayed()
  }

  @Test
  fun theCoffeeRowStartsWithoutACoffeeAndShowsTheChosenOne() {
    runBlocking { env.coffees.save(CoffeeDraft(name = "Etiopía Guji")) }
    show()
    rule.onNodeWithTag("coffee_trigger").assert(hasText(context.getString(R.string.brew_setup_coffee_none)))

    rule.onNodeWithTag("coffee_trigger").performClick()
    waitFor("coffee_picker_row")
    rule.onNodeWithTag("coffee_picker_row").performClick()

    rule.waitUntil(5_000) { env.scaleSession.ui.value.coffee != null }
    rule.onNodeWithTag("coffee_trigger").assert(hasText("Etiopía Guji", substring = true))
  }

  @Test
  fun aDeletedRecipeFallsBackToNoRecipeAndWarns() {
    val id = runBlocking { env.recipes.list().first().first { it.name.contains("V60") }.id }
    show()
    env.scaleSession.selectTarget(ReferenceKey.Recipe(id))
    rule.waitUntil(5_000) { env.scaleSession.ui.value.target != null }

    runBlocking { env.recipes.delete(id) }

    rule.waitUntil(5_000) { env.scaleSession.ui.value.target == null }
    rule.onNodeWithText(context.getString(R.string.target_error_removed)).assertIsDisplayed()
    rule.onNodeWithTag("target_chip").assert(hasText(context.getString(R.string.brew_setup_target_none)))
    // Sin receta vuelven la dosis y el ratio editables.
    rule.onNodeWithTag("prepare_dose").assertIsDisplayed()
  }

  // --- La tarjeta de molienda (SPEC-006) ---

  @Test
  fun theGrindCardShowsInPrepareWithAndWithoutARecipe() {
    runBlocking { env.db.addGrinder(TestGrinders.uno) }
    show()

    waitFor("grind_card")
    waitFor("grind_card_no_reference")
    rule.onNodeWithTag("grind_card_grinder").assertIsDisplayed()

    val id = runBlocking { env.recipes.list().first().first { it.name.contains("V60") }.id }
    rule.runOnUiThread { env.scaleSession.selectTarget(ReferenceKey.Recipe(id)) }
    waitFor("grind_card_value")
    assertTrue(!exists("grind_card_no_reference"))
  }
}
