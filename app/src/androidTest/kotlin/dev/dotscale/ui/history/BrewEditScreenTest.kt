package dev.dotscale.ui.history

import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import dev.dotscale.R
import dev.dotscale.coffee.CoffeeRepository
import dev.dotscale.coffee.PhotoFiles
import dev.dotscale.history.BrewDetails
import dev.dotscale.history.BrewMethod
import dev.dotscale.history.BrewRepository
import dev.dotscale.history.HistoryDatabase
import dev.dotscale.history.TestBrews
import dev.dotscale.ui.coffees.CoffeePickerViewModel
import dev.dotscale.ui.theme.DotScaleTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class BrewEditScreenTest {
  @get:Rule
  val rule = createComposeRule()

  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private lateinit var db: HistoryDatabase
  private lateinit var repository: BrewRepository
  private lateinit var coffees: CoffeeRepository
  private var backCount = 0
  private var newCoffeeRequests = 0
  private var id = 0L

  /** La preparación de partida es anterior al catálogo: tiene el texto "Etiopía" y ningún vínculo. */
  @Before
  fun setUp() {
    db = HistoryDatabase.inMemory(context)
    repository = BrewRepository(db)
    coffees = CoffeeRepository(db, PhotoFiles { })
    id = runBlocking { repository.save(TestBrews.finished(totalMs = 60_000), TestBrews.context()) }
    db.openHelper.writableDatabase.execSQL("UPDATE brews SET coffee = 'Etiopía' WHERE id = $id")
  }

  private fun open(brewId: Long = id) {
    val viewModel = BrewEditViewModel(repository, coffees, brewId)
    val picker = CoffeePickerViewModel(coffees)
    rule.setContent {
      DotScaleTheme(darkTheme = false) {
        BrewEditScreen(viewModel, onBack = { backCount++ }, coffeePickerViewModel = picker, onNewCoffee = { newCoffeeRequests++ })
      }
    }
    rule.waitUntil(5_000) { rule.onAllNodesWithTag("brew_edit_save").fetchSemanticsNodes().isNotEmpty() }
  }

  private fun catalogCoffee(name: String, roastDate: LocalDate? = null): Long =
    runBlocking { coffees.save(TestBrews.coffeeDraft(name = name).copy(roastDate = roastDate)) }

  private fun saveAndWait() {
    rule.onNodeWithTag("brew_edit_save").performScrollTo().performClick()
    rule.waitUntil(5_000) { backCount > 0 }
  }

  private fun entity() = runBlocking { repository.get(id) }!!.entity

  @After
  fun tearDown() = db.close()

  private fun modalShown() = rule.onAllNodesWithTag("ant_modal").fetchSemanticsNodes().isNotEmpty()

  @Test
  fun loadsTheExistingDetailsAndShowsAnOldCoffeeTextWithItsHelp() {
    open()
    rule.onNodeWithTag("brew_edit_coffee").assertExists().assertTextContains("Etiopía")
    rule.onNodeWithTag("brew_edit_coffee_legacy").assertExists()
    rule.onNodeWithTag("brew_edit_save").assertIsEnabled()
  }

  @Test
  fun anOldCoffeeTextIsKeptWhenNothingIsDoneAboutIt() {
    open()
    rule.onNodeWithTag("brew_edit_name").performTextInput("Solo el nombre")
    saveAndWait()
    val saved = entity()
    assertEquals("Etiopía", saved.coffee)
    assertNull(saved.coffeeId)
    assertEquals("Solo el nombre", saved.name)
  }

  @Test
  fun theMethodSelectorOffersTheNewMethodsAndSavesTheChosenOne() {
    open()
    val names = listOf(
      "Espresso" to BrewMethod.ESPRESSO,
      context.getString(R.string.brew_method_french_press) to BrewMethod.FRENCH_PRESS,
      context.getString(R.string.brew_method_cold_brew) to BrewMethod.COLD_BREW,
      context.getString(R.string.brew_method_moka) to BrewMethod.MOKA,
    )
    for ((name, _) in names) {
      rule.onNodeWithTag("brew_edit_method").performScrollTo().performClick()
      rule.onNode(hasText(name) and hasAnyAncestor(isPopup())).performClick()
      rule.onNodeWithTag("brew_edit_method").assertTextContains(name, substring = true)
    }
    saveAndWait()
    assertEquals(BrewMethod.MOKA, entity().method)
  }

  @Test
  fun theNotesFieldIsCalledTastingNotes() {
    open()
    rule.onNodeWithTag("brew_edit_notes").performScrollTo().assertExists()
    val label = context.getString(R.string.brew_edit_notes)
    assertTrue(label, label == "Notas de cata" || label == "Tasting notes")
    assertTrue(rule.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty())
  }

  @Test
  fun settingARatingAndSavingStoresIt() {
    open()
    rule.onNodeWithTag("brew_edit_rating_4").performScrollTo().performClick()
    rule.onNodeWithTag("brew_edit_rating_clear").assertExists()
    saveAndWait()
    assertEquals(4, entity().rating)
  }

  @Test
  fun theSameStarOrTheClearButtonRemovesTheRating() {
    runBlocking { repository.updateDetails(id, BrewDetails(rating = 4)) }
    open(id)

    rule.onNodeWithTag("brew_edit_rating_4").performScrollTo().performClick() // la misma estrella: quita
    rule.onAllNodesWithTag("brew_edit_rating_clear").assertCountEquals(0)

    rule.onNodeWithTag("brew_edit_rating_2").performClick()
    rule.onNodeWithTag("brew_edit_rating_clear").performClick() // "Quitar valoración"
    rule.onAllNodesWithTag("brew_edit_rating_clear").assertCountEquals(0)
    saveAndWait()
    assertNull(entity().rating)
  }

  @Test
  fun pickingACoffeeFromThePickerLinksTheBrewAndCopiesItsName() {
    open()
    val guji = catalogCoffee("Guji")
    rule.onNodeWithTag("brew_edit_coffee").performScrollTo().performClick()
    rule.waitUntil(5_000) { rule.onAllNodesWithTag("coffee_picker_row").fetchSemanticsNodes().isNotEmpty() }
    rule.onAllNodesWithTag("coffee_picker_row")[0].performClick()
    rule.waitUntil(5_000) { rule.onAllNodesWithTag("coffee_picker").fetchSemanticsNodes().isEmpty() }
    rule.onNodeWithTag("brew_edit_coffee").assertTextContains("Guji", substring = true)
    rule.onAllNodesWithTag("brew_edit_coffee_legacy").assertCountEquals(0)

    saveAndWait()
    assertEquals(guji, entity().coffeeId)
    assertEquals("Guji", entity().coffee)
  }

  @Test
  fun withoutCoffeeRemovesTheLinkAndTheName() {
    open()
    catalogCoffee("Guji")
    // Una preparación antigua: "Sin café" quita también el texto.
    rule.onNodeWithTag("brew_edit_coffee").performScrollTo().performClick()
    rule.waitUntil(5_000) { rule.onAllNodesWithTag("coffee_picker_clear").fetchSemanticsNodes().isNotEmpty() }
    rule.onNodeWithTag("coffee_picker_clear").performClick()
    rule.waitUntil(5_000) { rule.onAllNodesWithTag("coffee_picker").fetchSemanticsNodes().isEmpty() }

    saveAndWait()
    assertNull(entity().coffeeId)
    assertNull(entity().coffee)
  }

  @Test
  fun aLinkedArchivedCoffeeStaysSelectedAndOfferedInThePicker() {
    val archived = catalogCoffee("Colombia")
    runBlocking {
      repository.setCoffee(id, archived)
      coffees.setArchived(archived, true)
    }
    open(id)

    rule.onNodeWithTag("brew_edit_coffee").assertTextContains("Colombia", substring = true)
    rule.onNodeWithTag("brew_edit_coffee").performScrollTo().performClick()
    rule.waitUntil(5_000) { rule.onAllNodesWithTag("coffee_picker_row").fetchSemanticsNodes().isNotEmpty() }
    rule.onAllNodesWithTag("coffee_archived_tag", useUnmergedTree = true).assertCountEquals(1)
    rule.onNodeWithTag("coffee_selected_tag", useUnmergedTree = true).assertExists()
  }

  @Test
  fun theDaysSinceRoastAreCountedUpToTheBrewDate() {
    // La preparación empezó el 14 nov 2023; el tueste fue el 5: día 9, sea cual sea la fecha de hoy.
    val brewStart = LocalDate.of(2023, 11, 14).atTime(12, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    val dated = runBlocking { repository.save(TestBrews.finished(totalMs = 60_000), TestBrews.context(startedAtEpochMs = brewStart)) }
    val coffeeId = catalogCoffee("Guji", LocalDate.of(2023, 11, 5))
    runBlocking { repository.setCoffee(dated, coffeeId) }
    id = dated
    open(dated)

    rule.onNodeWithTag("brew_edit_coffee").assertTextContains(context.getString(R.string.brew_setup_coffee_value, "Guji", 9), substring = true)
  }

  @Test
  fun newCoffeeAsksForTheEditorAndThenSelectsTheCreatedOne() {
    open()
    rule.onNodeWithTag("brew_edit_coffee").performScrollTo().performClick()
    rule.waitUntil(5_000) { rule.onAllNodesWithTag("coffee_picker_new").fetchSemanticsNodes().isNotEmpty() }
    rule.onNodeWithTag("coffee_picker_new").performClick()
    assertEquals(1, newCoffeeRequests)

    val created = catalogCoffee("Recién creado")
    rule.waitUntil(5_000) {
      rule.onAllNodes(hasTestTag("brew_edit_coffee") and hasText("Recién creado", substring = true)).fetchSemanticsNodes().isNotEmpty()
    }
    saveAndWait()
    assertEquals(created, entity().coffeeId)
  }

  @Test
  fun aCoffeeCreatedWithoutAskingIsNotSelected() {
    open()
    catalogCoffee("Otro")
    rule.waitForIdle()
    rule.onNodeWithTag("brew_edit_coffee").assertTextContains("Etiopía", substring = true)
  }

  @Test
  fun theRatingAloneMakesTheFormDirty() {
    open()
    rule.onNodeWithTag("brew_edit_rating_3").performScrollTo().performClick()
    rule.onNodeWithContentDescription(context.getString(R.string.scale_back)).performClick()
    rule.waitUntil(5_000) { modalShown() }
    assertEquals(0, backCount)
  }

  @Test
  fun aTemperatureOutOfRangeShowsAnErrorAndDisablesSave() {
    open()
    rule.onNodeWithTag("brew_edit_temp").performScrollTo().performTextInput("120")
    rule.onNodeWithTag("brew_edit_temp_error").assertExists()
    rule.onNodeWithTag("brew_edit_save").performScrollTo().assertIsNotEnabled()

    rule.onNodeWithTag("brew_edit_temp").performTextReplacement("92,5")
    rule.onAllNodesWithTag("brew_edit_temp_error").let { assertEquals(0, it.fetchSemanticsNodes().size) }
    rule.onNodeWithTag("brew_edit_save").assertIsEnabled()
  }

  @Test
  fun savePersistsTheDataAndGoesBack() {
    open()
    rule.onNodeWithTag("brew_edit_name").performTextInput("Prueba")
    rule.onNodeWithTag("brew_edit_grind").performScrollTo().performTextInput("21 clics")
    rule.onNodeWithTag("brew_edit_temp").performScrollTo().performTextInput("93")
    rule.onNodeWithTag("brew_edit_notes").performScrollTo().performTextInput("Dulce")
    rule.onNodeWithTag("brew_edit_save").performScrollTo().performClick()

    rule.waitUntil(5_000) { backCount > 0 }
    val entity = runBlocking { repository.get(id) }!!.entity
    assertEquals("Prueba", entity.name)
    assertEquals("Etiopía", entity.coffee)
    assertNull(entity.coffeeId)
    assertEquals("21 clics", entity.grind)
    assertEquals(93.0, entity.waterTempC!!, 0.0)
    assertEquals("Dulce", entity.notes)
  }

  @Test
  fun backWithChangesAsksForConfirmation() {
    open()
    rule.onNodeWithTag("brew_edit_name").performTextInput("Cambio")
    Espresso.pressBack()
    // Con el teclado abierto, el primer "atrás" solo lo cierra: se repite una vez si el diálogo no aparece.
    val shown = try {
      rule.waitUntil(2_000) { modalShown() }
      true
    } catch (_: ComposeTimeoutException) {
      false
    }
    if (!shown) Espresso.pressBack()
    rule.waitUntil(5_000) { modalShown() }
    assertEquals(0, backCount)

    rule.onNodeWithTag("ant_modal_cancel").performClick() // "Seguir editando"
    rule.waitUntil(5_000) { !modalShown() }
    assertEquals(0, backCount)

    rule.onNodeWithContentDescription(context.getString(R.string.scale_back)).performClick()
    rule.waitUntil(5_000) { modalShown() }
    rule.onNodeWithTag("ant_modal_confirm").performClick() // "Descartar"
    rule.waitUntil(5_000) { backCount == 1 }
    assertTrue(runBlocking { repository.get(id) }!!.entity.name == null)
  }

  @Test
  fun backWithoutChangesLeavesDirectly() {
    open()
    rule.onNodeWithContentDescription(context.getString(R.string.scale_back)).performClick()
    rule.waitUntil(5_000) { backCount == 1 }
    assertEquals(false, modalShown())
  }
}
